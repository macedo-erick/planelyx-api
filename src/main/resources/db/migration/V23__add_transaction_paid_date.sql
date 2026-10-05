-- A bill ticked off on the dashboard disappeared from it, because nothing said when it was paid
-- and so nothing could be shown about it afterwards. paid_date records the day an entry was
-- settled, and is set exactly when paid is.
ALTER TABLE transaction ADD COLUMN paid_date DATE;

-- What is already paid has no record of the day it happened, so the day it fell due stands in.
-- Capped at today: a bill ticked off before its due date was not paid in the future.
UPDATE transaction
SET paid_date = LEAST(transaction_date, CURRENT_DATE)
WHERE paid;

ALTER TABLE transaction
    ADD CONSTRAINT chk_transaction_paid_date CHECK (paid = (paid_date IS NOT NULL));
