package br.com.fintrackapi.mapper;

import br.com.fintrackapi.domain.CreditCard;
import br.com.fintrackapi.dto.CreditCardResponse;

public final class CreditCardMapper {

    private CreditCardMapper() {}

    public static CreditCardResponse toResponse(CreditCard card) {
        return new CreditCardResponse(
                card.getId(),
                card.getBankAccount().getId(),
                card.getName(),
                card.getBrand(),
                card.getCreditLimit(),
                card.getClosingDay(),
                card.getDueDay(),
                card.isActive(),
                card.getCreatedAt());
    }
}
