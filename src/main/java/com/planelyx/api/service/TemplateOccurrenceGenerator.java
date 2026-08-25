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
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TemplateOccurrenceGenerator {

    private static final int INDEFINITE_HORIZON_MONTHS = 12;

    private final TransactionRepository transactionRepository;
    private final TransactionTemplateRepository transactionTemplateRepository;
    private final InvoiceService invoiceService;

    public void generateInitialOccurrences(TransactionTemplate template) {
        log.debug(
                "Generating initial occurrences for template {} ({}) owner={}",
                template.getId(),
                template.getRecurrenceType(),
                template.getOwnerId());

        if (template.getRecurrenceType() == RecurrenceType.FIXED_INDEFINITE) {
            generateThroughHorizon(template);
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

    /**
     * Runs monthly and unattended, so it says so in the log either way: a run that
     * produced nothing and a run that never happened are otherwise identical from the
     * outside.
     */
    @Scheduled(cron = "0 0 2 1 * *")
    public void topUpIndefiniteTemplates() {
        long startedAt = System.currentTimeMillis();
        int toppedUp = 0;

        log.info("Indefinite template top-up starting");

        for (TransactionTemplate template :
                transactionTemplateRepository.findAllByActiveTrueAndRecurrenceType(RecurrenceType.FIXED_INDEFINITE)) {
            int generated = generateThroughHorizon(template);

            if (generated == 0) {
                continue;
            }

            transactionTemplateRepository.save(template);
            toppedUp++;

            log.debug(
                    "Topped up template {} with {} occurrences, now at {}",
                    template.getId(),
                    generated,
                    template.getOccurrencesGenerated());
        }

        log.info(
                "Indefinite template top-up finished: {} templates in {}ms",
                toppedUp,
                System.currentTimeMillis() - startedAt);
    }

    private int generateThroughHorizon(TransactionTemplate template) {
        int target = occurrencesThroughHorizon(template);
        int generated = template.getOccurrencesGenerated();

        for (int occurrence = generated + 1; occurrence <= target; occurrence++) {
            generateOccurrence(template, occurrence);
        }

        if (target > generated) {
            template.setOccurrencesGenerated(target);
        }

        return Math.max(target - generated, 0);
    }

    private int occurrencesThroughHorizon(TransactionTemplate template) {
        YearMonth start = YearMonth.from(template.getStartDate());
        YearMonth horizonEnd = YearMonth.from(LocalDate.now()).plusMonths(INDEFINITE_HORIZON_MONTHS - 1L);

        long spanned = ChronoUnit.MONTHS.between(start, horizonEnd) + 1;

        return (int) Math.max(INDEFINITE_HORIZON_MONTHS, spanned);
    }

    private void generateOccurrence(TransactionTemplate template, int occurrenceNumber) {
        boolean installment = template.getRecurrenceType() == RecurrenceType.INSTALLMENT;
        LocalDate occurrenceDate = template.getStartDate().plusMonths(occurrenceNumber - 1L);
        BigDecimal amount = resolveAmount(template, occurrenceNumber);

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
