package br.com.fintrackapi.web;

import br.com.fintrackapi.dto.TransactionTemplateRequest;
import br.com.fintrackapi.dto.TransactionTemplateResponse;
import br.com.fintrackapi.mapper.TransactionTemplateMapper;
import br.com.fintrackapi.security.CurrentUser;
import br.com.fintrackapi.service.TransactionTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transaction-templates")
public class TransactionTemplateController {

    private final TransactionTemplateService transactionTemplateService;
    private final CurrentUser currentUser;

    public TransactionTemplateController(
            TransactionTemplateService transactionTemplateService, CurrentUser currentUser) {
        this.transactionTemplateService = transactionTemplateService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<TransactionTemplateResponse> findAll() {
        return transactionTemplateService.findAll(currentUser.ownerId()).stream()
                .map(TransactionTemplateMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public TransactionTemplateResponse findById(@PathVariable UUID id) {
        return TransactionTemplateMapper.toResponse(
                transactionTemplateService.findById(id, currentUser.ownerId()));
    }

    @PostMapping
    public ResponseEntity<TransactionTemplateResponse> create(
            @Valid @RequestBody TransactionTemplateRequest request) {
        TransactionTemplateResponse response = TransactionTemplateMapper.toResponse(
                transactionTemplateService.create(request, currentUser.ownerId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        transactionTemplateService.deactivate(id, currentUser.ownerId());
        return ResponseEntity.noContent().build();
    }
}
