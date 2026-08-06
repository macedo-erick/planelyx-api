-- Provisioning is now triggered by Keycloak's REGISTER event, which fires once per user, so
-- there is no longer a per-request path that needs to ask "have we set this owner up before?".
--
-- Nothing is lost with the table. Every owner it recorded already has their categories: V13
-- backfilled the ones that predated it, and each row was written in the same transaction as the
-- copy it guarded.
DROP TABLE provisioned_owner;
