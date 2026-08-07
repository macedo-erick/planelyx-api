package com.planelyx.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.planelyx.api.domain.CreditCard;
import com.planelyx.api.domain.Invoice;
import com.planelyx.api.domain.enums.InvoiceStatus;
import com.planelyx.api.security.CurrentUser;
import com.planelyx.api.security.SecurityConfig;
import com.planelyx.api.service.InvoiceService;
import com.planelyx.api.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InvoiceController.class)
@Import({SecurityConfig.class, CurrentUser.class})
class InvoiceControllerTest {

    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID INVOICE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvoiceService invoiceService;

    @MockitoBean
    private TransactionService transactionService;

    /**
     * The reference month has to reach the client as {@code "2026-09"}.
     *
     * It is a {@code YearMonth}, which Jackson will happily write as {@code [2026,9]} under the
     * wrong settings — and the whole point of the field is that every screen reads the same
     * month, so the wire format is worth pinning rather than assuming.
     */
    @Test
    void reportsTheReferenceMonthAsTheDueMonth() throws Exception {
        when(invoiceService.findAll(any(), any(), any())).thenReturn(List.of(invoice()));
        when(invoiceService.derivedStatus(any())).thenReturn(InvoiceStatus.CLOSED);

        mockMvc.perform(get("/api/invoices").with(jwt().jwt(jwt -> jwt.subject(OWNER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].referenceMonth").value("2026-09"))
                .andExpect(jsonPath("$[0].billingPeriodEnd").value("2026-08-28"))
                .andExpect(jsonPath("$[0].dueDate").value("2026-09-05"));
    }

    @Test
    void deletesAnInvoice() throws Exception {
        mockMvc.perform(delete("/api/invoices/{id}", INVOICE_ID)
                        .with(jwt().jwt(jwt -> jwt.subject(OWNER_ID.toString()))))
                .andExpect(status().isNoContent());

        verify(invoiceService).delete(any(), any());
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/invoices")).andExpect(status().isUnauthorized());
    }

    private Invoice invoice() {
        return Invoice.builder()
                .id(INVOICE_ID)
                .creditCard(CreditCard.builder()
                        .id(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                        .closingDay(28)
                        .dueDay(5)
                        .build())
                .billingPeriodStart(LocalDate.of(2026, 7, 29))
                .billingPeriodEnd(LocalDate.of(2026, 8, 28))
                .dueDate(LocalDate.of(2026, 9, 5))
                .totalAmount(new BigDecimal("200.00"))
                .status(InvoiceStatus.OPEN)
                .build();
    }
}
