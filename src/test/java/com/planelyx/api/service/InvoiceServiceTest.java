package com.planelyx.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.planelyx.api.domain.CreditCard;
import com.planelyx.api.domain.Invoice;
import com.planelyx.api.domain.enums.InvoiceStatus;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InvoiceServiceTest {

    private final InvoiceService invoiceService = new InvoiceService(null, null, null, null, null);

    @Test
    void resolvesBillingPeriodAfterClosingDay() {
        CreditCard card = CreditCard.builder().closingDay(10).dueDay(17).build();

        BillingPeriod period = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 15));

        assertEquals(LocalDate.of(2026, 8, 10), period.start());
        assertEquals(LocalDate.of(2026, 9, 9), period.end());
        assertEquals(LocalDate.of(2026, 9, 17), period.dueDate());
    }

    @Test
    void resolvesBillingPeriodBeforeClosingDay() {
        CreditCard card = CreditCard.builder().closingDay(10).dueDay(17).build();

        BillingPeriod period = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 5));

        assertEquals(LocalDate.of(2026, 7, 10), period.start());
        assertEquals(LocalDate.of(2026, 8, 9), period.end());
        assertEquals(LocalDate.of(2026, 8, 17), period.dueDate());
    }

    /**
     * The closing day is not the last day of the invoice it names — it is the first day of the
     * next one. A real card has already cut off by the time the day is through, so a charge dated
     * on it is too late for the invoice that is closing.
     */
    @Test
    void chargeOnTheClosingDayStartsTheNextPeriodRatherThanEndingThisOne() {
        CreditCard card = CreditCard.builder().closingDay(10).dueDay(17).build();

        BillingPeriod period = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 10));

        assertEquals(LocalDate.of(2026, 8, 10), period.start());
        assertEquals(LocalDate.of(2026, 9, 9), period.end());
        assertEquals(LocalDate.of(2026, 9, 17), period.dueDate());
    }

    @Test
    void dueDateRollsToNextMonthWhenDueDayNotAfterClosingDay() {
        CreditCard card = CreditCard.builder().closingDay(20).dueDay(5).build();

        BillingPeriod period = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 25));

        assertEquals(LocalDate.of(2026, 9, 19), period.end());
        assertEquals(LocalDate.of(2026, 10, 5), period.dueDate());
    }

    @Test
    void clampsClosingDayToShorterMonths() {
        CreditCard card = CreditCard.builder().closingDay(31).dueDay(10).build();

        BillingPeriod period = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 2, 20));

        assertEquals(LocalDate.of(2026, 2, 27), period.end());
    }

    /**
     * A card closing on the 28th and due on the 5th, walked across a closing boundary.
     *
     * The dates below are the ones an owner described from their own statement: everything from
     * 28 Jul through 27 Aug is on the invoice paid 5 Sep, and 28 Aug already starts the one paid
     * 5 Oct — the same day the card actually closes, not the day after. The period that invoice
     * closes in (August) is not the month it is known by (September) — see
     * {@link com.planelyx.api.dto.InvoiceResponse#referenceMonth()}.
     */
    @Test
    void assignsChargesAroundAnEndOfMonthClosingDay() {
        CreditCard card = CreditCard.builder().closingDay(28).dueDay(5).build();

        BillingPeriod beforeClosing = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 7, 30));
        assertEquals(LocalDate.of(2026, 7, 28), beforeClosing.start());
        assertEquals(LocalDate.of(2026, 8, 27), beforeClosing.end());
        assertEquals(LocalDate.of(2026, 9, 5), beforeClosing.dueDate());

        BillingPeriod midPeriod = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 15));
        assertEquals(LocalDate.of(2026, 7, 28), midPeriod.start());
        assertEquals(LocalDate.of(2026, 8, 27), midPeriod.end());
        assertEquals(LocalDate.of(2026, 9, 5), midPeriod.dueDate());

        BillingPeriod onClosingDay = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 28));
        assertEquals(LocalDate.of(2026, 8, 28), onClosingDay.start());
        assertEquals(LocalDate.of(2026, 9, 27), onClosingDay.end());
        assertEquals(LocalDate.of(2026, 10, 5), onClosingDay.dueDate());

        BillingPeriod dayAfterClosing = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 29));
        assertEquals(LocalDate.of(2026, 8, 28), dayAfterClosing.start());
        assertEquals(LocalDate.of(2026, 9, 27), dayAfterClosing.end());
        assertEquals(LocalDate.of(2026, 10, 5), dayAfterClosing.dueDate());
    }

    /**
     * Clamping the closing and due days into a short February used to collide (both landing on
     * the 28th), forcing the due date into the month after. Now that the period ends the day
     * before the closing day rather than on it, the clamped due day no longer lands on the
     * period's last day, so it resolves within the same month without needing that fallback.
     */
    @Test
    void dueDateAfterClampingInAShortFebruary() {
        CreditCard card = CreditCard.builder().closingDay(29).dueDay(30).build();

        BillingPeriod period = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 2, 10));

        assertEquals(LocalDate.of(2026, 2, 27), period.end());
        assertEquals(LocalDate.of(2026, 2, 28), period.dueDate());
    }

    @Test
    void dueDateStaysInTheClosingMonthWhenItComesAfterClosing() {
        CreditCard card = CreditCard.builder().closingDay(10).dueDay(17).build();

        BillingPeriod period = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 5));

        assertEquals(LocalDate.of(2026, 8, 9), period.end());
        assertEquals(LocalDate.of(2026, 8, 17), period.dueDate());
    }

    /** An early close overrides the calendar even while the period is nowhere near over. */
    @Test
    void derivedStatusIsClosedWhenClosedEarlyEvenBeforeTheBillingPeriodEnds() {
        Invoice invoice = Invoice.builder()
                .billingPeriodEnd(LocalDate.now().plusMonths(1))
                .closedEarlyOn(LocalDate.now())
                .build();

        assertEquals(InvoiceStatus.CLOSED, invoiceService.derivedStatus(invoice));
    }

    /** Paid wins over everything, early close included — a settled invoice stays settled. */
    @Test
    void derivedStatusIsPaidEvenWhenClosedEarly() {
        Invoice invoice = Invoice.builder()
                .paidAt(Instant.now())
                .billingPeriodEnd(LocalDate.now().plusMonths(1))
                .closedEarlyOn(LocalDate.now())
                .build();

        assertEquals(InvoiceStatus.PAID, invoiceService.derivedStatus(invoice));
    }
}
