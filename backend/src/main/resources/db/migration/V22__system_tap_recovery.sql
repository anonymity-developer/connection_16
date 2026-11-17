-- Automatic revalidation has no human actor. Preserve manual recovery identities.
ALTER TABLE tap_recoveries MODIFY COLUMN actor_id BIGINT NULL;
