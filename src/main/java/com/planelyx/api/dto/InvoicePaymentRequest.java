package com.planelyx.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * How a card invoice was settled. Every field is optional, and so is the body itself.
 *
 * @param paymentDate when the money left, defaulting to the invoice's due date — a balance
 *     projected to the end of a month has to place the debit inside that month to be right.
 * @param bankAccountId the account it came out of, defaulting to the one the card is billed
 *     against. Worth sending when a bill was paid from somewhere else.
 * @param description what the row is called on the account's history. The API has no
 *     translations, so the text a user actually reads has to come from the client that knows
 *     their language — the same reason {@link InvoiceAdjustmentRequest} carries one.
 */
public record InvoicePaymentRequest(LocalDate paymentDate, UUID bankAccountId, String description) {}
