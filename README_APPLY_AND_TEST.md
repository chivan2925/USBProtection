# USBProtection — Copilot follow-up corrections (2026-11-02)

**Source baseline:** `chivan2925/USBProtection` `main` commit `8e64a5a8cec8ca7a29e0fb5e7ece094b8a4313c9`.

This file replaces the outdated Ubuntu regression patch instructions. The tracked
`USBProtection_Ubuntu_E2E_Regression_Fix.patch` in older checkouts is OBSOLETE:
it represents an earlier implementation and must NOT be used to rebuild the
current source. The historical `modified-files/` folder mentioned by the old
README was never committed and is no longer advertised.

## Apply the new delta package

1. Ensure your working tree matches the above commit (`git rev-parse HEAD`) and
   back up the H2 database. Stop the Admin Server if it is running.
2. Unzip the new Copilot correction ZIP into the **repository root**. It only
   includes changed or new source, tests, and this README, with original paths.
3. Remove the obsolete tracked patch from your working tree and the next commit:

   ```powershell
   git rm -- USBProtection_Ubuntu_E2E_Regression_Fix.patch
   ```

4. Review the full diff (`git diff --check`, `git diff --stat`, `git status --short`).
   Do not reapply the original 2026-11-02 Ubuntu patch to this branch.

## Tests before native deployment

Run on Windows from repository root:

```powershell
mvn -B -f client-agent/pom.xml clean test
mvn -B -f admin-server/pom.xml clean test
Push-Location frontend
npm ci
npm test
npm run lint
npm run build
Pop-Location
```

**Review corrections covered:** durable retry of initial and final USB events,
canonical full-payload idempotency and endpoint-row serialization for concurrent
deliveries, batch-loaded whitelist metadata, and accurate frontend validation.

**Database compatibility:** New nullable H2 column `usb_events.payload_digest`
(64 lowercase hex characters). Existing events retain null digest; the Server
compares their persisted audit/device fields on duplicate-key requests. H2 is
configured with Hibernate `ddl-auto=update` in this project; verify schema
migration against a backed-up lab DB before starting a newer Server.

**Verification boundary:** Java 21 listener compilation and standalone smoke,
canonical SHA-256 digest smoke, and focused frontend Node tests passed in the
author environment. Full Maven/JUnit, complete frontend suite and real Ubuntu
USBGuard, socket, device and database integration tests have NOT been executed
in that environment. Do not mark native E2E as accepted solely from these checks.
