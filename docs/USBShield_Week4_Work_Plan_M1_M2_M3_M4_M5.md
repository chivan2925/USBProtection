# USBShield Ubuntu — Kế hoạch công việc Week 4 (M1–M5)

> **Chủ đề:** Stabilization, Documentation, Demo, Release  
> **Căn cứ chính:** `USBShield_Project_Blueprint_Week2_Client_Notification_Updated(1)(10).md` — mục 27 (WEEK 4), kết hợp mục 22–24, 28, 30–31, 34 và 42.  
> **Baseline:** Week 3 đã được nhóm chốt `PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`.  
> **Trạng thái tài liệu:** KẾ HOẠCH — các ô `[ ]` chưa phải bằng chứng đã thực hiện.  
> **Quyết định điều chỉnh lịch của nhóm:** Trong Week 4 ưu tiên hoàn thiện code, tài liệu, build/artifact, hồi quy Windows và chuẩn bị demo. **Kiểm thử thực tế Ubuntu 24.04, USB vật lý, USBGuard daemon, systemd, reboot, native GUI và clean install `.deb` được thực hiện sau Week 4**. Không tự đánh dấu PASS cho các bài chưa chạy.

## 1. Mục tiêu và nguyên tắc chung

1. **Không thêm feature lớn.** Chỉ sửa lỗi/blocker, ổn định tích hợp, khóa API/contract và chuẩn bị bản release candidate.
2. Đảm bảo chức năng cốt lõi không bị regression: **unknown USB Mass Storage BLOCK**, non-storage/HID được phép hoạt động, active Ubuntu user attribution, Allow/Revoke **theo endpoint**, local policy fail-closed khi server down, và native notification đúng graphical session (chức năng từ Week 2).
3. Giữ ownership: M1 quản lý `admin-server/` và tài liệu backend; M2 quản lý Java `usbguard/`, `session/`, `notification/`; M3 quản lý Agent `endpoint/`, `sync/`, `outbox/`, config, system và `packaging/` (trừ `packaging/usbguard/`); M4 quản lý frontend Admin; M5 quản lý Event History/QA.
4. Chỉ sửa shared files sau khi thống nhất owner và có review chéo. Không sửa code M2 từ phạm vi M3 nếu chưa được M2 review.
5. Phân biệt **unit/mock PASS**, **Windows HTTP/browser PASS**, **static/build PASS**, **Ubuntu E2E NOT EXECUTED**. Không được chuyển kết quả của nhóm này sang nhóm khác.
6. Không commit credentials, H2 runtime database, spool sự kiện hoặc artifact có dữ liệu cá nhân. Kiểm tra và xử lý credential từng xuất hiện trong lịch sử Git trước khi đưa lên môi trường dùng thật.
7. Mỗi đầu việc hoàn thành phải có **commit/PR, kết quả kiểm tra, bằng chứng, và cập nhật tài liệu liên quan**; không tạo file trùng chỉ để đúng tên gợi ý.

**Mức ưu tiên:** `P0` = cần hoàn tất trước khi chốt Week 4 preparation/release candidate; `P1` = cải thiện nếu phù hợp, không mở rộng tính năng.

## 2. Tổng quan phân công

| Thành viên | Trọng tâm Week 4 | Đầu ra cần bàn giao |
|---|---|---|
| **M1** — Admin Server / Integration Owner | API freeze, security/code review, tài liệu kiến trúc/developer, server JAR, điều phối release | API/contract đã khóa; tài liệu backend đồng bộ; server JAR có provenance; tổng hợp quyết định tích hợp |
| **M2** — USBGuard / Active User / Notification | Stress reconnect bằng mô phỏng, xác minh chính sách USBGuard, attribution và notifier, kỹ thuật Q&A | Test/fixtures, review core, tài liệu policy/session/notification, bộ câu hỏi đáp kỹ thuật |
| **M3** — Client Agent / Packaging | Ổn định retry/persistence, build `.deb`, script/service/IPC, hướng dẫn cài đặt | Client Agent JAR, `.deb` + checksum/provenance (nếu môi trường build cho phép), Client Installation Guide |
| **M4** — Admin Frontend | Fix UI, ngăn fake/mock production, Admin Guide và screenshots, tích hợp cuối | React production build, UI regression, Admin Guide final, ảnh minh họa |
| **M5** — Event History / QA / Release Validation | Ma trận test cuối, regression, tổng duyệt demo, evidence/video, release report | Test matrix, Demo Script final, evidence index, release-candidate validation report |

