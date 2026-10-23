# M5 Week 3 — công việc đã làm và bàn giao còn chờ

Cập nhật: **2026-10-23, Asia/Bangkok (+07:00)**. Revision đã kiểm tra: `8c222a47142d7a80f75e56b8d77e5bf732797e16`, branch `huy`. Đây là hồ sơ chuẩn bị bàn giao; chưa có xác nhận mới của các member hoặc quyết định nghiệm thu nhóm.

**M5: implementation ready; các kiểm tra Windows đã chạy PASS; hồ sơ QA đã đồng bộ. Group Week 3 gate: OPEN. Ubuntu E2E: NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4.** Lượt cập nhật hồ sơ này chỉ sửa tài liệu, không sửa source/test và không chạy lại các suite ứng dụng.

## Công việc M5 đã hoàn thành

| Công việc | Kết quả / bằng chứng |
|---|---|
| Event History, mapper và filter | Đã có trên revision kiểm tra; UNKNOWN, timestamp nguồn, metadata phân trang, dữ liệu lỗi và trạng thái loading/empty/error được kiểm tra. Endpoint/USB tìm theo tên hoặc ID; Linux User tìm một phần username, không phân biệt hoa thường. API exact cũ vẫn được giữ. |
| Regression trên code vừa pull | **96/96 frontend**, **26 server**, **61 Agent**, lint/build, ba browser suite và **1/1 policy HTTP contract** đều PASS trong phạm vi đã chạy. [16 lệnh và log gốc](week3-evidence/2026-10-23/post-pull-audit/README.md). |
| JAR chứa React và API cùng origin | Build `-Pwith-frontend` và browser Events chạy trực tiếp với JAR đã PASS. Đây là HTTP/default-profile; prod defaults được kiểm tra riêng trong server suite, không phải browser HTTPS production. [Provenance browser variant](week3-evidence/2026-10-23/post-pull-audit/packaged-browser-variant.json). |
| Packaging khả thi trên Windows | Existing static verifier với Agent JAR và bốn lệnh `bash -n` PASS. Đã đọc thêm control/installer/removal/service/builder; các điểm cần owner xác nhận nằm ở bảng M3 bên dưới. Chưa có `.deb` binary/build provenance trong hồ sơ. |
| Kế hoạch, inventory, QA summary, evidence | Đã đồng bộ kết quả ngày 23/10; giữ snapshot ngày 21/10 và toàn bộ log cũ. [Inventory](WEEK3_TEST_INVENTORY.md), [QA summary](WEEK3_QA_SUMMARY.md), [evidence index](week3-evidence/README.md). |
| Demo draft và backlog Ubuntu | [Demo draft](../DEMO_SCRIPT.md) khớp filter hiện tại; [kế hoạch](WEEK3_INTEGRATION_TEST_PLAN.md) giữ đủ U01–U09 chưa chạy. Final rehearsal/guides thuộc Week 4. |
| Chuẩn bị bàn giao và quyết định gate | Đã soạn checklist M1/M4, rà coverage hiện có của M2/M3 và chuẩn bị bảng gate bên dưới. Chưa gửi thông báo hoặc ghi thay xác nhận của member. |

M5 source/test không thay đổi so với snapshot cuối ngày 21/10. Agent/packaging cũng chưa thay đổi so với snapshot đó; đây không phải kết luận mọi yêu cầu Week 3 của M2/M3 đều thiếu. [Audit sau pull](WEEK3_M5_POST_PULL_REVIEW_20261023.md) là snapshot trước khi đồng bộ hồ sơ; các thiếu sót tài liệu nêu trong audit đã được xử lý ở lượt này. [Validation cập nhật tài liệu](week3-evidence/2026-10-23/qa-closeout/validation.json) đối chiếu hash source và liên kết evidence.

## Gói review đã chuẩn bị cho M1 và M4

| Người nhận | Nội dung review cụ thể | Bằng chứng sẵn có | Trạng thái còn chờ |
|---|---|---|---|
| M1 | Event query `endpoint`/`device`/`username` khớp UI; literal substring trước count/paging; exact `endpointId`/`deviceId`/`linuxUsername` tương thích. Xác nhận status/ACK/security và cách build/chạy JAR chứa React. | [M1 hardening](../member1/WEEK3_SERVER_HARDENING.md), [26 server tests/build](week3-evidence/2026-10-23/post-pull-audit/06-server-with-frontend.txt), [Events](week3-evidence/2026-10-23/post-pull-audit/09-browser-events.txt), [same-origin JAR](week3-evidence/2026-10-23/post-pull-audit/12-browser-packaged-jar.txt). | Implementation và kết quả Windows đã có; chưa ghi nhận M1 xác nhận gói tích hợp này. |
| M4 | Events dùng chung auth/resource/API không regression; lỗi/hết phiên xóa protected rows; CRLF readiness migration tương thích shared harness; desired/applied và A/B giữ đúng nghĩa. | [M4 hardening](../admin-web/WEEK3_UI_UX_HARDENING.md), [96 frontend tests](week3-evidence/2026-10-23/post-pull-audit/02-frontend-tests.txt), [management browser](week3-evidence/2026-10-23/post-pull-audit/08-browser-management.txt), [policy HTTP](week3-evidence/2026-10-23/post-pull-audit/10-policy-http-contract.txt). | Implementation và kết quả Windows đã có; chưa ghi nhận M4 xác nhận gói tích hợp này. |

