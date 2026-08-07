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
import com.planelyx.api.domain.enums.TransactionScope;
import com.planelyx.api.dto.BankAccountRequest;
import com.planelyx.api.dto.CategoryRequest;
import com.planelyx.api.dto.CreditCardRequest;
import com.planelyx.api.dto.DashboardResponse;
import com.planelyx.api.dto.InvoicePaymentRequest;
import com.planelyx.api.dto.TransactionRequest;
import com.planelyx.api.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Settling a card invoice.
 *
 * Paying used to flip a status and nothing else, so the money never left any account: the invoice
 * dropped out of the dashboard's deduction with no debit to replace it, and the total jumped *up*
 * by the amount just paid. These pin the settlement that closes that gap, and the places it must
 * stay out of — it moves a balance, but it is not a second expense on top of the charges it pays.
 */
class InvoicePaymentIntegrationTest extends AbstractIntegrationTest {

    private static final YearMonth DUE_MONTH = YearMonth.of(2026, 9);

    /**
     * A settlement date inside {@link #DUE_MONTH}, for the tests that read that month's figures.
     *
     * They pass it explicitly rather than letting the default apply. The default is now today, and
     * a test whose arithmetic depends on the settlement landing inside a fixed month would start
     * failing on its own once the wall clock walked past it.
     */
    private static final LocalDate SETTLED_ON = LocalDate.of(2026, 9, 5);

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

    /**
     * The regression the old code could not pass: the account has to be the poorer for it, and the
     * total must not move — the deduction simply shifts from the unpaid invoice to the balance.
     */
    @Test
    void payingTakesTheMoneyOutOfTheAccountWithoutMovingTheTotal() {
        Fixture fixture = invoice("400.00");

        DashboardResponse before = dashboard(fixture);

        pay(fixture);

        DashboardResponse after = dashboard(fixture);

        assertAmount("400.00", before.invoicesDueTotal());
        assertEquals(0, after.invoicesDueTotal().signum(), "a paid invoice is no longer owed");

        assertEquals(
                0,
                before.accountBalanceTotal().subtract(new BigDecimal("400.00")).compareTo(after.accountBalanceTotal()),
                "the accounts are 400 poorer");
        assertEquals(0, before.totalBalance().compareTo(after.totalBalance()), "the total does not move");
    }

    /** The charges were counted as spending when the invoice fell due. Paying is not a second one. */
    @Test
    void payingDoesNotAddToTheMonthsSpending() {
        Fixture fixture = invoice("400.00");

        BigDecimal before = dashboard(fixture).expense();

        pay(fixture);

        assertEquals(0, before.compareTo(dashboard(fixture).expense()));
        assertAmount("400.00", before);
    }

    /** The settlement points at the invoice, so it must not be counted as part of it. */
    @Test
    void theInvoiceStillComesToWhatItsChargesCome() {
        Fixture fixture = invoice("400.00");

        invoiceService.pay(fixture.invoice().getId(), null, fixture.ownerId());

        Invoice reloaded = invoiceService.findById(fixture.invoice().getId(), fixture.ownerId());

        assertAmount("400.00", reloaded.getTotalAmount());
        assertEquals(
                1,
                invoiceService.transactionsFor(reloaded.getId()).size(),
                "the settlement pays the invoice, it is not on it");
    }

    @Test
    void unpayingPutsTheMoneyBackAndLeavesNothingBehind() {
        Fixture fixture = invoice("400.00");
        UUID invoiceId = fixture.invoice().getId();

        DashboardResponse before = dashboard(fixture);

        pay(fixture);
        invoiceService.unpay(invoiceId, fixture.ownerId());

        DashboardResponse after = dashboard(fixture);

        assertEquals(0, before.accountBalanceTotal().compareTo(after.accountBalanceTotal()));
        assertEquals(0, before.invoicesDueTotal().compareTo(after.invoicesDueTotal()));
        assertTrue(
                transactionRepository
                        .findByInvoiceIdAndKind(invoiceId, TransactionKind.INVOICE_PAYMENT)
                        .isEmpty(),
                "the settlement is gone");
    }

    /** Guards the unique index: a repeated click must not stack up settlements. */
    @Test
    void payingTwiceSettlesOnce() {
        Fixture fixture = invoice("400.00");
        UUID invoiceId = fixture.invoice().getId();

        pay(fixture);
        pay(fixture);
        invoiceService.unpay(invoiceId, fixture.ownerId());
        pay(fixture);

        assertAmount("600.00", dashboard(fixture).accountBalanceTotal());
    }

    /**
     * With no body, the debit lands on today and comes out of the card's own account.
     *
     * It used to land on the due date. Someone paying by hand pays when they pay, and dating the
     * debit to the vencimento left the money showing in the account on a day it had already gone —
     * so correcting the balance by hand debited the month a second time.
     */
    @Test
    void theDefaultsAreTodayAndTheCardsAccount() {
        Fixture fixture = invoice("400.00");

        invoiceService.pay(fixture.invoice().getId(), null, fixture.ownerId());

        Transaction settlement = settlement(fixture);

        assertEquals(LocalDate.now(), settlement.getTransactionDate());
        assertEquals(LocalDate.now(), settlement.getPurchaseDate());
        assertEquals(fixture.account().getId(), settlement.getBankAccount().getId());
    }

