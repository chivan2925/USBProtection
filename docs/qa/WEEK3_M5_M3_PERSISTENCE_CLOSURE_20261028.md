# M5 QA closure — M3 policy persistence and ACK, 2026-10-28

Reviewed revision: `39561907ef92f97234f449140bc394ddfe4f30e8` on branch `huy`. M3 implementation commit under review: `0c9bb6d2b626abe1f22eed3559d89f19e503138b`, merged through `3956190`. [Inventory](WEEK3_TEST_INVENTORY.md), [QA summary](WEEK3_QA_SUMMARY.md), [execution evidence](week3-evidence/2026-10-28/m3-persistence-closure/README.md).

## M5-W3-07 — RESOLVED for Windows unit/mock scope

The previous finding required policy state to be durable before sending an ACK and required a failed pending-ACK clear to retain retry state. The M3 change closes both paths:

| Failure path | Verified behavior |
|---|---|
| Persisting an applied policy version fails | `EndpointManager` restores the prior in-memory policy and pending-ACK values; `PolicySyncService` returns `false` and does not call the server ACK. |
| Server ACK succeeds but clearing pending ACK fails | `EndpointManager` restores the pending-ACK value; `PolicySyncService` and `HeartbeatCoordinator` report the condition without claiming a durable clear, allowing a later retry. |

The fresh Agent package run passed **127/127** tests with no failures, errors, or skips, and built the executable JAR. It includes direct disk-write failure tests in `EndpointManagerTest`, no-false-ACK tests in `PolicySyncServiceTest` and `PolicyAckRecoveryTest`, and the heartbeat clear-failure path in `HeartbeatCoordinatorTest`.

The Week 3 static verifier also passed with the freshly built JAR. This closes M5-W3-07 and changes W3-A03 to `PASS`; it does not demonstrate a real `.deb` install, Ubuntu service, USBGuard daemon, physical USB, GUI, or reboot. Those cases remain deferred.

## Remaining M3 follow-up

`.deb` build/staging provenance and Ubuntu lifecycle acceptance remain **NOT BUILT / NOT VERIFIED**. The prior [2026-10-27 M3 follow-up](WEEK3_M5_M3_FOLLOWUP_20261027.md) is retained as the historical record of the then-open finding.
