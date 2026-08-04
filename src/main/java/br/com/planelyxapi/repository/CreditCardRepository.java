package br.com.planelyxapi.repository;

import br.com.planelyxapi.domain.CreditCard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditCardRepository extends JpaRepository<CreditCard, UUID> {

    List<CreditCard> findAllByOwnerId(UUID ownerId);

    Optional<CreditCard> findByIdAndOwnerId(UUID id, UUID ownerId);
}
