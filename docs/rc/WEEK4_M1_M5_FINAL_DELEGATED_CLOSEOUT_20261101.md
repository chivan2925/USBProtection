# USBShield — Week 4 M1–M5 final delegated closeout (Windows scope)

**Decision date:** 2026-11-01 (Asia/Ho_Chi_Minh)  
**Reviewed application baseline:** `d5e156afdad382dc19d3b0a7198bf681fd36763e` (`main`, PR #50 merged).  
**Final documentation PR merge SHA:** **PENDING until this closeout PR is merged**; record actual SHA afterward, do not infer it from the baseline.  
**Decision:** **WEEK 4 — WINDOWS/RELEASE-CANDIDATE PREPARATION PASS; UBUNTU E2E DEFERRED.**  
**Authority:** The coordinator reports delegation from M1, M3, M4 and M5; in this conversation the coordinator explicitly identified themself as **M2** and accepted the new notifier IPC contract, three PR #40 code corrections, and the Windows-only/Ubuntu-deferred scope. This is a **coordinator's delegated project decision**, not five independently authenticated signatures.

## Acceptance basis and scope

| Owner | Scope accepted | Evidence and limits |
|---|---|---|
| M1 | Backend/API freeze, review, server JAR, same-origin integration: **PASS for Windows preparation** | M5 S4 backend **28/28**, packaged-JAR HTTP/browser; `admin-server/` source tree unchanged through `d5e156a`. Historic credential rotation/re-enrollment **NOT VERIFIED / OPEN**; production rollout blocked pending remediation. |
| M2 | USBGuard policy/session/watch mock, notification IPC source/contract: **ACCEPTED FOR WEEK 4 WINDOWS SCOPE** | Uploaded ZIP Surefire 34 XML files, **179/179 tests PASS**, includes **9/9** PR #40 regression and **5/5** notifier path tests; user personally confirmed M2 approval. Linux `SO_PEERCRED`, POSIX ACL and actual GUI popup **NOT EXECUTED**. |
| M3 | Agent lifecycle, Windows static/JAR, Debian staging and structural assembly: **PASS for Week 4 preparation** | User ran Week 4 verifier with `-SkipStagingCheck`, result `M3 STATIC CONTRACT: PASS`, built JAR **20,190,216 bytes**, SHA-256 `f2bf259e3b7ab2ed0a5a47681e2a1327cc9a2f94c826dd6402235f80c95690a6`. Separate independent **9/9 byte-equal** staging and `dpkg-deb` structural build PASS; not a Windows full-staging execution or Ubuntu install. |
| M4 | Frontend production UI and browser: **PASS for Windows preparation** | M5 S4 management/Events/packaged-browser suites PASS; frontend tree unchanged by PR #50. No fresh S5 browser execution claimed. |
| M5 | Test matrix, evidence, integrated browser/API, release assessment: **PASS for Windows preparation** | S4 **215 Agent→Spring assertions / 64 scenarios**, 25 screenshots, validator PASS; changed Agent/packaging source separately reviewed after PR #50. Historical S4 evidence remains pinned to its original SHA. |

## PR #40: M2 disposition, accepted by owner

M2 has accepted code-level remediation in the current main baseline for all three findings (regression evidence **9/9 PASS**):

1. [#4431653759](https://github.com/chivan2925/USBProtection/pull/40#discussion_r4431653759) HIGH: obtain a fresh USBGuard `list-rules` snapshot and resolve numeric IDs before each managed-rule removal/rollback — **RESOLVED AT CODE/UNIT LEVEL**.
2. [#4431653772](https://github.com/chivan2925/USBProtection/pull/40#discussion_r4431653772) HIGH: reject unrecognized trailing rule tokens using bounded strict syntax validation — **RESOLVED AT CODE/UNIT LEVEL**.
3. [#4431653784](https://github.com/chivan2925/USBProtection/pull/40#discussion_r4431653784) MEDIUM: reject malformed numeric rule headers including unknown `permit`/`deny` targets — **RESOLVED AT CODE/UNIT LEVEL**.

**GitHub UI caveat:** The three original PR #40 review threads remained **UNRESOLVED IN GITHUB** on review. An attempted API resolution on 2026-11-01 was refused with `Resource not accessible by integration`. Owner/coordinator should click **Resolve conversation** for each thread. Do not confuse the owner-accepted code-level resolution with GitHub thread-state. There is no new open P0 in the verified Windows test scope from those findings.

## Packaging provenance and exclusions

- Client JAR: `client-agent/target/usbshield-agent.jar` from owner-supplied ZIP; **20,190,216 bytes / SHA-256 `f2bf259e3b7ab2ed0a5a47681e2a1327cc9a2f94c826dd6402235f80c95690a6`**. User reported both Client Agent Maven commands `BUILD SUCCESS`; Surefire XML independently counted 179 cases.
- M1's historical Windows server JAR: SHA-256 `43269264cf7cd3af5c785a12b15507ee89ce3ce3c6b42ff8e92ceb601604240d`, linked to M1's artifact baseline `d78189b` and unchanged backend/frontend trees; no new binary is claimed for this closeout docs commit.
- Independent Debian structural build: local candidate SHA-256 `b08909479dbbcd09e9e368e1e63bc5933e720ebbcffa83e7319ef4b24a0fc879` (evidence described in delegated audit). It is **not** an Ubuntu install verification or tagged release; the historical M3 `.deb NOT BUILT` entry still describes its dated run accurately.
- M3 Windows verifier used `-SkipStagingCheck`, so **Windows staging was not verified by that invocation**. The independent 9/9 staging equality/build result is different, explicitly scoped evidence.
- No Ubuntu 24.04 USB physical enforcement, live USBGuard, user session, socket ACL/kernel peer, native popup, reboot, fresh `.deb` install, upgrade or systemd runtime was executed; **all are deferred to the later Ubuntu release gate**.
- Credential exposure in Git history: revoke/rotate and re-enrollment **OPEN / NOT VERIFIED**. **Only isolated synthetic Windows demonstration/preparation is approved** until administrator remediation; no Internet-facing or real deployment is approved. Runtime ACL and true security are **not** proven by Windows tests.

## Publication and final SHA

After manually applying this documentation-only package, commit, push and merge its PR. Record the **actual merged SHA** in the publication follow-up; the `d5e156a` value above is the *reviewed application baseline*, not the future merge SHA. This closeout is the coordinator's delegated Windows-preparation acceptance; it is not a full production release or native security sign-off. No additional Maven build is necessary if this PR touches documentation only.
