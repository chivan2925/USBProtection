# M4 / M5 Week 2 technical handoff after merge

Prepared and technically reviewed by Codex for the user on 2026-10-16 (Asia/Bangkok). Frontend revision: `a9b1799e2920cba8b6d02fc1d7186fbc0bc59825` on `huy`, merging `main` (`1eb6be6`) with M5 Events (`c6b1b8f`). [Verification and evidence](week2-evidence/M5_POST_MERGE_VERIFICATION.md), [field mapping](EVENT_VIEW_MODEL_MAPPING.md).

**Technical review and handoff package: COMPLETE. Human M4 acknowledgment: PENDING; no owner confirmation is recorded.** No message has been sent to another member and no approval is implied by this document.

## Shared implementation to maintain

| File / owner | Final contract and merge review |
|---|---|
| `frontend/src/services/http.ts` — M4 | One HTTP implementation for auth, events and management. Cookies use `credentials: include` on relative same-origin URLs, JSON is validated, redirects are rejected and reads are cancellable. `/api/auth/csrf` must return a nonblank token with `headerName: X-CSRF-TOKEN`; mutations use the in-memory token. No JavaScript credential persistence. Caller-aborted reads stop before fetch; status-aware errors feed the UI |
| `frontend/src/services/api.ts` — M4, consuming M5 mapper | Retains M4 live snapshot/endpoints/whitelist adapters and auth functions. Adds `getEventPage(query, signal)` through shared `request()` and `mapEventPage()`, plus the events capability. No second fetcher. Snapshot metrics use complete bounded reads; live Events never filters or paginates a snapshot |
| `frontend/src/hooks/AuthProvider.tsx`, `useAuth.ts` — M4 | One provider owns `/me`, login, logout and session expiry. Login obtains CSRF, posts credentials, verifies `/me` and refreshes the rotated token; restoring `/me` also refreshes CSRF. Logout uses the current token and clears it after success |
| `frontend/src/App.tsx`, `layouts/AdminLayout.tsx`, `pages/Login.tsx` — M4 | Auth guards precede live page rendering; unauthorized/expired sessions route to Login. Successful login opens Dashboard. `/events` renders `LiveEvents` inside the same AdminLayout/provider; demo uses the existing snapshot route. Live Events does not fetch the management snapshot. Sidebar logout uses the same provider |
| `frontend/src/hooks/useResource.ts` — shared M4 hook | Cancels previous/unmounted reads, clears stale rows and retains `ApiError.status` as `statusCode`; preserves all Error messages and existing unavailable handling |
| `frontend/vite.config.ts` — M4/shared configuration | Proxy precedence: `API_PROXY_TARGET`, then `USBSHIELD_API_TARGET`, then `http://127.0.0.1:8080`. Dev and preview both proxy `/api`; M4 React SSR externals are retained |
| `frontend/package.json`, browser/API fixtures — M4/M5 | Union of all nine test files: 72 tests. Retains `test:browser`, M4 `test:live`, and M5 `test:browser:live`. Browser suites run sequentially after build; fixture now supplies canonical Events page metadata and server filters |
| `frontend/src/pages/Events.tsx`, `components/event/**`, `types/event.ts`, `utils/eventFilters.ts`, `services/mappers/eventMapper.ts` — M5 | Six columns and endpoint links; source attribution and timestamp retained. Live page size 20, zero-based server paging, server order/totals and exact query filters. Explicit demo uses local full-fixture filters and ten-row pagination. Mapper owns DTO validation and never manufactures audit data |

## Behavior verified together

| Review check | Evidence |
|---|---|
| Admin login failure/success, `/me`, rotated CSRF, reload/logout and expired-session redirect | [Management browser](week2-evidence/21-post-merge-browser-management.txt), [auth/API tests](week2-evidence/19-post-merge-tests.txt) |
| A-only Allow, duplicate idempotency, Revoke, B unchanged and H2 restart persistence | [Management browser](week2-evidence/21-post-merge-browser-management.txt); desired/applied remains 1/0→2/0→controlled ACK 2/2→Revoke 3/2 |
| Backend → Web Events, six columns, 20+5 rows, global filter/reset, fractional end-of-day, reload/logout, mobile | [Events browser](week2-evidence/22-post-merge-browser-events.txt) |
| Empty/error/no fixture fallback, readonly demo without API traffic, desktop/mobile regression | [Fixture browser](week2-evidence/20-post-merge-browser-fixture.txt), [Events browser faults](week2-evidence/22-post-merge-browser-events.txt) |
| TypeScript/build/lint and both members' regression suites | [Build](week2-evidence/17-post-merge-build.txt), [lint](week2-evidence/18-post-merge-lint.txt), [72 tests](week2-evidence/19-post-merge-tests.txt) |

## Reproduction

Use installed dependencies, local Chrome/Edge and a built `admin-server/target/usbshield-admin-server.jar`. Set `JAVA_HOME` to a JDK 21+ installation; this evidence uses JDK 22, identified in the [environment](week2-evidence/16-post-merge-environment.txt).

```powershell
cd frontend
npm run build
npm run lint
npm test
npm run test:browser
npm run test:live
npm run test:browser:live
```

Run browser commands sequentially. M4 fixture/management preview uses 4173; its real backend uses a temporary port. M5 Events uses backend 18083 and preview 4174. Both real backend suites use separate H2 databases and synthetic HTTP input; neither launches the actual M3 Agent or proves USBGuard enforcement. Screenshots and tested artifact hashes are indexed in the verification report.

## Owner acknowledgment and Ubuntu follow-up

M4 can review the merge contract table and evidence above and record their name, date, reviewed revision and accepted/open items here or in a linked PR review. The confirmation fields remain **PENDING** until M4 provides that record; Codex's technical review is the only review attributed here.

M5 maintains event mapping/filter/UI and QA; M4 maintains shared networking/auth/management. M1 owns API/DTO changes. M2/M3 supply Ubuntu USBGuard, actual Agent apply/ACK, offline/outbox, correct user and native notification evidence for the still-open Week 2 group gate.
