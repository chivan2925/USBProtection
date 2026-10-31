# QA evidence validation — 2026-10-27

Code tested at `2885cf9cfc30b841fb0e3d44d3fd281ea16350ba`; M3 implementation commit: `8beb954edbdcdfb9071c52a91d4fb82e1e8522a7`. The QA review head at this 27/10 validation was `fa19b3458fd063cdf9d59883885853759bd25fd2`, with no application-source delta from the tested revision.

- Markdown links in the current M3 follow-up, plan, inventory, QA summary, and evidence indexes: **217 checked, 0 broken**.
- QA-document working-tree diff: `git diff --check` reports no whitespace errors in the M5 QA changes.
- M3-range hygiene review: `git diff --check f80a936..HEAD` reports one non-functional trailing-whitespace line in `packaging/tests/test-staging-verifier.ps1:40`.
- Fresh execution results and scope are recorded in [README.md](README.md); no Ubuntu, `.deb`, installer, service, or reboot result is inferred.
- The M1 handoff acknowledgment was documented in the then-current QA status; its external secret-rotation/re-enrollment follow-up remains **NOT VERIFIED**. The M5-W3-07 result is superseded by the 28/10 persistence-closure evidence.
