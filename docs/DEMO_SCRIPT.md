# USBShield Week 4 demo script

Owner: M5. Prepared and automatically rehearsed: 2026-10-28 (Asia/Bangkok). Work item: **W4-M5-04**. Script/Q&A and fresh automated Windows fixture/management/Events/packaged-JAR rehearsals are **PASS** on the documented baseline; see the [Week 4 run record](qa/week4-evidence/2026-10-28/windows-regression/README.md). An [actual authenticated browser screencast replay](qa/week4-evidence/2026-10-28/windows-regression/recording/replay.html) contains 207 CDP frames with original capture timings. **No encoded video, audio or narrated live walkthrough was produced.** Ubuntu live demonstration remains **NOT EXECUTED / DEFERRED until after Week 4**.

Use the [test inventory](qa/WEEK4_TEST_INVENTORY.md), [release validation](qa/WEEK4_RELEASE_VALIDATION.md), [Event History review](qa/WEEK4_EVENT_HISTORY_REVIEW.md), [evidence index](qa/week4-evidence/README.md) and [Ubuntu recording sheets](qa/WEEK4_UBUNTU_ACCEPTANCE.md) with this script. The **28 October rehearsal baseline** is `2013ce726ef8cc960b30720435be23e8ccef1903`; its recordings/results keep that date and source. The [Week 3 owner decision](qa/WEEK3_FINAL_OWNER_CLOSEOUT_20261028.md) remains historical **PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED**, H05 closed.

