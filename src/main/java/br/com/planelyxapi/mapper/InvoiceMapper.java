package br.com.planelyxapi.mapper;

import br.com.planelyxapi.domain.Invoice;
import br.com.planelyxapi.domain.enums.InvoiceStatus;
import br.com.planelyxapi.dto.InvoiceResponse;

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
