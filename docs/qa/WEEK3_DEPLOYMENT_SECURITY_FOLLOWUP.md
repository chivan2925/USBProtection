# USBShield — external security and deferred deployment evidence

**Baseline:** main `5f32d415fc5a5ef6fb8e7b4d1867041eb4d05487`.

## Previously exposed runtime files

The prior public Git history included `data/endpoint.json` with an endpoint `agentToken`, a local H2 database, and a client event spool file. Current main HEAD no longer tracks those files and `.gitignore` ignores runtime data. **Removing tracked files does not revoke a credential or erase earlier public commits.**

The deployment owner must determine whether that endpoint/token or any related enrollment/bootstrap secret was live, and if so:

1. Revoke the affected endpoint credential server-side or reset the affected test environment, rotate the deployment enrollment/bootstrap secret as applicable, and re-enroll affected endpoints. The current Admin enrollment API rejects duplicate machine IDs; do not assume re-enrollment works by merely deleting `endpoint.json` without an authorized administrative reset.
2. Keep new secrets in protected local configuration or environment, not Git/CI logs. Do not paste secrets into ChatGPT evidence output.
3. Record **only** the fact of rotation/revocation (date, responsible owner, affected scope, result), with token values redacted. Consider coordinated history cleanup if required; it cannot replace revocation.

Status: `NOT VERIFIED` (requires deployment access and responsible human).

## `.deb` provenance

`packaging/tests/verify-week3-windows.ps1` validates static files and optionally the fat JAR. Its synthetic staging positive test does not establish that `packaging/scripts/build-deb.sh` produced a genuine `.deb`. Record a separate CI/WSL build invocation, exit status, exact artifact path, `sha256sum`, `dpkg-deb --info` and `dpkg-deb --contents` before declaring `DEB_BUILT_AND_INSPECTED`.

Status: `NOT BUILT / NOT VERIFIED` until direct evidence is supplied.

## Additional hardening before real Ubuntu rollout (recommendations, not proven Week 3 P0)

- `client-agent/.../endpoint/EndpointManager.java`: writing JSON directly with `ObjectMapper.writeValue(File, ...)` may truncate an existing credential file on sudden interruption. Consider atomic temporary-file + rename, protected permissions, and crash/corrupt-file tests. Do not label current write path atomic.
- `packaging/agent-deb/DEBIAN/postinst`: this patch adds explicit validation of active `ImplicitPolicyTarget`, rejects unsafe/ambiguous directives, and refuses to report success when USBGuard cannot start. Shell syntax is locally checked only. **M3/M2 ownership review and real Ubuntu runtime verification are still required before installation.**
- If a USBGuard rule owned by another program is broad and permits unknown Mass Storage, require explicit administrator decision instead of modifying a foreign rule invisibly.

These hardening items should be reviewed/implemented under M3/M2 ownership during pre-Ubuntu rollout; they are **not evidence of a newly reproduced Windows Week 3 failure**.

## After Week 4: original Ubuntu Gate

Retain `NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4` for clean `.deb` install, USBGuard daemon/enforcement, real flash/HID/composite devices, active local session switch, user GUI popup with correct UID, permission boundaries, service restart/reboot, A/B policy isolation, offline fail-closed, and no-serial identity. Run under the separate Ubuntu test plan and attach actual logs.
