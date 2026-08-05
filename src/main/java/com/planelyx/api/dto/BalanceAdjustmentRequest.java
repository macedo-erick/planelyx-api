package com.planelyx.api.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the balance should read after the adjustment, not the amount to move.
 *
 * The difference against the balance on {@code transactionDate} becomes an adjustment
 * transaction, so the caller never has to work out a delta itself.
 */
public record BalanceAdjustmentRequest(
        @NotNull BigDecimal targetBalance,
        /** Absent means today. The balance it corrects is the one as of this date. */
        LocalDate transactionDate) {}
