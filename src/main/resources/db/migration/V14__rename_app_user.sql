-- `app_user` never held a user. The profile lives in Keycloak; this table holds one id and one
-- timestamp, and exists only so that setting an owner up happens exactly once — see
-- UserProvisioningService. The name invited reading it as a second identity store next to
-- Keycloak, which is the one thing it is not.
ALTER TABLE app_user RENAME TO provisioned_owner;

-- RENAME TO leaves the primary key's index under its generated old name, which would be the
-- last stale reference to `app_user` in the schema.
ALTER INDEX app_user_pkey RENAME TO provisioned_owner_pkey;
