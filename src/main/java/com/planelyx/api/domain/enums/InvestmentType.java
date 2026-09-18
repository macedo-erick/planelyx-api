package com.planelyx.api.domain.enums;

/**
 * What an investment broadly is, for labelling and grouping only.
 *
 * Nothing in the ledger branches on it: every type holds a balance and moves the same way. It
 * exists so a client can show "Fixed income" beside a name, the way AccountType shows "Checking".
 */
public enum InvestmentType {
    FIXED_INCOME,
    FUNDS,
    STOCKS,
    CRYPTO,
    PENSION,
    OTHER
}
