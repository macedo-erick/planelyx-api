package br.com.planelyxapi.mapper;

import static java.util.Optional.ofNullable;

import br.com.planelyxapi.domain.BankAccount;
import br.com.planelyxapi.domain.CreditCard;
import br.com.planelyxapi.domain.Invoice;
import br.com.planelyxapi.domain.Transaction;
import br.com.planelyxapi.domain.TransactionTemplate;
import br.com.planelyxapi.dto.TransactionResponse;

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
