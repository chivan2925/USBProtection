"""Run QA source against the built Agent JAR; no application edits, Maven or downloads."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile
from datetime import datetime, timedelta, timezone

ROOT = Path(__file__).resolve().parents[3]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--run-dir", required=True)
parser.add_argument("--summary-name", default="http-transport-summary.json")
args = parser.parse_args()
evidence_root = (ROOT / "docs/qa/week4-evidence").resolve()
evidence = (ROOT / args.run_dir).resolve()
evidence.relative_to(evidence_root)
assert evidence != evidence_root, "Choose a run-specific evidence directory"
evidence.mkdir(parents=True, exist_ok=True)
assert re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*\.json", args.summary_name), "Summary name must be a plain JSON filename"
summary_path = evidence / args.summary_name
assert not summary_path.exists(), "Existing HTTP summary; choose a fresh run directory"
jar = ROOT / "client-agent/target/usbshield-agent.jar"
harness = ROOT / "docs/qa/scripts/AgentHttpTransportSmoke.java"
digest = lambda data: hashlib.sha256(data).hexdigest()
flags = 0x08000000 if sys.platform == "win32" else 0
git_executable = "C:/Program Files/Git/cmd/git.exe" if sys.platform == "win32" else "git"


def git(*argv):
    result = subprocess.run([git_executable, *argv], cwd=ROOT, capture_output=True, text=True, creationflags=flags)
    assert result.returncode == 0, "Git provenance check failed"
    return result.stdout.strip()


def record(path):
    data = path.read_bytes()
    return {"path": path.relative_to(ROOT).as_posix(), "bytes": len(data), "sha256": digest(data)}


def timestamp():
    return datetime.now(timezone.utc).isoformat()


result = {
    "status": "RUNNING", "revision": git("rev-parse", "HEAD"),
    "startedAt": timestamp(), "startedAtBangkok": datetime.now(timezone(timedelta(hours=7))).isoformat(),
    "jar": record(jar), "harness": record(harness), "runner": record(Path(__file__).resolve()),
    "assertionCount": 0, "exitCode": None,
    "scope": "Production AdminServerClient constructor over real loopback HTTP to a controlled JDK HttpServer; synthetic credentials/data. Not real Admin Server/Spring Security, retry scheduler, USBGuard, physical USB, Ubuntu or GUI acceptance.",
}
with summary_path.open("x", encoding="utf-8") as output:
    json.dump(result, output, indent=2)
    output.write("\n")
started_clock = time.monotonic()
try:
    target = (ROOT / "client-agent/target").resolve()
    target.relative_to(ROOT)
    work = Path(tempfile.mkdtemp(prefix="qa-http-", dir=target)).resolve()
    work.relative_to(target)
    assert git("check-ignore", "--", work.relative_to(ROOT).as_posix()), "Runtime must be ignored"
    result["runtimeDirectory"] = work.relative_to(ROOT).as_posix()
    result["runtimeRetained"] = True
    classes = work / "classes"
    libraries = work / "lib"
    classes.mkdir()
    libraries.mkdir()
    extracted = []
    total_bytes = 0
    with zipfile.ZipFile(jar) as archive:
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            if entry.filename.startswith("BOOT-INF/classes/"):
                destination_base, raw_relative = classes, entry.filename[len("BOOT-INF/classes/"):]
            elif entry.filename.startswith("BOOT-INF/lib/") and entry.filename.endswith(".jar"):
                destination_base, raw_relative = libraries, entry.filename[len("BOOT-INF/lib/"):]
            else:
                continue
            relative_path = PurePosixPath(raw_relative)
            assert raw_relative and not relative_path.is_absolute() and ".." not in relative_path.parts
            assert "\\" not in raw_relative and ":" not in raw_relative, "Unsafe archive path"
            assert (entry.external_attr >> 16) & 0o170000 != 0o120000, "Archive symlink refused"
            assert entry.file_size <= 64 * 1024 * 1024 and len(extracted) < 10000
            total_bytes += entry.file_size
            assert total_bytes <= 256 * 1024 * 1024, "Extraction byte limit exceeded"
            destination = (destination_base / raw_relative).resolve()
            destination.relative_to(destination_base)
            destination.parent.mkdir(parents=True, exist_ok=True)
            with destination.open("xb") as output:
                output.write(archive.read(entry))
            extracted.append(destination.relative_to(work).as_posix())
    assert (classes / "com/group/usbshield/agent/sync/AdminServerClient.class").is_file()
    assert list(libraries.glob("*.jar")), "Agent JAR has no runtime libraries"
    result["extraction"] = {"fileCount": len(extracted), "bytes": total_bytes, "source": "Built Agent BOOT-INF/classes and BOOT-INF/lib only"}
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin" / ("java.exe" if sys.platform == "win32" else "java")) if java_home else shutil.which("java")
    assert java and Path(java).is_file(), "Java 21+ executable required"
    version = subprocess.run([java, "-version"], cwd=ROOT, capture_output=True, text=True, timeout=15, creationflags=flags)
    assert version.returncode == 0, "Java version probe failed"
    result["java"] = {"executable": java, "version": (version.stdout + version.stderr).strip()}
    log_config = work / "logback-qa.xml"
    log_config.write_text('<configuration><root level="OFF"/></configuration>\n', encoding="utf-8")
    java_summary = work / "java-result.json"
    classpath = str(classes) + os.pathsep + str(libraries / "*")
    launch = [java, "--add-modules", "jdk.httpserver", "-Dlogback.configurationFile=" + str(log_config),
              "-cp", classpath, str(harness), str(java_summary)]
    result["launch"] = {"executable": java, "args": launch[1:], "cwd": str(ROOT), "timeoutSeconds": 90}
    process = subprocess.run(launch, cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=90, creationflags=flags)
    result["exitCode"] = process.returncode
    # Capture hashes rather than raw Java output: credentials are generated only
    # in the Java process and are never persisted by this runner.
    result["javaStdoutSHA256"] = digest(process.stdout.encode())
    result["javaStderrSHA256"] = digest(process.stderr.encode())
    if java_summary.exists():
        details = json.loads(java_summary.read_text(encoding="utf-8"))
        result["javaResult"] = details
        result["assertionCount"] = details["assertionCount"]
        assert details["constructor"] == "AdminServerClient(AgentProperties)"
        assert details["status"] == "PASS" and details["scenarioCount"] == 15
        assert details["requestCount"] == 14 and details["assertionCount"] > 0
        assert all(item["status"] == "PASS" for item in details["scenarios"])
    else:
        # Source compilation/startup diagnostics precede generation of credentials.
        result["startupDiagnostic"] = process.stderr[-4000:]
        raise RuntimeError("Java source did not produce a result")
    assert process.returncode == 0, "Java smoke returned nonzero"
    assert record(jar)["sha256"] == result["jar"]["sha256"], "Agent JAR changed during smoke"
    assert record(harness)["sha256"] == result["harness"]["sha256"], "Harness changed during smoke"
    result["revisionAfter"] = git("rev-parse", "HEAD")
    assert result["revisionAfter"] == result["revision"], "HEAD changed during smoke"
    result["status"] = "PASS"
except BaseException as error:
    result["status"] = "FAIL"
    result["error"] = {"type": type(error).__name__, "message": str(error)}
finally:
    result["endedAt"] = timestamp()
    result["durationSeconds"] = round(time.monotonic() - started_clock, 3)
    summary_path.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "assertionCount": result["assertionCount"],
                      "exitCode": result["exitCode"], "summary": summary_path.relative_to(ROOT).as_posix()}))
sys.exit(0 if result["status"] == "PASS" else 1)
