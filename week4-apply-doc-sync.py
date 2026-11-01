#!/usr/bin/env python3
"""One-time exact-base synchronization of docs and static verifiers.

Run AFTER extracting the overlay at repository root. It refuses an unexpected
commit, validates all expected original anchors before writing, and never signs
on behalf of an owner or fabricates test results.
"""
from __future__ import annotations
import re
import subprocess
import sys
from pathlib import Path

BASE = '63e4dc61b15b86859dd4ca2ca4be60021a293091'
root = Path(__file__).resolve().parent
head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
if head != BASE:
    raise SystemExit(f'REFUSED: expected main {BASE}, got HEAD {head}. Update/review patch manually.')

changes: dict[Path, str] = {}

def load(rel: str) -> str:
    p = root / rel
    value = p.read_text(encoding='utf-8')
    if rel in changes:
        raise RuntimeError(f'Already loaded: {rel}')
    return value

def sub_one(value: str, old: str, new: str, where: str) -> str:
    n = value.count(old)
    if n != 1:
        raise RuntimeError(f'{where}: expected exactly one anchor, found {n}: {old[:90]!r}')
    return value.replace(old, new, 1)

def regex_one(value: str, pattern: str, replacement: str, where: str) -> str:
    result, count = re.subn(pattern, lambda match: replacement, value, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f'{where}: regex anchor missing: {pattern[:90]}')
    return result

def save(rel: str, value: str) -> None:
    changes[root / rel] = value

# Update old Windows static packaging checks without changing historical evidence.
rel = 'packaging/tests/verify-week3-windows.ps1'
s = load(rel)
s = sub_one(s, "Assert-Contains $control 'libnotify-bin'", "Assert-Contains $control 'libnotify-bin'\nAssert-Contains $control 'acl'", rel)
s = sub_one(s, "Assert-Contains $postinst 'mkdir -p /run/usbshield-notifier'", "Assert-Contains $postinst 'legacy_tmpfiles=/etc/tmpfiles.d/usbshield-notifier.conf'", rel)
s = sub_one(s, "Assert-Contains $postinst 'chmod 1777 /run/usbshield-notifier'", "Assert-Contains $postinst 'chmod 0700 /run/usbshield-notifier'\nAssert-NotContains $postinst 'chmod 1777 /run/usbshield-notifier'", rel)
s = sub_one(s, "Assert-Contains $postinst 'd /run/usbshield-notifier 1777 root root -'", "Assert-Contains $postinst 'if [ ! -x /usr/bin/setfacl ]; then'", rel)
s = sub_one(s, "Assert-Contains $launcher '-Dloader.home=/opt/usbshield-agent'", "Assert-Contains $launcher '-Dloader.home=/opt/usbshield-agent'\nAssert-Contains $launcher 'EXPECTED_RUNTIME=\"/run/user/${USER_UID}\"'\nAssert-Contains $launcher '/usr/bin/setfacl'", rel)
save(rel, s)

rel = 'packaging/tests/verify-week4-windows.ps1'
s = load(rel)
s = sub_one(s, "Assert-Contains $control 'libnotify-bin'", "Assert-Contains $control 'libnotify-bin'\nAssert-Contains $control 'acl'", rel)
s = sub_one(s, "Assert-Contains $postinst 'chmod 1777 /run/usbshield-notifier'", "Assert-Contains $postinst 'chmod 0700 /run/usbshield-notifier'\nAssert-NotContains $postinst 'chmod 1777 /run/usbshield-notifier'", rel)
s = sub_one(s, "Assert-Contains $launcher 'com.group.usbshield.agent.notification.DesktopNotificationClientMain'", "Assert-Contains $launcher 'com.group.usbshield.agent.notification.DesktopNotificationClientMain'\nAssert-Contains $launcher 'EXPECTED_RUNTIME=\"/run/user/${USER_UID}\"'\nAssert-Contains $launcher '/usr/bin/setfacl'", rel)
save(rel, s)

rel = 'docs/API.md'
s = load(rel)
s = sub_one(s, 'oversized requests return 400; these limits do not change valid v0.2 payloads.',
    'oversized DTO fields typically return 400; authentication can reject an invalid or overlong bootstrap token with 401 **before** DTO validation (confirmed for 257 characters). These limits do not change valid v0.2 payloads.', rel)
save(rel, s)

