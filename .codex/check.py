"""Validate an explicitly staged USBShield candidate without changing Git or files."""

import argparse
import hashlib
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
from urllib.parse import unquote
import xml.etree.ElementTree as ET

from guard import publication_context, secret_path

H0_REF = "da15f1e9c078ec62d3394d421091ee84ebaf8213"
H0_BLUEPRINT = "c62d23c11b98ea01ebf84d43c124ba44eee951f0d5b1a63c14707ae39bf3a6b3"
OLD_BLUEPRINT = "USBShield_Blueprint_Allow_Revoke_Reinsert_20261103.md"
FIXTURE = "docs/agent-contract/sample-usb-event.json"
H0_REFS = {
    "packaging/scripts/build-deb.ps1": "chivan2925/USBProtection",
    "packaging/systemd/usbshield-agent.service": "chivan2925/USBProtection",
}
H0_SCOPE = ("AGENTS.md", "README.md", "BLUEPRINT.md", OLD_BLUEPRINT,
            "docs/", "diagrams/", ".agents/skills/", ".codex/", "frontend/README.md",
            *H0_REFS)
H0_GUIDES = {"docs/" + name + ".md" for name in
             ("ARCHITECTURE", "IT_SETUP_GUIDE", "ADMIN_GUIDE", "DEVELOPER_GUIDE", "API")}
H0_DIAGRAMS = {"diagrams/usb-insert.svg", "diagrams/policy-sync.svg"}
LINK = re.compile(r"\]\((?:<([^>]+)>|([^)\s]+))(?:\s+\"[^\"]*\")?\)")
SECRET_CONTENT = re.compile(
    rb"-----BEGIN [A-Z ]*PRIVATE KEY-----|"
    rb"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[A-Z0-9]{16})\b"
)


def git(*args):
    result = subprocess.run(["git", *args], check=True, capture_output=True)
    return result.stdout


def names(*args):
    return set(git(*args).decode("utf-8").rstrip("\0").split("\0")) - {""}


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def within_scope(path, scope):
    return any(path == item or (item.endswith("/") and path.startswith(item))
               for item in scope)


def index_blob(path):
    return git("show", ":" + path)


def check_links(path, body, index):
    fenced = False
    for number, line in enumerate(body.splitlines(), 1):
        if line.lstrip().startswith(("\x60\x60\x60", "~~~")):
            fenced = not fenced
        if fenced:
            continue
        for match in LINK.finditer(line):
            target = (match[1] or match[2]).split("#", 1)[0]
            if not target or re.match(r"^(?:https?://|mailto:)", target):
                continue
            parent = PurePosixPath(path).parent
            normalized = []
            for part in (parent / unquote(target)).parts:
                if part == "..":
                    require(bool(normalized), f"Link escapes repo at {path}:{number}")
                    normalized.pop()
                else:
                    normalized.append(part)
            dest = "/".join(normalized)
            require(dest in index or any(p.startswith(dest + "/") for p in index),
                    f"Missing indexed link target at {path}:{number}: {target}")


def check_h0(delta, index):
    blueprint = index_blob("BLUEPRINT.md")
    require(hashlib.sha256(blueprint).hexdigest() == H0_BLUEPRINT,
            "Indexed blueprint must match the Owner attachment exactly.")
    require(OLD_BLUEPRINT not in index, "Old blueprint is still tracked.")
    require(FIXTURE not in delta and FIXTURE in index, "Retain the exact shared fixture.")
    require({p for p in index if p.startswith("docs/")} == H0_GUIDES | {FIXTURE},
            "H0 docs inventory differs from approved living guides and fixture.")
    require({p for p in index if p.startswith("diagrams/")} == H0_DIAGRAMS,
            "Expected exactly the two H0 diagrams.")
    agents = index_blob("AGENTS.md")
    require(len(agents) <= 8192 and len(agents.splitlines()) <= 120,
            "AGENTS.md exceeds the instruction budget.")
    for path in H0_DIAGRAMS:
        body = index_blob(path)
        diagram = ET.fromstring(body)
        require(diagram.tag == "{http://www.w3.org/2000/svg}svg" and
                b"<script" not in body and b"@import" not in body,
                f"Expected standalone SVG without scripts or font imports: {path}")
    for path, old in H0_REFS.items():
        before = git("show", f"{H0_REF}:{path}")
        expected = before.replace(old.encode(), b"chivan2925/USBShield")
        require(index_blob(path) == expected, f"Only canonical documentation URL may change: {path}")


def check_candidate(baseline, scope, h0=False, reviewed=None):
    if h0:
        require(baseline == H0_REF, "Unexpected H0 baseline.")
    require(git("rev-parse", "HEAD").decode().strip() == baseline,
            "HEAD must match the reviewed baseline.")
    require(publication_context(Path.cwd()), "Expected vinhkhang and both canonical origin URLs.")
    require(not names("diff", "--name-only", "-z"), "Unstaged tracked changes exist.")
    require(not names("ls-files", "--others", "--exclude-standard", "-z"),
            "Untracked paths exist.")
    delta = names("diff", "--cached", "--name-only", "--no-renames", "-z")
    index = names("ls-files", "-z")
    require(bool(delta), "Stage the authorized candidate first.")
    for path in sorted(delta):
        require(not secret_path(path), f"Candidate includes a secret path: {path}")
        require(within_scope(path, scope), f"Path outside authorized scope: {path}")
        if path not in index:
            continue
        blob = index_blob(path)
        require(not SECRET_CONTENT.search(blob), f"Possible secret in indexed content: {path}")
        if path.endswith(".md"):
            check_links(path, blob.decode("utf-8"), index)
    if h0:
        check_h0(delta, index)
        git("diff", "--cached", "--check", "--", ".", ":(exclude)BLUEPRINT.md")
        # The exact attachment contains intentional Markdown hard breaks.
        git("-c", "core.whitespace=-blank-at-eol", "diff", "--cached", "--check", "--", "BLUEPRINT.md")
    else:
        git("diff", "--cached", "--check")
    patch = git("-c", "core.quotePath=true", "diff", "--cached", "--binary", "--full-index",
                "--no-renames", "--no-ext-diff", "--no-textconv", "--no-color",
                "--diff-algorithm=myers", "--src-prefix=a/", "--dst-prefix=b/")
    fingerprint = hashlib.sha256(patch).hexdigest()
    if reviewed is not None:
        require(fingerprint == reviewed, "Candidate changed since review.")
    print(f"PASS staged scope and static integrity ({len(delta)} paths)")
    print(f"candidate-sha256 {fingerprint}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True, help="Exact approved baseline SHA")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--h0", action="store_true", help="Apply the Owner-approved H0 scope")
    mode.add_argument("--scope", action="append", help="Exact file or directory prefix ending in /; repeat")
    parser.add_argument("--reviewed-fingerprint", help="Require the independently reviewed digest")
    args = parser.parse_args()
    try:
        check_candidate(args.baseline, H0_SCOPE if args.h0 else args.scope,
                        args.h0, args.reviewed_fingerprint)
    except (ValueError, OSError, ET.ParseError, subprocess.CalledProcessError) as error:
        if isinstance(error, subprocess.CalledProcessError):
            # Git errors may carry lines of a patch. Do not echo possible secret content.
            print("FAIL Git check. Inspect command status locally without exposing patch content.",
                  file=sys.stderr)
        else:
            print(f"FAIL {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
