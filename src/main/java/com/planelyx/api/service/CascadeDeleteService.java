package com.planelyx.api.service;

import com.planelyx.api.domain.BankAccount;
import com.planelyx.api.domain.CreditCard;
import com.planelyx.api.repository.BankAccountRepository;
import com.planelyx.api.repository.CreditCardRepository;
import com.planelyx.api.repository.InvoiceRepository;
import com.planelyx.api.repository.TransactionRepository;
import com.planelyx.api.repository.TransactionTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes a card or an account together with everything filed under it.
 *
 * No foreign key in the schema cascades, so deleting either used to fail on whatever still pointed
 * at it. Clearing the dependants here rather than with {@code ON DELETE CASCADE} keeps the
 * destruction where it can be read and tested, instead of leaving it to fire from any stray delete.
 *
 * It lives in its own class because both {@link CreditCardService} and {@link BankAccountService}
 * need it and neither may depend on the other: {@code CreditCardService} already resolves accounts
 * through {@code BankAccountService}, so the reverse edge would be a constructor cycle. Depending
 * only on repositories, this sits below both.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CascadeDeleteService {

    private final BankAccountRepository bankAccountRepository;
    private final CreditCardRepository creditCardRepository;
    private final InvoiceRepository invoiceRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionTemplateRepository transactionTemplateRepository;

    /**
     * The card, its charges, the settlements paying them off, the invoices they sat on, and the
     * rules that generate them.
     *
     * Order matters and is the reverse of the references: a charge points at both an invoice and
     * the template that produced it, so charges go before either.
     *
     * The settlements need a pass of their own. They name the account the money came out of, not
     * the card, so {@code deleteAllByCreditCardId} does not reach them — and left behind they
     * would still point at invoices about to be removed.
     */
    public void deleteCreditCard(CreditCard card) {
        log.info("Cascade-deleting credit card {} owner={}", card.getId(), card.getOwnerId());

        transactionRepository.deleteAllByInvoiceCreditCardId(card.getId());
        transactionRepository.deleteAllByCreditCardId(card.getId());
        transactionRepository.flush();

        invoiceRepository.deleteAllByCreditCardId(card.getId());
        transactionTemplateRepository.deleteAllByCreditCardId(card.getId());

        creditCardRepository.delete(card);
    }

    /**
     * The account, its transactions and rules, and every card drawn on it — cards included because
     * a card with no account behind it has nothing left to be paid from.
     */
    public void deleteBankAccount(BankAccount account) {
        log.info("Cascade-deleting bank account {} owner={}", account.getId(), account.getOwnerId());

        creditCardRepository.findAllByBankAccountId(account.getId()).forEach(this::deleteCreditCard);

        transactionRepository.deleteAllByBankAccountId(account.getId());
        transactionRepository.flush();

        transactionTemplateRepository.deleteAllByBankAccountId(account.getId());

        bankAccountRepository.delete(account);
    }
}
