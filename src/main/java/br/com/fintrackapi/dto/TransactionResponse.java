package br.com.fintrackapi.dto;

import br.com.fintrackapi.domain.enums.TransactionKind;
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
        BigDecimal amount,
        LocalDate transactionDate,
        String description,
        boolean paid,
        Instant createdAt) {}
