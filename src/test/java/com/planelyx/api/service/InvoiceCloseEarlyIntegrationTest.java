package com.planelyx.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.planelyx.api.AbstractIntegrationTest;
import com.planelyx.api.domain.BankAccount;
import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.CreditCard;
import com.planelyx.api.domain.Invoice;
import com.planelyx.api.domain.Transaction;
import com.planelyx.api.domain.enums.AccountType;
import com.planelyx.api.domain.enums.CategoryType;
import com.planelyx.api.domain.enums.InvoiceStatus;
import com.planelyx.api.domain.enums.TransactionKind;
import com.planelyx.api.dto.BankAccountRequest;
import com.planelyx.api.dto.CategoryRequest;
import com.planelyx.api.dto.CreditCardRequest;
import com.planelyx.api.dto.TransactionRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Closing an invoice before its calendar closing day, and undoing it.
 *
 * The card's {@code closingDay} is fixed, but a real card sometimes turns over on a different
 * day. These pin what happens to the invoice itself, and — the part that actually fixes
 * anything — what happens to the charges around the moment it closes.
 */
class InvoiceCloseEarlyIntegrationTest extends AbstractIntegrationTest {

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

    @Test
    void closingEarlySetsTheOverrideAndTheStatusBecomesClosed() {
        Fixture fixture = openInvoiceToday("100.00");

        Invoice closed = invoiceService.closeEarly(fixture.invoice().getId(), fixture.ownerId());

        assertEquals(LocalDate.now(), closed.getClosedEarlyOn());
        assertEquals(InvoiceStatus.CLOSED, invoiceService.derivedStatus(closed));
    }

    @Test
    void aChargeDatedAfterTheCutoffGoesToTheNextInvoice() {
        Fixture fixture = openInvoiceToday("100.00");
        UUID originalInvoiceId = fixture.invoice().getId();

        invoiceService.closeEarly(originalInvoiceId, fixture.ownerId());

        Transaction nextCharge = charge(fixture, "50.00", LocalDate.now().plusDays(1));

        assertNotEquals(originalInvoiceId, nextCharge.getInvoice().getId());
        assertEquals(
                fixture.invoice().getBillingPeriodEnd().plusDays(1),
                nextCharge.getInvoice().getBillingPeriodStart());
    }

    @Test
    void chargesAlreadyFiledPastTheCutoffAreMovedWhenClosing() {
        Fixture fixture = openInvoiceToday("100.00");
        UUID originalInvoiceId = fixture.invoice().getId();

        Transaction deadZoneCharge = charge(fixture, "40.00", LocalDate.now().plusDays(1));
        assertEquals(originalInvoiceId, deadZoneCharge.getInvoice().getId(), "filed before the close, still on it");

        invoiceService.closeEarly(originalInvoiceId, fixture.ownerId());

        Transaction reloaded = transactionService.findById(deadZoneCharge.getId(), fixture.ownerId());
        assertNotEquals(originalInvoiceId, reloaded.getInvoice().getId(), "moved off the invoice that closed");

        Invoice original = invoiceService.findById(originalInvoiceId, fixture.ownerId());
        assertAmount("100.00", original.getTotalAmount());

        Invoice next = invoiceService.findById(reloaded.getInvoice().getId(), fixture.ownerId());
        assertAmount("40.00", next.getTotalAmount());
    }

    @Test
    void anAlreadyClosedInvoiceCannotBeClosedEarlyAgain() {
        Fixture fixture = openInvoiceToday("100.00");

        invoiceService.closeEarly(fixture.invoice().getId(), fixture.ownerId());

        assertThrows(
                IllegalStateException.class,
                () -> invoiceService.closeEarly(fixture.invoice().getId(), fixture.ownerId()));
    }

    @Test
    void aPaidInvoiceCannotBeClosedEarly() {
        Fixture fixture = openInvoiceToday("100.00");

        invoiceService.pay(fixture.invoice().getId(), null, fixture.ownerId());

        assertThrows(
                IllegalStateException.class,
                () -> invoiceService.closeEarly(fixture.invoice().getId(), fixture.ownerId()));
    }

