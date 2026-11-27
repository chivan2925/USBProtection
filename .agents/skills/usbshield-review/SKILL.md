---
name: usbshield-review
description: Dispatch same-session independent read-only review after USBShield implementation and tests, then verify findings and review the final candidate.
---

Follow [blueprint §14.4](../../../BLUEPRINT.md#144-same-session-automatic-subagent-review-owner-v24). Before commit, freeze the candidate and record baseline, exact diff (including staged, unstaged and new paths), acceptance and executed tests. Prefer staging the authorized delta so the harness can fingerprint it reproducibly.

Invoke one or more suitable read-only reviewers directly in the current task. Match the role to the delta and prefer a verified custom reviewer. Use a built-in reviewer with an explicit no-write/no-escalation brief if custom roles are unavailable. Ask each reviewer to independently confirm baseline and candidate fingerprint or exact file set before reading. Reviewers must report actionable findings with severity, path/line, consequence and supporting evidence in chat.

For changed code and tests, check proportional use of SOLID, Design Patterns, Clean Architecture and Clean Code. Trace the real entry point and dependency direction. Look for mixed reasons to change, wrong ownership of invariants, needless coupling, error/retry ambiguity and patterns that obscure the simple path. Preserve behavior and failure coverage at stable contracts. Treat a style or abstraction preference as actionable only when you can show a concrete correctness, testability, ownership or maintenance cost. Review only the candidate and dependencies needed to understand it.

For a desktop UI delta, include the design contract, operator task, native render evidence, states and keyboard checks from the [JavaFX design skill](../usbshield-design/SKILL.md). Report missing visual evidence separately.

Wait before editing. Verify findings, correct in-scope issues, retest and request re-review of the final fingerprint. Use at most three review rounds per authorized slice, counting the initial review. A batch of reviewers evaluating the same frozen candidate together is one round. Carry the count when resuming. Stop once the exact final candidate is accepted. If the third round leaves a blocker or requires a material correction, stop before publication and bring the decision to Owner.

Report the round count, mode, unresolved items and verdict in chat. If native delegation or access fails, run a separate self-review and report the limitation. Stop before publication when independent review is required and unavailable. Keep review evidence in chat.
