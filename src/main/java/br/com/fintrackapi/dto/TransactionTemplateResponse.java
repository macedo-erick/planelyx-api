package br.com.fintrackapi.dto;

import br.com.fintrackapi.domain.enums.IntervalUnit;
import br.com.fintrackapi.domain.enums.RecurrenceType;
import br.com.fintrackapi.domain.enums.TransactionKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionTemplateResponse(
        UUID id,
        TransactionKind kind,
        UUID bankAccountId,
        UUID creditCardId,
        UUID categoryId,
        String description,
        BigDecimal totalAmount,
        RecurrenceType recurrenceType,
        IntervalUnit intervalUnit,
        LocalDate startDate,
        Integer totalOccurrences,
        int occurrencesGenerated,
        boolean active,
        Instant createdAt) {}
