# Week 4 — M5 release validation và bàn giao

Ngày cập nhật: **2026-11-01 (Asia/Bangkok)**. Owner: M5. Báo cáo phân biệt các lượt evidence S0–S4 với quyết định candidate của M1/toàn nhóm; kết quả cũ giữ nguyên ngày, SHA và giới hạn.

## Phạm vi và mốc nguồn

- **S4 / hiện tại ngày 01/11:** `c6cc1df21283588d70a792a37d42870bdea49497`. **S3 / trước handoff M1 ngày 01/11:** `3efa50b50031dac9cec5738e286ec0a7730529ae`. **S2 / 31/10:** `0b726304a29a78ac36aefbbc71e40fbd7758ae2e`. **S1 / 30/10:** `751387bf166c528326ae91c6a16a820a6d8fc24a`. **S0 / 28/10:** `2013ce726ef8cc960b30720435be23e8ccef1903`.
- QA S0 commit `63ea070`/merge `6127766`; S1 `7c54f85`/`ab34693`; S2 `48a13b3`/`176e547`; **S3 `bf067ef`/`c07cb2f`** đã tích hợp. Chỉ cập nhật S4 hiện ở working tree, chưa có commit/PR/tag mới; M5 không ký thay owner.
- Giữ nguyên quyết định [Week 3 PASS (WINDOWS SCOPE)](WEEK3_FINAL_OWNER_CLOSEOUT_20261028.md); không mở lại H05 hoặc ghi đè evidence cũ.
- [Test matrix và inventory](WEEK4_TEST_INVENTORY.md), [Event History review](WEEK4_EVENT_HISTORY_REVIEW.md), [Demo Script](../DEMO_SCRIPT.md), [Ubuntu acceptance](WEEK4_UBUNTU_ACCEPTANCE.md), [evidence index](week4-evidence/README.md).
- Maven/unit/mock, static/build, HTTP/browser synthetic và Ubuntu E2E là các mức bằng chứng riêng. Ubuntu/USB vật lý/USBGuard daemon/native GUI/systemd/reboot/package lifecycle không được thực thi trong đợt này.

## M1 handoff / tích hợp S4 — 01/11/2026

[M1 server handoff](../member1/WEEK4_SERVER_HANDOFF.md) và [M5 ACK M1](evidence/M5_WEEK4_M1_ACK_20261101.md) ghi **ĐÃ NHẬN** API/DTO freeze v0.2, security/code review, README/Architecture/API/Protocol/Developer Guide, build manifest và **M1 Windows preparation candidate**. Không còn thiếu các input M1 này; **group candidate/sign-off vẫn chưa được chốt**.

