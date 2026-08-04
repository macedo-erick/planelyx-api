package br.com.planelyxapi.dto;

import br.com.planelyxapi.domain.enums.TransactionKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionRequest(
        @NotNull TransactionKind kind,
        UUID bankAccountId,
        UUID creditCardId,
        @NotNull UUID categoryId,
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate transactionDate,
        @NotBlank String description) {}
