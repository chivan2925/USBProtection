"""Verify captured evidence and packaged assets; never run application tests or install packages."""
import hashlib
import json
import platform
import re
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
EVIDENCE = ROOT / "docs/qa/week4-evidence/2026-10-28/windows-regression"
BASELINE = "2013ce726ef8cc960b30720435be23e8ccef1903"
REQUIRED = [
    "02-agent-package", "03-shell-builder", "04-shell-postinst", "05-shell-prerm",
    "06-shell-notifier", "07-npm-ci-registry", "08-packaging-static", "09-staging-probes",
    "10-frontend-tests", "11-frontend-lint", "12-npm-audit", "13-frontend-build",
    "14-server-with-frontend", "15-browser-fixture", "16-browser-management",
    "17-policy-http", "18-browser-events", "19-browser-packaged-rehearsal",
]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def command(argv):
    result = subprocess.run(argv, cwd=ROOT, capture_output=True, text=True,
                            encoding="utf-8", errors="replace", creationflags=0x08000000)
    assert result.returncode == 0, (argv, result.stderr)
    return (result.stdout + result.stderr).strip()


def file_record(path):
    contents = path.read_bytes()
    return {"path": path.relative_to(ROOT).as_posix(), "bytes": len(contents), "sha256": sha(contents)}


started = datetime.now(timezone(timedelta(hours=7))).isoformat()
head = command(["C:/Program Files/Git/cmd/git.exe", "rev-parse", "HEAD"])
assert head == BASELINE, "HEAD changed: select the actual candidate and review affected reruns"
records = {name: json.loads((EVIDENCE / f"{name}.json").read_text(encoding="utf-8")) for name in REQUIRED}
for name, record in records.items():
    assert record["verdict"] == "PASS" and record["exitCode"] == 0, name
    assert record["sourceStable"] and record["revision"] == BASELINE, name
    assert record["revisionAfter"] == BASELINE, name
    assert record["outputSHA256"] == sha((EVIDENCE / f"{name}.txt").read_bytes()), name
source_digests = {record["sourceTreeSHA256"] for record in records.values()}
assert len(source_digests) == 1, "Commands used different application source trees"
source_digest = next(iter(source_digests))
source_manifest = json.loads((EVIDENCE / "sources" / f"{source_digest}.json").read_text(encoding="utf-8"))
for entry in source_manifest["files"]:
    assert sha((ROOT / entry["path"]).read_bytes()) == entry["sha256"], entry["path"]
assert sha(json.dumps(source_manifest["files"], ensure_ascii=False, separators=(",", ":")).encode()) == source_digest

offline = json.loads((EVIDENCE / "01-npm-ci.json").read_text(encoding="utf-8"))
assert offline["exitCode"] == 1 and "ENOTCACHED" in (EVIDENCE / "01-npm-ci.txt").read_text(encoding="utf-8")
assert offline["outputSHA256"] == sha((EVIDENCE / "01-npm-ci.txt").read_bytes())

tests = {}
for module, name in [("client-agent", "02-agent-package"), ("admin-server", "14-server-with-frontend")]:
    suites = [suite for suite in records[name]["junit"] if suite["module"] == module]
    for suite in suites:
        xml = ROOT / module / "target/surefire-reports" / f"TEST-{suite['name']}.xml"
        assert sha(xml.read_bytes()) == suite["xmlSHA256"], suite["name"]
        attributes = ET.fromstring(xml.read_bytes()).attrib
        for key in ["tests", "failures", "errors", "skipped"]:
            assert int(attributes[key]) == suite[key]
    totals = {key: sum(suite[key] for suite in suites) for key in ["tests", "failures", "errors", "skipped"]}
    assert suites and totals["failures"] == totals["errors"] == totals["skipped"] == 0
    tests[module] = {"capture": name, "totals": totals, "suites": suites}
