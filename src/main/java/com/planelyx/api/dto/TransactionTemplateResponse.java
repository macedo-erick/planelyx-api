package com.planelyx.api.dto;

import com.planelyx.api.domain.enums.IntervalUnit;
import com.planelyx.api.domain.enums.RecurrenceType;
import com.planelyx.api.domain.enums.TransactionKind;
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
