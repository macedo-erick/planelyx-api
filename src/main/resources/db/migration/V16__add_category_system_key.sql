-- Names the role a system category plays, so the application can find one without matching a name.
--
-- Until now there was exactly one system category per type, and the lookup leaned on that:
-- findAdjustmentForOwner matches (owner_id, system, type) and returns an Optional, which is
-- single-valued only because uq_category_template_system_type allowed one system template per
-- type. A second system EXPENSE category — the invoice payment one added below — makes that
-- query return two rows and throw.
--
-- The discriminator is a key rather than the name because V11 already established why: the name
-- is what a client translates, so matching on it breaks the moment the UI does.
ALTER TABLE category_template ADD COLUMN system_key VARCHAR(40);
ALTER TABLE category ADD COLUMN system_key VARCHAR(40);

UPDATE category_template SET system_key = 'ADJUSTMENT' WHERE system;
UPDATE category SET system_key = 'ADJUSTMENT' WHERE system;

-- Replaces uq_category_template_system_type, which said one system template per type — no longer
-- true once the invoice payment row below joins the two adjustment ones. What the lookup actually
-- needs is one per role and type: an adjustment comes in both an EXPENSE and an INCOME flavour,
-- because a correction can push a balance either way.
DROP INDEX uq_category_template_system_type;

CREATE UNIQUE INDEX uq_category_template_system_key
    ON category_template (system_key, type) WHERE system_key IS NOT NULL;

-- The category an invoice settlement is filed against. System, so the client keeps it out of its
-- picker and the API refuses to let a user file against it by hand.
INSERT INTO category_template (id, name, type, icon, color, system, system_key)
VALUES (gen_random_uuid(), 'Invoice payment', 'EXPENSE', 'pi-credit-card', '#64748b', TRUE, 'INVOICE_PAYMENT');

-- Existing owners were provisioned before that template existed, so they need the copy that
-- registration would otherwise have given them. Owners are recovered from their data exactly as
-- V13 did — there is still no users table.
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
WHERE ct.system_key = 'INVOICE_PAYMENT'
  AND NOT EXISTS (SELECT 1
                  FROM category c
                  WHERE c.owner_id = o.id
                    AND c.system_key = ct.system_key);
