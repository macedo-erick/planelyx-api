package com.planelyx.api.web;

import com.planelyx.api.dto.CreditCardRequest;
import com.planelyx.api.dto.CreditCardResponse;
import com.planelyx.api.mapper.CreditCardMapper;
import com.planelyx.api.security.CurrentUser;
import com.planelyx.api.service.CreditCardService;
import com.planelyx.api.service.InvoiceService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/credit-cards")
@RequiredArgsConstructor
public class CreditCardController {

    private final CreditCardService creditCardService;
    private final InvoiceService invoiceService;
    private final CurrentUser currentUser;

    @GetMapping
    public List<CreditCardResponse> findAll() {
        UUID ownerId = currentUser.ownerId();
        Map<UUID, BigDecimal> usedLimits = invoiceService.unpaidTotalsByCard(ownerId);

        return creditCardService.findAll(ownerId).stream()
                .map(card -> CreditCardMapper.toResponse(card, usedLimits.getOrDefault(card.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @GetMapping("/{id}")
    public CreditCardResponse findById(@PathVariable UUID id) {
        return CreditCardMapper.toResponse(
                creditCardService.findById(id, currentUser.ownerId()), invoiceService.unpaidTotal(id));
    }

    @PostMapping
    public ResponseEntity<CreditCardResponse> create(@Valid @RequestBody CreditCardRequest request) {
        CreditCardResponse response =
                CreditCardMapper.toResponse(creditCardService.create(request, currentUser.ownerId()), BigDecimal.ZERO);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public CreditCardResponse update(@PathVariable UUID id, @Valid @RequestBody CreditCardRequest request) {
        return CreditCardMapper.toResponse(
                creditCardService.update(id, request, currentUser.ownerId()), invoiceService.unpaidTotal(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        creditCardService.delete(id, currentUser.ownerId());

        return ResponseEntity.noContent().build();
    }
}
