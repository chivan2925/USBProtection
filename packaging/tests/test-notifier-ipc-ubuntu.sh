#!/usr/bin/env bash
# Read-only runtime inspection. Do not pass an arbitrary username/UID from an untrusted source.
set -euo pipefail
uid="${1:-$(id -u)}"
if [[ ! "$uid" =~ ^[0-9]+$ ]] || (( uid < 1000 )); then
  echo 'Expected a GUI UID >= 1000' >&2; exit 2
fi
runtime="/run/user/$uid"
private="$runtime/usbshield-notifier"
socket="$private/notice.sock"
[[ -d "$runtime" && ! -L "$runtime" ]] || { echo 'No active logind runtime directory'; exit 1; }
[[ -d "$private" && ! -L "$private" ]] || { echo 'No running private notifier directory'; exit 1; }
[[ -S "$socket" && ! -L "$socket" ]] || { echo 'No live notifier socket'; exit 1; }
[[ "$(stat -c '%u' "$runtime")" = "$uid" ]] || exit 1
[[ "$(stat -c '%u' "$private")" = "$uid" ]] || exit 1
[[ "$(stat -c '%u' "$socket")" = "$uid" ]] || exit 1
printf 'ACL evidence for GUI UID %s:\n' "$uid"
getfacl -p -- "$runtime" "$private" "$socket"
# Test service identity, not the current login group.
if [[ "$(id -un)" = usbshield ]]; then
  test -x "$private" && test -w "$socket"
else
  sudo -u usbshield /bin/sh -c 'test -x "$1" && test -w "$2"' sh "$private" "$socket"
fi
echo 'PASS: Agent service principal can traverse per-UID IPC directory and write to socket.'
echo 'NOT a proof of notification rendering; demonstrate actual BLOCK on Ubuntu separately.'
