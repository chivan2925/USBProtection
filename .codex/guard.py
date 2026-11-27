"""Advisory Codex PreToolUse guard. Never executes the inspected command."""

import json
from pathlib import PurePosixPath
import re
import subprocess
import sys

BRANCH = "vinhkhang"
ORIGIN = "https://github.com/chivan2925/USBShield.git"

GIT = r"\bgit(?:\.exe)?[\"']?\s+(?:(?:-C|-c|--git-dir|--work-tree)\s+(?:\"[^\"]+\"|'[^']+'|\S+)\s+|--\S+\s+)*"
GIT_DANGEROUS = (
    r"(?:reset\b[^\r\n;&|]*\s--hard\b|clean\b|"
    r"branch\b[^\r\n;&|]*\s(?:-D|-d|--delete)(?=\s|$)|"
    r"update-ref\b|(?:rebase|merge)(?=\s|$)|remote\s+(?:set-url|rename|remove|add)\b)"
)
GIT_PUBLICATION = GIT + r"(push|commit)\b"
RULES_PROBE_COMMAND = "codex execpolicy check --rules .codex/rules/usbshield.rules -- git reset --hard HEAD"
COMMIT_COMMAND = re.compile(
    r"""git commit(?: -m (?:'[^'&|;<>$\x60\r\n]+'|"[^"&|;<>$\x60\r\n]+"|[A-Za-z0-9._:-]+))?"""
)
SECRET_NAME = re.compile(r"^\.env(?:\..+)?$", re.IGNORECASE)
SECRET_COMMAND = re.compile(
    r"\b(?:get-content|gc|cat|type|set-content|copy-item|cp|select-string|"
    r"git\s+add|git\s+show)\b(?P<args>[^\r\n;&|]*)",
    re.IGNORECASE,
)
SECRET_ARGUMENT = re.compile(
    r"(?:^|[\s/\\'\":,])(?P<path>\.env(?:\.[\w.-]+)?|id_(?:rsa|ed25519)|[\w.-]+\.(?:key|pfx|p12))"
    r"(?=$|[\s'\",])",
    re.IGNORECASE,
)


def secret_path(path):
    name = PurePosixPath(path.replace("\\", "/")).name
    return bool(
        (SECRET_NAME.fullmatch(name) and name.lower() != ".env.example")
        or name.lower() in {"id_rsa", "id_ed25519"}
        or name.lower().endswith((".key", ".pfx", ".p12"))
    )


def publication_context(cwd):
    try:
        def git(*args):
            result = subprocess.run(
                ["git", *args], cwd=cwd, capture_output=True, text=True,
                timeout=3, check=True,
            )
            return result.stdout.splitlines()

        return (
            git("branch", "--show-current") == [BRANCH]
            and git("remote", "get-url", "--all", "origin") == [ORIGIN]
            and git("remote", "get-url", "--push", "--all", "origin") == [ORIGIN]
        )
    except (OSError, subprocess.SubprocessError):
        return False


def inspect_command(command, cwd, context_ok=publication_context):
    standalone = command.strip()
    # Codex execpolicy only evaluates its trailing command tokens as data.
    if standalone == RULES_PROBE_COMMAND:
        return None
    if re.search(GIT + GIT_DANGEROUS, command, re.IGNORECASE):
        return "Repository policy blocks this history, branch, or remote mutation."
    if re.search(r"\bgh(?:\.exe)?[\"']?\s+pr\s+(?:create|merge)\b", command, re.IGNORECASE):
        return "PR creation and merge are Owner actions."
    if re.search(
        r"\b(?:remove-item|ri)\b[^\r\n;&|]*\s-recurse\b|"
        r"\brm\b[^\r\n;&|]*\s(?:--recursive\b|-[a-z]*r[a-z]*(?=\s|$))|"
        r"\b(?:dropdb|mkfs(?:\.[a-z0-9]+)?)\b",
        command, re.IGNORECASE,
    ):
        return "Recursive deletion or destructive data command requires a separate Owner decision."
    for match in SECRET_COMMAND.finditer(command):
        if any(secret_path(item["path"]) for item in SECRET_ARGUMENT.finditer(match["args"])):
            return "Keep local secret files out of display, edits, copies, and staging."
    publication = re.search(GIT_PUBLICATION, command, re.IGNORECASE)
    if publication:
        operation = publication[1].lower()
        if operation == "push" and standalone != "git push origin vinhkhang":
            return "Use only a normal, standalone git push origin vinhkhang after approval."
        if operation == "commit" and not COMMIT_COMMAND.fullmatch(standalone):
            return "Use git commit or git commit -m MESSAGE on the reviewed index."
        if not context_ok(cwd):
            return "Commit/push requires vinhkhang and both canonical origin URLs."
    return None


def evaluate(payload, context_ok=publication_context):
    if (payload.get("hook_event_name") != "PreToolUse"
            or payload.get("tool_name") != "Bash"):
        return None
    inputs = payload.get("tool_input")
    if not isinstance(inputs, dict) or not isinstance(inputs.get("command"), str):
        return "Cannot inspect this shell tool input."
    cwd = inputs.get("workdir") or payload.get("cwd")
    if not isinstance(cwd, str) or not cwd:
        return "Cannot verify the shell working directory."
    return inspect_command(inputs["command"], cwd, context_ok)


def main():
    try:
        payload = json.load(sys.stdin)
        if not isinstance(payload, dict):
            raise ValueError("Expected hook JSON object.")
        reason = evaluate(payload)
    except (ValueError, OSError):
        reason = "Invalid hook input. Use the manual repository gate and repair the hook."
    if reason:
        print(json.dumps({"hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": reason,
        }}))


if __name__ == "__main__":
    main()
