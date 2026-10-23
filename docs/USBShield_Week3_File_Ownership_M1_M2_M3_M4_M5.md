# USBShield Ubuntu — Week 3 File Ownership & Completion Plan (M1–M5)

> **Dự án:** Xây dựng hệ thống ngăn chặn và quản lý USB Flash Disk trên Ubuntu 24.04 LTS  
> **Tuần:** Week 3 — *Productization + Per-Endpoint Isolation + `.deb`*  
> **Nguồn yêu cầu chính:** `USBShield_Project_Blueprint_Week2_Client_Notification_Updated(1)(8).md`, đặc biệt mục **26 — WEEK 3**, mục **22 — Phân chia công việc**, mục **23 — Documentation ownership**, mục **20 — Packaging và deployment**.  
> **Cấu trúc code đối chiếu:** [`chivan2925/USBProtection`, `main` tại `cf1bab2bf904783bfa4b6329595c5d21dd868deb`](https://github.com/chivan2925/USBProtection/tree/cf1bab2bf904783bfa4b6329595c5d21dd868deb) (20/10/2026).  
> **Phạm vi test được nhóm điều chỉnh:** Trong Week 3 chỉ nghiệm thu các kiểm thử **Windows** (Maven, Node/npm, HTTP/browser, mô phỏng lỗi mạng, kiểm tra script/cấu hình). **Các test phải chạy thật trên Ubuntu, cắm USB vật lý, GUI notification, clean install/reboot/systemd thực tế được hoãn đến sau Week 4.** Không ghi chúng là PASS khi chưa chạy.

## 1. Cách đọc danh sách

- **EXISTING — MODIFY/VERIFY:** File đã có trên `main`; hoàn thiện hoặc xác minh, **không bắt buộc sửa nếu đã đáp ứng**.
- **NEW — PROPOSED:** Tên file đề xuất để triển khai/test/document phần việc Week 3. Blueprint xác định *chức năng*, không quy định chính xác tên Java class/test file. Chỉ tạo khi cần; có thể chọn tên khác và không viết trùng chức năng.
- **GENERATED:** Artifact được build sinh ra, không mặc nhiên cần commit.
- **P0:** Điều kiện cần cho phạm vi Week 3; **P1:** hoàn thiện/hardening hỗ trợ.
- Prefix `.../` trong bảng nghĩa là giữ nguyên thư mục gốc đã nêu ở ngay phía trên; đường dẫn không có prefix là đường dẫn tính từ repository root.
- Trạng thái dưới đây là **kế hoạch cần thực hiện**, không phải xác nhận rằng các file chưa đạt. Cần review/diff/test để kết luận hoàn thành.

### Quy tắc ownership (giữ nguyên từ Week 2)

| Owner | Phạm vi ghi code/tài liệu |
|---|---|
| **M1** | `admin-server/**`; `docs/ARCHITECTURE.md`, `docs/API.md`, `docs/AGENT_SERVER_PROTOCOL.md`, phần hướng dẫn backend; tích hợp final server JAR |
| **M2** | `client-agent/.../usbguard/**`, `session/**`, `notification/**`, `event/UsbEventEnricher.java` và tests tương ứng; `packaging/usbguard/**`; `docs/usbguard/**` và tài liệu USBGuard/active-user |
| **M3** | `client-agent/.../config/**`, `endpoint/**`, `sync/**`, `outbox/**`, `system/**`, `event/EventSenderService.java` và `EventSimulationRunner.java`; `packaging/systemd/**`, `packaging/agent-deb/**`, `packaging/scripts/**`, `packaging/desktop/**`; `docs/client-agent/**`, `docs/CLIENT_INSTALLATION.md` |
| **M4** | `frontend/` phần app shell, auth, Dashboard, Endpoints/Detail, Devices, whitelist/management API adapter và test; `docs/admin-web/**`, `docs/ADMIN_GUIDE.md` |
| **M5** | `frontend/` phần Event History, event mapper/filter, event tests; `docs/qa/**`, `docs/TEST_PLAN.md`, `docs/DEMO_SCRIPT.md` |

> File dùng chung như `frontend/package.json`, `frontend/src/services/api.ts`, `frontend/tests/browser.mjs`, `frontend/vite.config.ts` phải có owner chốt và review chéo; tránh M4/M5 cùng ghi đè. M3 **không chỉnh Java core của M2** chỉ để sửa packaging.

---

## 2. M1 — Admin Server Lead / Architecture / Integration Owner

**Mục tiêu Week 3 theo blueprint:** Harden Admin/Agent authentication, endpoint ONLINE/OFFLINE, policy-version reconciliation, central production profile và **đưa React production build vào Admin Server** để chạy same-origin. Không viết lại API vốn đã hoàn thành Week 2.

**Gốc Java:** `admin-server/src/main/java/com/group/usbshield/server/`  
**Gốc test:** `admin-server/src/test/java/com/group/usbshield/server/`

| Mức | File / nhóm file | Loại | Việc cần hoàn thành / điều kiện chấp nhận |
|---|---|---|---|
| P0 | `config/SecurityConfig.java` | EXISTING — VERIFY/MODIFY | Xác minh Admin session/CSRF và Agent token được phân quyền tách biệt; request thiếu/sai quyền bị từ chối; không mở lại `/api/agent/**` công khai. |
| P0 | `agentapi/AgentAuthenticationFilter.java`, `agentapi/AgentCredentialService.java` | EXISTING — VERIFY/MODIFY | Kiểm tra token hash, endpoint binding, forged ID, 401/403 và hành vi khi thông tin xác thực bị lỗi. |
| P0 | `agentapi/AgentEnrollmentController.java`, `auth/AdminAuthController.java`, `auth/AdminBootstrap.java` | EXISTING — VERIFY/MODIFY | Đăng ký không cấp lại token tùy tiện; không hard-code secret; login/logout/rotate session hoạt động; quy trình bootstrap rõ ràng. |
| P0 | `endpoint/Endpoint.java`, `endpoint/AdminEndpointController.java`, `endpoint/EndpointRepository.java` | EXISTING — VERIFY/MODIFY | ONLINE/OFFLINE dựa trên thời điểm heartbeat và timeout; endpoint không có heartbeat phải hiển thị OFFLINE đúng, không lưu trạng thái ONLINE vô hạn. |
| P0 | `policy/EndpointPolicyService.java`, `policy/AgentPolicyController.java`, `policy/EndpointWhitelistEntryRepository.java` | EXISTING — VERIFY/MODIFY | Desired/applied version phân biệt; ACK đúng endpoint, không nhận version tương lai/lùi; duplicate Allow/Revoke idempotent; Endpoint A không làm đổi B. |
| P0 | `admin-server/src/main/resources/application.yml` | EXISTING — MODIFY/VERIFY | Phân biệt cấu hình phát triển và triển khai; cấu hình DB, cookie, logging, network/port không nhúng secret production. |
| P0 | `admin-server/src/main/resources/application-prod.yml` | NEW — PROPOSED | Profile triển khai Admin Server (nếu thật sự cần): đọc secret/DB path từ env hoặc external config, kiểm soát bind/HTTP(S)/cookie; không sinh mật khẩu mặc định. |
| P0 | `admin-server/pom.xml`, `admin-server/src/main/resources/static/` | MODIFY/VERIFY + GENERATED | Build React (`frontend/dist`) rồi đưa vào Spring Boot JAR để phục vụ SPA và `/api` cùng origin; xử lý route fallback nếu cần, không phá REST routes. Nội dung `static/` có thể là đầu ra build, **không bắt buộc commit**. |
| P0 | `admin-server/src/test/java/com/group/usbshield/server/Week2ServerIntegrationTest.java`, `PolicyConsumerContractTest.java` | EXISTING — EXTEND/VERIFY | Không regression các contract Week 2 khi thêm production/static UI. |
| P0 | `admin-server/src/test/java/com/group/usbshield/server/Week3SecurityAndStatusTest.java` | NEW — PROPOSED | Test token sai, endpoint giả mạo, status timeout, negative auth và boundary Admin/Agent. |
| P0 | `admin-server/src/test/java/com/group/usbshield/server/Week3PolicyVersionTest.java` | NEW — PROPOSED | Test desired/applied/ACK, policy unchanged/duplicate, per-endpoint isolation; có thể mở rộng test cũ thay vì tạo file mới. |
| P1 | `docs/API.md`, `docs/AGENT_SERVER_PROTOCOL.md`, `docs/ARCHITECTURE.md` | EXISTING — UPDATE IF NEEDED | Đồng bộ contract/deploy profile và cách server phục vụ SPA; tránh viết lại tài liệu Week 2 không thay đổi. |
| P1 | `docs/member1/WEEK3_SERVER_HARDENING.md` | NEW — PROPOSED | Báo cáo trạng thái từng gate M1 và liên kết test logs Windows; liệt kê các kiểm thử Ubuntu hoãn. |

**Bàn giao M1 → M3/M4/M5:** URL/headers/DTO canonical, desired/applied semantics, logic offline và cách chạy một JAR chứa React + H2/API. Nếu API không thay đổi, phải ghi rõ *contract unchanged*.

---

## 3. M2 — USBGuard Core + Active User Attribution + Notification Core

**Mục tiêu Week 3 theo blueprint:** Xử lý parser edge cases, USB **không có serial**, độ bền gateway/policy, reconnect/reboot behavior, session switch/ambiguity; hỗ trợ M3 về quy tắc USBGuard và cài đặt. Trong Week 3 hiện tại, mô phỏng các đầu vào Ubuntu bằng fixtures/mocks trên Windows; **không biến kết quả giả lập thành hardware PASS**.

**Gốc Java:** `client-agent/src/main/java/com/group/usbshield/agent/`  
**Gốc test:** `client-agent/src/test/java/com/group/usbshield/agent/`

| Mức | File / nhóm file | Loại | Việc cần hoàn thành / điều kiện chấp nhận |
|---|---|---|---|
| P0 | `usbguard/UsbGuardOutputParser.java`, `usbguard/UsbGuardDeviceMapper.java`, `usbguard/UsbGuardEventParser.java`, `usbguard/UsbGuardPresenceParser.java` | EXISTING — VERIFY/MODIFY | Parser không crash khi thiếu serial, trường rỗng, class/interface lạ, output nhiều dòng, ký tự/quote hợp lệ; phân biệt PresenceChanged với PolicyChanged/PolicyApplied. |
| P0 | `usbguard/UsbGuardRuleBuilder.java`, `usbguard/DeviceFingerprint.java`, `usbguard/UsbGuardPolicySynchronizer.java` | EXISTING — VERIFY/MODIFY | Whitelist theo **USBGuard hash** khi có; thiếu serial không được tự suy ra identity yếu; reject fingerprint không hợp lệ; giữ base rule/non-managed rules; fail-closed nếu reconcile lỗi. |
| P0 | `usbguard/UsbGuardCliGateway.java`, `usbguard/UsbGuardCommandRunner.java`, `usbguard/AdaptiveUsbGuardGateway.java` | EXISTING — VERIFY/MODIFY | Chạy subprocess theo argv, timeout/exit-code rõ; không `sudo` trong Java; retry/daemon unavailable không báo apply thành công/ACK giả. |
| P0 | `usbguard/UsbGuardEventListener.java`, `usbguard/UsbGuardPresenceEvent.java` | EXISTING — VERIFY/MODIFY | Watch reconnect/dedup an toàn; chỉ sự kiện Insert/Remove phát telemetry; lỗi notification không chặn transport. |
| P0 | `session/LoginctlActiveUserResolver.java`, `session/ProcessLoginctlGateway.java`, `session/LinuxActiveUserResolver.java`, `session/AdaptiveActiveUserResolver.java` | EXISTING — VERIFY/MODIFY | Active local session duy nhất được chọn; user switch, logout, remote-only, no GUI và session ambiguity trả `UNKNOWN` đúng; không hard-code UID/session. |
| P1 | `notification/BlockNotificationPolicy.java`, `notification/DesktopNotificationService.java`, `notification/UnixSocketNotificationPublisher.java`, `notification/DesktopNotificationClient.java` | EXISTING — VERIFY | Giữ bộ lọc BLOCKED class 08 Insert, 1 popup/Insert và no-GUI fail-independent; Week 3 không viết lại notifier nếu Week 2 đã đáp ứng. |
| P0 | `client-agent/src/test/java/com/group/usbshield/agent/usbguard/UsbGuardOutputParserTest.java`, `UsbGuardPolicySynchronizerTest.java`, `UsbGuardEventListenerTest.java` | EXISTING — EXTEND | Thêm fixtures/mocks: không serial, malformed output, base/foreign rule preservation, reconnect, duplicate suppression, apply failure. |
| P0 | `client-agent/src/test/java/com/group/usbshield/agent/session/LoginctlActiveUserResolverTest.java`, `LinuxActiveUserResolverTest.java` | EXISTING — EXTEND | User switch, active seat change, ambiguous concurrent sessions, remote session, logout/no GUI và UNKNOWN. |
| P1 | `client-agent/src/test/java/com/group/usbshield/agent/usbguard/UsbGuardNoSerialRegressionTest.java` | NEW — PROPOSED | Chỉ thêm nếu các edge-case thiếu serial chưa gọn trong test cũ. |
| P1 | `packaging/usbguard/rules-v0.1.conf` | EXISTING — VERIFY | Base rule không bị thay đổi hoặc ghi đè ngoài ý muốn; M2 cung cấp điều kiện cài đặt/quyền IPC cho M3, **M3 sở hữu script installer**. |
| P0 | `docs/usbguard/WEEK3_CORE_HARDENING.md` | NEW — PROPOSED | Tổng hợp trường hợp không serial/reconnect/user switch, bộ fixtures, Windows unit results, checklist Ubuntu hoãn. |
| P1 | `docs/usbguard/USBGuard_Week2_Integration.md`, `docs/usbguard/WEEK2_HANDOFF_M3.md` | EXISTING — VERIFY / ADDENDUM | Chỉ thêm lưu ý tương thích với Week 3 nếu cần; không sửa lịch sử test Week 2 thành Ubuntu PASS. |

**Bàn giao M2 → M3:** Định nghĩa chính xác fingerprint được whitelist, điều kiện USBGuard IPC read/write, cách preserve local/base policy và cách xử lý daemon offline. Không tự đổi contract `UsbGuardGateway` nếu chưa thống nhất M3.

---

## 4. M3 — Client Agent / System Service / Packaging Engineer

**Mục tiêu Week 3 theo blueprint:** Chốt systemd/permissions, `.deb`, enrollment configuration, restart/retry, client notifier packaging và phương án clean install. **Xây dựng package/cấu hình trong Week 3; kiểm thử chạy thật trên Ubuntu hoãn theo quyết định của nhóm.**

| Mức | File / nhóm file | Loại | Việc cần hoàn thành / điều kiện chấp nhận |
|---|---|---|---|
| P0 | `packaging/systemd/usbshield-agent.service` | EXISTING — VERIFY/MODIFY | Chạy non-root `usbshield`, live profile, external config, restart policy, USBGuard dependency; thư mục dữ liệu ghi được; không bật event simulation. |
| P0 | `packaging/agent-deb/DEBIAN/control` | EXISTING — VERIFY/MODIFY | Metadata, `openjdk-21-jre-headless`, `usbguard`, `libnotify-bin`, architecture và dependencies hợp lệ. |
| P0 | `packaging/agent-deb/DEBIAN/postinst`, `prerm` | EXISTING — VERIFY/MODIFY | Install/uninstall idempotent, system user/permissions đúng; không mất whitelist/base config/endpoint credentials vô tình khi upgrade/uninstall; xử lý trạng thái systemd an toàn. |
| P0 | `packaging/agent-deb/etc/usbshield-agent/application.yml`, `client-agent/src/main/resources/application-live.yml` | EXISTING — VERIFY/MODIFY | Server URL/enrollment token cấu hình ngoài hoặc an toàn; data dir `/var/lib/usbshield-agent`; tắt simulation; không commit credential production. |
| P0 | `packaging/scripts/build-deb.sh` | EXISTING — VERIFY/MODIFY | Build đúng executable JAR và `.deb` cùng cấu hình/service/notifier; script fail rõ khi thiếu artifact/dependency; có thể build lặp lại. |
| P0 | `packaging/desktop/usbshield-notifier.desktop`, `usbshield-notifier-launcher.sh` | EXISTING — VERIFY/MODIFY | XDG autostart trong user GUI, load đúng Java helper từ packaged JAR, đường socket theo UID; không gọi `notify-send` từ system service. |
| P0 | `client-agent/src/main/java/com/group/usbshield/agent/endpoint/EndpointManager.java`, `EndpointRegistration.java` | EXISTING — VERIFY/MODIFY | Enrollment token lưu bền; restart/reload không re-enroll sai; chỉ báo ACK nếu USBGuard apply thành công; bảo toàn pending ACK. |
| P0 | `client-agent/src/main/java/com/group/usbshield/agent/sync/HeartbeatCoordinator.java`, `PolicySyncService.java`, `AdminServerClient.java` | EXISTING — VERIFY/MODIFY | Offline/reconnect retry đúng; desired/applied/pending ACK không nhầm; no false-ACK; network error không làm agent crash. |
| P0 | `client-agent/src/main/java/com/group/usbshield/agent/outbox/OutboxSpoolService.java`, `event/EventSenderService.java` | EXISTING — VERIFY/MODIFY | Offline spool/durable FIFO, retry sau khi online, không mất event đã lưu, không giả mạo trạng thái gửi thành công. |
| P1 | `client-agent/src/main/java/com/group/usbshield/agent/event/EventSimulationRunner.java`, `client-agent/src/main/resources/application-simulation.yml` | EXISTING — VERIFY | Event giả chỉ bật trong profile simulation được chọn rõ; tuyệt đối không phát trong live. |
| P0 | `client-agent/src/test/java/com/group/usbshield/agent/sync/PolicyAckRecoveryTest.java`, `HeartbeatCoordinatorTest.java`, `AdminServerClientContractTest.java` | EXISTING — EXTEND | Test network flap, server-down, pending ACK qua restart, auth/token invalid, không ACK khi apply fail. |
| P0 | `client-agent/src/test/java/com/group/usbshield/agent/outbox/OutboxSpoolServiceTest.java`, `endpoint/EndpointManagerTest.java` | EXISTING — EXTEND | Kiểm tra spool/reload/retry, credential persistence, lỗi ghi đĩa và trạng thái khôi phục. |
| P0 | `packaging/tests/verify-week2-windows.ps1` | EXISTING — VERIFY/EXTEND | Kiểm tra tĩnh service, JAR, helper launch, package metadata/config trên Windows; có thể tạo một script Week 3 mới thay vì sửa script Week 2. |
| P0 | `packaging/tests/verify-week3-windows.ps1` | NEW — PROPOSED | Script kiểm tra metadata `.deb`, shell syntax nếu có bash, systemd ini, live config, notifier và archive staging; **không tuyên bố clean install Ubuntu PASS**. |
| P0 | `docs/CLIENT_INSTALLATION.md` | EXISTING — UPDATE DRAFT | Các bước cài `.deb`, cấu hình server URL/token, kiểm tra service, rollback/uninstall, quyền file; gắn nhãn Ubuntu runtime *DEFERRED*. |
| P0 | `docs/client-agent/WEEK3_PACKAGING_AND_RETRY.md` | NEW — PROPOSED | Checklist đóng gói + các bằng chứng unit/contract/Windows; phần clean install/reboot/GUI thật chờ thực thi. |
| P0 | `packaging/dist/usbshield-agent_1.0.0_amd64.deb` | GENERATED | Artifact đầu ra **dự kiến** của `build-deb.sh`; chạy `dpkg-deb` trong môi trường Linux phù hợp hoặc CI khi có, không mặc định là phải commit binary. |

**Bàn giao M3 → M2/M1/M5:** Mẫu cấu hình `.deb`/service, cách bật live, lifecycle enrollment/retry, bản kê quyền USBGuard/IPC và kế hoạch triển khai Ubuntu sau Week 4. M3 chỉ chỉnh phần M2 sở hữu khi có thỏa thuận review riêng.

---

## 5. M4 — Admin Frontend Lead

**Mục tiêu Week 3 theo blueprint:** Hoàn thiện Admin Web: Dashboard, Endpoints/Detail, per-endpoint whitelist UX, auth UX, trạng thái lỗi và tiến độ policy sync. **Không triển khai global whitelist hoặc fake protection status.**

| Mức | File / nhóm file | Loại | Việc cần hoàn thành / điều kiện chấp nhận |
|---|---|---|---|
| P0 | `frontend/src/App.tsx`, `frontend/src/layouts/AdminLayout.tsx`, `frontend/src/App.css`, `frontend/src/index.css` | EXISTING — VERIFY/MODIFY | Điều hướng nhất quán, bảo vệ route, responsive, layout không sai lệch khi backend trả empty/error. |
| P0 | `frontend/src/pages/Login.tsx`, `frontend/src/hooks/AuthProvider.tsx`, `frontend/src/hooks/useAuth.ts` | EXISTING — VERIFY/MODIFY | Login sai/đúng, CSRF/session restore/logout/expiration có thông báo rõ; không lưu credential vào localStorage. |
| P0 | `frontend/src/pages/Dashboard.tsx`, `frontend/src/pages/Endpoints.tsx`, `frontend/src/pages/Devices.tsx` | EXISTING — VERIFY/MODIFY | Dữ liệu thực từ API (không tự coi mock là production), trạng thái ONLINE/OFFLINE, lastSeen, counts và desired/applied chính xác. |
| P0 | `frontend/src/components/endpoint/EndpointWhitelistPanel.tsx`, `EndpointStatusBadge.tsx` | EXISTING — VERIFY/MODIFY | Allow/Revoke **theo endpoint**, xác nhận hành động, duplicate/idempotency, pending sync hoặc ACK failure thể hiện trung thực; không có nút global Allow. |
| P0 | `frontend/src/services/api.ts`, `frontend/src/services/http.ts`, `frontend/src/services/mappers/endpointMapper.ts`, `policyMapper.ts` | EXISTING — VERIFY/MODIFY | Typed API mapping; session cookie/CSRF; xử lý 401/403/409/5xx/network; không biến `desired` thành `applied`. Với `api.ts`, review cùng M5 để không làm gãy event adapter. |
| P0 | `frontend/src/hooks/useResource.ts`, `frontend/src/hooks/useToast.ts`, `frontend/src/components/Common.tsx`, `ConfirmDialog.tsx`, `Toast.tsx` | EXISTING — VERIFY/MODIFY | Loading/empty/error/retry/notification UX nhất quán; không giữ stale protected rows hoặc báo thay đổi policy đã áp dụng khi mới chỉ lưu desired. |
| P1 | `frontend/src/pages/Settings.tsx` | EXISTING — VERIFY | Chỉ hiển thị tính năng API hỗ trợ; chức năng chưa có backend phải ghi unavailable, không làm tính năng giả. |
| P0 | `frontend/tests/m4_auth.test.mjs`, `m4_admin_api.test.mjs`, `m4_endpoints.test.mjs` | EXISTING — EXTEND | Auth/session/CSRF, status offline, network errors, A/B isolation, desired vs applied và pending sync. |
| P0 | `frontend/tests/browser.mjs`, `liveServer.mjs` | EXISTING — VERIFY/EXTEND | Windows browser checks với fixture và Spring Boot/H2 thật dùng HTTP input thử nghiệm; startup/cleanup an toàn, không xem ACK thử nghiệm là Agent USBGuard apply. |
| P1 | `frontend/vite.config.ts`, `frontend/package.json` | EXISTING — VERIFY | Vite proxy same-origin, `build/lint/test`/browser suites chạy; tránh sửa scripts chung mà không báo M5. |
| P0 | `docs/admin-web/WEEK3_UI_UX_HARDENING.md` | NEW — PROPOSED | Danh sách màn hình đã hoàn thiện, unavailable features, test evidence và screenshots Windows. |
| P1 | `docs/ADMIN_GUIDE.md` | NEW — PROPOSED (DRAFT) | Có thể bắt đầu draft tuần 3; **bản final thuộc Week 4** theo blueprint. |

**Bàn giao M4 → M1/M5:** `frontend/dist` cho server integration, cách chạy/browser smoke, trạng thái UI dựa trên API thật; thay đổi shared adapter phải báo M5 trước khi merge.

---

## 6. M5 — Event History / QA / Release Validation

**Mục tiêu Week 3 theo blueprint:** Full regression, per-endpoint isolation, user attribution, server unavailable, uninstall/reinstall, clean install/reboot test plan và **Demo Script draft**. Do lịch đã thay đổi, Week 3 **chạy các phần khả thi trên Windows, còn Ubuntu E2E hoãn thực thi**.

| Mức | File / nhóm file | Loại | Việc cần hoàn thành / điều kiện chấp nhận |
|---|---|---|---|
| P0 | `frontend/src/pages/Events.tsx`, `frontend/src/components/event/EventFilters.tsx`, `EventTable.tsx` | EXISTING — VERIFY/MODIFY | Event History hiển thị endpoint, Linux username, USB, decision, timestamp; loading/empty/error không dùng mock thay thế data thực. |
| P0 | `frontend/src/services/mappers/eventMapper.ts`, `frontend/src/types/event.ts`, `frontend/src/utils/eventFilters.ts` | EXISTING — VERIFY/MODIFY | Giữ nguyên `UNKNOWN`, source timestamp, metadata paging/filter và endpoint identity; input sai phải fail rõ. |
| P0 | `frontend/tests/m5_events.test.mjs`, `m5_event_api.test.mjs`, `m5_live_api.test.mjs`, `eventFilters.test.mjs` | EXISTING — EXTEND | Regression theo endpoint/user/device/decision/date, page boundaries, session/auth errors, malformed response. |
| P0 | `frontend/tests/browser-live.mjs` | EXISTING — VERIFY/EXTEND | Windows browser against real Spring Boot/H2 bằng event HTTP tổng hợp; xác thực 401/403/5xx, lọc và thao tác; không coi đó là USB vật lý. |
| P0 | `docs/TEST_PLAN.md` | EXISTING — UPDATE | Dẫn chiếu kế hoạch Week 3, đánh dấu rõ Windows execution và các ca Ubuntu deferred. |
| P0 | `docs/qa/WEEK3_INTEGRATION_TEST_PLAN.md` | NEW — PROPOSED | Matrix end-to-end Week 3 theo blueprint: enrollment/heartbeat, per-endpoint A/B, no-serial, UNKNOWN/user switching, server-down, reboot, install/uninstall, notification. Phân chia `WINDOWS_EXECUTABLE`/`DEFERRED_UBUNTU`. |
| P0 | `docs/qa/WEEK3_TEST_INVENTORY.md` | NEW — PROPOSED | Owner/case ID/cấp độ test, môi trường, revision, PASS/FAIL/NOT EXECUTED; evidence link. |
| P0 | `docs/qa/WEEK3_QA_SUMMARY.md` | NEW — PROPOSED | Kết quả thực tế của Maven/npm/browser/shell validation đã chạy, lỗi còn mở; không tự đóng toàn bộ Release Gate. |
| P0 | `docs/DEMO_SCRIPT.md` | NEW — PROPOSED (DRAFT) | Kịch bản demo Week 3: Admin login → endpoint → event → Allow/Revoke A → B unaffected → outage/recovery; tách phần demo fixture/HTTP Windows và demo Ubuntu thật sau Week 4. |
| P0 | `docs/qa/week3-evidence/README.md` | NEW — PROPOSED | Index theo ngày/revision/OS/command/exit status; chỉ tạo thêm log và screenshots khi **đã chạy thật**. |
| P1 | `docs/qa/WEEK2_QA_SUMMARY.md`, `docs/qa/WEEK2_TEST_INVENTORY.md`, `docs/qa/M4_M5_WEEK2_HANDOFF.md` | EXISTING — PRESERVE | Lưu như historical Week 2 evidence; không ghi đè lịch sử hoặc biến các Ubuntu case chưa chạy thành PASS. |

**Bàn giao M5 → toàn nhóm:** Một báo cáo QA Week 3 phân biệt **implementation ready**, **Windows PASS** và **Ubuntu deferred**, nêu cụ thể owner của mỗi blocker còn lại.

---

## 7. Thứ tự triển khai và điểm giao nhau

| Thứ tự | Owner chính | Đầu ra/handoff | Người phụ thuộc |
|---|---|---|---|
| 1 | M1 | Xác nhận contract/security/status/policy API và production config | M3, M4, M5 |
| 2 | M2 | Edge-case fixtures, USBGuard reconciliation + session rules, yêu cầu quyền IPC/install | M3, M5 |
| 3 | M3 | Bản cấu hình Agent live, systemd, `.deb` staging, retry tests | M1, M2, M5 |
| 4 | M4 | UI hoàn thiện, desired/applied và offline display; React build | M1, M5 |
| 5 | M5 | Full Windows regression + matrix/evidence + demo draft | Cả nhóm |
| 6 | M1 (integration owner) + các owner | Rà soát kết quả đã merge trên `main`, quyết định Week 3 Windows Scope | Cả nhóm |

Không cần đợi người khác xong toàn bộ mới làm unit tests; khi thay contract dùng PR/review và chạy lại tests liên quan trên commit tích hợp.

## 8. Week 3 Gate và phạm vi nghiệm thu thực tế

### 8.1. Gate nguyên bản của blueprint — **chưa thể PASS chỉ bằng Windows**

Blueprint yêu cầu một **clean Ubuntu client** phải đi qua luồng:

```text
install .deb
→ enroll với Admin Server
→ ONLINE trên Admin Web
→ BLOCK unknown USB thật
→ gửi đúng active Linux username
→ nhận/apply policy riêng endpoint
→ tồn tại qua reboot
```

Đồng thời kiểm tra notifier GUI đúng UID, allow/revoke thiết bị và fail-closed khi server down. **Đây là Ubuntu/hardware E2E Gate, giữ trạng thái `DEFERRED UNTIL AFTER WEEK 4`.** Việc hoãn test không có nghĩa code chức năng được phép bỏ dở trong Week 3.

### 8.2. Gate tạm thời đã điều chỉnh — `WEEK 3 WINDOWS SCOPE`

- [ ] **M1:** `mvn -B -f admin-server/pom.xml clean test` PASS; auth/token/status/policy tests PASS; server JAR build; static React serving có test Windows HTTP/browser (nếu tích hợp trong Week 3).
- [ ] **M2/M3:** `mvn -B -f client-agent/pom.xml clean test` PASS; edge-case parser/session và mock policy/retry/outbox PASS; `mvn -B -f client-agent/pom.xml -DskipTests package` PASS.
- [ ] **M3 packaging:** Systemd, `.deb` control/scripts/live profile/desktop helper kiểm tra tĩnh PASS trên Windows. Nếu `.deb` binary chưa được build trong Linux/CI, ghi rõ `NOT BUILT/NOT VERIFIED`, **không PASS clean install**.
- [ ] **M4/M5:** `frontend/`: `npm ci`, `npm run lint`, `npm run build`, `npm test`, `npm run test:browser`, `npm run test:live`, `npm run test:browser:live` đều PASS.
- [ ] **M4/M5/M1:** Contract integration giữa React và server (login, endpoint, events, whitelist, desired/applied, session) PASS ở HTTP/browser Windows với data test; không gắn nhãn hardware event.
- [ ] **M5:** Week 3 test matrix/QA report/Demo Script draft có nguồn evidence, command, commit SHA, ngày, môi trường và trạng thái đúng.
- [ ] **Toàn nhóm:** Không còn P0 blocker cho phạm vi Windows; Ubuntu E2E, USB physical, clean `.deb` install, service reboot/IPC/GUI notification được ghi rõ trong backlog sau Week 4.

**Quyết định chốt hợp lệ khi đủ điều kiện:** `WEEK 3 — PASS (WINDOWS SCOPE) / UBUNTU E2E DEFERRED`. **Không dùng `WEEK 3 — FULL PASS`** trước khi Gate Ubuntu ở mục 8.1 thực sự được nghiệm thu.

## 9. Tránh làm nhầm Week 3 sang Week 4 hoặc tạo trùng chức năng

- **Week 3:** tập trung productization/hardening, `.deb` staging/packaging, production-ready Admin Server/React, tests Windows và QA draft.
- **Week 4 (theo blueprint):** API freeze, final code/security review, final docs/Guides, cuối cùng server JAR/`.deb`, demo rehearsal, release validation. Không tự gắn nhãn *final release* cho artifact Week 3.
- **Sau Week 4 (theo lịch nhóm):** mới chạy và nghiệm thu toàn bộ Ubuntu USBGuard/USB thật/user-switch/native GUI/systemd/`.deb` clean install, reboot và server-down fail-closed, cập nhật kết quả vào hồ sơ QA. Điều này là **thay đổi lịch thực thi test của nhóm**, không phải nội dung Gate nguyên bản trong blueprint.
- Giữ sự khác nhau: **unit/mock PASS** ≠ **HTTP integration PASS** ≠ **hardware Ubuntu PASS**.
- Không tạo thêm controller/service/mapper chỉ vì tên gợi ý trong kế hoạch nếu code hiện tại đã thực hiện tương đương.

---

**Checklist sử dụng:** Các owner đánh dấu những hàng `P0` đã hoàn thành hoặc không cần sửa sau khi review. Nếu phát hiện vấn đề mới, ghi Issue và liên kết PR/commit và log test, không thay đổi các evidence Week 1–2 đã đóng.
