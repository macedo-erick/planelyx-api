package com.planelyx.api.service;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.planelyx.api.domain.BankAccount;
import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.CreditCard;
import com.planelyx.api.domain.Invoice;
import com.planelyx.api.domain.Transaction;
import com.planelyx.api.domain.enums.RecurrenceType;
import com.planelyx.api.domain.enums.TransactionKind;
import com.planelyx.api.domain.enums.TransactionScope;
import com.planelyx.api.dto.TransactionRequest;
import com.planelyx.api.dto.TransactionSummaryResponse;
import com.planelyx.api.dto.TransactionUpdateRequest;
import com.planelyx.api.exception.NotFoundException;
import com.planelyx.api.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class TransactionService {

    /**
     * Most recent purchase first, with {@code createdAt} as a tiebreak.
     *
     * By purchase date and not by {@code transactionDate}: an installment's later occurrences are
     * dated months after anything was bought, so ordering by the entry's own date scatters a
     * January purchase through the middle of August. Sorted this way it settles below August's own
     * entries, which is how an invoice already reads.
     *
     * The tiebreak is not cosmetic. Without a total ordering, rows sharing a date can come back in
     * a different order per request, which makes a row appear on two pages or on none while paging.
     */
    public static final Sort NEWEST_FIRST =
            Sort.by(Sort.Direction.DESC, "purchaseDate").and(Sort.by(Sort.Direction.DESC, "createdAt"));

    private final TransactionRepository transactionRepository;
    private final BankAccountService bankAccountService;
    private final CreditCardService creditCardService;
    private final CategoryService categoryService;
    private final InvoiceService invoiceService;
    private final TransactionTemplateService transactionTemplateService;
    private final EntityManager entityManager;

    /**
     * Whether an entry is already settled the moment it is written.
     *
     * Only an account debit can be a bill: a card charge is settled by its invoice rather than one
     * at a time, and income is nothing to remember to pay. Among those, the date decides. A debit
     * dated today or earlier is being recorded after the fact — it happened, so it is paid. One
     * dated ahead has not happened yet, which is exactly what a reminder is for.
     *
     * Package-private and static so {@link TemplateOccurrenceGenerator} applies the same rule to
     * the occurrences it materialises; a recurring rule generates months of them at once, and most
     * of those are in the future.
     */
    static boolean settledOnCreation(TransactionKind kind, LocalDate date) {
        return kind != TransactionKind.ACCOUNT_DEBIT || !date.isAfter(LocalDate.now());
    }

    /**
     * The same, letting the caller override it — filing a bill due today that has not been paid, or
     * recording one that has.
     *
     * Only on an account debit. Anything else is settled the moment it is written whatever the
     * request says, so honouring {@code paid} there would let a client mark a card charge unpaid
     * and have it sit on a reminder it can never be taken off, its invoice being what settles it.
     */
    private static boolean settledOnCreation(TransactionRequest request) {
        if (request.kind() == TransactionKind.ACCOUNT_DEBIT && nonNull(request.paid())) {
            return request.paid();
        }

        return settledOnCreation(request.kind(), request.transactionDate());
    }

    public Page<Transaction> findAll(
            UUID ownerId,
            UUID bankAccountId,
            UUID creditCardId,
            UUID categoryId,
            TransactionKind kind,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {
        Specification<Transaction> spec = filterSpec(ownerId, bankAccountId, creditCardId, categoryId, kind, from, to)
                .and(fetchTemplate());

        return transactionRepository.findAll(spec, pageable);
    }

    /**
     * Income, outflow and net across the whole filtered selection, not just a page.
     *
     * Grouped in the database rather than by loading rows: the caller wants four numbers, and
     * the row count behind a loose filter is unbounded.
     */
    public TransactionSummaryResponse summarize(
            UUID ownerId,
            UUID bankAccountId,
            UUID creditCardId,
            UUID categoryId,
            TransactionKind kind,
            LocalDate from,
            LocalDate to) {
        Specification<Transaction> spec = filterSpec(ownerId, bankAccountId, creditCardId, categoryId, kind, from, to);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Transaction> root = query.from(Transaction.class);

        query.select(cb.tuple(root.get("kind"), cb.sum(root.get("amount")), cb.count(root)));
        query.where(spec.toPredicate(root, query, cb));
        query.groupBy(root.get("kind"));

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        long count = 0;

        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            TransactionKind rowKind = row.get(0, TransactionKind.class);
            BigDecimal total = row.get(1, BigDecimal.class);
            count += row.get(2, Long.class);

            if (rowKind == TransactionKind.ACCOUNT_CREDIT) {
                income = income.add(total);
            } else if (rowKind != TransactionKind.INVOICE_PAYMENT) {
                expense = expense.add(total);
            }
        }

        return new TransactionSummaryResponse(income, expense, income.subtract(expense), count);
    }

    /** Predicates only — reusable for aggregates, which must not carry a fetch join. */
    private Specification<Transaction> filterSpec(
            UUID ownerId,
            UUID bankAccountId,
            UUID creditCardId,
            UUID categoryId,
            TransactionKind kind,
            LocalDate from,
            LocalDate to) {
        Specification<Transaction> spec = (root, query, cb) -> cb.equal(root.get("ownerId"), ownerId);

        if (nonNull(bankAccountId)) {
            spec = spec.and(
                    (root, query, cb) -> cb.equal(root.get("bankAccount").get("id"), bankAccountId));
        }

        if (nonNull(creditCardId)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("creditCard").get("id"), creditCardId));
        }

        if (nonNull(categoryId)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }

        if (nonNull(kind)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("kind"), kind));
        }

        if (nonNull(from)) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("transactionDate"), from));
        }

        if (nonNull(to)) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("transactionDate"), to));
        }

        return spec;
    }

    /**
     * Eager-loads the lazy template so the mapper can still read it after the session closes.
     *
     * Skipped for the count query that a paged lookup issues alongside the data query: a fetch
     * join is illegal there and Hibernate fails while building it.
     */
    private Specification<Transaction> fetchTemplate() {
        return (root, query, cb) -> {
            if (nonNull(query) && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("template", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }

    public Transaction findById(UUID id, UUID ownerId) {
        return transactionRepository
                .findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + id));
    }

    public Transaction create(TransactionRequest request, UUID ownerId) {
        rejectSystemCategory(categoryService.findById(request.categoryId(), ownerId));

        return createCorrection(request, ownerId);
    }

    /**
     * The same write without the system-category guard, for the corrections the app posts on its
     * own behalf — {@link BalanceAdjustmentService} routes through here so an adjustment still gets
     * the ordinary validation, invoice rules and paid flag while legitimately wearing the
     * adjustment category.
     *
     * The purchase date falls back to the entry's own date, which is what it is for anything bought
     * outright and what every caller filing by hand means. A caller reading a statement is the one
     * that can tell them apart — a purchase late in a cycle posts in the next period — so it may
     * send its own and have it kept.
     */
    Transaction createCorrection(TransactionRequest request, UUID ownerId) {
        TransactionKindValidator.validate(request.kind(), request.bankAccountId(), request.creditCardId());
        Category category = categoryService.findById(request.categoryId(), ownerId);

        Transaction.TransactionBuilder builder = Transaction.builder()
                .ownerId(ownerId)
                .kind(request.kind())
                .category(category)
                .amount(request.amount())
                .transactionDate(request.transactionDate())
                .purchaseDate(Objects.requireNonNullElse(request.purchaseDate(), request.transactionDate()))
                .description(request.description())
                .paid(settledOnCreation(request));

        if (request.kind() == TransactionKind.CARD_CHARGE) {
            CreditCard card = creditCardService.findById(request.creditCardId(), ownerId);
            Transaction saved =
                    transactionRepository.save(builder.creditCard(card).build());
            Invoice invoice = invoiceService.findOrCreateInvoiceForCharge(card, request.transactionDate());

            saved.setInvoice(invoice);
            saved = transactionRepository.save(saved);

            invoiceService.recomputeTotal(invoice.getId());

            return saved;
        }

        BankAccount account = bankAccountService.findById(request.bankAccountId(), ownerId);

        return transactionRepository.save(builder.bankAccount(account).build());
    }

    /**
     * Applies the edit across the requested scope.
     *
     * Category, amount and description are applied to every transaction in scope, but the date
     * is applied only to the one being edited — moving every sibling's date would collapse a
     * monthly series onto a single day.
     *
     * The purchase date follows along only outside an installment, where it is simply the entry's
     * own date. An installment's occurrences share one purchase, so rewriting it from a single row
     * would put the same purchase on two different days.
     */
    public Transaction update(UUID id, TransactionUpdateRequest request, UUID ownerId) {
        Transaction target = findById(id, ownerId);
        rejectDerived(target);

        Category category = categoryService.findById(request.categoryId(), ownerId);
        rejectSystemCategory(category);

        List<Transaction> affected = inScope(target, request.scopeOrDefault());

        for (Transaction transaction : affected) {
            transaction.setCategory(category);
            transaction.setAmount(request.amount());
            transaction.setDescription(request.description());
        }

        target.setTransactionDate(request.transactionDate());

        if (!isInstallment(target)) {
            target.setPurchaseDate(request.transactionDate());
        }

        transactionRepository.saveAll(affected);
        recomputeInvoices(affected);

        return target;
    }

    public void delete(UUID id, UUID ownerId, TransactionScope scope) {
        Transaction target = findById(id, ownerId);
        rejectDerived(target);

        List<Transaction> affected = inScope(target, scope);

        Set<UUID> invoiceIds = affected.stream()
                .map(Transaction::getInvoice)
                .filter(Objects::nonNull)
                .map(Invoice::getId)
                .collect(Collectors.toSet());

        transactionRepository.deleteAll(affected);
        transactionRepository.flush();

        for (UUID invoiceId : invoiceIds) {
            invoiceService.recomputeTotal(invoiceId);
        }

        if (scope != TransactionScope.SINGLE && nonNull(target.getTemplate())) {
            transactionTemplateService.deactivate(target.getTemplate().getId(), ownerId);
        }
    }

    /**
     * Ticks a bill off, or puts it back on the list.
     *
     * This moves no money. The entry already exists and is already inside every balance figure —
     * balances here are a forecast to the end of a month, not a snapshot of today, so a bill dated
     * the 20th is deducted from the 31st's balance whether or not it has been paid. All this flag
     * decides is whether the dashboard still reminds the owner about it.
     *
     * Only an account debit can be ticked off. A card charge is settled through its invoice, and
     * flipping one here would claim it was paid on its own; income is not a bill at all.
     */
    public Transaction markPaid(UUID id, boolean paid, UUID ownerId) {
        Transaction target = findById(id, ownerId);
        rejectDerived(target);

        if (target.getKind() != TransactionKind.ACCOUNT_DEBIT) {
            throw new IllegalArgumentException("Only an account debit can be paid off on its own: " + id);
        }

        target.setPaid(paid);

        return transactionRepository.save(target);
    }

    /**
     * The transactions an operation reaches, newest-scope rules aside.
     *
     * A transaction with no template is a one-off, so scope cannot widen it — returning just
     * that row keeps a stale or over-eager client from touching anything it should not.
     */
    private List<Transaction> inScope(Transaction target, TransactionScope scope) {
        if (scope == TransactionScope.SINGLE || isNull(target.getTemplate())) {
            return List.of(target);
        }

        List<Transaction> siblings =
                transactionRepository.findAllByTemplateId(target.getTemplate().getId());

        if (scope == TransactionScope.ALL) {
            return siblings;
        }

        return siblings.stream()
                .filter(sibling -> !sibling.getTransactionDate().isBefore(target.getTransactionDate()))
                .toList();
    }

    /** One occurrence of a purchase split into parts, rather than an entry standing on its own. */
    private boolean isInstallment(Transaction transaction) {
        return nonNull(transaction.getTemplate())
                && transaction.getTemplate().getRecurrenceType() == RecurrenceType.INSTALLMENT;
    }

    /**
     * The adjustment categories mark a correction the app made, so a user may not file against one
     * directly — a hand-written transaction wearing that label would read as reconciled when
     * nothing was reconciled. Clients keep them out of their pickers; this is the backstop.
     */
    private void rejectSystemCategory(Category category) {
        if (category.isSystem()) {
            throw new IllegalArgumentException("Category is reserved for adjustments: " + category.getId());
        }
    }

    /**
     * A settlement belongs to its invoice, not to the account it appears on.
     *
     * Editing one would put it out of step with the invoice it claims to have paid, and deleting
     * one would leave the invoice marked paid with the money still in the account. Unpaying the
     * invoice is the only way to remove it.
     */
    private void rejectDerived(Transaction transaction) {
        if (transaction.getKind() == TransactionKind.INVOICE_PAYMENT) {
            throw new IllegalArgumentException(
                    "An invoice payment follows its invoice — unpay the invoice instead: " + transaction.getId());
        }
    }

    private void recomputeInvoices(List<Transaction> transactions) {
        transactions.stream()
                .map(Transaction::getInvoice)
                .filter(Objects::nonNull)
                .map(Invoice::getId)
                .collect(Collectors.toSet())
                .forEach(invoiceService::recomputeTotal);
    }
}
