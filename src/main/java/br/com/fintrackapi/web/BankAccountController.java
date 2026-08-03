package br.com.fintrackapi.web;

import br.com.fintrackapi.dto.BankAccountRequest;
import br.com.fintrackapi.dto.BankAccountResponse;
import br.com.fintrackapi.mapper.BankAccountMapper;
import br.com.fintrackapi.security.CurrentUser;
import br.com.fintrackapi.service.BankAccountService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bank-accounts")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountService bankAccountService;
    private final CurrentUser currentUser;

    @GetMapping
    public List<BankAccountResponse> findAll() {
        return bankAccountService.findAll(currentUser.ownerId()).stream()
                .map(BankAccountMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public BankAccountResponse findById(@PathVariable UUID id) {
        return BankAccountMapper.toResponse(bankAccountService.findById(id, currentUser.ownerId()));
    }

    @PostMapping
    public ResponseEntity<BankAccountResponse> create(@Valid @RequestBody BankAccountRequest request) {
        BankAccountResponse response =
                BankAccountMapper.toResponse(bankAccountService.create(request, currentUser.ownerId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public BankAccountResponse update(@PathVariable UUID id, @Valid @RequestBody BankAccountRequest request) {
        return BankAccountMapper.toResponse(bankAccountService.update(id, request, currentUser.ownerId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        bankAccountService.delete(id, currentUser.ownerId());

        return ResponseEntity.noContent().build();
    }
}
