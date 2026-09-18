-- category_id is NOT NULL, so movements need a category. One system key in both flavours, as
-- ADJUSTMENT already is: the flavour says which side of the ledger a row sits on, and no aggregate
-- reads it to decide what is spending — the kind alone does that.
INSERT INTO category_template (id, name, type, icon, color, system, system_key)
VALUES
    (gen_random_uuid(), 'Investment', 'EXPENSE', 'pi-chart-line', '#64748b', TRUE, 'INVESTMENT'),
    (gen_random_uuid(), 'Investment', 'INCOME', 'pi-chart-line', '#64748b', TRUE, 'INVESTMENT');

-- Existing owners predate the templates, so they need the copy registration would have given them.
-- Owners are recovered from their data exactly as V13 and V17 did; there is still no users table.
INSERT INTO category (id, owner_id, name, type, icon, color, system, system_key)
SELECT gen_random_uuid(), o.id, ct.name, ct.type, ct.icon, ct.color, ct.system, ct.system_key
FROM (SELECT DISTINCT owner_id AS id FROM bank_account
      UNION
      SELECT DISTINCT owner_id FROM credit_card
      UNION
      SELECT DISTINCT owner_id FROM transaction
      UNION
      SELECT DISTINCT owner_id FROM transaction_template
      UNION
      SELECT DISTINCT owner_id FROM category) o
         CROSS JOIN category_template ct
WHERE ct.system_key = 'INVESTMENT'
  AND NOT EXISTS (SELECT 1
                  FROM category c
                  WHERE c.owner_id = o.id
                    AND c.system_key = ct.system_key
                    AND c.type = ct.type);
