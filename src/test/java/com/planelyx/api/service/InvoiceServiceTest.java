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

        assertEquals(LocalDate.of(2026, 8, 11), period.start());
        assertEquals(LocalDate.of(2026, 9, 10), period.end());
        assertEquals(LocalDate.of(2026, 9, 17), period.dueDate());
    }

    @Test
    void resolvesBillingPeriodBeforeClosingDay() {
        CreditCard card = CreditCard.builder().closingDay(10).dueDay(17).build();

        BillingPeriod period = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 5));

        assertEquals(LocalDate.of(2026, 7, 11), period.start());
        assertEquals(LocalDate.of(2026, 8, 10), period.end());
        assertEquals(LocalDate.of(2026, 8, 17), period.dueDate());
    }

    /**
     * The closing day is still the last day of the invoice it names — that is the fechamento the
     * owner reads — but a charge dated on it is too late for that invoice: a real card has
     * already cut off by then. It goes onto the next period, whose dates are untouched by this.
     */
    @Test
    void chargeOnTheClosingDayGoesOntoTheNextPeriod() {
        CreditCard card = CreditCard.builder().closingDay(10).dueDay(17).build();

        BillingPeriod period = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 10));

        assertEquals(LocalDate.of(2026, 8, 11), period.start());
        assertEquals(LocalDate.of(2026, 9, 10), period.end());
        assertEquals(LocalDate.of(2026, 9, 17), period.dueDate());
    }

    /** Same rule when the closing day is clamped: 28 Feb is when a card closing on the 31st closes. */
    @Test
    void chargeOnAClampedClosingDayGoesOntoTheNextPeriod() {
        CreditCard card = CreditCard.builder().closingDay(31).dueDay(10).build();

        BillingPeriod period = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 2, 28));

        assertEquals(LocalDate.of(2026, 3, 1), period.start());
        assertEquals(LocalDate.of(2026, 3, 31), period.end());
        assertEquals(LocalDate.of(2026, 4, 10), period.dueDate());
    }

    @Test
    void dueDateRollsToNextMonthWhenDueDayNotAfterClosingDay() {
        CreditCard card = CreditCard.builder().closingDay(20).dueDay(5).build();

        BillingPeriod period = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 25));

        assertEquals(LocalDate.of(2026, 9, 20), period.end());
        assertEquals(LocalDate.of(2026, 10, 5), period.dueDate());
    }

    @Test
    void clampsClosingDayToShorterMonths() {
        CreditCard card = CreditCard.builder().closingDay(31).dueDay(10).build();

        BillingPeriod period = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 2, 20));

        assertEquals(LocalDate.of(2026, 2, 28), period.end());
    }

    /**
     * A card closing on the 28th and due on the 5th, walked across a closing boundary.
     *
     * The dates below are the ones an owner described from their own statement: the invoice paid
     * 5 Sep closes on 28 Aug, but a charge dated 28 Aug is already on the one paid 5 Oct — the
     * card has cut off by then. The period that invoice closes in (August) is not the month it is
     * known by (September) — see {@link com.planelyx.api.dto.InvoiceResponse#referenceMonth()}.
     */
    @Test
    void assignsChargesAroundAnEndOfMonthClosingDay() {
        CreditCard card = CreditCard.builder().closingDay(28).dueDay(5).build();

        BillingPeriod beforeClosing = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 7, 30));
        assertEquals(LocalDate.of(2026, 7, 29), beforeClosing.start());
        assertEquals(LocalDate.of(2026, 8, 28), beforeClosing.end());
        assertEquals(LocalDate.of(2026, 9, 5), beforeClosing.dueDate());

        BillingPeriod dayBeforeClosing = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 27));
        assertEquals(LocalDate.of(2026, 8, 28), dayBeforeClosing.end());
        assertEquals(LocalDate.of(2026, 9, 5), dayBeforeClosing.dueDate());

        BillingPeriod onClosingDay = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 28));
        assertEquals(LocalDate.of(2026, 8, 29), onClosingDay.start());
        assertEquals(LocalDate.of(2026, 9, 28), onClosingDay.end());
        assertEquals(LocalDate.of(2026, 10, 5), onClosingDay.dueDate());

        BillingPeriod dayAfterClosing = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 29));
        assertEquals(LocalDate.of(2026, 8, 29), dayAfterClosing.start());
        assertEquals(LocalDate.of(2026, 9, 28), dayAfterClosing.end());
        assertEquals(LocalDate.of(2026, 10, 5), dayAfterClosing.dueDate());
    }

    /**
     * Clamping the closing and due days into February would land both on the 28th, leaving an
     * invoice due the same day it stopped closing. The month after is the only sensible reading.
     */
    @Test
    void dueDateRollsOnWhenClampingCollidesWithTheClosingDate() {
        CreditCard card = CreditCard.builder().closingDay(29).dueDay(30).build();

        BillingPeriod period = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 2, 10));

        assertEquals(LocalDate.of(2026, 2, 28), period.end());
        assertEquals(LocalDate.of(2026, 3, 30), period.dueDate());
    }

    @Test
    void dueDateStaysInTheClosingMonthWhenItComesAfterClosing() {
        CreditCard card = CreditCard.builder().closingDay(10).dueDay(17).build();

        BillingPeriod period = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 5));

        assertEquals(LocalDate.of(2026, 8, 10), period.end());
        assertEquals(LocalDate.of(2026, 8, 17), period.dueDate());
    }

    /** A charge dated today already goes to the next invoice, so this one has stopped taking any. */
    @Test
    void derivedStatusIsClosedOnTheClosingDayItself() {
        Invoice invoice = Invoice.builder().billingPeriodEnd(LocalDate.now()).build();

        assertEquals(InvoiceStatus.CLOSED, invoiceService.derivedStatus(invoice));
    }

    @Test
    void derivedStatusIsOpenTheDayBeforeClosing() {
        Invoice invoice =
                Invoice.builder().billingPeriodEnd(LocalDate.now().plusDays(1)).build();

        assertEquals(InvoiceStatus.OPEN, invoiceService.derivedStatus(invoice));
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
