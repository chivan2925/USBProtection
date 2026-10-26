# M5 QA follow-up — M3 update 2026-10-27

Reviewed revision: `2885cf9cfc30b841fb0e3d44d3fd281ea16350ba` on branch `huy`. M3 implementation commit under review: `8beb954edbdcdfb9071c52a91d4fb82e1e8522a7`. [Inventory](WEEK3_TEST_INVENTORY.md), [QA summary](WEEK3_QA_SUMMARY.md), [execution evidence](week3-evidence/2026-10-27/m3-followup/README.md).

M3 fixed the staging-verifier defect recorded as M5-W3-06 and added Windows mock coverage for the outbox/enrollment gaps. M5 reran the Agent package and packaging checks. Group acceptance remains OPEN; Ubuntu cases remain `NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4`.

## Verified M3 changes

| Area | Result |
|---|---|
| Staging verifier | **M5-W3-06 RESOLVED.** `test-staging-verifier.ps1` passes all three cases: absent staging rejects, incomplete staging rejects, and complete staging passes. `verify-week3-windows.ps1` now throws when requested staging is absent or missing required files. |
| Outbox | FIFO payload order, false return, client exception, malformed JSON cleanup, restart drain, and disk-write failure are covered in `OutboxSpoolServiceTest` (7 tests). The Agent suite passes **122/122**. |
| Enrollment | `EndpointManagerTest` adds a persistence-failure case: enrollment is rejected and registration is not retained when credentials cannot be written. |
| Package lifecycle | `postinst` now makes fail-closed USBGuard configuration explicit and reports a service restart problem; `prerm` distinguishes remove from upgrade. This is source/static verification only. |
| Packaging checks | Static verifier with the freshly built Agent JAR and four shell parsers pass. No `.deb`, staging tree, install, service, or reboot was executed. |
| Diff hygiene | `git diff --check f80a936..HEAD` reports trailing whitespace only at `packaging/tests/test-staging-verifier.ps1:40`. This is a non-functional cleanup item and does not affect the executed verifier result. |

## Open QA finding

**M5-W3-07 — OPEN, M3.** `EndpointManager.updateAppliedPolicyVersion()` and `clearPendingAck()` now return whether persistence succeeded, but `PolicySyncService` and `HeartbeatCoordinator` ignore those results. In particular, after USBGuard applies a policy, `PolicySyncService` calls `updateAppliedPolicyVersion(newVersion)` and can still send the server ACK when the save returned `false`. The pending-ACK durability claim therefore is not established on this failure path.

This is a source-review finding, not a newly reproduced runtime failure. Closure needs a focused test that forces the registration save to fail during policy synchronization/retry, and a change that prevents an ACK or clear-pending transition from claiming durable state when the save fails.

## Status of previous M3 review items

| Item | Status |
|---|---|
| M5-W3-06 staging verifier | **RESOLVED and rerun PASS** |
| FIFO / network exception / corrupt event / restart / disk write | **Covered by new unit tests and rerun PASS** |
| Enrollment disk-write failure | **Covered by new unit test and rerun PASS** |
| `postinst` restart reporting and `prerm` upgrade branch | **Source/static PASS; Ubuntu runtime remains deferred** |
| `.deb` and real staging provenance | **NOT BUILT / NOT VERIFIED** in this workspace |
| M5-W3-07 durable policy/ACK failure path | **OPEN** |

The allowed group decision remains `WEEK 3 — PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED` only after M5-W3-07, owner reviews, and the group decision are resolved. This follow-up does not claim `FULL PASS`.
