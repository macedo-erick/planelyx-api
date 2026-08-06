package com.planelyx.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.planelyx.api.AbstractIntegrationTest;
import com.planelyx.api.domain.BankAccount;
import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.CreditCard;
import com.planelyx.api.domain.Invoice;
import com.planelyx.api.domain.Transaction;
import com.planelyx.api.domain.enums.AccountType;
import com.planelyx.api.domain.enums.CategoryType;
import com.planelyx.api.domain.enums.TransactionKind;
import com.planelyx.api.dto.BalanceAdjustmentRequest;
import com.planelyx.api.dto.BankAccountRequest;
import com.planelyx.api.dto.CategoryRequest;
import com.planelyx.api.dto.CreditCardRequest;
import com.planelyx.api.dto.DashboardResponse;
import com.planelyx.api.dto.TransactionRequest;
import com.planelyx.api.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Balance and invoice corrections.
 *
 * Both figures are derived rather than stored, so the thing worth pinning is that a correction
 * lands as a transaction of the right sign and that everything reading those figures — the
 * accounts list and the dashboard alike — then agrees on the new number.
 */
class AdjustmentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BalanceAdjustmentService balanceAdjustmentService;

    @Autowired
    private BankAccountService bankAccountService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CreditCardService creditCardService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void raisingTheBalanceCreditsTheDifference() {
        Fixture fixture = account(new BigDecimal("100.00"));

        Transaction adjustment = adjust(fixture, "250.00").orElseThrow();

        assertEquals(TransactionKind.ACCOUNT_CREDIT, adjustment.getKind());
        assertEquals(0, new BigDecimal("150.00").compareTo(adjustment.getAmount()));
        assertEquals(
                adjustmentCategory(fixture.ownerId(), CategoryType.INCOME).getId(),
                adjustment.getCategory().getId());
        assertBalance(fixture, "250.00");
    }

    @Test
    void loweringTheBalanceDebitsTheDifference() {
        Fixture fixture = account(new BigDecimal("100.00"));

        Transaction adjustment = adjust(fixture, "40.00").orElseThrow();

        assertEquals(TransactionKind.ACCOUNT_DEBIT, adjustment.getKind());
        assertEquals(0, new BigDecimal("60.00").compareTo(adjustment.getAmount()));
        assertEquals(
                adjustmentCategory(fixture.ownerId(), CategoryType.EXPENSE).getId(),
                adjustment.getCategory().getId());
        assertBalance(fixture, "40.00");
    }

    /** A zero-amount transaction is both noise and invalid, so nothing is written. */
    @Test
    void matchingTheCurrentBalanceWritesNothing() {
        Fixture fixture = account(new BigDecimal("100.00"));

        assertTrue(adjust(fixture, "100.00").isEmpty());
        assertEquals(
                0,
                transactionRepository
                        .findAllByOwnerIdAndTransactionDateBetween(
                                fixture.ownerId(),
                                LocalDate.now().minusYears(1),
                                LocalDate.now().plusYears(1))
                        .size());
    }

    /** The correction has to account for movement, not just the account's starting figure. */
    @Test
    void correctsAgainstMovementRatherThanTheInitialBalance() {
        Fixture fixture = account(new BigDecimal("100.00"));

        transactionService.create(
                new TransactionRequest(
                        TransactionKind.ACCOUNT_DEBIT,
                        fixture.account().getId(),
                        null,
                        fixture.category().getId(),
                        new BigDecimal("30.00"),
                        LocalDate.now(),
                        "Groceries"),
                fixture.ownerId());

        Transaction adjustment = adjust(fixture, "100.00").orElseThrow();

        assertEquals(TransactionKind.ACCOUNT_CREDIT, adjustment.getKind());
        assertEquals(0, new BigDecimal("30.00").compareTo(adjustment.getAmount()));
        assertBalance(fixture, "100.00");
    }

    /** The accounts list and the dashboard must not be able to disagree about a balance. */
    @Test
    void dashboardReportsTheAdjustedBalance() {
        Fixture fixture = account(new BigDecimal("100.00"));

        adjust(fixture, "175.50");

        DashboardResponse dashboard = dashboardService.forMonth(fixture.ownerId(), YearMonth.now());
        DashboardResponse.AccountBalance reported = dashboard.accountBalances().stream()
                .filter(balance ->
                        balance.bankAccountId().equals(fixture.account().getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(0, new BigDecimal("175.50").compareTo(reported.balance()));
        assertBalance(fixture, "175.50");
    }

    @Test
    void raisingAnInvoiceAddsAChargeForTheDifference() {
        InvoiceFixture fixture = invoice(new BigDecimal("200.00"));

        Invoice adjusted =
                invoiceService.adjust(fixture.invoice().getId(), new BigDecimal("320.00"), null, fixture.ownerId());

        assertEquals(0, new BigDecimal("320.00").compareTo(adjusted.getTotalAmount()));
        assertEquals(
                0, new BigDecimal("120.00").compareTo(adjustmentCharge(fixture).getAmount()));
    }

    /**
     * Correcting downwards needs a negative charge. Nothing downstream forbids one, and this is
     * the check that keeps it that way.
     */
    @Test
    void loweringAnInvoiceAddsANegativeCharge() {
        InvoiceFixture fixture = invoice(new BigDecimal("200.00"));

        Invoice adjusted =
                invoiceService.adjust(fixture.invoice().getId(), new BigDecimal("150.00"), null, fixture.ownerId());

        assertEquals(0, new BigDecimal("150.00").compareTo(adjusted.getTotalAmount()));
        assertEquals(
                0, new BigDecimal("-50.00").compareTo(adjustmentCharge(fixture).getAmount()));
    }

    /** Otherwise the charge would be swept onto the following month's invoice. */
    @Test
    void adjustmentChargeLandsInsideTheBillingPeriod() {
        InvoiceFixture fixture = invoice(new BigDecimal("200.00"));

        invoiceService.adjust(fixture.invoice().getId(), new BigDecimal("210.00"), null, fixture.ownerId());

        Transaction charge = adjustmentCharge(fixture);
        Invoice invoice = fixture.invoice();

        assertTrue(!charge.getTransactionDate().isBefore(invoice.getBillingPeriodStart()));
        assertTrue(!charge.getTransactionDate().isAfter(invoice.getBillingPeriodEnd()));
        assertEquals(invoice.getId(), charge.getInvoice().getId());
    }

    @Test
    void paidInvoicesRefuseAdjustment() {
        InvoiceFixture fixture = invoice(new BigDecimal("200.00"));
        invoiceService.pay(fixture.invoice().getId(), fixture.ownerId());

        assertThrows(
                IllegalStateException.class,
                () -> invoiceService.adjust(
                        fixture.invoice().getId(), new BigDecimal("250.00"), null, fixture.ownerId()));
    }

    private Optional<Transaction> adjust(Fixture fixture, String target) {
        return balanceAdjustmentService.adjust(
                fixture.account().getId(),
                new BalanceAdjustmentRequest(new BigDecimal(target), null, null),
                fixture.ownerId());
    }

    private void assertBalance(Fixture fixture, String expected) {
        BigDecimal balance =
                bankAccountService.balanceAsOf(fixture.account().getId(), fixture.ownerId(), LocalDate.now());

        assertEquals(0, new BigDecimal(expected).compareTo(balance));
    }

    private Transaction adjustmentCharge(InvoiceFixture fixture) {
        UUID adjustmentId =
                adjustmentCategory(fixture.ownerId(), CategoryType.EXPENSE).getId();

        List<Transaction> charges = transactionRepository
                .findAllByInvoiceId(fixture.invoice().getId())
                .stream()
                .filter(charge -> charge.getCategory().getId().equals(adjustmentId))
                .toList();

        assertEquals(1, charges.size(), "exactly one adjustment charge is expected");

        return charges.getFirst();
    }

    private record Fixture(UUID ownerId, BankAccount account, Category category) {}

    private Fixture account(BigDecimal initialBalance) {
        UUID ownerId = newOwner();

        BankAccount account = bankAccountService.create(
                new BankAccountRequest("Checking", "Test Bank", AccountType.CHECKING, initialBalance, "BRL"), ownerId);

        Category category =
                categoryService.create(new CategoryRequest("General", CategoryType.EXPENSE, null, null), ownerId);

        return new Fixture(ownerId, account, category);
    }

    private record InvoiceFixture(UUID ownerId, Invoice invoice) {}

    /** A card carrying a single charge of {@code amount}, and the invoice it landed on. */
    private InvoiceFixture invoice(BigDecimal amount) {
        Fixture fixture = account(BigDecimal.ZERO);

        CreditCard card = creditCardService.create(
                new CreditCardRequest(fixture.account().getId(), "Gold", "VISA", new BigDecimal("5000.00"), 10, 17),
                fixture.ownerId());

        Transaction charge = transactionService.create(
                new TransactionRequest(
                        TransactionKind.CARD_CHARGE,
                        null,
                        card.getId(),
                        fixture.category().getId(),
                        amount,
                        LocalDate.now(),
                        "Purchase"),
                fixture.ownerId());

        return new InvoiceFixture(
                fixture.ownerId(), invoiceService.findById(charge.getInvoice().getId(), fixture.ownerId()));
    }
}
