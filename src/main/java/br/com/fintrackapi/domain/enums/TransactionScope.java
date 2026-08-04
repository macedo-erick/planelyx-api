package br.com.fintrackapi.domain.enums;

/**
 * How far an edit or delete reaches through a series of generated transactions.
 *
 * Only meaningful for a transaction carrying a template — a one-off is always SINGLE, and the
 * scope is ignored for it rather than rejected.
 */
public enum TransactionScope {
    /** This transaction alone. */
    SINGLE,
    /** This transaction and every later one in the same series. */
    FUTURE,
    /** Every transaction in the series, past and future. */
    ALL
}
