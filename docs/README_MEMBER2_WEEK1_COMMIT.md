# Member 2 Week 1 Commit Package

Copy the two top-level folders from this package into the repository root:

```text
packaging/
docs/
```

Recommended branch:

```bash
git switch develop
git pull
git switch -c feature/member2-week1-usbguard-poc
```

Then:

```bash
git add packaging/usbguard docs/usbguard docs/agent-contract docs/HANDOFF_MEMBER1_MEMBER3.md
git status
git commit -m "feat: add USBGuard and active-user week 1 PoC"
git push -u origin feature/member2-week1-usbguard-poc
```

Create a Pull Request into `develop`.

The required Week 1 evidence used by this package has been captured.

One scenario remains intentionally documented as `NOT EXECUTED`:

```text
Multiple USB Mass Storage
```

Resolver ambiguity is:

```text
PASS (SIMULATED LOGIC TEST)
```

Reproducible artifacts:

```text
docs/usbguard/active-user-poc/test-ambiguity.sh
docs/usbguard/evidence/resolver-ambiguity.txt
```

See `docs/usbguard/evidence/TODO_CAPTURE.md` for the only remaining optional case.
