# Member 2 Week 2 — Maven/JUnit test status

- **Date:** 2026-10-14 (Asia/Ho_Chi_Minh)
- **Executor/environment:** project owner, Windows 11, JDK 21
- **Command:** `mvn -B -f client-agent/pom.xml clean test`
- **Reported outcome:** **PASS — 40 tests; 0 failures, 0 errors, 0 skipped** (reported by the executor after the `ClientAgentTest` fix)
- **Provenance:** Owner report in conversation. **The successful Maven console output was not supplied as a file** with this update; this record is not a substituted tool-generated Maven log. Do not treat it as independently verified build evidence.
- **Prior run:** 39/40 passed, `ClientAgentTest.contextLoadsAndAllBeansAreConfigured` failed on Windows due to asserting availability of a real USBGuard gateway. The test was adjusted; the owner reports all 40 tests now pass.

## Guide section status

| Section | Status | Remaining |
|---|---|---|
| **14 — Test `UsbGuardCommandRunner`** | **PARTIAL** | Maven phase PASS; manually execute `list-devices`, `list-rules`, `get-parameter ImplicitPolicyTarget` via Java on Ubuntu with USBGuard IPC |
| **20 — Test gateway với Ubuntu thật** | **NOT EXECUTED** | Verify `listDevices()` for Kingston class 08 + USB mouse class 03 in Ubuntu, capture `01-java-list-devices.txt` |
| **73 — Capture Maven tests** | **PARTIAL** | Tests PASS, but raw console output must be captured in `11-mvn-test.txt` |

### Next steps

From repository root on Windows:

```powershell
mvn -B -f client-agent/pom.xml clean test 2>&1 | Tee-Object -FilePath docs/usbguard/week2-evidence/11-mvn-test.txt
if ($LASTEXITCODE -ne 0) { throw "Maven tests failed (exit $LASTEXITCODE)" }
```

Ensure the output includes `Tests run: 40, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS` (or actual updated totals if more tests are added). Then update Section 73 to DONE. Ubuntu device/GUI integration and Week 2 Gate still remain open; Maven tests alone do not prove physical USB enforcement, per-endpoint Allow/Revoke, or user-session notification.
