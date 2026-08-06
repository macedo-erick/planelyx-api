package com.planelyx.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.planelyx.api.AbstractIntegrationTest;
import com.planelyx.api.domain.BankAccount;
import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.CreditCard;
import com.planelyx.api.domain.Invoice;
import com.planelyx.api.domain.SystemCategories;
import com.planelyx.api.domain.Transaction;
import com.planelyx.api.domain.enums.AccountType;
import com.planelyx.api.domain.enums.CategoryType;
import com.planelyx.api.domain.enums.TransactionKind;
import com.planelyx.api.domain.enums.TransactionScope;
import com.planelyx.api.dto.BankAccountRequest;
import com.planelyx.api.dto.CategoryRequest;
import com.planelyx.api.dto.CreditCardRequest;
import com.planelyx.api.dto.TransactionRequest;
import com.planelyx.api.exception.NotFoundException;
import com.planelyx.api.repository.CreditCardRepository;
import com.planelyx.api.repository.InvoiceRepository;
import com.planelyx.api.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Removing a card, an account or an invoice.
 *
 * Nothing in the schema cascades, so each of these used to fail on whatever still referenced the
 * row. What is worth pinning is that the delete now succeeds *and* leaves nothing orphaned behind
 * it — an invoice with no charges, or a charge with no card, would keep turning up in totals.
 */
class DeletionIntegrationTest extends AbstractIntegrationTest {

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
    private CreditCardRepository creditCardRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void deletingAnInvoiceTakesItsChargesWithIt() {
        Fixture fixture = cardWithCharge(new BigDecimal("120.00"));
        UUID invoiceId = fixture.invoice().getId();

        invoiceService.delete(invoiceId, fixture.ownerId());

        assertTrue(invoiceRepository.findById(invoiceId).isEmpty(), "the invoice should be gone");
        assertTrue(transactionRepository.findAllByInvoiceId(invoiceId).isEmpty(), "its charges should be gone");
        assertThrows(
                NotFoundException.class,
                () -> transactionService.findById(fixture.charge().getId(), fixture.ownerId()));
    }

    @Test
    void deletingAnInvoiceOwnedBySomeoneElseIsRefused() {
        Fixture fixture = cardWithCharge(new BigDecimal("50.00"));
        UUID invoiceId = fixture.invoice().getId();

        assertThrows(NotFoundException.class, () -> invoiceService.delete(invoiceId, UUID.randomUUID()));
    }

    @Test
    void deletingACardTakesItsInvoicesAndChargesWithIt() {
        Fixture fixture = cardWithCharge(new BigDecimal("200.00"));
        UUID cardId = fixture.card().getId();

        creditCardService.delete(cardId, fixture.ownerId());

        assertTrue(creditCardRepository.findById(cardId).isEmpty(), "the card should be gone");
        assertTrue(invoiceRepository.findAllByCreditCardId(cardId).isEmpty(), "its invoices should be gone");
        assertTrue(
                transactionRepository
                        .findAllByInvoiceId(fixture.invoice().getId())
                        .isEmpty(),
                "its charges should be gone");
    }

    @Test
    void deletingAnAccountTakesItsCardsAndTransactionsWithIt() {
        Fixture fixture = cardWithCharge(new BigDecimal("75.00"));
        UUID accountId = fixture.account().getId();
        UUID cardId = fixture.card().getId();

        Transaction debit = transactionService.create(
                new TransactionRequest(
                        TransactionKind.ACCOUNT_DEBIT,
                        accountId,
                        null,
                        fixture.category().getId(),
                        new BigDecimal("30.00"),
                        LocalDate.now(),
                        "Groceries"),
                fixture.ownerId());

        bankAccountService.delete(accountId, fixture.ownerId());

        assertThrows(NotFoundException.class, () -> bankAccountService.findById(accountId, fixture.ownerId()));
        assertTrue(creditCardRepository.findById(cardId).isEmpty(), "the card drawn on it should be gone");
        assertTrue(invoiceRepository.findAllByCreditCardId(cardId).isEmpty(), "the card's invoices should be gone");
        assertTrue(transactionRepository.findById(debit.getId()).isEmpty(), "its own transactions should be gone");
    }

    @Test
    void deletingAnAccountLeavesAnotherOwnersDataAlone() {
        Fixture mine = cardWithCharge(new BigDecimal("10.00"));
        Fixture theirs = cardWithCharge(new BigDecimal("20.00"));

        bankAccountService.delete(mine.account().getId(), mine.ownerId());

        assertEquals(
                theirs.card().getId(),
                creditCardService
                        .findById(theirs.card().getId(), theirs.ownerId())
                        .getId());
        assertEquals(
                1,
                transactionRepository
                        .findAllByInvoiceId(theirs.invoice().getId())
                        .size());
    }

    @Test
    void aTransactionMayNotBeFiledAgainstAnAdjustmentCategory() {
        Fixture fixture = cardWithCharge(new BigDecimal("10.00"));

        TransactionRequest request = new TransactionRequest(
                TransactionKind.ACCOUNT_DEBIT,
                fixture.account().getId(),
                null,
                SystemCategories.ADJUSTMENT_EXPENSE,
                new BigDecimal("5.00"),
                LocalDate.now(),
                "Sneaking one in");

        assertThrows(IllegalArgumentException.class, () -> transactionService.create(request, fixture.ownerId()));
    }

    /** Deleting the charge on its own leaves the invoice standing, just emptied. */
    @Test
    void deletingTheLastChargeEmptiesTheInvoiceWithoutRemovingIt() {
        Fixture fixture = cardWithCharge(new BigDecimal("90.00"));

        transactionService.delete(fixture.charge().getId(), fixture.ownerId(), TransactionScope.SINGLE);

        Invoice invoice = invoiceService.findById(fixture.invoice().getId(), fixture.ownerId());

        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getTotalAmount()));
    }

    private record Fixture(
            UUID ownerId,
            BankAccount account,
            Category category,
            CreditCard card,
            Transaction charge,
            Invoice invoice) {}

    /** A fresh owner with an account, a card drawn on it, and one charge sitting on an invoice. */
    private Fixture cardWithCharge(BigDecimal amount) {
        UUID ownerId = UUID.randomUUID();

        BankAccount account = bankAccountService.create(
                new BankAccountRequest("Checking", "Test Bank", AccountType.CHECKING, BigDecimal.ZERO, "BRL"), ownerId);

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
                        amount,
                        LocalDate.now(),
                        "Purchase"),
                ownerId);

        return new Fixture(
                ownerId,
                account,
                category,
                card,
                charge,
                invoiceService.findById(charge.getInvoice().getId(), ownerId));
    }
}
