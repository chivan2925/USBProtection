# USBProtection regression matrix — candidate

| ID | Area | Scenario | Expected | Coverage |
|---|---|---|---|---|
| R01 | Agent USBGuard | Real `Insert block -> PolicyChanged allow -> PolicyApplied allow` | Initial BLOCK audit preserved, final ALLOWED recorded, pending popup canceled | New `UbuntuAllowTransitionRegressionTest` + existing watcher tests |
| R02 | Agent USBGuard | `Insert block -> PolicyApplied block` | No fabricated ALLOW, blocked event retained | New test |
| R03 | Agent notifier | Initial BLOCK becomes effective ALLOW | No popup | New `UbuntuFinalStateNotificationTest` |
| R04 | Agent notifier | Effective BLOCK with class 08 | Exactly one attempted popup | New test + existing policy tests |
| R05 | Agent notifier | Device removed or PolicyApplied ALLOW before notification | Pending popup canceled / no stale popup | New tests |
| R06 | Agent network | `/etc/hosts` resolves hostname to `127.0.1.1`, real IPv4 exists | Use non-loopback IPv4, or NULL if none | New `SystemInfoProviderRegressionTest` |
| R07 | Agent network | Ubuntu `/etc/os-release` quoted values | Ubuntu + version parsed | New test |
| R08 | Server | Post-enrollment heartbeat reports IP, OS, current user | Persist and return in admin endpoint DTO; no token exposure | New `UbuntuE2EMetadataRegressionTest` |
| R09 | Server | UNKNOWN GUI user on later heartbeat | Do not display stale current username | New test |
| R10 | Server | Allow whitelist hash identity with device metadata | Return name, VID:PID, serial, createdAt; policy remains scoped A-only | New test + existing allow/revoke tests |
| R11 | Frontend | Endpoint response contains IP, OS, user, ACK | Show actual metadata, do not invent values | New `ubuntu-e2e-regression.test.mjs` |
| R12 | Frontend | Whitelist response contains device metadata + createdAt | Render populated fields | New test |
| R13 | Frontend | Historical BLOCK followed by ALLOWED | Distinguish audit history and latest status | New test |

## Required existing suites to re-run
- All Client Agent JUnit tests including policy synchronizer, event parser, notifications, outbox, session attribution.
- All Admin Server JUnit/H2/security tests including A/B isolation and CSRF.
- Entire frontend `npm test`, lint and production build.

## Remaining Ubuntu hardware-dependent validation (NOT simulated by Windows tests)
- USBGuard watcher final target vs `list-devices` across real VMware passthrough; verify settled transition latency. If transition >450ms, notification must still be suppressed by final-state verification; record actual timing.
- Unknown Kingston BLOCK + one popup on active GNOME; allowed Kingston ALLOW + zero BLOCK popup after reconnect.
- A Allow / B Block / A Revoke with same physical Kingston, separate machine IDs and endpoints.
- Real per-UID socket ACL, `SO_PEERCRED`, no-GUI user case, service non-root, reboot and persistence.
- Verify Admin UI device status latest decision, event history immutable, and dashboard historical count semantics.
