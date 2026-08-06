package com.planelyx.api.dto;

import com.planelyx.api.domain.enums.CategoryType;
import java.time.Instant;
import java.util.UUID;

/**
 * @param system whether the application owns this category rather than the user. System categories
 *     back the balance and invoice corrections, so they are neither editable nor a valid choice on
 *     a transaction — the flag lets a client keep them out of its pickers without hardcoding ids,
 *     while still being able to name one on a correction that already exists.
 */
public record CategoryResponse(
        UUID id, String name, CategoryType type, String icon, String color, boolean system, Instant createdAt) {}
