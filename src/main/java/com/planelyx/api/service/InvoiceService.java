package com.planelyx.api.service;

import static java.util.Objects.isNull;
import static org.springframework.util.StringUtils.hasText;

import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.CreditCard;
import com.planelyx.api.domain.Invoice;
import com.planelyx.api.domain.Transaction;
import com.planelyx.api.domain.enums.CategoryType;
import com.planelyx.api.domain.enums.InvoiceStatus;
import com.planelyx.api.domain.enums.TransactionKind;
import com.planelyx.api.exception.NotFoundException;
import com.planelyx.api.repository.CategoryRepository;
import com.planelyx.api.repository.InvoiceRepository;
import com.planelyx.api.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class InvoiceService {

    /**
     * Used when the caller sends no wording of its own. The API has no translations, so the text a
     * user actually reads comes from the client that knows their language.
     */
    private static final String DEFAULT_ADJUSTMENT_DESCRIPTION = "Invoice adjustment";

    private final InvoiceRepository invoiceRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final CreditCardService creditCardService;

    /**
     * The billing period a charge falls into, and when that period falls due.
     *
     * The closing day is inclusive: a charge dated on it belongs to the period ending that day, and
     * only the day after starts the next one. So a card closing on the 28th and due on the 5th puts
     * a charge from 30 Jul through one on 28 Aug onto the period 29 Jul – 28 Aug, due 5 Sep; a
     * charge on 29 Aug starts the following period, due 5 Oct.
     *
     * Note that such a period closes in August but is due in September, and it is September that
     * the owner calls it — see {@link com.planelyx.api.dto.InvoiceResponse#referenceMonth()}. The
     * two only coincide while the due day falls later in the month than the closing day.
     */
    public BillingPeriod resolveBillingPeriod(CreditCard card, LocalDate transactionDate) {
        YearMonth transactionMonth = YearMonth.from(transactionDate);
        LocalDate closingThisMonth = dateForDay(transactionMonth, card.getClosingDay());

        LocalDate periodEnd = transactionDate.isAfter(closingThisMonth)
                ? dateForDay(transactionMonth.plusMonths(1), card.getClosingDay())
                : closingThisMonth;

        YearMonth periodEndMonth = YearMonth.from(periodEnd);
        LocalDate previousClosing = dateForDay(periodEndMonth.minusMonths(1), card.getClosingDay());
        LocalDate periodStart = previousClosing.plusDays(1);

        return new BillingPeriod(periodStart, periodEnd, dueDateFor(card, periodEnd));
    }

    /**
     * When a period closing on {@code periodEnd} falls due.
     *
     * A due day at or before the closing day plainly means the month after. Otherwise it means the
     * closing month — except that clamping both days into a short month can collapse them onto the
     * same date (closing 29 and due 30 are both 28 February), which would have an invoice fall due
     * before it had finished closing. The month after is the only reading that stays sensible.
     */
    private LocalDate dueDateFor(CreditCard card, LocalDate periodEnd) {
        YearMonth periodEndMonth = YearMonth.from(periodEnd);

        if (card.getDueDay() > card.getClosingDay()) {
            LocalDate sameMonth = dateForDay(periodEndMonth, card.getDueDay());

            if (sameMonth.isAfter(periodEnd)) {
                return sameMonth;
            }
        }

        return dateForDay(periodEndMonth.plusMonths(1), card.getDueDay());
    }

    private LocalDate dateForDay(YearMonth yearMonth, int day) {
        int clampedDay = Math.min(day, yearMonth.lengthOfMonth());

        return yearMonth.atDay(clampedDay);
    }

    public Invoice findOrCreateInvoiceForCharge(CreditCard card, LocalDate transactionDate) {
        BillingPeriod period = resolveBillingPeriod(card, transactionDate);

        return invoiceRepository
                .findByCreditCardIdAndBillingPeriodStart(card.getId(), period.start())
                .orElseGet(() -> invoiceRepository.save(Invoice.builder()
                        .creditCard(card)
                        .billingPeriodStart(period.start())
                        .billingPeriodEnd(period.end())
                        .dueDate(period.dueDate())
                        .totalAmount(BigDecimal.ZERO)
                        .status(InvoiceStatus.OPEN)
                        .build()));
    }

    public void recomputeTotal(UUID invoiceId) {
        Invoice invoice = invoiceRepository
                .findById(invoiceId)
                .orElseThrow(() -> new NotFoundException("Invoice not found: " + invoiceId));

        BigDecimal total = transactionRepository.sumAmountByInvoiceId(invoiceId);

        invoice.setTotalAmount(total);

        invoiceRepository.save(invoice);
    }

    public InvoiceStatus derivedStatus(Invoice invoice) {
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return InvoiceStatus.PAID;
        }

        return LocalDate.now().isAfter(invoice.getBillingPeriodEnd()) ? InvoiceStatus.CLOSED : InvoiceStatus.OPEN;
    }

    public List<Invoice> findAll(UUID ownerId, UUID creditCardId, InvoiceStatus status) {
        List<Invoice> invoices = Objects.nonNull(creditCardId)
                ? invoiceRepository.findAllByCreditCardId(requireOwnedCard(creditCardId, ownerId))
                : invoiceRepository.findAllByCreditCardOwnerId(ownerId);

        if (isNull(status)) {
            return invoices;
        }

        return invoices.stream()
                .filter(invoice -> derivedStatus(invoice) == status)
                .toList();
    }

    private UUID requireOwnedCard(UUID creditCardId, UUID ownerId) {
        return creditCardService.findById(creditCardId, ownerId).getId();
    }

    public Invoice findById(UUID id, UUID ownerId) {
        return invoiceRepository
                .findByIdAndCreditCardOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Invoice not found: " + id));
    }

    public List<Transaction> transactionsFor(UUID invoiceId) {
        return transactionRepository.findAllByInvoiceId(invoiceId);
    }

    public Page<Transaction> transactionsFor(UUID invoiceId, Pageable pageable) {
        return transactionRepository.findAllByInvoiceId(invoiceId, pageable);
    }

    /** Total still owed on a card — everything invoiced but not yet paid. */
    public BigDecimal unpaidTotal(UUID creditCardId) {
        return invoiceRepository.sumUnpaidTotalByCreditCardId(creditCardId);
    }

    /** The same figure for every card an owner holds. Cards with nothing outstanding are absent. */
    public Map<UUID, BigDecimal> unpaidTotalsByCard(UUID ownerId) {
        return invoiceRepository.sumUnpaidTotalsByOwnerId(ownerId).stream()
                .collect(Collectors.toMap(
                        InvoiceRepository.CardTotal::getCreditCardId, InvoiceRepository.CardTotal::getTotal));
    }

    public Invoice pay(UUID id, UUID ownerId) {
        Invoice invoice = findById(id, ownerId);

        if (invoice.getStatus() != InvoiceStatus.PAID) {
            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setPaidAt(Instant.now());

            invoiceRepository.save(invoice);
        }

        return invoice;
    }

    /**
     * Corrects the invoice to the figure the owner read off their statement.
     *
     * The total is the sum of the invoice's charges, so there is nothing to overwrite — the
     * difference is recorded as a charge of its own and the total recomputed from it. That
     * leaves the discrepancy visible among the charges instead of silently absorbed.
     *
     * The adjustment charge is written directly rather than through
     * {@link TransactionService#create}, which requires a positive amount: correcting an invoice
     * downwards needs a negative one. Nothing downstream minds — the column has no sign
     * constraint, the total is a plain sum, and the dashboard's expense figure adds every
     * non-credit kind, so a negative charge correctly reduces reported spending.
     *
     * A paid invoice is refused. Its charges have already been settled, and moving the total
     * afterwards would rewrite what was actually paid.
     */
    public Invoice adjust(UUID id, BigDecimal targetAmount, String description, UUID ownerId) {
        Invoice invoice = findById(id, ownerId);

        if (derivedStatus(invoice) == InvoiceStatus.PAID) {
            throw new IllegalStateException("A paid invoice cannot be adjusted: " + id);
        }

        BigDecimal delta = targetAmount.subtract(invoice.getTotalAmount());

        if (delta.signum() == 0) {
            return invoice;
        }

        transactionRepository.save(Transaction.builder()
                .ownerId(invoice.getCreditCard().getOwnerId())
                .kind(TransactionKind.CARD_CHARGE)
                .creditCard(invoice.getCreditCard())
                .invoice(invoice)
                .category(adjustmentCategory(ownerId))
                .amount(delta)
                .transactionDate(adjustmentDate(invoice))
                .description(hasText(description) ? description : DEFAULT_ADJUSTMENT_DESCRIPTION)
                .paid(true)
                .build());

        recomputeTotal(invoice.getId());

        return invoice;
    }

    /**
     * A date inside the billing period, so the charge lands on this invoice rather than being
     * swept into the next one by {@link #resolveBillingPeriod}.
     */
    private LocalDate adjustmentDate(Invoice invoice) {
        LocalDate today = LocalDate.now();

        if (today.isBefore(invoice.getBillingPeriodStart())) {
            return invoice.getBillingPeriodStart();
        }

        return today.isAfter(invoice.getBillingPeriodEnd()) ? invoice.getBillingPeriodEnd() : today;
    }

    private Category adjustmentCategory(UUID ownerId) {
        return categoryRepository
                .findAdjustmentForOwner(ownerId, CategoryType.EXPENSE)
                .orElseThrow(() -> new NotFoundException("Adjustment category is missing for owner: " + ownerId));
    }

    public Invoice unpay(UUID id, UUID ownerId) {
        Invoice invoice = findById(id, ownerId);

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            invoice.setStatus(InvoiceStatus.OPEN);
            invoice.setPaidAt(null);

            invoiceRepository.save(invoice);
        }

        return invoice;
    }

    /**
     * Removes the invoice along with every charge on it.
     *
     * The charges go first: nothing may still point at the invoice when it is removed, and there is
     * no cascade on the foreign key. They are deleted rather than detached because a card charge
     * with no invoice is not a state the rest of the app knows how to read — it would still count
     * towards the month's spending while belonging to nothing.
     */
    public void delete(UUID id, UUID ownerId) {
        deleteWithCharges(findById(id, ownerId));
    }

    private void deleteWithCharges(Invoice invoice) {
        transactionRepository.deleteAll(transactionRepository.findAllByInvoiceId(invoice.getId()));
        transactionRepository.flush();

        invoiceRepository.delete(invoice);
    }
}
