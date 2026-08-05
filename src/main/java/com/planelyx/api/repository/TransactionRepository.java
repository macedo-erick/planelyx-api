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
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    List<Transaction> findAllByOwnerIdAndTransactionDateBetween(UUID ownerId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = "template")
    Optional<Transaction> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** Unpaged — for internal callers that need the whole invoice, such as total recomputation. */
    @EntityGraph(attributePaths = "template")
    List<Transaction> findAllByInvoiceId(UUID invoiceId);

    @EntityGraph(attributePaths = "template")
    Page<Transaction> findAllByInvoiceId(UUID invoiceId, Pageable pageable);

    List<Transaction> findAllByTemplateId(UUID templateId);

    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.invoice.id = :invoiceId")
    BigDecimal sumAmountByInvoiceId(UUID invoiceId);

    /**
     * Account movement per kind, cumulative up to {@code asOf}.
     *
     * Cumulative rather than per-month because a balance is a running total: the figure for any
     * month is everything that has happened up to the end of it. Card charges are excluded —
     * they hit the invoice, not the account, until the invoice is paid.
     */
    @Query("select t.bankAccount.id as bankAccountId, t.kind as kind, coalesce(sum(t.amount), 0) as total "
            + "from Transaction t "
            + "where t.ownerId = :ownerId and t.bankAccount is not null and t.transactionDate <= :asOf "
            + "group by t.bankAccount.id, t.kind")
    List<AccountKindTotal> sumByAccountAndKindAsOf(UUID ownerId, LocalDate asOf);

    @Query("select t.kind as kind, coalesce(sum(t.amount), 0) as total from Transaction t "
            + "where t.ownerId = :ownerId and t.transactionDate between :from and :to "
            + "group by t.kind")
    List<KindTotal> sumByKindBetween(UUID ownerId, LocalDate from, LocalDate to);

    @Query("select t.category.id as categoryId, coalesce(sum(t.amount), 0) as total from Transaction t "
            + "where t.ownerId = :ownerId and t.transactionDate between :from and :to and t.kind <> :excluded "
            + "group by t.category.id "
            + "order by sum(t.amount) desc")
    List<CategoryTotal> sumByCategoryBetweenExcludingKind(
            UUID ownerId, LocalDate from, LocalDate to, TransactionKind excluded);

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
