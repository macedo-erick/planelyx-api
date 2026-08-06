-- Gives every existing user their own copy of the seeded categories and retires the globals.
--
-- Afterwards category.owner_id is NOT NULL again and no row is shared, so a category is either
-- yours or invisible to you — there is no third case for the read and write paths to disagree
-- about. The adjustment categories lose their pinned ids in the process: each user's copy is a
-- fresh gen_random_uuid(), and the application finds it by (owner, system, type) instead.

-- The users table the app never had. Profiles live in Keycloak and only the JWT sub was ever
-- stored, so there was nowhere to record that a user had been seeded — which is what makes
-- seeding happen once and never again, even for a user who deletes every category they own.
CREATE TABLE app_user (
    id         UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- No user table to read yet, so the existing owners have to be recovered from their data.
CREATE TEMP TABLE owners AS
SELECT DISTINCT owner_id AS id FROM bank_account
UNION
SELECT DISTINCT owner_id FROM credit_card
UNION
SELECT DISTINCT owner_id FROM transaction
UNION
SELECT DISTINCT owner_id FROM transaction_template
UNION
SELECT DISTINCT owner_id FROM category WHERE owner_id IS NOT NULL;

INSERT INTO app_user (id) SELECT id FROM owners;

-- Which global row each transaction currently points at, remembered before the globals go.
CREATE TEMP TABLE old_global AS
SELECT id, name, type FROM category WHERE owner_id IS NULL;

-- Ids are generated up front rather than read back from the INSERT so that the old -> new
-- mapping is explicit. Matching on (owner_id, name, type) later is safe precisely because this
-- table holds only the rows about to be created: a category the user had already made under the
-- same name is not in here and cannot collide.
CREATE TEMP TABLE seeded AS
SELECT gen_random_uuid() AS id,
       o.id              AS owner_id,
       ct.name,
       ct.type,
       ct.icon,
       ct.color,
       ct.system
FROM owners o
         CROSS JOIN category_template ct;

INSERT INTO category (id, owner_id, name, type, icon, color, system)
SELECT id, owner_id, name, type, icon, color, system
FROM seeded;

-- Both transaction.category_id and transaction_template.category_id are NOT NULL REFERENCES
-- category (id) with no ON DELETE, so the references have to move before the globals are dropped.
UPDATE transaction t
SET category_id = s.id
FROM old_global g
         JOIN seeded s ON s.name = g.name AND s.type = g.type
WHERE g.id = t.category_id
  AND s.owner_id = t.owner_id;

UPDATE transaction_template tt
SET category_id = s.id
FROM old_global g
         JOIN seeded s ON s.name = g.name AND s.type = g.type
WHERE g.id = tt.category_id
  AND s.owner_id = tt.owner_id;

DELETE FROM category WHERE owner_id IS NULL;

-- Undoes V8. Nothing is shared any more, so a category without an owner is now a bug rather
-- than a default.
ALTER TABLE category ALTER COLUMN owner_id SET NOT NULL;

DROP TABLE owners, old_global, seeded;
