package com.planelyx.api.service;

import static java.util.Objects.isNull;

import com.planelyx.api.domain.CreditCard;
import com.planelyx.api.domain.Invoice;
import com.planelyx.api.domain.Transaction;
import com.planelyx.api.domain.enums.InvoiceStatus;
import com.planelyx.api.exception.NotFoundException;
import com.planelyx.api.repository.InvoiceRepository;
import com.planelyx.api.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final TransactionRepository transactionRepository;
    private final CreditCardService creditCardService;

    public BillingPeriod resolveBillingPeriod(CreditCard card, LocalDate transactionDate) {
        YearMonth transactionMonth = YearMonth.from(transactionDate);
        LocalDate closingThisMonth = dateForDay(transactionMonth, card.getClosingDay());

        LocalDate periodEnd = transactionDate.isAfter(closingThisMonth)
                ? dateForDay(transactionMonth.plusMonths(1), card.getClosingDay())
                : closingThisMonth;

        YearMonth periodEndMonth = YearMonth.from(periodEnd);
        LocalDate previousClosing = dateForDay(periodEndMonth.minusMonths(1), card.getClosingDay());
        LocalDate periodStart = previousClosing.plusDays(1);

        YearMonth dueMonth = card.getDueDay() <= card.getClosingDay() ? periodEndMonth.plusMonths(1) : periodEndMonth;
        LocalDate dueDate = dateForDay(dueMonth, card.getDueDay());

        return new BillingPeriod(periodStart, periodEnd, dueDate);
    }

    private LocalDate dateForDay(YearMonth yearMonth, int day) {
        int clampedDay = Math.min(day, yearMonth.lengthOfMonth());

        return yearMonth.atDay(clampedDay);
    }

    public Invoice findOrCreateInvoiceForCharge(CreditCard card, LocalDate transactionDate) {
        BillingPeriod period = resolveBillingPeriod(card, transactionDate);

        return invoiceRepository
                .findByCreditCardIdAndBillingPeriodStart(card.getId(), period.start())
                .orElseGet(() -> invoiceRepository.save(Invoice.builder()
                        .creditCard(card)
                        .billingPeriodStart(period.start())
                        .billingPeriodEnd(period.end())
                        .dueDate(period.dueDate())
                        .totalAmount(BigDecimal.ZERO)
                        .status(InvoiceStatus.OPEN)
                        .build()));
    }

    public void recomputeTotal(UUID invoiceId) {
        Invoice invoice = invoiceRepository
                .findById(invoiceId)
                .orElseThrow(() -> new NotFoundException("Invoice not found: " + invoiceId));

        BigDecimal total = transactionRepository.sumAmountByInvoiceId(invoiceId);

        invoice.setTotalAmount(total);

        invoiceRepository.save(invoice);
    }

    public InvoiceStatus derivedStatus(Invoice invoice) {
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return InvoiceStatus.PAID;
        }

        return LocalDate.now().isAfter(invoice.getBillingPeriodEnd()) ? InvoiceStatus.CLOSED : InvoiceStatus.OPEN;
    }

    public List<Invoice> findAll(UUID ownerId, UUID creditCardId, InvoiceStatus status) {
        List<Invoice> invoices = Objects.nonNull(creditCardId)
                ? invoiceRepository.findAllByCreditCardId(requireOwnedCard(creditCardId, ownerId))
                : invoiceRepository.findAllByCreditCardOwnerId(ownerId);

        if (isNull(status)) {
            return invoices;
        }

        return invoices.stream()
                .filter(invoice -> derivedStatus(invoice) == status)
                .toList();
    }

    private UUID requireOwnedCard(UUID creditCardId, UUID ownerId) {
        return creditCardService.findById(creditCardId, ownerId).getId();
    }

    public Invoice findById(UUID id, UUID ownerId) {
        return invoiceRepository
                .findByIdAndCreditCardOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Invoice not found: " + id));
    }

    public List<Transaction> transactionsFor(UUID invoiceId) {
        return transactionRepository.findAllByInvoiceId(invoiceId);
    }

    public Page<Transaction> transactionsFor(UUID invoiceId, Pageable pageable) {
        return transactionRepository.findAllByInvoiceId(invoiceId, pageable);
    }

    /** Total still owed on a card — everything invoiced but not yet paid. */
    public BigDecimal unpaidTotal(UUID creditCardId) {
        return invoiceRepository.sumUnpaidTotalByCreditCardId(creditCardId);
    }

    /** The same figure for every card an owner holds. Cards with nothing outstanding are absent. */
    public Map<UUID, BigDecimal> unpaidTotalsByCard(UUID ownerId) {
        return invoiceRepository.sumUnpaidTotalsByOwnerId(ownerId).stream()
                .collect(Collectors.toMap(
                        InvoiceRepository.CardTotal::getCreditCardId, InvoiceRepository.CardTotal::getTotal));
    }

    public Invoice pay(UUID id, UUID ownerId) {
        Invoice invoice = findById(id, ownerId);

        if (invoice.getStatus() != InvoiceStatus.PAID) {
            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setPaidAt(Instant.now());

            invoiceRepository.save(invoice);
        }

        return invoice;
    }

    public Invoice unpay(UUID id, UUID ownerId) {
        Invoice invoice = findById(id, ownerId);

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            invoice.setStatus(InvoiceStatus.OPEN);
            invoice.setPaidAt(null);

            invoiceRepository.save(invoice);
        }

        return invoice;
    }
}
