package com.planelyx.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.planelyx.api.domain.BankAccount;
import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.CreditCard;
import com.planelyx.api.domain.Invoice;
import com.planelyx.api.domain.Transaction;
import com.planelyx.api.domain.enums.AccountType;
import com.planelyx.api.domain.enums.CategoryType;
import com.planelyx.api.domain.enums.InvoiceStatus;
import com.planelyx.api.domain.enums.RecurrenceType;
import com.planelyx.api.domain.enums.TransactionKind;
import com.planelyx.api.dto.BankAccountRequest;
import com.planelyx.api.dto.CategoryRequest;
import com.planelyx.api.dto.CreditCardRequest;
import com.planelyx.api.dto.PageResponse;
import com.planelyx.api.dto.TransactionResponse;
import com.planelyx.api.dto.TransactionTemplateRequest;
import com.planelyx.api.mapper.TransactionMapper;
import com.planelyx.api.repository.TransactionRepository;
import com.planelyx.api.service.BankAccountService;
import com.planelyx.api.service.CategoryService;
import com.planelyx.api.service.CreditCardService;
import com.planelyx.api.service.InvoiceService;
import com.planelyx.api.service.TransactionService;
import com.planelyx.api.service.TransactionTemplateService;
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
        UUID ownerId = newOwner();

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
        UUID ownerId = newOwner();

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
