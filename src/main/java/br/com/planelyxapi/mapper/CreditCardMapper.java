package br.com.planelyxapi.mapper;

import br.com.planelyxapi.domain.CreditCard;
import br.com.planelyxapi.dto.CreditCardResponse;
import java.math.BigDecimal;

public final class CreditCardMapper {

    private CreditCardMapper() {}

    public static CreditCardResponse toResponse(CreditCard card, BigDecimal usedLimit) {
        BigDecimal used = usedLimit == null ? BigDecimal.ZERO : usedLimit;

        return new CreditCardResponse(
                card.getId(),
                card.getBankAccount().getId(),
                card.getName(),
                card.getBrand(),
                card.getCreditLimit(),
                used,
                card.getCreditLimit().subtract(used),
                card.getClosingDay(),
                card.getDueDay(),
                card.isActive(),
                card.getCreatedAt());
    }
}