**Historical post-pull update, 2026-10-30:** S1 was `751387bf166c528326ae91c6a16a820a6d8fc24a`. [M4's shared-frontend handoff](qa/evidence/M4_WEEK4_HANDOFF.md) was [technically acknowledged by M5](qa/evidence/M5_WEEK4_POST_PULL_ACK_20261030.md). That day's Agent regression was **136/136 PASS**, including **9/9** watch/stress tests, with static/JAR and evidence validation PASS; see [30 October evidence](qa/week4-evidence/2026-10-30/post-pull-regression/README.md). Browser evidence remained dated 28 October.

**Historical update, 2026-10-31:** S2 was `0b726304a29a78ac36aefbbc71e40fbd7758ae2e`. [M2 Part 2/M3 Windows technical ACK](qa/evidence/M5_WEEK4_M2_M3_ACK_20261031.md) covers Agent **156/156**, staging/full verifier/**9/9 byte-integrity**, production-constructor HTTP **15 scenarios / 14 loopback requests / 255 assertions** and final evidence validation PASS. Capture 05's harness timestamp expectation failed, corrected capture 06 passed on the same source/JAR; no application fix. This remains dated S2 proof, not current Part 3 execution.

**Historical S3, 2026-11-01:** `3efa50b50031dac9cec5738e286ec0a7730529ae` has [M2 Part 3 Windows ACK](qa/evidence/M5_WEEK4_M2_PART3_ACK_20261101.md): **174/174 tests**, staging 9/9, controlled-loopback HTTP 255 assertions and all 5 captures/evidence validation PASS. QA commit `bf067ef` was merged through `c07cb2f`; it is no longer an uncommitted run. Its native/external-PR limits remain intact.

**Current S4, 2026-11-01:** HEAD `c6cc1df21283588d70a792a37d42870bdea49497` has [M1 receipt and Windows technical ACK](qa/evidence/M5_WEEK4_M1_ACK_20261101.md). M1 JAR `43269264…` and fresh M5 JAR `0772ae2a…` are separate builds from equivalent server/frontend source. [S4 execution + final validation PASS](qa/week4-evidence/2026-11-01/m1-integration-regression/README.md): server **28/28**, policy **1/1**, frontend build, 3 browser suites/**25 screenshots**, production Agent→real Spring `prod` **215 assertions / 64 scenarios / 64 harness HTTP operations**, **5 embedded React assets byte-equal**, all **7 positive captures**. Strict log minimization passed with DEBUG/TRACE=false. Initial 07 FAIL is retained; 08 passed without app/JAR changes. Packaged suite retains 31 original assertion lines, cleanup confirmed, **recording DISABLED**: no new CDP/video/audio/narration. Group sign-offs remain pending; no Agent-unit rerun or Ubuntu result is claimed.

## Windows preparation

Use **JDK 21/22**, Maven, compatible Node/npm and locally installed Chrome/Edge. M1 verified JDK 22.0.2 targeting Java 21; pinned Lombok 1.18.36 fails with JDK 24 (`TypeTag :: UNKNOWN`). Set `JAVA_HOME` accordingly; do not upgrade frozen dependencies as a QA workaround. Set `CHROME_PATH` if Chromium is outside standard harness paths. Build from repository root, preserving command output/exit codes:

```powershell
git rev-parse HEAD
git status --short
java -version
mvn -version
node --version
npm --version
mvn -B -f client-agent/pom.xml clean package
Push-Location frontend
npm ci --no-audit --no-fund
npm run lint
npm test
npm run build
Pop-Location
mvn -B -f admin-server/pom.xml -Pwith-frontend clean package
```

Use `-o` for Maven and `--offline` for npm only when the required dependencies are already cached; record a missing-cache failure as such. A successful integrated JAR build proves asset packaging; the checked-in browser suites below use Vite preview/proxy and must not be described as a fresh JAR-served SPA demonstration. Same-origin packaged-JAR proof needs a separate run and evidence.

Run browser suites **sequentially** from `frontend/`; preview ports 4173/4174 must be free. Each real-backend suite creates its own temporary file-backed H2, random QA password/enrollment token and synthetic endpoints. Suites do not share accounts or data. They start headless Chromium and clean up their own child processes. Do not point them at an operator database or reuse deployment credentials.

```powershell
Push-Location frontend
npm run test:browser
npm run test:live
npm run test:browser:live
node --test tests/m4_policy_live.test.mjs
Pop-Location
```

| Command | What the presenter can show | Data/enforcement scope |
|---|---|---|
| `npm run test:browser` | Explicit demo labels, readonly demo management, loading/empty/error and mobile views | HTTP fixture; no Spring Boot, Agent or USBGuard proof |
| `npm run test:live` | Login, endpoint A/B, event, Allow/Revoke, version/ACK, real backend restart and session expiry | Real Spring Security/H2; endpoint enrollment, USB events and ACK are controlled HTTP inputs |
| `npm run test:browser:live` | Event History, 25 baseline records, 20+5 pagination, dates, filters, three extra attribution records, 403/500/network failure and recovery | Real Spring Boot/H2; 28 synthetic HTTP events; browser request interception injects failures |
| `node --test tests/m4_policy_live.test.mjs` | Additional API assertions for endpoint policy isolation and version/ACK behavior | Real backend HTTP assertions; no real client apply |

The harness writes captures to `frontend/.artifacts/`. Copy only captures generated during the current run into the Week 4 evidence directory before later runs overwrite shared names. Record source SHA, actual command/exit, tool versions, JAR hashes, screenshot hashes and fixture scope in the evidence manifest. Never copy temporary H2 databases, cookies, tokens or raw registration files. Inspect logs/images for credentials and personal data before sharing them. Screenshot availability does not establish a video recording or an interactive rehearsal.

## Walkthrough, approximately 8–10 minutes

Opening statement: **“This Week 4 demonstration runs the real Admin Server and browser on Windows. Endpoint enrollment, USB events and policy ACKs are synthetic HTTP inputs. The outage segment uses browser fault injection and a separate real server restart. Physical USB enforcement and Ubuntu desktop notifications will be demonstrated after Week 4.”**

Present the captures and actual assertion output in the order below. The management harness itself visits Events after Allow/Revoke; the table is the presentation order, not a claim that one continuous automated browser session follows this narration. The separate Events harness uses different QA data.

| Step | Presenter action / automated evidence | Expected observation and narration |
|---|---|---|
| 1. Login | `test:live`; show `m4-live-login.png`, then `m4-live-dashboard.png` | Invalid credentials produce an error, valid login opens Dashboard; reload restores the authenticated session. These are real session/CSRF checks. |
| 2. Endpoint | Show A and compare B; use management suite endpoint/version output | A and B are independently enrolled HTTP test endpoints. `desired` is the server's requested policy; `applied` changes only through an accepted ACK. ONLINE is telemetry, not hardware protection evidence. |
| 3. Event | Show `m4-live-events.png`, then `events-real-http-desktop.png` from `test:browser:live` | Explain endpoint, Linux user, USB, event/decision and source time. State both displays contain **synthetic** test events persisted by the real backend. The second suite starts with 25 events and verifies 20+5 pages. |
| 4. Allow on A | Show `m4-live-allow-a.png`, then `m4-live-http-ack.png` | Allow increases A desired version while applied remains unchanged; duplicate Allow is idempotent. A deliberately future ACK is rejected (409); the controlled current HTTP ACK updates applied. No local USBGuard apply has occurred in this demo. |
| 5. Revoke A; B unchanged | Show `m4-live-revoke-a.png` and the A/B assertion output | Revoke increments A desired version again; applied retains the last ACK and the UI shows pending state. B endpoint details and whitelist equal their pre-action snapshots. Cross-endpoint deletion is rejected (404). Never describe this as global Allow. |
| 6. Server down/recover — browser view | In the Events suite, explain the injected 403, 500 and network failure assertions, then recovery by Refresh | An explicit error replaces protected rows; recovery reads real API data. **These are intercepted browser requests**, so this segment proves UI error handling, not a stopped JVM or offline Agent enforcement. |
| 7. Server down/recover — real restart | Show `m4-live-expired-session.png` and management suite restart/persistence output | The harness stops and starts its isolated Spring Boot process with the same H2. Policy/event state persists; the old browser session gets 401, protected rows disappear and login works again. The harness does not keep the page polling during the stopped interval. Client reboot and local fail-closed behavior remain deferred. |
| 8. Event detail and finish | Show `events-username-substring.png`, `events-week3-unknown-no-serial.png`, `events-week3-endpoint-b.png`, `events-week3-endpoint-a.png`, and `events-real-http-mobile.png` | The three extra records bring total to 28 and keep shared no-serial hash, A/B attribution, UNKNOWN and fractional source time. Names/IDs and partial case-insensitive username search operate over persisted history. The `week3` filenames are the existing harness names; use copies from this **Week 4** execution. Check mobile and logout clearing. |

For precise policy versions, use the run's printed `beforeA`, `allowedA`, `revokedA` and persisted values, not hard-coded numbers from a previous run. If a suite fails, retain its current log and show that status. Historical screenshots support historical results only.

## Rehearsal and recording sheet

| Field | Current value / completion rule |
|---|---|
| Prepared script | Complete, 2026-10-28 |
| Fresh automated Windows rehearsal | **PASS**: fixture 15, management 16, standalone policy 17, Events 18, packaged-JAR 19; [current run record](qa/week4-evidence/2026-10-28/windows-regression/README.md) |
| Interactive/narrated walkthrough | NOT EXECUTED; record presenter, reviewer, date, duration, exact source and observed problems when performed |
| Video | NOT RECORDED; a video file needs checksum, duration and an explicit synthetic-data scope statement |
| Browser screencast replay | **CAPTURED**, 207 actual CDP JPEG frames / 6,764,775 bytes, login/password excluded; [timings and hashes](qa/week4-evidence/2026-10-28/windows-regression/recording/frames.json), [playable replay](qa/week4-evidence/2026-10-28/windows-regression/recording/replay.html); no encoded video/audio |
| M4 shared frontend review | **Received and technically acknowledged on 2026-10-30** for the same frontend reviewed by M4 at `6127766`; [M5 source-equivalence ACK](qa/evidence/M5_WEEK4_POST_PULL_ACK_20261030.md). Recheck if candidate frontend/API changes. |
| Candidate owner acceptance | M1 and owners confirm final SHA/artifacts/contract; rerun affected checks if the candidate changes |
| Live Ubuntu rehearsal | NOT EXECUTED / DEFERRED until after Week 4; requires M2/M3, hardware and GUI environment |

## Technical questions for the presenter

| Question | Answer |
|---|---|
| What blocks the device? | USBGuard performs local authorization. Agent resolves the active session, handles telemetry and pulls/reconciles endpoint policy; the server and React manage desired state. HTTP ACK evidence proves the server/UI contract; real apply needs Ubuntu evidence. |
| Does the username prove who physically inserted the USB? | It is the active local Ubuntu session snapshot at event time. It does not establish the physical person's identity. Unresolvable/ambiguous sessions return UNKNOWN; the service account is not a substitute for the desktop user. |
| Can a tty-only user be known without a popup? | Yes. A uniquely identified active local tty session can retain username/UID/session. A desktop popup separately requires a valid graphical session/helper; absence of GUI alone does not force UNKNOWN. |
| What happens when the server is down? | The intended design retains local USBGuard enforcement and retries saved events/ACKs when connectivity returns. Windows unit/mock and browser error tests cover parts of that design. Physical fail-closed behavior, persistence/restart and delivery must be measured on Ubuntu. |
| Why can Allow look pending? | Desired and applied are separate. Only a successful local reconcile plus durable applied-version save permits the real Agent to ACK. Server success alone cannot prove enforcement. |
| What about no-serial USBs? | A stable USBGuard hash can identify a particular device. The policy must not broaden to a VID:PID-only/model-wide allow. Validate the hash across real reconnects after Week 4. |
| Can a foreign USBGuard Allow override the desired whitelist? | In the current PR #57+ source, the ownership reconciler removes unauthorized foreign USBGuard ALLOW rules (after validating known syntax), re-lists rule IDs and withholds policy ACK on failure. Older Week 3/4 reports describe the previous preserve-foreign behavior; treat those records as historical. Verify this on an isolated Ubuntu VM and never bypass approval by adding broad Allows. |
| Are USBGuard numeric rule IDs stable? | No. The implementation re-lists and resolves the managed identity before each removal, including rollback. Current Windows unit regression covers the fix; external PR acceptance and native multi-rule behavior remain separate checks. |
| Does the reconciler accept every USBGuard rule syntax? | No. It verifies a conservative subset; unknown suffixes, numeric targets and unsupported conditions such as `if` cause failure/manual policy review. External changes can still race after a read, so controlled policy ownership is required. |
| Does a socket DELIVERED result prove a popup? | No. It means handoff to the helper. The new helper checks Linux `SO_PEERCRED` for sender `usbshield`; real connectivity, peer identity, per-UID ACL and native rendering still need Ubuntu evidence. The PR #50 code replaced the global 1777 directory and 0660 user socket with a logind-owned `/run/user/<UID>/usbshield-notifier/notice.sock`, named-user ACLs and kernel peer checks. M2/M3 must review that design and verify it on Ubuntu; do not claim native PASS from Windows Maven. |
| What does M1's production HTTP check prove? | Its isolated JAR serves React/API and enforces tested auth/policy/events/production-doc boundaries with synthetic data. The HTTP test overrides Secure cookies for loopback; it does not verify TLS or secure-cookie behavior in a deployed browser. M5's current S4 tests remain separately recorded. |
| Does successful heartbeat response itself say ONLINE? | The frozen response can report OK while the endpoint registry separately derives ONLINE from heartbeat timing. Use each field's contract; the first S4 Agent→Spring QA attempt incorrectly expected ONLINE in the response and is retained as a failed harness attempt. |
| Is every oversized request a 400? | Check the route and guard: validated DTO bounds generally return 400, while an oversized bootstrap credential can be rejected 401 by authentication first. The proposed canonical API documentation clarification is in the post-PR#50 docs-only review; M1 approval is still needed. Do not narrate a different result from the captured response. |
| Why is there no popup in this Windows demo? | The native helper is part of the Ubuntu graphical session. Eligible BLOCK/REJECT Mass Storage Insert must produce one popup for the resolved UID; Remove/HID/ALLOWED and unresolved users must not. Screenshot/mock delivery is not proof of a native popup. |
| Can a normal user or root disable protection? | Normal-user service/config restrictions must be verified on the target image. A root administrator can change services and policy; USBShield does not claim protection from root. |

## Ubuntu demonstration after Week 4

Use the [Ubuntu acceptance sheets](qa/WEEK4_UBUNTU_ACCEPTANCE.md), [Installation Guide](CLIENT_INSTALLATION.md), [M2 core guide/Q&A](usbguard/WEEK4_M2_TECHNICAL_QA.md), [native handoff](usbguard/WEEK4_M2_M3_M5_UBUNTU_HANDOFF.md) and received [M1 candidate/config/developer handoff](member1/WEEK4_SERVER_HANDOFF.md). Final group candidate, real `.deb`, deployment rotation and M2/M3 secure IPC/native policy checks remain prerequisites. Received M1 documentation is not a reason to claim those native steps ran.

1. Clean-install the owner-built/checksummed `.deb`, configure protected deployment settings and show Agent/USBGuard state plus endpoint ONLINE.
2. With a known active GUI user, insert unknown physical class-08 storage. Correlate real BLOCK, the correct user/session/endpoint and one native popup with the persisted Web event. Demonstrate USB HID operation.
3. Allow only A through Admin Web, verify real reconcile/durable ACK. **Unplug and reinsert** the USB to verify ALLOW on A; B stays BLOCKED. Revoke A, await the new policy ACK, **unplug and reinsert** to verify BLOCK again. Immediate policy change on a still-attached USB is out of scope.
4. Demonstrate no-serial/hash identity, user switching and UNKNOWN behavior where the lab can reproduce them.
5. Stop only the isolated test server using M1's recorded process/command; verify physical local enforcement, spool persistence and eventual delivery after recovery.
6. Demonstrate notification exclusions/helper failure, service recovery, reboot persistence and package lifecycle according to the recording sheets.

For the current Agent watch changes, M2/M3 must also demonstrate recovery after watch/daemon loss, discard incomplete input across reconnects, deduplicate replay within the short dedup window, preserve Remove→Insert transitions and recover from malformed/oversized input without invented events or repeated popups. Windows mocks for these behaviors do not establish actual daemon recovery; use the bounded-input/reconnect extension in the Ubuntu sheets.

Current Ubuntu execution for all six steps: **NOT EXECUTED / DEFERRED**. No Windows result closes this gate.

Historical references: [Week 3 evidence index](qa/week3-evidence/README.md) and [24/10 integrated capture](qa/week3-evidence/2026-10-24/integrated-regression/README.md). Retain those unchanged; new evidence belongs under Week 4.
