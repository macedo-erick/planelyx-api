package com.planelyx.api.repository;

import com.planelyx.api.domain.Investment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentRepository extends JpaRepository<Investment, UUID> {

    List<Investment> findAllByOwnerId(UUID ownerId);

    Optional<Investment> findByIdAndOwnerId(UUID id, UUID ownerId);
}
