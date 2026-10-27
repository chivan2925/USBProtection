# Windows live Event History verification

Date: 2026-10-16 (Asia/Bangkok). Base: `cd81060b8f5f32e4ffa42e9120e2827d92219204`, branch `huy`, plus working-tree changes. The user requested live Events after the mapper work and deferred Ubuntu tests. [Source manifest](15-live-events-source-state.txt), [run instructions](../../../frontend/README.md).

## Implemented and verified

- Shared API adapter: canonical Admin JSON reads, same-origin session cookies, CSRF login/token rotation/logout, `/me` validation, status-aware errors and cancellation. No browser credential storage.
- `#/events` now reads the real Admin Events API. Six columns, original server order, 20-row server pagination and global totals; exact endpoint/user/Device ID filters and canonical decision/type choices. Demo remains explicit, read-only and free of Admin HTTP requests.
- Calendar dates become inclusive UTC bounds, with the upper bound one nanosecond before next local midnight. A real stored `.999999Z` record is included; next-day midnight is excluded. H2 storage precision is microseconds, independent of Java Instant/query precision.
- 401 shows a login link; empty/error/network states clear rows without fallback. Real browser invalid/valid login, reload session restoration and logout were exercised. HTTP 403/500 and network outages were injected through browser interception and verified at the adapter unit boundary.

| Check | Actual result | Evidence |
|---|---|---|
| `npm test` | PASS, 60 tests including mapper, query, API/session and server-table regression | [Tests](10-live-events-tests.txt) |
| `npm run lint` | PASS, final run clean | [Lint](11-live-events-lint.txt) |
| `npm run build` | PASS, TypeScript + Vite | [Build](12-live-events-build.txt) |
| `npm run test:browser` | PASS, routes/demo/error states/desktop/mobile | [Browser regression](14-live-events-browser-regression.txt) |
| `npm run test:browser:live` | PASS, actual Spring Boot/H2 + proxy + Chromium | [Real HTTP/browser](13-live-events-browser-http.txt) |

The real browser script builds on a locally packaged backend JAR, creates an isolated H2 DB, enrolls one QA endpoint and POSTs 25 synthetic storage events through authenticated Agent HTTP. It reads those persisted events through actual Admin session/GET APIs in the shipped UI, checking 20+5 pagination, global exact filters/empty results, date boundaries, reload/logout and 375px mobile overflow. Ports: backend 18083, preview 4174. Credentials are random process-local values and are omitted from evidence. Its processes and temporary DB/profile are cleaned up; existing application databases are not used or changed.

Backend packaging required Maven Central downloads into ignored `target/maven-repository`. An initial offline attempt lacked the parent POM; an initial unquoted PowerShell Maven property was corrected before the successful build. The first real browser run reached the date test and timed out because its `.999999999Z` seed rounds into the next second at H2 storage precision. The fixture was corrected to `.999999Z`; the later run passed. An expanded filter run initially read Device ID during a loading transition; reading it from the settled first page fixed the test-script race. The final expanded run passed; the browser log retains all attempts. This change does not modify backend timestamp persistence. Early lint warnings in the new code/test script were corrected; the final lint log is clean.

## Screenshots

| Screenshot | Meaning |
|---|---|
| [Live Events desktop](events-real-http-desktop.png) | Actual H2-backed Admin response with synthetic HTTP QA records and global pagination |
| [Date filter](events-real-http-date-filter.png) | Selected local day includes the final persisted microsecond and excludes next midnight |
| [Live Events mobile](events-real-http-mobile.png) | Actual response at 375px; table scroll without document overflow |

## Limits

This is real backend/persistence/browser integration with a **synthetic HTTP event source**. It does not prove a physical USB source, active Linux attribution, USBGuard allow/block enforcement or native GUI notification. Ubuntu cases remain NOT EXECUTED by current scope. Existing M2 sample evidence is unchanged. Other management pages and policy mutations remain unavailable outside demo; shared changes should be reviewed with M4. The full Week 2 group gate stays open.