| Phạm vi S4 | Trạng thái hiện tại |
|---|---|
| M1 owner evidence | Build log ghi **28 tests PASS**, JAR **57.314.825 bytes / `43269264cf7cd3af5c785a12b15507ee89ce3ce3c6b42ff8e92ceb601604240d`**, source `d78189be42a5fa00a07e46446019c55fc547a592`. Manifest/raw build nhất quán; owner production-profile HTTP smoke PASS. Đây là **owner evidence**, chưa đổi thành M5 chạy lại. |
| Candidate/source | Git trees `admin-server`/`frontend` của artifact source `d78189b` giống S4 HEAD. M1 đã chọn artifact này làm candidate của M1; chưa có group RC/tag/owner sign-offs. Server code thay đổi so với S3 nên cần regression phần bị ảnh hưởng. |
| M5 S4 build/server/policy | **01 frontend build PASS; 02 server clean package 28/28 PASS**, 0 failure/error/skip, chạy **20:55:17–20:56:10 +07:00**; **03 policy HTTP 1/1 PASS**. Source digest `08dca0efb919680e7d5791c9de380dff275bf3d90ea0f2fb39edd08808d1bfe6`; M5 JAR **57.314.805 bytes / `0772ae2af64d855bc6f4c6c6ba08e311df6fa1792ecb6d0eb7944ee5a71d8ba5`**, build riêng trên source tương đương, không đổi hash owner manifest. |
| M5 S4 management/Events browser | **04 management PASS**, **9 screenshots**: auth/session, A/B Allow/Revoke, desired/applied, controlled ACK, JVM restart. **05 Events PASS**, **8 screenshots / 28 persisted events**: mapping/filter/paging/date/UNKNOWN/no-serial/error recovery. Dữ liệu HTTP tổng hợp qua backend thật, không phải USB/Ubuntu. |
| M5 S4 packaged browser | **06 same-origin packaged browser PASS**, **8 screenshots**, giữ **31 dòng assertion gốc**, cleanup confirmed, **recording DISABLED**. Tổng browser S4 **25 screenshots (9+8+8)**; không có CDP replay/video/audio/thuyết minh mới. |
| M5 S4 Agent integration | **08 PASS**, **215 assertions / 64 scenarios / 64 harness-initiated HTTP operations**: production Agent client→Spring Server `prod` thật, auth/limits/ACK/A-B/history và strict log minimization; [summary lần 02](week4-evidence/2026-11-01/m1-integration-regression/agent-server-summary-02.json). DEBUG/TRACE đặt rõ false; accepted-event INFO hiện diện, marker/synthetic secrets vắng trong log ở mọi level của run cô lập. **07 FAIL/27 assertions** giữ riêng (QA heartbeat OK/ONLINE và ambient DEBUG), không đổi app/source/JAR. Không có Agent unit rerun S4. |
| Evidence validator S4 | **PASS**: [validation.json](week4-evidence/2026-11-01/m1-integration-regression/validation.json) xác minh **7 positive captures**, **28 server tests**, **215 Agent→Spring assertions**, **25 screenshots** và **5 React assets byte-equal trong JAR**; source/log/tool/JUnit/owner-source/link/historical-preservation đạt. **07 FAIL** giữ riêng, không ghi đè hoặc đổi thành PASS. |
| Toolchain | Dùng **JDK 21/22**; M1 đã build JDK 22.0.2 targeting Java 21. JDK 24 không tương thích pinned Lombok 1.18.36 (`TypeTag :: UNKNOWN`); không đổi dependency để né lỗi compiler trong lượt QA. |
| Owner follow-up | API summary ghi oversized request → 400 quá tổng quát, trong khi bootstrap token 257 ký tự bị reject **401** theo auth guard. Giữ đúng actual/route semantics khi kiểm tra; cần M1 làm rõ tài liệu, M5 không tự sửa API. |

M1 đã review UTF-8 token byte limit, request/persistence bounds, event INFO-log minimization và production Swagger/OpenAPI off. P1 thiếu login rate-limit/lockout và unknown-user timing được M1 chấp nhận cho scope demo loopback, không tự nâng thành P0. Owner HTTP smoke dùng `prod` nhưng tắt Secure cookie riêng cho HTTP test, không chứng minh TLS/Secure-cookie browser deployment. Credential rotation/re-enrollment vẫn cần quản trị môi trường xác nhận.

Attempt 07 không được mô tả là bug P0 ứng dụng: đây là sai kỳ vọng heartbeat của QA và failure cô lập môi trường của bài prod INFO-log. Raw DEBUG runtime log giữ private/ignored, không sao chép secret vào evidence public. Retry **08 PASS** đặt rõ DEBUG/TRACE=false, kiểm tra INFO event được ghi và marker/synthetic secrets không xuất hiện trong toàn bộ log run đó. Proof áp dụng cho cấu hình INFO đã kiểm soát, không khẳng định payload an toàn khi bật DEBUG/TRACE tùy ý; cùng app/JAR không thay đổi.

## M2 Part 3 — lịch sử S3 ngày 01/11/2026

[M2 Part 3](../usbguard/WEEK4_M2_PART3_FINAL_CLOSEOUT.md), [core guide](../usbguard/WEEK4_M2_CORE_OPERATIONS_GUIDE.md), [technical Q&A](../usbguard/WEEK4_M2_TECHNICAL_QA.md) và [9 case Ubuntu/IPC](../usbguard/WEEK4_M2_M3_M5_UBUNTU_HANDOFF.md) đã **ĐƯỢC NHẬN / ACK KỸ THUẬT WINDOWS**, source merge `942527c` trên S3. [M5 ACK S3](evidence/M5_WEEK4_M2_PART3_ACK_20261101.md) và [evidence ngày 01/11](week4-evidence/2026-11-01/m2-part3-regression/README.md) ghi kết quả thực thi/validation PASS; không còn chờ M2 gửi Part 3 hoặc chờ các kiểm tra Windows M5 của lượt này.

