# Week 4 — M5 release validation và bàn giao

Ngày: 2026-10-28 (Asia/Bangkok). Owner: M5. Báo cáo này theo dõi công việc Week 4 đã thực hiện trong working tree; không thay thế quyết định release của M1/toàn nhóm.

## Phạm vi và mốc nguồn

- Source baseline: `2013ce726ef8cc960b30720435be23e8ccef1903`. Chưa có xác nhận đây là SHA release candidate cuối của Week 4.
- Giữ nguyên quyết định [Week 3 PASS (WINDOWS SCOPE)](WEEK3_FINAL_OWNER_CLOSEOUT_20261028.md); không mở lại H05 hoặc ghi đè evidence cũ.
- [Test matrix và inventory](WEEK4_TEST_INVENTORY.md), [Event History review](WEEK4_EVENT_HISTORY_REVIEW.md), [Demo Script](../DEMO_SCRIPT.md), [Ubuntu acceptance](WEEK4_UBUNTU_ACCEPTANCE.md), [evidence index](week4-evidence/README.md).
- Maven/unit/mock, static/build, HTTP/browser synthetic và Ubuntu E2E là các mức bằng chứng riêng. Ubuntu/USB vật lý/USBGuard daemon/native GUI/systemd/reboot/package lifecycle không được thực thi trong đợt này.

## Kết quả thực thi Windows

Đã chạy mới trên baseline trên; source tree SHA-256 `40b49888fa950c01ecc1381702e6f3ca15d95370b55319b614be2a12dcb3c759` không đổi trong từng lệnh. Các số dưới đây lấy từ [log/metadata ngày 28/10](week4-evidence/2026-10-28/windows-regression/README.md), không dùng lại số PASS Week 3 làm kết quả mới.

| Kiểm tra | Kết quả mới | Giới hạn |
|---|---|---|
| Frontend | **96/96 PASS**, lint và TypeScript/production build PASS | Unit/component/API với dữ liệu tổng hợp |
| Client Agent | **127/127 PASS**, executable JAR build PASS | Core/session/notification/sync/outbox chủ yếu unit/mock; không chạy live USBGuard |
| Admin Server + React JAR | **26/26 PASS**, combined build PASS | Profile/backend/H2 tests; browser cùng origin được xác minh riêng |
| Policy HTTP riêng | **1/1 PASS** | Backend thật; HTTP enrollment/event/ACK tổng hợp |
| Fixture browser | **PASS**, 14 screenshots | Nhãn demo/readonly, auth, errors, desktop/mobile |
| Management browser | **PASS**, 9 screenshots | Spring Boot/H2 thật; A/B Allow/Revoke, desired/applied, controlled ACK, actual JVM restart/session expiry |
| Event History browser | **PASS**, 8 screenshots, 28 persisted events | HTTP synthetic; filters, paging/date, UNKNOWN/no-serial/A/B, browser fault injection/recovery |
| Packaging/static/syntax | **PASS**: current JAR verifier, **3/3** staging probes, **4** `bash -n` | Positive staging dùng dummy files; không chứng minh có `.deb` hoặc cài đặt Ubuntu |
| npm locked install + audit | Cài đúng lockfile **PASS**; audit báo **0 vulnerabilities** | Offline attempt FAIL `ENOTCACHED` đã được lưu và retry registry thành công; không nâng dependency hoặc chạy `audit fix` |
| Packaged-JAR Events browser / recording | **PASS**, 8 screenshots; **207 actual CDP frames** / 6,764,775 bytes, có replay và timestamp | Cùng JAR phục vụ UI/API, giữ nguyên 31 dòng assertion gốc. Không có video encoded/audio/thuyết minh hoặc demo Ubuntu |

Không phát hiện lỗi P0 ứng dụng mới qua các kiểm tra đã hoàn tất. Nhánh UI phục hồi khi history co lại ở trang cuối chưa có scenario browser trực tiếp; [review Event History](WEEK4_EVENT_HISTORY_REVIEW.md) ghi đây là giới hạn coverage, không phải bug đã tái hiện.

## Trạng thái công việc M5

