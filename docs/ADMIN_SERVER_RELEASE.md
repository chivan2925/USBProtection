# USBShield — Admin Server: build, Windows verification, JAR distribution and runtime

> **Source baseline:** `main` `79384e26563ea08db7c16c223c618e1f51e9a3d9` (20/11/2026). This document describes the scripts **as committed at that source SHA**; it is not a fresh test report or release attestation. Where `RUN_GUIDE.md` still describes the older 09/11 workflow, the **20/11 script invocation and environment rules in this document take precedence**; its PostgreSQL, HTTPS, Ubuntu and policy instructions remain applicable. For end-to-end Ubuntu deployment, use [RUN_GUIDE.md](RUN_GUIDE.md). For Java/API development, use [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md).

## 1. Keep the responsibilities separate

| Component | What it does | What it does **not** do |
|---|---|---|
| `admin-server/build-release.ps1` | Build React and an executable Spring Boot JAR; optionally run Maven tests; verify JAR contents; copy a versioned artifact and write SHA-256/manifest | Does not start a persistent server, install PostgreSQL, configure TLS, or embed `.env` |
| `packaging/tests/verify-windows.ps1` | Run the current Windows regression gates and package checks | Does not prove native Ubuntu USBGuard, physical USB, HTTPS/LAN ACL or service lifecycle |
| `scripts/with-env.ps1` | Read an explicitly chosen `.env`-style file and run a command in that environment | Does not build/repackage JAR; does not make its variables persist after the child PowerShell exits |
| `java -jar ... --spring.profiles.active=prod` | Start the server with the **current process environment** | Does **not** automatically load `.env.e2e.local` or `.env` |

All commands below assume a PowerShell session at the **repository root**. Source build requires JDK 21 (or a compatible JDK), Maven, Node/npm and Git. `verify-windows.ps1` requires **Windows PowerShell 7.6+ (pwsh)** and dedicated PostgreSQL test databases. Runtime JAR requires Java 21+ and a running PostgreSQL service but **does not require Maven, Node, npm or the source checkout**.

## 2. Test databases vs. actual Admin Server database

- `.env` is **test-only**. Copy from `.env.example` and populate `USBSHIELD_TEST_DB_HOST`, `USBSHIELD_TEST_DB_PORT`, `USBSHIELD_TEST_DB_USERNAME=usbshield_test`, `USBSHIELD_TEST_DB_PASSWORD`. Test databases: `usbshield_test` and `usbshield_browser_test`; test role `usbshield_test` must not have permission to modify the application database.
- `.env.e2e.local` is an **untracked, environment-specific runtime file** used in the owner's E2E lab for `prod` profile. It may contain `USBSHIELD_DB_URL`, `USBSHIELD_DB_USERNAME`, `USBSHIELD_DB_PASSWORD`, `USBSHIELD_ENROLLMENT_TOKEN`, initial Admin bootstrap credentials and `PORT`/`USBSHIELD_BIND_ADDRESS`. This is a **lab prod-profile deployment**, not proof of an Internet-ready production service.
- Application DB: `usbshield`, role `usbshield`; **separate from both test databases**. For a new, empty application database, Flyway applies migrations automatically. Existing unversioned DBs require the reviewed baseline procedure in [`admin-server/ops/POSTGRES_FLYWAY_BASELINE.md`](../admin-server/ops/POSTGRES_FLYWAY_BASELINE.md). Do not apply a test cleanup, `ddl-auto=create` or browser suite to `usbshield`.
- Do not commit or distribute either local environment file, credentials, generated Agent enrollment material or database dumps.

## 3. Windows verification

Canonical verification entrypoint:

```powershell
# Admin Server Maven/frontend + packaged JAR + browser/HTTP-live suites;
# skips Agent and .deb packaging checks.
.\packaging\tests\verify-windows.ps1 -AdminOnly

# All supported Windows gates: Admin, Agent, frontend and .deb packaging.
.\packaging\tests\verify-windows.ps1
```

The script loads **only the required test DB settings from `.env`**, isolates them from inherited application/prod datasource configuration, and executes Admin Maven tests before rebuilding the Admin JAR. In the normal flow, it also runs frontend unit tests, lint and production build, checks JAR contents, then launches `test:browser:live` and `test:live` using **temporary PostgreSQL test schemas**. Full Windows mode additionally executes Agent Maven tests and validates a Windows-built `.deb` and release manifest. It does not import historical `admin-server/verify-week3.ps1` assertions or use `ddl-auto=create` for these browser suites.

`-SkipTests` and `-SkipBrowserLive` deliberately reduce coverage. `-SkipDebBuild` inspects an existing `.deb` and does not count as a fresh build. A partial run cannot be reported as a complete Windows PASS. On Windows, Linux-specific Agent tests may be skipped by JUnit; record exact counts from the new run rather than reusing historical numbers.

If a browser assertion fails, distinguish failure in **test expectations** from a real UI/API defect. For example, when the Events page has page size 10, a test expecting `1–14 trên 14 bản ghi` is incorrect for a 14-item filtered result; correct coverage includes page 1 (`1–10`) and page 2 (`11–14`). Do not alter application behavior merely to satisfy a stale assertion.

## 4. Build a distributable JAR

