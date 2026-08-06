package com.planelyx.api.domain.enums;

public enum TransactionKind {
    ACCOUNT_DEBIT,
    ACCOUNT_CREDIT,
    CARD_CHARGE,

    /**
     * Settling a card invoice from a bank account.
     *
     * Moves the balance like a debit but is not spending: the charges it pays off were already
     * counted as expenses in the month their invoice fell due, so counting the settlement too
     * would report the same money twice. Every aggregate that reports spending leaves it out;
     * every one that reports a balance includes it.
     */
    INVOICE_PAYMENT
}
