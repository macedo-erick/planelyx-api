package com.planelyx.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An account's balance at the close of {@code asOf} — the same figure the dashboard shows for
 * that date, not the account's initial balance.
 */
public record BankAccountBalanceResponse(UUID bankAccountId, String currency, BigDecimal balance, LocalDate asOf) {}
