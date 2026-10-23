# M5 Week 3 — review after pull, 2026-10-23

Reviewed: 2026-10-23 (Asia/Bangkok). Branch `huy`; commit **`8c222a47142d7a80f75e56b8d77e5bf732797e16`**. The working tree was clean when review started. Scope reference: section 6 and the Windows gate of the supplied Week 3 M1–M5 ownership plan.

**M5 implementation and current Windows regression: PASS. Existing plans/demo draft: ready. Primary QA inventory/summary/index: need an integrated-revision addendum. Group gate: remains open. Ubuntu runtime: deferred until after Week 4.**

This report and its [fresh evidence](week3-evidence/2026-10-23/post-pull-audit/README.md) are a separate audit of the pulled revision. Earlier evidence retains its original dates, source and scope. Product source was not changed during the audit.

## M5 task-by-task status

| Week 3 responsibility | Status on pulled code | Review finding / remaining work |
|---|---|---|
| Event History UI: endpoint/Linux username/USB/decision/source time and loading/empty/error | DONE | M5 UI/component source is byte-identical to the last successful M5 capture; current component and browser suites pass; no mock fallback on live reads |
| Mapper/types/filter: UNKNOWN, source precision, endpoint identity, server metadata and invalid payload rejection | DONE | Existing source preserved; exact legacy API and partial name/username UI queries pass current server/frontend/browser checks |
| Four M5 regression suites: endpoint/user/device/decision/date, paging/auth/malformed response | DONE | Included in fresh **96/96** integrated frontend tests; eight added M4 tests account for the increase from M5's earlier 88-test run |
| Windows browser against real Spring Boot/H2 | DONE | All three existing browser suites pass; Events uses 28 synthetic HTTP inputs, A/B, UNKNOWN/no-serial, partial usernames, dates, 403/500/network/recovery and mobile. Additional same-origin combined-JAR variant also passes |
| `docs/TEST_PLAN.md` Week 3 scope/reference | DONE | References Week 3 documents, Windows execution, W3-F11/F12 follow-ups and deferred Ubuntu cases |
| `WEEK3_INTEGRATION_TEST_PLAN.md` end-to-end matrix | DONE | Enrollment/heartbeat, isolation, no-serial, attribution, outage, reboot, lifecycle and notification are covered with Windows/deferred separation |
| `WEEK3_TEST_INVENTORY.md` execution bookkeeping | EXISTS; UPDATE NEEDED | Main table still records 2026-10-21 / `dee3cf7`-based runs and earlier 85/88, 13/18/20 counts. Add current revision, 96/26/61 results and new M1/M4 evidence without overwriting historical rows |
| `WEEK3_QA_SUMMARY.md` integrated QA handoff | EXISTS; UPDATE NEEDED | Earlier pending M1 profile/embedded React and M4 UI implementation items are now supplied. Add the present audit results and reclassify remaining owner work; do not infer group acceptance from technical tests |
| `docs/DEMO_SCRIPT.md` Week 3 draft | DONE AS DRAFT | Required login → endpoint → event → Allow/Revoke A → B unaffected → outage/recovery walkthrough and separate deferred Ubuntu path exist. Final rehearsal remains Week 4; current bundled-JAR invocation/source can be added when aligning handoff |
| `week3-evidence/README.md` execution index | EXISTS; UPDATE NEEDED | Primary index currently points to 2026-10-21 snapshots. New audit has its own complete index; link it from the primary index during QA closeout |
| P1: retain Week 2 history | DONE | No diff to `WEEK2_QA_SUMMARY.md`, `WEEK2_TEST_INVENTORY.md` or `M4_M5_WEEK2_HANDOFF.md` relative to the supplied baseline. Historical acknowledgment status is not rewritten |

## Fresh verification on the integrated revision