rel = 'docs/CLIENT_INSTALLATION.md'
s = load(rel)
s = sub_one(s, '> **Trạng thái kiểm thử:** **WEEK 4 — WINDOWS/RELEASE-CANDIDATE PREPARATION PASS; UBUNTU E2E DEFERRED**',
    '> **Trạng thái:** M3 Windows staging đã có historical PASS; **IPC patch chưa được kiểm tra bởi owner, GROUP SIGN-OFF PENDING; UBUNTU E2E DEFERRED**.', rel)
s = sub_one(s, '  * `libnotify-bin` (Tiện ích phát thông báo đồ họa `notify-send` trong phiên user)',
    '  * `libnotify-bin` (Tiện ích phát thông báo đồ họa `notify-send` trong phiên user)\n  * `acl` (`setfacl`/`getfacl`, phân quyền chính xác notifier socket theo UID)', rel)
s = sub_one(s, '| `/run/usbshield-notifier/` | `root:root` | `1777` (Sticky) | Thư mục socket IPC giữa Agent và Desktop Notifier (khởi tạo qua `tmpfiles.d`) |',
    '| `/run/user/<UID>/usbshield-notifier/` | GUI user, UID thực | `0700` ban đầu, ACL `u:usbshield:--x` | Directory riêng theo phiên logind; không có shared 1777 mới |\n| `/run/user/<UID>/usbshield-notifier/notice.sock` | GUI user | `0600` ban đầu, ACL `u:usbshield:rw-` | Socket IPC; kiểm tra peer với `SO_PEERCRED` trước khi gửi |', rel)
s = sub_one(s, '1. Tự động kéo và cài đặt `openjdk-21-jre-headless`, `usbguard`, `libnotify-bin`.',
    '1. Tự động kéo và cài đặt `openjdk-21-jre-headless`, `usbguard`, `libnotify-bin`, `acl`.', rel)
s = sub_one(s, '5. Đăng ký quy tắc `tmpfiles.d` để giữ socket directory qua các lần khởi động.',
    '5. Ngừng tạo socket directory `1777` qua `tmpfiles.d` của bản cũ; GUI user tự tạo socket dưới `XDG_RUNTIME_DIR`.', rel)
s = sub_one(s, 'sudo cat /var/lib/usbshield-agent/endpoint.json',
    "sudo stat -c '%U:%G %a %n' /var/lib/usbshield-agent/endpoint.json\n# Không in toàn bộ endpoint.json: chứa Agent token.", rel)
s = sub_one(s, 'ls -la /run/usbshield-notifier/',
    'echo "$XDG_RUNTIME_DIR"\nstat -c \'%U:%G %a %n\' "$XDG_RUNTIME_DIR" "$XDG_RUNTIME_DIR/usbshield-notifier" "$XDG_RUNTIME_DIR/usbshield-notifier/notice.sock"\ngetfacl -p "$XDG_RUNTIME_DIR" "$XDG_RUNTIME_DIR/usbshield-notifier" "$XDG_RUNTIME_DIR/usbshield-notifier/notice.sock"', rel)
s += '\n\n## Week 4 IPC amendment — owner acceptance pending\n\n' \
     'New IPC design, previous 1777 path retirement, ACL/security caveats and Ubuntu test steps: ' \
     '[`docs/usbguard/WEEK4_IPC_ACL_DESIGN.md`](usbguard/WEEK4_IPC_ACL_DESIGN.md). ' \
     'The legacy results above predate this patch. Owner Maven/package and Ubuntu native tests ' \
     'must be recorded separately; do not mark notification runtime PASS from a Windows build.\n'
save(rel, s)

rel = 'docs/usbguard/WEEK4_M2_CORE_OPERATIONS_GUIDE.md'
s = load(rel)
s = regex_one(s, r'\*\*M3 packaging/ACL dependency:\*\*.*?(?=\n\n## 7\.)',
    '**M2/M3 IPC design (patch under review):** The former shared `1777` directory is retired. ' \
    'The GUI helper now owns `/run/user/<UID>/usbshield-notifier/notice.sock`, with ' \
    'named-user ACL `u:usbshield:--x` on parent directories and `u:usbshield:rw-` ' \
    'on the socket, and verifies `SO_PEERCRED` sender. The Agent verifies target UID ' \
    'ownership, socket inode and helper peer before sending. M3 packages `acl` and ' \
    'XDG startup. Design details and native deferred proof are in ' \
    '[`WEEK4_IPC_ACL_DESIGN.md`](WEEK4_IPC_ACL_DESIGN.md). Do not claim Ubuntu PASS without real tests.', rel)