## 3. M1 — Admin Server Lead / Architecture / Integration Owner

**Phạm vi:** `admin-server/**`, `README.md`, `docs/ARCHITECTURE.md`, `docs/DEVELOPER_GUIDE.md`, `docs/API.md`, `docs/AGENT_SERVER_PROTOCOL.md` (phối hợp M2/M3).

| ID | Ưu tiên | Công việc | Điều kiện hoàn thành |
|---|---|---|---|
| W4-M1-01 | P0 | **API/DTO freeze**: rà soát Admin Auth, endpoint, event, whitelist, Agent enroll/heartbeat/policy/ACK; khóa semantic `desired` ≠ `applied`, endpoint isolation, event attribution. | Có danh sách API/contract canonical, thay đổi breaking được loại bỏ hoặc được tất cả owner xác nhận. |
| W4-M1-02 | P0 | **Security/code review**: session/CSRF, Agent token binding, 401/403/409, admin bootstrap, cấu hình production, bảo vệ secret và logging. | Không còn P0 security finding chưa xử lý trong phạm vi triển khai; kết quả review được ghi nhận. |
| W4-M1-03 | P0 | **Regression backend + integration**: status timeout ONLINE/OFFLINE, policy version/ACK, per-endpoint A/B, H2 persistence và React same-origin; xác minh không regression sau merge. | Maven/backend và HTTP/browser integration PASS trên release-candidate commit. |
| W4-M1-04 | P0 | **Tài liệu**: hoàn thiện Architecture, Developer Guide, API, Agent–Server Protocol, README; ghi rõ vai trò client/service, profile production, H2 và giới hạn security. | Tài liệu khớp implementation, có ví dụ triển khai và không chứa credential thật. |
| W4-M1-05 | P0 | **Server JAR**: build artifact chứa React production assets; ghi SHA/commit, phiên bản, toolchain, checksum, hướng dẫn start/config. | JAR khởi chạy và phục vụ SPA + `/api` đúng cùng origin trong bài test khả thi trên Windows. |
| W4-M1-06 | P0 | **Integration owner**: rà soát PR M2–M5, cập nhật release manifest, phối hợp M5 xác nhận các gate Windows trước khi đặt release-candidate tag. | Các owner ký xác nhận handoff; tag/release candidate có SHA và không mô tả nhầm thành full Ubuntu release. |
| W4-M1-07 | P1 | Theo dõi **rotation/re-enrollment** cho credential đã từng xuất hiện trong Git history. | Có xác nhận từ người quản trị môi trường; nếu chưa xử lý thì ghi OPEN, không tự báo hoàn thành. |

**Bàn giao:** bản JAR và checksum; danh sách frozen API/DTO; `README.md`, Architecture, Developer Guide, API/Protocol; phiếu review tích hợp gửi M3/M4/M5.

## 4. M2 — USBGuard Core / Active User Attribution / Desktop Notification

**Phạm vi:** `client-agent/src/main/java/com/group/usbshield/agent/{usbguard,session,notification}/**`, `event/UsbEventEnricher.java`, tests tương ứng; `packaging/usbguard/**`; `docs/usbguard/**`.

