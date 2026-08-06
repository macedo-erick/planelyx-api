package com.planelyx.api.service;

import com.planelyx.api.domain.BankAccount;
import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.Invoice;
import com.planelyx.api.domain.TransactionTemplate;
import com.planelyx.api.domain.enums.InvoiceStatus;
import com.planelyx.api.domain.enums.RecurrenceType;
import com.planelyx.api.domain.enums.TransactionKind;
import com.planelyx.api.dto.DashboardResponse;
import com.planelyx.api.dto.InvoiceResponse;
import com.planelyx.api.mapper.InvoiceMapper;
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
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    private static final int UPCOMING_INVOICE_LIMIT = 5;
    private static final int CATEGORY_BREAKDOWN_LIMIT = 8;

    private final TransactionRepository transactionRepository;
    private final TransactionTemplateRepository transactionTemplateRepository;
    private final BankAccountService bankAccountService;
    private final CategoryService categoryService;
    private final InvoiceService invoiceService;

    public DashboardResponse forMonth(UUID ownerId, YearMonth month) {
        LocalDate periodStart = month.atDay(1);
        LocalDate periodEnd = month.atEndOfMonth();

        // Each of these hits the database, so they are resolved once and reused rather than
        // recomputed per response field.
        List<DashboardResponse.AccountBalance> balances = accountBalances(ownerId, periodEnd);
        List<TransactionRepository.KindTotal> movement =
                transactionRepository.sumByKindInMonthDue(ownerId, periodStart, periodEnd);
        List<Invoice> unpaid = unpaid(ownerId);
        List<Invoice> due = dueThrough(unpaid, periodEnd);
        BigDecimal dueTotal = total(due);
        BigDecimal accountTotal = totalBalance(balances);

        return new DashboardResponse(
                periodStart,
                periodEnd,
                balances,
                accountTotal,
                accountTotal.subtract(dueTotal),
                dueTotal,
                due.size(),
                income(movement),
                expense(movement),
                categoryBreakdown(ownerId, periodStart, periodEnd),
                total(unpaid),
                upcomingInvoices(unpaid),
                beyondGeneratedOccurrences(ownerId, month));
    }

    /**
     * The unpaid invoices a forecast to {@code asOf} has to account for.
     *
     * An invoice already paid needs no deduction: paying one posts a settlement against the
     * account, so the money is gone from the balance already. What is left are the bills that
     * fall due by the end of the month and have not been settled — committed, still sitting in
     * the account, and not the owner's to spend. Ones falling due later are left alone: they are
     * not this month's problem.
     */
    private List<Invoice> dueThrough(List<Invoice> unpaid, LocalDate asOf) {
        return unpaid.stream()
                .filter(invoice -> !invoice.getDueDate().isAfter(asOf))
                .toList();
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

    private BigDecimal income(List<TransactionRepository.KindTotal> movement) {
        return movement.stream()
                .filter(row -> row.getKind() == TransactionKind.ACCOUNT_CREDIT)
                .map(TransactionRepository.KindTotal::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * What the month costs: account debits and card charges together.
     *
     * Settlements are already absent — {@link TransactionRepository#sumByKindInMonthDue} leaves
     * them out, because paying an invoice is not a second expense on top of the charges it pays.
     */
    private BigDecimal expense(List<TransactionRepository.KindTotal> movement) {
        return movement.stream()
                .filter(row -> row.getKind() != TransactionKind.ACCOUNT_CREDIT)
                .map(TransactionRepository.KindTotal::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    private List<InvoiceResponse> upcomingInvoices(List<Invoice> unpaid) {
        return unpaid.stream()
                .sorted(Comparator.comparing(Invoice::getDueDate))
                .limit(UPCOMING_INVOICE_LIMIT)
                .map(invoice -> InvoiceMapper.toResponse(invoice, invoiceService.derivedStatus(invoice)))
                .toList();
    }

    private List<Invoice> unpaid(UUID ownerId) {
        return invoiceService.findAll(ownerId, null, null).stream()
                .filter(invoice -> invoiceService.derivedStatus(invoice) != InvoiceStatus.PAID)
                .toList();
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
