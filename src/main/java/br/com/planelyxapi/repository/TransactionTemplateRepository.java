package br.com.planelyxapi.repository;

import br.com.planelyxapi.domain.TransactionTemplate;
import br.com.planelyxapi.domain.enums.RecurrenceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionTemplateRepository extends JpaRepository<TransactionTemplate, UUID> {

    List<TransactionTemplate> findAllByOwnerId(UUID ownerId);

    Optional<TransactionTemplate> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<TransactionTemplate> findAllByActiveTrueAndRecurrenceType(RecurrenceType recurrenceType);
}