| ID | Ưu tiên | Công việc | Điều kiện hoàn thành |
|---|---|---|---|
| W4-M2-01 | P0 | **Stress reconnect/recovery** bằng fixture/mocks: USBGuard watch drop/restart, lặp Insert/Remove, duplicate, malformed/oversized input, daemon unavailable. | Listener không treo, không phát telemetry/popup trùng, không false-success; regression PASS. |
| W4-M2-02 | P0 | **Review local USBGuard policy**: base allow non-storage, class `08` block mặc định, device-specific allow, rule ordering, hash/no-serial identity, reject fingerprint yếu, preserve foreign/base rules. | Không có `allow` quá rộng hoặc fallback fail-open từ các case mô phỏng; không phá keyboard/mouse trên mô hình policy. |
| W4-M2-03 | P0 | **Active-user attribution**: active local unique user, switch/logout, concurrent ambiguous, remote-only, seat change, no GUI → `UNKNOWN` khi không chắc. | Không gán nhầm username/UID/session; các session tests PASS. |
| W4-M2-04 | P0 | **Notification correctness**: chỉ Insert + Mass Storage + BLOCK/REJECT, một popup mỗi lần cắm; không popup ALLOWED/HID/Remove, notifier thất bại không ảnh hưởng event/enforcement. | Unit/mocks PASS; runtime GUI thực tế vẫn `DEFERRED_UBUNTU`. |
| W4-M2-05 | P0 | **Tài liệu core**: giải thích interface class `08`, rule order, USBGuard hash/serialless, `loginctl`/session resolution, watch recovery, notification UID/session, quyền IPC. | Tài liệu khớp code, có câu lệnh kiểm tra và cảnh báo khi thực thi thật trên Ubuntu. |
| W4-M2-06 | P0 | **Technical Q&A/demo handoff**: chuẩn bị giải thích USBGuard vs Agent, không thể chống root, không thể xác minh người cắm vật lý, cách Allow/Revoke local và offline enforcement. | Có bộ Q&A, checklist/expected output và hướng dẫn M3/M5 kiểm chứng trên Ubuntu sau Week 4. |
| W4-M2-07 | P1 | Review giao diện và quyền USBGuard IPC với M3 trước release candidate. | Kết luận bằng văn bản; chỉ sửa phạm vi M2-owned khi thật sự cần. |

**Bàn giao:** báo cáo M2 core review; test results; `docs/usbguard/` cập nhật; checklist Ubuntu test cho M5; bộ Q&A ngắn.

## 5. M3 — Client Agent / System Service / Packaging Engineer

**Phạm vi:** Agent `config/**`, `endpoint/**`, `sync/**`, `outbox/**`, `system/**`, `event/EventSenderService.java`; `packaging/{systemd,agent-deb,scripts,desktop}/**`; `docs/CLIENT_INSTALLATION.md`, `docs/client-agent/**`.

| ID | Ưu tiên | Công việc | Điều kiện hoàn thành |
|---|---|---|---|
| W4-M3-01 | P0 | **Stabilize Agent lifecycle**: enrollment/credential durability, heartbeat, policy pull/apply/ACK retry, spool FIFO, network outage/recovery, rollback khi disk-save fail. | Không false-ACK, không đánh mất event đã lưu qua retry/restart trong regression; Maven PASS. |
| W4-M3-02 | P0 | **Security/permissions review**: non-root service, quyền data/config, USBGuard IPC, notifier per-UID socket; live profile tắt simulation, không nhúng secret. | Static/contract tests PASS; không lẫn normal-user permission với service privilege. |
| W4-M3-03 | P0 | **Final Debian packaging**: kiểm tra `control`, `postinst`, `prerm`, `systemd`, XDG autostart, Java 21/libnotify dependency; build JAR + staging + `.deb` trong Linux/CI có `dpkg-deb` nếu khả dụng. | Có artifact `usbshield-agent_1.0.0_amd64.deb`, checksum và build/staging logs; nếu chưa build được thì ghi chính xác `NOT BUILT`, không tự nhận PASS. |
| W4-M3-04 | P0 | **Installer/release safety**: script fail rõ khi thiếu JAR, config/rules không fail-open, không xóa local whitelist/credentials ngoài ý muốn khi upgrade/remove. | Kiểm tra tĩnh Windows + shell syntax + package-content inspection PASS; runtime install chỉ kiểm tra sau Week 4. |
| W4-M3-05 | P0 | **Client Installation Guide**: install, cấu hình server URL/enrollment token, quyền file, systemctl/journalctl, upgrade/uninstall/reinstall, rollback, troubleshooting và cách kiểm tra notifier/IPC. | Có guide làm theo được, lệnh phân biệt admin vs normal user, tách rõ lệnh chưa chạy thực tế. |
| W4-M3-06 | P0 | **Chuẩn bị Ubuntu acceptance**: chuẩn hóa checklist cài mới, reboot, service protection, failure/recovery, Agent–Server/USBGuard correlation cùng M2/M5. | M5 nhận được test steps, expected evidence và các giới hạn; case runtime vẫn `NOT EXECUTED`. |
| W4-M3-07 | P1 | Kiểm tra khả năng build lặp lại, version/manifest, SBOM/dependency audit nếu đã có workflow. | Không thêm hạ tầng phức tạp; không thay đổi phiên bản dependency không cần thiết. |

