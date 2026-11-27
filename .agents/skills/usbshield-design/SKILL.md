---
name: usbshield-design
description: Design, implement or review Central Admin Desktop JavaFX UI, including FXML, CSS, interaction states, accessibility, localization and native Windows visual acceptance.
---

Read the [Desktop design contract](../../../admin-desktop/DESIGN.md), [Admin guide](../../../docs/ADMIN_GUIDE.md) and affected FXML/controller code. For a new design direction, consult [upstream references](references/upstream.md) as appropriate.

1. Define the operator task, current entry point, data scope and intended result. Inspect the existing screen and styles. For a new flow, sketch hierarchy, primary action, navigation and how it reads in loading, empty, error, pending and stale states.
2. Use FXML and JavaFX CSS, existing UI patterns, local resources and standard controls. Keep identity and policy state comprehensible. Separate desired, acknowledged and actual data. Keep network/storage work off the FX thread. Discuss material product or workflow changes with Owner.
3. Run `python -B admin-desktop/tools/check_design.py` from the repo root. For affected UI paths run `mvn -f admin-desktop/pom.xml test` with JDK 21 on Windows, and extend focused FXML coverage if the existing paths cannot catch the regression.
4. Inspect a real Windows render with synthetic data at default and minimum sizes, and at 100% and 150% scale as available. Check keyboard and visible focus, long Vietnamese strings, contrast, disabled/busy and error states, and target identity in confirmations. Capture relevant synthetic visuals when useful. Record each unavailable check as NOT EXECUTED.
5. Hand the frozen delta, changed screens, scenario, executed checks and remaining native evidence to the same-session reviewer. Use the design contract and [review skill](../usbshield-review/SKILL.md) to rank by impact on operator tasks. Stop before claiming accessibility, visual or native acceptance without relevant evidence.

Output the focused FXML/CSS/Java changes plus any contract or guide changes made necessary by the delta. Keep screenshots with synthetic data and review evidence local or in chat. Design tooling introduces no application dependency.