Các đầu ra là thay đổi có thể review trong working tree; tác vụ này không tạo commit/PR/tag hoặc ký thay owner. Điều kiện commit/PR và review của nhóm trong kế hoạch được giữ cho bước tích hợp.

| Work ID | Đã thực hiện | Phần còn chờ |
|---|---|---|
| W4-M5-01 | **DONE — chuẩn bị và cập nhật matrix/inventory**: BP-T01–28, DoD-01–11, owner/môi trường/expected/actual/SHA/status; phân biệt namespace T của Week 1 | Bổ sung kết quả khi candidate thay đổi hoặc Ubuntu chạy; không cần chờ member để dùng matrix hiện tại |
| W4-M5-02 | **WINDOWS TECHNICAL PASS** trên baseline; chưa chốt “final candidate regression” | M1 chốt candidate/API freeze; M2/M3/M4 bàn giao scope cuối. Nếu source/artifacts đổi, rerun phần bị ảnh hưởng; nếu giữ nguyên thì đối chiếu manifest và chấp nhận bằng chứng hiện có |
| W4-M5-03 | **TECHNICAL REVIEW/REGRESSION PASS** Event History; production không tự fallback mock | M4 review/handoff shared adapter, auth, browser harness trên candidate Week 4; attribution/enforcement thực tế vẫn hoãn Ubuntu |
| W4-M5-04 | **DONE trong phạm vi Windows tự động**: script/Q&A, fixture/management/Events và same-origin JAR rehearsal PASS; có recording thực tế | Walkthrough có người thuyết minh chưa thực hiện; live Ubuntu sau Week 4 |
| W4-M5-05 | **DONE — evidence hiện có đã backup và validation PASS**: logs/metadata, **39 screenshots theo suite sở hữu**, recording, build manifest/checksums lưu riêng Week 4; giữ nguyên evidence cũ | Không có video encoded/narrated. Recording là 207 frame có timing và replay; bổ sung evidence cho các đợt chạy sau khi có |
| W4-M5-06 | **DONE — báo cáo và danh sách handoff**; quyết định candidate còn BLOCKED do các mục W4-H01–H05 | M1–M4 cung cấp bằng chứng/owner handoffs, M1 ghi quyết định release candidate; report không tự tạo sự chấp thuận |
| W4-M5-07 | **DONE — bộ phiếu/checklist Ubuntu** có bước/lệnh/expected/evidence và mẫu ghi nhận | Runtime chỉ sau Week 4, cần M2/M3, Ubuntu/USB/GUI và artifacts tương ứng |

## Đề xuất go/no-go

**GO cho review/bàn giao các đầu ra M5 đã chuẩn bị và kiểm tra Windows trên baseline.** **NO-GO / BLOCKED cho việc tuyên bố `WEEK 4 — WINDOWS/RELEASE-CANDIDATE PREPARATION PASS` ở cấp nhóm** cho đến khi các handoff bên dưới được ghi nhận và candidate cuối khớp nguồn/artifact đã kiểm tra. Ubuntu full gate tiếp tục **NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4**.

## Evidence và kiểm tra provenance cuối

[Validation PASS](week4-evidence/2026-10-28/windows-regression/validation.json) xác minh **18 lệnh positive**, log/exit/source hashes, JUnit hiện tại, **39 screenshot** thuộc đúng browser suite, **207 frame** cùng hash/byte/timing và **5 frontend assets** giống byte trong server JAR. Bản ghi màn hình tự động dài khoảng **10,6 giây** phản ánh tốc độ chạy assertions; không phải walkthrough thuyết minh 8–10 phút.

- [Environment](week4-evidence/2026-10-28/windows-regression/environment.json): Java 22.0.2, Maven 3.9.11, Node 24.22.1, npm 11.19.1, Chromium 156.0.8078.49, Windows; times ghi UTC và Asia/Bangkok.
- [Build manifest](week4-evidence/2026-10-28/windows-regression/build-manifest.json): checksum JAR server/Agent, manifest và assets. Attribution dùng build thành công, source ổn định và thời gian ghi artifact; hash server được đối chiếu thêm với provenance của browser rehearsal. Hash build không được gán ngược thành đã lưu ngay trong raw command metadata nếu thực tế lưu ở bước validation.
- [QA document/tool manifest](week4-evidence/2026-10-28/windows-regression/qa-documents.json) ghi hash các thay đổi M5 trong working tree; tách với application manifest trên source SHA gốc.
- [Evidence SHA-256 inventory](week4-evidence/2026-10-28/windows-regression/checksums.json), [screenshots](week4-evidence/2026-10-28/windows-regression/screenshots.json), [recording replay](week4-evidence/2026-10-28/windows-regression/recording/replay.html) và [provenance](week4-evidence/2026-10-28/windows-regression/recording/provenance.json).

