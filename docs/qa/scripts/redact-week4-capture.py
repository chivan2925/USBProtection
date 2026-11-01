"""Redact additional key=value fixture credentials from one current QA capture, with provenance."""
import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--run-dir", required=True)
parser.add_argument("--id", required=True)
args = parser.parse_args()
assert re.fullmatch(r"[a-z0-9-]+", args.id)
run = (ROOT / args.run_dir).resolve()
run.relative_to(ROOT / "docs/qa/week4-evidence")
log = run / (args.id + ".txt")
metadata_path = run / (args.id + ".json")
metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
assert metadata["id"] == args.id and "postCaptureRedaction" not in metadata
before = log.read_bytes()
sha = lambda value: hashlib.sha256(value).hexdigest()
assert sha(before) == metadata["outputSHA256"], "Capture log changed before redaction"
pattern = re.compile(r"(\b(?:agentToken|enrollmentToken|password)\s*[=:]\s*)(?!\[REDACTED\])([^,\s\)\"}]+)", re.I)
text = before.decode("utf-8")
redacted, count = pattern.subn(lambda match: match.group(1) + "[REDACTED]", text)
assert count > 0, "No additional key=value values to redact"
after = redacted.encode("utf-8")
log.write_bytes(after)
metadata["postCaptureRedaction"] = {
    "performedAt": datetime.now(timezone.utc).isoformat(),
    "policy": "Additional key=value fixture credential values; original execution status/source/commands unchanged",
    "originalOutputSHA256": sha(before), "redactedOutputSHA256": sha(after), "replacementCount": count,
    "tool": {"path": Path(__file__).relative_to(ROOT).as_posix(), "sha256": sha(Path(__file__).read_bytes())},
    "unredactedCopyBackedUp": False,
}
metadata["outputSHA256"] = sha(after)
metadata_path.write_text(json.dumps(metadata, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(json.dumps({"status": "REDACTED", "capture": args.id, "replacementCount": count,
                  "originalExecutionVerdict": metadata["verdict"]}))
