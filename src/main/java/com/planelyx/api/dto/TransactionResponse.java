package com.planelyx.api.dto;

import com.planelyx.api.domain.enums.TransactionKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param transactionDate the day this entry hits the ledger — for an installment, the month it
 *     falls in rather than the day anything was bought
 * @param purchaseDate the day the purchase was actually made. The same as {@code transactionDate}
 *     for anything bought outright; for an installment it is the start date of the template it
 *     came from, since occurrences are generated a month apart and a sofa bought on 25 January
 *     is dated 25 March in the March invoice.
 */
public record TransactionResponse(
        UUID id,
        TransactionKind kind,
        UUID bankAccountId,
        UUID creditCardId,
        UUID categoryId,
        UUID invoiceId,
        UUID templateId,
        Integer installmentNumber,
        Integer totalInstallments,
        BigDecimal amount,
        LocalDate transactionDate,
        LocalDate purchaseDate,
        String description,
        boolean paid,
        Instant createdAt) {}
