package br.com.fintrackapi.service;

import br.com.fintrackapi.domain.BankAccount;
import br.com.fintrackapi.domain.CreditCard;
import br.com.fintrackapi.dto.CreditCardRequest;
import br.com.fintrackapi.exception.NotFoundException;
import br.com.fintrackapi.repository.CreditCardRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class CreditCardService {

    private final CreditCardRepository creditCardRepository;
    private final BankAccountService bankAccountService;

    public List<CreditCard> findAll(UUID ownerId) {
        return creditCardRepository.findAllByOwnerId(ownerId);
    }

    public CreditCard findById(UUID id, UUID ownerId) {
        return creditCardRepository
                .findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Credit card not found: " + id));
    }

    public CreditCard create(CreditCardRequest request, UUID ownerId) {
        BankAccount bankAccount = bankAccountService.findById(request.bankAccountId(), ownerId);
        CreditCard card = CreditCard.builder()
                .ownerId(ownerId)
                .bankAccount(bankAccount)
                .name(request.name())
                .brand(request.brand())
                .creditLimit(request.creditLimit())
                .closingDay(request.closingDay())
                .dueDay(request.dueDay())
                .active(true)
                .build();

        return creditCardRepository.save(card);
    }

    public CreditCard update(UUID id, CreditCardRequest request, UUID ownerId) {
        CreditCard card = findById(id, ownerId);
        BankAccount bankAccount = bankAccountService.findById(request.bankAccountId(), ownerId);

        card.setBankAccount(bankAccount);
        card.setName(request.name());
        card.setBrand(request.brand());
        card.setCreditLimit(request.creditLimit());
        card.setClosingDay(request.closingDay());
        card.setDueDay(request.dueDay());

        return creditCardRepository.save(card);
    }

    public void delete(UUID id, UUID ownerId) {
        CreditCard card = findById(id, ownerId);

        creditCardRepository.delete(card);
    }
}
