# Week 4 — M5 release validation và bàn giao

Ngày cập nhật: **2026-10-30 (Asia/Bangkok)**. Owner: M5. Báo cáo phân biệt evidence Windows ngày 28/10, đối chiếu source sau pull ngày 30/10 và quyết định candidate của M1/toàn nhóm.

## Phạm vi và mốc nguồn

- Source hiện tại sau pull: **`751387bf166c528326ae91c6a16a820a6d8fc24a`**; chưa được M1 chốt là release candidate cuối. Baseline chạy đầy đủ ngày 28/10 là `2013ce726ef8cc960b30720435be23e8ccef1903`.
- Bộ QA M5 ngày 28/10 đã commit tại `63ea070`, tích hợp qua `6127766`. Các cập nhật và evidence ngày 30/10 đang ở working tree; không tự nhận đã commit/PR/tag hoặc ký thay owner.
- Giữ nguyên quyết định [Week 3 PASS (WINDOWS SCOPE)](WEEK3_FINAL_OWNER_CLOSEOUT_20261028.md); không mở lại H05 hoặc ghi đè evidence cũ.
- [Test matrix và inventory](WEEK4_TEST_INVENTORY.md), [Event History review](WEEK4_EVENT_HISTORY_REVIEW.md), [Demo Script](../DEMO_SCRIPT.md), [Ubuntu acceptance](WEEK4_UBUNTU_ACCEPTANCE.md), [evidence index](week4-evidence/README.md).
- Maven/unit/mock, static/build, HTTP/browser synthetic và Ubuntu E2E là các mức bằng chứng riêng. Ubuntu/USB vật lý/USBGuard daemon/native GUI/systemd/reboot/package lifecycle không được thực thi trong đợt này.

## Đối chiếu sau pull — 30/10/2026

[Evidence sau pull](week4-evidence/2026-10-30/post-pull-regression/README.md), [scope equivalence](week4-evidence/2026-10-30/post-pull-regression/scope-equivalence.json), [validation](week4-evidence/2026-10-30/post-pull-regression/validation.json) và [build manifest](week4-evidence/2026-10-30/post-pull-regression/build-manifest.json) theo dõi đúng source hiện tại. Frontend, Admin Server và packaging không thay đổi so với source đã kiểm tra ngày 28/10; giữ các kết quả đó bằng đối chiếu source, **không đổi ngày hoặc gọi là test chạy mới ngày 30/10**.

Lượt chạy mới ngày 30/10 dùng HEAD `751387bf166c528326ae91c6a16a820a6d8fc24a`, source-tree SHA-256 **`21318e741b1571ab31b887a969d05e10e462490063f686403b33098f79173715`**. Agent JAR tạo bởi lượt này có SHA-256 **`26f8d74b450c399a798a9cbc7a0c0fc34d7a80cb34960e34a21c176c74d6e5d9`**. **Validation ngày 30/10 PASS**: đối chiếu source/log/JUnit/JAR/link, source equivalence và bảo toàn evidence cũ; kết quả được ghi riêng trong [validation.json](week4-evidence/2026-10-30/post-pull-regression/validation.json), không suy ra chỉ từ build thành công.

| Thay đổi / kiểm tra | Kết quả hoặc bước hiện tại |
|---|---|
| M2 watch hardening | Đã merge `ad57fa4`: đọc dòng/section có giới hạn, giữ dedup ngắn hạn qua reconnect, chặn tăng map không giới hạn. **9/9 test watch/stress mới PASS** trong lượt Agent hiện tại; đây là mock/unit evidence, không phải daemon/USB thật. |
| Agent `clean package` | **136/136 PASS**, 0 failure/error/skip, executable JAR build thành công; [01-agent-package](week4-evidence/2026-10-30/post-pull-regression/01-agent-package.json). Số 127 ngày 28/10 được giữ riêng trong bảng lịch sử. |
| Static/JAR verifier | **PASS** trên Agent JAR vừa build; [02-packaging-static](week4-evidence/2026-10-30/post-pull-regression/02-packaging-static.json). Không chạy installer/systemd hoặc tạo `.deb`. |
| M4 handoff / M5 acknowledgement | **ĐÃ NHẬN / ACK KỸ THUẬT**: [M4 handoff](evidence/M4_WEEK4_HANDOFF.md) và [M5 đối chiếu](evidence/M5_WEEK4_POST_PULL_ACK_20261030.md). Review của M4 ở `6127766` áp dụng cho frontend không đổi trên HEAD hiện tại. Không còn chờ M4 gửi bản review shared frontend này. |
| Candidate và các owner còn lại | Chờ M1 freeze/security/candidate; M2 phần core 2–3, Q&A/IPC và M3 package/guide cuối. M4 ACK không tự trở thành quyết định release candidate của nhóm. |
| Demo / Ubuntu | Không chạy demo browser mới, không tạo thêm recording/thuyết minh/video encoded và không thực thi Ubuntu trong lượt đối chiếu này. |

