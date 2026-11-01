> **FINAL DECISION ADDENDUM — 2026-11-01:** The coordinator, acting as M2 and reporting delegation from M1/M3/M4/M5, accepted **Week 4 Windows/release-candidate preparation** on application baseline `d5e156afdad382dc19d3b0a7198bf681fd36763e`. Read the [authoritative final closeout](WEEK4_M1_M5_FINAL_DELEGATED_CLOSEOUT_20261101.md). All older `PENDING M2`/`NO-GO` entries below are historical snapshots, not the current coordinator decision. PR #40 code findings are owner-accepted at code/unit level, while GitHub thread UI remains open; Ubuntu E2E and historic credential remediation remain outstanding. Final docs merge SHA is pending.

# USBShield Week 4 — Post-merge cross-owner review / decision sheet

> **Status addendum (2026-11-01):** The initial `DRAFT / OWNER SIGNATURES MISSING` status is retained below as the earlier position. Independent inspection now confirms 179/179 owner JUnit results, a successful *user-run* M3 Windows static/JAR verifier with staging skipped, and **separate 9/9 independently assembled staging files + Debian structural build**. M1/M3/M4/M5 delegation was **reported by the project coordinator**, but no M2 delegation was reported. See [current closeout ledger](WEEK4_DELEGATED_WINDOWS_CLOSEOUT_20261101.md) and [M2 review action](WEEK4_M2_REVIEW_ACTION.md). The current decision is **WINDOWS TECHNICAL REVIEW ACCEPTED / FORMAL GROUP SIGN-OFF PENDING M2 AND FINAL MERGE SHA**; Ubuntu E2E remains DEFERRED.


