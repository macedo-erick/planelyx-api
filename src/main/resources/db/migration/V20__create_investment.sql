-- Shaped like bank_account: another thing holding a balance that is derived from its movements,
-- never stored. initial_balance records an investment the owner already held, so adding one does
-- not invent a contribution from an account that never saw the money leave.
CREATE TABLE investment (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    institution VARCHAR(255) NOT NULL,
    investment_type VARCHAR(20) NOT NULL CHECK (
        investment_type IN ('FIXED_INCOME', 'FUNDS', 'STOCKS', 'CRYPTO', 'PENSION', 'OTHER')
    ),
    currency VARCHAR(3) NOT NULL,
    initial_balance NUMERIC(19, 2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX idx_investment_owner_id ON investment (owner_id);
