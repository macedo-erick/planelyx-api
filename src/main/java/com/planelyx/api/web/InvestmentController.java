package com.planelyx.api.web;

import com.planelyx.api.dto.BalanceAdjustmentRequest;
import com.planelyx.api.dto.InvestmentBalanceResponse;
import com.planelyx.api.dto.InvestmentMovementRequest;
import com.planelyx.api.dto.InvestmentRequest;
import com.planelyx.api.dto.InvestmentResponse;
import com.planelyx.api.dto.TransactionResponse;
import com.planelyx.api.mapper.InvestmentMapper;
import com.planelyx.api.mapper.TransactionMapper;
import com.planelyx.api.security.CurrentUser;
import com.planelyx.api.service.InvestmentService;
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
@RequestMapping("/api/investments")
@RequiredArgsConstructor
public class InvestmentController {

    private final InvestmentService investmentService;
    private final CurrentUser currentUser;

    @GetMapping
    public List<InvestmentResponse> findAll() {
        return investmentService.findAll(currentUser.ownerId()).stream()
                .map(InvestmentMapper::toResponse)
                .toList();
    }

    /**
     * Kept out of {@link InvestmentResponse} so the plain list stays one cheap query. {@code asOf}
     * defaults to the end of the current month, as it does for accounts.
     */
    @GetMapping("/balances")
    public List<InvestmentBalanceResponse> balances(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        LocalDate effectiveAsOf =
                Objects.requireNonNullElseGet(asOf, () -> YearMonth.now().atEndOfMonth());
        UUID ownerId = currentUser.ownerId();

        Map<UUID, BigDecimal> balances = investmentService.balancesAsOf(ownerId, effectiveAsOf);
        Map<UUID, BigDecimal> contributed = investmentService.contributedAsOf(ownerId, effectiveAsOf);

        return investmentService.findAll(ownerId).stream()
                .map(investment -> new InvestmentBalanceResponse(
                        investment.getId(),
                        investment.getCurrency(),
                        balances.getOrDefault(investment.getId(), investment.getInitialBalance()),
                        contributed.getOrDefault(investment.getId(), investment.getInitialBalance()),
                        effectiveAsOf))
                .toList();
    }

    @GetMapping("/{id}")
    public InvestmentResponse findById(@PathVariable UUID id) {
        return InvestmentMapper.toResponse(investmentService.findById(id, currentUser.ownerId()));
    }

    @PostMapping
    public ResponseEntity<InvestmentResponse> create(@Valid @RequestBody InvestmentRequest request) {
        InvestmentResponse response =
                InvestmentMapper.toResponse(investmentService.create(request, currentUser.ownerId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public InvestmentResponse update(@PathVariable UUID id, @Valid @RequestBody InvestmentRequest request) {
        return InvestmentMapper.toResponse(investmentService.update(id, request, currentUser.ownerId()));
    }

    /** Moves money out of an account and into the investment, as one row with two legs. */
    @PostMapping("/{id}/contribute")
    public ResponseEntity<TransactionResponse> contribute(
            @PathVariable UUID id, @Valid @RequestBody InvestmentMovementRequest request) {
        TransactionResponse response =
                TransactionMapper.toResponse(investmentService.contribute(id, request, currentUser.ownerId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** The same in reverse. Refused above what the investment holds on that date. */
    @PostMapping("/{id}/redeem")
    public ResponseEntity<TransactionResponse> redeem(
            @PathVariable UUID id, @Valid @RequestBody InvestmentMovementRequest request) {
        TransactionResponse response =
                TransactionMapper.toResponse(investmentService.redeem(id, request, currentUser.ownerId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Sets the investment to the figure the owner read off their broker, recording the difference
     * as yield or as a loss.
     *
     * Returns the transaction it wrote, or 204 when the figures already agreed.
     */
    @PostMapping("/{id}/adjust-balance")
    public ResponseEntity<TransactionResponse> adjustBalance(
            @PathVariable UUID id, @Valid @RequestBody BalanceAdjustmentRequest request) {
        return investmentService
                .adjustBalance(id, request, currentUser.ownerId())
                .map(TransactionMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        investmentService.delete(id, currentUser.ownerId());

        return ResponseEntity.noContent().build();
    }
}
