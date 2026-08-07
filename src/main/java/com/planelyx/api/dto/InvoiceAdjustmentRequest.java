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
 *
 * An absent description falls back to English: the API holds no translations, so only a caller
 * that knows the user's language can name that charge properly.
 */
public record InvoiceAdjustmentRequest(
        @NotNull @PositiveOrZero BigDecimal targetAmount,
        @Size(max = 255) String description) {}
