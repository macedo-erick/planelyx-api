CREATE TABLE invoice (
    id UUID PRIMARY KEY,
    credit_card_id UUID NOT NULL REFERENCES credit_card (id),
    billing_period_start DATE NOT NULL,
    billing_period_end DATE NOT NULL,
    due_date DATE NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'CLOSED', 'PAID')),
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_invoice_card_period UNIQUE (credit_card_id, billing_period_start)
);

CREATE INDEX idx_invoice_credit_card_id ON invoice (credit_card_id);
