package com.planelyx.api.service;

import com.planelyx.api.domain.enums.TransactionKind;
import java.util.Objects;
import java.util.UUID;

final class TransactionKindValidator {

    private TransactionKindValidator() {}

    static void validate(TransactionKind kind, UUID bankAccountId, UUID creditCardId) {
        if (kind == TransactionKind.CARD_CHARGE) {
            if (Objects.isNull(creditCardId) || Objects.nonNull(bankAccountId)) {
                throw new IllegalArgumentException("Card charges must reference a creditCardId and no bankAccountId");
            }

            return;
        }

        if (Objects.isNull(bankAccountId) || Objects.nonNull(creditCardId)) {
            throw new IllegalArgumentException(
                    "Account debits/credits must reference a bankAccountId and no creditCardId");
        }
    }
}
