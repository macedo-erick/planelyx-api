package br.com.fintrackapi.web;

import br.com.fintrackapi.dto.TransactionRequest;
import br.com.fintrackapi.dto.TransactionResponse;
import br.com.fintrackapi.dto.TransactionUpdateRequest;
import br.com.fintrackapi.mapper.TransactionMapper;
import br.com.fintrackapi.security.CurrentUser;
import br.com.fintrackapi.service.TransactionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final CurrentUser currentUser;

    @GetMapping
    public List<TransactionResponse> findAll(
            @RequestParam(required = false) UUID bankAccountId,
            @RequestParam(required = false) UUID creditCardId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return transactionService
                .findAll(currentUser.ownerId(), bankAccountId, creditCardId, categoryId, from, to)
                .stream()
                .map(TransactionMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public TransactionResponse findById(@PathVariable UUID id) {
        return TransactionMapper.toResponse(transactionService.findById(id, currentUser.ownerId()));
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse response =
                TransactionMapper.toResponse(transactionService.create(request, currentUser.ownerId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public TransactionResponse update(@PathVariable UUID id, @Valid @RequestBody TransactionUpdateRequest request) {
        return TransactionMapper.toResponse(transactionService.update(id, request, currentUser.ownerId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        transactionService.delete(id, currentUser.ownerId());

        return ResponseEntity.noContent().build();
    }
}
