package br.com.planelyxapi.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreditCardResponse(
        UUID id,
        UUID bankAccountId,
        String name,
        String brand,
        BigDecimal creditLimit,
        /** Sum of the card's invoices that have not been paid yet. */
        BigDecimal usedLimit,
        /** {@code creditLimit - usedLimit}. Goes negative once the card is over its limit. */
        BigDecimal availableLimit,
        int closingDay,
        int dueDay,
        boolean active,
        Instant createdAt) {}