```powershell
# Recommended for a clean committed source checkout; runs Admin Maven tests.
.\admin-server\build-release.ps1

# Optional destination outside the repository.
.\admin-server\build-release.ps1 -OutputDirectory 'C:\USBShieldReleases'
```

By default, `build-release.ps1`:

1. Refuses a dirty Git working tree unless `-AllowDirtyTree` is explicitly supplied.
2. Runs `npm ci` and a current production React build.
3. Loads `.env` **only for Maven tests** (unless `-SkipTests`), shields the tests from inherited production datasource variables, and restores the previous environment afterward.
4. Runs `mvn ... -Pwith-frontend clean package`, producing `admin-server/target/usbshield-admin-server.jar` containing React static assets and Flyway migrations.
5. Uses `jar.exe` from the **same JDK as the selected `java.exe`**, checks required classes/assets/migrations and copies the output to `admin-server/target/release/` by default.
6. Produces a filename `usbshield-admin-server_<12-char-commit>_<UTC-stamp>.jar`, a sibling `<filename>.manifest.json`, and `<filename>.sha256`.

Optional parameters:

```powershell
# Use when the default PATH/JAVA_HOME selects an unsupported JDK.
.\admin-server\build-release.ps1 -JavaHome 'C:\Path\To\JDK21'

# Non-release build from uncommitted changes (marked non-release-qualified).
.\admin-server\build-release.ps1 -AllowDirtyTree

# Skips Admin Maven tests: weaker evidence; explicitly marked in manifest.
.\admin-server\build-release.ps1 -SkipTests
```

**Do not interpret `BUILD SUCCESS` or a generated JAR as evidence of a complete verifier pass.** Publish a clean-source artifact only after the verification gates relevant to the release pass. Keep the manifest and checksum alongside the JAR; the Git commit alone does not identify a dirty-build binary.

## 5. Run a JAR locally with `prod` settings

When using the owner's lab runtime file:

```powershell
.\scripts\with-env.ps1 -EnvFile .env.e2e.local -Command {
    java -jar admin-server/target/usbshield-admin-server.jar --spring.profiles.active=prod
}
```

When the required runtime variables **already exist** in the invoking PowerShell session or machine environment, this is sufficient:

```powershell
java -jar admin-server/target/usbshield-admin-server.jar --spring.profiles.active=prod
```

`--spring.profiles.active=prod` selects `application-prod.yml`; it **does not read an environment file**. The `prod` datasource requires a valid `USBSHIELD_DB_URL` such as `jdbc:postgresql://127.0.0.1:5432/usbshield`, `USBSHIELD_DB_PASSWORD` and the correct role (`USBSHIELD_DB_USERNAME`, default `usbshield`). The profile also requires `USBSHIELD_ENROLLMENT_TOKEN`; keep bootstrap Admin credentials external to Git.

The specific error `Driver org.postgresql.Driver claims to not accept jdbcUrl, ${USBSHIELD_DB_URL}` means the JDBC URL placeholder was not resolved in the launched process. It is **not by itself evidence** that the JAR failed to build or that PostgreSQL is offline. Load runtime variables correctly before investigating database connectivity.

The server binds to loopback by default. Configure `PORT`/`USBSHIELD_BIND_ADDRESS` as needed. For the current HTTPS reverse proxy lab, keep Spring Boot bound to `127.0.0.1` (e.g. port `18080`) and terminate TLS in Nginx; `prod` enables secure session cookies by default.

## 6. Give the JAR to another person

The recipient does **not** need to clone the repository. Distribute only the **chosen, verified** versioned release JAR, checksum and a deployment guide via GitHub Releases or another controlled artifact channel. Avoid committing binary JARs to the source tree. Keeping `target/` locally is correct: that folder is a build output, not part of the required runtime path.

Recipient prerequisites:

1. Java 21+ compatible runtime.
2. Running PostgreSQL plus an application-owned database/role. On a new empty database, Flyway applies V1/V2 at startup; a dump is not needed. Existing user data must be backed up/migrated separately.
3. Runtime environment variables or equivalent OS/service secrets; **do not give them the owner's `.env.e2e.local`**.
4. If Browser or Ubuntu Agent connects from other machines, appropriately configured **Nginx HTTPS, TLS certificate/private key, SAN-matching hostname/DNS, firewall and route/IP ACL**. Agent must trust the certificate's CA and may access only `/api/agent/**`; Admin pages/auth/API are limited to Admin machines. Nginx and certificates are **not inside the JAR**.

The JAR can live anywhere, for example `C:\USBShield\usbshield-admin-server.jar`. Then run the file by its actual name/path; the versioned release filename is **not** automatically the fixed `admin-server/target/usbshield-admin-server.jar` path. Copying the JAR out of `target` does not change its contents or add environment configuration.

Verify integrity against the release SHA-256 before running:

```powershell
Get-FileHash 'C:\USBShield\usbshield-admin-server.jar' -Algorithm SHA256
```

**Verification boundary:** Successful local HTTP/DB startup is not equivalent to HTTPS/ACL or USBGuard physical E2E acceptance. Record actual source SHA, build flags, JAR hash, Windows logs, deployment config and Ubuntu evidence separately.