| Phạm vi S3 | Trạng thái hiện tại |
|---|---|
| M2 policy/notification fixes | Source đã merge; **bốn suite mới 9/7/1/1 PASS** về removal/syntax, notification correctness, peer-name policy và effective watch target. Có regression unit thực tế cho fix candidate; không tự chốt external PR review, native credentials hay ba finding trên USBGuard thật. |
| Agent clean package S3 | **174/174 PASS**, 0 failure/error/skip; policy **12**, session **8**, watch **9** tiếp tục PASS. [01-agent-package](week4-evidence/2026-11-01/m2-part3-regression/01-agent-package.json), chạy **14:37:58–14:38:37 +07:00**. Source digest `438fc983ed9e2b656884f5dceb7802f25de227d945a260b3a35c6b29630f8722`; JAR **20.186.115 bytes**, SHA-256 `63255be52082dd8883b102633d8ec962b2d3e864a4204861b6b69cd8f532dd24`. |
| Staging S3 | **02 guarded preparation PASS**, **03 full Week 4 verifier không Skip PASS**, **04 independent byte/hash equality 9/9 PASS**; [staging manifest](week4-evidence/2026-11-01/m2-part3-regression/staging-manifest.json) ràng buộc source/JAR S3, không dùng staged JAR S2 làm proof hiện tại. POSIX mode/`.deb`/Linux chưa được thực thi. |
| Production HTTP S3 | **05 PASS**, **255 assertions / 15 scenarios / 14 real loopback requests** với production `AdminServerClient` constructor, cùng source/JAR S3; [HTTP summary](week4-evidence/2026-11-01/m2-part3-regression/http-transport-summary.json). Responses do test server kiểm soát; không phải backend auth/scheduler hoặc Ubuntu E2E. |
| Evidence validator S3 | **PASS**: [validation.json](week4-evidence/2026-11-01/m2-part3-regression/validation.json) xác minh **5 captures PASS**, **174 tests**, **9 staged file byte-equal**, **255 HTTP assertions**, source/log/JAR/staging/link và bảo toàn lịch sử. Không có failed capture trong S3; 05 FAIL của S2 vẫn giữ đúng thư mục ngày 31/10. |
| Frontend/server/packaging/sync | [Scope equivalence](week4-evidence/2026-11-01/m2-part3-regression/scope-equivalence.json) xác minh phần không đổi; packaging/sync còn đối chiếu trực tiếp S2. Giữ evidence S0/S1/S2 phù hợp, không đổi thành UI/browser/Ubuntu chạy ngày 01/11. JAR S3 đã được stage/kiểm tra mới. |
| Cross-owner/runtime tại S3 | M3 per-UID socket ACL/startup và M2 contract/native/cross-owner chưa chốt. M1 freeze/security/M1 candidate khi ấy còn thiếu; S4 đã nhận handoff M1, group sign-off vẫn riêng. Ubuntu chưa thực thi. |

Owner M2 trước đó báo **43 isolated checks** bằng stub/harness không được giao trong repo; [closeout](../usbguard/WEEK4_M2_PART3_FINAL_CLOSEOUT.md) ghi rõ chưa phải Maven/Spring/JUnit và khi chuẩn bị patch còn chờ Windows signoff. M5 không chuyển con số đó thành evidence độc lập: lượt S3 mới đã chạy thật **174/174**, có JUnit/build/staging/HTTP và validator riêng. Unit regression xác minh các fix candidate trong phạm vi đã chạy; việc đóng thảo luận PR bên ngoài, native USBGuard và M3 ACL signoff vẫn là các xác nhận riêng.

## Tích hợp Agent/packaging — lịch sử ngày 31/10/2026

[M5 ACK M2/M3](evidence/M5_WEEK4_M2_M3_ACK_20261031.md) ghi nhận **đã nhận và ACK kỹ thuật Windows** M2 Part 2 cùng bộ M3 closeout/guide/checklist/staging. [Evidence độc lập ngày 31/10](week4-evidence/2026-10-31/integrated-agent-packaging/README.md) là nguồn kết quả S2; không chuyển lời báo PASS của owner thành kết quả M5 đã chạy hoặc thành proof S3.

