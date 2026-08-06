package com.planelyx.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.planelyx.api.domain.CreditCard;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InvoiceServiceTest {

    private final InvoiceService invoiceService = new InvoiceService(null, null, null, null);

    @Test
    void resolvesBillingPeriodAfterClosingDay() {
        CreditCard card = CreditCard.builder().closingDay(10).dueDay(17).build();

        BillingPeriod period = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 15));

        assertEquals(LocalDate.of(2026, 8, 11), period.start());
        assertEquals(LocalDate.of(2026, 9, 10), period.end());
        assertEquals(LocalDate.of(2026, 9, 17), period.dueDate());
    }

    @Test
    void resolvesBillingPeriodOnOrBeforeClosingDay() {
        CreditCard card = CreditCard.builder().closingDay(10).dueDay(17).build();

        BillingPeriod period = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 5));

        assertEquals(LocalDate.of(2026, 7, 11), period.start());
        assertEquals(LocalDate.of(2026, 8, 10), period.end());
        assertEquals(LocalDate.of(2026, 8, 17), period.dueDate());
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
     * The three dates below are the ones an owner described from their own statement: everything
     * from 30 Jul through 28 Aug is on the invoice paid 5 Sep, and 29 Aug starts the one paid
     * 5 Oct. The period that invoice closes in (August) is not the month it is known by
     * (September) — see {@link com.planelyx.api.dto.InvoiceResponse#referenceMonth()}.
     */
    @Test
    void assignsChargesAroundAnEndOfMonthClosingDay() {
        CreditCard card = CreditCard.builder().closingDay(28).dueDay(5).build();

        BillingPeriod dayAfterClosing = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 7, 30));
        assertEquals(LocalDate.of(2026, 7, 29), dayAfterClosing.start());
        assertEquals(LocalDate.of(2026, 8, 28), dayAfterClosing.end());
        assertEquals(LocalDate.of(2026, 9, 5), dayAfterClosing.dueDate());

        BillingPeriod midPeriod = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 15));
        assertEquals(LocalDate.of(2026, 8, 28), midPeriod.end());
        assertEquals(LocalDate.of(2026, 9, 5), midPeriod.dueDate());

        BillingPeriod onClosingDay = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 28));
        assertEquals(LocalDate.of(2026, 9, 5), onClosingDay.dueDate());

        BillingPeriod nextPeriod = invoiceService.resolveBillingPeriod(card, LocalDate.of(2026, 8, 29));
        assertEquals(LocalDate.of(2026, 8, 29), nextPeriod.start());
        assertEquals(LocalDate.of(2026, 9, 28), nextPeriod.end());
        assertEquals(LocalDate.of(2026, 10, 5), nextPeriod.dueDate());
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
}
