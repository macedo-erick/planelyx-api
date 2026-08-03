package br.com.fintrackapi.dto;

import br.com.fintrackapi.domain.enums.InvoiceStatus;
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
