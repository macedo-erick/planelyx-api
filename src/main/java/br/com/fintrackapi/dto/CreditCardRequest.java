package br.com.fintrackapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreditCardRequest(
        @NotNull UUID bankAccountId,
        @NotBlank String name,
        @NotBlank String brand,
        @NotNull BigDecimal creditLimit,
        @Min(1) @Max(31) int closingDay,
        @Min(1) @Max(31) int dueDay) {}
