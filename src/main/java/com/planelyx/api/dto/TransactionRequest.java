package com.planelyx.api.dto;

import com.planelyx.api.domain.enums.TransactionKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A transaction to write.
 *
 * {@code paid} is for an account debit the caller has an opinion about — filing a bill not paid
 * yet, or recording one that has been. Left null the server reads it off the date: a debit dated
 * ahead has not happened yet, anything on or before today is being recorded after the fact. It is
 * ignored on any other kind, since a card charge is settled through its invoice rather than one at
 * a time, and income is not a bill.
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
