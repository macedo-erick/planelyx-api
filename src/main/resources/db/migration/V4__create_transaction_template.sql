CREATE TABLE transaction_template (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('ACCOUNT_DEBIT', 'ACCOUNT_CREDIT', 'CARD_CHARGE')),
    bank_account_id UUID REFERENCES bank_account (id),
    credit_card_id UUID REFERENCES credit_card (id),
    category_id UUID NOT NULL REFERENCES category (id),
    description VARCHAR(255) NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    recurrence_type VARCHAR(20) NOT NULL CHECK (recurrence_type IN ('FIXED_INDEFINITE', 'FIXED_COUNT', 'INSTALLMENT')),
    interval_unit VARCHAR(20) NOT NULL CHECK (interval_unit IN ('MONTHLY')),
    start_date DATE NOT NULL,
    total_occurrences INT,
    occurrences_generated INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_transaction_template_owner CHECK (
        (kind = 'CARD_CHARGE' AND credit_card_id IS NOT NULL AND bank_account_id IS NULL)
        OR (kind IN ('ACCOUNT_DEBIT', 'ACCOUNT_CREDIT') AND bank_account_id IS NOT NULL AND credit_card_id IS NULL)
    )
);

CREATE INDEX idx_transaction_template_owner_id ON transaction_template (owner_id);
