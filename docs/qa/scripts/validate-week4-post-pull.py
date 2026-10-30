"""Validate affected post-pull checks and source equivalence without rewriting an earlier run."""
import argparse
import hashlib
import json
import platform
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--run-dir", required=True)
parser.add_argument("--baseline", required=True)
args = parser.parse_args()
evidence = (ROOT / args.run_dir).resolve()
evidence.relative_to(ROOT / "docs/qa/week4-evidence")
assert re.fullmatch(r"[0-9a-f]{40}", args.baseline)
reference = ROOT / "docs/qa/week4-evidence/2026-10-28/windows-regression"
reference_sha = "2013ce726ef8cc960b30720435be23e8ccef1903"
m4_sha = "6127766a1d3b982efa0ad0f9414ff1abc368cbb5"
assert evidence != reference, "Use a separate post-pull run directory"
now = lambda: datetime.now(timezone(timedelta(hours=7))).isoformat()
digest = lambda data: hashlib.sha256(data).hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(name, value):
    (evidence / name).write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def command(argv):
    result = subprocess.run(argv, cwd=ROOT, capture_output=True, encoding="utf-8", errors="replace",
                            creationflags=0x08000000 if sys.platform == "win32" else 0)
    assert result.returncode == 0, (argv, result.stderr)
    return (result.stdout + result.stderr).strip()


def git(*argv):
    return command(["git", *argv])


def scope_tree(revision, scope):
    entry = git("ls-tree", revision, "--", scope)
    return entry.split()[2] if entry else None


def file_record(path):
    data = path.read_bytes()
    result = {"path": path.relative_to(ROOT).as_posix(), "bytes": len(data), "sha256": digest(data)}
    if path.suffix in {".md", ".mjs", ".py"}:
        result["lfNormalizedSHA256"] = digest(data.replace(b"\r\n", b"\n"))
    return result


assert git("rev-parse", "HEAD") == args.baseline
captures = {name: read_json(evidence / f"{name}.json") for name in ["01-agent-package", "02-packaging-static"]}
for name, capture in captures.items():
    assert capture["revision"] == capture["revisionAfter"] == args.baseline
    assert capture["verdict"] == "PASS" and capture["exitCode"] == 0 and capture["sourceStable"]
    assert digest((evidence / f"{name}.txt").read_bytes()) == capture["outputSHA256"]
assert len({capture["sourceTreeSHA256"] for capture in captures.values()}) == 1
source_sha = captures["01-agent-package"]["sourceTreeSHA256"]
source = read_json(evidence / "sources" / f"{source_sha}.json")
assert digest(json.dumps(source["files"], ensure_ascii=False, separators=(",", ":")).encode()) == source_sha
for entry in source["files"]:
    assert digest((ROOT / entry["path"]).read_bytes()) == entry["sha256"], entry["path"]
assert not git("diff", "--name-only", "HEAD", "--", "frontend", "admin-server", "client-agent", "packaging", ".github")

agent = captures["01-agent-package"]
suites = [suite for suite in agent["junit"] if suite["module"] == "client-agent"]
assert len({suite["name"] for suite in suites}) == len(suites)
for suite in suites:
    report = ROOT / "client-agent/target/surefire-reports" / f"TEST-{suite['name']}.xml"
    assert digest(report.read_bytes()) == suite["xmlSHA256"]
    attributes = ET.fromstring(report.read_bytes()).attrib
    for key in ["tests", "failures", "errors", "skipped"]:
        assert int(attributes[key]) == suite[key]
totals = {key: sum(suite[key] for suite in suites) for key in ["tests", "failures", "errors", "skipped"]}
new_suite = [suite for suite in suites if suite["name"].endswith(".UsbGuardWeek4WatchStressTest")]
assert len(new_suite) == 1 and new_suite[0]["tests"] == 9
previous_tests = read_json(reference / "validation.json")["tests"]["client-agent"]["tests"]
assert totals["tests"] == previous_tests + 9 and totals["failures"] == totals["errors"] == totals["skipped"] == 0
write_json("junit-summary.json", {"revision": args.baseline, "totals": totals, "newWatchSuite": new_suite[0], "suites": suites})

jar = ROOT / "client-agent/target/usbshield-agent.jar"
built = [entry for entry in agent["buildArtifacts"] if entry["path"] == jar.relative_to(ROOT).as_posix()]
assert len(built) == 1 and digest(jar.read_bytes()) == built[0]["sha256"]
assert jar.stat().st_size == built[0]["bytes"]
with zipfile.ZipFile(jar) as archive:
    assert "BOOT-INF/classes/com/group/usbshield/agent/usbguard/UsbGuardEventListener.class" in archive.namelist()
    manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
write_json("build-manifest.json", {"revision": args.baseline, "sourceTreeSHA256": source_sha,
           "agent": {**built[0], "builtBy": "01-agent-package", "manifest": manifest},
           "staticCheck": "02-packaging-static PASS", "deb": "NOT BUILT IN THIS RUN"})

scopes = []
for scope in ["frontend", "admin-server", "packaging", ".github"]:
    old_tree = scope_tree(reference_sha, scope)
    current_tree = scope_tree(args.baseline, scope)
    assert old_tree == current_tree and not git("diff", "--name-only", reference_sha, args.baseline, "--", scope)
    scopes.append({"scope": scope, "referenceTree": old_tree, "currentTree": current_tree,
                   "status": "UNCHANGED_SOURCE" if old_tree else "NO_TRACKED_ENTRY_AT_EITHER_REVISION",
                   "execution": "Historical 28/10 result remains source-applicable; NOT RERUN on 30/10" if old_tree else "No tracked CI source or CI execution claimed"})
