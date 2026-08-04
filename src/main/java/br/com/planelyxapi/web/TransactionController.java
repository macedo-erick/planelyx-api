package br.com.planelyxapi.web;

import br.com.planelyxapi.domain.enums.TransactionKind;
import br.com.planelyxapi.domain.enums.TransactionScope;
import br.com.planelyxapi.dto.PageResponse;
import br.com.planelyxapi.dto.TransactionRequest;
import br.com.planelyxapi.dto.TransactionResponse;
import br.com.planelyxapi.dto.TransactionSummaryResponse;
import br.com.planelyxapi.dto.TransactionUpdateRequest;
import br.com.planelyxapi.mapper.TransactionMapper;
import br.com.planelyxapi.security.CurrentUser;
import br.com.planelyxapi.service.TransactionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

    /** Sort is fixed server-side ({@link TransactionService#NEWEST_FIRST}); the client sends none. */
    @GetMapping
    public PageResponse<TransactionResponse> findAll(
            @RequestParam(required = false) UUID bankAccountId,
            @RequestParam(required = false) UUID creditCardId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) TransactionKind kind,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return PageResponse.of(
                transactionService.findAll(
                        currentUser.ownerId(),
                        bankAccountId,
                        creditCardId,
                        categoryId,
                        kind,
                        from,
                        to,
                        PageRequest.of(page, size, TransactionService.NEWEST_FIRST)),
                TransactionMapper::toResponse);
    }

    /**
     * Totals across the whole filtered selection, which a single page cannot supply.
     *
     * Spring matches literal paths before templated ones, so "summary" never reaches
     * {@code /{id}} to be parsed as a UUID.
     */
    @GetMapping("/summary")
    public TransactionSummaryResponse summary(
            @RequestParam(required = false) UUID bankAccountId,
            @RequestParam(required = false) UUID creditCardId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) TransactionKind kind,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return transactionService.summarize(
                currentUser.ownerId(), bankAccountId, creditCardId, categoryId, kind, from, to);
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

    /** {@code scope} only reaches past this row when the transaction belongs to a template. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id, @RequestParam(defaultValue = "SINGLE") TransactionScope scope) {
        transactionService.delete(id, currentUser.ownerId(), scope);

        return ResponseEntity.noContent().build();
    }
}
