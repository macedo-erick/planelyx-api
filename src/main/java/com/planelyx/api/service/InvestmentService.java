package com.planelyx.api.service;

import static java.util.Objects.nonNull;
import static org.springframework.util.StringUtils.hasText;

import com.planelyx.api.domain.BankAccount;
import com.planelyx.api.domain.Category;
import com.planelyx.api.domain.Investment;
import com.planelyx.api.domain.Transaction;
import com.planelyx.api.domain.enums.CategoryType;
import com.planelyx.api.domain.enums.SystemCategoryKey;
import com.planelyx.api.domain.enums.TransactionKind;
import com.planelyx.api.dto.BalanceAdjustmentRequest;
import com.planelyx.api.dto.InvestmentMovementRequest;
import com.planelyx.api.dto.InvestmentRequest;
import com.planelyx.api.exception.NotFoundException;
import com.planelyx.api.repository.CategoryRepository;
import com.planelyx.api.repository.InvestmentRepository;
import com.planelyx.api.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Investments, and the money moving in and out of them.
 *
 * Every movement is one transaction carrying both ends, which is what stops the two balances
 * drifting apart. Rows go straight to the repository rather than through {@link TransactionService}
 * — as {@code InvoiceService} writes its settlements — because that service refuses system
 * categories and knows nothing of these kinds, and both refusals are correct.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class InvestmentService {

    /** Used when a client sends no wording. The API holds no translations; the client does. */
    private static final String DEFAULT_CONTRIBUTION_DESCRIPTION = "Investment contribution";

    private static final String DEFAULT_REDEMPTION_DESCRIPTION = "Investment redemption";

    private static final String DEFAULT_RETURN_DESCRIPTION = "Investment return";

    private final InvestmentRepository investmentRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final BankAccountService bankAccountService;
    private final CascadeDeleteService cascadeDeleteService;

    public List<Investment> findAll(UUID ownerId) {
        return investmentRepository.findAllByOwnerId(ownerId);
    }

    public Investment findById(UUID id, UUID ownerId) {
        return investmentRepository
                .findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Investment not found: " + id));
    }

    /**
     * What each investment is worth at the close of {@code asOf}, signed from the investment's own
     * side. The mirror of {@link BankAccountService#balancesAsOf}, forecast included.
     */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> balancesAsOf(UUID ownerId, LocalDate asOf) {
        return foldMovements(ownerId, asOf, kind -> kind.investmentSign());
    }

    /**
     * What the owner put in, net of what they took out, so balance less this is the return. Yield
     * and loss score zero on purpose: what the investment made is not what the owner contributed.
     */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> contributedAsOf(UUID ownerId, LocalDate asOf) {
        return foldMovements(ownerId, asOf, kind -> switch (kind) {
            case INVESTMENT_CONTRIBUTION -> 1;
            case INVESTMENT_REDEMPTION -> -1;
            default -> 0;
        });
    }

    /**
     * One walk over the movements, folded with whatever sign the caller wants. Both figures start
     * from {@code initialBalance}: money already there was contributed before the app saw it.
     */
    private Map<UUID, BigDecimal> foldMovements(UUID ownerId, LocalDate asOf, KindWeight weight) {
        Map<UUID, BigDecimal> movement = new HashMap<>();

        for (TransactionRepository.InvestmentKindTotal row :
                transactionRepository.sumByInvestmentAndKindAsOf(ownerId, asOf)) {
            BigDecimal signed = row.getTotal().multiply(BigDecimal.valueOf(weight.applyTo(row.getKind())));

            movement.merge(row.getInvestmentId(), signed, BigDecimal::add);
        }

        Map<UUID, BigDecimal> totals = new HashMap<>();

        for (Investment investment : findAll(ownerId)) {
            totals.put(
                    investment.getId(),
                    investment.getInitialBalance().add(movement.getOrDefault(investment.getId(), BigDecimal.ZERO)));
        }

        return totals;
    }

    /** The same figure for one investment. */
    @Transactional(readOnly = true)
    public BigDecimal balanceAsOf(UUID id, UUID ownerId, LocalDate asOf) {
        Investment investment = findById(id, ownerId);

        return balancesAsOf(ownerId, asOf).getOrDefault(investment.getId(), investment.getInitialBalance());
    }

    public Investment create(InvestmentRequest request, UUID ownerId) {
        Investment investment = Investment.builder()
                .ownerId(ownerId)
                .name(request.name())
                .institution(request.institution())
                .investmentType(request.investmentType())
                .initialBalance(request.initialBalance())
                .currency(request.currency())
                .active(true)
                .build();

        return investmentRepository.save(investment);
    }

    public Investment update(UUID id, InvestmentRequest request, UUID ownerId) {
        Investment investment = findById(id, ownerId);

        investment.setName(request.name());
        investment.setInstitution(request.institution());
        investment.setInvestmentType(request.investmentType());
        investment.setInitialBalance(request.initialBalance());
        investment.setCurrency(request.currency());

        return investmentRepository.save(investment);
    }

    /** Takes its movements with it — see {@link CascadeDeleteService#deleteInvestment}. */
    public void delete(UUID id, UUID ownerId) {
        cascadeDeleteService.deleteInvestment(findById(id, ownerId));
    }

    /** Lowers the account and raises the investment in one row, reaching neither spending nor income. */
    public Transaction contribute(UUID id, InvestmentMovementRequest request, UUID ownerId) {
        log.info("Contributing {} to investment {} owner={}", request.amount(), id, ownerId);

        return postMovement(
                id, request, ownerId, TransactionKind.INVESTMENT_CONTRIBUTION, DEFAULT_CONTRIBUTION_DESCRIPTION);
    }

    /** The same in reverse, refused above the balance on that date — an investment cannot go negative. */
    public Transaction redeem(UUID id, InvestmentMovementRequest request, UUID ownerId) {
        log.info("Redeeming {} from investment {} owner={}", request.amount(), id, ownerId);

        LocalDate date = movementDate(request);
        BigDecimal available = balanceAsOf(id, ownerId, date);

        if (request.amount().compareTo(available) > 0) {
            throw new IllegalArgumentException(
                    "Cannot redeem %s: the investment holds %s on %s".formatted(request.amount(), available, date));
        }

        return postMovement(
                id, request, ownerId, TransactionKind.INVESTMENT_REDEMPTION, DEFAULT_REDEMPTION_DESCRIPTION);
    }

    private Transaction postMovement(
            UUID id, InvestmentMovementRequest request, UUID ownerId, TransactionKind kind, String defaultDescription) {
        Investment investment = findById(id, ownerId);
        BankAccount account = bankAccountService.findById(request.bankAccountId(), ownerId);

        rejectCurrencyMismatch(investment, account);

        LocalDate date = movementDate(request);

        return transactionRepository.save(Transaction.builder()
                .ownerId(ownerId)
                .kind(kind)
                .bankAccount(account)
                .investment(investment)
                .category(systemCategory(ownerId, categoryTypeFor(kind)))
                .amount(request.amount())
                .transactionDate(date)
                .purchaseDate(date)
                .description(hasText(request.description()) ? request.description() : defaultDescription)
                .paid(true)
                .build());
    }

    /**
     * Corrects an investment to the figure the owner read off their broker, posting the difference
     * as yield or as a loss. The balance is derived, so a correction has to be a row — and this is
     * the only place a return is recognised, since redeeming moves money already counted.
     *
     * Empty when the figures already agree; a zero-amount row would be noise.
     */
    public Optional<Transaction> adjustBalance(UUID id, BalanceAdjustmentRequest request, UUID ownerId) {
        log.info("Investment adjustment on {} target={} owner={}", id, request.targetBalance(), ownerId);

        Investment investment = findById(id, ownerId);
        LocalDate date = Objects.requireNonNullElseGet(request.transactionDate(), LocalDate::now);

        BigDecimal delta = request.targetBalance().subtract(balanceAsOf(id, ownerId, date));

        if (delta.signum() == 0) {
            return Optional.empty();
        }

        TransactionKind kind = delta.signum() > 0 ? TransactionKind.INVESTMENT_YIELD : TransactionKind.INVESTMENT_LOSS;

        return Optional.of(transactionRepository.save(Transaction.builder()
                .ownerId(ownerId)
                .kind(kind)
                .investment(investment)
                .category(systemCategory(ownerId, categoryTypeFor(kind)))
                .amount(delta.abs())
                .transactionDate(date)
                .purchaseDate(date)
                .description(hasText(request.description()) ? request.description() : DEFAULT_RETURN_DESCRIPTION)
                .paid(true)
                .build()));
    }

    /** No FX exists here, so adding unlike currencies would produce a number that means nothing. */
    private void rejectCurrencyMismatch(Investment investment, BankAccount account) {
        if (!investment.getCurrency().equals(account.getCurrency())) {
            throw new IllegalArgumentException("Cannot move %s from an account held in %s: no conversion exists"
                    .formatted(investment.getCurrency(), account.getCurrency()));
        }
    }

    /**
     * Which side of the ledger the row sits on, nothing more. No aggregate reads this to decide
     * what is spending, so an EXPENSE category here does not make a contribution an expense.
     */
    private CategoryType categoryTypeFor(TransactionKind kind) {
        return switch (kind) {
            case INVESTMENT_REDEMPTION, INVESTMENT_YIELD -> CategoryType.INCOME;
            case INVESTMENT_CONTRIBUTION, INVESTMENT_LOSS -> CategoryType.EXPENSE;
            default -> throw new IllegalArgumentException("Not an investment kind: " + kind);
        };
    }

    private Category systemCategory(UUID ownerId, CategoryType type) {
        return categoryRepository
                .findByOwnerIdAndSystemKeyAndType(ownerId, SystemCategoryKey.INVESTMENT, type)
                .orElseThrow(() -> new NotFoundException("Investment category is missing for owner: " + ownerId));
    }

    private LocalDate movementDate(InvestmentMovementRequest request) {
        return nonNull(request.transactionDate()) ? request.transactionDate() : LocalDate.now();
    }

    /** The weight a fold gives one kind. */
    @FunctionalInterface
    private interface KindWeight {
        int applyTo(TransactionKind kind);
    }
}
