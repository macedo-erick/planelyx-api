package com.planelyx.api.service;

import com.planelyx.api.domain.enums.TransactionKind;
import java.util.Objects;
import java.util.UUID;

final class TransactionKindValidator {

    private TransactionKindValidator() {}

    /**
     * Guards the two write paths a user reaches: filing a transaction and defining a recurring rule.
     *
     * The derived kinds are not among them. One written by hand would claim an invoice had been
     * settled when it had not, or would move an account balance while leaving the investment's
     * untouched — neither of which anything downstream can detect.
     */
    static void validate(TransactionKind kind, UUID bankAccountId, UUID creditCardId) {
        if (kind.isDerived()) {
            throw new IllegalArgumentException(
                    "%s is posted by the operation it belongs to, not filed directly".formatted(kind));
        }

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
