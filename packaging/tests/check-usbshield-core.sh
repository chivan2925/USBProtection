#!/usr/bin/env bash
# USBShield minimal-core read-only check for Ubuntu 24.04 A/B.
# Run under the real Ubuntu GUI login, with sudo access for read-only diagnostics.
# Does not change policy, restart services, read Agent secrets, or call Admin mutations.
# Usage: bash check-usbshield-core.sh [--expect block|allow] [--usb-id VID:PID]
set -euo pipefail
expected=''
usb_id=''
while (( $# > 0 )); do
    case "$1" in
        --expect)
            [[ $# -ge 2 ]] || { echo 'Missing --expect value' >&2; exit 2; }
            expected="$2"; shift 2 ;;
        --usb-id)
            [[ $# -ge 2 ]] || { echo 'Missing --usb-id value' >&2; exit 2; }
            usb_id="${2,,}"; shift 2 ;;
        -h|--help)
            echo 'Usage: bash check-usbshield-core.sh [--expect block|allow] [--usb-id VID:PID]'
            exit 0 ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
done
if [[ -n "$expected" && "$expected" != 'block' && "$expected" != 'allow' ]]; then
    echo '--expect must be block or allow' >&2; exit 2
fi
if [[ -n "$usb_id" && ! "$usb_id" =~ ^[0-9a-f]{4}:[0-9a-f]{4}$ ]]; then
    echo '--usb-id must be VID:PID in hex (e.g. 0951:1665)' >&2; exit 2
fi

pass_count=0
fail_count=0
pass() { printf '[PASS] %s\n' "$1"; ((pass_count+=1)); }
fail() { printf '[FAIL] %s\n' "$1" >&2; ((fail_count+=1)); }
command -v usbguard >/dev/null || { echo '[FAIL] usbguard executable missing' >&2; exit 1; }
command -v curl >/dev/null || { echo '[FAIL] curl executable missing' >&2; exit 1; }
sudo -v || { echo '[FAIL] sudo authentication failed' >&2; exit 1; }

printf 'USBShield core check: %s | host=%s | expect=%s\n' \
    "$(date -Is)" "$(hostname)" "${expected:-preflight}"

for svc in usbguard usbshield-agent; do
    if systemctl is-active --quiet "$svc"; then pass "$svc active"; else fail "$svc is not active"; fi
done
if [[ "$(systemctl show usbshield-agent -p User --value 2>/dev/null)" == 'usbshield' ]]; then
    pass 'Agent runs as usbshield (not root)'
else
    fail 'Agent service User is not usbshield'
fi

policy="$(sudo usbguard get-parameter ImplicitPolicyTarget 2>/dev/null || true)"
if [[ "$policy" == block ]]; then pass 'ImplicitPolicyTarget=block'; else fail "Unsafe implicit policy: ${policy:-unavailable}"; fi

rules="$(sudo -u usbshield usbguard list-rules 2>/dev/null || true)"
if [[ -n "$rules" ]]; then pass 'USBGuard IPC list-rules as usbshield'; else fail 'USBGuard IPC list-rules unavailable/empty'; fi
if sudo -u usbshield usbguard list-devices >/dev/null 2>&1; then
    pass 'USBGuard IPC list-devices as usbshield'
else
    fail 'USBGuard IPC list-devices denied'
fi
if sudo -u usbshield usbguard get-parameter ImplicitPolicyTarget >/dev/null 2>&1; then
    pass 'USBGuard IPC get-parameter as usbshield'
else
    fail 'USBGuard IPC get-parameter denied'
fi
if printf '%s\n' "$rules" | grep -Eq 'allow[[:space:]]+with-interface[[:space:]]+none-of[[:space:]]*\{[[:space:]]*08:\*:\*[[:space:]]*\}'; then
    pass 'Canonical non-storage base rule exists'
else
    fail 'Canonical non-storage base rule missing'
fi
# Reject unreviewed Allow rules; only the packaged base rule and Agent-managed
# rules are expected for this small, single-USB demo. Do not modify any rules.
foreign=0
while IFS= read -r line; do
    [[ "$line" =~ ^[[:space:]]*[0-9]+:[[:space:]]+allow[[:space:]] ]] || continue
    if [[ "$line" == *'none-of { 08:*:* }'* || "$line" == *'usbshield-managed'* ]]; then
        continue
    fi
    ((foreign+=1))
done <<< "$rules"
if (( foreign == 0 )); then pass 'No foreign USBGuard Allow rules'; else fail "Found $foreign foreign Allow rule(s)"; fi

if [[ -s /var/lib/usbshield-agent/endpoint.json ]] || sudo test -s /var/lib/usbshield-agent/endpoint.json; then
    pass 'Agent endpoint.json exists (contents not displayed)'
else
    fail 'Agent has no durable endpoint enrollment'
fi

env_file='/etc/usbshield-agent/agent.env'
env_meta="$(sudo stat -c '%U:%a' "$env_file" 2>/dev/null || true)"
if [[ "$env_meta" == 'root:600' ]]; then pass 'agent.env is root-owned 0600'; else fail "agent.env ownership/mode invalid: ${env_meta:-missing}"; fi
if sudo grep -qxF 'USBSHIELD_SERVER_URL=https://usbprotection.lab' "$env_file" 2>/dev/null; then
    pass 'Agent HTTPS URL is usbprotection.lab'
else
    fail 'Wrong/missing USBSHIELD_SERVER_URL (check privately; do not display tokens)'
fi
if getent ahostsv4 usbprotection.lab >/dev/null 2>&1; then
    pass 'usbprotection.lab resolves on this VM'
else
    fail 'usbprotection.lab cannot be resolved'
fi
ca='/etc/usbshield-agent/usbshield-ca.pem'
curl_params=(--silent --show-error --output /dev/null --max-time 10 --write-out '%{http_code}')
if sudo test -f "$ca"; then
    if [[ -r "$ca" ]]; then
        curl_params+=(--cacert "$ca")
        pass 'Lab CA public PEM readable'
    else
        fail 'Lab CA is not readable by the current user; cannot test TLS'
    fi
fi
http_status="$(curl "${curl_params[@]}" 'https://usbprotection.lab/api/agent/policy' 2>/dev/null)" || http_status='TLS/CONNECT ERROR'
if [[ "$http_status" == '401' ]]; then
    pass 'HTTPS/CA/Agent path: unauthenticated request returned 401'
else
    fail "HTTPS Agent route expected 401, observed ${http_status:-NO_RESPONSE}"
fi

if [[ -n "$expected" ]]; then
    devices="$(sudo usbguard list-devices 2>/dev/null || true)"
    matched=0
    actual=''
    while IFS= read -r line; do
        [[ "$line" =~ ^[[:space:]]*[0-9]+:[[:space:]]+(allow|block|reject)[[:space:]] ]] || continue
        state="${BASH_REMATCH[1]}"
        # Restrict to mass-storage class 08; do not mix up keyboards/mice.
        [[ "$line" == *'with-interface'* && "$line" =~ 08:[0-9A-Fa-f*]+:[0-9A-Fa-f*]+ ]] || continue
        if [[ -n "$usb_id" ]]; then
            [[ "${line,,}" == *"id $usb_id "* ]] || continue
        fi
        ((matched+=1))
        actual="$state"
    done <<< "$devices"
    if (( matched != 1 )); then
        fail "Expected exactly one matching mass-storage USB, found $matched; use --usb-id if multiple devices. Check VMware passthrough."
    elif [[ "$actual" == "$expected" || ( "$expected" == block && "$actual" == reject ) ]]; then
        pass "USBGuard reports $actual for the selected mass-storage USB"
    else
        fail "USBGuard state=$actual, expected=$expected"
    fi
    if [[ "$expected" == allow ]]; then
        if printf '%s\n' "$rules" | grep -q 'usbshield-managed'; then
            pass 'USBShield managed Allow rule present'
        else
            fail 'No usbshield-managed rule found despite expected ALLOW'
        fi
    else
        if printf '%s\n' "$rules" | grep -q 'usbshield-managed'; then
            fail 'Managed Allow rule remains while expecting BLOCK; inspect whitelist/rule identity'
        else
            pass 'No managed Allow rule remains'
        fi
    fi
    echo 'Storage visibility (manual cross-check; not an automatic mount PASS):'
    lsblk -o NAME,TRAN,TYPE,MOUNTPOINTS
fi
printf 'Summary: %d PASS, %d FAIL\n' "$pass_count" "$fail_count"
if (( fail_count > 0 )); then exit 1; fi
printf 'Preflight/device-state checks PASS; still verify Admin ACK, event, GUI popup and physical USB behavior manually.\n'
