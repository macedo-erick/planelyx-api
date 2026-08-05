package com.planelyx.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.planelyx.api.domain.CreditCard;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InvoiceServiceTest {

    private final InvoiceService invoiceService = new InvoiceService(null, null, null);

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
}
