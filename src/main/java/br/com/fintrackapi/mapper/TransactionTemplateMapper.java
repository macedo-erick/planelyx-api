package br.com.fintrackapi.mapper;

import static java.util.Optional.ofNullable;

import br.com.fintrackapi.domain.BankAccount;
import br.com.fintrackapi.domain.CreditCard;
import br.com.fintrackapi.domain.TransactionTemplate;
import br.com.fintrackapi.dto.TransactionTemplateResponse;

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
