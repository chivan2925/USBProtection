---
name: usbshield-implement
description: Write or refactor authorized USBShield production code using SOLID, Design Patterns, Clean Architecture and Clean Code while preserving behavior at real entry points.
---

Use this skill when changing implementation in an authorized slice. Read [BLUEPRINT.md](../../../BLUEPRINT.md), especially section 10, and the relevant module entry points in the [Developer guide](../../../docs/DEVELOPER_GUIDE.md). Use [architecture and security](../usbshield-architecture/SKILL.md) to trace trust boundaries and decisions that need approval.

1. Identify the affected use case, callers, invariants and failure behavior. Use existing tests to characterize behavior before refactoring. Add a focused test when a changed invariant has no existing coverage.
2. Keep each unit responsible for one change reason. Put business decisions in the domain or use case and let the entry point translate transport data. Bind implementations at composition. Keep Spring, JavaFX, SQL, network and OS details on the adapter side of any new boundary. Work incrementally within the existing module layout.
3. Apply SOLID where it reduces coupling. Keep interfaces narrow enough for their callers, preserve their success and failure contracts across implementations, and put abstractions at dependencies the use case needs to substitute or isolate. Add extension points for a concrete variation. Keep direct code when it already expresses the behavior clearly.
4. Choose the pattern that fits the change. Keep USBGuard CLI behind the existing gateway as an Adapter. Use a Repository port when a use case needs persistence isolation. Use Strategy for real interchangeable collectors, or an explicit state machine when retry and recovery have distinct, testable transitions. Reuse existing seams before adding more classes or packages.
5. Use names that express intent, make validation and errors explicit, minimize shared mutable state, and keep methods cohesive. Trace transaction and persistence ordering through duplicate requests, partial failure and retry. For example, preserve the policy path from snapshot validation through USBGuard rule verification and durable applied version before ACK. Keep these concerns visible where readers can follow the use case.
6. Check the relevant tests through the normal entry point and use [verification and evidence](../usbshield-verify/SKILL.md) for the candidate. Report the behavior preserved or changed, the seam chosen and the evidence in chat. Stop before implementing an unapproved design, broad refactor, schema or privilege change.
