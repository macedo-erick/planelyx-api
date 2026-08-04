package br.com.planelyxapi.repository;

import br.com.planelyxapi.domain.Invoice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByCreditCardIdAndBillingPeriodStart(UUID creditCardId, LocalDate billingPeriodStart);

    List<Invoice> findAllByCreditCardId(UUID creditCardId);

    List<Invoice> findAllByCreditCardOwnerId(UUID ownerId);

    Optional<Invoice> findByIdAndCreditCardOwnerId(UUID id, UUID ownerId);

    /**
     * How much of a card's limit is still committed. The stored status is only ever PAID once the
     * invoice has been settled — OPEN vs CLOSED is derived from the date at read time — so filtering
     * on the column here is exactly "not yet paid".
     */
    @Query(
            "select coalesce(sum(i.totalAmount), 0) from Invoice i "
                    + "where i.creditCard.id = :creditCardId and i.status <> br.com.planelyxapi.domain.enums.InvoiceStatus.PAID")
    BigDecimal sumUnpaidTotalByCreditCardId(UUID creditCardId);

    /** The same figure for every card an owner holds, so the list endpoint stays at one query. */
    @Query("select i.creditCard.id as creditCardId, sum(i.totalAmount) as total from Invoice i "
            + "where i.creditCard.ownerId = :ownerId and i.status <> br.com.planelyxapi.domain.enums.InvoiceStatus.PAID "
            + "group by i.creditCard.id")
    List<CardTotal> sumUnpaidTotalsByOwnerId(UUID ownerId);

    interface CardTotal {
        UUID getCreditCardId();

        BigDecimal getTotal();
    }
}
