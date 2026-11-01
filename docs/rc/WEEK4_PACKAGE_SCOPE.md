# Week 4 finalization patch — scope and limits

**Baseline:** `main` `63e4dc61b15b86859dd4ca2ca4be60021a293091`.  
**Delivery:** ZIP overlay + a single deterministic doc/static-verifier synchronization script.  
**No owner sign-offs, credential rotations or Ubuntu E2E tests are fabricated.**

## Changed code

- M2: add deterministic `NotifierSocketPaths`; helper uses logind's UID-owned runtime directory, grants exact named-user POSIX ACLs, fails on missing/forged runtime or stale socket; Agent publisher confirms path ownership, socket inode and Linux `SO_PEERCRED` helper owner **before** transmitting.
- M3: add Debian `acl` dependency; remove new global world-writable IPC directory provisioning; retire known legacy `tmpfiles.d` entry safely; validate launcher/XDG UID; new read-only Ubuntu ACL inspection command.
- M2/M3: regression for path/UID/peer predicate and static checks. Avoid unrelated `admin-server`, `frontend`, `sync` and persisted data changes.
- M1: clarify route-specific `401` vs `400` in existing canonical API doc (synced by the one-time script).
- M5: add prepared owner sign-off, design/acceptance and evidence instructions. Keep historical evidence unchanged.

## Required verification

1. Owner Windows Maven/JUnit compile and regression on patched Agent code; JAR build.
2. Updated `verify-week3-windows.ps1` and `verify-week4-windows.ps1` + staging scripts.
3. M2/M3 review of `SO_PEERCRED`, `setfacl` and startup assumptions, including target Ubuntu session behavior; actual Ubuntu tests are `DEFERRED`.
4. M5 source SHA evidence and any affected integration tests; M1 human group sign-off, credential administrator attestation, binary handoff.

**Important:** This is a *candidate change package*, not verified Week 4 completion by itself. Its secure runtime behavior has not been observed on a running Ubuntu desktop. The logged-in user may have to log out and back in after `.deb` upgrade for autostart. A known old Java source deployed in a previously running GUI process will not automatically gain the new IPC behavior.
