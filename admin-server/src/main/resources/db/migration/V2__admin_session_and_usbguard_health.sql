-- Phase 1 additions. ADD COLUMN IF NOT EXISTS supports existing PostgreSQL
-- databases on which Hibernate update already added some or all of the fields.
ALTER TABLE admin_users
    ADD COLUMN IF NOT EXISTS credential_version bigint;
UPDATE admin_users SET credential_version = 0 WHERE credential_version IS NULL;

ALTER TABLE endpoints
    ADD COLUMN IF NOT EXISTS usbguard_reported_status varchar(20);
ALTER TABLE endpoints
    ADD COLUMN IF NOT EXISTS usbguard_checked_at timestamp(6) with time zone;
