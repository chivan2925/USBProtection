> **FINAL DECISION ADDENDUM — 2026-11-01:** The coordinator, acting as M2 and reporting delegation from M1/M3/M4/M5, accepted **Week 4 Windows/release-candidate preparation** on application baseline `d5e156afdad382dc19d3b0a7198bf681fd36763e`. Read the [authoritative final closeout](WEEK4_M1_M5_FINAL_DELEGATED_CLOSEOUT_20261101.md). All older `PENDING M2`/`NO-GO` entries below are historical snapshots, not the current coordinator decision. PR #40 code findings are owner-accepted at code/unit level, while GitHub thread UI remains open; Ubuntu E2E and historic credential remediation remain outstanding. Final docs merge SHA is pending.

# Week 4 — Owner dispositions and candidate sign-off (UNSIGNED)

> **Delegated coordinator closeout addendum (2026-11-01):** The coordinator reports authorization from **M1, M3, M4 and M5**, *not M2*, to complete the Week 4 review. Independent technical recommendation for all M1–M5 Windows scopes is ACCEPTED on the tested evidence. Updated Windows M3 static/JAR verification PASS; independently staged **9/9** package payloads and built a structural Debian candidate. Original owner-signature fields below remain unfilled because this is a recorded delegated statement, not individually witnessed signatures. **Final group GO still awaits M2 acceptance and a merged final SHA.** Read [delegated closeout assessment](WEEK4_DELEGATED_WINDOWS_CLOSEOUT_20261101.md); do not use this addendum as full Ubuntu release approval.


