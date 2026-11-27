---
name: usbshield-architecture
description: Trace or change USBShield architecture, refactors, authentication, tenant boundaries, USBGuard, notifier or policy sync under an approved slice.
---

Read the relevant sections in [blueprint](../../../BLUEPRINT.md). Start at the real module entry point in the [Developer guide](../../../docs/DEVELOPER_GUIDE.md), then follow request, transaction, port, adapter and persistence paths. Record what the source and tests prove separately from planned design.

When authorized to implement, preserve existing behavior with targeted contract checks before changing dependencies. Keep central policy authority, Agent enforcement and notifier separate. For new workspace work, trace authorization through queries, lists and counts, and check Agent credential binding. Trace desired, applied, pending ACK and actual block separately through retry and crash cases. Review privilege and data collection at the native boundary. Keep tests synthetic.

Output a bounded plan or implementation and evidence in chat. Stop if the change requires an unapproved ADR, DB mutation, privilege change or expanded scope.
