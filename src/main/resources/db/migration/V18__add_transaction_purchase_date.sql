-- An installment is one purchase spread across several entries, so its occurrences carry a
-- transaction_date a month apart while the purchase itself happened once. That day was derived on
-- read until now, which left it impossible to order by. Materialised here so the listing can sort
-- a January purchase below August's own entries instead of dating it 25 August.
ALTER TABLE transaction ADD COLUMN purchase_date DATE;

UPDATE transaction t
SET purchase_date = COALESCE(
    (SELECT tt.start_date
       FROM transaction_template tt
      WHERE tt.id = t.template_id
        AND tt.recurrence_type = 'INSTALLMENT'),
    t.transaction_date
);

ALTER TABLE transaction ALTER COLUMN purchase_date SET NOT NULL;

CREATE INDEX idx_transaction_owner_purchase_date ON transaction (owner_id, purchase_date);
