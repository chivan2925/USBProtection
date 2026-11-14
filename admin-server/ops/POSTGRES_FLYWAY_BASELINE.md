# USBShield PostgreSQL: first Flyway adoption (Phase 2 / F07)

This runbook is **only for an existing unversioned application database** (for example `usbshield`) which Hibernate formerly maintained with `ddl-auto=update`. It does **not** apply to a fresh empty database or to the dedicated integration/browser test databases.

## Safety requirements

1. Stop every Admin Server process connected to this application database. Take a **verified, restorable backup** of both database structure and data first. Never run this on `usbshield_test` or `usbshield_browser_test`.
2. Open the **application** PostgreSQL database in IntelliJ Database. Run this **read-only** preflight query and verify the output:

   ```sql
   SELECT current_database() AS db, current_schema() AS schema,
     (SELECT COUNT(*) FROM information_schema.tables
       WHERE table_schema='public' AND table_type='BASE TABLE'
         AND table_name IN ('admin_users','endpoints','usb_devices','usb_events',
                            'endpoint_whitelist_entries')) AS expected_app_tables,
     (SELECT COUNT(*) FROM information_schema.tables
       WHERE table_schema='public' AND table_type='BASE TABLE') AS total_public_tables,
     to_regclass('public.flyway_schema_history') AS existing_flyway_history;
   ```

   Continue only if `db=usbshield`, `schema=public`, `expected_app_tables=5`, `total_public_tables=5`, and `existing_flyway_history` is NULL. If any is different, stop and review the schema: do **not** force baseline.
3. Build the new Admin Server JAR with the Phase 2 code. Set `USBSHIELD_DB_USERNAME`, `USBSHIELD_DB_PASSWORD`, `USBSHIELD_DB_HOST`, `USBSHIELD_DB_PORT`, and `USBSHIELD_DB_NAME=usbshield` for the application database. Do not use the test credentials. Do not put the password in a command-line argument or Git.
4. Run the following **one-time** command from the repository root (PowerShell):

   ```powershell
   java -jar admin-server/target/usbshield-admin-server.jar `
     --spring.main.web-application-type=none `
     --spring.flyway.baseline-on-migrate=true `
     --spring.flyway.baseline-version=1 `
     --spring.jpa.hibernate.ddl-auto=validate
   ```

   This explicitly marks V1 as the existing baseline, applies V2 to add any missing Phase 1 columns, and makes Hibernate validate the resulting schema. **Do not persist** `baseline-on-migrate=true` in any YML/environment variable. No destructive SQL is expected from these migrations. The command may require restarting/terminating the no-web Java process after it finishes its log messages.
5. Verify migration history in the **application** database:

   ```sql
   SELECT installed_rank, version, description, type, success
   FROM public.flyway_schema_history ORDER BY installed_rank;
   ```

   Expected: successful V1 `BASELINE` and V2 `SQL` (or equivalent history descriptions depending on Flyway version). Verify the Admin Server starts normally afterward **without** baseline-on-migrate and keeps the existing endpoints, users, devices, events and whitelist records.

## Fresh database

On a brand-new, otherwise empty PostgreSQL database, do **not** baseline. Boot uses Flyway V1 to create tables and V2 to add `credential_version` and USBGuard health fields, then Hibernate `validate`. No `ddl-auto=update/create/drop` is permitted for the normal Admin Server runtime.

## Why V1 + V2

Some installations were started after Phase 1 (new columns already present), others were not. V2 uses guarded `ADD COLUMN IF NOT EXISTS` and backfills missing `credential_version` to zero; it handles both cases. Flyway doesn't infer or repair arbitrary schema drift; Hibernate `validate` is a separate runtime compatibility check. Always back up before changing an existing database.

## Test databases

- Java integration tests: `usbshield_test`, role `usbshield_test`, random schema `usbshield_it_*` **per test class**, cleaned at the end of that class.
- Browser live tests: `usbshield_browser_test`, role `usbshield_test`, random schema `usbshield_e2e_*` **per browser run**, migrated using Flyway and cleaned when its backend JVM stops.
- The test role must have `CREATE` permission on **these two test databases only**. Tests refuse the application/admin database names and roles. Hard-killing a test process can leave an abandoned random test schema; inspect and clean it manually only after verifying its ownership and that no matching test process remains.
