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
                // A settlement moves the balance but is not spending — the charges it pays off
                // were already counted. Left in, it would report the same money twice and put
                // this summary at odds with the dashboard.
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
                // Nothing posted directly is spread over time, so the entry is the purchase.
                .purchaseDate(request.transactionDate())
                .description(request.description())
                .paid(true);

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

        // Collected before the delete: reading getInvoice() off a removed entity afterwards is
        // not safe, and the totals still have to be recomputed.
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

        // Anything reaching forward must also stop the generator, or the monthly top-up job
        // simply recreates what was just removed.
        if (scope != TransactionScope.SINGLE && nonNull(target.getTemplate())) {
            transactionTemplateService.deactivate(target.getTemplate().getId(), ownerId);
        }
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
