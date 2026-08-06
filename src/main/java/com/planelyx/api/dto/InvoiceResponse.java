package com.planelyx.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.planelyx.api.domain.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * @param referenceMonth the month the invoice is known by — the month it falls due in, not the one
 *     it closes in. A card closing on the 28th and due on the 5th produces a period running
 *     29 Jul – 28 Aug that everyone calls the September invoice, because that is when it is paid.
 *     Derived here so no screen has to work it out from the dates and reach a different answer.
 */
public record InvoiceResponse(
        UUID id,
        UUID creditCardId,
        @JsonFormat(pattern = "yyyy-MM") YearMonth referenceMonth,
        LocalDate billingPeriodStart,
        LocalDate billingPeriodEnd,
        LocalDate dueDate,
        BigDecimal totalAmount,
        InvoiceStatus status,
        Instant paidAt,
        Instant createdAt) {}
