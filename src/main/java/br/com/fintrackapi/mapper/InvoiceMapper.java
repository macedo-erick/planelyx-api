package br.com.fintrackapi.mapper;

import br.com.fintrackapi.domain.Invoice;
import br.com.fintrackapi.domain.enums.InvoiceStatus;
import br.com.fintrackapi.dto.InvoiceResponse;

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
}
