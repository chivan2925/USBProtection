# M4 Week 4 — Admin Frontend Handoff

Date: 2026-10-30 (Asia/Saigon)
Owner: M4 — Admin Frontend
Source revision reviewed: `6127766a1d3b982efa0ad0f9414ff1abc368cbb5`

## Scope completed

| Work item | Result |
| --- | --- |
| W4-M4-01 UI stabilization | PASS: loading, empty, error, retry, session expiry, CSRF and responsive routes covered by tests. |
| W4-M4-02 No production mock/fake state | PASS: demo data is explicit read-only mode; live API failures and empty responses stay truthful. |
| W4-M4-03 Per-endpoint policy UX | PASS: Allow/Revoke, idempotency, A/B isolation, desired/applied versions and controlled ACK are covered. |
| W4-M4-04 Admin Guide and screenshots | PASS: final guide in `docs/ADMIN_GUIDE.md`; Week 4 screenshots are checked in under `docs/qa/week4-evidence/2026-10-28/windows-regression/screenshots/`. |
| W4-M4-05 Production build and handoff | PASS technical: production build, 26/26 server tests and same-origin server-JAR integration completed; M5 acknowledgement remains pending. |

## Verification

| Command | Result |
| --- | --- |
| `npm test` | 96/96 PASS |
| `npm run lint` | PASS |
| `npm run build` | PASS |
| `npm run test:browser` | PASS; HTTP fixture browser regression |
| `npm run test:live` | PASS; Spring Boot/Spring Security/H2 browser regression |
| `mvn -B -f admin-server/pom.xml -Pwith-frontend clean package` | BUILD SUCCESS; 26/26 tests; 5 frontend resources copied into server static assets |

Shared frontend review covers `frontend/src/services/api.ts`, `frontend/src/services/http.ts`, Event API mapping, browser harness, demo boundary and production error states. No frontend source change was needed for this handoff; current code already passed the full M4 suite.

## Handoff limits

- M5 still needs to acknowledge shared frontend review against its selected release-candidate SHA.
- HTTP ACK and H2 persistence do not prove Agent apply, USBGuard enforcement or physical USB behavior.
- Ubuntu 24.04 install, systemd, reboot, native notification and physical USB cases remain `NOT EXECUTED / DEFERRED_UBUNTU`.
