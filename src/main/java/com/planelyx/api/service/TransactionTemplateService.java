package com.planelyx.api.service;

import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.TransactionTemplate;
import com.planelyx.api.domain.enums.IntervalUnit;
import com.planelyx.api.domain.enums.TransactionKind;
import com.planelyx.api.dto.TransactionTemplateRequest;
import com.planelyx.api.exception.NotFoundException;
import com.planelyx.api.repository.TransactionTemplateRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class TransactionTemplateService {

    private final TransactionTemplateRepository transactionTemplateRepository;
    private final BankAccountService bankAccountService;
    private final CreditCardService creditCardService;
    private final CategoryService categoryService;
    private final TemplateOccurrenceGenerator occurrenceGenerator;

    public List<TransactionTemplate> findAll(UUID ownerId) {
        return transactionTemplateRepository.findAllByOwnerId(ownerId);
    }

    public TransactionTemplate findById(UUID id, UUID ownerId) {
        return transactionTemplateRepository
                .findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Transaction template not found: " + id));
    }

    public TransactionTemplate create(TransactionTemplateRequest request, UUID ownerId) {
        TransactionKindValidator.validate(request.kind(), request.bankAccountId(), request.creditCardId());
        validateRecurrence(request);
        Category category = categoryService.findById(request.categoryId(), ownerId);

        TransactionTemplate.TransactionTemplateBuilder builder = TransactionTemplate.builder()
                .ownerId(ownerId)
                .kind(request.kind())
                .category(category)
                .description(request.description())
                .totalAmount(request.totalAmount())
                .recurrenceType(request.recurrenceType())
                .intervalUnit(IntervalUnit.MONTHLY)
                .startDate(request.startDate())
                .totalOccurrences(request.totalOccurrences())
                .occurrencesGenerated(0)
                .active(true);

        if (request.kind() == TransactionKind.CARD_CHARGE) {
            builder.creditCard(creditCardService.findById(request.creditCardId(), ownerId));
        } else {
            builder.bankAccount(bankAccountService.findById(request.bankAccountId(), ownerId));
        }

        TransactionTemplate template = transactionTemplateRepository.save(builder.build());

        occurrenceGenerator.generateInitialOccurrences(template);

        return template;
    }

    public void deactivate(UUID id, UUID ownerId) {
        TransactionTemplate template = findById(id, ownerId);
        template.setActive(false);
        transactionTemplateRepository.save(template);
    }

    private void validateRecurrence(TransactionTemplateRequest request) {
        switch (request.recurrenceType()) {
            case INSTALLMENT -> {
                if (request.kind() != TransactionKind.CARD_CHARGE) {
                    throw new IllegalArgumentException("Installments are only supported for card charges");
                }

                if (Objects.isNull(request.totalOccurrences()) || request.totalOccurrences() < 2) {
                    throw new IllegalArgumentException("Installments require totalOccurrences >= 2");
                }
            }
            case FIXED_COUNT -> {
                if (Objects.isNull(request.totalOccurrences()) || request.totalOccurrences() < 1) {
                    throw new IllegalArgumentException("Fixed-count recurrence requires a positive totalOccurrences");
                }
            }
            case FIXED_INDEFINITE -> {
                if (Objects.nonNull(request.totalOccurrences())) {
                    throw new IllegalArgumentException("Indefinite recurrence must not set totalOccurrences");
                }
            }
        }
    }
}
