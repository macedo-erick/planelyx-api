package com.planelyx.api.service;

import com.planelyx.api.domain.enums.TransactionKind;
import java.util.Objects;
import java.util.UUID;

final class TransactionKindValidator {

    private TransactionKindValidator() {}

    /**
     * Guards the two write paths a user reaches: filing a transaction and defining a recurring rule.
     *
     * A settlement is not among them. It is derived from an invoice — {@code InvoiceService} posts
     * it when the invoice is paid and removes it when that is undone — so one written by hand
     * would claim an invoice had been settled when it had not, and nothing would ever clean it up.
     */
    static void validate(TransactionKind kind, UUID bankAccountId, UUID creditCardId) {
        if (kind == TransactionKind.INVOICE_PAYMENT) {
            throw new IllegalArgumentException("Invoice payments are posted by paying an invoice, not filed directly");
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
