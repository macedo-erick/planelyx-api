package br.com.fintrackapi.dto;

import br.com.fintrackapi.domain.enums.RecurrenceType;
import br.com.fintrackapi.domain.enums.TransactionKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionTemplateRequest(
        @NotNull TransactionKind kind,
        UUID bankAccountId,
        UUID creditCardId,
        @NotNull UUID categoryId,
        @NotBlank String description,
        @NotNull @Positive BigDecimal totalAmount,
        @NotNull RecurrenceType recurrenceType,
        @NotNull LocalDate startDate,
        Integer totalOccurrences) {}
