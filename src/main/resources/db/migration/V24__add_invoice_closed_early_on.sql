ALTER TABLE invoice ADD COLUMN closed_early_on DATE;

-- Status was only ever PAID or not — OPEN vs CLOSED was already derived at read time, never
-- stored. paid_at carries that same information (null means not paid), so the column is dropped
-- rather than kept redundant.
ALTER TABLE invoice DROP COLUMN status;