| Phạm vi hiện tại | Trạng thái |
|---|---|
| M2 Part 2 | Source/test policy và active-session đã merge `b39425d`; **12/12 policy + 8/8 session PASS** trong lượt Maven S2, cùng **9/9 watch**. Đây là Windows unit/mock, không phải policy/session runtime Ubuntu. |
| M3 handoff | **ĐÃ NHẬN** closeout, manifest, Installation Guide, 14 phiếu Ubuntu và scripts prepare/full verifier. `.deb` vẫn **NOT BUILT**; staging Windows không chứng minh Linux mode/install. |
| Agent clean package | **156/156 PASS**, 0 failure/error/skip trên S2. Source-tree SHA-256 `3e312fbc65aa9bc71bb99f7dc4a9b1d0747fd1a19bf181430b54fa5e514bc59a`; Agent JAR **20.177.551 bytes**, SHA-256 `06e96129d308fd1bfb059d0885c8f2f378772cfeba76e24d649d8d3f43430a30`. |
| Chuẩn bị và kiểm tra staging | **PASS**: guarded staging preparation, **full Week 4 verifier không dùng Skip**, và kiểm tra độc lập **9/9 file byte-equal** với source/JAR hiện tại; [staging manifest](week4-evidence/2026-10-31/integrated-agent-packaging/staging-manifest.json). Chưa chứng minh POSIX mode, `.deb` hoặc Linux runtime. |
| Production HTTP constructor | **PASS tại capture 06**: **15 scenario / 14 request loopback thật / 255 assertion**, dùng production `AdminServerClient` constructor; [summary lần 02](week4-evidence/2026-10-31/integrated-agent-packaging/http-transport-summary-02.json). Đây là HTTP client tới server kiểm thử có response kiểm soát, không phải Spring backend auth/scheduler hay Ubuntu. |
| Evidence validation S2 | **PASS**: **5 positive captures**, Agent 156 tests, staging byte-equal 9/9, HTTP 255 assertions; [validation](week4-evidence/2026-10-31/integrated-agent-packaging/validation.json) đối chiếu source/log/JUnit/JAR/staging/transport/link và bảo toàn evidence cũ. Capture 05 FAIL được giữ riêng. |
| Frontend / Admin Server | Source không đổi; giữ evidence S0 ngày 28/10 và ACK M4, không có UI/browser/server regression mới trong lượt này. Packaging có thêm công cụ mới nên kiểm tra staging theo S2. |
| Phạm vi còn chờ tại ngày 31/10 | Khi đó M2 Part 3 còn thiếu; ngày 01/11 đã nhận source/guide/Q&A/checklist như mục S3. M1 freeze/security/candidate, M3 raw-provenance follow-up và runtime vẫn chưa được chốt bằng bảng S2 này. |

**Giới hạn provenance M3:** [closeout](../client-agent/WEEK4_CLIENT_AGENT_CLOSEOUT.md) ghi 156 tests/JAR `A926…` (20.177.520 bytes), nhưng [raw Maven](../client-agent/week4-evidence/01-agent-maven-test.txt) ghi 127 và [raw verifier](../client-agent/week4-evidence/02-packaging-staging-verification.txt) ghi JAR `219D…` (20.173.441 bytes). Raw staging/postinst hash cũng khác [manifest final](../client-agent/WEEK4_PACKAGING_MANIFEST.md). Đây là **mâu thuẫn evidence owner cần truy vết**, không phải failure mới đã tái hiện trên S2. Không sửa/ghi đè log owner; kết quả M5 trên S2 đã có SHA/log/hash riêng làm evidence hiện tại.

Proof S2 độc lập đã có [build manifest](week4-evidence/2026-10-31/integrated-agent-packaging/build-manifest.json), JUnit, staging/HTTP summaries, environment và checksums. [Scope equivalence](week4-evidence/2026-10-31/integrated-agent-packaging/scope-equivalence.json) xác minh frontend/server không đổi; `.github` không có trong cả hai revision được đối chiếu, không suy ra có CI đã chạy. Hash JAR M5 là định danh build mới, không cần trùng một JAR cũ được build ở thời điểm khác.

