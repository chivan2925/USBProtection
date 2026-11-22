# USBShield — Documentation audit / source-alignment (20/11/2026)

> **Source baseline:** `main` `721f164f63533461b6c18b62e1607baa4ab1b731` (`docs: sync README and deployment guides with current release workflow`). **Scope:** inventory of `docs/` Git tree; cross-check of the active top-level operational/API/architecture documents with current release scripts, selected frontend/backend files and reference docs. This is a **documentation review**, not fresh Maven/browser/HTTPS/Ubuntu execution. No user credentials, runtime `.env` or machine-specific secrets were inspected or included.

## 1. How to interpret old documents

At this baseline the Git tree contains **942 files under `docs/`, including 131 Markdown files**, 21 of them at the top of `docs/`. The other files include binary QA screenshots/recordings, JSON/evidence, scripts and artifacts. Older content is not automatically wrong merely because it has an older date: many files are **time-stamped design notes, contracts, handoffs, test plans or test evidence** and must retain the source SHA, test counts and conditions under which they were produced. A document titled `WEEK*`, `*_20261109` or containing an old source SHA is not necessarily a current runbook.

Active documentation is navigated from [DOCUMENT_INDEX.md](DOCUMENT_INDEX.md). For current execution use [RUN_GUIDE.md](RUN_GUIDE.md), [ADMIN_SERVER_RELEASE.md](ADMIN_SERVER_RELEASE.md), [ADMIN_GUIDE.md](ADMIN_GUIDE.md), [ARCHITECTURE.md](ARCHITECTURE.md), [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md), [API v0.2](API.md) **together with** [API Phase 1–3 addendum](API_PHASE1_TO_PHASE3_ADDENDUM.md). Use the dated QA docs to understand **what was actually tested when**, not as evidence that the new HEAD has passed.

## 2. Top-level `docs/` audit decisions

| File | Classification at 20/11 | Decision / rationale |
|---|---|---|
| `ADMIN_GUIDE.md` | Current Admin UI manual | **Updated**: old vinhkhang/04-11 “current” banner, Event Logs server pagination (10/page), name/user filters, Allow/Revoke replug, settings and correct evidence boundary |
| `ADMIN_SERVER_RELEASE.md` | Current packaging/runtime guide | **Updated** reference hierarchy: RUN_GUIDE already describes the 20/11 workflow, so old “09/11 takes precedence” language is no longer accurate |
| `AGENT_SERVER_PROTOCOL.md` | Frozen protocol v0.2 with historical v0.1 content | **Preserved**: API wire contract is deliberately frozen; read together with Phase 1–3 addendum, do not silently revise schemas or historical examples |
| `API.md` | Frozen canonical API v0.2 | **Preserved**: canonical Week 2/Week 4 contract is not superseded by an invented API version; later changes documented in the addendum |
| `API_PHASE1_TO_PHASE3_ADDENDUM.md` | Current additive API/transport contract | **Updated**: removed the false claim it is “chưa merge main”; retained supported admin, pagination, Agent, TLS/ACL semantics |
| `ARCHITECTURE.md` | Current architecture overview | **Updated**: source-independent client/server/enforcement diagram, runtime PostgreSQL/Flyway, Windows release tools and test/E2E boundaries. Earlier v0.1 notes remain traceable in Git history and dated member/QA files |
| `CLIENT_INSTALLATION.md` | Redirect to RUN_GUIDE | **Preserved**: small deliberate compatibility link for historical citations |
| `DEMO_SCRIPT.md` | **Historical** Week 4 rehearsal script | **Preserved**: it contains dated evidence and recorded rehearsal; do not rewrite results as though replayed on 20/11. New current checklist: [DEMO_RUNBOOK_20261120.md](DEMO_RUNBOOK_20261120.md) |
| `DEVELOPER_GUIDE.md` | Current code/developer notes | **Preserved from previous docs sync**; now references canonical verifier and release builder, not deleted Week 3 gate |
| `DOCUMENT_INDEX.md` | Current documentation map | **Updated**: current vs frozen vs historical, new demo/checklist, release tools and dated evidence |
| `HANDOFF_MEMBER1_MEMBER3.md` | Historical cross-member handoff | **Preserved**: describes handoff at source/test milestone, not current operational procedure |
| `IMPLEMENTATION_STATUS_20261104.md` | Historical snapshot 04/11 | **Preserved**: earlier finding IDs differ from review F01–F14 07–09/11 |
| `IMPLEMENTATION_STATUS_20261109.md` | Historical owner/status snapshot 09/11 | **Preserved**: PASS counts, SHA and open/deferred gates belong to that date |
| `PHASE3_HTTPS_ACL_AND_PAGING.md` | Phase 3 contract + 09/11 lab boundary | **Preserved**: describes valid network design and explicitly distinguishes historic owner PASS from fresh 20/11 re-run |
| `RUN_GUIDE.md` | Canonical deployment/runbook | **Preserved from previous docs sync**, still aligns with `.env` test, `.env.e2e.local` runtime, `verify-windows` and `build-release` |
| `TEST_CATALOG.md` | Test inventory, current execution entrypoints | **Updated**: canonical Windows verifier, test DB isolation, deleted milestone scripts, latest frontend assertions and exact Linux/Ubuntu limitations |
| `TEST_PLAN.md` | Historical v0.1/Week 4 test plan | **Preserved**: planning/evidence at old source, not the latest execution report; current commands in TEST_CATALOG and release guide |
| `USBShield_HTTPS_ACL_Setup_usbprotection.lab_vinhkhang_4d2216c_20261109.md` | Lab-specific setup snapshot | **Preserved**: network/CA names/IPs must be revalidated in each target environment; never copy private CA keys or secrets to distribution artifacts |
| `USBShield_Ubuntu24_Minimal_Core_E2E_vinhkhang_f446c04_20261107.md` | Ubuntu minimal-core procedure with dated owner report | **Preserved**: distinguishes tested P0/C1–C5 from extended Ubuntu E2E |
| `USBShield_Week3_File_Ownership_M1_M2_M3_M4_M5.md` | Historic team ownership | **Preserved**: week-specific assignment, not runtime requirements |
| `USBShield_Week4_Work_Plan_M1_M2_M3_M4_M5.md` | Historic sprint plan | **Preserved**: week-specific milestones and acceptance state |

