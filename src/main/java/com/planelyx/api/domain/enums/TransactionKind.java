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
    INVOICE_PAYMENT,

    /** Money leaving an account for an investment. Not spending: nothing was bought. */
    INVESTMENT_CONTRIBUTION,

    /**
     * The same money coming back. Not income: it was the owner's already, and counting it would
     * report principal as earnings every time a daily-liquidity fund was cycled in and out.
     */
    INVESTMENT_REDEMPTION,

    /**
     * An investment growing on its own. This — not redemption — is where a return becomes income:
     * recognising it here never double counts and cannot be inflated by moving money around.
     */
    INVESTMENT_YIELD,

    /**
     * An investment shrinking on its own. Not an expense — that figure is what a month cost, and
     * nobody chose to spend a market dip — but not nothing either: it is subtracted from income,
     * where the yield it cancels was added.
     */
    INVESTMENT_LOSS;

    /**
     * How this kind moves the bank account it names: 1 in, -1 out, 0 when it names none.
     *
     * A ternary asking "is it a credit, or else out" silently claims every future kind is an
     * outflow, which is how a redemption ends up subtracted instead of added.
     */
    public int accountSign() {
        return switch (this) {
            case ACCOUNT_CREDIT, INVESTMENT_REDEMPTION -> 1;
            case ACCOUNT_DEBIT, INVOICE_PAYMENT, INVESTMENT_CONTRIBUTION -> -1;
            case CARD_CHARGE, INVESTMENT_YIELD, INVESTMENT_LOSS -> 0;
        };
    }

    /**
     * The same for the investment leg. A contribution is an outflow read from the account and an
     * inflow read from the investment: one row, two signs.
     */
    public int investmentSign() {
        return switch (this) {
            case INVESTMENT_CONTRIBUTION, INVESTMENT_YIELD -> 1;
            case INVESTMENT_REDEMPTION, INVESTMENT_LOSS -> -1;
            case ACCOUNT_DEBIT, ACCOUNT_CREDIT, CARD_CHARGE, INVOICE_PAYMENT -> 0;
        };
    }

    /**
     * Money spent. A closed list on purpose: asking "is it not a credit?" made every new kind an
     * expense by default, where this makes one count as nothing until it is named.
     */
    public boolean isSpending() {
        return this == ACCOUNT_DEBIT || this == CARD_CHARGE;
    }

    /**
     * How this kind moves the month's income: 1 earned, -1 given back, 0 neither.
     *
     * A loss is the only negative, and it does not belong in expense. Leaving it at 0 would have
     * let yield pile up against nothing — a portfolio that rose and fell by the same amount would
     * still report a month of income — so it nets here instead, which keeps the spending chart
     * free of market moves.
     */
    public int incomeSign() {
        return switch (this) {
            case ACCOUNT_CREDIT, INVESTMENT_YIELD -> 1;
            case INVESTMENT_LOSS -> -1;
            case ACCOUNT_DEBIT, CARD_CHARGE, INVOICE_PAYMENT, INVESTMENT_CONTRIBUTION, INVESTMENT_REDEMPTION -> 0;
        };
    }

    /** True when the kind is posted by the application rather than filed by a user. */
    public boolean isDerived() {
        return this == INVOICE_PAYMENT || investmentSign() != 0;
    }
}
