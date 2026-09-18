-- Moving money to and from an investment, without either end reading as spending.
--
-- Carried in the kind rather than a category, for the reason V17 gives: it puts the rule where the
-- aggregates already branch. Four kinds and not two because amount is always positive here and the
-- sign lives in the kind, so yield needs a counterpart the way a credit needs a debit.
ALTER TABLE transaction DROP CONSTRAINT transaction_kind_check;
ALTER TABLE transaction DROP CONSTRAINT chk_transaction_owner;
ALTER TABLE transaction DROP CONSTRAINT chk_transaction_invoice;

-- INVESTMENT_CONTRIBUTION is 23 characters and the column held 20.
ALTER TABLE transaction ALTER COLUMN kind TYPE VARCHAR(32);

ALTER TABLE transaction ADD CONSTRAINT transaction_kind_check
    CHECK (kind IN (
        'ACCOUNT_DEBIT', 'ACCOUNT_CREDIT', 'CARD_CHARGE', 'INVOICE_PAYMENT',
        'INVESTMENT_CONTRIBUTION', 'INVESTMENT_REDEMPTION', 'INVESTMENT_YIELD', 'INVESTMENT_LOSS'
    ));

ALTER TABLE transaction ADD COLUMN investment_id UUID REFERENCES investment (id);

CREATE INDEX idx_transaction_investment_id ON transaction (investment_id);

-- A contribution and a redemption name both ends: one row with two legs is what stops the balances
-- drifting. Yield and loss name only the investment, having no counterparty account — which is also
-- what keeps them out of sumByAccountAndKindAsOf.
ALTER TABLE transaction ADD CONSTRAINT chk_transaction_owner
    CHECK (
        (kind = 'CARD_CHARGE'
            AND credit_card_id IS NOT NULL AND bank_account_id IS NULL AND investment_id IS NULL)
        OR (kind IN ('ACCOUNT_DEBIT', 'ACCOUNT_CREDIT', 'INVOICE_PAYMENT')
            AND bank_account_id IS NOT NULL AND credit_card_id IS NULL AND investment_id IS NULL)
        OR (kind IN ('INVESTMENT_CONTRIBUTION', 'INVESTMENT_REDEMPTION')
            AND bank_account_id IS NOT NULL AND credit_card_id IS NULL AND investment_id IS NOT NULL)
        OR (kind IN ('INVESTMENT_YIELD', 'INVESTMENT_LOSS')
            AND bank_account_id IS NULL AND credit_card_id IS NULL AND investment_id IS NOT NULL)
    );

-- Restated, not left alone: V17 wrote this as a closed list, so a kind matching no branch makes the
-- whole CHECK false and every contribution is rejected by a constraint named after invoices.
ALTER TABLE transaction ADD CONSTRAINT chk_transaction_invoice
    CHECK (
        (kind = 'INVOICE_PAYMENT' AND invoice_id IS NOT NULL)
        OR kind = 'CARD_CHARGE'
        OR (kind IN (
                'ACCOUNT_DEBIT', 'ACCOUNT_CREDIT',
                'INVESTMENT_CONTRIBUTION', 'INVESTMENT_REDEMPTION', 'INVESTMENT_YIELD', 'INVESTMENT_LOSS'
            ) AND invoice_id IS NULL)
    );
