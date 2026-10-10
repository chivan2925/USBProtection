# Week 1 QA Summary

Updated: 2026-10-10 (Asia/Bangkok). Scope: repository review and frontend verification on Windows.

## Week 1 gate status

| Gate item | Owner | Evidence | Result / remaining work |
|---|---|---|---|
| USBGuard flash BLOCK, physical mouse ALLOW, active user, sample event | M2 | [M2 evidence review](WEEK1_M2_POC_VERIFICATION.md) | 9/9 evidence-review PASS; Ubuntu runtime rerun pending. Keyboard and multiple flash devices remain unverified. |
| Admin Server skeleton | M1 | [Admin Server](../../admin-server/), [contract test](../../admin-server/src/test/java/com/group/usbshield/server/agentapi/AgentEventContractTest.java) | Code present on main; boot/H2 runtime verification not rerun in this review. |
| Contract v0.1 | M1 | [Agent-Server protocol](../AGENT_SERVER_PROTOCOL.md) | Freeze document present; live interoperability verification pending. |
| Agent shell, heartbeat/event transport | M3 | [Client Agent](../../client-agent/) | Code present on main; boot and live POST verification not rerun in this review. |
| Heartbeat/event POST integration | M3 + M1 | Server API and Agent transport code present | Live integration evidence still required; no branch-merge blocker. |
| Admin Web and Event History mock | M4 + M5 | [Frontend verification](evidence/M5_FRONTEND_VERIFICATION.md) | 31/31 automated tests PASS; build, lint and browser smoke PASS. |
| Test Plan and inventory | M5 | [Test Plan](../TEST_PLAN.md), [inventory](WEEK1_TEST_INVENTORY.md) | Present; distinguishes evidence review, frontend checks and pending runtime integration. |

## Remaining work

| Item | Owner | Next action |
|---|---|---|
| Server and Agent runtime checks | M1 + M3 | Verify boot/H2 and live heartbeat/event POST; record execution evidence. |
| Contract interoperability | M1 + M2 + M3 | Verify real payload against server DTO and published v0.1 protocol. |
| Real API in Admin Web | M1 + M4 + M5 | Integrate endpoint, event and whitelist APIs; preserve empty responses without mock fallback. |
| Hardware/session gaps | M2 + M5 | Test keyboard, multiple storage devices and real concurrent sessions on Ubuntu. |

## Verification limits

This update does not rerun Java tests, USBGuard, reboot or Ubuntu session checks. M2 evidence files were left unchanged. Frontend PASS applies to the mock/fixture UI and recorded browser checks; it does not establish end-to-end Agent to Server to Web success.
