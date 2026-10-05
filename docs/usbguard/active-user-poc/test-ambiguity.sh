#!/usr/bin/env bash
set -euo pipefail

# Simulated Week 1 resolver-logic test.
# Two equally valid candidates are intentionally provided.
candidates=(
  "10|student01|1001|wayland|seat1"
  "11|student02|1002|wayland|seat2"
)

if [[ ${#candidates[@]} -eq 0 ]]; then
  echo "status=UNKNOWN"
  echo "reason=no-active-local-session"
  exit 0
fi

if [[ ${#candidates[@]} -eq 1 ]]; then
  selected="${candidates[0]}"
else
  graphical=()

  for candidate in "${candidates[@]}"; do
    IFS='|' read -r sid name uid type seat <<< "$candidate"

    if [[ "$seat" == "seat0" && \
          ( "$type" == "wayland" || "$type" == "x11" ) ]]; then
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
