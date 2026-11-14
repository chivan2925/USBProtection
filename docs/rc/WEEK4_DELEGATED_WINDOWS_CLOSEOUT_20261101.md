> **FINAL DECISION ADDENDUM — 2026-11-01:** The coordinator, acting as M2 and reporting delegation from M1/M3/M4/M5, accepted **Week 4 Windows/release-candidate preparation** on application baseline `d5e156afdad382dc19d3b0a7198bf681fd36763e`. Read the [authoritative final closeout](WEEK4_M1_M5_FINAL_DELEGATED_CLOSEOUT_20261101.md). All older `PENDING M2`/`NO-GO` entries below are historical snapshots, not the current coordinator decision. PR #40 code findings are owner-accepted at code/unit level, while GitHub thread UI remains open; Ubuntu E2E and historic credential remediation remain outstanding. Final docs merge SHA is pending.

# USBShield — Week 4 Windows preparation: delegated closeout assessment

**Review date:** 2026-11-01 (Asia/Ho_Chi_Minh)  
**Merged application baseline:** `d5e156afdad382dc19d3b0a7198bf681fd36763e` (PR #50).  
**Working-tree delta:** post-merge documentation updates plus the packaging assembly correction in `packaging/scripts/build-deb.sh`; **not yet a merged final SHA**.  
**Scope:** only Week 4 §9.1 Windows/static/build/documentation/release-candidate preparation; Ubuntu native gate §9.2 remains deferred.  
**Independent technical reviewer:** ChatGPT, not an individual M1–M5 member.  
**Delegated authority:** project coordinator stated on 2026-11-01 that **M1, M3, M4 and M5** authorized the coordinator to complete Week 4. This statement is recorded as a *coordinator attestation*, not independently authenticated personal signatures. **M2 was not included in that stated delegation.**

## Gate evidence reviewed

| Area | Concrete evidence | Technical disposition |
|---|---|---|
| M1 backend/API/React JAR | M5 S4: Spring tests **28/28 PASS**, packaged HTTP/browser proof and backend JAR candidate; on `d5e156a`, `admin-server/` Git tree remains `a5b1f2101c9816c6050cb5dcf58a2d59717facad`, identical to M5 S4 source. API docs clarified DTO validation **400** versus bootstrap auth guard **401**. | **ACCEPTED — unchanged Windows scope**. Source equivalence justifies reuse of S4 rather than inventing a fresh backend run. |
| M2 policy, session, USBGuard and notifications | ZIP contains **34 JUnit Surefire XML files**, totaling **179 tests/0 failures/0 errors/0 skips**; notifier path test **5/5** and PR #40 focused regression **9/9**. Current code re-fetches live rule IDs before each removal, validates complete numeric rule syntax and rejects malformed targets. | **ACCEPTED — independent technical Windows unit/mock review; M2-specific owner IPC acceptance still pending.** Real daemon, ACL and popup not exercised. |
| M3 Agent/JAR | Owner-reported both `mvn -B -f client-agent/pom.xml clean test` and `mvn -B -f client-agent/pom.xml -DskipTests package` **BUILD SUCCESS**. Independently inspected JAR **20,190,216 bytes**, SHA-256 `f2bf259e3b7ab2ed0a5a47681e2a1327cc9a2f94c826dd6402235f80c95690a6`. | **ACCEPTED — owner Windows build + independently inspected JUnit/artifact**. Raw Maven console not independently rerun. |
| M3 Windows static verification | Project coordinator ran `powershell -NoProfile -ExecutionPolicy Bypass -File packaging/tests/verify-week4-windows.ps1 -SkipStagingCheck`; all six sections concluded **M3 STATIC CONTRACT PASS / JAR STRUCTURE-CHECKSUM PASS** with final `STAGING: NOT VERIFIED`, `DEB/NATIVE: NOT VERIFIED` printed by the script. | **PASS — actual user-run Windows static verifier**. Do **not** call this invocation a full staging test. |
| M3 independent Debian structural check | Independently assembled the **nine** package source/JAR payload files: **9/9 byte-equal**, with explicit CRLF→LF normalization for shell scripts. `bash -n` succeeded on the package scripts. The corrected `build-deb.sh` assembled a Debian archive using the already-built owner JAR; `dpkg-deb --root-owner-group --build` returned 0. Directory `DEBIAN` mode **0755**, no inherited setgid dirs, control scripts LF, dependency `acl` and root/root archive ownership confirmed. Controlled test used a stand-in `mvnw` that checked the existing JAR; **it did not recompile Java**. Archive SHA-256 `b08909479dbbcd09e9e368e1e63bc5933e720ebbcffa83e7319ef4b24a0fc879` (local structural candidate, not a tagged deliverable). | **PASS — independent staging/deb structure** (different environment than the Windows script). **Ubuntu install/upgrade/runtime NOT EXECUTED**. |
| M4 frontend | M5 S4 browser suites **PASS**, 25 screenshots; current `frontend/` Git tree `85938291c72c25de0fc7716261440cfe136128e7` unchanged across S4→PR #50, and no new API wire contract introduced. | **ACCEPTED — Windows UI/browser scope using pinned source equivalence.** |
| M5 QA and release validation | M5 S4 **215 Agent→real Spring assertions / 64 scenarios**, 28 server tests, management/Events/packaged browser suites and artifact/source validator. Later PR #50 delta addressed by 179 owner JUnit tests, static verification and independent Debian structural inspection above. | **ACCEPTED — technical QA report may be updated, not recast as a freshly executed M5 S5 campaign.** |

## Security/disposition boundary

1. **PR #40 review:** GitHub still shows three **unresolved** inline discussions (review IDs ending `4431653759`, `4431653772`, `4431653784`), despite corrective code already merged in PR #44 and focused Windows tests PASS. Independent conclusion: **the reported defects are addressed at code/unit level**. Formal GitHub thread resolution and M2 acceptance remain separate; do not write `RESOLVED` in the GitHub sense unless a reviewer actually resolves them. The verified rule subset may reject otherwise-valid future USBGuard syntax conservatively.
2. **Notifier security:** `/run/user/<UID>/usbshield-notifier/notice.sock`, `acl`/`setfacl`, `SO_PEERCRED` and UID checks pass source/contract inspection, **not real Linux/GUI validation**. M2 must review the new Java IPC/peer contract and its deferred-native limitation; no M2 delegation was reported to this reviewer.
3. **Historic credentials:** possible exposure in Git history remains **OPEN / rotation and re-enrollment NOT VERIFIED**. Do not run this candidate against an exposed production endpoint or publish it as Internet-ready. Limiting testing to a disposable, isolated demonstration is a scope restriction, not a substitute for credential revocation before production.
4. **Windows versus Ubuntu:** `.deb` archive construction and staging equality are **not** clean install, `systemd`, USBGuard daemon enforcement, physical USB, normal-user security, desktop popup, user-switch or reboot acceptance. All those remain **DEFERRED** exactly per Week 4 plan.

## Delegated closeout ledger (no impersonation)

| Scope | Delegation reported by coordinator | Reviewer's technical recommendation | Human owner signature |
|---|---|---|---|
| M1 | Yes | **ACCEPT Windows backend; final group decision requires M2 disposition and merged doc SHA.** | Not separately produced |
| M2 | **No** | **ACCEPT Windows policy/notifier unit tests; request M2 acknowledgment of cross-owner IPC and three thread dispositions.** | **PENDING** |
| M3 | Yes | **ACCEPT Windows Agent/JAR/static and independent Linux structural assembly**; no Ubuntu native claim. | Not separately produced |
| M4 | Yes | **ACCEPT unchanged frontend S4 Windows regression evidence.** | Not separately produced |
| M5 | Yes | **ACCEPT QA S4 + post-merge addendum**, preserving historical evidence and failures. | Not separately produced |

**Independent review signature:** `ChatGPT / technical evidence review / 2026-11-01`. This is **not** a signature by M1, M2, M3, M4 or M5.

## Decision

**WINDOWS TECHNICAL PREPARATION: ACCEPTED FOR M1–M5 AT THE EVIDENCE LEVEL.**  
**FORMAL GROUP RELEASE-CANDIDATE SIGN-OFF: PENDING M2 CROSS-OWNER APPROVAL AND FINAL COMMIT IDENTIFICATION.**  
**UBUNTU E2E: DEFERRED UNTIL AFTER WEEK 4; FULL RELEASE: NOT APPROVED.**

The coordinator can finalize the group Week 4 §9.1 statement only after M2 confirms the merged IPC contract and records a disposition of the three PR #40 findings (or the authorized owner explicitly records a bounded waiver). Then record the **actual post-merge final SHA** and a dated, attributed M1 group decision. The user-reported delegation must not be represented as five independently obtained signatures. No additional Maven run is required solely because documentation changed; if the build script or project source changes again, review the new diff/provenance first.
