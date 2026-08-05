package com.planelyx.api.dto;

import com.planelyx.api.domain.enums.AccountType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BankAccountResponse(
        UUID id,
        String name,
        String bankName,
        AccountType accountType,
        BigDecimal initialBalance,
        String currency,
        boolean active,
        Instant createdAt) {}
