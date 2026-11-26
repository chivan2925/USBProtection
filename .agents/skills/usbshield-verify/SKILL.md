---
name: usbshield-verify
description: Verify USBShield candidates with source-grounded tests and record exact native, simulated and static evidence.
---

Use the slice acceptance, baseline and candidate paths as input. Select existing entry-point tests and test environments from the [Developer guide](../../../docs/DEVELOPER_GUIDE.md) and [harness](../../../.codex/HARNESS.md). For H0, check the retained fixture with its current consumers and run the harness checks. Observe actual output and exit status. Keep product acceptance distinct from documentation, packaging, unit and fixture checks.

Report environment, commands, outcome, SHA/fingerprint and observed limit in chat. Record skipped/unavailable/historical evidence explicitly. Stop before publication on a failed required gate or an unreviewed change. A Windows simulation cannot show actual Ubuntu USBGuard behavior. DB tests target explicitly configured isolated test DBs only.
