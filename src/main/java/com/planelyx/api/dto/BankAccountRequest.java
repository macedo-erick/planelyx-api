package com.planelyx.api.dto;

import com.planelyx.api.domain.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record BankAccountRequest(
        @NotBlank String name,
        @NotBlank String bankName,
        @NotNull AccountType accountType,
        @NotNull BigDecimal initialBalance,
        @NotBlank String currency) {}
