package com.planelyx.api.service;

import com.planelyx.api.domain.Transaction;
import com.planelyx.api.domain.enums.CategoryType;
import com.planelyx.api.domain.enums.SystemCategoryKey;
import com.planelyx.api.domain.enums.TransactionKind;
import com.planelyx.api.dto.BalanceAdjustmentRequest;
import com.planelyx.api.dto.TransactionRequest;
import com.planelyx.api.exception.NotFoundException;
import com.planelyx.api.repository.CategoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Correcting an account balance to a figure the owner read off their bank.
 *
 * A balance is derived, never stored, so there is nothing to overwrite: the correction is
 * recorded as an ordinary transaction for the difference. That keeps the history honest — the
 * balance moved because something was posted, and the owner can see what.
 *
 * It sits apart from {@link BankAccountService} because it needs {@link TransactionService},
 * which in turn needs {@code BankAccountService} — putting it there would close that loop and
 * Spring would refuse to build the context.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class BalanceAdjustmentService {

    /**
     * Used when the caller sends no wording of its own. The API has no translations, so the text a
     * user actually reads comes from the client that knows their language.
     */
    private static final String DEFAULT_DESCRIPTION = "Balance adjustment";

    private final BankAccountService bankAccountService;
    private final TransactionService transactionService;
    private final CategoryRepository categoryRepository;

    /**
     * Posts the difference between {@code request.targetBalance()} and the balance on the
     * adjustment date.
     *
     * Returns empty when they already agree — writing a zero-amount transaction would only add
     * noise, and {@code TransactionRequest} rejects it anyway.
     */
    public Optional<Transaction> adjust(UUID bankAccountId, BalanceAdjustmentRequest request, UUID ownerId) {
        LocalDate date = Objects.requireNonNullElseGet(request.transactionDate(), LocalDate::now);

        BigDecimal current = bankAccountService.balanceAsOf(bankAccountId, ownerId, date);
        BigDecimal delta = request.targetBalance().subtract(current);

        if (delta.signum() == 0) {
            return Optional.empty();
        }

        // The kind carries the direction, so the amount stays positive and the ordinary
        // create path — validation, invoice rules, the paid flag — applies unchanged.
        boolean inflow = delta.signum() > 0;

        TransactionRequest transaction = new TransactionRequest(
                inflow ? TransactionKind.ACCOUNT_CREDIT : TransactionKind.ACCOUNT_DEBIT,
                bankAccountId,
                null,
                adjustmentCategoryId(ownerId, inflow ? CategoryType.INCOME : CategoryType.EXPENSE),
                delta.abs(),
                date,
                StringUtils.hasText(request.description()) ? request.description() : DEFAULT_DESCRIPTION);

        return Optional.of(transactionService.createCorrection(transaction, ownerId));
    }

    private UUID adjustmentCategoryId(UUID ownerId, CategoryType type) {
        return categoryRepository
                .findByOwnerIdAndSystemKeyAndType(ownerId, SystemCategoryKey.ADJUSTMENT, type)
                .orElseThrow(() -> new NotFoundException("Adjustment category is missing for owner: " + ownerId))
                .getId();
    }
}
