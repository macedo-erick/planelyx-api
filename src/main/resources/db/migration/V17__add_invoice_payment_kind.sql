-- Settling a card invoice, recorded as a transaction so the money actually leaves an account.
--
-- Paying an invoice used to flip a status and nothing else, so a card charge never reached any
-- balance the app shows: marking an invoice paid made the dashboard's total jump *up*, because
-- the invoice dropped out of the deduction without any debit taking its place.
--
-- A settlement is neither an expense nor income — the charges on the invoice were already
-- counted as spending in their own month — so it needs a kind of its own rather than an
-- ACCOUNT_DEBIT. Carrying that in the kind puts the rule where BankAccountService and every
-- aggregate already branch, instead of in a category filter each query would have to remember.
ALTER TABLE transaction DROP CONSTRAINT transaction_kind_check;
ALTER TABLE transaction DROP CONSTRAINT chk_transaction_owner;
ALTER TABLE transaction DROP CONSTRAINT chk_transaction_invoice;

ALTER TABLE transaction ADD CONSTRAINT transaction_kind_check
    CHECK (kind IN ('ACCOUNT_DEBIT', 'ACCOUNT_CREDIT', 'CARD_CHARGE', 'INVOICE_PAYMENT'));

-- A payment moves money out of a bank account, so it is shaped like an account transaction:
-- the card is reached through the invoice, not through credit_card_id.
ALTER TABLE transaction ADD CONSTRAINT chk_transaction_owner
    CHECK (
        (kind = 'CARD_CHARGE' AND credit_card_id IS NOT NULL AND bank_account_id IS NULL)
        OR (kind IN ('ACCOUNT_DEBIT', 'ACCOUNT_CREDIT', 'INVOICE_PAYMENT')
            AND bank_account_id IS NOT NULL AND credit_card_id IS NULL)
    );

-- A payment exists only to settle one invoice, so unlike a charge it must name one.
ALTER TABLE transaction ADD CONSTRAINT chk_transaction_invoice
    CHECK (
        (kind = 'INVOICE_PAYMENT' AND invoice_id IS NOT NULL)
        OR kind = 'CARD_CHARGE'
        OR (kind IN ('ACCOUNT_DEBIT', 'ACCOUNT_CREDIT') AND invoice_id IS NULL)
    );

-- An invoice is settled once. This is what makes paying idempotent and leaves unpay exactly one
-- row to remove.
CREATE UNIQUE INDEX uq_transaction_invoice_payment
    ON transaction (invoice_id) WHERE kind = 'INVOICE_PAYMENT';
