# M5 Week 3 — hoàn tất kiểm thử và hồ sơ QA Windows

Cập nhật **2026-10-24, Asia/Bangkok (+07:00)**. Branch `huy`, revision `a0ad421901d5cb67fbe1b27ea9a74c05dfe2c3c1`. [Inventory hiện tại](WEEK3_TEST_INVENTORY.md), [QA summary](WEEK3_QA_SUMMARY.md), [log và provenance](week3-evidence/2026-10-24/integrated-regression/README.md).

**M5 đã thực hiện xong phần kiểm thử và hồ sơ QA khả thi trên Windows. Event History và các regression ứng dụng đã chạy PASS. Group Week 3 gate vẫn OPEN:** có một lỗi verifier packaging đã tái hiện, các câu hỏi coverage/packaging của M3 và quyết định nhóm chưa được đóng. Ubuntu U01–U09 vẫn **NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4**.

Lượt này không sửa application source, test hay packaging của các owner. Chỉ bổ sung/cập nhật tài liệu và evidence; các harness tạm nằm trong thư mục artifact bị Git ignore. M5 không tự xác nhận acceptance thay M1–M4.

## Công việc M5 đã hoàn thành

| Hạng mục Week 3 của M5 | Trạng thái thực tế |
|---|---|
| Events UI/filter/table | Đã có trên revision mới; regression xác minh endpoint/user/USB/event/decision/time, tên hoặc ID, partial username, loading/empty/error và không fallback mock. |
| Mapper/types/filter contracts | Regression PASS cho UNKNOWN, no-serial identity, source timestamp, malformed input, server totals/pagination và final-page boundaries. |
| Unit/component/API tests | **96/96 frontend PASS**; không sửa lại implementation đã đạt. |
| Browser Events với Spring Boot/H2 | PASS với 28 event HTTP tổng hợp; A/B attribution, no-serial/UNKNOWN, tên/ID/partial user, date/paging, auth/error/recovery/mobile. |
| Regression Windows tích hợp | **117 Agent**, **26 server**, lint/build, fixture/management/Events browser và policy HTTP riêng **1/1 PASS**. |
| JAR phục vụ React và API cùng origin | Build `-Pwith-frontend` PASS; browser giữ toàn bộ assertions Events, dùng JAR trực tiếp không Vite, PASS trên HTTP/default profile. Không phải browser HTTPS production. |
| Rà soát M2/M3 sau pull | Đã nhận/đọc report, đối chiếu test assertions và chạy Maven thật; H03 không còn chờ source/report. H04 đã nhận report nhưng còn các vấn đề ở dưới. |
| Packaging validation | Script Week 3 `-CheckBuiltJar` và bốn shell parse PASS trong phạm vi source/archive/syntax. Negative probe staging FAIL đúng theo kỳ vọng QA: verifier báo PASS sai khi staging không tồn tại. |
| Test plan/inventory/QA summary/evidence | Đồng bộ revision, ngày, counts, issue và owner. Không ghi đè log/snapshot ngày 21–23/10 hoặc Week 1/2. |
| Demo Script draft | Đồng bộ filter, artifact/evidence mới và phạm vi Windows/Ubuntu. Final rehearsal và final guides thuộc Week 4. |

## Kết quả độc lập trên revision mới

