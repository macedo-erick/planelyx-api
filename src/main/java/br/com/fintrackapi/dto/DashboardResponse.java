package br.com.fintrackapi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything the dashboard renders for one month, in a single round trip.
 *
 * Balances are cumulative as of the end of that month, which is what makes stepping forward a
 * forecast: installments and recurring occurrences are already materialised as rows, so a future
 * month simply includes rows that exist.
 */
public record DashboardResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        List<AccountBalance> accountBalances,
        BigDecimal totalBalance,
        BigDecimal income,
        BigDecimal expense,
        List<CategoryBreakdown> categoryBreakdown,
        BigDecimal outstandingInvoiceTotal,
        List<InvoiceResponse> upcomingInvoices,
        /**
         * True when the month sits beyond the last generated occurrence of an open-ended
         * recurring rule, so the figures are necessarily incomplete rather than simply low.
         */
        boolean beyondGeneratedOccurrences) {

    public record AccountBalance(
            UUID bankAccountId, String name, String bankName, String currency, BigDecimal balance) {}

    public record CategoryBreakdown(UUID categoryId, String name, String color, BigDecimal total) {}
}
