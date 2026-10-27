# USBShield Week 2 — Windows browser preview readiness regression

**Date:** 2026-10-20 (+07:00)  
**GitHub source baseline:** `main` `e75213b437d01b3335b952618b02705874d257dc`  
**Docs/packaging baseline:** the user's `USBProtection(6).zip` attachment (latest already-patched docs/packaging), not replaced by old GitHub docs.  
**Scope:** `frontend/tests/browser.mjs` startup check for `npm run test:browser` and `npm run test:live`; Ubuntu tests remain deferred until after Week 4.

## Observed result on Windows (user-provided)

- Node.js `v24.14.0`, Vite `v8.3.1`.
- `npm ci`: complete; npm audit reported **one high-severity advisory**, not a build/test failure. Audit remediation is a separate dependency-review task; do not run blind dependency updates as part of this harness fix.
- `npm run lint`: 0 warnings, 0 errors.
- `npm run build`: PASS.
- `npm test`: **72 PASS / 0 FAIL** before this patch.
- `npm run test:browser` and `npm run test:live`: both stopped at `Timed out: preview server startup` although Vite printed `Local: http://127.0.0.1:4173/`.
- `npm run test:browser:live`: no final result was included in the supplied output. Do not label PASS or FAIL without its exit code.
- Admin Server and Client Agent Maven tests: PASS **as reported by the user**; no new independent Maven execution in this patch.

## Cause and scope

`frontend/tests/browser.mjs` previously waited for a **literal, uninterrupted URL string** inside the child Vite console output:

```js
await until(() => output.includes('http://127.0.0.1:4173'), 'preview server startup')
```

Vite's colored/styled terminal output need not contain that raw contiguous substring even when the server is listening. This is consistent with the observed output; no evidence establishes that Vite itself failed to start. The replacement uses HTTP GET `/` against `http://127.0.0.1:4173/` and accepts actual HTTP 2xx response (`response.ok`). Other tests and browser assertions are unchanged.

## Delivery and one-time source migration

The uploaded ZIP **did not contain `frontend/`**; frontend source was retrieved from the current GitHub `main` only for review. To avoid shipping an incomplete/reconstructed copy of the large, existing browser script, the fix is delivered as a small **idempotent source migration** plus two npm `pre*` scripts:

- `frontend/tests/apply-browser-readiness-fix.mjs`: checks the exact known pre-fix line and replaces it once; refuses unknown/ambiguous versions instead of overwriting unrelated code.
- `frontend/tests/browser-readiness.test.mjs`: 4 regression assertions for scope, idempotence, unknown source safety and colored-output case.
- `frontend/package.json`: runs migration automatically before both `test:browser` and `test:live`, and includes the 4 regression tests in `npm test`.

**Important:** The first browser test run will also modify the existing tracked file `frontend/tests/browser.mjs` on disk. This is intentional. Review and include that change when committing the patch. Subsequent runs print “fix already applied” and leave the file unchanged. The actual browser script remains the single canonical test implementation; runtime source transformations and duplicated browser suites are not introduced.

## Rerun from Windows PowerShell

From repository root (JDK 21, Node 24 and Chrome/Edge already installed):

```powershell
# If already built, no need to rebuild backend unless sources changed.
# For the real browser HTTP tests, the Admin Server JAR must exist.
mvn -B -f admin-server/pom.xml -DskipTests package

cd frontend
npm ci
npm run lint
npm run build
npm test
npm run test:browser
npm run test:live
npm run test:browser:live
cd ..
git status --short
```

Set `JAVA_HOME` to a real JDK 21 path first if `browser-live.mjs` cannot find Java. Run browser suites sequentially. Ports 4173, 4174, 18083 should be free before starting. If readiness fails again, report the complete error/stdout including the HTTP probe outcome and process-exit status. A successful test on Windows does not prove native Ubuntu USBGuard/GUI behavior.

## Evidence provenance

- This patch was prepared from GitHub `main` and uploaded docs/packaging; no production Agent/Server/React code was modified.
- Locally checked with Node.js 22: `node --test tests/browser-readiness.test.mjs` **4 PASS / 0 FAIL** and `node --check` PASS. This is not equivalent to rerunning browser suites on Windows with Node 24.
- **Current Windows browser gate: PENDING USER RERUN** on the patched workspace. Preserve prior raw QA evidence without rewriting historical results.
