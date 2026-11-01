"""QA-only built Agent -> packaged Spring Server integration; no builds or downloads."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import zipfile
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[3]
FLAGS = subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0
GIT = shutil.which("git") or "C:/Program Files/Git/cmd/git.exe"
SOURCE_SCOPES = ["admin-server", "client-agent", "frontend", "packaging", "docs/agent-contract"]


def timestamp():
    return datetime.now(timezone.utc).isoformat()


def sha(data):
    return hashlib.sha256(data).hexdigest()


def git(*args):
    process = subprocess.run([GIT, *args], cwd=ROOT, capture_output=True, timeout=30, creationflags=FLAGS)
    if process.returncode:
        raise RuntimeError("Git provenance check failed")
    return process.stdout.decode("utf-8").strip()


def file_record(path):
    data = path.read_bytes()
    return {"path": path.relative_to(ROOT).as_posix(), "bytes": len(data), "sha256": sha(data)}


def source_snapshot():
    names = git("ls-files", "-z", "--", *SOURCE_SCOPES).split("\0")
    records = [file_record(ROOT / name) for name in sorted(filter(None, names))]
    return {"scopes": SOURCE_SCOPES, "fileCount": len(records),
            "workingTreeSHA256": sha(json.dumps(records, separators=(",", ":")).encode())}


def extract_agent(jar, work):
    classes, libraries = work / "classes", work / "lib"
    classes.mkdir()
    libraries.mkdir()
    count = total = 0
    with zipfile.ZipFile(jar) as archive:
        for entry in archive.infolist():
            if entry.is_dir():
                continue
            if entry.filename.startswith("BOOT-INF/classes/"):
                base, relative = classes, entry.filename[len("BOOT-INF/classes/"):]
            elif entry.filename.startswith("BOOT-INF/lib/") and entry.filename.endswith(".jar"):
                base, relative = libraries, entry.filename[len("BOOT-INF/lib/"):]
            else:
                continue
            parts = PurePosixPath(relative)
            assert relative and not parts.is_absolute() and ".." not in parts.parts, "Unsafe archive path"
            assert "\\" not in relative and ":" not in relative, "Unsafe archive path"
            assert (entry.external_attr >> 16) & 0o170000 != 0o120000, "Archive symlink refused"
            assert count < 10000 and entry.file_size <= 64 * 1024 * 1024, "Archive entry limit exceeded"
            total += entry.file_size
            assert total <= 256 * 1024 * 1024, "Archive extraction limit exceeded"
            destination = (base / relative).resolve()
            destination.relative_to(base)
            destination.parent.mkdir(parents=True, exist_ok=True)
            with destination.open("xb") as output:
                output.write(archive.read(entry))
            count += 1
    assert (classes / "com/group/usbshield/agent/sync/AdminServerClient.class").is_file(), "Production Agent class missing"
    assert list(libraries.glob("*.jar")), "Agent runtime libraries missing"
    return classes, libraries, {"files": count, "bytes": total, "source": "Built Agent BOOT-INF/classes and BOOT-INF/lib"}


def find_java(java_home):
    if java_home:
        path = Path(java_home) / "bin" / ("java.exe" if sys.platform == "win32" else "java")
    else:
        path = Path(shutil.which("java") or "")
    assert path.is_file(), "Set JAVA_HOME or --java-home to JDK 22"
    process = subprocess.run([str(path), "-version"], capture_output=True, text=True, timeout=15, creationflags=FLAGS)
    assert process.returncode == 0, "Java version probe failed"
    version = (process.stdout + process.stderr).strip()
    assert re.search(r'version "22(?:[.\"+\-])', version), "This integration run requires JDK 22"
    return str(path.resolve()), version


def ready(base, server):
    deadline = time.monotonic() + 90
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    while time.monotonic() < deadline:
        assert server.poll() is None, "Packaged server exited before readiness"
        try:
            with opener.open(base + "/api/auth/csrf", timeout=2) as response:
                if response.status == 200:
                    return
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(0.4)
    raise RuntimeError("Packaged server readiness timeout")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-dir", required=True)
    parser.add_argument("--summary-name", default="agent-server-summary.json")
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    args = parser.parse_args()
    evidence_root = (ROOT / "docs/qa/week4-evidence").resolve()
    evidence = (ROOT / args.run_dir).resolve()
    evidence.relative_to(evidence_root)
    assert evidence != evidence_root, "Choose a run-specific evidence directory"
    assert re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*\.json", args.summary_name), "Use a plain JSON summary filename"
    evidence.mkdir(parents=True, exist_ok=True)
    summary = evidence / args.summary_name
    assert not summary.exists(), "Existing integration summary; choose a fresh filename"
    result = {"status": "RUNNING", "startedAt": timestamp(), "assertionCount": 0, "exitCode": None,
              "scope": "Actual built production AdminServerClient -> actual packaged Spring Admin Server/prod over loopback. Synthetic credentials/data; no native daemon, durable retries, IPC, GUI, USB or Ubuntu execution.",
              "commands": {}, "scenarios": []}
    # Reserve the evidence filename before any execution. Never replace a historical attempt.
    with summary.open("x", encoding="utf-8") as output:
        json.dump(result, output, indent=2)
        output.write("\n")
    start = time.monotonic()
    server = None
    server_out = server_err = None
    runtime = None
    port = None
    secret_values = []
    marker = None
    try:
        server_jar = ROOT / "admin-server/target/usbshield-admin-server.jar"
        agent_jar = ROOT / "client-agent/target/usbshield-agent.jar"
        harness = ROOT / "docs/qa/scripts/AgentServerIntegrationSmoke.java"
        runner = Path(__file__).resolve()
        result.update({"revision": git("rev-parse", "HEAD"), "sourceBefore": source_snapshot(),
                       "serverJar": file_record(server_jar), "agentJar": file_record(agent_jar),
                       "harness": file_record(harness), "runner": file_record(runner)})
        target = (ROOT / "admin-server/target").resolve()
        target.relative_to(ROOT)
        runtime = Path(tempfile.mkdtemp(prefix="qa-integration-", dir=target)).resolve()
        runtime.relative_to(target)
        assert git("check-ignore", "--", runtime.relative_to(ROOT).as_posix()), "Runtime must be ignored"
        result.update({"runtimeDirectory": runtime.relative_to(ROOT).as_posix(), "runtimeRetained": True,
                       "runtimeScope": "Private generated H2/runtime logs, not copied into evidence; no recursive cleanup"})
        classes, libraries, extraction = extract_agent(agent_jar, runtime)
        result["extraction"] = extraction
        java, version = find_java(args.java_home)
        result["java"] = {"executable": java, "version": version, "requiredMajor": 22}
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        base = "http://127.0.0.1:" + str(port)
        result["loopback"] = {"address": "127.0.0.1", "port": port, "profile": "prod",
                              "testOverride": "Session Secure=false permits this isolated HTTP-only loopback exercise; production defaults are not changed"}
        bootstrap, password, db_password = (secrets.token_urlsafe(32) for _ in range(3))
        admin_user = "qa-admin-" + secrets.token_hex(8)
        marker = "qa-" + secrets.token_hex(12)
        secret_values = [bootstrap, password, db_password]
        environment = os.environ.copy()
        removed_names = []
        # Prevent ambient deployment configuration/JVM options or proxies entering this isolated run.
        for key in list(environment):
            upper = key.upper()
            if upper.startswith(("USBSHIELD_", "SPRING_", "SERVER_", "LOGGING_", "QA_INTEGRATION_")) or upper in {
                    "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "DEBUG", "TRACE"}:
                environment.pop(key)
                removed_names.append(key)
        result["ambientEnvironmentRemoved"] = sorted(removed_names)
        environment.update({"USBSHIELD_ENROLLMENT_TOKEN": bootstrap, "USBSHIELD_ADMIN_USERNAME": admin_user,
                            "USBSHIELD_ADMIN_PASSWORD": password, "USBSHIELD_DB_PASSWORD": db_password,
                            "USBSHIELD_DB_USERNAME": "sa", "QA_INTEGRATION_BOOTSTRAP": bootstrap,
                            "QA_INTEGRATION_ADMIN_USERNAME": admin_user, "QA_INTEGRATION_ADMIN_PASSWORD": password,
                            "QA_INTEGRATION_LOG_MARKER": marker})
        server_launch = [java, "-jar", str(server_jar), "--spring.profiles.active=prod", "--debug=false", "--trace=false", "--server.address=127.0.0.1",
                         "--server.port=" + str(port), "--server.servlet.session.cookie.secure=false",
                         "--spring.datasource.url=jdbc:h2:file:" + (runtime / "server-db").as_posix() + ";DB_CLOSE_ON_EXIT=FALSE"]
        result["commands"]["server"] = {"executable": java, "args": server_launch[1:], "cwd": str(runtime),
                                          "secretDelivery": "Synthetic environment variables, values omitted"}
        server_out = (runtime / "server.stdout.log").open("xb")
        server_err = (runtime / "server.stderr.log").open("xb")
        server = subprocess.Popen(server_launch, cwd=runtime, env=environment, stdout=server_out, stderr=server_err, creationflags=FLAGS)
        ready(base, server)
        result["serverReady"] = True
        config = runtime / "logback-agent-off.xml"
        config.write_text('<configuration><root level="OFF"/></configuration>\n', encoding="utf-8")
        java_result = runtime / "java-result.json"
        launch = [java, "-Dlogback.configurationFile=" + str(config), "-cp", str(classes) + os.pathsep + str(libraries / "*"),
                  str(harness), base, str(java_result)]
        result["commands"]["harness"] = {"executable": java, "args": launch[1:], "cwd": str(ROOT), "timeoutSeconds": 150}
        process = subprocess.run(launch, cwd=ROOT, env=environment, capture_output=True, timeout=150, creationflags=FLAGS)
        result["exitCode"] = process.returncode
        result["harnessOutput"] = {"stdoutSHA256": sha(process.stdout), "stderrSHA256": sha(process.stderr),
                                   "rawOutputPersisted": False}
        assert java_result.is_file(), "Java integration result missing; inspect private runtime only"
        details = json.loads(java_result.read_text(encoding="utf-8"))
        result["javaResult"] = details
        result["assertionCount"] = details["assertionCount"]
        result["scenarios"] = details["scenarios"]
        result["scenarioCount"] = details["scenarioCount"]
        result["requestCount"] = details["requestCount"]
        assert process.returncode == 0 and details["status"] == "PASS", "Agent-to-server assertions failed"
        assert details["constructor"] == "AdminServerClient(AgentProperties)" and result["assertionCount"] > 0
        assert all(item["status"] == "PASS" for item in result["scenarios"]), "Incomplete integration scenario"
        assert server.poll() is None, "Server exited during integration"
        result["status"] = "PASS"
    except BaseException as error:
        result["status"] = "FAIL"
        # A subprocess/library exception may contain input. Persist only fixed QA labels/types.
        result["error"] = {"type": type(error).__name__, "message": str(error) if isinstance(error, (AssertionError, RuntimeError)) else "Integration runner failed"}
    finally:
        if server is not None:
            try:
                if server.poll() is None:
                    server.terminate()
                    try:
                        server.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        server.kill()
                        server.wait(timeout=10)
                result["serverTermination"] = {"confirmed": server.poll() is not None, "exitCode": server.returncode,
                                                "scope": "Direct Java child only; no shell wrapper or recursive deletion"}
                if server.poll() is None:
                    raise RuntimeError("Server termination unconfirmed")
                closed = False
                for _ in range(15):
                    try:
                        with socket.create_connection(("127.0.0.1", port), timeout=0.2):
                            pass
                    except OSError:
                        closed = True
                        break
                    time.sleep(0.2)
                result["serverTermination"]["loopbackPortClosed"] = closed
                if not closed:
                    raise RuntimeError("Server loopback port remained open; runtime retained")
            except BaseException as error:
                result["status"] = "FAIL"
                result["cleanupError"] = {"type": type(error).__name__, "message": "Server termination could not be confirmed; runtime retained"}
        for stream in (server_out, server_err):
            if stream is not None:
                stream.close()
        try:
            if runtime is not None and marker is not None:
                logs = [path for path in (runtime / "server.stdout.log", runtime / "server.stderr.log") if path.is_file()]
                combined = b"\n".join(path.read_bytes() for path in logs)
                markers = [marker + suffix for suffix in ("-user", "-session", "-serial", "-hash")]
                marker_presence = {name: {"infoLines": 0, "otherLines": 0}
                                   for name in ("username", "session", "serial", "hash")}
                for line in combined.splitlines():
                    level = "infoLines" if re.search(rb"\bINFO\b", line) else "otherLines"
                    for name, value in zip(marker_presence, markers):
                        if value.encode() in line:
                            marker_presence[name][level] += 1
                log_check = {"status": "PASS", "infoAcceptancePresent": b"Accepted USB event id=" in combined,
                             "uniqueSyntheticMarkersAbsent": all(value.encode() not in combined for value in markers),
                             "infoMarkersAbsent": all(counts["infoLines"] == 0 for counts in marker_presence.values()),
                             "otherMarkersAbsent": all(counts["otherLines"] == 0 for counts in marker_presence.values()),
                             "markerPresenceByLevel": marker_presence,
                             "syntheticSecretsAbsent": all(value.encode() not in combined for value in secret_values),
                             "markersChecked": ["username", "session", "serial", "hash"],
                             "logs": [{"file": path.name, "bytes": path.stat().st_size, "sha256": sha(path.read_bytes())} for path in logs],
                             "rawLogsCopiedToEvidence": False,
                             "scope": "Private prod server stdout/stderr: INFO acceptance plus separate INFO/other marker scan, including validation warnings. Strict overall absence required; Agent logging disabled separately"}
                if not all(log_check[key] for key in ("infoAcceptancePresent", "uniqueSyntheticMarkersAbsent", "syntheticSecretsAbsent")):
                    log_check["status"] = "FAIL"
                    result["status"] = "FAIL"
                result["logMinimization"] = log_check
            if "sourceBefore" in result:
                result["revisionAfter"] = git("rev-parse", "HEAD")
                result["sourceAfter"] = source_snapshot()
                result["sourceStable"] = result["sourceBefore"] == result["sourceAfter"] and result["revision"] == result["revisionAfter"]
                assert result["sourceStable"], "Application source changed during integration"
                for key, path in (("serverJar", ROOT / "admin-server/target/usbshield-admin-server.jar"),
                                  ("agentJar", ROOT / "client-agent/target/usbshield-agent.jar"),
                                  ("harness", ROOT / "docs/qa/scripts/AgentServerIntegrationSmoke.java"), ("runner", Path(__file__).resolve())):
                    assert file_record(path)["sha256"] == result[key]["sha256"], "Artifact or QA source changed during integration"
        except BaseException as error:
            result["status"] = "FAIL"
            result["finalizationError"] = {"type": type(error).__name__, "message": str(error) if isinstance(error, AssertionError) else "Provenance/log finalization failed"}
        result["endedAt"] = timestamp()
        result["durationSeconds"] = round(time.monotonic() - start, 3)
        summary.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(json.dumps({"status": result["status"], "assertionCount": result["assertionCount"], "exitCode": result["exitCode"],
                          "summary": summary.relative_to(ROOT).as_posix()}))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