## Kết quả thực thi Windows — lịch sử ngày 28/10/2026

Đã chạy trên baseline `2013ce726ef8cc960b30720435be23e8ccef1903`; source tree SHA-256 `40b49888fa950c01ecc1381702e6f3ca15d95370b55319b614be2a12dcb3c759` không đổi trong từng lệnh của ngày đó. Các số dưới đây lấy từ [log/metadata ngày 28/10](week4-evidence/2026-10-28/windows-regression/README.md); không đại diện cho Agent đã thay đổi ở HEAD ngày 30/10.

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

Bộ QA ban đầu đã được commit/merge như trên; cập nhật sau pull là thay đổi có thể review trong working tree. M5 ghi nhận handoff và bằng chứng kỹ thuật, không tạo sự chấp thuận thay M1/các owner.

| Work ID | Đã thực hiện | Phần còn chờ |
|---|---|---|
| W4-M5-01 | **DONE — chuẩn bị và cập nhật matrix/inventory**: BP-T01–28, DoD-01–11, owner/môi trường/expected/actual/SHA/status; phân biệt namespace T của Week 1 | Bổ sung kết quả khi candidate thay đổi hoặc Ubuntu chạy; không cần chờ member để dùng matrix hiện tại |
| W4-M5-02 | **WINDOWS TECHNICAL PASS sau pull**: Agent **136/136**, suite mới **9/9**, static/JAR và evidence validation PASS ngày 30/10; frontend/server/packaging giữ kết quả ngày 28/10 qua source equivalence, không nhận là đã chạy lại | M1 chọn candidate/API freeze, M2/M3 bàn giao scope cuối. Rerun phần bị ảnh hưởng nếu source/artifacts tiếp tục đổi |
| W4-M5-03 | **DONE phần technical review và M4 shared-frontend ACK** trên source frontend không đổi ở HEAD hiện tại; production không tự fallback mock | M1 xác nhận candidate cuối; đối chiếu lại nếu frontend/API đổi. Attribution/enforcement thực tế vẫn hoãn Ubuntu; không còn chờ bản handoff M4 đã nhận |
| W4-M5-04 | **DONE trong phạm vi Windows tự động ngày 28/10**: script/Q&A, các browser rehearsal PASS và recording; đã bổ sung scope sau pull | Chưa có demo mới trên Agent hiện tại; walkthrough thuyết minh chưa thực hiện; live Ubuntu sau Week 4 |
| W4-M5-05 | **DONE bộ evidence ngày 28/10 và cập nhật sau pull ngày 30/10**: hai capture Agent/static mới, JUnit/build/scope/environment/checksums; **validation source/log/JUnit/JAR/link và bảo toàn evidence cũ PASS** | Không có video encoded/narrated mới; **39 screenshots** và recording **207 frame** có timing/replay vẫn thuộc ngày 28/10. Bổ sung evidence khi candidate thay đổi |
| W4-M5-06 | **DONE — báo cáo và handoff được cập nhật**; W4-H04 technical review đã ACK, candidate vẫn BLOCKED bởi các handoff còn lại | M1 freeze/security/candidate, M2 core 2–3/Q&A/IPC và M3 package/guide cuối; M1 ghi quyết định nhóm |
| W4-M5-07 | **DONE — bộ phiếu/checklist Ubuntu** có bước/lệnh/expected/evidence và mẫu ghi nhận | Runtime chỉ sau Week 4, cần M2/M3, Ubuntu/USB/GUI và artifacts tương ứng |

## Đề xuất go/no-go

**GO cho review/bàn giao M5 và ACK shared frontend đã nhận từ M4.** Agent/static và evidence validation hiện tại đã PASS; **NO-GO / BLOCKED cho tuyên bố `WEEK 4 — WINDOWS/RELEASE-CANDIDATE PREPARATION PASS` ở cấp nhóm** cho đến khi M1 chốt freeze/security/candidate, M2 hoàn tất handoff/giải thích contract còn mở và M3 bàn giao package/guide cuối. Candidate cuối phải khớp source/artifact đã kiểm tra. Ubuntu full gate tiếp tục **NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4**.

## Evidence và provenance — bộ lịch sử ngày 28/10

[Validation PASS](week4-evidence/2026-10-28/windows-regression/validation.json) xác minh **18 lệnh positive**, log/exit/source hashes, JUnit hiện tại, **39 screenshot** thuộc đúng browser suite, **207 frame** cùng hash/byte/timing và **5 frontend assets** giống byte trong server JAR. Bản ghi màn hình tự động dài khoảng **10,6 giây** phản ánh tốc độ chạy assertions; không phải walkthrough thuyết minh 8–10 phút.

