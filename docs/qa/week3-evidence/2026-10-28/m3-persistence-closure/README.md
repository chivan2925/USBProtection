# M3 persistence/ACK closure evidence — 2026-10-28

Branch `huy`; tested revision `39561907ef92f97234f449140bc394ddfe4f30e8`. M3 implementation commit: `0c9bb6d2b626abe1f22eed3559d89f19e503138b`. [QA closure](../../../WEEK3_M5_M3_PERSISTENCE_CLOSURE_20261028.md).

| Command | Result |
|---|---|
| `mvn.cmd -o -B -f client-agent/pom.xml clean package` | PASS — **127 tests**, 0 failures/errors/skips; executable Agent JAR built. |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File packaging/tests/verify-week3-windows.ps1 -CheckBuiltJar` with JDK 22 `bin` on `PATH` | PASS — static service/control/live/launcher/build checks and fresh JAR contents. |
| Source and test review of M3 persistence changes | PASS — failed policy-state persistence rolls back state and prevents server ACK; failed pending-ACK clear retains retry state. |

The tests are Windows unit/mock/static checks. No `.deb`, Ubuntu service, USBGuard daemon, physical USB, native GUI, systemd lifecycle, install, or reboot was executed. The JDK `jar` executable required `C:\Program Files\Java\jdk-22\bin` on `PATH` for the static verifier process; this is an execution-environment adjustment, not a product change.
