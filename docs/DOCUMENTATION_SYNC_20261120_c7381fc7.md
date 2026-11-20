# USBProtection — Documentation sync after Settings removal (20/11/2026)

> **Code source inspected:** `main` `c7381fc7b9ba34aa561083431ce7c68b9a514a51` (`refactor(admin): remove Settings page and password-change endpoint`). This is a **documentation-only review**. No Maven, npm, Nginx or Ubuntu tests were executed in this documentation workspace; owner-supplied local log is the source of execution results.

## 1. Cross-checked source changes

- Deleted: `frontend/src/pages/Settings.tsx`, `admin-server/src/main/java/com/group/usbshield/server/auth/AdminPasswordController.java`, `admin-server/src/test/java/com/group/usbshield/server/auth/AdminPasswordControllerTest.java`.
- Frontend route `/#/settings`, nav metadata, fallback links and `changeAdminPassword` adapter were removed; obsolete frontend tests updated. `admin-server/.../config/SpaController.java` no longer redirects `/settings` to SPA. Hash-route `/#/settings` falls through the React 404 view.
- Preserved: `/api/auth/csrf`, `/api/auth/login`, `/api/auth/me`, `/api/auth/logout`, Admin session/CSRF and DB users, `AdminBootstrap`, `AdminPasswordPolicy`, `AdminPasswordResetRunner` with `admin-reset` no-web maintenance profile. Removing the UI **does not** mean deleting password recovery or changing the schema.
- `packaging/tests/verify-phase3-https-acl.sh` checks source-IP HTTPS/ACL from separate Admin/Client environments; `packaging/tests/verify-windows.ps1` does **not** replace it. The scripts and source logic are unchanged in this documentation sync.

## 2. Evidence and release provenance — do not conflate

| Item | Evidence / conclusion |
|---|---|
| Source HEAD under review | `c7381fc7b9ba34aa561083431ce7c68b9a514a51` (main; source baseline, not automatically a binary hash) |
| `verify-windows.ps1` | **Owner-reported PASS** for executed Windows verification gates after Settings removal/frontend tests fix; exact new suite counts not supplied here |
| Client Agent tests | `NotifierStaleSocketRecoveryTest` is Linux-only (`@EnabledOnOs(OS.LINUX)`), with three test methods; Windows JUnit skips can be normal. Check Surefire XML to confirm the actual skipped tests in any specific run |
| `build-release.ps1` | Owner-reported JAR packaging PASS, but **WARNING** release not fully qualified |
| Committed JAR | `releases/usbshield-admin-server_b12bb597d452_20261120T144326Z.jar` |
| JAR manifest `source.commit` | `b12bb597d45289ff507150554d6562b87c5848f4`, **not** `c7381fc7b9ba34aa561083431ce7c68b9a514a51` |
| JAR manifest `workingTreeCleanAtStart` | `false`: dirty-tree provenance, not clean reproducible release |
| JAR manifest build flags | `backendTestsExecuted=true`, `frontendBuildExecuted=true` (not a substitute for actual gate coverage) |
| SHA-256 committed JAR | `8cd573ca4ee70a3bf3c276623d30512cc3a4af92d819c961c57437dfdb7909e0` |
| HTTPS/ACL, native Ubuntu | No new full E2E proof from Windows gate; 09/11 lab PASS remains historical owner report; extended Ubuntu E2E remains deferred |

Rebuild from a committed clean tree if a **fully release-qualified** deliverable is required; store its own JAR checksum/manifest and native gate results. Do not rename the dirty-tree JAR as though its source were `c7381fc7`.

## 3. Document inventory and edit policy

Reviewed the `main` tree at `c7381fc7` (133 Markdown files under `docs/` before this documentation patch) and scanned all Markdown from the supplied repo snapshot + previous doc-sync package for obsolete Settings, password-change API, deleted scripts and stale baselines. Current/operational documentation is updated; historical snapshots are retained.

**Updated active sources:** root `README.md`, root blueprint `USBShield_Blueprint_Allow_Revoke_Reinsert_20261103.md`, `frontend/README.md`, `docs/ADMIN_GUIDE.md`, `API_PHASE1_TO_PHASE3_ADDENDUM.md`, `ARCHITECTURE.md`, `DEVELOPER_GUIDE.md`, `TEST_CATALOG.md`, `RUN_GUIDE.md`, `ADMIN_SERVER_RELEASE.md`, `DEMO_RUNBOOK_20261120.md`, `DOCUMENT_INDEX.md`.

**Intentionally unchanged:** `docs/API.md` and `docs/AGENT_SERVER_PROTOCOL.md` (frozen v0.2 wire/protocol contracts), `TEST_PLAN.md`, `DEMO_SCRIPT.md`, `IMPLEMENTATION_STATUS_*.md`, `PHASE3_HTTPS_ACL_AND_PAGING.md`, dated Ubuntu/HTTPS lab guides, `docs/admin-web/WEEK*`, Phase 3 source snapshots, `docs/admin-web/POST_CORE_E2E_UI_CHANGE_GUARDRAILS.md` (09/11 proposal scoped to old branch), `docs/member1/`, `docs/client-agent/`, `docs/usbguard/`, `docs/qa/`, `docs/rc/` and evidence. Their Settings/password remarks are **historical**, not supported features at `c7381fc7`. `DOCUMENTATION_AUDIT_20261120.md` is the **previous audit snapshot at `721f164`**, not silently rewritten to claim it verified this HEAD. `CLIENT_INSTALLATION.md` remains a redirect to current runbook.

Scope restriction: **documentation files only**; no changes to Java/React/tests, migrations, scripts, packaging, `.env`, release JAR or historical evidence. For actual operations use [Document Index](DOCUMENT_INDEX.md), [Run Guide](RUN_GUIDE.md), [Admin Guide](ADMIN_GUIDE.md), [API addendum](API_PHASE1_TO_PHASE3_ADDENDUM.md), [Release Guide](ADMIN_SERVER_RELEASE.md), and [Architecture](ARCHITECTURE.md).