| Check | Result | Evidence |
|---|---|---|
| Exact lockfile install | PASS: `npm ci --offline --no-audit --no-fund` | [Install](week3-evidence/2026-10-23/post-pull-audit/01-npm-ci.txt) |
| Frontend tests | PASS: **96/96**, no failures/skips/cancellations | [Tests](week3-evidence/2026-10-23/post-pull-audit/02-frontend-tests.txt) |
| Lint / TypeScript / Vite build | PASS | [Lint](week3-evidence/2026-10-23/post-pull-audit/03-frontend-lint.txt), [build](week3-evidence/2026-10-23/post-pull-audit/04-frontend-build.txt) |
| Agent unit/mock/contract tests + JAR | PASS: **61 tests**, 0 failures/errors/skips | [Agent](week3-evidence/2026-10-23/post-pull-audit/05-agent-tests-package.txt) |
| Server tests + JAR containing current React bundle | PASS: **26 tests**, 0 failures/errors/skips; `-Pwith-frontend clean package` | [Server](week3-evidence/2026-10-23/post-pull-audit/06-server-with-frontend.txt) |
| Fixture browser | PASS | [Fixture](week3-evidence/2026-10-23/post-pull-audit/07-browser-fixture.txt) |
| Real backend management browser | PASS | [Management](week3-evidence/2026-10-23/post-pull-audit/08-browser-management.txt) |
| Real backend Events browser | PASS | [Events](week3-evidence/2026-10-23/post-pull-audit/09-browser-events.txt) |
| New M4 standalone policy HTTP test | PASS: **1/1**; duplicate Revoke, future ACK rejection and B isolation | [Policy](week3-evidence/2026-10-23/post-pull-audit/10-policy-http-contract.txt); separate from `npm test` |
| Source/JAR packaging static checks | PASS | [Packaging](week3-evidence/2026-10-23/post-pull-audit/11-packaging-static.txt) |
| Browser UI/API directly from one combined JAR | PASS with all existing M5 Events assertions | [Packaged-JAR browser](week3-evidence/2026-10-23/post-pull-audit/12-browser-packaged-jar.txt) |
| Four individual shell syntax checks | PASS; parsing only | [Builder](week3-evidence/2026-10-23/post-pull-audit/13-shell-builder.txt), [postinst](week3-evidence/2026-10-23/post-pull-audit/14-shell-postinst.txt), [prerm](week3-evidence/2026-10-23/post-pull-audit/15-shell-prerm.txt), [launcher](week3-evidence/2026-10-23/post-pull-audit/16-shell-launcher.txt) |

The combined-JAR browser audit used a temporary derived copy of the original M5 script: `web = api` and no Vite startup. All original assertions ran. [Variant provenance](week3-evidence/2026-10-23/post-pull-audit/packaged-browser-variant.json) records the exact source/replacements/hash. Runtime was isolated loopback HTTP with the default application profile, not a production HTTPS deployment. Production configuration is separately covered by the current M1 tests and owner evidence.

## New integration evidence now available

- **M1:** [Week 3 hardening report](../member1/WEEK3_SERVER_HARDENING.md), production profile, role/credential/status/ACK tests, SPA navigation and optional embedded React Maven build are now present. The integrated test total is 26 because it includes M5's seven Event search tests in addition to M1's reported 19. Current same-origin UI/API behavior was freshly checked above.
- **M4:** [Week 3 UI/UX report](../admin-web/WEEK3_UI_UX_HARDENING.md), eight added frontend tests, shared auth/session handling, management mapper/pending-state behavior and Windows evidence are present. Existing M5 source is preserved, and the shared integration passes fresh regression.
- **M2/M3:** Client-agent and packaging source hashes match the last M5 capture, and their last repository changes are still Week 2-era. Existing 61 tests/static checks pass. No dedicated Week 3 core/packaging handoff, `.deb` binary or corresponding build provenance was found in the reviewed repository/artifact paths. This is a missing handoff/evidence finding, not proof that every existing implementation fails Week 3 requirements.

## Remaining work

1. Append current revision/results and new evidence to the primary QA inventory, QA summary and evidence index. Keep historical records intact; update M1/M4 items from waiting-for-implementation to available/verified, and retain actual review/owner decisions separately.
2. Review/record the M1/M4 integration handoff, including the additive Event search contract and shared readiness helper. Technical PASS is recorded; no human signature or group decision is inferred from merged code.
3. Obtain or close M2/M3 Week 3 hardening/packaging coverage and blockers: additional no-serial/parser/session/reconnect cases, restart/network/disk/pending ACK coverage and package metadata/staging/build provenance. M5 incorporates owner evidence and reruns affected checks after subsequent changes.
4. M1 and the team decide the Windows gate on the integrated revision after remaining owner P0 items are assessed. The permissible label remains `WEEK 3 — PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`; this audit does not declare the group gate closed.

All **W3-U01–W3-U09** remain `NOT EXECUTED / DEFERRED_UBUNTU` after Week 4: clean `.deb` install/enrollment, physical BLOCK/event correlation, actual A/B Allow/Revoke/apply/ACK, actual session switching/UNKNOWN, fail-closed/outbox recovery, reboot, native notification/IPC, lifecycle/permissions and physical no-serial identity. Their execution is not a missing Windows task.
