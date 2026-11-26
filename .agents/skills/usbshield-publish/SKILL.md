---
name: usbshield-publish
description: Publish an accepted USBShield slice by exact-candidate commit and normal push to the authorized vinhkhang remote.
---

Require Owner authorization for both the slice and publication, a verified baseline, scope, test and same-session review evidence. Run the staged candidate gate in the [harness](../../../.codex/HARNESS.md) and compare its fingerprint with the reviewed diff. Check current branch, repository identity, both origin URLs and push permission. Refresh and compare upstream against the approved baseline before commit and again before push.

Commit only the verified index. Inspect the new commit, verify clean state and ensure only the expected commit(s) are ahead. Use a normal `git push origin vinhkhang`, then run `git ls-remote origin refs/heads/vinhkhang` or query the exact GitHub branch to confirm publication. Stop on unexpected changes, failed gates, divergence or operational blockers. Report local and remote SHA with actual outcomes. Owner handles PR/merge, and later slices need separate authorization.
