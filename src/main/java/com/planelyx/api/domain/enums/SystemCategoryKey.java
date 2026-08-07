package com.planelyx.api.domain.enums;

/**
 * The role a system category plays, so the application can find one without matching its name.
 *
 * Only ever set on categories the application owns; a user's own categories have none.
 */
public enum SystemCategoryKey {

    /** Backs the balance and invoice corrections the app posts on the owner's behalf. */
    ADJUSTMENT,

    /** Backs the settlement posted when a card invoice is paid. */
    INVOICE_PAYMENT
}
