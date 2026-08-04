package br.com.planelyxapi.web;

import br.com.planelyxapi.domain.Invoice;
import br.com.planelyxapi.domain.enums.InvoiceStatus;
import br.com.planelyxapi.dto.InvoiceResponse;
import br.com.planelyxapi.dto.PageResponse;
import br.com.planelyxapi.dto.TransactionResponse;
import br.com.planelyxapi.mapper.InvoiceMapper;
import br.com.planelyxapi.mapper.TransactionMapper;
import br.com.planelyxapi.security.CurrentUser;
import br.com.planelyxapi.service.InvoiceService;
import br.com.planelyxapi.service.TransactionService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/{id}/pay")
    public InvoiceResponse pay(@PathVariable UUID id) {
        Invoice invoice = invoiceService.pay(id, currentUser.ownerId());

        return InvoiceMapper.toResponse(invoice, invoiceService.derivedStatus(invoice));
    }

    @PostMapping("/{id}/unpay")
    public InvoiceResponse unpay(@PathVariable UUID id) {
        Invoice invoice = invoiceService.unpay(id, currentUser.ownerId());

        return InvoiceMapper.toResponse(invoice, invoiceService.derivedStatus(invoice));
    }
}
