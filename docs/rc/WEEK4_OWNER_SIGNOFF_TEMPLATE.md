# Week 4 — Owner dispositions and candidate sign-off (UNSIGNED)

**Baseline patched:** `63e4dc61b15b86859dd4ca2ca4be60021a293091`  
**Candidate SHA after owner commit/merge:** `PENDING`  
**Decision:** `NO-GO / AWAITING OWNER INPUTS`  
**Ubuntu E2E:** `DEFERRED UNTIL AFTER WEEK 4`; **never** claim full release from Windows mocks.

| Owner | Deliverable already supported by prior evidence | Must supply now | Human sign-off |
|---|---|---|---|
| M1 | Frozen API v0.2, security review, 28-test backend and server JAR manifest | Reconcile `400` vs `401` documentation, verify candidate SHA + artifact distribution/checksum, review M2/M3 decisions, owner RC decision | PENDING |
| M2 | PR #37/#40/#44 merge, S3 Agent 174 tests and M5 technical ACK, Q&A | Review new IPC Java + tests; PR #40 three findings addressed in PR #44 (review discussions still separate); contract/native deferral, accept/reject design | PENDING |
| M3 | PR #41, staging/Client guide and M5 technical ACK; `.deb NOT BUILT` | Review `acl`, per-user paths, ownership/ACL/startup, installer and Windows static checks; explain old provenance discrepancy; decide Linux `.deb` route | PENDING |
| M4 | Admin Guide, original 96 frontend tests, browser and shared-file ACK | Confirm updated source has no frontend/API breaking delta or request targeted tests | PENDING |
| M5 | S4 independent 28 server tests, 215 real Agent→Spring HTTP assertions, 25 screenshots, validator PASS | Verify patched SHA after M2/M3 tests; append release report with accurate source/artifact/evidence IDs | PENDING |
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

## Group decision after verification

- Test SHA / final source SHA: `PENDING`
- Server JAR checksum + binary location: `PENDING`
- Client JAR checksum + binary location: `PENDING`
- `.deb`: `NOT BUILT` until a real build succeeds
- Tests (Windows Maven / static / browser): `PENDING FOR PATCHED SOURCE`
- Known issue / residual exceptions and P0 disposition: `PENDING`
- M1 group GO/NO-GO + explicit date and signer: `PENDING`

Only after owner signatures and acceptance should M1 record `WEEK 4 — WINDOWS/RELEASE-CANDIDATE PREPARATION PASS; UBUNTU E2E DEFERRED` with a verified SHA. Do not create a final release tag.
