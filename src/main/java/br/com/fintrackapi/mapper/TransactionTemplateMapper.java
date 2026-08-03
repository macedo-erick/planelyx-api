package br.com.fintrackapi.mapper;

import static java.util.Objects.nonNull;

import br.com.fintrackapi.domain.TransactionTemplate;
import br.com.fintrackapi.dto.TransactionTemplateResponse;

public final class TransactionTemplateMapper {

    private TransactionTemplateMapper() {}

    public static TransactionTemplateResponse toResponse(TransactionTemplate template) {
        return new TransactionTemplateResponse(
                template.getId(),
                template.getKind(),
                nonNull(template.getBankAccount()) ? template.getBankAccount().getId() : null,
                nonNull(template.getCreditCard()) ? template.getCreditCard().getId() : null,
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
