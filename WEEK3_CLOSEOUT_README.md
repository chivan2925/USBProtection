# USBShield — Week 3 final closeout (direct file patch)

**Final decision 2026-10-28:** `WEEK 3 — PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`, H05 CLOSED by explicit owner authorization. This supersedes the earlier PENDING text below; see `docs/qa/WEEK3_FINAL_OWNER_CLOSEOUT_20261028.md`. The follow-up package consists entirely of ready-to-overwrite Markdown files; **no scripts are necessary to apply it**.


**Baseline:** `origin/main` at `5f32d415fc5a5ef6fb8e7b4d1867041eb4d05487`.

**Installation:** Extract this ZIP into the repository ROOT, preserving paths and overwriting only the listed files. **There is no update/apply script to run**. All QA updates have already been applied directly in the included files.

## Contents

- Original attached Blueprint restored at root.
- M5 QA summary/inventory/index and M4 handoff aligned to H02 DONE and M5-W3-07 RESOLVED.
- Outdated review snapshots marked explicitly HISTORICAL, without replacing their contents.
- M3 closeout addendum documents installer change and limitations.
- `postinst` updated to fail closed on ambiguous USBGuard implicit policy settings; this is a packaged installer source file, **not** a patch-application script.
- H05 closeout checklist and credential/`.deb` evidence follow-up remain truthful and PENDING.

## Validation

Run relevant Windows Maven/npm and packaging checks from `docs/qa/WEEK3_FINAL_OWNER_CLOSEOUT_PENDING.md` and report their output. `bash -n packaging/agent-deb/DEBIAN/postinst` is a **syntax check only**, not an installer invocation.

No new Maven/npm/Ubuntu tests are claimed by this ZIP. Ubuntu physical USB, systemd, desktop notifier, reboot and actual `.deb` installation tests remain deferred until after Week 4. H05 needs explicit group sign-off, never set automatically.
