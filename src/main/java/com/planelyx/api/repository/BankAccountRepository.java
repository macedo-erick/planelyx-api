package com.planelyx.api.repository;

import com.planelyx.api.domain.BankAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    List<BankAccount> findAllByOwnerId(UUID ownerId);

    Optional<BankAccount> findByIdAndOwnerId(UUID id, UUID ownerId);
}
