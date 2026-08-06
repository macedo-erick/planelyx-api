package com.planelyx.api.mapper;

import static java.util.Optional.ofNullable;

import com.planelyx.api.domain.BankAccount;
import com.planelyx.api.domain.CreditCard;
import com.planelyx.api.domain.Invoice;
import com.planelyx.api.domain.Transaction;
import com.planelyx.api.domain.TransactionTemplate;
import com.planelyx.api.domain.enums.RecurrenceType;
import com.planelyx.api.dto.TransactionResponse;
import java.time.LocalDate;

public final class TransactionMapper {

    private TransactionMapper() {}

    public static TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getKind(),
                ofNullable(transaction.getBankAccount()).map(BankAccount::getId).orElse(null),
                ofNullable(transaction.getCreditCard()).map(CreditCard::getId).orElse(null),
                transaction.getCategory().getId(),
                ofNullable(transaction.getInvoice()).map(Invoice::getId).orElse(null),
                ofNullable(transaction.getTemplate())
                        .map(TransactionTemplate::getId)
                        .orElse(null),
                transaction.getInstallmentNumber(),
                ofNullable(transaction.getTemplate())
                        .map(TransactionTemplate::getTotalOccurrences)
                        .orElse(null),
                transaction.getAmount(),
                transaction.getTransactionDate(),
                purchaseDate(transaction),
                transaction.getDescription(),
                transaction.isPaid(),
                transaction.getCreatedAt());
    }

    /**
     * The template's start date for an installment, the transaction's own date otherwise.
     *
     * Restricted to installments on purpose. Every recurring rule has a start date, but on a
     * monthly subscription that date is when the rule began, not when this month's charge was
     * bought — only an installment is one purchase spread across several entries.
     */
    private static LocalDate purchaseDate(Transaction transaction) {
        return ofNullable(transaction.getTemplate())
                .filter(template -> template.getRecurrenceType() == RecurrenceType.INSTALLMENT)
                .map(TransactionTemplate::getStartDate)
                .orElseGet(transaction::getTransactionDate);
    }
}
