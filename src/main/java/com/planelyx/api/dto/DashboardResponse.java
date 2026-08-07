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
 */
public record DashboardResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        List<AccountBalance> accountBalances,
        /** The plain sum of {@code accountBalances}, so a client can show the subtraction below. */
        BigDecimal accountBalanceTotal,
        /**
         * What is actually the owner's by {@code periodEnd}: {@code accountBalanceTotal} less
         * {@code invoicesDueTotal}. It deliberately does not match the sum of the accounts — an
         * unpaid card invoice is money already committed but not yet taken out of any one
         * account, so it is deducted from the total only.
         *
         * Invoices already paid are not deducted here and do not need to be: paying one posts a
         * settlement against an account, so it has already left the balances above.
         */
        BigDecimal totalBalance,
        /** Unpaid invoices falling due on or before {@code periodEnd}, already deducted above. */
        BigDecimal invoicesDueTotal,
        int invoicesDueCount,
        BigDecimal income,
        BigDecimal expense,
        List<CategoryBreakdown> categoryBreakdown,
        BigDecimal outstandingInvoiceTotal,
        List<InvoiceResponse> upcomingInvoices,
        /**
         * This month's recurring account bills that have not been ticked off yet — rent, power,
         * internet — oldest first, so the owner can see what is still to pay.
         *
         * Purely a reminder. Every one of these is an ordinary transaction that already exists and
         * is <strong>already inside {@code accountBalanceTotal}</strong>, because balances here are
         * a forecast to the end of the month rather than a snapshot of today. Marking one paid
         * moves no money and changes no figure above. A client must not subtract
         * {@code billsDueTotal} from anything — that is double counting, and double counting is
         * what this feature exists to stop.
         *
         * Card invoices are not here. They are settled as one bill through {@code upcomingInvoices}
         * and are deducted through {@code invoicesDueTotal}, which is a different thing entirely.
         */
        List<TransactionResponse> billsDue,
        /** The plain sum of {@code billsDue}, for a heading. Not deducted from anything. */
        BigDecimal billsDueTotal,
        int billsDueCount,
        /**
         * True when the month sits beyond the last generated occurrence of an open-ended
         * recurring rule, so the figures are necessarily incomplete rather than simply low.
         */
        boolean beyondGeneratedOccurrences) {

    public record AccountBalance(
            UUID bankAccountId, String name, String bankName, String currency, BigDecimal balance) {}

    /**
     * One slice of {@code expense}. The slices total {@code expense}, so a chart of them agrees
     * with the figure beside it.
     *
     * @param categoryId null on the single remainder slice that carries every category past the
     *     largest few. It stands for no one category, which is how a client tells it apart and
     *     labels it in the reader's own language.
     */
    public record CategoryBreakdown(UUID categoryId, String name, String color, BigDecimal total) {}
}