## Coverage M2/M3 đã rà soát và phần cần bàn giao

Các hàng dưới là review assertions trong các test được liên kết, kết hợp kết quả Agent **61 PASS**. “Cần xác nhận/bổ sung” không có nghĩa test hiện tại FAIL hoặc chắc chắn toàn repo không có coverage tương đương. Owner có thể chỉ ra test/evidence đã có thay vì viết test trùng.

| Owner / phạm vi | Đã có bằng chứng gì | Cần owner xác nhận/bổ sung |
|---|---|---|
| M2 — parser/fingerprint | [Parser test](../../client-agent/src/test/java/com/group/usbshield/agent/usbguard/UsbGuardOutputParserTest.java) có output storage/mouse, dòng không nhận diện và base rule; mouse fixture có serial rỗng. [Policy test](../../client-agent/src/test/java/com/group/usbshield/agent/usbguard/UsbGuardPolicySynchronizerTest.java) có idempotency, giữ base/foreign rule, lỗi IPC và fingerprint không an toàn. | Ma trận thiếu/null serial của **storage**, identity theo hash, malformed/quote/class edge cases. No-serial HTTP của M5 chỉ chứng minh contract/display. |
| M2 — listener/reconnect | [Listener test](../../client-agent/src/test/java/com/group/usbshield/agent/usbguard/UsbGuardEventListenerTest.java) kiểm tra attribution/notification và loại policy events khỏi telemetry Insert. | Chưa thấy assertion daemon disconnect/reconnect/restart trong lớp đã rà; cần test hoặc evidence tương đương và semantics fail-closed. |
| M2 — active user | [Loginctl test](../../client-agent/src/test/java/com/group/usbshield/agent/session/LoginctlActiveUserResolverTest.java), [Linux resolver test](../../client-agent/src/test/java/com/group/usbshield/agent/session/LinuxActiveUserResolverTest.java) có chọn local graphical user, no-user, ambiguity và UNKNOWN khi gateway lỗi. | Sequence user switch/logout/seat change và remote-only; tài liệu Week 3 core/session/IPC handoff cho M3/M5. |
| M3 — enrollment/ACK | [Endpoint test](../../client-agent/src/test/java/com/group/usbshield/agent/endpoint/EndpointManagerTest.java) đã reload registration, policy version và pending ACK từ disk khi tạo manager mới. [ACK recovery test](../../client-agent/src/test/java/com/group/usbshield/agent/sync/PolicyAckRecoveryTest.java) có apply-fail/no-ACK, ACK-fail/giữ pending, ACK-success/clear pending. | Xác nhận coverage vòng đời retry/restart rộng hơn và fault cases yêu cầu Week 3; không liệt kê pending-ACK reload là hoàn toàn chưa có. |
| M3 — outbox | [Spool test](../../client-agent/src/test/java/com/group/usbshield/agent/outbox/OutboxSpoolServiceTest.java) có tạo file, gửi/xóa khi thành công, giữ file khi mạng lỗi. | Test drain có tên “in order” nhưng assertions hiện chỉ đếm số lần gửi; cần chứng minh FIFO bằng thứ tự payload. Xác nhận/bổ sung disk-write failure, reload spool qua restart và network flaps. |
| M3 — control/service/scripts | [Control](../../packaging/agent-deb/DEBIAN/control) khai báo package 1.0.0/amd64 và dependencies Java 21 headless, USBGuard, libnotify. [Service](../../packaging/systemd/usbshield-agent.service) dùng user `usbshield`, live profile và external config; [builder](../../packaging/scripts/build-deb.sh) stage config/rules/helper. [Static log](week3-evidence/2026-10-23/post-pull-audit/11-packaging-static.txt) và bốn shell parse logs PASS. | Control được đọc source, chưa kiểm tra metadata của `.deb`. M3 xác nhận quyền IPC và hành vi lỗi khởi động: [postinst](../../packaging/agent-deb/DEBIAN/postinst) bỏ qua một số lỗi `systemctl` rồi in success. Xác nhận xử lý upgrade của [prerm](../../packaging/agent-deb/DEBIAN/prerm), đang stop/disable service không phân nhánh tham số lifecycle. Đây là điểm review, chưa phải lỗi runtime đã tái hiện. |
| M3 — package/handoff | Agent JAR build PASS; source maintainer scripts parse PASS. | Giao báo cáo Week 3 packaging/retry, config/permissions/lifecycle checklist và `.deb` artifact/hash/build log khi có Linux/CI; nếu chưa build thì ghi **NOT BUILT / NOT VERIFIED**. Clean install/reboot vẫn deferred. |

