#!/usr/bin/env bash
set -euo pipefail

declare -a candidates=()

while read -r session_id _; do
  [[ -z "${session_id:-}" ]] && continue

  name="$(loginctl show-session "$session_id" -p Name --value 2>/dev/null || true)"
  uid="$(loginctl show-session "$session_id" -p User --value 2>/dev/null || true)"
  active="$(loginctl show-session "$session_id" -p Active --value 2>/dev/null || true)"
  remote="$(loginctl show-session "$session_id" -p Remote --value 2>/dev/null || true)"
  seat="$(loginctl show-session "$session_id" -p Seat --value 2>/dev/null || true)"
  type="$(loginctl show-session "$session_id" -p Type --value 2>/dev/null || true)"

  if [[ "$active" == "yes" && "$remote" == "no" ]]; then
    candidates+=("$session_id|$name|$uid|$type|$seat")
  fi
done < <(loginctl list-sessions --no-legend)

if [[ ${#candidates[@]} -eq 0 ]]; then
  echo "status=UNKNOWN"
  echo "reason=no-active-local-session"
  exit 0
fi

if [[ ${#candidates[@]} -eq 1 ]]; then
  selected="${candidates[0]}"
else
  declare -a graphical=()

  for candidate in "${candidates[@]}"; do
    IFS='|' read -r sid name uid type seat <<< "$candidate"

    if [[ "$seat" == "seat0" && ( "$type" == "wayland" || "$type" == "x11" ) ]]; then
      graphical+=("$candidate")
    fi
  done

  if [[ ${#graphical[@]} -eq 1 ]]; then
    selected="${graphical[0]}"
  else
    echo "status=UNKNOWN"
    echo "reason=ambiguous-active-local-sessions"
    printf 'candidate=%s\n' "${candidates[@]}"
    exit 0
  fi
fi

IFS='|' read -r sid name uid type seat <<< "$selected"

echo "status=RESOLVED"
echo "username=$name"
echo "uid=$uid"
echo "sessionId=$sid"
echo "sessionType=$type"
echo "seat=$seat"
