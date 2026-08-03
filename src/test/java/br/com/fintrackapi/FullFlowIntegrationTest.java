package br.com.fintrackapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import br.com.fintrackapi.domain.BankAccount;
import br.com.fintrackapi.domain.Category;
import br.com.fintrackapi.domain.CreditCard;
import br.com.fintrackapi.domain.Invoice;
import br.com.fintrackapi.domain.Transaction;
import br.com.fintrackapi.domain.enums.AccountType;
import br.com.fintrackapi.domain.enums.CategoryType;
import br.com.fintrackapi.domain.enums.InvoiceStatus;
import br.com.fintrackapi.domain.enums.RecurrenceType;
import br.com.fintrackapi.domain.enums.TransactionKind;
import br.com.fintrackapi.dto.BankAccountRequest;
import br.com.fintrackapi.dto.CategoryRequest;
import br.com.fintrackapi.dto.CreditCardRequest;
import br.com.fintrackapi.dto.TransactionTemplateRequest;
import br.com.fintrackapi.repository.TransactionRepository;
import br.com.fintrackapi.service.BankAccountService;
import br.com.fintrackapi.service.CategoryService;
import br.com.fintrackapi.service.CreditCardService;
import br.com.fintrackapi.service.InvoiceService;
import br.com.fintrackapi.service.TransactionTemplateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FullFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BankAccountService bankAccountService;

    @Autowired
    private CreditCardService creditCardService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TransactionTemplateService transactionTemplateService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void creatingAnInstallmentTemplateGeneratesOccurrencesAndAnInvoice() {
        UUID ownerId = UUID.randomUUID();

        BankAccount account = bankAccountService.create(
                new BankAccountRequest("Checking", "Test Bank", AccountType.CHECKING, BigDecimal.TEN, "BRL"), ownerId);

        CreditCard card = creditCardService.create(
                new CreditCardRequest(account.getId(), "Gold Card", "VISA", new BigDecimal("5000.00"), 10, 17),
                ownerId);

        Category category =
                categoryService.create(new CategoryRequest("Electronics", CategoryType.EXPENSE, null, null), ownerId);

        transactionTemplateService.create(
                new TransactionTemplateRequest(
                        TransactionKind.CARD_CHARGE,
                        null,
                        card.getId(),
                        category.getId(),
                        "New laptop",
                        new BigDecimal("300.00"),
                        RecurrenceType.INSTALLMENT,
                        LocalDate.of(2026, 8, 15),
                        3),
                ownerId);

        List<Transaction> generated = transactionRepository.findAll().stream()
                .filter(transaction -> transaction.getKind() == TransactionKind.CARD_CHARGE)
                .toList();
        assertEquals(3, generated.size());

        Transaction first = generated.stream()
                .filter(t -> t.getInstallmentNumber() == 1)
                .findFirst()
                .orElseThrow();
        assertNotNull(first.getInvoice());

        List<Invoice> invoices = invoiceService.findAll(ownerId, card.getId(), null);
        assertEquals(3, invoices.size(), "each monthly installment lands in its own billing cycle");

        BigDecimal total = generated.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("300.00"), total);

        BigDecimal invoicedTotal =
                invoices.stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("300.00"), invoicedTotal);

        Invoice firstInvoice = invoices.getFirst();
        InvoiceStatus derived = invoiceService.derivedStatus(firstInvoice);
        assertEquals(true, derived == InvoiceStatus.CLOSED || derived == InvoiceStatus.OPEN);
    }
}
