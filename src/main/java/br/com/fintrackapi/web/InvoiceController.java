package br.com.fintrackapi.web;

import br.com.fintrackapi.domain.Invoice;
import br.com.fintrackapi.domain.InvoiceStatus;
import br.com.fintrackapi.dto.InvoiceDetailResponse;
import br.com.fintrackapi.dto.InvoiceResponse;
import br.com.fintrackapi.mapper.InvoiceMapper;
import br.com.fintrackapi.security.CurrentUser;
import br.com.fintrackapi.service.InvoiceService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final CurrentUser currentUser;

    public InvoiceController(InvoiceService invoiceService, CurrentUser currentUser) {
        this.invoiceService = invoiceService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<InvoiceResponse> findAll(
            @RequestParam(required = false) UUID creditCardId, @RequestParam(required = false) InvoiceStatus status) {
        return invoiceService.findAll(currentUser.ownerId(), creditCardId, status).stream()
                .map(invoice -> InvoiceMapper.toResponse(invoice, invoiceService.derivedStatus(invoice)))
                .toList();
    }

    @GetMapping("/{id}")
    public InvoiceDetailResponse findById(@PathVariable UUID id) {
        Invoice invoice = invoiceService.findById(id, currentUser.ownerId());
        return InvoiceMapper.toDetailResponse(
                invoice, invoiceService.derivedStatus(invoice), invoiceService.transactionsFor(invoice.getId()));
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
