package com.planelyx.api.web;

import com.planelyx.api.domain.Invoice;
import com.planelyx.api.domain.enums.InvoiceStatus;
import com.planelyx.api.dto.InvoiceAdjustmentRequest;
import com.planelyx.api.dto.InvoicePaymentRequest;
import com.planelyx.api.dto.InvoiceResponse;
import com.planelyx.api.dto.PageResponse;
import com.planelyx.api.dto.TransactionResponse;
import com.planelyx.api.mapper.InvoiceMapper;
import com.planelyx.api.mapper.TransactionMapper;
import com.planelyx.api.security.CurrentUser;
import com.planelyx.api.service.InvoiceService;
import com.planelyx.api.service.TransactionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final CurrentUser currentUser;

    @GetMapping
    public List<InvoiceResponse> findAll(
            @RequestParam(required = false) UUID creditCardId, @RequestParam(required = false) InvoiceStatus status) {
        return invoiceService.findAll(currentUser.ownerId(), creditCardId, status).stream()
                .map(invoice -> InvoiceMapper.toResponse(invoice, invoiceService.derivedStatus(invoice)))
                .toList();
    }

    /**
     * The invoice itself. Its charges are paged separately through {@code /{id}/transactions},
     * so turning a page of charges does not refetch the summary.
     */
    @GetMapping("/{id}")
    public InvoiceResponse findById(@PathVariable UUID id) {
        Invoice invoice = invoiceService.findById(id, currentUser.ownerId());

        return InvoiceMapper.toResponse(invoice, invoiceService.derivedStatus(invoice));
    }

    @GetMapping("/{id}/transactions")
    public PageResponse<TransactionResponse> transactions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        // Resolved through the service so an invoice owned by someone else 404s before any of
        // its charges are read.
        Invoice invoice = invoiceService.findById(id, currentUser.ownerId());

        return PageResponse.of(
                invoiceService.transactionsFor(
                        invoice.getId(), PageRequest.of(page, size, TransactionService.NEWEST_FIRST)),
                TransactionMapper::toResponse);
    }

    /**
     * Sets the invoice total to a given figure by recording the difference as a charge on it.
     * Refused once the invoice is paid.
     */
    @PostMapping("/{id}/adjust")
    public InvoiceResponse adjust(@PathVariable UUID id, @Valid @RequestBody InvoiceAdjustmentRequest request) {
        Invoice invoice =
                invoiceService.adjust(id, request.targetAmount(), request.description(), currentUser.ownerId());

        return InvoiceMapper.toResponse(invoice, invoiceService.derivedStatus(invoice));
    }

    /** Removes the invoice and every charge on it. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        invoiceService.delete(id, currentUser.ownerId());

        return ResponseEntity.noContent().build();
    }

    /**
     * Marks the invoice settled and posts the debit that takes the money out of an account.
     *
     * The body is optional: with none, the payment is dated on the due date and comes out of the
     * account the card is billed against.
     */
    @PostMapping("/{id}/pay")
    public InvoiceResponse pay(@PathVariable UUID id, @RequestBody(required = false) InvoicePaymentRequest request) {
        Invoice invoice = invoiceService.pay(id, request, currentUser.ownerId());

        return InvoiceMapper.toResponse(invoice, invoiceService.derivedStatus(invoice));
    }

    @PostMapping("/{id}/unpay")
    public InvoiceResponse unpay(@PathVariable UUID id) {
        Invoice invoice = invoiceService.unpay(id, currentUser.ownerId());

        return InvoiceMapper.toResponse(invoice, invoiceService.derivedStatus(invoice));
    }
}