**Source under review:** `main` `d5e156afdad382dc19d3b0a7198bf681fd36763e` (PR #50 merged)  
**Final documentation/source candidate SHA after this patch merges:** `PENDING`  
**Decision:** `NO-GO / AWAITING OWNER INPUTS`
**Independent audit:** [WEEK4_INDEPENDENT_TECHNICAL_REVIEW_20261101.md](../qa/WEEK4_INDEPENDENT_TECHNICAL_REVIEW_20261101.md) — 179 owner-JUnit cases PASS, IPC review threads OPEN, packaging verifier/installer fixes prepared; not an owner signature.  
**Ubuntu E2E:** `DEFERRED UNTIL AFTER WEEK 4`; **never** claim full release from Windows mocks.

| Owner | Deliverable already supported by prior evidence | Must supply now | Human sign-off |
|---|---|---|---|
| M1 | Frozen API v0.2, security review, 28-test backend and server JAR manifest | Review the proposed canonical `400` DTO vs `401` bootstrap guard documentation fix; verify final SHA + actual artifact locations/checksums; review M2/M3 dispositions; sign RC decision | PENDING |
| M2 | PR #37/#40/#44 merge, S3 Agent 174 tests and M5 technical ACK, Q&A | Review merged PR #50 Java and owner-reported Maven PASS; formally dispose three PR #40 review threads (PR #44 code/tests addressed them, UI review status separate); accept/reject IPC contract and native deferral | PENDING |
| M3 | PR #41, staging/Client guide and M5 technical ACK; `.deb NOT BUILT` | Review merged `acl`, per-UID paths, ownership/ACL/startup/legacy retirement; approve installer and disclose that *new* static/staging checks are not yet supplied; reconcile historical provenance; decide `.deb` route | PENDING |
| M4 | Admin Guide, original 96 frontend tests, browser and shared-file ACK | Confirm Git frontend tree unchanged on PR #50 and API wire unchanged; targeted browser rerun only if final source modifies frontend/server/contract | PENDING |
| M5 | S4 independent 28 server tests, 215 real Agent→Spring HTTP assertions, 25 screenshots, validator PASS | Independently review PR #50 source diff and owner Maven output if available; approve new report section only after source/artifact identities and M2/M3 dispositions are recorded | PENDING |
| Environment administrator | Historic secret exposure recorded | Confirm **actual** revoke/rotate and affected Agent re-enrollment in the real deployment; provide redacted attestation, or mark OPEN and restrict to isolated demo | PENDING |

## M2 PR #40 disposition proposal (for owner review)

- F1 HIGH — ID renumbering after removal: M2 Part 3 candidate re-resolves IDs before each delete/rollback; M5 S3 recorded **9/9** regression suite PASS. Keep `NATIVE DEFERRED` and `EXTERNAL REVIEW NOT FORMALLY CLOSED` until reviewed.
- F2 HIGH — unknown tokens in foreign Allow rules: verified-subset syntax parser rejects unrecognized suffix/conditions; regression PASS. Unsupported valid upstream syntax remains a conservative reconciliation failure.
- F3 MEDIUM — malformed numeric header: parser fails instead of silently dropping `permit` or `deny`; regression PASS. Native USBGuard policy evidence pending.

## Credential attestation — record status without secrets

- Environment/account owner: `PENDING`
- Was the historical token live? `UNKNOWN`
- Endpoint token revoked/rotated: `NOT VERIFIED`
- Enrollment/bootstrap token rotated if affected: `NOT VERIFIED`
- Affected Agents re-enrolled by authorized admin: `NOT VERIFIED`
- Date / sanitized evidence reference: `PENDING`

Do not paste secrets, personal device hashes, full `endpoint.json`, H2 files or unredacted debug logs in this record.

## Required cross-owner disposition entries (not signatures)

| Owner / topic | Proposed technical assessment | Actual owner disposition / date / evidence |
|---|---|---|
| M2 / Java IPC security | Per-UID route and two-sided peer checks are merged; Windows Maven owner-reported PASS; native proof deferred | **PENDING — M2 must sign and cite test SHA** |
| M2 / PR #40 review threads | Three code-level fixes in PR #44 with S3 independent unit evidence; GitHub discussion resolution not established | **PENDING — mark each thread addressed/accepted/deferred explicitly** |
| M3 / Debian ACL and autostart | `acl` and launcher/postinst are merged; `setfacl`, legacy retirement, full staging verification not supplied on PR #50 | **PENDING — M3 must approve/record static result or accept an explicit exception** |
| M4 / frontend | Same `frontend/` Git tree as M5 S4 tested source | **PENDING — M4 acknowledgement** |
| M5 / release report | S4 independent results preserved; new PR #50 only Agent/packaging/docs, two Maven owner-reported passes | **PENDING — M5 verification and acceptance** |
| M1 / final acceptance | No group signing can be inferred from merged PR or a build log | **PENDING — GO/NO-GO with signer and final SHA** |

## Group decision after verification

- Merged IPC source SHA: `d5e156afdad382dc19d3b0a7198bf681fd36763e` (PR #50)
- Test SHA / final documentation+source SHA: `PENDING`
- Server JAR checksum + binary location: `PENDING`
- Client JAR checksum + binary location: `PENDING`
- `.deb`: **INDEPENDENT STRUCTURAL CANDIDATE BUILT** (Debian `dpkg-deb` inspection only), 17,929,524 bytes / SHA-256 `0a8399160fe72a63f363f335577ef884e5a4ab28f13eccedcc7f4537dcb2993e`; source Java JAR from owner ZIP; **no Ubuntu install or M3 owner acceptance**. The original M3 historical `.deb NOT BUILT` record remains factually valid for its dated attempt.
- Agent Windows `mvn -B -f client-agent/pom.xml clean test`: `BUILD SUCCESS` **owner-reported**; test totals, date/command output, tested commit checkout and raw logs not attached
- Agent Windows `mvn -B -f client-agent/pom.xml -DskipTests package`: `BUILD SUCCESS` **owner-reported**, resulting JAR SHA absent
- Packaging/static/staging on PR #50 source: `NOT VERIFIED`; candidate packaging static fixes require Windows verification, and new Debian package build/owner approval remain separate
- M1 server/M4 UI/M5 S4 Windows: existing independent PASS; `admin-server/` and `frontend/` Git trees unchanged since S4 (no fresh S5 browser claim)
- Known issue / residual exceptions and P0 disposition: `PENDING`
- M1 group GO/NO-GO + explicit date and signer: `PENDING`

Only after owner signatures and acceptance should M1 record `WEEK 4 — WINDOWS/RELEASE-CANDIDATE PREPARATION PASS; UBUNTU E2E DEFERRED` with a verified SHA. Do not create a final release tag.