Lần `npm ci --offline` thiếu cache được lưu FAIL và có lần cài registry đúng lockfile PASS; bootstrap sandbox chặn child process trước khi suite chạy; lần kiểm tra evidence đầu tiên gặp thứ tự tạo link index được ghi ở [validation-attempts](week4-evidence/2026-10-28/windows-regression/validation-attempts.json) và đã sửa. Không coi các lỗi công cụ/môi trường đã giải quyết là bug ứng dụng, cũng không xóa chúng khỏi hồ sơ. Text logs đã che key credential; các ảnh mẫu/đầu-cuối recording đã được xem, dữ liệu là fixture/QA tổng hợp. Không sao lưu H2, token hay browser profile.

Source application/test/packaging không thay đổi trong tác vụ M5 này; evidence Week 1–3 giữ nguyên. Review chỉ ra một thiếu scenario browser (history co lại khi đang ở trang cuối), chưa có failure ứng dụng tái hiện. Không thêm test trùng implementation hoặc sửa code của member khác.

## Handoff cần từ các member cho Week 4

Đây là phần việc cần bàn giao, chưa phải thông điệp đã gửi cho member và không phải mở lại nghiệm thu Week 3.

| ID | Owner | Cần bàn giao/xác nhận | Điều kiện để M5 chốt |
|---|---|---|---|
| W4-H01 | M1 | API/DTO freeze; review security/production; backend guides; SHA/manifest candidate, server JAR và cấu hình chạy | Contract khớp implementation, security finding P0 đã có hướng xử lý, candidate SHA rõ ràng để đối chiếu/rerun |
| W4-H02 | M2 | Báo cáo core Week 4: stress/watch, policy, session, notification; Q&A và checklist USBGuard/IPC/GUI | Đối chiếu coverage/evidence hiện có và các giới hạn; M2 xác nhận phần core/IPC dùng khi demo/triển khai |
| W4-H03 | M3 | Bàn giao Agent/package, checksum/build/staging logs hoặc khai báo `.deb NOT BUILT` cùng lý do; Installation Guide và permissions/lifecycle notes | Chứng minh artifact đã build đúng source; không dùng synthetic staging probes làm bằng chứng có `.deb`; thống nhất các bước Ubuntu còn hoãn |
| W4-H04 | M4 | Xác nhận Event API adapter/browser harness dùng chung tại candidate; production frontend handoff, Admin Guide và screenshots cuối | Review phần shared frontend hiện tại được ghi nhận; thay đổi sau baseline phải chạy lại phần bị ảnh hưởng |
| W4-H05 | M1 + các owner | Chốt source/artifact candidate, nhận bàn giao M5, rà soát issue/P0 và quyết định candidate | Ghi quyết định Week 4 có SHA, scope và các ngoại lệ; không tự đặt tag hoặc tuyên bố FULL RELEASE PASS |

## Bảo mật và giới hạn triển khai

[Security follow-up Week 3](WEEK3_DEPLOYMENT_SECURITY_FOLLOWUP.md) vẫn là nguồn theo dõi credential từng có trong Git history. M5 không có bằng chứng quyền truy cập deployment để xác nhận revoke/rotation/re-enrollment; cần M1/người quản trị môi trường xác nhận, chỉ lưu kết quả đã che secret. Không xem việc `.gitignore` runtime data là token đã được thu hồi.

Các kiểm tra Windows không chứng minh production HTTPS, quyền service/IPC thực tế, local fail-closed trên USB thật, user/session thực hoặc native popup. Bộ phiếu Ubuntu chỉ là chuẩn bị để chạy sau Week 4.
