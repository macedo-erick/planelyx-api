package com.planelyx.api.dto;

import com.planelyx.api.domain.enums.TransactionKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        TransactionKind kind,
        UUID bankAccountId,
        UUID creditCardId,
        UUID categoryId,
        UUID invoiceId,
        UUID templateId,
        Integer installmentNumber,
        Integer totalInstallments,
        BigDecimal amount,
        LocalDate transactionDate,
        String description,
        boolean paid,
        Instant createdAt) {}
