# USBShield Week 4 — Independent technical review and sign-off boundary

> **Independent update after owner verification (2026-11-01):** The earlier `PATCHED PACKAGING STATIC VERIFICATION PENDING` line below is the historical state before the user's actual `verify-week4-windows.ps1 -SkipStagingCheck` run. Its reported result is **M3 STATIC CONTRACT PASS / JAR INSPECTION PASS; staging intentionally skipped**. Separately, the reviewer inspected **9/9 staging payloads** and assembled a Debian structural archive after correcting inherited `setgid` handling and LF normalization in `build-deb.sh`. See [post-verification closeout](../rc/WEEK4_DELEGATED_WINDOWS_CLOSEOUT_20261101.md). **Current reviewer disposition: Windows technical preparation ACCEPTED; formal group sign-off remains pending M2/new SHA.** Historical method limits and Ubuntu deferral below remain valid.


**Reviewer:** ChatGPT (independent, source/evidence audit; **not** M1, M2, M3, M4 or M5)  
**Reviewed at:** 2026-11-01 (Asia/Ho_Chi_Minh)  
**Canonical source:** `main` `d5e156afdad382dc19d3b0a7198bf681fd36763e` (PR #50).  
**Additional input:** owner-supplied `USBProtection(20261101-151827).zip`, containing unmerged post-merge documentation updates and local Maven build outputs.  
**Result:** **INDEPENDENT TECHNICAL REVIEW — PARTIAL ACCEPTANCE; GROUP SIGN-OFF NOT GRANTED**. This record cannot impersonate the member owners or attest production credential rotation.

## 1. Independently checked evidence

| Area | Observation | Disposition |
|---|---|---|
| Agent tests M2/M3 | 34 Maven Surefire XML reports extracted from owner ZIP: **179 tests, 0 failures, 0 errors, 0 skipped**. `Week4NotifierSocketContractTest` 5/5; `UsbGuardWeek4CopilotReviewRegressionTest` 9/9. Source/test classes relate to merged PR #50. | **PASS (uploaded Windows JUnit evidence)**; independently verified counts, not a new Maven execution by reviewer. |
| Agent JAR | Owner ZIP `client-agent/target/usbshield-agent.jar`, **20,190,216 bytes**, SHA-256 **`f2bf259e3b7ab2ed0a5a47681e2a1327cc9a2f94c826dd6402235f80c95690a6`**. | **Artifact present with hash**; provenance is owner-supplied local build, not an independently reproducible Maven build. |
| Backend M1 | M5 S4 independent 28/28 server tests, packaged HTTP/browser evidence; current Git subtree `admin-server/` **`a5b1f2101c9816c6050cb5dcf58a2d59717facad`**, equal to S4 source. | **PASS (existing S4 Windows evidence reused, no new run claimed)**. |
| Frontend M4 / Event History M5 | M5 S4 three browser suites, 25 screenshots, and 215 Agent→real Spring HTTP assertions across 64 scenarios; current `frontend/` subtree **`85938291c72c25de0fc7716261440cfe136128e7`**, equal to S4 source. | **PASS (existing S4 Windows evidence reused)**. |
| M2 PR #40 | Three review threads verified open on GitHub 2026-11-01; PR #44 addresses code logic with live-ID re-list, strict foreign-token validation, malformed numeric-header rejection; 9/9 test suite in supplied Maven report PASS. | **CODE-LEVEL FIXES SUPPORTED; GITHUB THREADS REMAIN UNRESOLVED.** The owner/reviewer must record dispositions; do not silently treat merge as review approval. |
| M2/M3 native notifier | Current code uses `/run/user/<UID>/usbshield-notifier/notice.sock`, `acl`, `SO_PEERCRED`, service user `usbshield`. | **STATIC CONTRACT REVIEWED; native ACL/session/GNOME/USBGuard path NOT EXECUTED**. |
| Debian shell sources | Bash syntax PASS on merged LF source for `postinst`, `prerm`, `build-deb.sh`, launcher and notifier inspection script. Owner's Windows ZIP had CRLF copies of `prerm` and `build-deb.sh`, failing direct `bash -n` until LF-normalized. | **PORTABILITY RISK FOUND; `.gitattributes` fix prepared**, recheck on new Windows checkout. |
| M3 package verifiers | `verify-week3-windows.ps1` and `verify-week4-windows.ps1` asserted removed `1777` IPC and would fail current postinst. Both fixed: **57/57 and 55/55 source-content assertions PASS** using independent Python checks of their declared static constraints. | **SOURCE ASSERTIONS PASS; actual Windows PowerShell execution pending**. |
| Installer hardening | `postinst` previously logged only WARNING and exited 0 if Agent failed to restart; build script did not force root ownership in `.deb`. | **DEFECT FOUND; candidate now errors on missing JAR/service restart, forces `--root-owner-group`, and clears inherited setgid on control staging.** Bash syntax PASS; runtime maintainer-script behavior NOT EXECUTED. |
| Independent `.deb` construction | Debian `dpkg-deb --root-owner-group --build` returned 0. Candidate **17,929,524 bytes**, SHA-256 **`0a8399160fe72a63f363f335577ef884e5a4ab28f13eccedcc7f4537dcb2993e`**. Nine packaging files embedded, all data-archive entries root/root, `acl` dependency present, control scripts in LF. Staged embedded JAR is the owner-provided **`f2bf259e3b7ab2ed0a5a47681e2a1327cc9a2f94c826dd6402235f80c95690a6`** artifact. | **DEB STRUCTURAL BUILD/INSPECTION PASS on Debian container**, not project Maven builder execution, not Ubuntu install/upgrade/runtime acceptance. Artifact is out-of-repo and not a release tag. |
| Ubuntu / physical USB | No native USBGuard daemon, real GUI notification, loginctl/switch-user, systemd/reboot or `.deb` lifecycle evidence. | **DEFERRED BY WEEK 4 PLAN; not `FULL RELEASE PASS`**. |

## 2. Reviewer-owned disposition (not the member signatures)

- **M1:** Backend Windows technical scope **ACCEPTED** via M5 S4 evidence. Group W4-M1-06 **PENDING** formal M1 approval, artifact distribution proof and final SHA.
- **M2:** Agent policy/attribution/notifier tests **ACCEPTED FOR WINDOWS UNIT/MOCK SCOPE**. Native notifier deferred; PR #40 discussions remain open. New source contract needs M2 sign-off.
- **M3:** Agent JUnit/JAR and prior static/staging evidence **ACCEPTED IN THEIR TESTED SCOPES**. New packaging defects fixed in candidate but **not yet verified** with final Windows static verifier/owner approval, and Linux installation is deferred.
- **M4:** Frontend technical scope **ACCEPTED**, unchanged source tree. Human M4 ACK for candidate remains pending.
- **M5:** Independent S4 browser/HTTP/QA evidence **ACCEPTED**, unchanged FE/server; final M5 release report must incorporate the final candidate SHA/updated packaging checks. Human M5 ACK pending.

These statements are the reviewer's **technical dispositions**, not member signatures, not code-owner approval, not evidence of credential rotation and not a full release.

## 3. Exact remaining Week 4 Windows gate

1. Apply the reviewed patch (`postinst`, `build-deb.sh`, `.gitattributes`, two packaging verifiers). **M2/M3 approve ownership and installer behavior.**
2. After checkout, rerun one current packaging static/Agent JAR inspection on Windows (see below). Do not claim staging verified when deliberately skipped. The independently built `.deb` is inspectable but **must not** be claimed installed or owner-accepted.
3. M2/PR reviewer explicitly dispose all three PR #40 threads; a verified code fix does not itself close a GitHub review thread.
4. M5 records final source SHA, source differences and evidence; M4 acknowledges unchanged frontend; M1 chooses and signs final group GO/NO-GO. Credential rotation remains `OPEN` unless administrator confirms with redacted evidence. Isolated demo-only exception must be documented if applicable.
5. Keep post-Week-4 native `.deb` install, USBGuard daemon, physical USB, loginctl/GUI and reboot as `NOT EXECUTED / DEFERRED`.

**Reviewer conclusion:** `WINDOWS FUNCTIONAL/UNIT AND HISTORICAL INTEGRATION EVIDENCE ACCEPTED; PATCHED PACKAGING STATIC VERIFICATION PENDING; GROUP OWNER SIGN-OFF PENDING; UBUNTU E2E DEFERRED`.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File packaging/tests/verify-week4-windows.ps1 -SkipStagingCheck
```

This command examines static source contracts and the executable Agent JAR. It intentionally **does not** check staging or Linux native behavior. Send the full exit/output to reviewer before changing the final technical status. Do not run `.deb` install on an actual protected workstation as a shortcut to Week 4 acceptance.

## 4. Source hygiene for the eventual PR

The uploaded workspace includes a historical `week4-apply-doc-sync.py` at its root and untracked local runtime/test output. **Do not stage** the old one-shot script, `client-agent/target`, `client-agent/data`, credentials, generated databases or any raw user-session records. This reviewer patch adds no client/server Java or frontend code. Check the final diff before merging; this review is pinned to the stated parent source SHA, not yet to the future docs/packaging merge SHA.
