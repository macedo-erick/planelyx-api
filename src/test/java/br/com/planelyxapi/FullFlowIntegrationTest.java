package br.com.planelyxapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import br.com.planelyxapi.domain.BankAccount;
import br.com.planelyxapi.domain.Category;
import br.com.planelyxapi.domain.CreditCard;
import br.com.planelyxapi.domain.Invoice;
import br.com.planelyxapi.domain.Transaction;
import br.com.planelyxapi.domain.enums.AccountType;
import br.com.planelyxapi.domain.enums.CategoryType;
import br.com.planelyxapi.domain.enums.InvoiceStatus;
import br.com.planelyxapi.domain.enums.RecurrenceType;
import br.com.planelyxapi.domain.enums.TransactionKind;
import br.com.planelyxapi.dto.BankAccountRequest;
import br.com.planelyxapi.dto.CategoryRequest;
import br.com.planelyxapi.dto.CreditCardRequest;
import br.com.planelyxapi.dto.PageResponse;
import br.com.planelyxapi.dto.TransactionResponse;
import br.com.planelyxapi.dto.TransactionTemplateRequest;
import br.com.planelyxapi.mapper.TransactionMapper;
import br.com.planelyxapi.repository.TransactionRepository;
import br.com.planelyxapi.service.BankAccountService;
import br.com.planelyxapi.service.CategoryService;
import br.com.planelyxapi.service.CreditCardService;
import br.com.planelyxapi.service.InvoiceService;
import br.com.planelyxapi.service.TransactionService;
import br.com.planelyxapi.service.TransactionTemplateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

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

    @Test
    void invoiceChargeMappingDoesNotFailOnLazyTemplateAssociation() {
        // Mirrors what InvoiceController#transactions does: page an invoice's charges in a
        // @Transactional service call, then map them outside any transaction. Each charge linked
        // to an installment template holds a lazy TransactionTemplate proxy that must not require
        // an open Hibernate session to serialize.
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

        List<Invoice> invoices = invoiceService.findAll(ownerId, card.getId(), null);
        Invoice invoice = invoices.getFirst();

        PageResponse<TransactionResponse> response = PageResponse.of(
                invoiceService.transactionsFor(invoice.getId(), PageRequest.of(0, 25, TransactionService.NEWEST_FIRST)),
                TransactionMapper::toResponse);

        assertEquals(1, response.content().size());
        assertEquals(1, response.totalElements());
        assertEquals(3, response.content().getFirst().totalInstallments());
    }
}
