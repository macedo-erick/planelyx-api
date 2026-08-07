-- transaction.paid has existed since V6 but never meant anything: every write site set it to TRUE,
-- so there was no such thing as a bill still to be paid. The dashboard now reminds the owner of the
-- month's recurring account bills, which needs the flag to be true to its name.
--
-- Going forward an account debit is written unpaid when it is dated ahead of today and paid
-- otherwise, because a debit dated in the past is being recorded after the fact. The same rule is
-- applied here to what is already stored. Card charges are left alone: they are settled through
-- their invoice, not one at a time. So is income — nothing to remember to pay.
--
-- CURRENT_DATE is evaluated once, when this runs. That is the intent: it draws the line at the
-- moment the feature arrives, and everything generated afterwards is classified on its own terms.
UPDATE transaction
SET paid = FALSE
WHERE kind = 'ACCOUNT_DEBIT'
  AND transaction_date > CURRENT_DATE;

-- The reminder reads one owner's unpaid rows for one month. Partial, because paid rows are the
-- overwhelming majority and none of them are ever the answer to that question.
CREATE INDEX idx_transaction_owner_unpaid ON transaction (owner_id, transaction_date) WHERE paid = FALSE;
