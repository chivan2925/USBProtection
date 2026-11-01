"""Validate the M1 server integration run without changing historical Agent evidence."""
import argparse
import atexit
import hashlib
import json
import platform
import re
import struct
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
parser.add_argument("--profile", required=True)
args = parser.parse_args()
assert re.fullmatch(r"[0-9a-f]{40}", args.baseline)
evidence = (ROOT / args.run_dir).resolve()
evidence.relative_to(ROOT / "docs/qa/week4-evidence")
profile_path = (ROOT / args.profile).resolve()
profile = json.loads(profile_path.read_text(encoding="utf-8"))
digest = lambda data: hashlib.sha256(data).hexdigest()
now = lambda: datetime.now(timezone(timedelta(hours=7))).isoformat()
validation_state = {"complete": False, "error": None}


def failed_exception(kind, error, traceback):
    validation_state["error"] = {"type": kind.__name__, "message": "Evidence assertion or finalization failed; inspect validator diagnostics"}
    sys.__excepthook__(kind, error, traceback)


def mark_unfinished():
    if not validation_state["complete"]:
        (evidence / "validation.json").write_text(json.dumps({"status": "FAIL", "validatedAtBangkok": now(),
            "revision": args.baseline, "error": validation_state["error"] or {"message": "Validation did not finish"}}, indent=2) + "\n", encoding="utf-8")


sys.excepthook = failed_exception
atexit.register(mark_unfinished)
# A refresh must not leave a previous PASS if any later assertion/write fails.
(evidence / "validation.json").write_text(json.dumps({"status": "RUNNING", "revision": args.baseline}) + "\n", encoding="utf-8")


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


def record(path):
    data = path.read_bytes()
    result = {"path": path.relative_to(ROOT).as_posix(), "bytes": len(data), "sha256": digest(data)}
    if path.suffix in {".md", ".py", ".mjs", ".java"}:
        result["lfNormalizedSHA256"] = digest(data.replace(b"\r\n", b"\n"))
    return result


def tree(revision, scope):
    entry = git("ls-tree", revision, "--", scope)
    return entry.split()[2] if entry else None


assert git("rev-parse", "HEAD") == args.baseline
captures = {name: read_json(evidence / f"{name}.json") for name in profile["requiredCaptures"]}
failed = {name: read_json(evidence / f"{name}.json") for name in profile.get("failedCaptures", [])}
assert not set(captures).intersection(failed)
for name, capture in {**captures, **failed}.items():
    assert capture["revision"] == capture["revisionAfter"] == args.baseline and capture["sourceStable"]
    assert capture["sourceTreeSHA256"] == capture["sourceTreeSHA256After"]
    assert capture["verdict"] == ("FAIL" if name in failed else "PASS")
    assert (capture["exitCode"] != 0) if name in failed else (capture["exitCode"] == 0)
    assert digest((evidence / f"{name}.txt").read_bytes()) == capture["outputSHA256"]
    if capture.get("postCaptureRedaction"):
        redaction = capture["postCaptureRedaction"]
        assert redaction["redactedOutputSHA256"] == capture["outputSHA256"] and redaction["replacementCount"] > 0
        assert digest((ROOT / redaction["tool"]["path"]).read_bytes()) == redaction["tool"]["sha256"]
assert len({capture["sourceTreeSHA256"] for capture in [*captures.values(), *failed.values()]}) == 1
source_sha = captures[profile["serverCapture"]]["sourceTreeSHA256"]
source = read_json(evidence / "sources" / f"{source_sha}.json")
assert digest(json.dumps(source["files"], ensure_ascii=False, separators=(",", ":")).encode()) == source_sha
for entry in source["files"]:
    assert digest((ROOT / entry["path"]).read_bytes()) == entry["sha256"], entry["path"]
application_scopes = ["frontend", "admin-server", "client-agent", "packaging", ".github"]
assert not git("diff", "--name-only", "HEAD", "--", *application_scopes), "Application sources modified during QA"

server_capture = captures[profile["serverCapture"]]
suites = [item for item in server_capture["junit"] if item["module"] == "admin-server"]
assert len({item["name"] for item in suites}) == len(suites)
for suite in suites:
    report = ROOT / "admin-server/target/surefire-reports" / f"TEST-{suite['name']}.xml"
    assert digest(report.read_bytes()) == suite["xmlSHA256"]
    attributes = ET.fromstring(report.read_bytes()).attrib
    for key in ["tests", "failures", "errors", "skipped"]:
        assert int(attributes[key]) == suite[key]
totals = {key: sum(item[key] for item in suites) for key in ["tests", "failures", "errors", "skipped"]}
assert totals == {"tests": profile["expectedServerTests"], "failures": 0, "errors": 0, "skipped": 0}
for name, count in profile["requiredSuites"].items():
    matching = [suite for suite in suites if suite["name"].endswith("." + name)]
    assert len(matching) == 1 and matching[0]["tests"] == count, name
