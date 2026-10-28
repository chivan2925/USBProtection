# USBShield Week 4 demo script

Owner: M5. Prepared and automatically rehearsed: 2026-10-28 (Asia/Bangkok). Work item: **W4-M5-04**. Script/Q&A and fresh automated Windows fixture/management/Events/packaged-JAR rehearsals are **PASS** on the documented baseline; see the [Week 4 run record](qa/week4-evidence/2026-10-28/windows-regression/README.md). An [actual authenticated browser screencast replay](qa/week4-evidence/2026-10-28/windows-regression/recording/replay.html) contains 207 CDP frames with original capture timings. **No encoded video, audio or narrated live walkthrough was produced.** Ubuntu live demonstration remains **NOT EXECUTED / DEFERRED until after Week 4**.

Use the [test inventory](qa/WEEK4_TEST_INVENTORY.md), [release validation](qa/WEEK4_RELEASE_VALIDATION.md), [Event History review](qa/WEEK4_EVENT_HISTORY_REVIEW.md), [evidence index](qa/week4-evidence/README.md) and [Ubuntu recording sheets](qa/WEEK4_UBUNTU_ACCEPTANCE.md) with this script. The current source baseline is `2013ce726ef8cc960b30720435be23e8ccef1903`; record the actual tested HEAD and working-tree hashes again for every run. The [Week 3 owner decision](qa/WEEK3_FINAL_OWNER_CLOSEOUT_20261028.md) is **PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED**, with H05 closed; it is historical acceptance, not a Week 4 test result.

## Windows preparation

Use JDK 21+, Maven, compatible Node/npm and a locally installed Chrome/Edge. Set `JAVA_HOME` to the chosen JDK; set `CHROME_PATH` if Chromium is outside the harness's standard Windows locations. Build from repository root, preserving command output and exit codes:

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
| M4 shared frontend review | Carry Week 3 acceptance as history; obtain Week 4 review for the tested candidate and Event History handoff |
| Candidate owner acceptance | M1 and owners confirm final SHA/artifacts/contract; rerun affected checks if the candidate changes |
| Live Ubuntu rehearsal | NOT EXECUTED / DEFERRED until after Week 4; requires M2/M3, hardware and GUI environment |

## Technical questions for the presenter

| Question | Answer |
|---|---|
| What blocks the device? | USBGuard performs local authorization. Agent resolves the active session, handles telemetry and pulls/reconciles endpoint policy; the server and React manage desired state. HTTP ACK evidence proves the server/UI contract; real apply needs Ubuntu evidence. |
| Does the username prove who physically inserted the USB? | It is the active local Ubuntu session snapshot at event time. It does not establish the physical person's identity. Unresolvable/ambiguous sessions return UNKNOWN; the service account is not a substitute for the desktop user. |
| What happens when the server is down? | The intended design retains local USBGuard enforcement and retries saved events/ACKs when connectivity returns. Windows unit/mock and browser error tests cover parts of that design. Physical fail-closed behavior, persistence/restart and delivery must be measured on Ubuntu. |
| Why can Allow look pending? | Desired and applied are separate. Only a successful local reconcile plus durable applied-version save permits the real Agent to ACK. Server success alone cannot prove enforcement. |
| What about no-serial USBs? | A stable USBGuard hash can identify a particular device. The policy must not broaden to a VID:PID-only/model-wide allow. Validate the hash across real reconnects after Week 4. |
| Why is there no popup in this Windows demo? | The native helper is part of the Ubuntu graphical session. Eligible BLOCK/REJECT Mass Storage Insert must produce one popup for the resolved UID; Remove/HID/ALLOWED and unresolved users must not. Screenshot/mock delivery is not proof of a native popup. |
| Can a normal user or root disable protection? | Normal-user service/config restrictions must be verified on the target image. A root administrator can change services and policy; USBShield does not claim protection from root. |

## Ubuntu demonstration after Week 4

Use the [Ubuntu acceptance sheets](qa/WEEK4_UBUNTU_ACCEPTANCE.md), the [Client Installation Guide](CLIENT_INSTALLATION.md), [M2 core handoff](usbguard/WEEK3_PART3_SESSION_NOTIFICATION_HANDOFF.md) and M1/M4's candidate artifacts. M2/M3 must first confirm USBGuard policy/IPC, notifier provisioning and the package/installation procedure on the actual lab image.

1. Clean-install the owner-built/checksummed `.deb`, configure protected deployment settings and show Agent/USBGuard state plus endpoint ONLINE.
2. With a known active GUI user, insert unknown physical class-08 storage. Correlate real BLOCK, the correct user/session/endpoint and one native popup with the persisted Web event. Demonstrate USB HID operation.
3. Allow only A through Admin Web, verify real reconcile/durable ACK and ALLOW after reconnect. Show the same device still BLOCKED on B. Revoke A and verify BLOCK after reconnect.
4. Demonstrate no-serial/hash identity, user switching and UNKNOWN behavior where the lab can reproduce them.
5. Stop only the isolated test server using M1's recorded process/command; verify physical local enforcement, spool persistence and eventual delivery after recovery.
6. Demonstrate notification exclusions/helper failure, service recovery, reboot persistence and package lifecycle according to the recording sheets.

Current Ubuntu execution for all six steps: **NOT EXECUTED / DEFERRED**. No Windows result closes this gate.

Historical references: [Week 3 evidence index](qa/week3-evidence/README.md) and [24/10 integrated capture](qa/week3-evidence/2026-10-24/integrated-regression/README.md). Retain those unchanged; new evidence belongs under Week 4.
