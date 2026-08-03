package br.com.fintrackapi.service;

import br.com.fintrackapi.domain.TransactionKind;
import java.util.UUID;

final class TransactionKindValidator {

    private TransactionKindValidator() {
    }

    static void validate(TransactionKind kind, UUID bankAccountId, UUID creditCardId) {
        if (kind == TransactionKind.CARD_CHARGE) {
            if (creditCardId == null || bankAccountId != null) {
                throw new IllegalArgumentException(
                        "Card charges must reference a creditCardId and no bankAccountId");
            }
        } else if (bankAccountId == null || creditCardId != null) {
            throw new IllegalArgumentException(
                    "Account debits/credits must reference a bankAccountId and no creditCardId");
        }
    }
}