Capture HTTP 05 được giữ **FAIL của harness**: assertion ban đầu chỉ nhận Instant dạng ISO string, trong khi cùng JAR serialize `ack.appliedAt`/`event.occurredAt` thành numeric seconds. Harness được sửa để giải mã và so sánh đúng Instant; capture **06 PASS với cùng source/JAR**, không sửa application. Không coi lỗi kỳ vọng của công cụ này là bug ứng dụng hoặc xóa lần chạy thất bại khỏi evidence.

## Đối chiếu sau pull — lịch sử ngày 30/10/2026

[Evidence sau pull](week4-evidence/2026-10-30/post-pull-regression/README.md), [scope equivalence](week4-evidence/2026-10-30/post-pull-regression/scope-equivalence.json), [validation](week4-evidence/2026-10-30/post-pull-regression/validation.json) và [build manifest](week4-evidence/2026-10-30/post-pull-regression/build-manifest.json) theo dõi source S1 của ngày đó. Frontend, Admin Server và packaging khi ấy không thay đổi so với source đã kiểm tra ngày 28/10; giữ các kết quả bằng đối chiếu source, **không đổi ngày hoặc gọi là test chạy mới ngày 30/10**.

Lượt chạy mới ngày 30/10 dùng HEAD `751387bf166c528326ae91c6a16a820a6d8fc24a`, source-tree SHA-256 **`21318e741b1571ab31b887a969d05e10e462490063f686403b33098f79173715`**. Agent JAR tạo bởi lượt này có SHA-256 **`26f8d74b450c399a798a9cbc7a0c0fc34d7a80cb34960e34a21c176c74d6e5d9`**. **Validation ngày 30/10 PASS**: đối chiếu source/log/JUnit/JAR/link, source equivalence và bảo toàn evidence cũ; kết quả được ghi riêng trong [validation.json](week4-evidence/2026-10-30/post-pull-regression/validation.json), không suy ra chỉ từ build thành công.

| Thay đổi / kiểm tra | Kết quả hoặc bước hiện tại |
|---|---|
| M2 watch hardening | Đã merge `ad57fa4`: đọc dòng/section có giới hạn, giữ dedup ngắn hạn qua reconnect, chặn tăng map không giới hạn. **9/9 test watch/stress mới PASS** trong lượt Agent hiện tại; đây là mock/unit evidence, không phải daemon/USB thật. |
| Agent `clean package` | **136/136 PASS**, 0 failure/error/skip, executable JAR build thành công; [01-agent-package](week4-evidence/2026-10-30/post-pull-regression/01-agent-package.json). Số 127 ngày 28/10 được giữ riêng trong bảng lịch sử. |
| Static/JAR verifier | **PASS** trên Agent JAR vừa build; [02-packaging-static](week4-evidence/2026-10-30/post-pull-regression/02-packaging-static.json). Không chạy installer/systemd hoặc tạo `.deb`. |
| M4 handoff / M5 acknowledgement | **ĐÃ NHẬN / ACK KỸ THUẬT**: [M4 handoff](evidence/M4_WEEK4_HANDOFF.md) và [M5 đối chiếu](evidence/M5_WEEK4_POST_PULL_ACK_20261030.md). Review của M4 ở `6127766` áp dụng cho frontend không đổi trên HEAD hiện tại. Không còn chờ M4 gửi bản review shared frontend này. |
| Candidate và các owner còn lại tại ngày 30/10 | Khi ấy chờ M1, M2 phần 2–3 và M3 package/guide. M2 Part 2/M3 handoff đã nhận ngày 31/10 như mục S2; M4 ACK không tự trở thành quyết định candidate của nhóm. |
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
| W4-M5-02 | **S4 WINDOWS TECHNICAL + EVIDENCE PASS**: build/server **28/28**, policy **1/1**, 3 browser suites, Agent→real prod Spring **215 assertions / 64 scenarios / 64 HTTP operations**, validator **7 positive captures PASS** | Group candidate/sign-off và M2/M3 dispositions riêng; rerun phần bị ảnh hưởng nếu candidate đổi. Giữ 07 FAIL của QA, không phải application regression |
| W4-M5-03 | **TECHNICAL + EVIDENCE PASS compatibility S4** qua Events/management/packaged browser, cùng M4 shared-frontend ACK và source frontend không đổi | Group source/artifact acceptance còn riêng; attribution/enforcement native vẫn hoãn Ubuntu |
| W4-M5-04 | **DONE script/Q&A và S4 management/Events/same-origin packaged automated rehearsal PASS**, **25 screenshots**; packaged suite giữ 31 dòng assertion gốc | Không có CDP recording/audio/narrated walkthrough/encoded video/live Ubuntu mới; `--no-recording` được ghi rõ |
| W4-M5-05 | **DONE evidence S0–S4**, validator/checksums S4 **PASS**: **25 screenshots**, 7 positive captures (01–06/08), 07 FAIL và sanitized summaries; assets 5/5 byte-equal, lịch sử giữ nguyên | **39 screenshots / 207 frame** vẫn thuộc bộ ngày 28/10; không có recording S4 hoặc secret-bearing DEBUG log trong backup public. Bổ sung evidence khi source/candidate đổi |
| W4-M5-06 | **DONE receipt/report + M1 WINDOWS TECHNICAL ACK S4**, freeze/security/docs/artifact/M1candidate đã nhận và current proof/validator hoàn tất | Group owner decision/sign-offs, M2 contract/native/external-review disposition, M3 IPC/provenance và deployment rotation còn riêng |
| W4-M5-07 | **DONE — mapping 14 case M3 và 9 case M2** vào case M5 hiện hữu, thêm biến thể rule-ID renumber/rollback và peer/ACL/path collision | Tất cả native case vẫn NOT EXECUTED/DEFERRED; cần `.deb` thật, M2/M3 và Ubuntu/USB/GUI |

