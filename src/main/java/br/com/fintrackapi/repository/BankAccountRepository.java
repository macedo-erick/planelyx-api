package br.com.fintrackapi.repository;

import br.com.fintrackapi.domain.BankAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    List<BankAccount> findAllByOwnerId(UUID ownerId);

    Optional<BankAccount> findByIdAndOwnerId(UUID id, UUID ownerId);
}