**Prepared:** 2026-11-01 · **review source:** `main` `d5e156afdad382dc19d3b0a7198bf681fd36763e` (PR #50 merged).  
**Scope:** documentation alignment and dispositions for W4-M1-06, W4-M2-07, W4-M3-02/04, W4-M5-06.  
**Status:** **DRAFT / OWNER SIGNATURES MISSING**.

**Independent follow-up (reviewer only):** [audit and partial technical sign-off](../qa/WEEK4_INDEPENDENT_TECHNICAL_REVIEW_20261101.md), including uploaded 179/179 JUnit report inspection, current static-verifier corrections and independent `.deb` structural inspection. No member signature or Ubuntu acceptance is inferred. This document is a prepared assessment, not a human owner sign-off or newly executed regression.

## A. What is demonstrated

| Component | Evidence and version | Interpretation |
|---|---|---|
| M1 Admin Server | S4: 28/28 backend tests; production HTTP smoke and separate server JAR provenance, recorded by M1/M5 | `admin-server/` tree unchanged through `d5e156afdad3`; retain S4 technical PASS, no new S5 backend run implied |
| M4/M5 UI + events | S4: management, Event History and packaged-browser suites PASS, 25 screenshots, 5 assets byte-equal; M4 ACK on shared UI | `frontend/` tree unchanged through `d5e156afdad3`; do not invent S5 screenshots |
| M1/M3 Agent–Server integration | S4: production Agent HTTP→real Spring 215 assertions / 64 scenarios; S3 Agent 174/174 JUnit independently passed before new IPC patch | Historical integration evidence remains scoped to S3/S4, not a new native notification result |
| M2/M3 notifier IPC | PR #50 merged: private `/run/user/<UID>/usbshield-notifier/notice.sock`, `acl` named-user entries, Linux `SO_PEERCRED`, cross-platform path test | Updated two Maven commands `BUILD SUCCESS` per owner report; raw logs/JAR SHA and native kernel GUI tests not attached |
| M3 `.deb` / packaging | Prior Windows staging/equality proof on pre-PR#50 source; new Debian dependency `acl` and changed `postinst`/launcher | New static/staging proof **not supplied**; `.deb NOT BUILT`, Ubuntu native E2E **DEFERRED** |

## B. Current architecture and inherited limits

The Agent runs as `usbshield`; the desktop helper belongs to the active GUI UID. Only the helper can create its runtime child directory under logind-managed `/run/user/<UID>` and grant named ACLs: `u:usbshield:--x` on runtime/private directories, `u:usbshield:rw-` on the socket. The Agent checks expected UID-owned inode and helper peer credentials; the helper verifies the sender `usbshield` and exact approved text/target. No world-writable IPC namespace is required. Missing helper or permission should not weaken USBGuard enforcement/event transport. **None of this proves successful runtime IPC, popup display, or retained security after upgrade until Ubuntu tests run.**

Known limitations requiring owner disposition: GUI user can suppress their own informational popup; `setfacl` affects effective runtime ACL mask and may need desktop compatibility validation; `SO_PEERCRED` runtime enforcement was not tested on Windows; stale socket is intentionally not unlinked automatically; existing GUI helper after upgrade may require logout/login. Older root-owned `/run/usbshield-notifier` is retired only through a cautious cleanup; do not forcibly wipe nonempty runtime directories. M2 policy review PR #40 has three code fixes plus old review-thread resolution still requiring explicit human disposition.

## C. Cross-owner sign-off checklist (signature fields intentionally blank)

| Person | Review required before release-candidate decision | Owner decision / evidence / date |
|---|---|---|
| M2 | Review `NotifierSocketPaths`, `DesktopNotificationClient`, `UnixSocketNotificationPublisher`, peer/UID checks and new JUnit; explicitly resolve or accept/defer 3 PR #40 threads | **PENDING** |
| M3 | Review `DEBIAN/control` (`acl`), `postinst`, launcher, runtime privilege model; sign packaging static exception if test not performed, and acknowledge old manifest hashes are historical | **PENDING** |
| M4 | Acknowledge identical frontend Git tree / no wire-contract changes; no forced full browser rerun without affected source | **PENDING** |
| M5 | Accept owner-reported Maven evidence at precise SHA or request raw proof; review current documentation, identify any affected regression requirement and amend W4-M5-06 decision | **PENDING** |
| M1 | Review API 400/401 documentation clarification, M2/M3/M4/M5 dispositions, final docs SHA + deliverable/JAR provenance, then choose and sign `GO`/`NO-GO` | **PENDING** |
| Environment administrator | Rotate/revoke exposed historic credentials and re-enroll Agents if affected (or document isolated demo-only exception); provide redacted attestation | **PENDING / W4-M1-07 P1 OPEN** |

**Minimum record from each person:** owner identity; `ACCEPTED`, `CHANGES_REQUESTED` or explicitly documented `DEFERRED`; exact reviewed SHA and relevant file paths; environment/test scope; evidence identifiers; date. Do not infer assent from merged PR alone. No unsigned draft should be labeled `APPROVED`.

## D. Final gate to be completed by M1 after the above

- [ ] M2/M3 IPC and PR review dispositions recorded, with owner identity and date.
- [ ] M3 accepts or supplies post-PR#50 static/packaging verification, with precise limitation.
- [ ] M4 UI-equivalence ACK and M5 candidate acceptance recorded.
- [ ] All relevant P0 findings resolved or accepted as explicitly scoped exceptions.
- [ ] M1 records final tested source SHA **after** doc PR, Agent/server JAR names, SHA-256 and accessible artifact locations (do not mix separately built JAR checksums).
- [ ] W4-M1-07 exposure mitigated by admin, or isolated demo-only restriction documented without claiming rotation happened.
- [ ] Decision signed by M1 with date: `WEEK 4 — WINDOWS/RELEASE-CANDIDATE PREPARATION PASS; UBUNTU E2E DEFERRED` **only if** gate 9.1 satisfied; else `NO-GO / PENDING`.

**Current conservative decision:** `NO-GO — GROUP OWNER SIGN-OFF PENDING`; full Ubuntu release **NOT EXECUTED**. Do not create a `final release` tag on the basis of this document.

## E. How to publish the reviewed result

After owners decide, M5 updates [release validation](../qa/WEEK4_RELEASE_VALIDATION.md) with their actual acceptance or changes requested. M1 signs [owner template](WEEK4_OWNER_SIGNOFF_TEMPLATE.md), records final SHA and provenance, and selects an RC/pre-release only for the explicitly verified Windows preparation scope. Historical S0–S4 logs and unrelated Week 3 acceptance remain unchanged.
