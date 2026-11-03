# Apply package — USBShield Phase 3 / R09–R11

1. In Windows PowerShell, switch to `vinhkhang`. **Do not** merge `main` before testing:
   ```powershell
   git switch vinhkhang
   git status --short
   git rev-parse HEAD
   ```
   Working tree should be clean and the expected HEAD is `3b8d0c7d469c7a2d52a5fb34d0529e35766dc955`.
2. Extract the ZIP into the repository root, preserving folders. The ZIP contains only changed/new source, test and documentation files. It does **not** contain `target/`, `node_modules/`, database files, secrets or generated binaries.
3. Run the existing Windows gates:
   ```powershell
   mvn -f admin-server/pom.xml clean test
   mvn -f client-agent/pom.xml clean test
   cd frontend
   npm ci
   npm test
   npm run lint
   npm run build
   cd ..
   git diff --check
   git status --short
   ```
4. Note: the two Maven commands and the frontend build are independent. A PASS in one does not prove the other is correct. On failure, save the **complete** command output and Surefire report for the failed module. For frontend errors, include the specific test name and stack trace.
5. If all gates PASS, commit/push the changed files to `vinhkhang` as planned; **do not create a PR to `main`** until the independent post-Phase-3 review. Suggested commit message below.

`perf(admin): batch event metadata and paginate dashboard device projections`

## Review checklist

- `/api/admin/events` still returns historical rows without metadata and honors filters.
- Dashboard with >10,000 events loads without client page traversal.
- Devices/Blocked and endpoint detail server-pagination/search show true totals and isolation.
- Allow/Revoke is endpoint-specific; latest `POLICY_APPLIED` changes displayed state.
- Admin-only API is protected; no mock state leaks into the production adapter.
- Rollback: restore the pre-package Git state on `vinhkhang` before commit; no DB migration is required by this change.
