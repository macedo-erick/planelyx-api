package com.planelyx.api.mapper;

import com.planelyx.api.domain.Invoice;
import com.planelyx.api.domain.enums.InvoiceStatus;
import com.planelyx.api.dto.InvoiceResponse;
import java.time.YearMonth;

public final class InvoiceMapper {

    private InvoiceMapper() {}

    public static InvoiceResponse toResponse(Invoice invoice, InvoiceStatus derivedStatus) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getCreditCard().getId(),
                YearMonth.from(invoice.getDueDate()),
                invoice.getBillingPeriodStart(),
                invoice.getBillingPeriodEnd(),
                invoice.getDueDate(),
                invoice.getTotalAmount(),
                derivedStatus,
                invoice.getPaidAt(),
                invoice.getCreatedAt());
    }
}
