package com.planelyx.api.mapper;

import com.planelyx.api.domain.Investment;
import com.planelyx.api.dto.InvestmentResponse;

public final class InvestmentMapper {

    private InvestmentMapper() {}

    public static InvestmentResponse toResponse(Investment investment) {
        return new InvestmentResponse(
                investment.getId(),
                investment.getName(),
                investment.getInstitution(),
                investment.getInvestmentType(),
                investment.getInitialBalance(),
                investment.getCurrency(),
                investment.isActive(),
                investment.getCreatedAt());
    }
}
