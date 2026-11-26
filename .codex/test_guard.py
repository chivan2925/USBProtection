"""Synthetic tests. Command strings are input data and never run."""

import subprocess
import sys
from pathlib import Path
from unittest import TestCase, main
from unittest.mock import patch
import json

from check import check_links, within_scope
from guard import evaluate, inspect_command, publication_context, secret_path

ROOT = Path(__file__).resolve().parent


class GuardTest(TestCase):
    def test_allowed(self):
        for command in ("git status --short", "git fetch origin", "Get-Content .env.example",
                        "git merge-base --is-ancestor HEAD origin/vinhkhang",
                        "git commit", "git commit -m test",
                        "git commit -m 'chore: update docs'", "git push origin vinhkhang"):
            with self.subTest(command=command):
                self.assertIsNone(inspect_command(command, ".", lambda _: True))

    def test_denied_command_inputs(self):
        for command in (
            "git push origin main", "git push origin +vinhkhang", "git push origin vinhkhang --force",
            "git -C 'some checkout' push origin --force vinhkhang", "git push origin :main",
            "git -C other commit -m witness", "Set-Location other; git commit -m witness",
            "git commit --amend", "git commit -n -m witness", "git commit -am witness",
            "git commit -m witness README.md", "git commit --include README.md -m witness",
            "git merge other", "git reset --hard HEAD", "git clean -fd", "git branch -D vinhkhang",
            "git -C 'some checkout' branch --delete vinhkhang", "git remote set-url origin test",
            "Remove-Item -LiteralPath docs -Recurse", "rm -rf docs", "gh pr create",
            "Get-Content .env", "type config/.env.e2e.local", "git add server.key",
            "Get-Content .env.example,.env", "cat .env.example .env",
        ):
            with self.subTest(command=command):
                self.assertIsNotNone(inspect_command(command, ".", lambda _: True))

    def test_documented_rules_probe_only_inspects_git_tokens(self):
        command = "codex execpolicy check --rules .codex/rules/usbshield.rules -- git reset --hard HEAD"
        self.assertIn(command, (ROOT / "HARNESS.md").read_text(encoding="utf-8"))
        self.assertIsNone(inspect_command(command, "."))
        self.assertIsNotNone(inspect_command(command + "; git status", "."))
        self.assertIsNotNone(inspect_command("git reset --hard HEAD", "."))

    def test_publication_requires_context(self):
        for command in ("git commit -m test", "git push origin vinhkhang"):
            self.assertIsNotNone(inspect_command(command, ".", lambda _: False))
        self.assertIsNone(inspect_command("git status", ".", lambda _: False))

    def test_context_checks_both_urls_and_branch(self):
        origin = "https://github.com/chivan2925/USBShield.git"
        for branch, fetch, push, allowed in (
            ("vinhkhang", origin, origin, True), ("main", origin, origin, False),
            ("vinhkhang", origin, "https://example.invalid/USBShield.git", False),
            ("vinhkhang", origin, origin + "\nother", False),
        ):
            with self.subTest(branch=branch, fetch=fetch, push=push):
                with patch("guard.subprocess.run", side_effect=[
                    subprocess.CompletedProcess([], 0, value + "\n") for value in (branch, fetch, push)
                ]):
                    self.assertEqual(publication_context("."), allowed)

    def test_workdir_and_native_output(self):
        payload = {"hook_event_name": "PreToolUse", "tool_name": "Bash", "cwd": "/root",
                   "tool_input": {"command": "git push origin vinhkhang", "workdir": "/other"}}
        self.assertIsNotNone(evaluate(payload, lambda cwd: cwd == "/root"))
        payload["tool_input"]["command"] = "git reset --hard HEAD"
        output = subprocess.run([sys.executable, "-B", str(ROOT / "guard.py")],
                                input=json.dumps(payload), capture_output=True, check=True, text=True)
        self.assertEqual(json.loads(output.stdout)["hookSpecificOutput"]["permissionDecision"], "deny")


class CandidateTest(TestCase):
    def test_scope_and_secret_paths(self):
        self.assertTrue(within_scope("docs/ARCHITECTURE.md", {"docs/"}))
        self.assertFalse(within_scope("docs-outside/file.md", {"docs/"}))
        self.assertFalse(within_scope("README.md.local", {"README.md"}))
        self.assertTrue(secret_path("local/.env.test"))
        self.assertTrue(secret_path("local/lan.pfx"))
        self.assertFalse(secret_path(".env.example"))

    def test_relative_link_resolves_only_to_index(self):
        check_links("docs/guide.md", "[code](../src/main.py#L5)", {"src/main.py"})
        with self.assertRaisesRegex(ValueError, "Missing indexed link"):
            check_links("docs/guide.md", "[code](../src/missing.py)", {"src/main.py"})
        with self.assertRaisesRegex(ValueError, "escapes"):
            check_links("docs/guide.md", "[code](../../secret.txt)", {"src/main.py"})


if __name__ == "__main__":
    main()
