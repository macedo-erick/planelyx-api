package com.planelyx.api.service;

import static java.util.Objects.isNull;

import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.CreditCard;
import com.planelyx.api.domain.Invoice;
import com.planelyx.api.domain.SystemCategories;
import com.planelyx.api.domain.Transaction;
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

    private final InvoiceRepository invoiceRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final CreditCardService creditCardService;

    public BillingPeriod resolveBillingPeriod(CreditCard card, LocalDate transactionDate) {
        YearMonth transactionMonth = YearMonth.from(transactionDate);
        LocalDate closingThisMonth = dateForDay(transactionMonth, card.getClosingDay());

        LocalDate periodEnd = transactionDate.isAfter(closingThisMonth)
                ? dateForDay(transactionMonth.plusMonths(1), card.getClosingDay())
                : closingThisMonth;

        YearMonth periodEndMonth = YearMonth.from(periodEnd);
        LocalDate previousClosing = dateForDay(periodEndMonth.minusMonths(1), card.getClosingDay());
        LocalDate periodStart = previousClosing.plusDays(1);

        YearMonth dueMonth = card.getDueDay() <= card.getClosingDay() ? periodEndMonth.plusMonths(1) : periodEndMonth;
        LocalDate dueDate = dateForDay(dueMonth, card.getDueDay());

        return new BillingPeriod(periodStart, periodEnd, dueDate);
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
    public Invoice adjust(UUID id, BigDecimal targetAmount, UUID ownerId) {
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
                .category(adjustmentCategory())
                .amount(delta)
                .transactionDate(adjustmentDate(invoice))
                .description("Invoice adjustment")
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

    private Category adjustmentCategory() {
        return categoryRepository
                .findById(SystemCategories.ADJUSTMENT_EXPENSE)
                .orElseThrow(() -> new NotFoundException("Adjustment category is missing — check migration V11"));
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
}
