CREATE TABLE credit_card (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    bank_account_id UUID NOT NULL REFERENCES bank_account (id),
    name VARCHAR(255) NOT NULL,
    brand VARCHAR(50) NOT NULL,
    credit_limit NUMERIC(19, 2) NOT NULL,
    closing_day INT NOT NULL CHECK (closing_day BETWEEN 1 AND 31),
    due_day INT NOT NULL CHECK (due_day BETWEEN 1 AND 31),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_credit_card_owner_id ON credit_card (owner_id);
CREATE INDEX idx_credit_card_bank_account_id ON credit_card (bank_account_id);
