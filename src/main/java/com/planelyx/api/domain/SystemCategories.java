package com.planelyx.api.domain;

import com.planelyx.api.domain.enums.CategoryType;
import java.util.UUID;

/**
 * Ids of the categories the application creates transactions against on its own behalf.
 *
 * Seeded by {@code V11__seed_adjustment_categories.sql} with these exact ids. They are pinned
 * rather than looked up by name because the name is user-facing text that translation will
 * change.
 */
public final class SystemCategories {

    /** Backs a downward correction — a balance debit, or a charge added to an invoice. */
    public static final UUID ADJUSTMENT_EXPENSE = UUID.fromString("00000000-0000-0000-0000-00000000ad01");

    /** Backs an upward correction to an account balance. */
    public static final UUID ADJUSTMENT_INCOME = UUID.fromString("00000000-0000-0000-0000-00000000ad02");

    private SystemCategories() {}

    public static UUID adjustmentFor(CategoryType type) {
        return type == CategoryType.INCOME ? ADJUSTMENT_INCOME : ADJUSTMENT_EXPENSE;
    }
}
