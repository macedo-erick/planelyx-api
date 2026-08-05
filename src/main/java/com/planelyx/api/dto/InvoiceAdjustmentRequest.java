package com.planelyx.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * What the invoice should total after the adjustment, not the amount to add.
 *
 * The difference against the current total becomes an adjustment charge on the invoice, so the
 * caller never has to work out a delta itself.
 */
public record InvoiceAdjustmentRequest(
        @NotNull @PositiveOrZero BigDecimal targetAmount) {}
