package com.planelyx.api.mapper;

import static java.util.Optional.ofNullable;

import com.planelyx.api.domain.BankAccount;
import com.planelyx.api.domain.CreditCard;
import com.planelyx.api.domain.TransactionTemplate;
import com.planelyx.api.dto.TransactionTemplateResponse;

public final class TransactionTemplateMapper {

    private TransactionTemplateMapper() {}

    public static TransactionTemplateResponse toResponse(TransactionTemplate template) {
        return new TransactionTemplateResponse(
                template.getId(),
                template.getKind(),
                ofNullable(template.getBankAccount()).map(BankAccount::getId).orElse(null),
                ofNullable(template.getCreditCard()).map(CreditCard::getId).orElse(null),
                template.getCategory().getId(),
                template.getDescription(),
                template.getTotalAmount(),
                template.getRecurrenceType(),
                template.getIntervalUnit(),
                template.getStartDate(),
                template.getTotalOccurrences(),
                template.getOccurrencesGenerated(),
                template.isActive(),
                template.getCreatedAt());
    }
}
