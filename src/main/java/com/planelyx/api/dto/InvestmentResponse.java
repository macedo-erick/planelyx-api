package com.planelyx.api.dto;

import com.planelyx.api.domain.enums.InvestmentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvestmentResponse(
        UUID id,
        String name,
        String institution,
        InvestmentType investmentType,
        BigDecimal initialBalance,
        String currency,
        boolean active,
        Instant createdAt) {}