frontend_log = (EVIDENCE / "10-frontend-tests.txt").read_text(encoding="utf-8")
assert re.search(r"(?:#|ℹ) pass 96\b", frontend_log) and re.search(r"(?:#|ℹ) fail 0\b", frontend_log)
policy_log = (EVIDENCE / "17-policy-http.txt").read_text(encoding="utf-8")
assert re.search(r"(?:#|ℹ) pass 1\b", policy_log) and re.search(r"(?:#|ℹ) fail 0\b", policy_log)
tests["frontend"] = {"capture": "10-frontend-tests", "tests": 96, "passed": 96, "failed": 0}
tests["policy-http"] = {"capture": "17-policy-http", "tests": 1, "passed": 1, "failed": 0}
write_json(EVIDENCE / "junit-summary.json", tests)

screenshots = []
for name in ["15-browser-fixture", "16-browser-management", "18-browser-events", "19-browser-packaged-rehearsal"]:
    record = records[name]
    assert record["screenshots"], f"No fresh screenshots for {name}"
    for shot in record["screenshots"]:
        path = EVIDENCE / shot["path"]
        assert sha(path.read_bytes()) == shot["sha256"]
        modified = datetime.fromisoformat(shot["modifiedAt"].replace("Z", "+00:00"))
        assert datetime.fromisoformat(record["startedAt"].replace("Z", "+00:00")) <= modified
        assert modified <= datetime.fromisoformat(record["endedAt"].replace("Z", "+00:00")) + timedelta(seconds=2)
        screenshots.append({**shot, "ownerCapture": name, "scope": record["scope"]})
write_json(EVIDENCE / "screenshots.json", screenshots)

