package com.planelyx.api.service;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.springframework.util.StringUtils.hasText;

import com.planelyx.api.domain.BankAccount;
import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.CreditCard;
import com.planelyx.api.domain.Invoice;
import com.planelyx.api.domain.Transaction;
import com.planelyx.api.domain.enums.CategoryType;
import com.planelyx.api.domain.enums.InvoiceStatus;
import com.planelyx.api.domain.enums.SystemCategoryKey;
import com.planelyx.api.domain.enums.TransactionKind;
import com.planelyx.api.dto.InvoicePaymentRequest;
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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class InvoiceService {

    /**
     * Used when the caller sends no wording of its own. The API has no translations, so the text a
     * user actually reads comes from the client that knows their language.
     */
    private static final String DEFAULT_ADJUSTMENT_DESCRIPTION = "Invoice adjustment";

    /** Same reasoning as above: the client has no wording to send for a payment it did not describe. */
    private static final String DEFAULT_PAYMENT_DESCRIPTION = "Invoice payment";

    private final InvoiceRepository invoiceRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final CreditCardService creditCardService;
    private final BankAccountService bankAccountService;

    /**
     * The billing period a charge falls into, and when that period falls due.
     *
     * The closing day itself starts the next period rather than finishing the current one: a real
     * card has already cut off by the time the day is through, so a charge dated on it is too late
     * for the invoice that is closing. So a card closing on the 28th and due on the 5th puts a
     * charge from 28 Jul through one on 27 Aug onto the period 28 Jul – 27 Aug, due 5 Sep; a charge
     * on 28 Aug already starts the following period, due 5 Oct.
     *
     * Note that such a period closes in August but is due in September, and it is September that
     * the owner calls it — see {@link com.planelyx.api.dto.InvoiceResponse#referenceMonth()}. The
     * two only coincide while the due day falls later in the month than the closing day.
     */
    public BillingPeriod resolveBillingPeriod(CreditCard card, LocalDate transactionDate) {
        YearMonth transactionMonth = YearMonth.from(transactionDate);
        LocalDate closingThisMonth = dateForDay(transactionMonth, card.getClosingDay());

        LocalDate periodStart = transactionDate.isBefore(closingThisMonth)
                ? dateForDay(transactionMonth.minusMonths(1), card.getClosingDay())
                : closingThisMonth;

        LocalDate periodEnd = dateForDay(YearMonth.from(periodStart).plusMonths(1), card.getClosingDay())
                .minusDays(1);

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

    /** A corrupted override chain can't loop forever; this is a defensive cap, never an expected path. */
    private static final int MAX_FORWARD_HOPS = 24;

    /**
     * The invoice a charge belongs to — ordinarily whichever one {@link #resolveBillingPeriod}
     * names, created on the spot if it doesn't exist yet.
     *
     * When that invoice was closed early (see {@link #closeEarly}) and the charge is dated after
     * its cutoff, this walks forward to the next period instead: a freshly-created invoice always
     * starts with no override, so the walk only ever chains across invoices already closed early
     * by hand, which in practice is at most one or two hops.
     */
    public Invoice findOrCreateInvoiceForCharge(CreditCard card, LocalDate transactionDate) {
        LocalDate effectiveDate = transactionDate;

        for (int hop = 0; hop < MAX_FORWARD_HOPS; hop++) {
            BillingPeriod period = resolveBillingPeriod(card, effectiveDate);
            Optional<Invoice> existing =
                    invoiceRepository.findByCreditCardIdAndBillingPeriodStart(card.getId(), period.start());

            if (existing.isPresent() && redirectsPast(existing.get(), transactionDate)) {
                effectiveDate = period.end().plusDays(1);
                continue;
            }

            return existing.orElseGet(() -> invoiceRepository.save(Invoice.builder()
                    .creditCard(card)
                    .billingPeriodStart(period.start())
                    .billingPeriodEnd(period.end())
                    .dueDate(period.dueDate())
                    .totalAmount(BigDecimal.ZERO)
                    .build()));
        }

        throw new IllegalStateException("Could not resolve an invoice for " + card.getId() + " on " + transactionDate);
    }

    /** Whether a charge dated {@code transactionDate} belongs after this invoice's early close, not on it. */
    private boolean redirectsPast(Invoice invoice, LocalDate transactionDate) {
        return invoice.getClosedEarlyOn() != null && transactionDate.isAfter(invoice.getClosedEarlyOn());
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
        if (invoice.getPaidAt() != null) {
            return InvoiceStatus.PAID;
        }

        if (invoice.getClosedEarlyOn() != null) {
            return InvoiceStatus.CLOSED;
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
        return transactionRepository.findChargesByInvoiceId(invoiceId);
    }

    public Page<Transaction> transactionsFor(UUID invoiceId, Pageable pageable) {
        return transactionRepository.findChargesByInvoiceId(invoiceId, pageable);
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

    /**
     * Settles the invoice, taking the money out of a real account.
     *
     * The status flip alone was never enough. A card charge never touches an account, so if
     * paying only marked the invoice, the money left no trace anywhere: the invoice dropped out
     * of the dashboard's deduction and no balance moved to replace it, which read as the debt
     * simply evaporating. The settlement is what closes that gap.
     *
     * It is posted as an {@link TransactionKind#INVOICE_PAYMENT} rather than an ordinary debit
     * because it is not spending — the charges it pays off were already counted as expenses in
     * the month this invoice fell due, and counting the settlement too would report them twice.
     *
     * Written straight to the repository, as {@link #adjust} is, because
     * {@link TransactionService#create} refuses system categories and knows nothing of this kind.
     */
    public Invoice pay(UUID id, InvoicePaymentRequest request, UUID ownerId) {
        log.info("Paying invoice {} owner={}", id, ownerId);

        Invoice invoice = findById(id, ownerId);

        if (invoice.getPaidAt() != null) {
            return invoice;
        }

        postSettlement(invoice, request, ownerId);

        invoice.setPaidAt(Instant.now());

        return invoiceRepository.save(invoice);
    }

    private void postSettlement(Invoice invoice, InvoicePaymentRequest request, UUID ownerId) {
        if (invoice.getTotalAmount().signum() == 0) {
            return;
        }

        LocalDate settledOn = paymentDate(request);

        transactionRepository.save(Transaction.builder()
                .ownerId(ownerId)
                .kind(TransactionKind.INVOICE_PAYMENT)
                .bankAccount(paymentAccount(invoice, request, ownerId))
                .invoice(invoice)
                .category(systemCategory(ownerId, SystemCategoryKey.INVOICE_PAYMENT, CategoryType.EXPENSE))
                .amount(invoice.getTotalAmount())
                .transactionDate(settledOn)
                .purchaseDate(settledOn)
                .description(paymentDescription(invoice, request))
                .paid(true)
                .build());
    }

    /**
     * What the row is called, in the caller's wording where there is any.
     *
     * The fallback names the card so the entry is still readable on its own, but it is English —
     * only the client knows what language to write in.
     */
    private String paymentDescription(Invoice invoice, InvoicePaymentRequest request) {
        if (nonNull(request) && hasText(request.description())) {
            return request.description();
        }

        return DEFAULT_PAYMENT_DESCRIPTION + " — " + invoice.getCreditCard().getName();
    }

    /**
     * The account the money comes out of: the one asked for, or the card's own.
     *
     * {@code credit_card.bank_account_id} is NOT NULL, so there is always a fallback — a card is
     * always tied to the account it is billed against.
     */
    private BankAccount paymentAccount(Invoice invoice, InvoicePaymentRequest request, UUID ownerId) {
        if (nonNull(request) && nonNull(request.bankAccountId())) {
            return bankAccountService.findById(request.bankAccountId(), ownerId);
        }

        return invoice.getCreditCard().getBankAccount();
    }

    /**
     * When the money left: the date given, or today.
     *
     * This used to fall back to the due date, on the reasoning that the due date is when the bank
     * takes it. That is only true of a direct debit. Someone paying by hand pays when they pay,
     * and dating their settlement to the vencimento puts the debit on a day the money was still
     * in the account — so the balance they read back does not match their statement, and
     * correcting it by hand leaves the month debited twice.
     *
     * Today is what the API actually knows: a client that knows better sends the date it means.
     */
    private LocalDate paymentDate(InvoicePaymentRequest request) {
        if (nonNull(request) && nonNull(request.paymentDate())) {
            return request.paymentDate();
        }

        return LocalDate.now();
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
        log.info("Adjusting invoice {} to {} owner={}", id, targetAmount, ownerId);

        Invoice invoice = findById(id, ownerId);

        if (derivedStatus(invoice) == InvoiceStatus.PAID) {
            throw new IllegalStateException("A paid invoice cannot be adjusted: " + id);
        }

        BigDecimal delta = targetAmount.subtract(invoice.getTotalAmount());

        if (delta.signum() == 0) {
            return invoice;
        }

        LocalDate adjustedOn = adjustmentDate(invoice);

        transactionRepository.save(Transaction.builder()
                .ownerId(invoice.getCreditCard().getOwnerId())
                .kind(TransactionKind.CARD_CHARGE)
                .creditCard(invoice.getCreditCard())
                .invoice(invoice)
                .category(adjustmentCategory(ownerId))
                .amount(delta)
                .transactionDate(adjustedOn)
                .purchaseDate(adjustedOn)
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

    /**
     * Closes the invoice today, even though the card's calendar closing day has not been
     * reached — compensating for a real-world cycle that has already turned over. From this
     * moment, {@link #findOrCreateInvoiceForCharge} routes anything dated after today onto the
     * next period instead of this one.
     *
     * Charges already filed into the dead zone — dated after today but still inside this period,
     * such as a pre-dated recurring occurrence — are moved to the next invoice right away;
     * otherwise the feature would not fix anything already filed ahead of time.
     *
     * Only an open invoice may be closed early: one already closed, by calendar or by an earlier
     * early close, or already paid, has nothing left to bring forward.
     */
    public Invoice closeEarly(UUID id, UUID ownerId) {
        log.info("Closing invoice {} early owner={}", id, ownerId);

        Invoice invoice = findById(id, ownerId);

        if (derivedStatus(invoice) != InvoiceStatus.OPEN) {
            throw new IllegalStateException("Only an open invoice can be closed early: " + id);
        }

        LocalDate cutoff = LocalDate.now();
        invoice.setClosedEarlyOn(cutoff);
        invoiceRepository.save(invoice);

        migrateDeadZoneCharges(invoice, cutoff);

        return invoice;
    }

    /**
     * Moves charges dated after {@code cutoff} off an invoice that just closed early, onto
     * whichever invoice they actually belong to now. Routed back through
     * {@link #findOrCreateInvoiceForCharge}, which already knows to skip past {@code invoice}
     * itself now that its {@code closedEarlyOn} is set — the same redirect a brand-new charge
     * would get.
     */
    private void migrateDeadZoneCharges(Invoice invoice, LocalDate cutoff) {
        List<Transaction> deadZone = transactionRepository.findChargesByInvoiceIdAfter(invoice.getId(), cutoff);

        if (deadZone.isEmpty()) {
            return;
        }

        CreditCard card = invoice.getCreditCard();

        for (Transaction charge : deadZone) {
            charge.setInvoice(findOrCreateInvoiceForCharge(card, charge.getTransactionDate()));
        }

        transactionRepository.saveAll(deadZone);

        recomputeTotal(invoice.getId());
        deadZone.stream().map(charge -> charge.getInvoice().getId()).distinct().forEach(this::recomputeTotal);
    }

    private Category adjustmentCategory(UUID ownerId) {
        return systemCategory(ownerId, SystemCategoryKey.ADJUSTMENT, CategoryType.EXPENSE);
    }

    private Category systemCategory(UUID ownerId, SystemCategoryKey key, CategoryType type) {
        return categoryRepository
                .findByOwnerIdAndSystemKeyAndType(ownerId, key, type)
                .orElseThrow(() -> new NotFoundException(key + " category is missing for owner: " + ownerId));
    }

    /**
     * Undoes {@link #pay}, settlement included.
     *
     * The transaction is deleted rather than reversed with an opposing entry: the payment is
     * derived from the invoice, not a fact about the account in its own right, so a user
     * correcting a misclick should not be left with two rows explaining it.
     *
     * {@code paidAt} is simply cleared, even for a period that closed long ago; {@link
     * #derivedStatus} re-derives {@code CLOSED} on read, so it corrects itself.
     */
    public Invoice unpay(UUID id, UUID ownerId) {
        log.info("Reversing payment on invoice {} owner={}", id, ownerId);

        Invoice invoice = findById(id, ownerId);

        if (invoice.getPaidAt() == null) {
            return invoice;
        }

        transactionRepository
                .findByInvoiceIdAndKind(invoice.getId(), TransactionKind.INVOICE_PAYMENT)
                .ifPresent(transactionRepository::delete);

        invoice.setPaidAt(null);

        return invoiceRepository.save(invoice);
    }

    /**
     * Undoes {@link #closeEarly}: clears the override so status and charge routing revert to the
     * ordinary date-driven rules.
     *
     * Charges already migrated to the next invoice when the early close happened are NOT pulled
     * back. {@link TransactionService#update} already never moves a charge across invoices when
     * its date is edited; this is the same limitation, accepted for the same reason — nothing
     * downstream knows how to undo a move that then cascaded, so reopening undoes the status, not
     * every consequence already drawn from it.
     */
    public Invoice reopen(UUID id, UUID ownerId) {
        log.info("Reopening early-closed invoice {} owner={}", id, ownerId);

        Invoice invoice = findById(id, ownerId);

        if (invoice.getClosedEarlyOn() == null || invoice.getPaidAt() != null) {
            return invoice;
        }

        invoice.setClosedEarlyOn(null);

        return invoiceRepository.save(invoice);
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
        log.info("Deleting invoice {} and its charges owner={}", id, ownerId);

        deleteWithCharges(findById(id, ownerId));
    }

    private void deleteWithCharges(Invoice invoice) {
        transactionRepository.deleteAll(transactionRepository.findAllByInvoiceId(invoice.getId()));
        transactionRepository.flush();

        invoiceRepository.delete(invoice);
    }
}
