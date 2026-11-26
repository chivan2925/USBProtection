---
name: usbshield-review
description: Dispatch same-session independent read-only review after USBShield implementation and tests, then verify findings and review the final candidate.
---

Follow [blueprint §14.4](../../../BLUEPRINT.md#144-same-session-automatic-subagent-review-owner-v24). Before commit, freeze the candidate and record baseline, exact diff (including staged, unstaged and new paths), acceptance and executed tests. Prefer staging the authorized delta so the harness can fingerprint it reproducibly.

Invoke one or more suitable read-only reviewers directly in the current task. Match the role to the delta and prefer a verified custom reviewer. Use a built-in reviewer with an explicit no-write/no-escalation brief if custom roles are unavailable. Ask each reviewer to independently confirm baseline and candidate fingerprint or exact file set before reading. Reviewers must report actionable findings with severity, path/line, consequence and supporting evidence in chat.

Wait before editing. Verify findings, correct in-scope issues, retest and request re-review of the final fingerprint. Report unresolved items and actual review mode. If native delegation or access fails, run a separate self-review and report the limitation. Stop before publication when independent review is required and unavailable. Never place a review report in the repository.