| Nhóm kiểm tra | Kết quả | Evidence |
|---|---|---|
| Agent `mvn -o -B -f client-agent/pom.xml clean package` | **117 tests**, 0 failures/errors/skips; JAR mới | [02](week3-evidence/2026-10-24/integrated-regression/02-agent-tests-package.txt) |
| Frontend install/test/lint/build | PASS; **96 tests**, lockfile Vite 8.3.1 | [01 install](week3-evidence/2026-10-24/integrated-regression/01-npm-ci.txt), [11 tests](week3-evidence/2026-10-24/integrated-regression/11-frontend-tests.txt), [12 lint](week3-evidence/2026-10-24/integrated-regression/12-frontend-lint.txt), [13 build](week3-evidence/2026-10-24/integrated-regression/13-frontend-build.txt) |
| Server `-Pwith-frontend clean package` | **26 tests**, 0 failures/errors/skips; JAR chứa React | [14](week3-evidence/2026-10-24/integrated-regression/14-server-with-frontend.txt) |
| Packaging static + shell syntax | PASS cho assertions hiện có và bốn script riêng; không build/install `.deb` | [15 static](week3-evidence/2026-10-24/integrated-regression/15-packaging-static.txt), [07–10 shell index](week3-evidence/2026-10-24/integrated-regression/README.md) |
| Fixture + management + Events browser | PASS; management có restart JVM thật, session expiry và persistence | [16 fixture](week3-evidence/2026-10-24/integrated-regression/16-browser-fixture.txt), [18 management](week3-evidence/2026-10-24/integrated-regression/18-browser-management.txt), [20 Events](week3-evidence/2026-10-24/integrated-regression/20-browser-events.txt) |
| Standalone policy HTTP contract | **1/1 PASS**, không nằm trong 96 npm tests; duplicate Revoke/future ACK/B isolation | [19](week3-evidence/2026-10-24/integrated-regression/19-policy-http-contract.txt) |
| Same-origin packaged-JAR browser | PASS; toàn bộ assertion Events giữ nguyên | [21](week3-evidence/2026-10-24/integrated-regression/21-browser-packaged-jar.txt), [variant provenance](week3-evidence/2026-10-24/integrated-regression/packaged-browser-variant.json) |
| Yêu cầu kiểm tra staging khi staging vắng mặt | **FAIL — M5-W3-06**; process exit 0 nhưng assertion nghiệm thu staging không đạt | [17 negative probe](week3-evidence/2026-10-24/integrated-regression/17-staging-negative-probe.txt), [precondition/result](week3-evidence/2026-10-24/integrated-regression/staging-probe.json) |

Bốn lần gọi Git Bash ban đầu bị sandbox chặn signal pipe/file mapping trước khi parse. Log 03–06 là lỗi môi trường, không phải FAIL cú pháp; retry được phép 07–10 đều exit 0. Không thay hoặc xóa log lỗi. Tổng cộng 21 command attempts: 16 lượt kiểm tra dương PASS, 4 lỗi launch đã giải quyết, 1 negative probe tái hiện lỗi verifier. Không kết luận mọi command exit 0 là mọi yêu cầu đã PASS.

## H03 — M2 đã có bằng chứng độc lập

[M2 core report](../usbguard/WEEK3_CORE_HARDENING.md) và [session/notification handoff](../usbguard/WEEK3_PART3_SESSION_NOTIFICATION_HANDOFF.md) đã có trong pull. Sáu suite mới sau đây đã PASS trong Maven/JUnit thật, bổ sung **48 test** so với snapshot Agent 61 test trước đây:

| Suite | Số test | Coverage đã đối chiếu |
|---|---|---|
| `UsbGuardWeek3ParsingAndIdentityTest` | 9 | Storage không serial/hash-only, reject identity yếu, malformed/duplicate/overflow, composite class 08. |
| `UsbGuardWeek3PolicyAndGatewayTest` | 11 | Reapply sau daemon restart mô phỏng, base/foreign rules, revoke-before-grant, apply/list/remove failure, không báo apply thành công giả. |
| `Week3ActiveSessionTransitionTest` | 9 | Switch/logout/remote-only/seat/ambiguity, invalid UID và UNKNOWN. |
| `Week3NotificationPolicyTest` | 6 | Cooldown reset/Remove, switch UID, graphical/no-GUI/UNKNOWN. |
| `UsbGuardWeek3WatchRecoveryTest` | 7 | Dedup/reinsert, partial-section reset, notifier failure, policy-event exclusion và oversized section. |
| `UsbGuardWeek3CloseoutAuditTest` | 6 | Base rule giả, foreign allow quá rộng, specific foreign rule và recovery sau lỗi enrich event. |

Đây là mock/fixture Windows; không chuyển các ca Linux/USB/GUI thật thành PASS. [JUnit summary](week3-evidence/2026-10-24/integrated-regression/junit-summary.json) lưu tên từng testcase, trạng thái, tổng số và hash report nguồn. **H03: DONE trong phạm vi review và execution của M5**; review acceptance của owner/nhóm được ghi riêng.

## H04 — M3 đã bàn giao, còn vấn đề cần xử lý

[Report M3](../client-agent/WEEK3_PACKAGING_AND_RETRY.md) và script Week 3 đã có. Các suite hiện tại: `AdminServerClientContractTest` **10 PASS**, `HeartbeatCoordinatorTest` **6 PASS**, `EndpointManagerTest` **4 PASS**, `OutboxSpoolServiceTest` **3 PASS**. Có thêm 8 test M3 so với snapshot cũ: HTTP negative cases, giữ pending ACK khi retry thất bại, server unreachable và registration corrupt. Reload registration/policy/pending ACK đã được bao phủ từ trước.

