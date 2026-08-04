package br.com.fintrackapi.service;

import static java.util.Objects.nonNull;

import br.com.fintrackapi.domain.BankAccount;
import br.com.fintrackapi.domain.Category;
import br.com.fintrackapi.domain.CreditCard;
import br.com.fintrackapi.domain.Invoice;
import br.com.fintrackapi.domain.Transaction;
import br.com.fintrackapi.domain.enums.TransactionKind;
import br.com.fintrackapi.dto.TransactionRequest;
import br.com.fintrackapi.dto.TransactionUpdateRequest;
import br.com.fintrackapi.exception.NotFoundException;
import br.com.fintrackapi.repository.TransactionRepository;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final BankAccountService bankAccountService;
    private final CreditCardService creditCardService;
    private final CategoryService categoryService;
    private final InvoiceService invoiceService;

    public List<Transaction> findAll(
            UUID ownerId, UUID bankAccountId, UUID creditCardId, UUID categoryId, LocalDate from, LocalDate to) {
        Specification<Transaction> spec = (root, query, cb) -> cb.equal(root.get("ownerId"), ownerId);

        spec = spec.and((root, query, cb) -> {
            root.fetch("template", JoinType.LEFT);
            return cb.conjunction();
        });

        if (nonNull(bankAccountId)) {
            spec = spec.and(
                    (root, query, cb) -> cb.equal(root.get("bankAccount").get("id"), bankAccountId));
        }

        if (nonNull(creditCardId)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("creditCard").get("id"), creditCardId));
        }

        if (nonNull(categoryId)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }

        if (nonNull(from)) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("transactionDate"), from));
        }

        if (nonNull(to)) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("transactionDate"), to));
        }

        return transactionRepository.findAll(spec);
    }

    public Transaction findById(UUID id, UUID ownerId) {
        return transactionRepository
                .findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + id));
    }

    public Transaction create(TransactionRequest request, UUID ownerId) {
        TransactionKindValidator.validate(request.kind(), request.bankAccountId(), request.creditCardId());
        Category category = categoryService.findById(request.categoryId(), ownerId);

        Transaction.TransactionBuilder builder = Transaction.builder()
                .ownerId(ownerId)
                .kind(request.kind())
                .category(category)
                .amount(request.amount())
                .transactionDate(request.transactionDate())
                .description(request.description())
                .paid(true);

        if (request.kind() == TransactionKind.CARD_CHARGE) {
            CreditCard card = creditCardService.findById(request.creditCardId(), ownerId);
            Transaction saved =
                    transactionRepository.save(builder.creditCard(card).build());
            Invoice invoice = invoiceService.findOrCreateInvoiceForCharge(card, request.transactionDate());

            saved.setInvoice(invoice);
            saved = transactionRepository.save(saved);

            invoiceService.recomputeTotal(invoice.getId());

            return saved;
        }

        BankAccount account = bankAccountService.findById(request.bankAccountId(), ownerId);

        return transactionRepository.save(builder.bankAccount(account).build());
    }

    public Transaction update(UUID id, TransactionUpdateRequest request, UUID ownerId) {
        Transaction transaction = findById(id, ownerId);
        Category category = categoryService.findById(request.categoryId(), ownerId);

        transaction.setCategory(category);
        transaction.setAmount(request.amount());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setDescription(request.description());

        Transaction saved = transactionRepository.save(transaction);

        if (nonNull(saved.getInvoice())) {
            invoiceService.recomputeTotal(saved.getInvoice().getId());
        }

        return saved;
    }

    public void delete(UUID id, UUID ownerId) {
        Transaction transaction = findById(id, ownerId);
        UUID invoiceId =
                nonNull(transaction.getInvoice()) ? transaction.getInvoice().getId() : null;

        transactionRepository.delete(transaction);

        if (nonNull(invoiceId)) {
            invoiceService.recomputeTotal(invoiceId);
        }
    }
}