write_json("junit-summary.json", {"revision": args.baseline, "totals": totals, "suites": suites})

jar = ROOT / "admin-server/target/usbshield-admin-server.jar"
artifacts = [entry for entry in server_capture["buildArtifacts"] if entry["path"] == jar.relative_to(ROOT).as_posix()]
assert len(artifacts) == 1 and record(jar)["sha256"] == artifacts[0]["sha256"]
assert jar.stat().st_size == artifacts[0]["bytes"]
dist = ROOT / "frontend/dist"
asset_records = []
with zipfile.ZipFile(jar) as archive:
    assert "BOOT-INF/classes/com/group/usbshield/server/UsbShieldServerApplication.class" in archive.namelist()
    for asset in sorted(dist.rglob("*")):
        if asset.is_file():
            name = "BOOT-INF/classes/static/" + asset.relative_to(dist).as_posix()
            assert archive.read(name) == asset.read_bytes(), name
            asset_records.append({**record(asset), "jarEntry": name, "byteEqual": True})
    assert asset_records and any(item["jarEntry"].endswith("static/index.html") for item in asset_records)
    manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")

reference = profile["referenceRevision"]
comparisons = []
for scope in profile["sameSourceScopes"]:
    before, after = tree(reference, scope), tree(args.baseline, scope)
    assert before == after and not git("diff", "--name-only", reference, args.baseline, "--", scope)
    comparisons.append({"scope": scope, "referenceTree": before, "currentTree": after,
                        "status": "UNCHANGED_SOURCE" if before else "NO_TRACKED_ENTRY_AT_EITHER_REVISION"})
delta = git("diff", "--name-only", reference, args.baseline, "--", *application_scopes).splitlines()
assert set(delta) == set(profile["allowedChangedPaths"]), "Application delta requires review"
owner_manifest_path = ROOT / "docs/member1/week4-evidence/server-release-manifest.json"
owner_manifest = read_json(owner_manifest_path)
assert owner_manifest["sourceCommit"] == profile["m1ArtifactRevision"]
for scope in ["admin-server", "frontend"]:
    assert tree(owner_manifest["sourceCommit"], scope) == tree(args.baseline, scope), scope
    assert not git("diff", "--name-only", owner_manifest["sourceCommit"], args.baseline, "--", scope)
assert git("rev-parse", owner_manifest["sourceCommit"] + "^{tree}") == owner_manifest["sourceTree"]
agent_reference = read_json(ROOT / profile["agentReferenceManifest"])["agent"]
agent_jar = ROOT / agent_reference["path"]
assert record(agent_jar)["sha256"] == agent_reference["sha256"] and agent_jar.stat().st_size == agent_reference["bytes"]
write_json("scope-equivalence.json", {"referenceRevision": reference, "currentRevision": args.baseline,
           "comparisons": comparisons, "changedPaths": delta,
           "m1ArtifactSource": owner_manifest["sourceCommit"], "m1ArtifactSourceMatchesCurrentServerAndFrontend": True,
           "limits": "Source equality retains historical execution dates. Updated server integration is freshly tested; no Agent unit or Debian/native rerun inferred."})

integration = read_json(evidence / profile["integrationSummary"])
assert integration["status"] == "PASS" and integration["exitCode"] == 0 and integration["assertionCount"] > 0
assert integration["revision"] == integration["revisionAfter"] == args.baseline
assert integration["serverJar"]["sha256"] == artifacts[0]["sha256"]
assert integration["agentJar"]["sha256"] == agent_reference["sha256"]
assert integration["sourceStable"] and integration["sourceBefore"] == integration["sourceAfter"]
assert integration["javaResult"]["status"] == "PASS" and integration["javaResult"]["assertionCount"] == integration["assertionCount"]
assert integration["scenarios"] and all(item["status"] == "PASS" for item in integration["scenarios"])
assert integration["serverTermination"]["confirmed"] and integration["serverTermination"]["loopbackPortClosed"]
for key in ["harness", "runner"]:
    entry = integration[key]
    assert digest((ROOT / entry["path"]).read_bytes()) == entry["sha256"]
assert integration["logMinimization"]["status"] == "PASS"
assert all(integration["logMinimization"][key] for key in ["infoAcceptancePresent", "uniqueSyntheticMarkersAbsent", "syntheticSecretsAbsent"])

packaged = evidence / profile["packagedProvenance"]
rehearsal = read_json(packaged)
assert rehearsal["status"] == "PASS" and rehearsal["exitCode"] == 0 and rehearsal["revision"] == args.baseline
assert rehearsal["jar"]["sha256"] == artifacts[0]["sha256"]
assert rehearsal["originalAssertionLinesPreserved"] > 0 and rehearsal["reversibleToOriginalSource"]
assert rehearsal["originalSourceUnchangedAfterRun"]
for key in ["source", "variant", "wrapper"]:
    entry = rehearsal[key]
    assert digest((ROOT / entry["path"]).read_bytes()) == entry["sha256"]
