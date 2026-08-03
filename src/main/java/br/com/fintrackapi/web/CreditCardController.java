package br.com.fintrackapi.web;

import br.com.fintrackapi.dto.CreditCardRequest;
import br.com.fintrackapi.dto.CreditCardResponse;
import br.com.fintrackapi.mapper.CreditCardMapper;
import br.com.fintrackapi.security.CurrentUser;
import br.com.fintrackapi.service.CreditCardService;
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
@RequestMapping("/api/credit-cards")
@RequiredArgsConstructor
public class CreditCardController {

    private final CreditCardService creditCardService;
    private final CurrentUser currentUser;

    @GetMapping
    public List<CreditCardResponse> findAll() {
        return creditCardService.findAll(currentUser.ownerId()).stream()
                .map(CreditCardMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public CreditCardResponse findById(@PathVariable UUID id) {
        return CreditCardMapper.toResponse(creditCardService.findById(id, currentUser.ownerId()));
    }

    @PostMapping
    public ResponseEntity<CreditCardResponse> create(@Valid @RequestBody CreditCardRequest request) {
        CreditCardResponse response =
                CreditCardMapper.toResponse(creditCardService.create(request, currentUser.ownerId()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public CreditCardResponse update(@PathVariable UUID id, @Valid @RequestBody CreditCardRequest request) {
        return CreditCardMapper.toResponse(creditCardService.update(id, request, currentUser.ownerId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        creditCardService.delete(id, currentUser.ownerId());

        return ResponseEntity.noContent().build();
    }
}
