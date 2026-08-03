package br.com.fintrackapi.service;

import br.com.fintrackapi.domain.BankAccount;
import br.com.fintrackapi.dto.BankAccountRequest;
import br.com.fintrackapi.exception.NotFoundException;
import br.com.fintrackapi.repository.BankAccountRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;

    public List<BankAccount> findAll(UUID ownerId) {
        return bankAccountRepository.findAllByOwnerId(ownerId);
    }

    public BankAccount findById(UUID id, UUID ownerId) {
        return bankAccountRepository
                .findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Bank account not found: " + id));
    }

    public BankAccount create(BankAccountRequest request, UUID ownerId) {
        BankAccount account = BankAccount.builder()
                .ownerId(ownerId)
                .name(request.name())
                .bankName(request.bankName())
                .accountType(request.accountType())
                .initialBalance(request.initialBalance())
                .currency(request.currency())
                .active(true)
                .build();

        return bankAccountRepository.save(account);
    }

    public BankAccount update(UUID id, BankAccountRequest request, UUID ownerId) {
        BankAccount account = findById(id, ownerId);

        account.setName(request.name());
        account.setBankName(request.bankName());
        account.setAccountType(request.accountType());
        account.setInitialBalance(request.initialBalance());
        account.setCurrency(request.currency());

        return bankAccountRepository.save(account);
    }

    public void delete(UUID id, UUID ownerId) {
        BankAccount account = findById(id, ownerId);
        
        bankAccountRepository.delete(account);
    }
}
