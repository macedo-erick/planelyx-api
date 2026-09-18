package com.planelyx.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What an investment is worth at the close of {@code asOf}. {@code contributed} is net of
 * redemptions, so {@code balance} less it is the return.
 */
public record InvestmentBalanceResponse(
        UUID investmentId, String currency, BigDecimal balance, BigDecimal contributed, LocalDate asOf) {

    /** Yield less losses, whatever the movements were. */
    public BigDecimal returned() {
        return balance.subtract(contributed);
    }
}
