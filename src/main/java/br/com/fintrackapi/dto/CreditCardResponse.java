package br.com.fintrackapi.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreditCardResponse(
        UUID id,
        UUID bankAccountId,
        String name,
        String brand,
        BigDecimal creditLimit,
        int closingDay,
        int dueDay,
        boolean active,
        Instant createdAt) {}