- [Environment](week4-evidence/2026-10-28/windows-regression/environment.json): Java 22.0.2, Maven 3.9.11, Node 24.22.1, npm 11.19.1, Chromium 156.0.8078.49, Windows; times ghi UTC và Asia/Bangkok.
- [Build manifest](week4-evidence/2026-10-28/windows-regression/build-manifest.json): checksum JAR server/Agent, manifest và assets. Attribution dùng build thành công, source ổn định và thời gian ghi artifact; hash server được đối chiếu thêm với provenance của browser rehearsal. Hash build không được gán ngược thành đã lưu ngay trong raw command metadata nếu thực tế lưu ở bước validation.
- [QA document/tool manifest](week4-evidence/2026-10-28/windows-regression/qa-documents.json) ghi hash các thay đổi M5 trong working tree; tách với application manifest trên source SHA gốc.
- [Evidence SHA-256 inventory](week4-evidence/2026-10-28/windows-regression/checksums.json), [screenshots](week4-evidence/2026-10-28/windows-regression/screenshots.json), [recording replay](week4-evidence/2026-10-28/windows-regression/recording/replay.html) và [provenance](week4-evidence/2026-10-28/windows-regression/recording/provenance.json).

Lần `npm ci --offline` thiếu cache được lưu FAIL và có lần cài registry đúng lockfile PASS; bootstrap sandbox chặn child process trước khi suite chạy; lần kiểm tra evidence đầu tiên gặp thứ tự tạo link index được ghi ở [validation-attempts](week4-evidence/2026-10-28/windows-regression/validation-attempts.json) và đã sửa. Không coi các lỗi công cụ/môi trường đã giải quyết là bug ứng dụng, cũng không xóa chúng khỏi hồ sơ. Text logs đã che key credential; các ảnh mẫu/đầu-cuối recording đã được xem, dữ liệu là fixture/QA tổng hợp. Không sao lưu H2, token hay browser profile.

Trong lượt M5 ngày 28/10, source application/test/packaging không bị sửa; evidence Week 1–3 giữ nguyên. Sau pull ngày 30/10, Agent có thay đổi của M2 như đã nêu; M5 chỉ cập nhật tài liệu/evidence, không sửa implementation của member khác. Giới hạn browser history co lại ở trang cuối vẫn chưa có scenario trực tiếp, chưa có failure ứng dụng tái hiện.

## Handoff cần từ các member cho Week 4

Đây là phần việc cần bàn giao, chưa phải thông điệp đã gửi cho member và không phải mở lại nghiệm thu Week 3.

| ID | Owner | Cần bàn giao/xác nhận | Điều kiện để M5 chốt |
|---|---|---|---|
| W4-H01 | M1 | API/DTO freeze; review security/production; backend guides; SHA/manifest candidate, server JAR và cấu hình chạy | Contract khớp implementation, security finding P0 đã có hướng xử lý, candidate SHA rõ ràng để đối chiếu/rerun |
| W4-H02 | M2 | Watch hardening phần 1 đã merge; còn review core phần 2–3 (policy/session/notification), Q&A và checklist USBGuard/IPC/GUI | Regression Agent hiện tại có evidence; M2 xác nhận phần core/IPC và giới hạn trước demo/triển khai |
| W4-H03 | M3 | Bàn giao Agent/package, checksum/build/staging logs hoặc khai báo `.deb NOT BUILT` cùng lý do; Installation Guide và permissions/lifecycle notes | Chứng minh artifact đã build đúng source; không dùng synthetic staging probes làm bằng chứng có `.deb`; thống nhất các bước Ubuntu còn hoãn |
| W4-H04 | M4 → M5 | **ĐÃ NHẬN / ACK KỸ THUẬT** qua [M4 handoff](evidence/M4_WEEK4_HANDOFF.md), Admin Guide final và [M5 ACK](evidence/M5_WEEK4_POST_PULL_ACK_20261030.md); frontend không đổi từ source đã review | Không còn thiếu bản shared-frontend handoff. M1 vẫn phải chọn candidate; đổi frontend/API sau ACK thì đối chiếu/rerun phần bị ảnh hưởng |
| W4-H05 | M1 + các owner | Chốt source/artifact candidate, nhận bàn giao M5, rà soát issue/P0 và quyết định candidate | Ghi quyết định Week 4 có SHA, scope và các ngoại lệ; không tự đặt tag hoặc tuyên bố FULL RELEASE PASS |

## Bảo mật và giới hạn triển khai

[Security follow-up Week 3](WEEK3_DEPLOYMENT_SECURITY_FOLLOWUP.md) vẫn là nguồn theo dõi credential từng có trong Git history. M5 không có bằng chứng quyền truy cập deployment để xác nhận revoke/rotation/re-enrollment; cần M1/người quản trị môi trường xác nhận, chỉ lưu kết quả đã che secret. Không xem việc `.gitignore` runtime data là token đã được thu hồi.

Các kiểm tra Windows không chứng minh production HTTPS, quyền service/IPC thực tế, local fail-closed trên USB thật, user/session thực hoặc native popup. Bộ phiếu Ubuntu chỉ là chuẩn bị để chạy sau Week 4.
