package com.planelyx.api.dto;

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
 *
 * Three figures are easy to double count, so they are worth stating together. {@code
 * accountBalanceTotal} is the plain sum of the accounts. {@code invoicesDueTotal} covers the unpaid
 * invoices falling due by {@code periodEnd} — committed money that has not left any one account
 * yet. {@code totalBalance} is the first less the second, so it deliberately does not match the
 * accounts below it. An invoice already paid is in neither: paying one posts a settlement, so it
 * has left the balances already.
 *
 * {@code billsDue} is not a fourth figure of that kind. It lists the month's recurring account
 * bills still to be ticked off, and every one is an ordinary transaction already inside {@code
 * accountBalanceTotal} — a reminder, nothing more. Subtracting {@code billsDueTotal} from anything
 * counts the same money twice.
 *
 * {@code beyondGeneratedOccurrences} says the month sits past the last generated occurrence of an
 * open-ended recurring rule, so its figures are necessarily incomplete rather than simply low.
 */
public record DashboardResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        List<AccountBalance> accountBalances,
        BigDecimal accountBalanceTotal,
        BigDecimal totalBalance,
        BigDecimal invoicesDueTotal,
        int invoicesDueCount,
        BigDecimal income,
        BigDecimal expense,
        List<CategoryBreakdown> categoryBreakdown,
        BigDecimal outstandingInvoiceTotal,
        List<InvoiceResponse> upcomingInvoices,
        List<TransactionResponse> billsDue,
        BigDecimal billsDueTotal,
        int billsDueCount,
        boolean beyondGeneratedOccurrences) {

    public record AccountBalance(
            UUID bankAccountId, String name, String bankName, String currency, BigDecimal balance) {}

    /**
     * One slice of {@code expense}. The slices total {@code expense}, so a chart of them agrees
     * with the figure beside it.
     *
     * A null {@code categoryId} marks the single remainder slice carrying every category past the
     * largest few. It stands for no one category, which is how a client tells it apart and labels
     * it in the reader's own language.
     */
    public record CategoryBreakdown(UUID categoryId, String name, String color, BigDecimal total) {}
}
