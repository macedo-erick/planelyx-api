package br.com.fintrackapi.mapper;

import static java.util.Optional.ofNullable;

import br.com.fintrackapi.domain.BankAccount;
import br.com.fintrackapi.domain.CreditCard;
import br.com.fintrackapi.domain.Invoice;
import br.com.fintrackapi.domain.Transaction;
import br.com.fintrackapi.domain.TransactionTemplate;
import br.com.fintrackapi.dto.TransactionResponse;

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
                transaction.getDescription(),
                transaction.isPaid(),
                transaction.getCreatedAt());
    }
}