s = sub_one(s, "stat -c '%n %U:%G %a' /run/usbshield-notifier /run/usbshield-notifier/*.sock",
    "# In the active GUI user session:\nstat -c '%n %U:%G %a' \"$XDG_RUNTIME_DIR\" \"$XDG_RUNTIME_DIR/usbshield-notifier\" \"$XDG_RUNTIME_DIR/usbshield-notifier/notice.sock\"\ngetfacl -p \"$XDG_RUNTIME_DIR\" \"$XDG_RUNTIME_DIR/usbshield-notifier\" \"$XDG_RUNTIME_DIR/usbshield-notifier/notice.sock\"", rel)
save(rel, s)

rel = 'docs/usbguard/WEEK4_M2_M3_M5_UBUNTU_HANDOFF.md'
s = load(rel)
s = sub_one(s, '- **M3:** `/run/usbshield-notifier` provisioning, POSIX ACL and socket startup, XDG autostart, `systemd`, Agent sync/ACK, outbox and `.deb` installer. M2\'s patch does not modify M3 files.',
    '- **M3:** `acl` dependency, retirement of the legacy shared `/run/usbshield-notifier` namespace, per-UID runtime/ACL contract, XDG autostart, `systemd`, Agent sync/ACK, outbox and `.deb` installer. Cross-owner packaging changes are in the separate Week 4 group package.', rel)
s = regex_one(s, r'## M3 IPC/permission review — action required.*?(?=## Post-Week-4 Ubuntu acceptance cases)',
    '## M2/M3 IPC revised design — Windows review pending, Ubuntu native DEFERRED\n\n' \
    'The candidate helper binds `/run/user/<UID>/usbshield-notifier/notice.sock`, not the ' \
    'former shared `1777` directory. Named ACL grants `usbshield` directory traverse ' \
    'and socket read/write, with owner/UID and Linux peer checks in both directions. ' \
    'See [`WEEK4_IPC_ACL_DESIGN.md`](WEEK4_IPC_ACL_DESIGN.md). M3 must review ' \
    'packaging `acl`/launcher/startup and M2 the notifier code. No Linux native ' \
    'permissions, connection, GUI delivery or cross-owner approval is asserted yet.\n\n', rel)
save(rel, s)

rel = 'docs/usbguard/WEEK4_M2_THREE_PART_PLAN.md'
s = load(rel)
s = sub_one(s, '**IMPLEMENTATION PREPARED — owner Maven/independent review pending; native Ubuntu and M3 IPC ACL signoff deferred/open**',
    '**PR #44 MERGED; M5 S3 174/174 Maven PASS; new IPC ACL correction under owner review; native Ubuntu DEFERRED**', rel)
s += '\n\n## Cross-owner IPC candidate after PR #49\n\n' \
     'See [`WEEK4_IPC_ACL_DESIGN.md`](WEEK4_IPC_ACL_DESIGN.md). Previous S3 ' \
     'tests covered the old IPC layout; revised Java/packaging requires fresh owner ' \
     'Maven, staging, security review and real Ubuntu runtime acceptance. ' \
     'Original M2 preparation statements remain historical and are not rewritten.\n'
save(rel, s)

rel = 'docs/qa/WEEK4_UBUNTU_ACCEPTANCE.md'
s = load(rel)
s = regex_one(s, r'The new helper authenticates sender `usbshield`.*?(?=\n\nM2 Part 2)',
    'Notifier IPC correction candidate: replace the old shared `1777` directory with ' \
    'a GUI UID-owned logind runtime subdirectory and `notice.sock`; named POSIX ACLs ' \
    'grant system Agent `usbshield` traversal/write, and Java verifies both socket ' \
    'owner and `SO_PEERCRED` identities. M2/M3 reviews and Ubuntu native reachability, ' \
    'peer rejection, GUI delivery and logind compatibility are **NOT EXECUTED / DEFERRED**. ' \
    'Follow [`WEEK4_IPC_ACL_DESIGN.md`](../usbguard/WEEK4_IPC_ACL_DESIGN.md).', rel)
s = sub_one(s, 'dpkg-query -W usbshield-agent usbguard openjdk-21-jre-headless libnotify-bin',
    'dpkg-query -W usbshield-agent usbguard openjdk-21-jre-headless libnotify-bin acl', rel)