| Mục | Kết luận QA / việc M3 cần làm |
|---|---|
| **M5-W3-06 — verifier staging** | **OPEN, đã tái hiện trên Windows.** `verify-week3-windows.ps1 -CheckStaging` khi `packaging/staging` không tồn tại vẫn exit 0/in PASS. Source cũng chỉ WARN khi thiếu file. M3 cần làm thất bại hoặc trả trạng thái chưa xác minh rõ ràng cho staging được yêu cầu nhưng không hợp lệ; sau thay đổi M5 kiểm tra lại thiếu thư mục, thiếu file và staging hợp lệ. |
| Outbox FIFO/fault/restart coverage | Test drain hiện chỉ đếm hai lần gửi và xóa file, chưa assert thứ tự payload. Chưa thấy bổ sung disk-write failure, reload spool qua restart và nhiều chu kỳ network flap trong suite đã rà. M3 chỉ ra evidence tương đương hoặc bổ sung; không đổi tên test thành bằng chứng chưa assert. |
| Installer/service lifecycle | Source `postinst` vẫn bỏ qua lỗi restart rồi in success; `prerm` stop/disable không phân nhánh upgrade. M3 xác nhận thiết kế/triage; đây là source-review questions, chưa phải lỗi runtime Ubuntu được tái hiện. |
| Quyền file trong report | Report M3 ghi mode `750` cho file `application.yml`, builder đặt `640`; directory `/etc/usbshield-agent` mới là `750`. Cần đồng bộ tài liệu owner. |
| `.deb`/staging provenance | Không có `packaging/dist` hoặc `packaging/staging` trong lượt kiểm tra; không thấy `.deb` artifact. Trạng thái **NOT BUILT / NOT VERIFIED**; M3 cung cấp build/hash/log từ Linux/CI khi có. Windows static PASS không chứng minh clean install. |

## Việc còn lại và điều kiện đóng

| ID | Trạng thái sau lượt M5 này | Owner / bước tiếp theo |
|---|---|---|
| H01 — M1 Event API/server/SPA | Technical regression PASS; chưa ghi nhận owner acknowledgment mới | M1 review contract/tích hợp và xác nhận revision. |
| H02 — M4 shared frontend/auth/browser | Technical regression PASS; chưa ghi nhận owner acknowledgment mới | M4 review shared integration. |
| H03 — M2 core/session evidence | **DONE — M5 đã review report và chạy suites thật** | Không còn chờ gửi code/report; owner/nhóm acceptance là mục riêng. |
| H04 — M3 retry/packaging evidence | Đã review/test; **OPEN** với M5-W3-06 và các câu hỏi trên | M3 xử lý/triage, cung cấp evidence; M5 retest phần thay đổi. |
| H05 — Windows group gate | **OPEN** | M1 + owner chốt revision tích hợp, P0/issue disposition và quyết định có nguồn xác nhận. |
| U01–U09 | **NOT EXECUTED / DEFERRED_UBUNTU** | M1–M5 thực hiện sau Week 4 với Ubuntu/USBGuard/USB/GUI và artifacts tích hợp. |

Nhãn quyết định nhóm chỉ ghi sau khi có xác nhận: `WEEK 3 — PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`. Câu “trạng thái chính thức” trong report M3 và dòng PASS của static verifier không thay thế quyết định toàn nhóm. Không tuyên bố FULL PASS.

## Provenance và kiểm tra hồ sơ

Môi trường Windows 11 amd64; JDK 22.0.2, Maven 3.9.11, Node 22.20.0, npm 10.9.3, Vite 8.3.1, Chrome. Source tree SHA-256: `117de4465bd08ad8604fe940475e1699ba8cb539c060f6bfb8316f0c3b7b6fd6`. [Environment](week3-evidence/2026-10-24/integrated-regression/environment.json), [source manifest](week3-evidence/2026-10-24/integrated-regression/sources/117de4465bd08ad8604fe940475e1699ba8cb539c060f6bfb8316f0c3b7b6fd6.json), [source/artifact/link validation](week3-evidence/2026-10-24/integrated-regression/validation.json). Mỗi command có log/JSON cùng tên ghi argv/cwd/revision/times/scope/exit. H2/runtime QA được cô lập; không đọc/reset/dùng database ứng dụng của người dùng.
