package com.planelyx.api.service;

import com.planelyx.api.domain.Invoice;
import com.planelyx.api.domain.Transaction;
import com.planelyx.api.domain.TransactionTemplate;
import com.planelyx.api.domain.enums.RecurrenceType;
import com.planelyx.api.domain.enums.TransactionKind;
import com.planelyx.api.repository.TransactionRepository;
import com.planelyx.api.repository.TransactionTemplateRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class TemplateOccurrenceGenerator {

    private static final int INDEFINITE_INITIAL_BUFFER = 3;

    private final TransactionRepository transactionRepository;
    private final TransactionTemplateRepository transactionTemplateRepository;
    private final InvoiceService invoiceService;

    public void generateInitialOccurrences(TransactionTemplate template) {
        if (template.getRecurrenceType() == RecurrenceType.FIXED_INDEFINITE) {
            for (int occurrence = 1; occurrence <= INDEFINITE_INITIAL_BUFFER; occurrence++) {
                generateOccurrence(template, occurrence);
            }

            template.setOccurrencesGenerated(INDEFINITE_INITIAL_BUFFER);
        } else {
            int totalOccurrences = template.getTotalOccurrences();

            for (int occurrence = 1; occurrence <= totalOccurrences; occurrence++) {
                generateOccurrence(template, occurrence);
            }

            template.setOccurrencesGenerated(totalOccurrences);
            template.setActive(false);
        }

        transactionTemplateRepository.save(template);
    }

    @Scheduled(cron = "0 0 2 1 * *")
    public void topUpIndefiniteTemplates() {
        for (TransactionTemplate template :
                transactionTemplateRepository.findAllByActiveTrueAndRecurrenceType(RecurrenceType.FIXED_INDEFINITE)) {
            int nextOccurrence = template.getOccurrencesGenerated() + 1;

            generateOccurrence(template, nextOccurrence);

            template.setOccurrencesGenerated(nextOccurrence);

            transactionTemplateRepository.save(template);
        }
    }

    private void generateOccurrence(TransactionTemplate template, int occurrenceNumber) {
        boolean installment = template.getRecurrenceType() == RecurrenceType.INSTALLMENT;
        LocalDate occurrenceDate = template.getStartDate().plusMonths(occurrenceNumber - 1L);
        BigDecimal amount = resolveAmount(template, occurrenceNumber);

        // Only an installment is one purchase spread across several entries, so only there does the
        // purchase date differ from the occurrence. Every other rule has a start date too, but on a
        // monthly subscription that is when the rule began, not when this month's charge was made.
        LocalDate purchaseDate = installment ? template.getStartDate() : occurrenceDate;

        Transaction transaction = Transaction.builder()
                .ownerId(template.getOwnerId())
                .kind(template.getKind())
                .bankAccount(template.getBankAccount())
                .creditCard(template.getCreditCard())
                .category(template.getCategory())
                .template(template)
                .installmentNumber(installment ? occurrenceNumber : null)
                .amount(amount)
                .transactionDate(occurrenceDate)
                .purchaseDate(purchaseDate)
                .description(template.getDescription())
                // A rule materialises months of occurrences at once, so most of them are bills that
                // have not been paid yet. That is what the dashboard reminds the owner of.
                .paid(TransactionService.settledOnCreation(template.getKind(), occurrenceDate))
                .build();

        Transaction saved = transactionRepository.save(transaction);

        if (template.getKind() == TransactionKind.CARD_CHARGE) {
            Invoice invoice = invoiceService.findOrCreateInvoiceForCharge(template.getCreditCard(), occurrenceDate);

            saved.setInvoice(invoice);

            transactionRepository.save(saved);
            invoiceService.recomputeTotal(invoice.getId());
        }
    }

    private BigDecimal resolveAmount(TransactionTemplate template, int occurrenceNumber) {
        if (template.getRecurrenceType() != RecurrenceType.INSTALLMENT) {
            return template.getTotalAmount();
        }

        int totalOccurrences = template.getTotalOccurrences();
        BigDecimal base = template.getTotalAmount().divide(BigDecimal.valueOf(totalOccurrences), 2, RoundingMode.DOWN);

        if (occurrenceNumber < totalOccurrences) {
            return base;
        }

        BigDecimal allocatedBeforeLast = base.multiply(BigDecimal.valueOf(totalOccurrences - 1L));

        return template.getTotalAmount().subtract(allocatedBeforeLast);
    }
}
