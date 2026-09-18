package com.planelyx.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Money moving between one account and one investment, in whichever direction the endpoint names.
 * The amount is always positive; the kind carries the direction, as everywhere else in the ledger.
 *
 * An absent date means today, and an absent description falls back to English.
 */
public record InvestmentMovementRequest(
        @NotNull UUID bankAccountId,
        @NotNull @Positive BigDecimal amount,
        LocalDate transactionDate,
        @Size(max = 255) String description) {}