    @Test
    void reopeningClearsTheOverrideAndTheStatusGoesBackToOpen() {
        Fixture fixture = openInvoiceToday("100.00");

        invoiceService.closeEarly(fixture.invoice().getId(), fixture.ownerId());
        Invoice reopened = invoiceService.reopen(fixture.invoice().getId(), fixture.ownerId());

        assertNull(reopened.getClosedEarlyOn());
        assertEquals(InvoiceStatus.OPEN, invoiceService.derivedStatus(reopened));
    }

    @Test
    void reopeningDoesNotPullBackChargesAlreadyMoved() {
        Fixture fixture = openInvoiceToday("100.00");
        UUID originalInvoiceId = fixture.invoice().getId();

        Transaction deadZoneCharge = charge(fixture, "40.00", LocalDate.now().plusDays(1));

        invoiceService.closeEarly(originalInvoiceId, fixture.ownerId());
        UUID movedToInvoiceId = transactionService
                .findById(deadZoneCharge.getId(), fixture.ownerId())
                .getInvoice()
                .getId();

        invoiceService.reopen(originalInvoiceId, fixture.ownerId());

        Transaction stillMoved = transactionService.findById(deadZoneCharge.getId(), fixture.ownerId());
        assertEquals(movedToInvoiceId, stillMoved.getInvoice().getId(), "a reopen undoes the status, not the move");
    }

    @Test
    void reopeningAnInvoiceThatWasNeverClosedEarlyIsANoOp() {
        Fixture fixture = openInvoiceToday("100.00");

        Invoice unchanged = invoiceService.reopen(fixture.invoice().getId(), fixture.ownerId());

        assertNull(unchanged.getClosedEarlyOn());
        assertEquals(InvoiceStatus.OPEN, invoiceService.derivedStatus(unchanged));
    }

    @Test
    void reopeningAPaidInvoiceIsANoOp() {
        Fixture fixture = openInvoiceToday("100.00");

        invoiceService.pay(fixture.invoice().getId(), null, fixture.ownerId());
        Invoice unchanged = invoiceService.reopen(fixture.invoice().getId(), fixture.ownerId());

        assertEquals(InvoiceStatus.PAID, invoiceService.derivedStatus(unchanged));
    }

    private Transaction charge(Fixture fixture, String amount, LocalDate transactionDate) {
        return transactionService.create(
                new TransactionRequest(
                        TransactionKind.CARD_CHARGE,
                        null,
                        fixture.card().getId(),
                        fixture.category().getId(),
                        new BigDecimal(amount),
                        transactionDate,
                        "Purchase"),
                fixture.ownerId());
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private record Fixture(UUID ownerId, BankAccount account, Category category, CreditCard card, Invoice invoice) {}

    /**
     * An account and a card whose invoice, opened by a charge dated today, closes at least two
     * days from now — whatever today happens to be when the suite runs.
     *
     * The closing day is picked three days out when there is room left in the month; otherwise
     * — the last couple of days of a month — it is pinned to the 3rd, which charge rolls past
     * into next month, landing on a period that only just started and so still has weeks of
     * room. Either way there is always at least a one-day dead zone to migrate in the tests that
     * need one.
     */
    private Fixture openInvoiceToday(String amount) {
        UUID ownerId = newOwner();
        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.from(today);

        int headroomDay = today.getDayOfMonth() + 3;
        int closingDay = headroomDay <= thisMonth.lengthOfMonth() ? headroomDay : 3;

        BankAccount account = bankAccountService.create(
                new BankAccountRequest("Checking", "Test Bank", AccountType.CHECKING, new BigDecimal("1000.00"), "BRL"),
                ownerId);

        Category category =
                categoryService.create(new CategoryRequest("General", CategoryType.EXPENSE, null, null), ownerId);

        CreditCard card = creditCardService.create(
                new CreditCardRequest(account.getId(), "Gold", "VISA", new BigDecimal("5000.00"), closingDay, 10),
                ownerId);

        Transaction opening = transactionService.create(
                new TransactionRequest(
                        TransactionKind.CARD_CHARGE,
                        null,
                        card.getId(),
                        category.getId(),
                        new BigDecimal(amount),
                        today,
                        "Purchase"),
                ownerId);

        return new Fixture(
                ownerId,
                account,
                category,
                card,
                invoiceService.findById(opening.getInvoice().getId(), ownerId));
    }
}
