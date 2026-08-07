package com.planelyx.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.planelyx.api.AbstractIntegrationTest;
import com.planelyx.api.domain.BankAccount;
import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.CreditCard;
import com.planelyx.api.domain.Transaction;
import com.planelyx.api.domain.enums.AccountType;
import com.planelyx.api.domain.enums.CategoryType;
import com.planelyx.api.domain.enums.RecurrenceType;
import com.planelyx.api.domain.enums.TransactionKind;
import com.planelyx.api.dto.BankAccountRequest;
import com.planelyx.api.dto.CategoryRequest;
import com.planelyx.api.dto.CreditCardRequest;
import com.planelyx.api.dto.DashboardResponse;
import com.planelyx.api.dto.TransactionRequest;
import com.planelyx.api.dto.TransactionTemplateRequest;
import com.planelyx.api.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The dashboard's reminder: the month's recurring account bills that have not been ticked off.
 *
 * {@code transaction.paid} existed from the first migration but meant nothing — every write site
 * set it to true, so there was no such thing as a bill still to pay. These pin what it means now,
 * and the line it must not cross: it is a reminder, so ticking one off moves no money.
 */
class BillsIntegrationTest extends AbstractIntegrationTest {

    /**
     * The month the bills fall in, always ahead of today.
     *
     * A recurring rule materialises its occurrences up front, and whether one counts as a bill
     * turns on whether its date has arrived. Pinning this to a fixed calendar month would make the
     * whole class start failing once the wall clock walked past it.
     */
    private static final YearMonth BILL_MONTH = YearMonth.now().plusMonths(1);

    @Autowired
    private BankAccountService bankAccountService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CreditCardService creditCardService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionTemplateService transactionTemplateService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void theMonthsRecurringBillsShowUpUnpaid() {
        Fixture fixture = withMonthlyBill("Rent", "1500.00");

        DashboardResponse dashboard = dashboard(fixture);

        assertEquals(1, dashboard.billsDueCount());
        assertEquals("Rent", dashboard.billsDue().getFirst().description());
        assertAmount("1500.00", dashboard.billsDueTotal());
    }

    /**
     * The invariant the whole feature rests on.
     *
     * The bill is an ordinary transaction that already exists, and balances here run to the end of
     * the month rather than stopping at today — so the money is already deducted whether or not the
     * owner has ticked the box. If ticking it moved a figure, the month would be charged twice,
     * which is the bug this release is fixing elsewhere.
     */
    @Test
    void tickingABillOffRemovesItWithoutMovingAnyBalance() {
        Fixture fixture = withMonthlyBill("Rent", "1500.00");

        DashboardResponse before = dashboard(fixture);

        transactionService.markPaid(before.billsDue().getFirst().id(), true, fixture.ownerId());

        DashboardResponse after = dashboard(fixture);

        assertEquals(0, after.billsDueCount(), "the reminder is gone");
        assertEquals(0, after.billsDueTotal().signum());
        assertEquals(
                0,
                before.accountBalanceTotal().compareTo(after.accountBalanceTotal()),
                "the accounts are no poorer for the tick");
        assertEquals(0, before.totalBalance().compareTo(after.totalBalance()), "the total does not move");
        assertEquals(0, before.expense().compareTo(after.expense()), "nor does the month's spending");
    }

    /** And back on the list, for one ticked off by mistake. */
    @Test
    void aBillCanBePutBackOnTheList() {
        Fixture fixture = withMonthlyBill("Rent", "1500.00");
        UUID billId = dashboard(fixture).billsDue().getFirst().id();

        transactionService.markPaid(billId, true, fixture.ownerId());
        transactionService.markPaid(billId, false, fixture.ownerId());

        assertEquals(1, dashboard(fixture).billsDueCount());
    }

    /** A card charge is settled through its invoice, all at once. It is not a bill of its own. */
    @Test
    void aCardChargeIsNeverABill() {
        Fixture fixture = base();
        CreditCard card = creditCardService.create(
                new CreditCardRequest(fixture.account().getId(), "Gold", "VISA", new BigDecimal("5000.00"), 28, 5),
                fixture.ownerId());

        transactionTemplateService.create(
                new TransactionTemplateRequest(
                        TransactionKind.CARD_CHARGE,
                        null,
                        card.getId(),
                        fixture.category().getId(),
                        "Streaming",
                        new BigDecimal("39.90"),
                        RecurrenceType.FIXED_INDEFINITE,
                        BILL_MONTH.atDay(10),
                        null),
                fixture.ownerId());

        assertEquals(0, dashboard(fixture).billsDueCount());
    }