jar = ROOT / "admin-server/target/usbshield-admin-server.jar"
agent_jar = ROOT / "client-agent/target/usbshield-agent.jar"
frontend_dist = ROOT / "frontend/dist"
assets = []
with zipfile.ZipFile(jar) as archive:
    for path in sorted(frontend_dist.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(frontend_dist).as_posix()
        embedded = f"BOOT-INF/classes/static/{relative}"
        assert archive.read(embedded) == path.read_bytes(), embedded
        assets.append({**file_record(path), "jarEntry": embedded, "matchesPackagedBytes": True})
    server_manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
with zipfile.ZipFile(agent_jar) as archive:
    agent_manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
artifacts = {
    "sourceRevision": BASELINE, "sourceTreeSHA256": source_digest,
    "server": {**file_record(jar), "manifest": server_manifest, "builtBy": "14-server-with-frontend"},
    "agent": {**file_record(agent_jar), "manifest": agent_manifest, "builtBy": "02-agent-package"},
    "frontendAssets": assets,
    "deb": {"status": "NOT BUILT IN THIS RUN", "scope": "Windows static/synthetic staging checks are not a Debian build or installation"},
}
for module, artifact, capture in [("server", jar, "14-server-with-frontend"), ("agent", agent_jar, "02-agent-package")]:
    modified = datetime.fromtimestamp(artifact.stat().st_mtime, timezone.utc)
    assert datetime.fromisoformat(records[capture]["startedAt"].replace("Z", "+00:00")) <= modified
    assert modified <= datetime.fromisoformat(records[capture]["endedAt"].replace("Z", "+00:00"))
    artifacts[module]["modifiedAt"] = modified.isoformat()
    artifacts[module]["withinSuccessfulBuildInterval"] = True
    artifacts[module]["attributionLimit"] = "Hash captured at validation; attribution uses successful build, stable source and artifact write time. Server hash is also checked against browser rehearsal provenance."
write_json(EVIDENCE / "build-manifest.json", artifacts)

recordings = []
recording_attempts = []
for directory in sorted(EVIDENCE.glob("recording*")):
    provenance = json.loads((directory / "provenance.json").read_text(encoding="utf-8"))
    recording_attempts.append({"path": directory.relative_to(EVIDENCE).as_posix(), "status": provenance["status"],
                               "exitCode": provenance.get("exitCode")})
    if provenance["status"] != "PASS" or provenance.get("exitCode") != 0:
        continue  # Preserve preparation/failure attempts; do not promote them to acceptance.
    assert records["19-browser-packaged-rehearsal"]["startedAt"] <= provenance["startedAt"]
    assert provenance["endedAt"] <= records["19-browser-packaged-rehearsal"]["endedAt"]
    assert provenance["revision"] == BASELINE and provenance["jar"]["sha256"] == sha(jar.read_bytes())
    assert provenance["originalSourceUnchangedAfterRun"] is True
    for key in ["source", "variant", "wrapper"]:
        assert sha((ROOT / provenance[key]["path"]).read_bytes()) == provenance[key]["sha256"], key
    original_bytes = (ROOT / provenance["source"]["path"]).read_bytes()
    transformed = original_bytes.decode().replace("\r\n", "\n")
    for substitution in provenance["substitutions"]:
        assert transformed.count(substitution["before"]) == substitution["count"] == 1
        transformed = transformed.replace(substitution["before"], substitution["after"], 1)
    generated = (ROOT / provenance["variant"]["path"]).read_bytes().decode().replace("\r\n", "\n")
    assert transformed == generated, "Undocumented browser variant change"
    assertion_lines = [line for line in original_bytes.decode().splitlines() if re.search(r"\bassert\.", line)]
    assert len(assertion_lines) == provenance["originalAssertionLinesPreserved"]
    assert all(line in generated for line in assertion_lines)
    frames = json.loads((directory / "frames.json").read_text(encoding="utf-8"))
    assert frames["status"] == "CAPTURED" and frames["frameCount"] > 0
    assert not frames["errors"], frames["errors"]
    assert frames["frameCount"] == len(frames["frames"]) <= frames["limits"]["maxFrames"]
    assert frames["totalBytes"] == sum(frame["bytes"] for frame in frames["frames"]) <= frames["limits"]["maxTotalBytes"]
    for frame in frames["frames"]:
        data = (directory / frame["file"]).read_bytes()
        assert sha(data) == frame["sha256"]
        assert len(data) == frame["bytes"] <= frames["limits"]["maxFrameBytes"]
    assert provenance["recordingResult"]["frameCount"] == frames["frameCount"]
    assert provenance["recordingResult"]["totalBytes"] == frames["totalBytes"]
    cleanup = json.loads((directory / "cleanup.json").read_text(encoding="utf-8"))
    assert cleanup["terminationConfirmed"] and not cleanup["runtimeRetained"]
    assert (directory / "replay.html").exists()
    recordings.append({"path": directory.relative_to(EVIDENCE).as_posix(), "status": frames["status"],
                       "frames": frames["frameCount"], "bytes": frames["totalBytes"],
                       "durationMs": frames["durationMs"], "capped": frames["capped"],
                       "scope": frames["scope"]})
assert recordings, "No actual browser screencast was captured"

audit = json.loads((EVIDENCE / "12-npm-audit.txt").read_text(encoding="utf-8"))
assert audit["metadata"]["vulnerabilities"]["total"] == 0
environment = {
    "capturedAtBangkok": started, "platform": platform.platform(), "architecture": platform.machine(),
    "java": command(["C:/Program Files/Java/jdk-22/bin/java.exe", "-version"]),
    "maven": command(["powershell.exe", "-NoProfile", "-Command", "& 'C:/maven/bin/mvn.cmd' --version"]),
    "node": command(["C:/Program Files/nodejs/node.exe", "--version"]),
    "npm": json.loads(Path("C:/Program Files/nodejs/node_modules/npm/package.json").read_text())["version"],
    "chromium": command(["powershell.exe", "-NoProfile", "-Command", "(Get-Item -LiteralPath 'C:/Program Files/Google/Chrome/Application/chrome.exe').VersionInfo.ProductVersion"]),
    "timezone": "Asia/Bangkok", "sourceRevision": BASELINE, "sourceTreeSHA256": source_digest,
    "registryAudit": audit["metadata"]["vulnerabilities"],
    "privacy": "Only selected tool versions are captured; no environment secret dump or deployment accounts.",
}
write_json(EVIDENCE / "environment.json", environment)

historical_diff = command(["C:/Program Files/Git/cmd/git.exe", "diff", "--name-only", "HEAD", "--",
                           "docs/qa/week1-evidence", "docs/qa/week2-evidence", "docs/qa/week3-evidence"])
assert not historical_diff, "Historical evidence changed"
application_diff = command(["C:/Program Files/Git/cmd/git.exe", "diff", "--name-only", "HEAD", "--",
                            "frontend", "admin-server", "client-agent", "packaging"])
assert not application_diff, "Application source changed during QA"
doc_paths = [ROOT / "docs/DEMO_SCRIPT.md", ROOT / "docs/TEST_PLAN.md"] + sorted((ROOT / "docs/qa").glob("WEEK4_*.md"))
doc_paths += [ROOT / "docs/qa/week4-evidence/README.md", EVIDENCE / "README.md"]
broken_links = []
for path in doc_paths:
    content = path.read_text(encoding="utf-8")
    for target in re.findall(r"\[[^\]]*\]\(((?:[^()]|\([^()]*\))*)\)", content):
        if target.startswith(("http:", "https:", "#", "mailto:")):
            continue
        target = target.split("#", 1)[0]
        resolved_target = (path.parent / target).resolve()
        generated_at_end = {EVIDENCE / "validation.json", EVIDENCE / "checksums.json"}
        if not resolved_target.exists() and resolved_target not in generated_at_end:
            broken_links.append({"document": path.relative_to(ROOT).as_posix(), "target": target})
assert not broken_links, broken_links
qa_documents = doc_paths + sorted((ROOT / "docs/qa/scripts").glob("*week4*"))
write_json(EVIDENCE / "qa-documents.json", {"baseRevision": BASELINE,
           "scope": "Uncommitted M5 documentation and QA tools, separate from unchanged application manifest",
           "files": [file_record(path) for path in qa_documents if path.is_file()]})

# Scan only actual captured text, not application fixtures/history or hashed source manifests.
secret_pattern = re.compile(r'"(?:agentToken|enrollmentToken|password)"\s*:\s*"(?!\[REDACTED\])[^"\s]{12,}"', re.I)
for path in EVIDENCE.glob("*.txt"):
    assert not secret_pattern.search(path.read_text(encoding="utf-8")), f"Unsanitized credential pattern: {path.name}"

validation = {
    "status": "PASS", "validatedAtBangkok": datetime.now(timezone(timedelta(hours=7))).isoformat(),
    "command": {"executable": sys.executable, "args": ["docs/qa/scripts/validate-week4.py"], "cwd": str(ROOT)},
    "sourceRevision": BASELINE,
    "validatorSHA256": sha(Path(__file__).read_bytes()),
    "sourceTreeSHA256": source_digest, "successfulChecks": len(REQUIRED),
    "resolvedEnvironmentFailures": [{"capture": "01-npm-ci", "reason": "ENOTCACHED", "resolvedBy": "07-npm-ci-registry"}],
    "tests": {key: value.get("totals", {k: v for k, v in value.items() if k != "capture"}) for key, value in tests.items()},
    "screenshots": len(screenshots), "recordings": recordings, "recordingAttempts": recording_attempts,
    "packagedFrontendAssetMatches": len(assets), "historicalEvidenceUnchanged": True,
    "trackedApplicationUnchanged": True, "brokenDocumentationLinks": broken_links,
    "credentialPatternScan": "PASS for captured text; synthetic screenshot/replay data separately reviewed",
    "limits": "Windows technical checks only. Candidate/owner handoffs pending; Ubuntu E2E NOT EXECUTED/DEFERRED. No narrated or encoded video.",
}
write_json(EVIDENCE / "validation.json", validation)
files = [file_record(path) for path in sorted(EVIDENCE.rglob("*")) if path.is_file() and path.name != "checksums.json"]
write_json(EVIDENCE / "checksums.json", {"algorithm": "SHA-256", "files": files})
assert (EVIDENCE / "validation.json").is_file() and (EVIDENCE / "checksums.json").is_file()
print(json.dumps({"status": "PASS", "checks": len(REQUIRED), "tests": validation["tests"],
                  "screenshots": len(screenshots), "recordings": recordings, "assets": len(assets)}, ensure_ascii=True))
