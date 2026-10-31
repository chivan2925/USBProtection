# M3 follow-up QA evidence — 2026-10-27

Branch `huy`; reviewed revision `2885cf9cfc30b841fb0e3d44d3fd281ea16350ba`. M3 implementation commit: `8beb954edbdcdfb9071c52a91d4fb82e1e8522a7`. The source working tree was clean before the QA documentation update. [QA follow-up](../../../WEEK3_M5_M3_FOLLOWUP_20261027.md).

| Command | Result |
|---|---|
| `mvn.cmd -o -B -f client-agent/pom.xml clean package` | PASS — **122 tests**, 0 failures/errors/skips; Agent JAR built. |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File packaging/tests/test-staging-verifier.ps1` | PASS — 3/3: absent staging and missing required file reject; complete synthetic staging passes. |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File packaging/tests/verify-week3-windows.ps1 -CheckBuiltJar` with JDK 22 `bin` on `PATH` | PASS — source/control/live/service/helper checks and fresh JAR contents. |
| `bash -n` on builder, `postinst`, `prerm`, and notifier launcher | PASS — syntax only. The first sandbox attempt could not create a Git Bash signal pipe; the permitted retry parsed all four scripts successfully. |
| Source review of policy persistence/ACK callers | **OPEN — M5-W3-07.** Boolean persistence results are ignored before ACK/clear-pending behavior. |
| `git diff --check f80a936..HEAD` | Non-blocking hygiene finding: trailing whitespace at `packaging/tests/test-staging-verifier.ps1:40`; no functional test failure. |

The Maven and PowerShell checks run only Windows unit/mock/static scenarios. The staging test creates temporary synthetic staging under the system temp directory and removes it. No `.deb`, Ubuntu service, USBGuard daemon, physical USB, native GUI, systemd lifecycle, install, or reboot was executed. No application database was used.

The JDK `jar` executable was absent from the default `PATH`; adding `C:\Program Files\Java\jdk-22\bin` for the verifier process allowed its built-JAR inspection. This is an execution-environment adjustment, not a product change.
