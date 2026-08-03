package br.com.fintrackapi.mapper;

import br.com.fintrackapi.domain.TransactionTemplate;
import br.com.fintrackapi.dto.TransactionTemplateResponse;

public final class TransactionTemplateMapper {

    private TransactionTemplateMapper() {
    }

    public static TransactionTemplateResponse toResponse(TransactionTemplate template) {
        return new TransactionTemplateResponse(
                template.getId(),
                template.getKind(),
                template.getBankAccount() != null ? template.getBankAccount().getId() : null,
                template.getCreditCard() != null ? template.getCreditCard().getId() : null,
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
