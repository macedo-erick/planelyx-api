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
