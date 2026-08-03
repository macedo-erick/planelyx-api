package br.com.fintrackapi.mapper;

import br.com.fintrackapi.domain.Transaction;
import br.com.fintrackapi.dto.TransactionResponse;

public final class TransactionMapper {

    private TransactionMapper() {
    }

    public static TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getKind(),
                transaction.getBankAccount() != null ? transaction.getBankAccount().getId() : null,
                transaction.getCreditCard() != null ? transaction.getCreditCard().getId() : null,
                transaction.getCategory().getId(),
                transaction.getInvoice() != null ? transaction.getInvoice().getId() : null,
                transaction.getTemplate() != null ? transaction.getTemplate().getId() : null,
                transaction.getInstallmentNumber(),
                transaction.getAmount(),
                transaction.getTransactionDate(),
                transaction.getDescription(),
                transaction.isPaid(),
                transaction.getCreatedAt());
    }
}
