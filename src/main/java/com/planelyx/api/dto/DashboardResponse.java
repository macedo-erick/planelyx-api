package com.planelyx.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
 * {@code invoicesDue} lists the invoices {@code invoicesDueTotal} adds up — still owed by the end
 * of the month, earliest first, so an overdue one from before it leads — capped at a handful, with
 * {@code invoicesDueCount} giving how many there are in all. {@code invoicesPaid} lists the ones
 * falling due in the month that are already settled, so a client can show the whole month.
 *
 * {@code billsDue} is not a fourth figure of that kind. It lists the month's recurring account
 * bills still to be ticked off, and every one is an ordinary transaction already inside {@code
 * accountBalanceTotal} — a reminder, nothing more. Subtracting {@code billsDueTotal} from anything
 * counts the same money twice.
 *
 * {@code investedTotal} stands apart from those on purpose: folding it into {@code
 * accountBalanceTotal} would stop that figure reconciling against a bank statement.
 * {@code netWorth} is {@code totalBalance} plus {@code investedTotal} — the one number that does
 * not move when money is contributed.
 *
 * {@code result} is {@code income} less {@code expense} — what the month left over, or overspent.
 * {@code previousResult} is the same subtraction for the month before, so a client can say how
 * this month compares without a second round trip.
 *
 * {@code trend} is the income and expense of the twelve months ending with this one, oldest
 * first. Every month is present — one with no movement reads as zero rather than being left out,
 * so a chart of it keeps an even time axis — and the last entry is this month's own {@code
 * income} and {@code expense}.
 *
 * {@code beyondGeneratedOccurrences} says the month sits past the last generated occurrence of an
 * open-ended recurring rule, so its figures are necessarily incomplete rather than simply low.
 */
public record DashboardResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        List<AccountBalance> accountBalances,
        BigDecimal accountBalanceTotal,
        List<InvestmentBalance> investmentBalances,
        BigDecimal investedTotal,
        BigDecimal totalBalance,
        BigDecimal netWorth,
        BigDecimal invoicesDueTotal,
        int invoicesDueCount,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal result,
        BigDecimal previousResult,
        List<MonthMovement> trend,
        List<CategoryBreakdown> categoryBreakdown,
        List<InvoiceResponse> invoicesDue,
        List<InvoiceResponse> invoicesPaid,
        List<TransactionResponse> billsDue,
        BigDecimal billsDueTotal,
        int billsDueCount,
        boolean beyondGeneratedOccurrences) {

    public record AccountBalance(
            UUID bankAccountId, String name, String bankName, String currency, BigDecimal balance) {}

    /**
     * One investment at {@code periodEnd}. {@code contributed} is net of redemptions, so the
     * difference against {@code balance} is the return.
     */
    public record InvestmentBalance(
            UUID investmentId,
            String name,
            String institution,
            String currency,
            BigDecimal balance,
            BigDecimal contributed) {}

    /** One month of {@code trend}: the same two figures the dashboard shows for that month. */
    public record MonthMovement(
            @JsonFormat(pattern = "yyyy-MM") YearMonth month, BigDecimal income, BigDecimal expense) {}

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
