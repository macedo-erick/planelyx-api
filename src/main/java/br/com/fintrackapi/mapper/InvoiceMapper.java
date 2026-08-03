package br.com.fintrackapi.mapper;

import br.com.fintrackapi.domain.Invoice;
import br.com.fintrackapi.domain.Transaction;
import br.com.fintrackapi.domain.enums.InvoiceStatus;
import br.com.fintrackapi.dto.InvoiceDetailResponse;
import br.com.fintrackapi.dto.InvoiceResponse;
import java.util.List;

public final class InvoiceMapper {

    private InvoiceMapper() {}

    public static InvoiceResponse toResponse(Invoice invoice, InvoiceStatus derivedStatus) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getCreditCard().getId(),
                invoice.getBillingPeriodStart(),
                invoice.getBillingPeriodEnd(),
                invoice.getDueDate(),
                invoice.getTotalAmount(),
                derivedStatus,
                invoice.getPaidAt(),
                invoice.getCreatedAt());
    }

    public static InvoiceDetailResponse toDetailResponse(
            Invoice invoice, InvoiceStatus derivedStatus, List<Transaction> transactions) {
        return new InvoiceDetailResponse(
                invoice.getId(),
                invoice.getCreditCard().getId(),
                invoice.getBillingPeriodStart(),
                invoice.getBillingPeriodEnd(),
                invoice.getDueDate(),
                invoice.getTotalAmount(),
                derivedStatus,
                invoice.getPaidAt(),
                invoice.getCreatedAt(),
                transactions.stream().map(TransactionMapper::toResponse).toList());
    }
}
