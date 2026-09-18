package com.planelyx.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.planelyx.api.AbstractIntegrationTest;
import com.planelyx.api.domain.BankAccount;
import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.Investment;
import com.planelyx.api.domain.Transaction;
import com.planelyx.api.domain.enums.AccountType;
import com.planelyx.api.domain.enums.CategoryType;
import com.planelyx.api.domain.enums.InvestmentType;
import com.planelyx.api.domain.enums.TransactionKind;
import com.planelyx.api.domain.enums.TransactionScope;
import com.planelyx.api.dto.BalanceAdjustmentRequest;
import com.planelyx.api.dto.BankAccountRequest;
import com.planelyx.api.dto.CategoryRequest;
import com.planelyx.api.dto.DashboardResponse;
import com.planelyx.api.dto.InvestmentMovementRequest;
import com.planelyx.api.dto.InvestmentRequest;
import com.planelyx.api.dto.TransactionRequest;
import com.planelyx.api.dto.TransactionSummaryResponse;
import com.planelyx.api.dto.TransactionUpdateRequest;
import com.planelyx.api.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Money moving between an account and an investment.
 *
 * What is worth pinning is not that balances move but that they move on both legs while the
 * month's spending and income stay put. The aggregates used to be written as "everything except",
 * so a new kind joined the expense figure by default; most of this exists to catch that returning.
 */
class InvestmentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private InvestmentService investmentService;

    @Autowired
    private BankAccountService bankAccountService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void contributionMovesBothBalances() {
        Fixture fixture = fixture("1000.00", "0.00");

        contribute(fixture, "250.00");

        assertAccountBalance(fixture, "750.00");
        assertInvestmentBalance(fixture, "250.00");
    }

    /** The whole reason the kind exists. A contribution is not spending and not income. */
    @Test
    void contributionReachesNeitherSpendingNorIncome() {
        Fixture fixture = fixture("1000.00", "0.00");

        contribute(fixture, "250.00");

        DashboardResponse dashboard = dashboard(fixture);

        assertEquals(0, BigDecimal.ZERO.compareTo(dashboard.expense()));
        assertEquals(0, BigDecimal.ZERO.compareTo(dashboard.income()));
    }

    /** Cash falls, invested rises, and the two cancel. */
    @Test
    void contributionLeavesNetWorthUnchanged() {
        Fixture fixture = fixture("1000.00", "0.00");

        BigDecimal before = dashboard(fixture).netWorth();

        contribute(fixture, "250.00");

        assertEquals(0, before.compareTo(dashboard(fixture).netWorth()));
    }

    /** A contribution filed as spending would show up here, which is how this would be noticed. */
    @Test
    void contributionStaysOutOfTheCategoryBreakdown() {
        Fixture fixture = fixture("1000.00", "0.00");

        contribute(fixture, "250.00");

        assertTrue(dashboard(fixture).categoryBreakdown().isEmpty());
    }

    @Test
    void redemptionReturnsTheMoneyAndIsNotIncome() {
        Fixture fixture = fixture("1000.00", "500.00");

        investmentService.redeem(fixture.investment().getId(), movement(fixture, "200.00"), fixture.ownerId());

        assertAccountBalance(fixture, "1200.00");
        assertInvestmentBalance(fixture, "300.00");
        assertEquals(0, BigDecimal.ZERO.compareTo(dashboard(fixture).income()));
    }

    /** The old sign rule would have taken the money out of the account as well as the investment. */
    @Test
    void redemptionCountsAsAnInflowOnTheAccount() {
        Fixture fixture = fixture("100.00", "500.00");

        investmentService.redeem(fixture.investment().getId(), movement(fixture, "400.00"), fixture.ownerId());

        assertAccountBalance(fixture, "500.00");
    }

    @Test
    void redeemingMoreThanTheInvestmentHoldsIsRefused() {
        Fixture fixture = fixture("1000.00", "100.00");

        assertThrows(
                IllegalArgumentException.class,
                () -> investmentService.redeem(
                        fixture.investment().getId(), movement(fixture, "100.01"), fixture.ownerId()));
    }

    @Test
    void recordingAHigherBalancePostsYieldAndCountsAsIncome() {
        Fixture fixture = fixture("0.00", "1000.00");

        Transaction yield = adjust(fixture, "1042.35");

        assertEquals(TransactionKind.INVESTMENT_YIELD, yield.getKind());
        assertEquals(0, new BigDecimal("42.35").compareTo(yield.getAmount()));
        assertInvestmentBalance(fixture, "1042.35");
        assertEquals(0, new BigDecimal("42.35").compareTo(dashboard(fixture).income()));
    }

    /** Yield raises what the owner is worth without any money reaching an account. */
    @Test
    void yieldRaisesNetWorthButNoAccountBalance() {
        Fixture fixture = fixture("0.00", "1000.00");

        adjust(fixture, "1042.35");

        assertAccountBalance(fixture, "0.00");
        assertEquals(0, new BigDecimal("1042.35").compareTo(dashboard(fixture).netWorth()));
    }

    /** A fund dipping is not spending, so it stays out of the expense figure and its chart. */
    @Test
    void recordingALowerBalancePostsALossThatLowersIncomeRatherThanSpending() {
        Fixture fixture = fixture("0.00", "1000.00");

        Transaction loss = adjust(fixture, "910.00");

        assertEquals(TransactionKind.INVESTMENT_LOSS, loss.getKind());
        assertEquals(0, new BigDecimal("90.00").compareTo(loss.getAmount()));
        assertInvestmentBalance(fixture, "910.00");

        DashboardResponse dashboard = dashboard(fixture);

        assertEquals(0, BigDecimal.ZERO.compareTo(dashboard.expense()));
        assertTrue(dashboard.categoryBreakdown().isEmpty());
        assertEquals(0, new BigDecimal("-90.00").compareTo(dashboard.income()));
    }

    /**
     * The reason a loss nets against income instead of counting as nothing: a return that gives
     * itself back has earned nothing, and leaving the loss out would report the yield anyway.
     */
    @Test
    void yieldGivenBackLeavesNoIncome() {
        Fixture fixture = fixture("0.00", "1000.00");

        adjust(fixture, "1100.00");
        adjust(fixture, "1000.00");

        DashboardResponse dashboard = dashboard(fixture);

        assertEquals(0, BigDecimal.ZERO.compareTo(dashboard.income()));
        assertEquals(0, BigDecimal.ZERO.compareTo(dashboard.expense()));
        assertInvestmentBalance(fixture, "1000.00");
    }

    /** The transactions page keeps its own income/expense split, which has to agree with above. */
    @Test
    void theTransactionListSummaryNetsALossAgainstIncomeToo() {
        Fixture fixture = fixture("0.00", "1000.00");

        adjust(fixture, "1100.00");
        adjust(fixture, "1040.00");

        TransactionSummaryResponse summary =
                transactionService.summarize(fixture.ownerId(), null, null, null, null, null, null);

        assertEquals(0, new BigDecimal("40.00").compareTo(summary.totalIncome()));
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalExpense()));
    }

    @Test
    void matchingTheCurrentBalanceWritesNothing() {
        Fixture fixture = fixture("0.00", "1000.00");

        assertTrue(investmentService
                .adjustBalance(
                        fixture.investment().getId(),
                        new BalanceAdjustmentRequest(new BigDecimal("1000.00"), null, null),
                        fixture.ownerId())
                .isEmpty());
    }

    /** Contributed is what went in less what came out, so balance minus it is the return. */
    @Test
    void contributedIsNetOfRedemptions() {
        Fixture fixture = fixture("1000.00", "0.00");

        contribute(fixture, "500.00");
        investmentService.redeem(fixture.investment().getId(), movement(fixture, "200.00"), fixture.ownerId());
        adjust(fixture, "350.00");

        DashboardResponse.InvestmentBalance reported =
                dashboard(fixture).investmentBalances().getFirst();

        assertEquals(0, new BigDecimal("350.00").compareTo(reported.balance()));
        assertEquals(0, new BigDecimal("300.00").compareTo(reported.contributed()));
    }

    /** One filed through the ordinary path would move the account and leave the investment alone. */
    @Test
    void movementsCannotBeFiledByHand() {
        Fixture fixture = fixture("1000.00", "0.00");

        assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.create(
                        new TransactionRequest(
                                TransactionKind.INVESTMENT_CONTRIBUTION,
                                fixture.account().getId(),
                                null,
                                fixture.category().getId(),
                                new BigDecimal("100.00"),
                                LocalDate.now(),
                                "By hand"),
                        fixture.ownerId()));
    }

    @Test
    void movementsCannotBeEditedOrDeletedFromTheTransactionSide() {
        Fixture fixture = fixture("1000.00", "0.00");
        Transaction movement = contribute(fixture, "250.00");

        assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.update(
                        movement.getId(),
                        new TransactionUpdateRequest(
                                fixture.category().getId(),
                                new BigDecimal("400.00"),
                                LocalDate.now(),
                                "Edited",
                                TransactionScope.SINGLE),
                        fixture.ownerId()));

        assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.delete(movement.getId(), fixture.ownerId(), TransactionScope.SINGLE));
    }

    /** There are no rates anywhere in this app, so adding unlike currencies would mean nothing. */
    @Test
    void movingBetweenUnlikeCurrenciesIsRefused() {
        Fixture fixture = fixture("1000.00", "0.00");

        BankAccount euros = bankAccountService.create(
                new BankAccountRequest("Euros", "Test Bank", AccountType.CHECKING, new BigDecimal("1000.00"), "EUR"),
                fixture.ownerId());

        assertThrows(
                IllegalArgumentException.class,
                () -> investmentService.contribute(
                        fixture.investment().getId(),
                        new InvestmentMovementRequest(euros.getId(), new BigDecimal("100.00"), null, null),
                        fixture.ownerId()));
    }

    @Test
    void deletingAnInvestmentRemovesItsMovements() {
        Fixture fixture = fixture("1000.00", "0.00");
        contribute(fixture, "250.00");

        investmentService.delete(fixture.investment().getId(), fixture.ownerId());

        assertTrue(transactionRepository
                .findAllByOwnerIdAndTransactionDateBetween(
                        fixture.ownerId(),
                        LocalDate.now().minusYears(1),
                        LocalDate.now().plusYears(1))
                .isEmpty());
    }

    /** Ordinary spending has to keep working exactly as it did, alongside all of the above. */
    @Test
    void ordinarySpendingIsUnaffected() {
        Fixture fixture = fixture("1000.00", "0.00");

        contribute(fixture, "250.00");
        transactionService.create(
                new TransactionRequest(
                        TransactionKind.ACCOUNT_DEBIT,
                        fixture.account().getId(),
                        null,
                        fixture.category().getId(),
                        new BigDecimal("80.00"),
                        LocalDate.now(),
                        "Groceries"),
                fixture.ownerId());

        DashboardResponse dashboard = dashboard(fixture);

        assertEquals(0, new BigDecimal("80.00").compareTo(dashboard.expense()));
        assertEquals(1, dashboard.categoryBreakdown().size());
        assertAccountBalance(fixture, "670.00");
    }

    private Transaction contribute(Fixture fixture, String amount) {
        return investmentService.contribute(fixture.investment().getId(), movement(fixture, amount), fixture.ownerId());
    }

    private InvestmentMovementRequest movement(Fixture fixture, String amount) {
        return new InvestmentMovementRequest(fixture.account().getId(), new BigDecimal(amount), LocalDate.now(), null);
    }

    private Transaction adjust(Fixture fixture, String target) {
        return investmentService
                .adjustBalance(
                        fixture.investment().getId(),
                        new BalanceAdjustmentRequest(new BigDecimal(target), LocalDate.now(), null),
                        fixture.ownerId())
                .orElseThrow();
    }

    private DashboardResponse dashboard(Fixture fixture) {
        return dashboardService.forMonth(fixture.ownerId(), YearMonth.now());
    }

    private void assertAccountBalance(Fixture fixture, String expected) {
        BigDecimal balance =
                bankAccountService.balanceAsOf(fixture.account().getId(), fixture.ownerId(), LocalDate.now());

        assertEquals(0, new BigDecimal(expected).compareTo(balance), "account balance");
    }

    private void assertInvestmentBalance(Fixture fixture, String expected) {
        BigDecimal balance =
                investmentService.balanceAsOf(fixture.investment().getId(), fixture.ownerId(), LocalDate.now());

        assertEquals(0, new BigDecimal(expected).compareTo(balance), "investment balance");
    }

    private record Fixture(UUID ownerId, BankAccount account, Investment investment, Category category) {}

    private Fixture fixture(String accountBalance, String investmentBalance) {
        UUID ownerId = newOwner();

        BankAccount account = bankAccountService.create(
                new BankAccountRequest(
                        "Checking", "Test Bank", AccountType.CHECKING, new BigDecimal(accountBalance), "BRL"),
                ownerId);

        Investment investment = investmentService.create(
                new InvestmentRequest(
                        "Tesouro Selic",
                        "Tesouro Direto",
                        InvestmentType.FIXED_INCOME,
                        new BigDecimal(investmentBalance),
                        "BRL"),
                ownerId);

        Category category =
                categoryService.create(new CategoryRequest("General", CategoryType.EXPENSE, null, null), ownerId);

        return new Fixture(ownerId, account, investment, category);
    }
}
