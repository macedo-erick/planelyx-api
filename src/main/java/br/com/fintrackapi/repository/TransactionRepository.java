package br.com.fintrackapi.repository;

import br.com.fintrackapi.domain.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository
        extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    List<Transaction> findAllByOwnerIdAndTransactionDateBetween(UUID ownerId, LocalDate from, LocalDate to);

    Optional<Transaction> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Transaction> findAllByInvoiceId(UUID invoiceId);

    List<Transaction> findAllByTemplateId(UUID templateId);

    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.invoice.id = :invoiceId")
    BigDecimal sumAmountByInvoiceId(UUID invoiceId);
}
