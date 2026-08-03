CREATE TABLE transaction (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('ACCOUNT_DEBIT', 'ACCOUNT_CREDIT', 'CARD_CHARGE')),
    bank_account_id UUID REFERENCES bank_account (id),
    credit_card_id UUID REFERENCES credit_card (id),
    category_id UUID NOT NULL REFERENCES category (id),
    invoice_id UUID REFERENCES invoice (id),
    template_id UUID REFERENCES transaction_template (id),
    installment_number INT,
    amount NUMERIC(19, 2) NOT NULL,
    transaction_date DATE NOT NULL,
    description VARCHAR(255) NOT NULL,
    paid BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_transaction_owner CHECK (
        (kind = 'CARD_CHARGE' AND credit_card_id IS NOT NULL AND bank_account_id IS NULL)
        OR (kind IN ('ACCOUNT_DEBIT', 'ACCOUNT_CREDIT') AND bank_account_id IS NOT NULL AND credit_card_id IS NULL)
    ),
    CONSTRAINT chk_transaction_invoice CHECK (
        kind = 'CARD_CHARGE' OR invoice_id IS NULL
    )
);

CREATE INDEX idx_transaction_owner_date ON transaction (owner_id, transaction_date);
CREATE INDEX idx_transaction_invoice_id ON transaction (invoice_id);
CREATE INDEX idx_transaction_template_id ON transaction (template_id);
