package com.planelyx.api.service;

import com.planelyx.api.domain.BankAccount;
import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.Investment;
import com.planelyx.api.domain.Invoice;
import com.planelyx.api.domain.Transaction;
import com.planelyx.api.domain.TransactionTemplate;
import com.planelyx.api.domain.enums.InvoiceStatus;
import com.planelyx.api.domain.enums.RecurrenceType;
import com.planelyx.api.dto.DashboardResponse;
import com.planelyx.api.dto.InvoiceResponse;
import com.planelyx.api.mapper.InvoiceMapper;
import com.planelyx.api.mapper.TransactionMapper;
import com.planelyx.api.repository.TransactionRepository;
import com.planelyx.api.repository.TransactionTemplateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dashboard's figures, computed here rather than by pulling every transaction to the client.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {

    private static final int CATEGORY_BREAKDOWN_LIMIT = 8;
    private static final int TREND_MONTHS = 12;

    private final TransactionRepository transactionRepository;
    private final TransactionTemplateRepository transactionTemplateRepository;
    private final BankAccountService bankAccountService;
    private final InvestmentService investmentService;
    private final CategoryService categoryService;
    private final InvoiceService invoiceService;

    public DashboardResponse forMonth(UUID ownerId, YearMonth month) {
        LocalDate periodStart = month.atDay(1);
        LocalDate periodEnd = month.atEndOfMonth();

        List<DashboardResponse.AccountBalance> balances = accountBalances(ownerId, periodEnd);
        List<TransactionRepository.KindTotal> movement =
                transactionRepository.sumByKindInMonthDue(ownerId, periodStart, periodEnd);
        List<Invoice> invoices = invoiceService.findAll(ownerId, null, null);
        List<Invoice> due = owedThrough(invoices, periodEnd);
        List<Transaction> bills = transactionRepository.findUnpaidBillsInMonth(ownerId, periodStart, periodEnd);
        List<Transaction> paidBills = transactionRepository.findPaidBillsInMonth(ownerId, periodStart, periodEnd);
        BigDecimal dueTotal = total(due);
        BigDecimal accountTotal = totalBalance(balances);
        List<DashboardResponse.InvestmentBalance> investments = investmentBalances(ownerId, periodEnd);
        BigDecimal investedTotal = totalInvested(investments);
        BigDecimal cashTotal = accountTotal.subtract(dueTotal);
        BigDecimal income = income(movement);
        BigDecimal expense = expense(movement);

        return new DashboardResponse(
                periodStart,
                periodEnd,
                balances,
                accountTotal,
                investments,
                investedTotal,
                cashTotal,
                cashTotal.add(investedTotal),
                dueTotal,
                due.size(),
                income,
                expense,
                income.subtract(expense),
                result(ownerId, month.minusMonths(1)),
                trend(ownerId, month),
                categoryBreakdown(ownerId, periodStart, periodEnd),
                invoicesDue(due),
                invoicesPaid(invoices, due, periodStart, periodEnd),
                bills.stream().map(TransactionMapper::toResponse).toList(),
                billsTotal(bills),
                bills.size(),
                paidBills.stream().map(TransactionMapper::toResponse).toList(),
                beyondGeneratedOccurrences(ownerId, month));
    }

    /**
     * The invoices a forecast to {@code asOf} still has to deduct.
     *
     * Two things have to be true. It has to fall due by the end of the month — one falling due
     * later is not this month's problem. And it has to have been unpaid <em>as of that day</em>,
     * which is not the same as being unpaid now: a settlement carries its own date, and one paid
     * in September does not take the money out of August. Reading the stored status alone would
     * drop the deduction from a month whose balance still holds the money, and the debt would
     * read as having evaporated — the very gap the settlement exists to close.
     *
     * A paid invoice with no settlement came to nothing (paying a zero total posts no row), so
     * there is nothing to deduct either way.
     */
    private List<Invoice> owedThrough(List<Invoice> invoices, LocalDate asOf) {
        List<Invoice> fallingDue = invoices.stream()
                .filter(invoice -> !invoice.getDueDate().isAfter(asOf))
                .toList();

        Map<UUID, LocalDate> settledOn = settlementDates(fallingDue);

        return fallingDue.stream()
                .filter(invoice -> stillOwedOn(invoice, settledOn.get(invoice.getId()), asOf))
                .toList();
    }

    private boolean stillOwedOn(Invoice invoice, LocalDate settledOn, LocalDate asOf) {
        if (invoiceService.derivedStatus(invoice) != InvoiceStatus.PAID) {
            return true;
        }

        return settledOn != null && settledOn.isAfter(asOf);
    }

    private Map<UUID, LocalDate> settlementDates(List<Invoice> invoices) {
        if (invoices.isEmpty()) {
            return Map.of();
        }

        return transactionRepository
                .findSettlementDatesByInvoiceIds(
                        invoices.stream().map(Invoice::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        TransactionRepository.InvoiceSettlement::getInvoiceId,
                        TransactionRepository.InvoiceSettlement::getSettledOn));
    }

    private BigDecimal billsTotal(List<Transaction> bills) {
        return bills.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * The accounts with their balance as of {@code asOf}, named and ordered for display.
     *
     * The arithmetic lives in {@link BankAccountService#balancesAsOf} so this and the accounts
     * page cannot drift apart.
     */
    private List<DashboardResponse.AccountBalance> accountBalances(UUID ownerId, LocalDate asOf) {
        Map<UUID, BigDecimal> balances = bankAccountService.balancesAsOf(ownerId, asOf);

        return bankAccountService.findAll(ownerId).stream()
                .sorted(Comparator.comparing(BankAccount::getName))
                .map(account -> new DashboardResponse.AccountBalance(
                        account.getId(),
                        account.getName(),
                        account.getBankName(),
                        account.getCurrency(),
                        balances.getOrDefault(account.getId(), account.getInitialBalance())))
                .toList();
    }

    private BigDecimal totalBalance(List<DashboardResponse.AccountBalance> balances) {
        return balances.stream()
                .map(DashboardResponse.AccountBalance::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * The investments with their balance as of {@code asOf}. The arithmetic lives in
     * {@link InvestmentService} so this and the investments page cannot drift apart.
     */
    private List<DashboardResponse.InvestmentBalance> investmentBalances(UUID ownerId, LocalDate asOf) {
        Map<UUID, BigDecimal> balances = investmentService.balancesAsOf(ownerId, asOf);
        Map<UUID, BigDecimal> contributed = investmentService.contributedAsOf(ownerId, asOf);

        return investmentService.findAll(ownerId).stream()
                .sorted(Comparator.comparing(Investment::getName))
                .map(investment -> new DashboardResponse.InvestmentBalance(
                        investment.getId(),
                        investment.getName(),
                        investment.getInstitution(),
                        investment.getCurrency(),
                        balances.getOrDefault(investment.getId(), investment.getInitialBalance()),
                        contributed.getOrDefault(investment.getId(), investment.getInitialBalance())))
                .toList();
    }

    private BigDecimal totalInvested(List<DashboardResponse.InvestmentBalance> balances) {
        return balances.stream()
                .map(DashboardResponse.InvestmentBalance::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * What the month earned: money from outside, plus the net return recognised on investments.
     *
     * A return counts when recorded rather than when redeemed, so income and "money that arrived
     * in an account" are no longer the same figure — yield not yet redeemed cannot be spent, and
     * a month whose losses outrun its earnings reports negative income rather than hiding them.
     */
    private BigDecimal income(List<? extends TransactionRepository.KindTotal> movement) {
        return movement.stream()
                .map(row ->
                        row.getTotal().multiply(BigDecimal.valueOf(row.getKind().incomeSign())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * What the month costs: account debits and card charges, named rather than inferred from "not
     * income" — which made every kind the ledger gained spending until excluded by hand.
     */
    private BigDecimal expense(List<? extends TransactionRepository.KindTotal> movement) {
        return movement.stream()
                .filter(row -> row.getKind().isSpending())
                .map(TransactionRepository.KindTotal::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * {@link #income} less {@link #expense} for a month other than the one being read, taken from
     * the same totals so the comparison cannot drift from the figures on screen.
     */
    private BigDecimal result(UUID ownerId, YearMonth month) {
        List<TransactionRepository.KindTotal> movement =
                transactionRepository.sumByKindInMonthDue(ownerId, month.atDay(1), month.atEndOfMonth());

        return income(movement).subtract(expense(movement));
    }

    /**
     * {@link #income} and {@link #expense} for each of the twelve months ending with {@code
     * month}, oldest first, from a single query and the same arithmetic as the tiles — so the
     * last point on the chart is the figure printed above it.
     *
     * A month with no rows still gets a point, at zero: leaving it out would have the chart join
     * its neighbours straight across the gap.
     */
    private List<DashboardResponse.MonthMovement> trend(UUID ownerId, YearMonth month) {
        YearMonth first = month.minusMonths(TREND_MONTHS - 1);

        Map<YearMonth, List<TransactionRepository.MonthKindTotal>> byMonth =
                transactionRepository.sumByKindAndMonthDue(ownerId, first.atDay(1), month.atEndOfMonth()).stream()
                        .collect(Collectors.groupingBy(row -> YearMonth.of(row.getYear(), row.getMonth())));

        return Stream.iterate(first, current -> current.plusMonths(1))
                .limit(TREND_MONTHS)
                .map(current -> {
                    List<TransactionRepository.MonthKindTotal> movement = byMonth.getOrDefault(current, List.of());
                    return new DashboardResponse.MonthMovement(current, income(movement), expense(movement));
                })
                .toList();
    }

    /**
     * The same spending as {@link #expense}, split by category, so a chart of it adds up to the
     * figure printed beside it.
     *
     * Only the largest few are worth drawing, but the rest cannot simply be dropped — that is
     * what left the old chart quietly totalling less than the figure above it. They are rolled
     * into one remainder instead.
     */
    private List<DashboardResponse.CategoryBreakdown> categoryBreakdown(UUID ownerId, LocalDate from, LocalDate to) {
        Map<UUID, Category> categoriesById = categoryService.findAll(ownerId).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        List<TransactionRepository.CategoryTotal> totals =
                transactionRepository.sumByCategoryInMonthDue(ownerId, from, to);

        List<DashboardResponse.CategoryBreakdown> breakdown = totals.stream()
                .limit(CATEGORY_BREAKDOWN_LIMIT)
                .map(row -> {
                    Category category = categoriesById.get(row.getCategoryId());
                    return new DashboardResponse.CategoryBreakdown(
                            row.getCategoryId(),
                            category != null ? category.getName() : "Uncategorised",
                            category != null ? category.getColor() : null,
                            row.getTotal());
                })
                .collect(Collectors.toCollection(ArrayList::new));

        remainder(totals).ifPresent(breakdown::add);

        return List.copyOf(breakdown);
    }

    /**
     * Everything past the largest few, as a single slice.
     *
     * Carries no category id — there is no one category it stands for, and that is how the client
     * recognises it and labels it in the reader's own language.
     *
     * Omitted when it is not positive. A negative remainder is possible, since
     * {@link InvoiceService#adjust} records a downward correction as a negative charge; a slice
     * cannot be drawn from it, so the chart is left reading slightly under the total rather than
     * given something nonsensical to draw.
     */
    private Optional<DashboardResponse.CategoryBreakdown> remainder(List<TransactionRepository.CategoryTotal> totals) {
        BigDecimal rest = totals.stream()
                .skip(CATEGORY_BREAKDOWN_LIMIT)
                .map(TransactionRepository.CategoryTotal::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (rest.signum() <= 0) {
            return Optional.empty();
        }

        return Optional.of(new DashboardResponse.CategoryBreakdown(null, "Other", null, rest));
    }

    private BigDecimal total(List<Invoice> invoices) {
        return invoices.stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * The invoices still owed by the end of the month, the same set {@code invoicesDueTotal} adds
     * up, earliest first — so an overdue one from a previous month leads the list. All of them are
     * sent, so a client can expand the list in place rather than point at a page that cannot show
     * them; they are one per card per month, so the list stays short.
     */
    private List<InvoiceResponse> invoicesDue(List<Invoice> due) {
        return due.stream()
                .sorted(Comparator.comparing(Invoice::getDueDate))
                .map(this::toResponse)
                .toList();
    }

    /**
     * The month's invoices that are already settled: those falling due in it that {@code due} does
     * not still hold. A card has one invoice a month, so this is bounded by the number of cards
     * and needs no limit of its own.
     *
     * An invoice that came to nothing is left out. Paying a zero total posts no settlement, so it
     * is marked paid without anything having been paid.
     */
    private List<InvoiceResponse> invoicesPaid(
            List<Invoice> invoices, List<Invoice> due, LocalDate periodStart, LocalDate periodEnd) {
        Set<UUID> stillOwed = due.stream().map(Invoice::getId).collect(Collectors.toSet());

        return invoices.stream()
                .filter(invoice -> !invoice.getDueDate().isBefore(periodStart))
                .filter(invoice -> !invoice.getDueDate().isAfter(periodEnd))
                .filter(invoice -> !stillOwed.contains(invoice.getId()))
                .filter(invoice -> invoice.getTotalAmount().signum() > 0)
                .sorted(Comparator.comparing(Invoice::getDueDate))
                .map(this::toResponse)
                .toList();
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        return InvoiceMapper.toResponse(invoice, invoiceService.derivedStatus(invoice));
    }

    /**
     * Open-ended recurring rules are only materialised a few months ahead and topped up monthly,
     * so past that horizon the month is genuinely incomplete. The UI says so rather than
     * presenting a total that looks like a drop in spending.
     */
    private boolean beyondGeneratedOccurrences(UUID ownerId, YearMonth month) {
        return transactionTemplateRepository.findAllByOwnerId(ownerId).stream()
                .filter(TransactionTemplate::isActive)
                .filter(template -> template.getRecurrenceType() == RecurrenceType.FIXED_INDEFINITE)
                .anyMatch(template -> lastGeneratedMonth(template).isBefore(month));
    }

    /**
     * Compared by month, not by date. An occurrence generated on the 10th is still an occurrence
     * for that whole month, and measuring it against the 31st would report every month as
     * incomplete right up to the day its own occurrence falls.
     */
    private YearMonth lastGeneratedMonth(TransactionTemplate template) {
        return YearMonth.from(lastGeneratedDate(template));
    }

    private LocalDate lastGeneratedDate(TransactionTemplate template) {
        return template.getStartDate().plusMonths(Math.max(template.getOccurrencesGenerated() - 1, 0));
    }
}