## Đề xuất go/no-go

**GO cho bàn giao M5 và M1 Windows technical ACK trên S4: execution, evidence validator và artifact/source checks đã PASS.** **NO-GO cho M5 tự tuyên bố group `WEEK 4 — WINDOWS/RELEASE-CANDIDATE PREPARATION PASS`** còn do **owner/group dispositions/sign-offs**, không do thiếu M1 input hoặc kiểm tra M5 hiện tại. M1 candidate đã đề xuất; M3 IPC là **P1/cross-owner**, M2 contract/native/external-review disposition và rotation/re-enrollment ngoài workspace cần được ghi nhận. API oversized/bootstrap wording và provenance M3 cũ là owner follow-up riêng. `.deb NOT BUILT`, Ubuntu full gate **NOT EXECUTED / DEFERRED UNTIL AFTER WEEK 4**.

## Evidence và provenance — bộ lịch sử ngày 28/10

[Validation PASS](week4-evidence/2026-10-28/windows-regression/validation.json) xác minh **18 lệnh positive**, log/exit/source hashes, JUnit hiện tại, **39 screenshot** thuộc đúng browser suite, **207 frame** cùng hash/byte/timing và **5 frontend assets** giống byte trong server JAR. Bản ghi màn hình tự động dài khoảng **10,6 giây** phản ánh tốc độ chạy assertions; không phải walkthrough thuyết minh 8–10 phút.

- [Environment](week4-evidence/2026-10-28/windows-regression/environment.json): Java 22.0.2, Maven 3.9.11, Node 24.22.1, npm 11.19.1, Chromium 156.0.8078.49, Windows; times ghi UTC và Asia/Bangkok.
- [Build manifest](week4-evidence/2026-10-28/windows-regression/build-manifest.json): checksum JAR server/Agent, manifest và assets. Attribution dùng build thành công, source ổn định và thời gian ghi artifact; hash server được đối chiếu thêm với provenance của browser rehearsal. Hash build không được gán ngược thành đã lưu ngay trong raw command metadata nếu thực tế lưu ở bước validation.
- [QA document/tool manifest](week4-evidence/2026-10-28/windows-regression/qa-documents.json) ghi hash các thay đổi M5 trong working tree; tách với application manifest trên source SHA gốc.
- [Evidence SHA-256 inventory](week4-evidence/2026-10-28/windows-regression/checksums.json), [screenshots](week4-evidence/2026-10-28/windows-regression/screenshots.json), [recording replay](week4-evidence/2026-10-28/windows-regression/recording/replay.html) và [provenance](week4-evidence/2026-10-28/windows-regression/recording/provenance.json).

