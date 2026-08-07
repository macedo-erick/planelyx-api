package com.planelyx.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A card with its limit worked out.
 *
 * {@code usedLimit} is the sum of the card's invoices that have not been paid yet, and {@code
 * availableLimit} is the limit less that — which goes negative once the card is over its limit.
 */
public record CreditCardResponse(
        UUID id,
        UUID bankAccountId,
        String name,
        String brand,
        BigDecimal creditLimit,
        BigDecimal usedLimit,
        BigDecimal availableLimit,
        int closingDay,
        int dueDay,
        boolean active,
        Instant createdAt) {}