cleanup = read_json(packaged.parent / "cleanup.json")
assert cleanup["terminationConfirmed"] and not cleanup["runtimeRetained"]
screenshots = []
for name, capture in captures.items():
    expected_count = profile.get("screenshotCounts", {}).get(name, 0)
    assert len(capture["screenshots"]) == expected_count, name
    for shot in capture["screenshots"]:
        path = (evidence / shot["path"]).resolve()
        path.relative_to(evidence)
        data = path.read_bytes()
        assert len(data) == shot["bytes"] and digest(data) == shot["sha256"]
        assert data[:8] == b"\x89PNG\r\n\x1a\n"
        width, height = struct.unpack(">II", data[16:24])
        assert width > 0 and height > 0
        screenshots.append({**record(path), "capture": name, "width": width, "height": height})
write_json("screenshots.json", {"count": len(screenshots), "scope": "Actual Windows browser screenshots; synthetic HTTP/H2 data", "files": screenshots})
write_json("build-manifest.json", {"revision": args.baseline, "sourceTreeSHA256": source_sha,
           "server": {**artifacts[0], "manifest": manifest, "builtBy": profile["serverCapture"]},
           "frontendAssets": asset_records, "agent": {**agent_reference, "execution": "Existing S3 JAR consumed by fresh Agent-to-server integration; Agent unit suite not rerun"},
           "m1SubmittedArtifact": owner_manifest, "m1ArtifactTreatment": "Owner artifact source matches; fresh M5 build identity remains separate", "deb": "NOT BUILT IN THIS RUN"})

assert not git("diff", "--name-only", "HEAD", "--", *profile["historicalEvidencePaths"]), "Historical evidence changed"
for name in [*captures, *failed]:
    text = (evidence / f"{name}.txt").read_text(encoding="utf-8")
    assert not re.search(r'"(?:agentToken|enrollmentToken|password)"\s*:\s*"(?!\[REDACTED\])[^"\s]{12,}"', text, re.I)
    assert not re.search(r'\b(?:agentToken|enrollmentToken|password)\s*[=:]\s*(?!\[REDACTED\])[^,\s\)"}]{12,}', text, re.I)
write_json("environment.json", {"capturedAtBangkok": now(), "platform": platform.platform(), "timezone": "Asia/Bangkok",
           "java": command([str(Path(server_capture["javaHome"]) / "bin/java.exe"), "-version"]),
           "maven": command(["powershell.exe", "-NoProfile", "-Command", "& 'C:/maven/bin/mvn.cmd' --version"]),
           "node": command(["node", "--version"]), "python": sys.version,
           "scope": "Windows build, HTTP and Chromium integration; no native Ubuntu/USB execution"})
documents = [ROOT / path for path in profile["documents"]]
future = {evidence / "validation.json", evidence / "qa-documents.json", evidence / "checksums.json"}
broken = []
for document in documents:
    for target in re.findall(r"\[[^\]]*\]\(((?:[^()]|\([^()]*\))*)\)", document.read_text(encoding="utf-8")):
        if target.startswith(("http:", "https:", "#", "mailto:")):
            continue
        destination = (document.parent / target.split("#", 1)[0]).resolve()
        if not destination.exists() and destination not in future:
            broken.append({"document": document.relative_to(ROOT).as_posix(), "target": target})
assert not broken, broken
tools = [ROOT / path for path in profile["qaTools"]] + [Path(__file__).resolve(), profile_path]
write_json("qa-documents.json", {"baseRevision": args.baseline, "files": [record(path) for path in [*documents, *tools]]})
validation = {"status": "PASS", "validatedAtBangkok": now(), "revision": args.baseline, "sourceTreeSHA256": source_sha,
              "command": {"executable": sys.executable, "args": sys.argv, "cwd": str(ROOT)}, "validatorSHA256": digest(Path(__file__).read_bytes()),
              "successfulCaptures": len(captures), "retainedFailedCaptures": list(failed), "serverTests": totals,
              "frontendAssetsByteEqual": len(asset_records), "agentServerAssertions": integration["assertionCount"],
              "logMinimization": "PASS", "browserScreenshots": len(screenshots), "packagedBrowser": "PASS",
              "historicalEvidenceUnchanged": True, "brokenLinks": broken, "newRecording": rehearsal["recordingRequested"],
              "scope": "Current Windows technical verification only; group owner decisions, credential rotation and Ubuntu gates remain separate"}
write_json("validation.json", validation)
write_json("checksums.json", {"algorithm": "SHA-256", "files": [record(path) for path in sorted(evidence.rglob("*"))
           if path.is_file() and path.name != "checksums.json"]})
validation_state["complete"] = True
print(json.dumps({"status": "PASS", "captures": len(captures), "serverTests": totals,
                  "agentServerAssertions": integration["assertionCount"], "screenshots": len(screenshots),
                  "frontendAssetsByteEqual": len(asset_records), "serverJarSHA256": artifacts[0]["sha256"]}))