**Bàn giao:** Agent JAR; `.deb`/checksum/log (nếu đã build), packaging manifest, Client Installation Guide, checklist triển khai và log evidence. **Không sửa Java `usbguard/`, `session/`, `notification/` thuộc M2.**

## 6. M4 — Admin Frontend Lead

**Phạm vi:** `frontend/` (Login, Dashboard, Endpoints/Detail, Devices, whitelist, shared auth/API adapter phần M4); `docs/ADMIN_GUIDE.md`, `docs/admin-web/**`.

| ID | Ưu tiên | Công việc | Điều kiện hoàn thành |
|---|---|---|---|
| W4-M4-01 | P0 | **UI stabilization**: fix bug/visual regressions từ Week 3; kiểm tra loading/empty/error/retry, session expiry, CSRF, responsive desktop/mobile. | Lint, typecheck/build, unit/component và browser suites PASS. |
| W4-M4-02 | P0 | **No production mock/fake state**: login/API production chỉ lấy từ backend; demo fixture được nhận diện và tách biệt. Không hiển thị protected/ONLINE/ALLOWED khi thiếu evidence. | Error/empty states trung thực, không reuse stale protected rows. |
| W4-M4-03 | P0 | **Policy UX cuối**: Allow/Revoke theo endpoint, duplicate/idempotency, A/B isolation; hiển thị desired khác applied và pending/unknown ACK. | Browser integration với Spring Boot/H2 PASS; không xuất hiện global Allow. |
| W4-M4-04 | P0 | **Admin Guide final**: login, dashboard, endpoints, detail, filters, Allow/Revoke, xử lý lỗi/hết phiên, cảnh báo dữ liệu demo. | Hướng dẫn dựa trên UI hiện tại, screenshots thật từ giao diện đã test, không chứa credential. |
| W4-M4-05 | P0 | **Production frontend build/handoff**: đóng băng app shell và shared files, bàn giao `frontend/dist` cho M1; review cùng M5 đối với Event API adapter và browser harness. | Production build PASS, asset tích hợp server JAR, không conflict với M5. |
| W4-M4-06 | P1 | Tinh chỉnh accessibility/UX nhỏ nếu không đụng contract hoặc mở rộng tính năng. | Không tạo thêm feature và có test bảo vệ. |

**Bàn giao:** React production build; `docs/ADMIN_GUIDE.md`; screenshots cập nhật; UI regression report; xác nhận shared-file review với M5.

## 7. M5 — Event History / QA / Release Validation

**Phạm vi:** Event History UI/mapper/filter/tests của `frontend/`; `docs/TEST_PLAN.md`, `docs/DEMO_SCRIPT.md`, `docs/qa/**`.

