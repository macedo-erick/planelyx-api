package com.planelyx.api.repository;

import com.planelyx.api.domain.TransactionTemplate;
import com.planelyx.api.domain.enums.RecurrenceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionTemplateRepository extends JpaRepository<TransactionTemplate, UUID> {

    List<TransactionTemplate> findAllByOwnerId(UUID ownerId);

    Optional<TransactionTemplate> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<TransactionTemplate> findAllByActiveTrueAndRecurrenceType(RecurrenceType recurrenceType);
}
