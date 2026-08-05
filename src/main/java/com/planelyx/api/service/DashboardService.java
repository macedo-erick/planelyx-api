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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                transactionRepository.sumByKindBetween(ownerId, periodStart, periodEnd);
        List<Invoice> unpaid = unpaid(ownerId);
        List<Invoice> due = dueThrough(unpaid, periodEnd);
        BigDecimal dueTotal = total(due);

        return new DashboardResponse(
                periodStart,
                periodEnd,
                balances,
                totalBalance(balances).subtract(dueTotal),
                dueTotal,
                due.size(),
                income(movement),
                expense(movement),
                categoryBreakdown(ownerId, periodStart, periodEnd),
                total(unpaid),
                upcomingInvoices(unpaid),
                beyondGeneratedOccurrences(ownerId, periodEnd));
    }

    /**
     * The unpaid invoices a forecast to {@code asOf} has to account for.
     *
     * Paying an invoice only flips its status — it never posts a debit — so a card charge would
     * otherwise be invisible to every balance the app shows. Deducting the invoices already due
     * by the end of the month is what stops a card-heavy month from reading as though nothing
     * were owed. Ones falling due later are left alone: they are not this month's problem.
     */
    private List<Invoice> dueThrough(List<Invoice> unpaid, LocalDate asOf) {
        return unpaid.stream()
                .filter(invoice -> !invoice.getDueDate().isAfter(asOf))
                .toList();
    }

    /**
     * Initial balance plus account credits minus account debits, cumulative to {@code asOf}.
     *
     * Card charges never move an account balance — they sit on an invoice until it is paid.
     */
    private List<DashboardResponse.AccountBalance> accountBalances(UUID ownerId, LocalDate asOf) {
        Map<UUID, BigDecimal> movementByAccount = new HashMap<>();

        for (TransactionRepository.AccountKindTotal row :
                transactionRepository.sumByAccountAndKindAsOf(ownerId, asOf)) {
            BigDecimal signed = row.getKind() == TransactionKind.ACCOUNT_CREDIT
                    ? row.getTotal()
                    : row.getTotal().negate();

            movementByAccount.merge(row.getBankAccountId(), signed, BigDecimal::add);
        }

        return bankAccountService.findAll(ownerId).stream()
                .sorted(Comparator.comparing(BankAccount::getName))
                .map(account -> new DashboardResponse.AccountBalance(
                        account.getId(),
                        account.getName(),
                        account.getBankName(),
                        account.getCurrency(),
                        account.getInitialBalance()
                                .add(movementByAccount.getOrDefault(account.getId(), BigDecimal.ZERO))))
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

    /** Everything that is not income: account debits and card charges together. */
    private BigDecimal expense(List<TransactionRepository.KindTotal> movement) {
        return movement.stream()
                .filter(row -> row.getKind() != TransactionKind.ACCOUNT_CREDIT)
                .map(TransactionRepository.KindTotal::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<DashboardResponse.CategoryBreakdown> categoryBreakdown(UUID ownerId, LocalDate from, LocalDate to) {
        Map<UUID, Category> categoriesById = categoryService.findAll(ownerId).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        return transactionRepository
                .sumByCategoryBetweenExcludingKind(ownerId, from, to, TransactionKind.ACCOUNT_CREDIT)
                .stream()
                .limit(CATEGORY_BREAKDOWN_LIMIT)
                .map(row -> {
                    Category category = categoriesById.get(row.getCategoryId());
                    return new DashboardResponse.CategoryBreakdown(
                            row.getCategoryId(),
                            category != null ? category.getName() : "Uncategorised",
                            category != null ? category.getColor() : null,
                            row.getTotal());
                })
                .toList();
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
    private boolean beyondGeneratedOccurrences(UUID ownerId, LocalDate periodEnd) {
        return transactionTemplateRepository.findAllByOwnerId(ownerId).stream()
                .filter(TransactionTemplate::isActive)
                .filter(template -> template.getRecurrenceType() == RecurrenceType.FIXED_INDEFINITE)
                .anyMatch(template -> lastGeneratedDate(template).isBefore(periodEnd));
    }

    private LocalDate lastGeneratedDate(TransactionTemplate template) {
        return template.getStartDate().plusMonths(Math.max(template.getOccurrencesGenerated() - 1, 0));
    }
}
