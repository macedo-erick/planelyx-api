package com.planelyx.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the balance should read after the adjustment, not the amount to move.
 *
 * The difference against the balance on {@code transactionDate} becomes an adjustment
 * transaction, so the caller never has to work out a delta itself. That date is the one whose
 * balance is corrected, and absent it means today.
 *
 * An absent description falls back to English: the API holds no translations, so only a caller
 * that knows the user's language can name the resulting transaction properly.
 */
public record BalanceAdjustmentRequest(
        @NotNull BigDecimal targetBalance,
        LocalDate transactionDate,
        @Size(max = 255) String description) {}
