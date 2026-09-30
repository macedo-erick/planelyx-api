package com.planelyx.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.planelyx.api.AbstractIntegrationTest;
import com.planelyx.api.domain.BankAccount;
import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.CreditCard;
import com.planelyx.api.domain.enums.AccountType;
import com.planelyx.api.domain.enums.CategoryType;
import com.planelyx.api.domain.enums.TransactionKind;
import com.planelyx.api.dto.BankAccountRequest;
import com.planelyx.api.dto.CategoryRequest;
import com.planelyx.api.dto.CreditCardRequest;
import com.planelyx.api.dto.DashboardResponse;
import com.planelyx.api.dto.TransactionRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Which month a figure on the dashboard belongs to.
 *
 * The screen used to run on two calendars: a card charge counted as spending in the month it was
 * bought, while the invoice paying for it was deducted from the balance in the month it fell due.
 * Nothing on the screen could reconcile as a result. These pin the single rule that replaced it —
 * a charge belongs to the month its invoice falls due — and the arithmetic that now follows from
 * it.
 */
class DashboardMonthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BankAccountService bankAccountService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CreditCardService creditCardService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private DashboardService dashboardService;

    /**
     * The worked example from {@code InvoiceService.resolveBillingPeriod}: a card closing on the
     * 28th and due on the 5th puts a charge dated 30 July onto the period ending 28 August, which
     * falls due on 5 September. September is the month that has to pay for it.
     */
    @Test
    void aChargeCountsInTheMonthItsInvoiceFallsDue() {
        Fixture fixture = fixture(28, 5);

        charge(fixture, "100.00", LocalDate.of(2026, 7, 30));

        assertEquals(0, expense(fixture, YearMonth.of(2026, 7)).signum(), "not the month it was bought");
        assertEquals(0, expense(fixture, YearMonth.of(2026, 8)).signum(), "not the month it closed");
        assertAmount("100.00", expense(fixture, YearMonth.of(2026, 9)));
    }

    /**
     * The distinction that started this: two cards, one whose bill for August has already been
     * settled and one still collecting. A purchase on 6 August goes onto the second card's 20
     * August bill, but has to wait for the first card's next one in September. Same day, same
     * money, a month apart — because that is when each is actually paid.
     */
    @Test
    void whetherTheCardHasClosedDecidesWhichMonthPays() {
        Fixture fixture = fixture(28, 5);
        LocalDate purchase = LocalDate.of(2026, 8, 6);

        CreditCard stillOpen = card(fixture, "Open", 10, 20);

        charge(fixture, "300.00", purchase);
        charge(fixture, stillOpen, "700.00", purchase);

        assertAmount("700.00", expense(fixture, YearMonth.of(2026, 8)));
        assertAmount("300.00", expense(fixture, YearMonth.of(2026, 9)));
    }

    /** Account movements have no invoice, so they keep their own date. */
    @Test
    void anAccountDebitCountsOnItsOwnDate() {
        Fixture fixture = fixture(28, 5);

        debit(fixture, "80.00", LocalDate.of(2026, 8, 10));

        assertAmount("80.00", expense(fixture, YearMonth.of(2026, 8)));
        assertEquals(0, expense(fixture, YearMonth.of(2026, 9)).signum());
    }

    /**
     * The chart and the figure beside it have to be the same number. Ten categories is past the
     * eight the chart draws, so this only holds if the tail is rolled into a remainder rather than
     * dropped.
     */
    @Test
    void theBreakdownAddsUpToTheExpenseItSplits() {
        Fixture fixture = fixture(28, 5);
        LocalDate date = LocalDate.of(2026, 8, 10);

        for (int i = 0; i < 10; i++) {
            Category category = categoryService.create(
                    new CategoryRequest("Cat " + i, CategoryType.EXPENSE, null, null), fixture.ownerId());

            transactionService.create(
                    new TransactionRequest(
                            TransactionKind.ACCOUNT_DEBIT,
                            fixture.account().getId(),
                            null,
                            category.getId(),
                            new BigDecimal((i + 1) + "0.00"),
                            date,
                            "Spend " + i),
                    fixture.ownerId());
        }

        DashboardResponse dashboard = dashboardService.forMonth(fixture.ownerId(), YearMonth.of(2026, 8));

        BigDecimal charted = dashboard.categoryBreakdown().stream()
                .map(DashboardResponse.CategoryBreakdown::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(9, dashboard.categoryBreakdown().size(), "eight categories plus the remainder");
        assertEquals(0, dashboard.expense().compareTo(charted), "the chart totals the figure beside it");

        DashboardResponse.CategoryBreakdown remainder =
                dashboard.categoryBreakdown().getLast();

        assertNotNull(remainder);
        assertTrue(remainder.categoryId() == null, "the remainder stands for no one category");
    }

    /** The subtraction the tile is meant to show, so a client can print it rather than explain it. */
    @Test
    void theTotalIsTheAccountsLessWhatIsStillOwed() {
        Fixture fixture = fixture(28, 5);

        charge(fixture, "250.00", LocalDate.of(2026, 8, 6));

        DashboardResponse dashboard = dashboardService.forMonth(fixture.ownerId(), YearMonth.of(2026, 9));

        assertEquals(
                0,
                dashboard
                        .accountBalanceTotal()
                        .subtract(dashboard.invoicesDueTotal())
                        .compareTo(dashboard.totalBalance()));
        assertAmount("250.00", dashboard.invoicesDueTotal());
    }

    /**
     * The month's result is what came in less what went out, and the month before is worked out
     * the same way so the two can be compared.
     */
    @Test
    void theResultIsIncomeLessExpenseAlongsideTheMonthBefore() {
        Fixture fixture = fixture(28, 5);

        credit(fixture, "500.00", LocalDate.of(2026, 7, 5));
        debit(fixture, "200.00", LocalDate.of(2026, 7, 10));
        credit(fixture, "400.00", LocalDate.of(2026, 8, 5));
        debit(fixture, "450.00", LocalDate.of(2026, 8, 10));

        DashboardResponse dashboard = dashboardService.forMonth(fixture.ownerId(), YearMonth.of(2026, 8));

        assertAmount("-50.00", dashboard.result());
        assertEquals(0, dashboard.income().subtract(dashboard.expense()).compareTo(dashboard.result()));
        assertAmount("300.00", dashboard.previousResult());
    }

    /**
     * Twelve months ending with the one on screen, oldest first — here spanning a new year — with
     * a quiet month present at zero rather than missing, so the chart keeps an even axis.
     */
    @Test
    void theTrendCoversTheTwelveMonthsEndingWithThisOne() {
        Fixture fixture = fixture(28, 5);

        credit(fixture, "1000.00", LocalDate.of(2025, 3, 5));
        debit(fixture, "400.00", LocalDate.of(2025, 3, 20));
        credit(fixture, "1200.00", LocalDate.of(2026, 2, 5));
        debit(fixture, "700.00", LocalDate.of(2026, 2, 15));

        DashboardResponse dashboard = dashboardService.forMonth(fixture.ownerId(), YearMonth.of(2026, 2));

        assertEquals(12, dashboard.trend().size());
        assertEquals(YearMonth.of(2025, 3), dashboard.trend().getFirst().month());
        assertEquals(YearMonth.of(2026, 2), dashboard.trend().getLast().month());

        DashboardResponse.MonthMovement oldest = dashboard.trend().getFirst();
        assertAmount("1000.00", oldest.income());
        assertAmount("400.00", oldest.expense());

        DashboardResponse.MonthMovement quiet = dashboard.trend().get(6);
        assertEquals(YearMonth.of(2025, 9), quiet.month());
        assertEquals(0, quiet.income().signum());
        assertEquals(0, quiet.expense().signum());
    }

    /** Nothing from after the month on screen, and nothing from before the window. */
    @Test
    void theTrendStopsAtTheMonthOnScreen() {
        Fixture fixture = fixture(28, 5);

        debit(fixture, "90.00", LocalDate.of(2025, 7, 31));
        debit(fixture, "60.00", LocalDate.of(2026, 8, 1));

        DashboardResponse dashboard = dashboardService.forMonth(fixture.ownerId(), YearMonth.of(2026, 7));

        BigDecimal charted = dashboard.trend().stream()
                .map(DashboardResponse.MonthMovement::expense)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, charted.signum());
    }

    /**
     * The last point is the month on screen, and it has to agree with the tiles — including a card
     * charge, which lands in the month its invoice falls due rather than the month it was bought.
     */
    @Test
    void theTrendEndsOnTheFiguresOnScreen() {
        Fixture fixture = fixture(28, 5);

        credit(fixture, "900.00", LocalDate.of(2026, 9, 1));
        charge(fixture, "100.00", LocalDate.of(2026, 7, 30));
        debit(fixture, "50.00", LocalDate.of(2026, 9, 12));

        DashboardResponse dashboard = dashboardService.forMonth(fixture.ownerId(), YearMonth.of(2026, 9));
        DashboardResponse.MonthMovement last = dashboard.trend().getLast();

        assertEquals(0, dashboard.income().compareTo(last.income()));
        assertEquals(0, dashboard.expense().compareTo(last.expense()));
        assertAmount("150.00", last.expense());
        assertEquals(0, dashboard.trend().get(9).expense().signum(), "not July, when it was bought");
    }

    /**
     * The list follows the month on screen: what falls due after it is not listed, and what an
     * earlier month left unpaid leads, since that is the most urgent.
     */
    @Test
    void theOwedListIsWhatIsStillOwedByTheEndOfTheMonthEarliestFirst() {
        Fixture fixture = fixture(28, 5);

        charge(fixture, "100.00", LocalDate.of(2026, 7, 30));
        charge(fixture, "200.00", LocalDate.of(2026, 8, 30));

        DashboardResponse september = dashboardService.forMonth(fixture.ownerId(), YearMonth.of(2026, 9));
        DashboardResponse october = dashboardService.forMonth(fixture.ownerId(), YearMonth.of(2026, 10));

        assertEquals(1, september.invoicesDue().size(), "October's invoice is not September's problem");
        assertEquals(1, september.invoicesDueCount());
        assertEquals(
                LocalDate.of(2026, 9, 5), september.invoicesDue().getFirst().dueDate());

        assertEquals(2, october.invoicesDue().size());
        assertEquals(
                LocalDate.of(2026, 9, 5),
                october.invoicesDue().getFirst().dueDate(),
                "the one left over from September leads");
    }

    /**
     * Every owed invoice is sent, not just the first few, so the dashboard can expand the list in
     * place; the count and the total cover the same set.
     */
    @Test
    void theOwedListCarriesEveryInvoiceItCounts() {
        Fixture fixture = fixture(28, 5);

        for (int i = 0; i < 7; i++) {
            charge(fixture, card(fixture, "Card " + i, 10, 20), "10.00", LocalDate.of(2026, 8, 6));
        }

        DashboardResponse dashboard = dashboardService.forMonth(fixture.ownerId(), YearMonth.of(2026, 8));

        assertEquals(7, dashboard.invoicesDue().size());
        assertEquals(7, dashboard.invoicesDueCount());
        assertAmount("70.00", dashboard.invoicesDueTotal());
    }

    private BigDecimal expense(Fixture fixture, YearMonth month) {
        return dashboardService.forMonth(fixture.ownerId(), month).expense();
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private record Fixture(UUID ownerId, BankAccount account, CreditCard card, Category category) {}

    private Fixture fixture(int closingDay, int dueDay) {
        UUID ownerId = newOwner();

        BankAccount account = bankAccountService.create(
                new BankAccountRequest("Checking", "Test Bank", AccountType.CHECKING, new BigDecimal("1000.00"), "BRL"),
                ownerId);

        Category category =
                categoryService.create(new CategoryRequest("General", CategoryType.EXPENSE, null, null), ownerId);

        CreditCard card = creditCardService.create(
                new CreditCardRequest(account.getId(), "Gold", "VISA", new BigDecimal("5000.00"), closingDay, dueDay),
                ownerId);

        return new Fixture(ownerId, account, card, category);
    }

    private CreditCard card(Fixture fixture, String name, int closingDay, int dueDay) {
        return creditCardService.create(
                new CreditCardRequest(
                        fixture.account().getId(), name, "VISA", new BigDecimal("5000.00"), closingDay, dueDay),
                fixture.ownerId());
    }

    private void charge(Fixture fixture, String amount, LocalDate date) {
        charge(fixture, fixture.card(), amount, date);
    }

    private void charge(Fixture fixture, CreditCard card, String amount, LocalDate date) {
        transactionService.create(
                new TransactionRequest(
                        TransactionKind.CARD_CHARGE,
                        null,
                        card.getId(),
                        fixture.category().getId(),
                        new BigDecimal(amount),
                        date,
                        "Purchase"),
                fixture.ownerId());
    }

    private void credit(Fixture fixture, String amount, LocalDate date) {
        transactionService.create(
                new TransactionRequest(
                        TransactionKind.ACCOUNT_CREDIT,
                        fixture.account().getId(),
                        null,
                        fixture.category().getId(),
                        new BigDecimal(amount),
                        date,
                        "Salary"),
                fixture.ownerId());
    }

    private void debit(Fixture fixture, String amount, LocalDate date) {
        transactionService.create(
                new TransactionRequest(
                        TransactionKind.ACCOUNT_DEBIT,
                        fixture.account().getId(),
                        null,
                        fixture.category().getId(),
                        new BigDecimal(amount),
                        date,
                        "Bill"),
                fixture.ownerId());
    }
}
