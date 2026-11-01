> **M2 OWNER DECISION — 2026-11-01:** The user explicitly identifies themself as M2 and accepts `ACCEPTED FOR WEEK 4 WINDOWS SCOPE`, the notifier IPC contract, and the three corrections below as **RESOLVED AT CODE/UNIT LEVEL** on `d5e156afdad382dc19d3b0a7198bf681fd36763e`. The three GitHub review conversations are **not yet marked resolved in GitHub**; API resolution was denied to the integration. Ubuntu ACL/`SO_PEERCRED`/native popup remain deferred. Refer to [final closeout](WEEK4_M1_M5_FINAL_DELEGATED_CLOSEOUT_20261101.md).

# Week 4 M2 — Three PR #40 findings and notifier IPC review

**Evidence baseline:** `d5e156afdad382dc19d3b0a7198bf681fd36763e`, 2026-11-01.  
**Status:** **CODE-LEVEL REVIEW ACCEPTED BY INDEPENDENT REVIEWER; FORMAL M2/PR-REVIEW THREAD ACK PENDING**. This is a handoff, not a statement signed by M2.

| GitHub discussion | Original finding | Current corrective behavior verified at source level | Regression evidence | Reviewer's disposition |
|---|---|---|---|---|
| [#4431653759](https://github.com/chivan2925/USBProtection/pull/40#discussion_r4431653759) | HIGH: stale numeric rule IDs on removal | `UsbGuardPolicySynchronizer.reconcile()` gets a fresh `list-rules` snapshot, locates the managed key and then removes the current numeric ID. Rollback does the same. | `UsbGuardWeek4CopilotReviewRegressionTest` included in 179/179 JUnit PASS | **FIX SUPPORTED — GitHub thread OPEN** |
| [#4431653772](https://github.com/chivan2925/USBProtection/pull/40#discussion_r4431653772) | HIGH: parser accepted unrecognized trailing rule tokens | `UsbGuardRuleSyntax.validateList(...)` validates numeric rule definitions before mutations and rejects unsupported grammar instead of silently accepting a potentially unsafe foreign Allow. | Same 9/9 regression suite | **FIX SUPPORTED — GitHub thread OPEN** |
| [#4431653784](https://github.com/chivan2925/USBProtection/pull/40#discussion_r4431653784) | MEDIUM: `permit` / `deny` headers omitted from count | Syntax validation counts/checks numeric `id:` rule headers independently from target and throws on unknown target. | Same 9/9 regression suite | **FIX SUPPORTED — GitHub thread OPEN** |

**M2 must review:** `NotifierSocketPaths`, `DesktopNotificationClient`, `DesktopNotificationClientMain`, `UnixSocketNotificationPublisher` and `Week4NotifierSocketContractTest`, together with `packaging/agent-deb/DEBIAN/control`, `packaging/desktop/usbshield-notifier-launcher.sh` and `postinst` as the M3 interface. Verify that cross-owner runtime contract and failure-isolation expectations match. The actual Linux `SO_PEERCRED`/ACL behavior and native popup tests are **DEFERRED**, not covered by Windows JUnit.

**Minimal owner acknowledgment to record (no additional commands):** M2 states `ACCEPTED FOR WEEK 4 WINDOWS SCOPE` or `CHANGES_REQUESTED`, explicitly accepts/rejects the IPC contract and identifies any remaining PR #40 issue as OPEN/ACCEPTED RISK/RESOLVED, with reference to the merged SHA and date. Closing GitHub threads is a separate action; reviewer may mark them resolved after confirming code evidence. No assistant-generated text constitutes that owner's acceptance.