## Các nhiệm vụ còn chờ

| ID | Việc còn lại | Owner / đầu vào cần nhận | Khi M5 có thể đóng |
|---|---|---|---|
| M5-W3-H01 | Review Event API và server/SPA integration | M1: phản hồi checklist ở trên, contract/config/revision đã chốt | Ghi liên kết review; kiểm tra lại phần bị ảnh hưởng nếu source đổi. |
| M5-W3-H02 | Review shared frontend/auth/browser integration | M4: phản hồi checklist ở trên | Ghi liên kết review; regression liên quan nếu source đổi. |
| M5-W3-H03 | Chốt coverage core/session Week 3 | M2: report/test matrix/evidence và bàn giao IPC/policy cho M3 | Đối chiếu yêu cầu với assertions, cập nhật inventory/QA; chạy lại suite nếu có thay đổi. |
| M5-W3-H04 | Chốt retry/outbox/packaging Week 3 | M3: report/test evidence, kết luận các điểm source review và trạng thái `.deb` | Đối chiếu coverage/build provenance; giữ NOT BUILT/NOT VERIFIED nếu chưa có artifact. |
| M5-W3-H05 | Quyết định Week 3 Windows gate trên revision tích hợp | M1 + các owner: revision trên `main`, P0 còn mở và quyết định nhóm | Cập nhật quyết định có nguồn xác nhận; không tự chốt thay nhóm. |
| W3-U01–U09 | Chạy Ubuntu E2E theo lịch nhóm | M1–M5, Ubuntu/USBGuard/USB/GUI và artifact tích hợp; **sau Week 4** | Chỉ đổi trạng thái từng ca khi có log thực thi thật. |

## Hồ sơ quyết định gate đã chuẩn bị

| Điều kiện Windows gate | Kết quả hiện có | Phần còn mở |
|---|---|---|
| Server security/status/policy + bundled React | 26 tests/build và same-origin browser PASS trên `8c222a4` | H01; browser này không nghiệm thu production HTTPS. |
| Agent core/session/retry + JAR | 61 tests/build PASS cho assertions hiện có | H03/H04: chốt coverage P0 Week 3. |
| Packaging static/scripts | Verifier + bốn shell parse PASS; control/source đã đọc | H04: owner review, package provenance hoặc ghi rõ chưa build. |
| Frontend + API integration | 96 tests, lint/build, ba browser suites, policy HTTP riêng PASS | H02; ACK trong HTTP là dữ liệu tổng hợp. |
| Hồ sơ M5 / backlog Ubuntu | Plan/inventory/summary/evidence/demo draft đã đồng bộ | U01–U09 NOT EXECUTED; không đổi thành PASS từ Windows. |
| Tích hợp và quyết định toàn nhóm | Gói bằng chứng đã sẵn sàng review | **OPEN** — branch đã kiểm tra là `huy`; chưa xác nhận revision trên `main` hoặc toàn bộ P0 đã đóng. |

Revision đã kiểm tra: `8c222a47142d7a80f75e56b8d77e5bf732797e16`. Source tree SHA-256: `e16a567bf1cd754d48d43c9e69c7c58a793695c91273e751d585fd30f631de4d`. Môi trường: Windows 11 amd64, JDK 22.0.2, Maven 3.9.11, Node 22.20.0, npm 10.9.3, Chrome; [environment](week3-evidence/2026-10-23/post-pull-audit/environment.json).

| Trường quyết định nhóm | Giá trị |
|---|---|
| Revision tích hợp trên `main` | PENDING — M1 cung cấp |
| Review/acknowledgment của owner | PENDING — H01–H04 |
| P0 Windows còn mở và hướng xử lý | PENDING — owner đối chiếu coverage ở trên |
| Người/ngày quyết định và liên kết xác nhận | PENDING |
| Quyết định | **OPEN — chưa ghi nhận quyết định nhóm** |

Khi các điều kiện đủ và nhóm xác nhận, nhãn cho phép là `WEEK 3 — PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`. Hồ sơ này không tuyên bố `FULL PASS`.