assert git("rev-parse", f"{m4_sha}:frontend") == git("rev-parse", f"{args.baseline}:frontend")
agent_delta = git("diff", "--name-only", reference_sha, args.baseline, "--", "client-agent").splitlines()
assert set(agent_delta) == {
    "client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardEventListener.java",
    "client-agent/src/test/java/com/group/usbshield/agent/usbguard/UsbGuardWeek4WatchStressTest.java",
}
write_json("scope-equivalence.json", {"referenceRevision": reference_sha, "currentRevision": args.baseline,
           "referenceRun": reference.relative_to(ROOT).as_posix(), "scopes": scopes,
           "agent": {"changedPaths": agent_delta, "action": "Fresh Agent clean package and JAR static validation"},
           "m4ReviewedRevision": m4_sha, "m4FrontendTreeMatches": True,
           "limits": "Source equivalence does not redate historical execution, audit, artifact hashes or screenshots; changed future delivery/config requires affected checks."})

historical_paths = ["docs/qa/week1-evidence", "docs/qa/week2-evidence", "docs/qa/week3-evidence", "docs/qa/week4-evidence/2026-10-28"]
assert not git("diff", "--name-only", "HEAD", "--", *historical_paths), "Historical evidence changed"
for name in captures:
    text = (evidence / f"{name}.txt").read_text(encoding="utf-8")
    assert not re.search(r'"(?:agentToken|enrollmentToken|password)"\s*:\s*"(?!\[REDACTED\])[^"\s]{12,}"', text, re.I)

java = Path(agent["javaHome"]) / "bin/java.exe"
write_json("environment.json", {"capturedAtBangkok": now(), "platform": platform.platform(),
           "java": command([str(java), "-version"]),
           "maven": command(["powershell.exe", "-NoProfile", "-Command", "& 'C:/maven/bin/mvn.cmd' --version"]),
           "node": command(["node", "--version"]), "python": sys.version,
           "timezone": "Asia/Bangkok", "scope": "Fresh Agent/static execution only; no browser/Ubuntu run"})

documents = [ROOT / "docs/TEST_PLAN.md", ROOT / "docs/DEMO_SCRIPT.md", ROOT / "docs/qa/week4-evidence/README.md",
             ROOT / "docs/qa/evidence/M5_WEEK4_POST_PULL_ACK_20261030.md", ROOT / "docs/qa/evidence/M4_WEEK4_HANDOFF.md",
             evidence / "README.md"] + sorted((ROOT / "docs/qa").glob("WEEK4_*.md"))
broken = []
future_outputs = {evidence / "validation.json", evidence / "checksums.json", evidence / "qa-documents.json"}
for document in documents:
    text = document.read_text(encoding="utf-8")
    for target in re.findall(r"\[[^\]]*\]\(((?:[^()]|\([^()]*\))*)\)", text):
        if target.startswith(("http:", "https:", "#", "mailto:")):
            continue
        destination = (document.parent / target.split("#", 1)[0]).resolve()
        if not destination.exists() and destination not in future_outputs:
            broken.append({"document": document.relative_to(ROOT).as_posix(), "target": target})
assert not broken, broken
documents += [ROOT / "docs/qa/scripts/capture-week4.mjs", Path(__file__).resolve()]
write_json("qa-documents.json", {"baseRevision": args.baseline,
           "scope": "Current working-tree QA documents/tools; raw and LF-normalized hashes, separate from source evidence",
           "files": [file_record(path) for path in documents]})
validation = {"status": "PASS", "validatedAtBangkok": now(), "revision": args.baseline,
           "sourceTreeSHA256": source_sha, "command": {"executable": sys.executable, "args": sys.argv, "cwd": str(ROOT)},
           "validatorSHA256": digest(Path(__file__).read_bytes()), "successfulChecks": len(captures),
           "agentTests": totals, "newWatchTests": new_suite[0]["tests"], "staticJarCheck": "PASS",
           "unchangedSourceScopes": [entry["scope"] for entry in scopes if entry["referenceTree"]],
           "absentScopesAtBothRevisions": [entry["scope"] for entry in scopes if not entry["referenceTree"]],
           "m4FrontendSourceEquivalent": True,
           "historicalEvidenceUnchanged": True, "brokenLinks": broken, "newScreenshotsOrRecording": False,
           "scope": "Affected post-pull Windows regression; final candidate/member acceptance pending; Ubuntu NOT EXECUTED/DEFERRED"}
try:
    write_json("validation.json", validation)
    write_json("checksums.json", {"algorithm": "SHA-256", "files": [file_record(path) for path in sorted(evidence.rglob("*"))
               if path.is_file() and path.name != "checksums.json"]})
    assert all(path.exists() for path in future_outputs)
except Exception as error:
    validation["status"] = "FAIL"
    validation["finalizationError"] = {"type": type(error).__name__, "message": str(error)}
    write_json("validation.json", validation)
    raise
print(json.dumps({"status": "PASS", "checks": len(captures), "agentTests": totals,
                  "newWatchTests": new_suite[0]["tests"], "sourceEquivalentScopes": len(validation["unchangedSourceScopes"]),
                  "absentScopes": validation["absentScopesAtBothRevisions"],
                  "agentJarSHA256": built[0]["sha256"]}))
