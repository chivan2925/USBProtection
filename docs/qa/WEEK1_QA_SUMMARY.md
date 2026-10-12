# Week 1 QA Summary

Updated: 2026-10-12 (Asia/Saigon). Scope: repository review, frontend verification and recorded M1/M3 Windows runtime evidence. This update reviews existing logs; it does not rerun Java or Ubuntu hardware tests.

## Week 1 gate status

| Gate item | Owner | Evidence | Result / remaining work |
|---|---|---|---|
| USBGuard flash BLOCK, physical mouse ALLOW, active user, sample event | M2 | [M2 evidence review](WEEK1_M2_POC_VERIFICATION.md) | 9/9 evidence-review PASS; Ubuntu runtime rerun pending. Keyboard and multiple flash devices remain unverified. |
| Admin Server skeleton | M1 | [Server verification](../member1/WEEK1_SERVER_VERIFICATION.md), [test summary](../member1/evidence/test-summary.txt), [server log](../member1/evidence/server-runtime.log) | PASS (Windows PoC): standalone server boots, file H2 connects; 4 tests pass, including HTTP and persisted data assertions. |
| Contract v0.1 | M1 | [Agent-Server protocol](../AGENT_SERVER_PROTOCOL.md), [API](../API.md), [contract test](../../admin-server/src/test/java/com/group/usbshield/server/agentapi/AgentEventContractTest.java) | PASS (Week 1 sample/DTO/docs): unchanged M2 sample, session 21, ALLOWED; production interoperability remains Week 2. |
| Agent shell, heartbeat/event transport | M3 | [Agent runtime log](../member1/evidence/agent-runtime.log), [server runtime log](../member1/evidence/server-runtime.log) | PASS (Windows PoC): actual Agent JAR starts, enrolls and sends heartbeat. |
| Heartbeat/event POST integration | M3 + M1 | [Responses](../member1/evidence/poc-responses.json), [runtime verification](../member1/WEEK1_SERVER_VERIFICATION.md) | PASS (real HTTP): M2 sample accepted and persisted; Agent simulated event persisted. Physical USB and active Ubuntu user E2E not executed. |
| Admin Web and Event History mock | M4 + M5 | [Frontend verification](evidence/M5_FRONTEND_VERIFICATION.md) | 31/31 automated tests PASS; build, lint and browser smoke PASS. |
| Test Plan and inventory | M5 | [Test Plan](../TEST_PLAN.md), [inventory](WEEK1_TEST_INVENTORY.md) | Present; distinguishes evidence review, frontend checks and pending runtime integration. |

## Remaining work

| Item | Owner | Next action |
|---|---|---|
| Ubuntu runtime and physical USB integration | M1 + M2 + M3 | Windows boot/H2/HTTP evidence is complete. Verify Ubuntu deployment, real USB events and actual active-session attribution. |
| Production contract and authentication | M1 + M2 + M3 | Sample/DTO/docs checked for Week 1; verify authenticated endpoint isolation and production payload interoperability. Agent token validation remains unimplemented. |
| Real API in Admin Web | M1 + M4 + M5 | Integrate endpoint, event and whitelist APIs; preserve empty responses without mock fallback. |
| Hardware/session gaps | M2 + M5 | Test keyboard, multiple storage devices and real concurrent sessions on Ubuntu. |

## M5 completion review

Week 1 M5 deliverables are present: Event History mock and required columns, five filter controls, page/table tests, Test Plan v0.1, inventory, nine-item M2 evidence review and this summary. Frontend checks are recorded in the execution report; M2 verification is read-only evidence review.

Test Plan and inventory now share T* IDs. Keyboard hardware remains NOT EXECUTED; functional revoke T36 has explicit acceptance criteria and stays PLANNED WEEK 2. The M2 report records the actual Codex reviewer, date and reviewed revision without claiming an independent member sign-off. [Event field mapping](EVENT_VIEW_MODEL_MAPPING.md) documents the view model and API integration limits.

No unresolved M5 mock/UI implementation blocker was found. Remaining runtime/hardware and live API work stays assigned to the relevant owners above; this does not imply every Week 1 system gate has passed.

## Verification limits

### Member 4 follow-up — 2026-10-11 (Asia/Saigon)

[M4 verification](evidence/M4_FRONTEND_VERIFICATION.md): build/lint, 34/34 tests và browser smoke PASS sau khi tích hợp whitelist vào Endpoint Detail và thêm demo rõ nhãn cho Dashboard/Endpoints. Merge markers đã hết trước lần rà soát này. M4 PASS áp dụng cho skeleton/wireframe Week 1; auth/session và API quản trị thật vẫn chờ tích hợp.

The M4 follow-up did not rerun Java tests, USBGuard, reboot or Ubuntu session checks. The 2026-10-12 synchronization incorporates the existing M1/M3 execution logs above. M2 evidence files remain unchanged. Frontend PASS applies to the mock/fixture UI and recorded browser checks; it does not establish end-to-end physical USB to Server to Web success.
