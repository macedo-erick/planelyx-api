package com.planelyx.api.service;

import com.planelyx.api.domain.BankAccount;
import com.planelyx.api.domain.enums.TransactionKind;
import com.planelyx.api.dto.BankAccountRequest;
import com.planelyx.api.exception.NotFoundException;
import com.planelyx.api.repository.BankAccountRepository;
import com.planelyx.api.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;
    private final CascadeDeleteService cascadeDeleteService;

    public List<BankAccount> findAll(UUID ownerId) {
        return bankAccountRepository.findAllByOwnerId(ownerId);
    }

    /**
     * What each account is worth at the close of {@code asOf}: its initial balance, plus every
     * account credit and minus every account debit dated on or before that day.
     *
     * Card charges are deliberately absent — they sit on an invoice and never move an account
     * balance until the invoice is paid. Because installments and recurring occurrences are
     * already materialised as rows, a future {@code asOf} reads as a forecast rather than an
     * estimate.
     *
     * Every account the owner holds appears in the result, including ones with no movement.
     */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> balancesAsOf(UUID ownerId, LocalDate asOf) {
        Map<UUID, BigDecimal> movementByAccount = new HashMap<>();

        for (TransactionRepository.AccountKindTotal row :
                transactionRepository.sumByAccountAndKindAsOf(ownerId, asOf)) {
            BigDecimal signed = row.getKind() == TransactionKind.ACCOUNT_CREDIT
                    ? row.getTotal()
                    : row.getTotal().negate();

            movementByAccount.merge(row.getBankAccountId(), signed, BigDecimal::add);
        }

        Map<UUID, BigDecimal> balances = new HashMap<>();

        for (BankAccount account : findAll(ownerId)) {
            balances.put(
                    account.getId(),
                    account.getInitialBalance().add(movementByAccount.getOrDefault(account.getId(), BigDecimal.ZERO)));
        }

        return balances;
    }

    /** The same figure for one account. */
    @Transactional(readOnly = true)
    public BigDecimal balanceAsOf(UUID id, UUID ownerId, LocalDate asOf) {
        BankAccount account = findById(id, ownerId);

        return balancesAsOf(ownerId, asOf).getOrDefault(account.getId(), account.getInitialBalance());
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

    /** Takes the account's cards, transactions and invoices with it — see {@link CascadeDeleteService}. */
    public void delete(UUID id, UUID ownerId) {
        cascadeDeleteService.deleteBankAccount(findById(id, ownerId));
    }
}
