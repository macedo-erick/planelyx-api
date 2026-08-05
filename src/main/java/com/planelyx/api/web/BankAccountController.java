package com.planelyx.api.web;

import com.planelyx.api.dto.BalanceAdjustmentRequest;
import com.planelyx.api.dto.BankAccountBalanceResponse;
import com.planelyx.api.dto.BankAccountRequest;
import com.planelyx.api.dto.BankAccountResponse;
import com.planelyx.api.dto.TransactionResponse;
import com.planelyx.api.mapper.BankAccountMapper;
import com.planelyx.api.mapper.TransactionMapper;
import com.planelyx.api.security.CurrentUser;
import com.planelyx.api.service.BalanceAdjustmentService;
import com.planelyx.api.service.BankAccountService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/bank-accounts")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountService bankAccountService;
    private final BalanceAdjustmentService balanceAdjustmentService;
    private final CurrentUser currentUser;

    @GetMapping
    public List<BankAccountResponse> findAll() {
        return bankAccountService.findAll(currentUser.ownerId()).stream()
                .map(BankAccountMapper::toResponse)
                .toList();
    }

    /**
     * Current balances, deliberately not folded into {@link BankAccountResponse} so the plain
     * list stays one cheap query for the callers that only want names and types.
     *
     * {@code asOf} defaults to the end of the current month, which is what the dashboard means
     * by a balance: everything already committed for the month, including occurrences dated
     * later in it.
     */
    @GetMapping("/balances")
    public List<BankAccountBalanceResponse> balances(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        LocalDate effectiveAsOf =
                Objects.requireNonNullElseGet(asOf, () -> YearMonth.now().atEndOfMonth());
        UUID ownerId = currentUser.ownerId();

        Map<UUID, BigDecimal> balances = bankAccountService.balancesAsOf(ownerId, effectiveAsOf);

        return bankAccountService.findAll(ownerId).stream()
                .map(account -> new BankAccountBalanceResponse(
                        account.getId(),
                        account.getCurrency(),
                        balances.getOrDefault(account.getId(), account.getInitialBalance()),
                        effectiveAsOf))
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

    /**
     * Sets the balance to a given figure by recording the difference as a transaction.
     *
     * Returns the transaction it wrote, or 204 when the balance already matched and there was
     * nothing to record.
     */
    @PostMapping("/{id}/adjust-balance")
    public ResponseEntity<TransactionResponse> adjustBalance(
            @PathVariable UUID id, @Valid @RequestBody BalanceAdjustmentRequest request) {
        return balanceAdjustmentService
                .adjust(id, request, currentUser.ownerId())
                .map(TransactionMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        bankAccountService.delete(id, currentUser.ownerId());

        return ResponseEntity.noContent().build();
    }
}
