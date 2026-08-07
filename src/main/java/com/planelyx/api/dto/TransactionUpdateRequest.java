package com.planelyx.api.dto;

import com.planelyx.api.domain.enums.TransactionScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A narrower payload than the one that creates a transaction — kind and the account or card it
 * names are immutable once written.
 *
 * An absent scope means SINGLE, which is also what a client predating that field sends.
 */
public record TransactionUpdateRequest(
        @NotNull UUID categoryId,
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate transactionDate,
        @NotBlank String description,
        TransactionScope scope) {

    public TransactionScope scopeOrDefault() {
        return Objects.requireNonNullElse(scope, TransactionScope.SINGLE);
    }
}
