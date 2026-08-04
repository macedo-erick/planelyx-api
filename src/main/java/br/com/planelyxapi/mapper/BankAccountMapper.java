package br.com.planelyxapi.mapper;

import br.com.planelyxapi.domain.BankAccount;
import br.com.planelyxapi.dto.BankAccountResponse;

public final class BankAccountMapper {

    private BankAccountMapper() {}

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
