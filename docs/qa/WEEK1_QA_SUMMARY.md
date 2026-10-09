# Week 1 — QA Summary

> **Owner:** Member 5  
> **Date:** 2026-10-09  
> **Project:** USBShield Ubuntu

---

## Week 1 Gate Status

| Gate item | Owner | Evidence | Result | Blocker | Next week action |
|---|---|---|---|---|---|
| Unknown Flash BLOCK | M2 | `docs/usbguard/evidence/05-blocked-devices.txt` | ✅ PASS | — | — |
| Mouse/keyboard ALLOW | M2 | `docs/usbguard/evidence/12-hid-mouse-test.txt` | ✅ PASS | — | — |
| Active Ubuntu username/session | M2 | `docs/usbguard/evidence/18-active-user-resolver-poc.txt` | ✅ PASS | — | — |
| Sample event — endpoint+user+USB+decision | M2 | `docs/agent-contract/sample-usb-event.json` | ✅ PASS | — | M1 freeze contract v0.1 |
| Admin Server skeleton | M1 | *(not in this repo branch)* | ⚠️ NOT VERIFIED | Pending M1 branch merge | M1 verify boot + H2 |
| Contract freeze v0.1 | M1 | *(pending)* | ⚠️ NOT VERIFIED | Depends on M1+M2+M3 alignment | M1 publish AGENT_SERVER_PROTOCOL.md |
| Agent shell starts | M3 | *(not in this repo branch)* | ⚠️ NOT VERIFIED | Pending M3 branch merge | M3 verify skeleton boots |
| Heartbeat/event POST PoC | M3+M1 | *(pending)* | ⚠️ NOT VERIFIED | Both branches need to merge | Live integration test Week 2 |
| Admin Web skeleton | M4 | `frontend/tests/m4_endpoints.test.mjs` | ✅ PASS | — | API integration Week 2 |
| Event History mock | M5 | `frontend/tests/m5_events.test.mjs` | ✅ PASS | — | Real API events Week 2 |
| Test Plan v0.1 | M5 | `docs/TEST_PLAN.md` | ✅ PASS | — | — |

---

## Member 2 PoC summary

Full verification: [`docs/qa/WEEK1_M2_POC_VERIFICATION.md`](./WEEK1_M2_POC_VERIFICATION.md)

| Item | Result |
|---|---|
| Flash BLOCK | ✅ PASS |
| Mouse ALLOW | ✅ PASS |
| Reboot persistence | ✅ PASS |
| USBGuard watch stream | ✅ PASS |
| Active user resolved | ✅ PASS |
| Second user (no hard-code) | ✅ PASS |
| Ambiguity → UNKNOWN | ✅ PASS (SIMULATED) |
| Parser massStorage=true | ✅ PASS |
| Sample event contract | ✅ PASS |
| Multiple USB storage | ❌ NOT EXECUTED |

---

## Risks & Blockers

| Risk | Severity | Owner | Mitigation |
|---|---|---|---|
| M1 Admin Server not merged/verified | High | M1 | Merge and boot test before Week 2 integration starts |
| M3 Agent shell not merged/verified | High | M3 | Merge and test heartbeat POST before Week 2 |
| Contract fields not aligned (M1 DTO vs M3 transport DTO vs M2 sample) | High | M1 | M1 hosts contract review; all three align before Week 2 |
| `EndpointWhitelistPanel` not connected to real API | Medium | M4+M1 | Wire up Week 2 after M1 API is live |
| Event History reads mock, not real events | Medium | M5+M1 | Replace mock adapter in Week 2 |

---

## Integrity note

M5 did not modify any M2 source evidence files.

All verification was performed by reading the following files read-only:
```text
docs/usbguard/USBGuard_POC.md
docs/usbguard/evidence/
docs/agent-contract/sample-usb-event.json
```

Results are recorded only in `docs/qa/` (M5 ownership).
