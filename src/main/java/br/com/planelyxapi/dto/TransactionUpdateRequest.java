package br.com.planelyxapi.dto;

import br.com.planelyxapi.domain.enums.TransactionScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record TransactionUpdateRequest(
        @NotNull UUID categoryId,
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate transactionDate,
        @NotBlank String description,
        /** Absent means SINGLE — which is also what a client that predates this field sends. */
        TransactionScope scope) {

    public TransactionScope scopeOrDefault() {
        return Objects.requireNonNullElse(scope, TransactionScope.SINGLE);
    }
}