| ID | Ưu tiên | Công việc | Điều kiện hoàn thành |
|---|---|---|---|
| W4-M5-01 | P0 | **Final test matrix**: mapping Blueprint T01–T28 và DoD, case ID, owner, môi trường, nguồn dữ liệu, expected/actual, commit SHA, PASS/FAIL/NOT EXECUTED. | Không dùng evidence Windows làm bằng chứng USB vật lý; các case Ubuntu đánh `DEFERRED`. |
| W4-M5-02 | P0 | **Regression tích hợp cuối**: M1 API + M2/M3 Agent + M4/M5 frontend; kiểm thử auth/session, endpoint A/B, version/ACK, no-serial/UNKNOWN, paging/date/decision, mất mạng và lỗi dữ liệu. | Maven/npm/static + real HTTP/browser test PASS ở release-candidate SHA, không phát sinh P0 blocker. |
| W4-M5-03 | P0 | **Event History sign-off**: kiểm tra endpoint, Linux user, USB, event/decision, source time, filter/paging và dữ liệu giả lập được gắn nhãn. | Không bị sai user/endpoint, không tự tạo dòng mock trong production; M4 đã review phần shared frontend. |
| W4-M5-04 | P0 | **Demo rehearsal**: cập nhật Demo Script, tổng duyệt trình tự Login → Endpoint → Event → Allow/Revoke A → B unchanged → server down/recover. | Demo Windows có thể dùng HTTP synthetic/fixture và phải nói rõ; live Ubuntu demo thực tế vẫn chờ sau Week 4. |
| W4-M5-05 | P0 | **Evidence backup**: lưu logs, screenshots, video, checksum/manifest, environment/tool versions, test command và exit code; che credential/PII. | Bằng chứng truy vết được về đúng commit và trạng thái test, không ghi đè evidence Week 1–3. |
| W4-M5-06 | P0 | **Release validation report**: tổng hợp kết quả của M1–M4, source/build provenance, bug còn mở, security note, phạm vi chưa chạy; đề xuất go/no-go cho release candidate. | Có quyết định rõ: Windows preparation PASS hay BLOCKED; Ubuntu full gate chưa được nhận PASS. |
| W4-M5-07 | P1 | Xây dựng bộ phiếu ghi nhận test chạy thật trên Ubuntu để dùng ngay sau Week 4. | Các test T01–T28 liên quan Ubuntu có lệnh/expected/evidence cần thu; chưa ghi kết quả giả. |

**Bàn giao:** final Test Plan, Test Inventory, Demo Script, QA/evidence index, release validation report và checklist Ubuntu E2E sau Week 4.

## 8. Thứ tự tích hợp và lịch đề xuất trong Week 4

| Mốc | Owner chính | Công việc | Bàn giao |
|---|---|---|---|
| **Đầu tuần** | M1 + tất cả | Chốt freeze candidate, phân công issue, xác định source SHA, rà soát rủi ro/security và contract | API freeze list; bug list |
| **Giữa tuần (1)** | M2 + M3 | Stress/retry/policy/session tests; review packaging, fix blocker; build Agent JAR và `.deb` nếu có Linux/CI | Core/Agent regression; artifact provenance |
| **Giữa tuần (2)** | M4 + M5 | UI fixes, Event History regression, Admin Guide, screenshot; cập nhật test matrix/demo | Frontend build; QA draft |
| **Cuối tuần (1)** | M1 + M3 + M4 | Build production frontend + server JAR, chốt Agent package, review bàn giao | Release-candidate artifacts + checksums |
| **Cuối tuần (2)** | M5 + toàn nhóm | Run Windows integrated regression, tập demo, rà soát tài liệu, lập release report, quyết định candidate | Week 4 preparation/release-candidate decision |

**Phụ thuộc cần giữ:** M2 USBGuard/session contract → M3 Agent integration → M1 server API → M4 frontend; M5 tổng hợp end-to-end và nhận evidence từ tất cả.

## 9. Week 4 gate theo lịch điều chỉnh

### 9.1. Gate có thể chốt trong Week 4 — Windows + static/build + documentation

