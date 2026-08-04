package br.com.planelyxapi.mapper;

import static java.util.Optional.ofNullable;

import br.com.planelyxapi.domain.BankAccount;
import br.com.planelyxapi.domain.CreditCard;
import br.com.planelyxapi.domain.TransactionTemplate;
import br.com.planelyxapi.dto.TransactionTemplateResponse;

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
