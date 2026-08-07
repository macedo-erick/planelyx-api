package com.planelyx.api.mapper;

import static java.util.Optional.ofNullable;

import com.planelyx.api.domain.BankAccount;
import com.planelyx.api.domain.CreditCard;
import com.planelyx.api.domain.Invoice;
import com.planelyx.api.domain.Transaction;
import com.planelyx.api.domain.TransactionTemplate;
import com.planelyx.api.dto.TransactionResponse;

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
                transaction.getPurchaseDate(),
                transaction.getDescription(),
                transaction.isPaid(),
                transaction.getCreatedAt());
    }
}