- [ ] **M1:** API/contract freeze và security review có kết quả; Admin Server Maven PASS, server JAR + React integrated HTTP/browser PASS; Architecture/Developer Guide/API/Protocol đồng bộ.
- [ ] **M2:** USBGuard policy/session/watch/notification mock regression PASS; core documentation và technical Q&A hoàn chỉnh.
- [x] **M3:** Client Agent Maven PASS; packaging static/JAR/staging checks PASS; `.deb` build có artifact/provenance nếu môi trường cho phép, hoặc công khai `NOT BUILT` và lý do; Client Installation Guide hoàn chỉnh.
- [ ] **M4:** `npm ci`, lint, build, tests, browser/live regression PASS; no mock in production; Admin Guide + screenshots final.
- [ ] **M5:** Test matrix, Demo Script, evidence backup, release validation report PASS; không có P0 blocker chưa được disposition.
- [ ] **Cả nhóm:** PR/commit đã review, owner sign-off, ghi nhận SHA và release-candidate status đúng; không dùng nhãn `FULL PASS` cho Ubuntu.

### 9.2. Release gate gốc của Blueprint — **chưa được chốt trong Week 4**

Các nội dung sau phải thực thi trên môi trường Ubuntu 24.04 sau Week 4, không tự điền PASS:

- [ ] Clean `.deb` install, upgrade/uninstall/reinstall và service/permissions.
- [ ] Agent enroll/heartbeat, USBGuard daemon enforcement và local policy khi server down.
- [ ] USB Mass Storage lạ thực sự BLOCK; keyboard/mouse không bị ảnh hưởng.
- [ ] Event có đúng endpoint + active Ubuntu username/session; `UNKNOWN` khi không có session xác định.
- [ ] Per-endpoint Allow/Revoke thực tế và A/B isolation.
- [ ] Reboot, Agent/systemd restart, local policy persistence, normal user không bypass.
- [ ] Native desktop BLOCK notification đúng active GUI user; không duplicate, không nhầm UID/session; notifier fail không làm mất enforcement/event.

> **Cách gọi trạng thái:** Nếu toàn bộ mục 9.1 đạt, có thể ghi `WEEK 4 — WINDOWS/RELEASE-CANDIDATE PREPARATION PASS; UBUNTU E2E DEFERRED`. Chỉ sau khi mục 9.2 đã chạy và PASS mới được xem xét `FULL RELEASE PASS`.

## 10. Mẫu evidence/handoff chung

Mỗi owner bàn giao tối thiểu:

| Trường | Yêu cầu |
|---|---|
| `Owner / Work ID` | Ví dụ `M3 / W4-M3-03` |
| `Base SHA / Final SHA` | Commit code thực tế dùng để kiểm tra |
| `Source changed` | Danh sách file mới/sửa, lý do, reviewer |
| `Environment` | OS, Java/Maven/Node/npm hoặc Ubuntu/USBGuard tùy bài test |
| `Command + exit code` | Chính xác lệnh thực thi và kết quả |
| `Evidence` | Log/screenshot/video/artifact/checksum; không nhúng token |
| `Verdict` | PASS / FAIL / NOT EXECUTED / DEFERRED + giải thích |
| `Handoff` | Các member phụ thuộc và công việc còn mở |

## 11. Các việc không chuyển nhầm phạm vi

- **Week 4 không làm thêm AI, cloud, microservice, global whitelist hoặc dashboard realtime.**
- **Không sửa lịch sử Week 3 đã PASS (Windows Scope).** Lỗi regression mới phải có issue và bằng chứng mới.
- **Không dùng Windows mock để khẳng định Ubuntu USBGuard, native notification hoặc installer đã chạy thực tế.**
- **Không yêu cầu M3 sửa trực tiếp USBGuard/session/notification của M2.**
- **Không tạo tag “final release” khi Ubuntu E2E và security deployment chưa được xác nhận.** Có thể tạo release candidate/tag pre-release nếu M1 và nhóm thống nhất.
- Các mục Blueprint Week 4 `clean install final`, `service/reboot/logs` và `RELEASE GATE` được **chuẩn bị bằng tài liệu/artifact trong Week 4**, còn **thực thi Ubuntu sau Week 4**, đúng lịch test mà nhóm đã chốt.

---

**Tài liệu tham chiếu nội bộ:** Blueprint mục 27, 23, 28, 30, 31, 34 và 42; quyết định Week 3 `PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`.
