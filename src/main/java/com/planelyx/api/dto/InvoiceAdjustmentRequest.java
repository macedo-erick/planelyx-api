package com.planelyx.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * What the invoice should total after the adjustment, not the amount to add.
 *
 * The difference against the current total becomes an adjustment charge on the invoice, so the
 * caller never has to work out a delta itself.
 */
public record InvoiceAdjustmentRequest(
        @NotNull @PositiveOrZero BigDecimal targetAmount,
        /**
         * What the resulting charge should be called. Absent falls back to English: the API holds no
         * translations, so only a caller that knows the user's language can name it properly.
         */
        @Size(max = 255) String description) {}
