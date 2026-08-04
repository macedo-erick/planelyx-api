package br.com.planelyxapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.planelyxapi.domain.BankAccount;
import br.com.planelyxapi.domain.Category;
import br.com.planelyxapi.domain.Transaction;
import br.com.planelyxapi.domain.TransactionTemplate;
import br.com.planelyxapi.domain.enums.IntervalUnit;
import br.com.planelyxapi.domain.enums.RecurrenceType;
import br.com.planelyxapi.domain.enums.TransactionKind;
import br.com.planelyxapi.repository.TransactionRepository;
import br.com.planelyxapi.repository.TransactionTemplateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TemplateOccurrenceGeneratorTest {

    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final TransactionTemplateRepository transactionTemplateRepository =
            mock(TransactionTemplateRepository.class);
    private final InvoiceService invoiceService = mock(InvoiceService.class);
    private final TemplateOccurrenceGenerator generator =
            new TemplateOccurrenceGenerator(transactionRepository, transactionTemplateRepository, invoiceService);

    @Test
    void splitsInstallmentAmountsAcrossOccurrencesWithRemainderOnLast() {
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionTemplate template = TransactionTemplate.builder()
                .id(UUID.randomUUID())
                .ownerId(UUID.randomUUID())
                .kind(TransactionKind.ACCOUNT_DEBIT)
                .bankAccount(BankAccount.builder().id(UUID.randomUUID()).build())
                .category(Category.builder().id(UUID.randomUUID()).build())
                .description("Gym contract")
                .totalAmount(new BigDecimal("100.00"))
                .recurrenceType(RecurrenceType.INSTALLMENT)
                .intervalUnit(IntervalUnit.MONTHLY)
                .startDate(LocalDate.of(2026, 1, 1))
                .totalOccurrences(3)
                .occurrencesGenerated(0)
                .active(true)
                .build();

        generator.generateInitialOccurrences(template);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        List<Transaction> saved = captor.getAllValues();

        assertEquals(new BigDecimal("33.33"), saved.get(0).getAmount());
        assertEquals(new BigDecimal("33.33"), saved.get(1).getAmount());
        assertEquals(new BigDecimal("33.34"), saved.get(2).getAmount());
        assertEquals(1, saved.get(0).getInstallmentNumber());
        assertEquals(3, saved.get(2).getInstallmentNumber());
        assertEquals(LocalDate.of(2026, 3, 1), saved.get(2).getTransactionDate());
        assertEquals(3, template.getOccurrencesGenerated());
        assertEquals(false, template.isActive());
    }

    @Test
    void generatesFixedAmountPerOccurrenceForFixedCount() {
        List<Transaction> savedTransactions = new ArrayList<>();
        when(transactionRepository.save(any())).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            savedTransactions.add(transaction);
            return transaction;
        });

        TransactionTemplate template = TransactionTemplate.builder()
                .id(UUID.randomUUID())
                .ownerId(UUID.randomUUID())
                .kind(TransactionKind.ACCOUNT_DEBIT)
                .bankAccount(BankAccount.builder().id(UUID.randomUUID()).build())
                .category(Category.builder().id(UUID.randomUUID()).build())
                .description("Gym plan")
                .totalAmount(new BigDecimal("50.00"))
                .recurrenceType(RecurrenceType.FIXED_COUNT)
                .intervalUnit(IntervalUnit.MONTHLY)
                .startDate(LocalDate.of(2026, 1, 15))
                .totalOccurrences(2)
                .occurrencesGenerated(0)
                .active(true)
                .build();

        generator.generateInitialOccurrences(template);

        assertEquals(2, savedTransactions.size());
        assertEquals(new BigDecimal("50.00"), savedTransactions.get(0).getAmount());
        assertEquals(new BigDecimal("50.00"), savedTransactions.get(1).getAmount());
        assertEquals(LocalDate.of(2026, 2, 15), savedTransactions.get(1).getTransactionDate());
    }
}
