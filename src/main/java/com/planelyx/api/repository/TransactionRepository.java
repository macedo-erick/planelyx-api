package com.planelyx.api.repository;

import com.planelyx.api.domain.Transaction;
import com.planelyx.api.domain.enums.TransactionKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    List<Transaction> findAllByOwnerIdAndTransactionDateBetween(UUID ownerId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = "template")
    Optional<Transaction> findByIdAndOwnerId(UUID id, UUID ownerId);

    /**
     * Everything pointing at the invoice, settlement included.
     *
     * For callers that must account for every row, such as deleting the invoice — nothing may
     * still reference it afterwards and the foreign key has no cascade. Use
     * {@link #findChargesByInvoiceId} to show an invoice's contents instead.
     */
    @EntityGraph(attributePaths = "template")
    List<Transaction> findAllByInvoiceId(UUID invoiceId);

    /** What the invoice is made of. The settlement pays the invoice; it is not on it. */
    @EntityGraph(attributePaths = "template")
    @Query("select t from Transaction t where t.invoice.id = :invoiceId "
            + "and t.kind <> com.planelyx.api.domain.enums.TransactionKind.INVOICE_PAYMENT")
    List<Transaction> findChargesByInvoiceId(UUID invoiceId);

    @EntityGraph(attributePaths = "template")
    @Query("select t from Transaction t where t.invoice.id = :invoiceId "
            + "and t.kind <> com.planelyx.api.domain.enums.TransactionKind.INVOICE_PAYMENT")
    Page<Transaction> findChargesByInvoiceId(UUID invoiceId, Pageable pageable);

    List<Transaction> findAllByTemplateId(UUID templateId);

    void deleteAllByCreditCardId(UUID creditCardId);

    void deleteAllByBankAccountId(UUID bankAccountId);

    /**
     * Everything filed against any of a card's invoices, whichever account it names.
     *
     * Reaches the settlements that {@link #deleteAllByCreditCardId} cannot: a settlement names the
     * account it was paid from, so it is not the card's row by that measure, but it does point at
     * the card's invoice and has to go with it.
     */
    @Modifying
    @Query("delete from Transaction t where t.invoice.id in "
            + "(select i.id from Invoice i where i.creditCard.id = :creditCardId)")
    void deleteAllByInvoiceCreditCardId(UUID creditCardId);

    Optional<Transaction> findByInvoiceIdAndKind(UUID invoiceId, TransactionKind kind);

    /**
     * What the invoice comes to — its charges only.
     *
     * The settlement posted when the invoice is paid also points at it, and folding that into the
     * sum would double the total the moment it was paid.
     */
    @Query("select coalesce(sum(t.amount), 0) from Transaction t "
            + "where t.invoice.id = :invoiceId "
            + "and t.kind <> com.planelyx.api.domain.enums.TransactionKind.INVOICE_PAYMENT")
    BigDecimal sumAmountByInvoiceId(UUID invoiceId);

    /**
     * Account movement per kind, cumulative up to {@code asOf}.
     *
     * Cumulative rather than per-month because a balance is a running total: the figure for any
     * month is everything that has happened up to the end of it. Card charges are excluded by
     * {@code bankAccount is not null} — they hit the invoice, not the account. The settlement
     * posted when that invoice is paid does name an account, so it is what finally takes the
     * money out.
     */
    @Query("select t.bankAccount.id as bankAccountId, t.kind as kind, coalesce(sum(t.amount), 0) as total "
            + "from Transaction t "
            + "where t.ownerId = :ownerId and t.bankAccount is not null and t.transactionDate <= :asOf "
            + "group by t.bankAccount.id, t.kind")
    List<AccountKindTotal> sumByAccountAndKindAsOf(UUID ownerId, LocalDate asOf);

    /**
     * Movement per kind for one month, dated by when the money is actually owed.
     *
     * A card charge belongs to the month its invoice falls due, not the month it was bought: a
     * purchase made after the card has closed is settled a month later, and calling it this
     * month's spending puts it on a different calendar from every figure that deducts the
     * invoice. Account movements have no invoice and keep their own date.
     *
     * The join has to be explicit. Navigating {@code t.invoice.dueDate} implicitly makes
     * Hibernate emit an inner join, which would silently drop every account transaction.
     *
     * Settlements are left out — see {@link TransactionKind#INVOICE_PAYMENT}.
     */
    @Query("select t.kind as kind, coalesce(sum(t.amount), 0) as total "
            + "from Transaction t left join t.invoice i "
            + "where t.ownerId = :ownerId "
            + "and t.kind <> com.planelyx.api.domain.enums.TransactionKind.INVOICE_PAYMENT "
            + "and coalesce(i.dueDate, t.transactionDate) between :from and :to "
            + "group by t.kind")
    List<KindTotal> sumByKindInMonthDue(UUID ownerId, LocalDate from, LocalDate to);

    /** The same window and the same dating rule, broken down by category. Income is not spending. */
    @Query("select t.category.id as categoryId, coalesce(sum(t.amount), 0) as total "
            + "from Transaction t left join t.invoice i "
            + "where t.ownerId = :ownerId "
            + "and t.kind <> com.planelyx.api.domain.enums.TransactionKind.ACCOUNT_CREDIT "
            + "and t.kind <> com.planelyx.api.domain.enums.TransactionKind.INVOICE_PAYMENT "
            + "and coalesce(i.dueDate, t.transactionDate) between :from and :to "
            + "group by t.category.id "
            + "order by sum(t.amount) desc")
    List<CategoryTotal> sumByCategoryInMonthDue(UUID ownerId, LocalDate from, LocalDate to);

    interface AccountKindTotal {
        UUID getBankAccountId();

        TransactionKind getKind();

        BigDecimal getTotal();
    }

    interface KindTotal {
        TransactionKind getKind();

        BigDecimal getTotal();
    }

    interface CategoryTotal {
        UUID getCategoryId();

        BigDecimal getTotal();
    }
}
