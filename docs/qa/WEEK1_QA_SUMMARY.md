# Week 1 — QA Summary

> **Owner:** Member 5
> **Date:** 2026-10-10
> **Project:** USBShield Ubuntu

---

## Week 1 Gate Status

| Gate item | Owner | Evidence | Result | Blocker | Next week action |
|---|---|---|---|---|---|
| Unknown Flash BLOCK | M2 | `docs/usbguard/evidence/05-blocked-devices.txt` | ✅ PASS | — | — |
| Physical mouse ALLOW | M2 | `docs/usbguard/evidence/12-hid-mouse-test.txt` | PASS (M2 reported / evidence reviewed) | Keyboard runtime not executed | Verify keyboard separately |
| Active Ubuntu username/session | M2 | `docs/usbguard/evidence/18-active-user-resolver-poc.txt` | ✅ PASS | — | — |
| Sample event — endpoint+user+USB+decision | M2 | `docs/agent-contract/sample-usb-event.json` | ✅ PASS | — | M1 freeze contract v0.1 |
| Admin Server skeleton | M1 | `admin-server/` | MERGED; runtime NOT VERIFIED | Boot/H2 execution evidence pending | Verify boot + H2 |
| Contract freeze v0.1 | M1 | [Protocol v0.1](../AGENT_SERVER_PROTOCOL.md) | Specification merged; DTO alignment NOT VERIFIED | Runtime contract review pending | Compare server/agent/sample contracts |
| Agent shell starts | M3 | `client-agent/` | MERGED; runtime NOT VERIFIED | Startup execution evidence pending | Verify agent startup |
| Heartbeat/event POST PoC | M3+M1 | Agent API and sender source merged | Live integration NOT VERIFIED | Execution evidence pending | Live integration test Week 2 |
| Admin Web skeleton | M4 | [Frontend verification](evidence/M5_FRONTEND_VERIFICATION.md) | PASS (frontend) | None | API integration Week 2 |
| Event History mock | M5 | [Frontend verification](evidence/M5_FRONTEND_VERIFICATION.md) | PASS (frontend) | None | Real API events Week 2 |
| Test Plan v0.1 | M5 | `docs/TEST_PLAN.md` | ✅ PASS | — | — |

---

## Member 2 PoC summary

M2 evidence review (no runtime rerun): [`docs/qa/WEEK1_M2_POC_VERIFICATION.md`](./WEEK1_M2_POC_VERIFICATION.md)

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
| M1 Admin Server runtime not verified | High | M1 | Source merged; record boot/H2 test evidence |
| M3 Agent runtime not verified | High | M3 | Source merged; record startup and live POST evidence |
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

This run updated frontend source/tests and QA documents; M2 source evidence remains unchanged.

Frontend verification on 2026-10-10: 31/31 automated tests, build, lint and browser smoke PASS. M2 PASS rows refer to reported/evidence-reviewed results, not runtime reruns. M1/M3 code and protocol are merged; source presence does not establish live integration PASS.
