package com.planelyx.api.mapper;

import com.planelyx.api.domain.BankAccount;
import com.planelyx.api.dto.BankAccountResponse;

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