## 3. Subdirectories and historical source of truth

| Subtree | Inventory classification / treatment |
|---|---|
| `docs/admin-web/` | Contains M4/M5 Week 1–3 UI notes, scalability Phase 3 snapshot and post-Core E2E change guardrails. Keep as dated design/handoff. The older `SCALABILITY_PHASE3.md` talks about a separate “Blocked” page; **current UI no longer has that page** and uses the **Devices → Bị chặn** filter instead. Current UI behavior lives in ADMIN_GUIDE, while scalability file stays an implementation snapshot |
| `docs/agent-contract/` | Week 1 sample contract, not new API authority; keep for integration provenance |
| `docs/client-agent/` | Phase 2/reliability design and Week 1–4 Agent notes/closeouts; use current Agent source + Ubuntu E2E manuals for live procedure |
| `docs/member1/` | Archived API v0.1 and server Week 1–4 handoffs, preserving old scripts and test counts as history |
| `docs/qa/` | QA plans, findings, evidence ledgers, screenshots, replay/JSON and dated signoffs; **do not revise retrospective evidence or reinterpret old H2 results as current PostgreSQL tests** |
| `docs/rc/` | Week 4 delegated review and signoff templates; historical acceptance boundary, not current release qualification |
| `docs/usbguard/` | USBGuard POC, security/rules design, native operations guides, Week-specific QA/evidence; real current Ubuntu enforcement still requires native tests |

## 4. Reconciled current behavior from code and scripts

- `admin-server/build-release.ps1` is for **release JAR packaging**: React + Maven, optional tests, JAR content checks, per-commit UTC artifact name, SHA-256 and manifest; clean working tree is required unless `-AllowDirtyTree`. Test `.env` is process-local to build/tests and **does not become runtime configuration**.
- `packaging/tests/verify-windows.ps1` is the **canonical Windows verification entrypoint** requiring PowerShell 7.6+; `-AdminOnly` excludes Agent/DEB checks. It builds JAR after `clean test`, runs current live suites on PostgreSQL test schemas, and does not import old `verify-week3.ps1` / `ddl-auto=create` destructive behavior.
- Deleted `admin-server/verify-week1.ps1`, `verify-week2.ps1`, `verify-week3.ps1` are absent in HEAD. Historical references in Week 1–4 evidence remain untouched.
- `.env`: test-only role `usbshield_test` and databases `usbshield_test`, `usbshield_browser_test`; `.env.e2e.local`: untracked **runtime E2E** configuration for profile `prod` and app database `usbshield`. Java `-jar` with `--spring.profiles.active=prod` does **not** load either file. Missing `USBSHIELD_DB_URL` yields unresolved JDBC placeholder, not proof the JAR is corrupt.
- Frontend `LiveEvents` sets page size **10**, so a 14-item Decision filter yields `1–10 trên 14 bản ghi` on page one and `11–14 trên 14 bản ghi` on page two. `Blocked Devices` is no longer a separate route; Events and Devices provide filters.
- HTTPS/TLS/CA/ACL and PostgreSQL installation are **deployment dependencies outside the JAR**. Ubuntu Agent enforces USBGuard on Client; Admin ACK/ONLINE/UI are not independent physical-enforcement proof.

## 5. Validation and known limits of this documentation patch

**Document checks**: source paths and selected relative Markdown links were compared with the HEAD tree; replacement docs were checked for UTF-8, whitespace and internal link targets. **No Maven, browser, Windows Nginx, Ubuntu USBGuard, physical USB, packaging or runtime tests were executed to validate the documentation changes.** Those results must be supplied by the owner. The current Git HEAD can change after this baseline; rebase documentation edits rather than blindly overwrite newer work.

**Evidence boundaries**: owner-reported Windows and HTTPS/ACL PASS, Ubuntu Minimal Core P0/C1–C5 PASS on 09/11 remain historical; extended Ubuntu acceptance (Debian remove/upgrade, reboot/offline, negative TLS, native notifier IPC, stress/security) remains deferred unless separately retested. The documentation update does not accept or close those gates.
