package com.planelyx.api.dto;

import com.planelyx.api.domain.enums.InvestmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * {@code initialBalance} is what the investment was already worth when it was added here, not a
 * contribution — no account is debited for it. An investment opened from scratch sends zero and
 * gets its money through a contribution like everything else.
 */
public record InvestmentRequest(
        @NotBlank String name,
        @NotBlank String institution,
        @NotNull InvestmentType investmentType,
        @NotNull BigDecimal initialBalance,
        @NotBlank @Size(min = 3, max = 3) String currency) {}
