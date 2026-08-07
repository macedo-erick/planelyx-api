package com.planelyx.api.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * How a card invoice was settled. Every field is optional, and so is the body itself.
 *
 * The payment date defaults to today, which is the only thing the API knows on its own — someone
 * paying by hand pays when they pay, and dating the debit to the vencimento would put it on a day
 * the money was still in the account. The account defaults to the one the card is billed against,
 * so it is worth sending only when the bill was paid from somewhere else.
 *
 * The description names the row on the account's history. The API has no translations, so the text
 * a user actually reads has to come from the client that knows their language — the same reason
 * {@link InvoiceAdjustmentRequest} carries one.
 */
public record InvoicePaymentRequest(LocalDate paymentDate, UUID bankAccountId, String description) {}
