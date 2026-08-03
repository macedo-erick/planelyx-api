package br.com.fintrackapi.repository;

import br.com.fintrackapi.domain.Invoice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByCreditCardIdAndBillingPeriodStart(UUID creditCardId, LocalDate billingPeriodStart);

    List<Invoice> findAllByCreditCardId(UUID creditCardId);

    List<Invoice> findAllByCreditCardOwnerId(UUID ownerId);

    Optional<Invoice> findByIdAndCreditCardOwnerId(UUID id, UUID ownerId);
}