    /**
     * A month whose invoice was settled after it ended still has to deduct that invoice.
     *
     * The status column says paid but not <em>when</em>, and now that paying is no longer pinned to
     * the due date the two can fall in different months. Reading the status alone would drop the
     * deduction from August while the debit that replaces it sits in September — the debt reading
     * as having evaporated, which is the whole thing the settlement was built to prevent.
     */
    @Test
    void anInvoiceSettledAfterTheMonthIsStillOwedInIt() {
        Fixture fixture = invoice("400.00");

        DashboardResponse before = dashboard(fixture);

        invoiceService.pay(
                fixture.invoice().getId(),
                new InvoicePaymentRequest(DUE_MONTH.plusMonths(1).atDay(3), null, null),
                fixture.ownerId());

        DashboardResponse after = dashboard(fixture);

        assertAmount("400.00", after.invoicesDueTotal());
        assertEquals(1, after.invoicesDueCount());
        assertEquals(0, before.totalBalance().compareTo(after.totalBalance()), "the debt did not evaporate");
    }

    /** The API has no translations, so the wording a user reads has to come from the client. */
    @Test
    void theClientsWordingIsKept() {
        Fixture fixture = invoice("400.00");

        invoiceService.pay(
                fixture.invoice().getId(),
                new InvoicePaymentRequest(LocalDate.of(2026, 9, 2), null, "Pagamento da fatura"),
                fixture.ownerId());

        Transaction settlement = settlement(fixture);

        assertEquals("Pagamento da fatura", settlement.getDescription());
        assertEquals(LocalDate.of(2026, 9, 2), settlement.getTransactionDate());
    }

    /**
     * A settlement is derived from its invoice. Editing or deleting one directly would leave the
     * invoice marked paid with the money back in the account, and nothing to explain the gap.
     */
    @Test
    void aSettlementCannotBeEditedOrDeletedOnItsOwn() {
        Fixture fixture = invoice("400.00");

        invoiceService.pay(fixture.invoice().getId(), null, fixture.ownerId());

        UUID settlementId = settlement(fixture).getId();

        assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.delete(settlementId, fixture.ownerId(), TransactionScope.SINGLE));
    }

    /** Nor may one be conjured up by hand — only paying an invoice creates it. */
    @Test
    void aSettlementCannotBeFiledByHand() {
        Fixture fixture = invoice("400.00");

        TransactionRequest request = new TransactionRequest(
                TransactionKind.INVOICE_PAYMENT,
                fixture.account().getId(),
                null,
                fixture.category().getId(),
                new BigDecimal("400.00"),
                LocalDate.of(2026, 9, 5),
                "Sneaky");

        assertThrows(IllegalArgumentException.class, () -> transactionService.create(request, fixture.ownerId()));
    }

    /**
     * A settlement names the account it was paid from, not the card, so the card's own cascade
     * cannot see it — but it points at an invoice that is about to be removed. Deleting a card
     * with a paid invoice is what would otherwise trip the foreign key.
     */
    @Test
    void deletingACardTakesTheSettlementsWithIt() {
        Fixture fixture = invoice("400.00");
        UUID invoiceId = fixture.invoice().getId();

        invoiceService.pay(invoiceId, null, fixture.ownerId());

        creditCardService.delete(fixture.card().getId(), fixture.ownerId());

        assertTrue(
                transactionRepository
                        .findByInvoiceIdAndKind(invoiceId, TransactionKind.INVOICE_PAYMENT)
                        .isEmpty(),
                "the settlement went with the card");
    }

    /** Settles the invoice on {@link #SETTLED_ON}, the date every figure below is read against. */
    private void pay(Fixture fixture) {
        invoiceService.pay(
                fixture.invoice().getId(), new InvoicePaymentRequest(SETTLED_ON, null, null), fixture.ownerId());
    }

    private Transaction settlement(Fixture fixture) {
        return transactionRepository
                .findByInvoiceIdAndKind(fixture.invoice().getId(), TransactionKind.INVOICE_PAYMENT)
                .orElseThrow(() -> new AssertionError("No settlement was posted"));
    }

    private DashboardResponse dashboard(Fixture fixture) {
        return dashboardService.forMonth(fixture.ownerId(), DUE_MONTH);
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private record Fixture(UUID ownerId, BankAccount account, Category category, CreditCard card, Invoice invoice) {}

    /**
     * An account holding 1000, and a card carrying a single charge that falls due in September.
     *
     * The card closes on the 28th and is due on the 5th, so a charge dated 10 August lands on the
     * period ending 28 August, which falls due on 5 September — {@link #DUE_MONTH}, the month
     * every figure here is read in.
     */
    private Fixture invoice(String amount) {
        UUID ownerId = newOwner();

        BankAccount account = bankAccountService.create(
                new BankAccountRequest("Checking", "Test Bank", AccountType.CHECKING, new BigDecimal("1000.00"), "BRL"),
                ownerId);

        Category category =
                categoryService.create(new CategoryRequest("General", CategoryType.EXPENSE, null, null), ownerId);

        CreditCard card = creditCardService.create(
                new CreditCardRequest(account.getId(), "Gold", "VISA", new BigDecimal("5000.00"), 28, 5), ownerId);

        Transaction charge = transactionService.create(
                new TransactionRequest(
                        TransactionKind.CARD_CHARGE,
                        null,
                        card.getId(),
                        category.getId(),
                        new BigDecimal(amount),
                        LocalDate.of(2026, 8, 10),
                        "Purchase"),
                ownerId);

        return new Fixture(
                ownerId,
                account,
                category,
                card,
                invoiceService.findById(charge.getInvoice().getId(), ownerId));
    }
}
