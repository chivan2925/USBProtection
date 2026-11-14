#!/usr/bin/env bash
# Execute separately from the real Admin workstation and a real Client workstation.
# Usage: ./verify-phase3-https-acl.sh admin|client https://usbshield.example.internal [ca-public.pem]
set -euo pipefail
if [ "$#" -lt 2 ] || [ "$#" -gt 3 ] || { [ "$1" != admin ] && [ "$1" != client ]; }; then
    echo "usage: $0 admin|client https://<DNS_NAME> [ca-public.pem]" >&2
    exit 2
fi
mode="$1"
base="${2%/}"
case "$base" in https://*) ;; *) echo 'Only validated HTTPS is allowed' >&2; exit 2 ;; esac
args=(--silent --show-error --output /dev/null --max-time 10)
if [ "$#" -eq 3 ]; then args+=(--cacert "$3"); fi
status() { curl "${args[@]}" --write-out '%{http_code}' "$base$1"; }
expect() {
    local expected="$1" path="$2" actual
    actual="$(status "$path")"
    if [ "$actual" != "$expected" ]; then
        echo "FAIL $mode $path expected=$expected actual=$actual" >&2
        exit 1
    fi
    echo "PASS $mode $path HTTP $actual"
}
if [ "$mode" = admin ]; then
    expect 200 '/api/auth/csrf'
    # Admin-only API still requires login, so unauthenticated traffic should be 401.
    expect 401 '/api/admin/endpoints/page'
else
    expect 403 '/'
    expect 403 '/api/auth/csrf'
    expect 403 '/api/admin/endpoints/page'
    # Agent route is allowed past the proxy; the Spring Agent filter rejects missing token.
    expect 401 '/api/agent/policy'
fi
