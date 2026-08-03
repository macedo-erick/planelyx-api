package br.com.fintrackapi.mapper;

import br.com.fintrackapi.domain.BankAccount;
import br.com.fintrackapi.dto.BankAccountResponse;

public final class BankAccountMapper {

    private BankAccountMapper() {
    }

    public static BankAccountResponse toResponse(BankAccount account) {
        return new BankAccountResponse(
                account.getId(),
                account.getName(),
                account.getBankName(),
                account.getAccountType(),
                account.getInitialBalance(),
                account.getCurrency(),
                account.isActive(),
                account.getCreatedAt());
    }
}
