package com.planelyx.api.dto;

import com.planelyx.api.domain.enums.TransactionKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param paid whether the entry is already settled, for an account debit that the caller has an
 *     opinion about — filing a bill you have not paid yet, or recording one you have. Null leaves
 *     it to the server, which reads it off the date: a debit dated ahead has not happened yet,
 *     anything on or before today is being recorded after the fact. Ignored on any other kind. A
 *     card charge is settled through its invoice rather than one at a time, and income is not a
 *     bill, so both are always paid.
 */
public record TransactionRequest(
        @NotNull TransactionKind kind,
        UUID bankAccountId,
        UUID creditCardId,
        @NotNull UUID categoryId,
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate transactionDate,
        @NotBlank String description,
        Boolean paid) {

    /**
     * Without an opinion on {@code paid}, which is what most callers have.
     *
     * Spelt out rather than made every caller pass a null they do not mean — the field was added
     * long after this record, and only one screen has anything to say about it.
     */
    public TransactionRequest(
            TransactionKind kind,
            UUID bankAccountId,
            UUID creditCardId,
            UUID categoryId,
            BigDecimal amount,
            LocalDate transactionDate,
            String description) {
        this(kind, bankAccountId, creditCardId, categoryId, amount, transactionDate, description, null);
    }
}
