package com.planelyx.api.dto;

import com.planelyx.api.domain.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID creditCardId,
        LocalDate billingPeriodStart,
        LocalDate billingPeriodEnd,
        LocalDate dueDate,
        BigDecimal totalAmount,
        InvoiceStatus status,
        Instant paidAt,
        Instant createdAt) {}