Lần `npm ci --offline` thiếu cache được lưu FAIL và có lần cài registry đúng lockfile PASS; bootstrap sandbox chặn child process trước khi suite chạy; lần kiểm tra evidence đầu tiên gặp thứ tự tạo link index được ghi ở [validation-attempts](week4-evidence/2026-10-28/windows-regression/validation-attempts.json) và đã sửa. Không coi các lỗi công cụ/môi trường đã giải quyết là bug ứng dụng, cũng không xóa chúng khỏi hồ sơ. Text logs đã che key credential; các ảnh mẫu/đầu-cuối recording đã được xem, dữ liệu là fixture/QA tổng hợp. Không sao lưu H2, token hay browser profile.

Trong lượt M5 ngày 28/10, source application/test/packaging không bị sửa; evidence Week 1–3 giữ nguyên. Sau pull ngày 30/10, Agent có thay đổi của M2 như đã nêu; M5 chỉ cập nhật tài liệu/evidence, không sửa implementation của member khác. Giới hạn browser history co lại ở trang cuối vẫn chưa có scenario trực tiếp, chưa có failure ứng dụng tái hiện.

## Handoff cần từ các member cho Week 4

Trạng thái dưới đây dựa trên source, tài liệu và evidence đã nhận trong repository; không mở lại nghiệm thu Week 3.

| ID | Owner | Cần bàn giao/xác nhận | Điều kiện để M5 chốt |
|---|---|---|---|
| W4-H01 | M1 | **ĐÃ NHẬN / WINDOWS TECHNICAL ACK S4 + VALIDATION PASS** v0.2 freeze/security/guides và M1 candidate `d78189b`/JAR `43269264…`; fresh M5 build `0772ae2a…` riêng, source-equivalent | Owner giải thích API oversized400/bootstrap257401 doc discrepancy; group sign-off và deployment rotation còn riêng. Không còn thiếu M1 input hoặc proof M5 S4 |
| W4-H02 | M2 | **Part 1/2/3 ĐÃ NHẬN; S3 WINDOWS TECHNICAL ACK + VALIDATION PASS**, gồm 174 tests, core guide/Q&A/9 native cases và fix-candidate coverage PR #40 | External PR, contract/native/cross-owner confirmation còn riêng. `SO_PEERCRED` Linux/popup thật không được chốt từ Windows; không còn thiếu Part 3 docs hoặc proof M5 S3 |
| W4-H03 | M3 | **Guide/14 cases và S2 ACK ĐÃ CÓ; JAR/staging/HTTP S3 kiểm tra PASS**; `.deb NOT BUILT` công khai. M2 Part 3 nêu per-UID socket ACL/startup follow-up | M3 cung cấp thiết kế quyền/socket collision an toàn với M2; native IPC sau Week 4. Owner raw 127/219D vs 156/A926 vẫn là follow-up lịch sử riêng, không phải failure S3 |
| W4-H04 | M4 → M5 | **ĐÃ NHẬN / ACK KỸ THUẬT** qua [handoff](evidence/M4_WEEK4_HANDOFF.md)/[M5 ACK](evidence/M5_WEEK4_POST_PULL_ACK_20261030.md); frontend source giữ nguyên | Backend mới cần compatibility/browser S4; không yêu cầu M4 gửi lại handoff không đổi. Group acceptance và actual enforcement không suy ra từ UI PASS |
| W4-H05 | M1 + các owner | **M1 review/candidate selection và M5 S4 proof/ACK ĐÃ CÓ**; group sign-off/dispositions và RC/tag chưa có | Ghi SHA/artifact/scope/ngoại lệ và quyết định nhóm sau owner dispositions; M5 không ký thay owner hoặc tuyên bố FULL RELEASE PASS |

## Bảo mật và giới hạn triển khai

[Security follow-up Week 3](WEEK3_DEPLOYMENT_SECURITY_FOLLOWUP.md) vẫn là nguồn theo dõi credential từng có trong Git history. M5 không có bằng chứng quyền truy cập deployment để xác nhận revoke/rotation/re-enrollment; cần M1/người quản trị môi trường xác nhận, chỉ lưu kết quả đã che secret. Không xem việc `.gitignore` runtime data là token đã được thu hồi.

Các kiểm tra Windows không chứng minh production HTTPS, quyền service/IPC thực tế, local fail-closed trên USB thật, user/session thực hoặc native popup. Bộ phiếu Ubuntu chỉ là chuẩn bị để chạy sau Week 4.
