# USBShield PostgreSQL and Flyway

For P1, use a fresh PostgreSQL database. V3 refuses to migrate a V1/V2 schema containing endpoint, device, event or whitelist rows. Keep the existing database and verified backup until the ownership mapping and cutover are approved. The historical V1/V2 adoption procedure below requires an artifact built from the V1/V2 code.

## Historical V1/V2 adoption

This runbook is **only for an existing unversioned application database** (for example `usbshield`) which Hibernate formerly maintained with `ddl-auto=update`. It does **not** apply to a fresh empty database or to the dedicated integration/browser test databases.

## Safety requirements

1. Stop every Admin Server process connected to this application database. Take a **verified, restorable backup** of both database structure and data first. Apply this procedure only to the verified application database.
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

On a fresh PostgreSQL database, boot runs Flyway V1 for base tables, V2 for credential version and USBGuard health fields, and V3 for workspace, enrollment and composite foreign keys. Hibernate then validates the schema. Keep `spring.flyway.baseline-on-migrate=false` and `spring.jpa.hibernate.ddl-auto=validate`.

## Why V1 + V2

Some installations were started after Phase 1 (new columns already present), others were not. V2 uses guarded `ADD COLUMN IF NOT EXISTS` and backfills missing `credential_version` to zero; it handles both cases. Flyway doesn't infer or repair arbitrary schema drift; Hibernate `validate` is a separate runtime compatibility check. Always back up before changing an existing database.

## Test databases

- Java integration tests: `usbshield_test`, role `usbshield_test`, random schema `usbshield_it_*` **per test class**, cleaned at the end of that class.
- The test role needs `CREATE` on the dedicated test database. Tests verify database and role. If a test process is terminated abruptly, verify ownership and process status before removing a leftover test schema.
