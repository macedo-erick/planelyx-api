-- Categories that back a balance or invoice correction.
--
-- Global (owner_id NULL) like the other defaults, so findAllVisibleToOwner already returns them.
-- The ids are fixed rather than generated because the application references them directly:
-- matching on the name would break the moment the UI translates it.
INSERT INTO category (id, owner_id, name, type, icon, color) VALUES
    ('00000000-0000-0000-0000-00000000ad01', NULL, 'Adjustment', 'EXPENSE', 'pi-sliders-h', '#64748b'),
    ('00000000-0000-0000-0000-00000000ad02', NULL, 'Adjustment', 'INCOME', 'pi-sliders-h', '#64748b')
ON CONFLICT (id) DO NOTHING;