s = sub_one(s, '/var/lib/usbshield-agent /run/usbshield-notifier',
    '/var/lib/usbshield-agent', rel)
s = sub_one(s, 'ls -ld /run/usbshield-notifier\nls -l /run/usbshield-notifier',
    'ls -ld "$XDG_RUNTIME_DIR" "$XDG_RUNTIME_DIR/usbshield-notifier"\nls -l "$XDG_RUNTIME_DIR/usbshield-notifier/notice.sock"\ngetfacl -p "$XDG_RUNTIME_DIR" "$XDG_RUNTIME_DIR/usbshield-notifier" "$XDG_RUNTIME_DIR/usbshield-notifier/notice.sock"', rel)
save(rel, s)

rel = 'docs/qa/WEEK4_RELEASE_VALIDATION.md'
s = load(rel)
heading = '# Week 4 — M5 release validation và bàn giao\n'
append = '''
## Post-PR #49 IPC finalization candidate (NOT YET ACCEPTED)

The separate Week 4 group overlay is based on `main` at
`63e4dc61b15b86859dd4ca2ca4be60021a293091`. It changes **M2 Java notifier IPC**
and **M3 packaging/ACL**. Prior S3/S4 evidence remains historical, not PASS for this
new code. Await Maven/JUnit and staging on the patched source, M2/M3 security
review, M1 release-candidate choice, M5 final source-SHA validation, group owner
sign-offs and administrator credential attestation. Ubuntu E2E remains DEFERRED.
See [`Week 4 owner sign-off template`](../rc/WEEK4_OWNER_SIGNOFF_TEMPLATE.md)
and [`IPC design`](../usbguard/WEEK4_IPC_ACL_DESIGN.md). The historical S4
Windows Agent-to-Spring and browser regression is not rewritten as a new run.

'''
s = sub_one(s, heading, heading+append, rel)
save(rel, s)

rel = 'docs/member1/WEEK4_SERVER_HANDOFF.md'
s = load(rel)
s += '\n\n## Post-PR #49 group IPC follow-up (candidate, not owner signature)\n\n' \
     'The separate finalization patch based on `63e4dc61b` adjusts only M2 notifier ' \
     'Java and M3 packaging plus docs. M1 v0.2 wire API remains unchanged. ' \
     'It does not supersede the historical M1 JAR manifest or deliver the JAR binary. ' \
     'The M1 group candidate must reference the merged final SHA, fresh Agent/packaging ' \
     'results, M2/M3 owner approvals, M5 source-matched QA and deployment credential ' \
     'attestation. See `docs/rc/WEEK4_OWNER_SIGNOFF_TEMPLATE.md`. Until then ' \
     '`W4-M1-06` and `W4-M1-07` remain OPEN.\n'
save(rel, s)

rel = 'docs/client-agent/WEEK4_CLIENT_AGENT_CLOSEOUT.md'
s = load(rel)
s += '\n\n## IPC correction after historical M3 Week 4 closeout (UNVERIFIED)\n\n' \
     'A later M2/M3 candidate replaces `/run/usbshield-notifier` mode `1777` ' \
     'with UID-owned `XDG_RUNTIME_DIR` paths and named POSIX ACL. Old M3 Windows ' \
     'build/staging logs do not verify this changed code. Run fresh Maven, new static ' \
     'packaging checks and obtain M2/M3 review. Ubuntu native peer/ACL/GUI and ' \
     'actual `.deb` build remain separately recorded. Details: ' \
     '`docs/usbguard/WEEK4_IPC_ACL_DESIGN.md`.\n'
save(rel, s)

# Stage all changes in memory; fail before touching anything on bad anchor.
for path, value in changes.items():
    if path.read_text(encoding='utf-8') == value:
        raise RuntimeError(f'No changes made: {path}')

if '--check' in sys.argv:
    print(f'PASS: {len(changes)} files have unique expected anchors. No files written.')
    for path in changes:
        print('  ', path.relative_to(root))
    sys.exit(0)
if '--apply' not in sys.argv:
    print('Use --check (dry run) or --apply (write validated replacements).')
    sys.exit(2)
for path, value in changes.items():
    path.write_text(value, encoding='utf-8', newline='\n')
    print('UPDATED', path.relative_to(root))
print(f'Applied {len(changes)} synchronized docs/static-verifier files.')
