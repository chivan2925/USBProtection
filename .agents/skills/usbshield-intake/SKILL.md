---
name: usbshield-intake
description: Start or resume an authorized USBShield slice by reconciling Owner scope, Git baseline, worktree and acceptance gates.
---

Read [repository instructions](../../../AGENTS.md), [blueprint](../../../BLUEPRINT.md) and any existing Owner authorization in the chat. Resolve both configured remote URLs, verify canonical identity, fetch and compare local/remote SHAs. Check for staged, unstaged and untracked changes. Record baseline, actor, allowed paths, tests, evidence and where to stop. On resume, confirm what the previous candidate actually contained and whether evidence still applies.

Stop for unverified remote, wrong branch, divergence, dirty worktree at intake, conflicting scope or a missing Owner decision. After finishing or blocking, report the next step in chat with actor, scope, gate and required authorization. Use a single current state outside docs only when needed for resumption.
