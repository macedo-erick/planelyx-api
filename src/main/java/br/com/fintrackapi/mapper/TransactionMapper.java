package br.com.fintrackapi.mapper;

import static java.util.Objects.nonNull;

import br.com.fintrackapi.domain.Transaction;
import br.com.fintrackapi.dto.TransactionResponse;

public final class TransactionMapper {

    private TransactionMapper() {}

    public static TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getKind(),
                nonNull(transaction.getBankAccount())
                        ? transaction.getBankAccount().getId()
                        : null,
                nonNull(transaction.getCreditCard())
                        ? transaction.getCreditCard().getId()
                        : null,
                transaction.getCategory().getId(),
                nonNull(transaction.getInvoice()) ? transaction.getInvoice().getId() : null,
                nonNull(transaction.getTemplate()) ? transaction.getTemplate().getId() : null,
                transaction.getInstallmentNumber(),
                transaction.getAmount(),
                transaction.getTransactionDate(),
                transaction.getDescription(),
                transaction.isPaid(),
                transaction.getCreatedAt());
    }
}