    /** Nor may one be ticked off by hand — that would claim it was paid outside its invoice. */
    @Test
    void onlyAnAccountDebitCanBeTickedOff() {
        Fixture fixture = base();
        CreditCard card = creditCardService.create(
                new CreditCardRequest(fixture.account().getId(), "Gold", "VISA", new BigDecimal("5000.00"), 28, 5),
                fixture.ownerId());

        Transaction charge = transactionService.create(
                new TransactionRequest(
                        TransactionKind.CARD_CHARGE,
                        null,
                        card.getId(),
                        fixture.category().getId(),
                        new BigDecimal("400.00"),
                        BILL_MONTH.atDay(10),
                        "Purchase"),
                fixture.ownerId());

        assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.markPaid(charge.getId(), true, fixture.ownerId()));
    }

    /** A settlement follows its invoice. Unpaying the invoice is the only way to undo one. */
    @Test
    void aSettlementCannotBeTickedOff() {
        Fixture fixture = base();
        CreditCard card = creditCardService.create(
                new CreditCardRequest(fixture.account().getId(), "Gold", "VISA", new BigDecimal("5000.00"), 28, 5),
                fixture.ownerId());

        Transaction charge = transactionService.create(
                new TransactionRequest(
                        TransactionKind.CARD_CHARGE,
                        null,
                        card.getId(),
                        fixture.category().getId(),
                        new BigDecimal("400.00"),
                        LocalDate.now(),
                        "Purchase"),
                fixture.ownerId());

        invoiceService.pay(charge.getInvoice().getId(), null, fixture.ownerId());

        UUID settlementId = transactionRepository
                .findByInvoiceIdAndKind(charge.getInvoice().getId(), TransactionKind.INVOICE_PAYMENT)
                .orElseThrow(() -> new AssertionError("No settlement was posted"))
                .getId();

        assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.markPaid(settlementId, true, fixture.ownerId()));
    }

    /**
     * Filing a bill that is due today and has not been paid, in the one call that creates it.
     *
     * The date rule would call this one paid. The caller knows better, and says so on the way in
     * rather than having to correct the row afterwards.
     */
    @Test
    void theCallerCanSayABillIsNotPaidYet() {
        Fixture fixture = base();

        Transaction bill = transactionService.create(
                new TransactionRequest(
                        TransactionKind.ACCOUNT_DEBIT,
                        fixture.account().getId(),
                        null,
                        fixture.category().getId(),
                        new BigDecimal("300.00"),
                        LocalDate.now(),
                        "Water",
                        false),
                fixture.ownerId());

        assertFalse(bill.isPaid());
    }

    /**
     * And is refused it on a card charge, whatever it asks for.
     *
     * A charge is settled through its invoice, all at once. One written unpaid would sit on a
     * reminder with no way to take it off, since ticking a card charge off is refused too.
     */
    @Test
    void aCardChargeIsPaidWhateverTheCallerAsksFor() {
        Fixture fixture = base();
        CreditCard card = creditCardService.create(
                new CreditCardRequest(fixture.account().getId(), "Gold", "VISA", new BigDecimal("5000.00"), 28, 5),
                fixture.ownerId());

        Transaction charge = transactionService.create(
                new TransactionRequest(
                        TransactionKind.CARD_CHARGE,
                        null,
                        card.getId(),
                        fixture.category().getId(),
                        new BigDecimal("300.00"),
                        LocalDate.now(),
                        "Purchase",
                        false),
                fixture.ownerId());

        assertTrue(charge.isPaid());
    }

    /** A debit dated today or earlier is being recorded after the fact, so it is already paid. */
    @Test
    void aDebitRecordedAfterTheFactIsAlreadyPaid() {
        Fixture fixture = base();

        Transaction groceries = transactionService.create(
                new TransactionRequest(
                        TransactionKind.ACCOUNT_DEBIT,
                        fixture.account().getId(),
                        null,
                        fixture.category().getId(),
                        new BigDecimal("80.00"),
                        LocalDate.now().minusDays(1),
                        "Groceries"),
                fixture.ownerId());

        assertTrue(groceries.isPaid());
    }

    /**
     * A one-off is left off the reminder even when it is unpaid.
     *
     * The panel is for the contas fixas — rent, power, internet — which is what the owner asked to
     * be reminded of. The flag itself is general, so this is one predicate away from widening if
     * that turns out to be too narrow.
     */
    @Test
    void aOneOffDebitIsNotOnTheReminder() {
        Fixture fixture = base();

        Transaction future = transactionService.create(
                new TransactionRequest(
                        TransactionKind.ACCOUNT_DEBIT,
                        fixture.account().getId(),
                        null,
                        fixture.category().getId(),
                        new BigDecimal("80.00"),
                        BILL_MONTH.atDay(10),
                        "Something one-off"),
                fixture.ownerId());

        assertFalse(future.isPaid(), "a debit dated ahead has not happened yet");
        assertEquals(0, dashboard(fixture).billsDueCount());
    }

    private DashboardResponse dashboard(Fixture fixture) {
        return dashboardService.forMonth(fixture.ownerId(), BILL_MONTH);
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private record Fixture(UUID ownerId, BankAccount account, Category category) {}

    private Fixture base() {
        UUID ownerId = newOwner();

        BankAccount account = bankAccountService.create(
                new BankAccountRequest("Checking", "Test Bank", AccountType.CHECKING, new BigDecimal("5000.00"), "BRL"),
                ownerId);

        Category category =
                categoryService.create(new CategoryRequest("General", CategoryType.EXPENSE, null, null), ownerId);

        return new Fixture(ownerId, account, category);
    }

    /**
     * An open-ended monthly debit whose first occurrence falls in {@link #BILL_MONTH}.
     *
     * Open-ended rules are materialised three months ahead at once, so every occurrence this
     * creates is dated in the future and none of them counts as paid.
     */
    private Fixture withMonthlyBill(String description, String amount) {
        Fixture fixture = base();

        transactionTemplateService.create(
                new TransactionTemplateRequest(
                        TransactionKind.ACCOUNT_DEBIT,
                        fixture.account().getId(),
                        null,
                        fixture.category().getId(),
                        description,
                        new BigDecimal(amount),
                        RecurrenceType.FIXED_INDEFINITE,
                        BILL_MONTH.atDay(10),
                        null),
                fixture.ownerId());

        return fixture;
    }
}
