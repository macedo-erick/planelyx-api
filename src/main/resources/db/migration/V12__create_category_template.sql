-- The blueprint every user's categories are copied from.
--
-- Until now the seeded categories were single global rows (owner_id NULL) shared by everyone,
-- which made them readable but not writable: the update and delete paths match on
-- owner_id = :ownerId, and that is never true for NULL. Splitting the blueprint out lets every
-- user own a real copy, so there is no longer any category that some code paths can see and
-- others cannot.
--
-- Rows are copied from the current globals rather than restated as literals, so V9, V10 and V11
-- stay the single source of truth for their content.
CREATE TABLE category_template (
    id     UUID PRIMARY KEY,
    name   VARCHAR(255) NOT NULL,
    type   VARCHAR(20)  NOT NULL CHECK (type IN ('EXPENSE', 'INCOME')),
    icon   VARCHAR(100),
    color  VARCHAR(20),
    system BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO category_template (id, name, type, icon, color, system)
SELECT gen_random_uuid(), name, type, icon, color, (name = 'Adjustment')
FROM category
WHERE owner_id IS NULL;

-- The application looks up a user's adjustment category by (owner, type). That is only
-- single-valued if there is exactly one system template per type, so say so.
CREATE UNIQUE INDEX uq_category_template_system_type
    ON category_template (type) WHERE system;

-- Carried onto every copy, so the flag costs no join. It marks the categories the application
-- writes corrections against: the client keeps them out of its pickers and the API refuses to
-- let a user edit or delete one.
ALTER TABLE category ADD COLUMN system BOOLEAN NOT NULL DEFAULT FALSE;
