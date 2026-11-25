# Ngăn chặn người dùng cắm usb flash disk vào máy tính ubuntu — Hướng dẫn Lập trình và Đóng góp Code (Developer Coding Guide)

> **Mục đích tài liệu:** Cung cấp hướng dẫn toàn diện từ kiến trúc, chuẩn coding, luồng phát triển tính năng, kiểm thử đến quy trình debug cho các lập trình viên tham gia phát triển dự án **Ngăn chặn người dùng cắm usb flash disk vào máy tính ubuntu** (`admin-server`, `client-agent`, `frontend`, và `packaging`).
> **Tài liệu tham chiếu liên quan:**
> - [docs/RUN_GUIDE.md](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/docs/RUN_GUIDE.md) (Hướng dẫn cài đặt, cấu hình môi trường & vận hành hệ thống)
> - [docs/ARCHITECTURE.md](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/docs/ARCHITECTURE.md) (Tài liệu kiến trúc hệ thống cốt lõi)
> - [docs/API.md](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/docs/API.md) & [docs/API_PHASE1_TO_PHASE3_ADDENDUM.md](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/docs/API_PHASE1_TO_PHASE3_ADDENDUM.md) (Quy chuẩn API hợp đồng)
> - [docs/AGENT_SERVER_PROTOCOL.md](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/docs/AGENT_SERVER_PROTOCOL.md) (Giao thức truyền thông Agent — Server)
> - [docs/ADMIN_SERVER_RELEASE.md](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/docs/ADMIN_SERVER_RELEASE.md) (Quy trình build JAR phát hành & kiểm thử Windows)

---

## 1. Tổng quan hệ thống và Cấu trúc Mã nguồn

### 1.1. Bức tranh kiến trúc đa thành phần

USBShield là hệ thống bảo mật Endpoint tập trung quản lý thiết bị USB Flash Disk trên Ubuntu 24.04 LTS, gồm 4 phân hệ chính:

```mermaid
graph TB
    subgraph Client_Ubuntu ["Ubuntu 24.04 Client Endpoint"]
        USB[USB Flash Disk / HID] -->|Kernel uevent| UG[USBGuard Daemon]
        UG -->|USBGuard CLI watch / list| AGENT[USBShield Client Agent - Java 21]
        LOGIND[systemd-logind] -->|loginctl query| AGENT
        AGENT -->|Local Spooling| OUTBOX[Local Outbox JSON Files]
        AGENT -->|Unix Socket IPC| NOTIFY[Desktop Notifier Helper]
    end

    subgraph Reverse_Proxy ["Reverse Proxy & Network Boundary"]
        NGINX[Nginx HTTPS Reverse Proxy + IP ACL]
    end

    subgraph Central_Server ["Admin Central Server"]
        SPRING[Spring Boot 3.3.4 Application]
        SPA[React 19 SPA Static Assets]
        PG[(PostgreSQL 16+ - Flyway V1/V2)]
        SPRING -->|Embedded| SPA
        SPRING -->|Spring Data JPA + JDBC| PG
    end

    subgraph Admin_Browser ["Admin Management Console"]
        BROWSER[Web Browser - Admin Workstation]
    end

    AGENT -->|TLS /api/agent/**| NGINX
    BROWSER -->|TLS /api/admin/** & Web SPA| NGINX
    NGINX -->|127.0.0.1:18080 prod / 8080 dev| SPRING
```

### 1.2. Các nguyên tắc kỹ thuật cốt lõi (Core Invariants)

Bất kỳ đoạn code nào được viết hoặc chỉnh sửa trong dự án đều phải tuân thủ nghiêm ngặt các nguyên tắc sau:

1. **Fail-Closed Principle (Mặc định chặn):**
   - Thiết bị USB Mass Storage (Interface class `08`) chưa được cấp phép luôn luôn bị chặn bởi USBGuard.
   - Khi mất kết nối mạng hoặc Agent gặp sự cố, USBGuard daemon trên máy trạm vẫn thực thi chính sách đã lưu cục bộ (`/etc/usbguard/rules.conf`). Thiết bị chưa cấp phép không được tự động mở trong bất kỳ tình huống lỗi nào.
   - Chuột/bàn phím (HID - class `03`) hoạt động bình thường theo quy tắc cơ sở `allow with-interface none-of { 08:*:* }`.
2. **Per-Endpoint Whitelist (Không dùng Global Whitelist):**
   - Không tồn tại khái niệm "Allow cho toàn bộ hệ thống". Mỗi quyền cấp phép (Whitelist Entry) gắn chặt với một cặp: `(endpoint_id, device_hash/fingerprint)`.
   - Một USB được phép cắm ở máy A tuyệt đối không được tự động cho phép ở máy B trừ khi có bản ghi cấp phép riêng cho máy B.
3. **Active User Attribution (Định danh người dùng tại thời điểm sự kiện):**
   - Ghi nhận thông tin phiên làm việc đồ họa local đang active tại thời điểm cắm USB (`linuxUsername`, `linuxUid`, `sessionId`, `sessionType`, `seat`).
   - **Quy tắc không đoán mò (No-guessing):** Nếu không thể xác định duy nhất phiên local active (máy đang lock screen, có nhiều session SSH, hoặc không có session đồ họa), hệ thống **bắt buộc** gán nhãn `UNKNOWN`.
4. **Tách biệt phân quyền (Role & Credential Separation):**
   - Admin routes (`/api/admin/**`, `/api/auth/me`, `/api/auth/logout`): Yêu cầu Session Cookie (`JSESSIONID`) của Spring Security + CSRF Token qua header `X-CSRF-TOKEN` (lấy từ `GET /api/auth/csrf` cho các request thay đổi trạng thái `POST`, `PUT`, `DELETE`).
   - Agent routes (`/api/agent/**`, trừ `/api/agent/enroll`): Yêu cầu định danh qua Header `X-Endpoint-Id` và Token bí mật qua `X-Agent-Token`.
   - Tuyệt đối không cho phép dùng Session Admin để gọi API Agent hoặc dùng Agent Token để gọi API Admin.
5. **Đồng bộ trạng thái chính sách hai pha (Desired vs Applied Policy):**
   - Khi Admin cấp phép/thu hồi (Allow/Revoke), Server tăng `policyVersion` (Desired Version).
   - Agent kéo chính sách mới về qua Heartbeat, nạp vào USBGuard thành công thì mới gửi bản tin ACK (`/api/agent/policy/ack`). Khi đó Server mới cập nhật `appliedPolicyVersion`.
   - Người dùng phải **rút USB ra cắm lại** để áp dụng chính sách mới.
6. **Giảm thiểu đặc quyền (Principle of Least Privilege):**
   - Client Agent chạy dưới user hệ thống không đặc quyền `usbshield` (`User=usbshield`, `Group=usbshield`), **tuyệt đối không chạy dưới quyền `root`**.
   - Quyền truy cập IPC của USBGuard được cấu hình riêng qua `IPCAccessControlFiles` cho user `usbshield`.
   - Helper hiển thị thông báo màn hình (`DesktopNotificationClient`) chạy trong session đồ họa của người dùng và cấp quyền socket cho user `usbshield` thông qua POSIX ACL (`u:usbshield:rw-`), cấm hoàn toàn các thư mục dùng chung lỏng lẻo (`chmod 1777`).
7. **Chiếu trạng thái sức khỏe USBGuard (USBGuard Health Projection):**
   - Server phản ánh trạng thái thực thi của Endpoint qua 4 mức: `HEALTHY`, `DEGRADED`, `UNAVAILABLE`, `UNKNOWN` (kèm cờ `usbGuardStale`).
   - Nếu `desiredPolicyVersion != appliedPolicyVersion`, trạng thái tự động hạ xuống `DEGRADED`.
   - Nếu Agent không gửi heartbeat quá 90 giây (`usbshield.offline-after-seconds`), trạng thái tự động chuyển thành `UNKNOWN` và `usbGuardStale=true`.

### 1.3. Cấu trúc cây thư mục dự án

```
USBProtection/
├── admin-server/                  # Backend Spring Boot 3
│   ├── src/main/java/             # Source code Java (Controllers, Services, Repositories, Entities)
│   ├── src/main/resources/        # application.yml, application-prod.yml, application-admin-reset.yml, db/migration (V1, V2)
│   ├── src/test/java/             # Integration tests, Unit tests, MockMvc suites
│   ├── pom.xml                    # Maven POM (Spring Boot 3.3.4, Java 21, Profile with-frontend)
│   └── build-release.ps1          # Script build JAR kèm frontend tĩnh & manifest
├── client-agent/                  # Daemon chạy trên máy trạm Ubuntu (User unprivileged: usbshield)
│   ├── src/main/java/             # Agent daemon, UsbGuard CLI gateway/watcher, session resolver, notifier
│   ├── src/main/resources/        # application.yml, application-live.yml, application-simulation.yml
│   ├── src/test/java/             # Unit & contract tests (Outbox, Policy sync, Notifier IPC, USBGuard parser)
│   └── pom.xml                    # Maven POM độc lập
├── frontend/                      # Admin Web Console (React 19, TypeScript, Vite)
│   ├── src/                       # Components, Pages, Hooks, Services, Types, Utils, Mocks
│   ├── tests/                     # Node.js native test runner & Browser/HTTP Live test suites
│   └── package.json               # NPM scripts, dependencies (React 19, Vite 8, Oxlint)
├── packaging/                     # Đóng gói và triển khai Ubuntu / Nginx
│   ├── agent-deb/                 # Cấu trúc Debian package (DEBIAN/{control,postinst,prerm}, etc/usbshield-agent/)
│   ├── desktop/                   # Script launcher & .desktop autostart cho Desktop Notifier Helper
│   ├── nginx/                     # Mẫu cấu hình reverse proxy HTTPS + IP ACL
│   ├── systemd/                   # Unit file usbshield-agent.service
│   ├── usbguard/                  # Chính sách cơ sở rules-v0.1.conf
│   ├── scripts/                   # build-deb.ps1 (Windows), build-deb.sh (Linux)
│   └── tests/                     # verify-windows.ps1 & các script kiểm tra Linux/HTTPS
├── scripts/                       # Tiện ích nạp biến môi trường (with-env.ps1)
└── docs/                          # Tài liệu kiến trúc, đặc tả API, hướng dẫn vận hành & QA
```

---

## 2. Thiết lập Môi trường Phát triển (Local Dev Setup)

### 2.1. Yêu cầu công cụ và phiên bản chuẩn

| Công cụ | Phiên bản yêu cầu | Ghi chú |
|---|---|---|
| **OS** | Windows 11 / Ubuntu 24.04 LTS | Dev backend/frontend chạy tốt trên Windows; Agent đầy đủ cần Ubuntu 24.04 |
| **JDK** | Java 21 hoặc 22 (LTS) | Khuyến nghị OpenJDK 21 / Oracle JDK 22.0.2. **Lưu ý:** JDK 24 chưa hỗ trợ Lombok 1.18.36 |
| **Maven** | 3.9+ | Trình quản lý dependency và build Java |
| **Node.js** | 22.x+ (Khuyến nghị 22.19+) | Dùng để chạy Frontend và bộ test Node (`--experimental-strip-types`) |
| **npm** | 10.9+ | Trình quản lý package Frontend |
| **PostgreSQL** | 16+ | Cơ sở dữ liệu chính của hệ thống |
| **PowerShell** | 7.6+ (Đã kiểm tra: 7.6.9) | Bắt buộc để chạy các script kiểm thử tự động trên Windows (`pwsh.exe`, hỗ trợ `System.Formats.Tar`) |

### 2.2. Cấu hình biến môi trường và file `.env` vs `.env.e2e.local`

Để bảo mật, dự án không commit mật khẩu hoặc bí mật môi trường vào Git. Hệ thống quy định rõ ràng sự tách biệt giữa môi trường kiểm thử và ứng dụng:
- **File `.env` (Chỉ dùng cho Test):** Chứa thông tin đăng nhập của role kiểm thử chuyên biệt `usbshield_test` và database test `usbshield_test`/`usbshield_browser_test`. Script `with-env.ps1 -RequireTestDb`, `build-release.ps1` và `verify-windows.ps1` sẽ đọc file này. **Không đặt credentials của database ứng dụng `usbshield` hay thông tin admin vào file này** (script build/verify chủ động xóa các biến `USBSHIELD_DB_*` khi chạy test để bảo vệ dữ liệu ứng dụng).
- **File `.env.e2e.local` (Dùng cho Runtime Production tại Lab E2E):** Chứa credentials thật của role `usbshield` và database `usbshield` (`USBSHIELD_DB_URL`, `USBSHIELD_DB_PASSWORD`, `USBSHIELD_ENROLLMENT_TOKEN`, ...). Dùng khi chạy JAR ở profile `prod` thông qua `scripts/with-env.ps1 -EnvFile .env.e2e.local -Command { ... }`.

Khi lập trình cục bộ:
1. Sao chép `.env.example` thành `.env` tại thư mục gốc của repository (chỉ điền 4 biến test DB):
   ```env
   # Database chạy Integration Tests & Browser Live (BẮT BUỘC RIÊNG BIỆT - DÙNG ROLE usbshield_test)
   USBSHIELD_TEST_DB_HOST=127.0.0.1
   USBSHIELD_TEST_DB_PORT=5432
   USBSHIELD_TEST_DB_USERNAME=usbshield_test
   USBSHIELD_TEST_DB_PASSWORD=your_test_password
   ```

2. Đối với **ứng dụng Backend Dev** (chạy profile `default` trong `application.yml`), thiết lập trực tiếp trong shell phiên làm việc (không lưu vào `.env`):
   - `USBSHIELD_DB_HOST` (mặc định: `127.0.0.1`)
   - `USBSHIELD_DB_PORT` (mặc định: `5432`)
   - `USBSHIELD_DB_NAME` (mặc định: `usbshield`)
   - `USBSHIELD_DB_USERNAME` (mặc định: `usbshield`)
   - `USBSHIELD_DB_PASSWORD` (mật khẩu của user `usbshield`)
   - `USBSHIELD_ADMIN_USERNAME` (mặc định: `admin`)
   - `USBSHIELD_ADMIN_PASSWORD` (mật khẩu khởi tạo Admin ban đầu khi bảng `admin_users` chưa có user này)
   - `USBSHIELD_ENROLLMENT_TOKEN` (token cấp phát khi Agent đăng ký lần đầu)
   *(Lưu ý: Biến `USBSHIELD_DB_URL` chỉ được sử dụng khi kích hoạt profile `prod` trong `application-prod.yml`).*

> [!CAUTION]
> **Quy tắc cách ly Database Test:** Tuyệt đối không trỏ biến `USBSHIELD_TEST_DB_*` vào database chạy chính `usbshield`. Bộ test sẽ tự động tạo và xóa các schema `usbshield_it_*` và `usbshield_e2e_*`.

Để chạy lệnh nạp biến từ `.env` an toàn trên Windows với kiểm tra điều kiện test DB:
```powershell
./scripts/with-env.ps1 -RequireTestDb -Command { mvn -f admin-server/pom.xml clean test }
```

### 2.3. Khởi tạo Cơ sở dữ liệu PostgreSQL cho Dev

Đăng nhập PostgreSQL bằng quyền superuser (`postgres`) và khởi tạo 2 database cùng 2 role riêng biệt:

```sql
-- 1. Database & User cho ứng dụng dev
CREATE USER usbshield WITH PASSWORD 'your_dev_password';
CREATE DATABASE usbshield OWNER usbshield;
GRANT ALL PRIVILEGES ON DATABASE usbshield TO usbshield;

-- 2. Database & User cho kiểm thử tự động (Integration Tests & Browser Live)
CREATE USER usbshield_test WITH PASSWORD 'your_test_password';
CREATE DATABASE usbshield_test OWNER usbshield_test;
CREATE DATABASE usbshield_browser_test OWNER usbshield_test;
GRANT ALL PRIVILEGES ON DATABASE usbshield_test TO usbshield_test;
GRANT ALL PRIVILEGES ON DATABASE usbshield_browser_test TO usbshield_test;

-- Cho phép role test tạo schema tự do trong test database
\c usbshield_test
GRANT ALL ON SCHEMA public TO usbshield_test;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO usbshield_test;

\c usbshield_browser_test
GRANT ALL ON SCHEMA public TO usbshield_test;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO usbshield_test;
```

### 2.4. Khởi chạy từng service độc lập trong môi trường Dev

#### A. Khởi chạy Backend Admin Server
```powershell
cd admin-server
$env:USBSHIELD_DB_PASSWORD = "your_dev_password"
$env:USBSHIELD_ADMIN_USERNAME = "admin"
$env:USBSHIELD_ADMIN_PASSWORD = "AdminSecurePassword123!"
$env:USBSHIELD_ENROLLMENT_TOKEN = "BootstrapEnrollmentSecretToken2026"

mvn spring-boot:run
```
- Server lắng nghe tại: `http://127.0.0.1:8080`.
- Swagger / OpenAPI UI: `http://127.0.0.1:8080/swagger-ui.html` (chỉ mở ở profile `default`, tự động tắt khi chạy `prod`).

#### B. Khởi chạy Frontend React Dev Server
File [vite.config.ts](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/frontend/vite.config.ts) mặc định proxy `/api` về `https://usbprotection.lab` trừ khi được ghi đè bằng biến môi trường `API_PROXY_TARGET` (hoặc `USBSHIELD_API_TARGET`). Khi dev với Backend local ở cổng `8080`, hãy set `API_PROXY_TARGET`:
```powershell
cd frontend
npm ci
$env:API_PROXY_TARGET = "http://127.0.0.1:8080"
npm run dev
```
- Vite Dev Server lắng nghe tại `http://localhost:5173`.
- Mọi request `/api/**` được proxy sang `http://127.0.0.1:8080`, đảm bảo trình duyệt xử lý cookie session và CSRF cùng origin giống hệt môi trường production.

#### C. Khởi chạy Client Agent trên môi trường Dev (Windows)
Khi chạy trên Windows:
- [AdaptiveActiveUserResolver.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/session/AdaptiveActiveUserResolver.java) tự động nhận diện OS không phải Linux và fallback sang `MockActiveUserResolver`.
- Lưu ý: [AdaptiveUsbGuardGateway.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/usbguard/AdaptiveUsbGuardGateway.java) (`@Primary`, bean `realUsbGuardGateway`) vẫn gọi `UsbGuardCliGateway` (yêu cầu binary `usbguard` trên Linux), nên trên Windows Agent sẽ báo `usbGuardStatus=UNAVAILABLE` khi probe sức khỏe — điều này bình thường khi test luồng enroll/heartbeat/simulation với Server.
- Để giả lập sự kiện cắm USB tự động sau khi Agent khởi động 10 giây ([EventSimulationRunner.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/event/EventSimulationRunner.java)), kích hoạt profile `simulation`:

```powershell
cd client-agent
$env:USBSHIELD_SERVER_URL = "http://127.0.0.1:8080"
$env:USBSHIELD_ENROLLMENT_TOKEN = "BootstrapEnrollmentSecretToken2026"
mvn spring-boot:run -Dspring-boot.run.profiles=simulation
```

---

## 3. Hướng dẫn Code cho Từng Phân Hệ

### 3.1. Phân hệ Admin Server (`admin-server/`)

#### Kiến trúc phân tầng (Layered Architecture):
Mã nguồn đặt trong package `com.group.usbshield.server`:
- `auth/`: Xác thực Admin (Session/CSRF), kiểm soát phiên đăng nhập (`AdminSessionVersionFilter`), bootstrap tài khoản ban đầu (`AdminBootstrap`), khôi phục mật khẩu offline qua CLI profile `admin-reset` (`AdminPasswordResetRunner`, `AdminPasswordPolicy`). *Lưu ý:* Web UI đổi mật khẩu và trang Settings đã được loại bỏ khỏi hệ thống; không có endpoint đổi mật khẩu online qua HTTP.
- `agentapi/`: Các endpoint giao tiếp với Agent (`/api/agent/enroll`, `/api/agent/heartbeat`, `/api/agent/events`), lọc token (`AgentAuthenticationFilter`, `AgentCredentialService`), canonical digest chống sửa payload khi retry (`AgentEventCanonicalDigest`).
- `endpoint/`: Quản lý danh sách Endpoint, trạng thái kết nối, chiếu trạng thái sức khỏe USBGuard (`AdminEndpointController`, `EndpointRepository`).
- `policy/`: Nghiệp vụ Whitelist, phiên bản chính sách (`EndpointPolicyService`, `AdminEndpointWhitelistController`, `AdminWhitelistPageController`, `AgentPolicyController`, `UsbHashIdentity`).
- `event/`: Tiếp nhận, deduplicate và lưu trữ sự kiện cắm/rút USB (`AdminEventController`, `UsbEventRepository`) cùng các truy vấn tổng hợp hiệu năng cao bằng JDBC/CTE (`AdminScalabilityController`).
- `device/`: Danh mục thiết bị USB đã quan sát (`UsbDevice`, `UsbDeviceRepository`).
- `config/`: Cấu hình Spring Security (`SecurityConfig`), OpenAPI (`OpenApiConfig`), fallback SPA (`SpaController`), exception handler (`ApiExceptionHandler` trong `common/`).

#### Luồng xử lý một Request chuẩn:
```
HTTP Request
   ├──> Spring Security Filters (Kiểm tra Session/CSRF hoặc Agent Token Header)
   ├──> Controller (Validate DTO bằng @Valid / kiểm tra tham số truy vấn)
   ├──> Service / JDBC Query (Chứa nghiệp vụ, @Transactional)
   ├──> Repository (Spring Data JPA Interface hoặc NamedParameterJdbcTemplate)
   └──> PostgreSQL (Flyway managed tables)
```

#### Quy tắc bảo mật xác thực (Security Authentication):
Xem [SecurityConfig.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/admin-server/src/main/java/com/group/usbshield/server/config/SecurityConfig.java), [OpenApiConfig.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/admin-server/src/main/java/com/group/usbshield/server/config/OpenApiConfig.java) và [AgentAuthenticationFilter.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/admin-server/src/main/java/com/group/usbshield/server/agentapi/AgentAuthenticationFilter.java):
1. **Agent Request** (trừ `/api/agent/enroll`): Phải chứa 2 header bắt buộc:
   - `X-Endpoint-Id`: Định danh endpoint (`EP-<uuid>`).
   - `X-Agent-Token`: Token bí mật trả về khi enrollment (tối đa 72 bytes UTF-8).
   - Filter sẽ đối chiếu BCrypt với trường `agent_token_hash` trong bảng `endpoints`. Nếu sai hoặc không khớp `endpoint_id`, trả về `401 Unauthorized` ngay lập tức (hoặc `403 Forbidden` nếu `endpointId` trong body khác với header đã xác thực qua `AgentCredentialService.bind`).
2. **Admin Request:**
   - Dùng Session Cookie (`JSESSIONID`), được cấu hình `http-only: true`, `same-site: strict` (và `secure: true` mặc định ở profile `prod`).
   - Sử dụng `HttpSessionCsrfTokenRepository` (lấy token qua `GET /api/auth/csrf`). Mọi phương thức thay đổi trạng thái (`POST`, `PUT`, `DELETE`) **bắt buộc** có header `X-CSRF-TOKEN` khớp với token trong session.
3. **Production Profile (`application-prod.yml`):**
   - Bind loopback `127.0.0.1:8080` mặc định (đổi port qua `PORT`, thường là `18080` sau Nginx), `forward-headers-strategy: none`, `spring.jpa.open-in-view: false`, vô hiệu hóa `/swagger-ui.html` và `/api-docs`, và đọc datasource qua `USBSHIELD_DB_URL`.

#### Khôi phục Mật khẩu Admin qua CLI (`admin-reset` profile):
Khi cần thiết lập lại mật khẩu quản trị viên (chỉ thực hiện ngoại tuyến, không mở HTTP server):
```powershell
cd admin-server
$env:USBSHIELD_ADMIN_RESET_USERNAME = "admin"
$env:USBSHIELD_ADMIN_RESET_PASSWORD = "NewAdminPassword123!"  # Tối thiểu 12 ký tự, tối đa 72 bytes UTF-8 theo AdminPasswordPolicy
$env:USBSHIELD_DB_PASSWORD = "your_dev_password"

# Chạy profile bảo trì không bật web (spring.main.web-application-type=none)
mvn spring-boot:run -Dspring-boot.run.profiles=admin-reset

# Hoặc nếu thực hiện trên JAR độc lập:
java -jar target/usbshield-admin-server.jar --spring.profiles.active=admin-reset
```
*Cơ chế:* `AdminPasswordResetRunner` sẽ khóa dòng admin trong database (`lockByUsername`), từ chối nếu trùng mật khẩu cũ, băm mật khẩu mới và tăng trường `credential_version`. Bộ lọc `AdminSessionVersionFilter` sẽ ngay lập tức vô hiệu hóa (revoke) tất cả phiên đăng nhập cũ còn hiệu lực.

#### Pessimistic Locking trong cập nhật chính sách:
Khi thay đổi whitelist hoặc nhận ACK chính sách, để tránh race condition giữa các thread hoặc nhiều request đồng thời, bắt buộc dùng khóa bi quan (`PESSIMISTIC_WRITE`) trên dòng `Endpoint`:
```java
// Trong EndpointPolicyService.java
@Transactional
public EntryResponse allow(String id, AllowRequest request) {
    var endpoint = lock(id); // endpoints.lockByEndpointId(id)
    if (request.usbDeviceId() == null) {
        throw new ResponseStatusException(BAD_REQUEST, "usbDeviceId required");
    }
    var device = devices.findById(request.usbDeviceId())
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Device not found"));

    // Ràng buộc nghiệp vụ: USB phải từng được Agent ghi nhận cắm trên chính endpoint này
    if (!events.existsByEndpointIdAndUsbDeviceId(id, device.getId())) {
        throw new ResponseStatusException(CONFLICT,
            "USB has not been observed on this endpoint; pre-authorization is unsupported");
    }
    // Chỉ chấp nhận USBGuard HASH hợp lệ
    if (!UsbHashIdentity.valid(device.getDeviceHash())) {
        throw new ResponseStatusException(CONFLICT, "Invalid USBGuard HASH identity; manual review required");
    }
    ...
    endpoint.setPolicyVersion(endpoint.getPolicyVersion() + 1);
    return view(entry);
}
```

#### Cơ chế Chiếu trạng thái Sức khỏe USBGuard (USBGuard Health Projection):
Xem [AdminEndpointController.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/admin-server/src/main/java/com/group/usbshield/server/endpoint/AdminEndpointController.java):
Server tổng hợp trạng thái thực thi bảo vệ của máy trạm theo 4 mức:
- **`HEALTHY`**: Agent online, gửi báo cáo USBGuard hoạt động bình thường, thời gian kiểm tra gần nhất < 90 giây (`usbshield.offline-after-seconds`), và phiên bản chính sách đã đồng bộ hoàn tất (`policyVersion == appliedPolicyVersion`).
- **`DEGRADED`**: Daemon USBGuard hoạt động tốt nhưng chính sách vừa được sửa đổi và đang chờ Agent nạp/ACK (`policyVersion != appliedPolicyVersion`).
- **`UNAVAILABLE`**: Agent báo cáo không thể kết nối tới daemon `usbguard` trên hệ thống Ubuntu (hoặc `ImplicitPolicyTarget` không phải `block`).
- **`UNKNOWN`**: Máy trạm offline hoặc không nhận được báo cáo kiểm tra mới (`usbGuardStale = true`).

#### Quy tắc Phân trang & Truy vấn Hiệu năng cao:
Để tối ưu hiệu năng và tránh tải toàn bộ lịch sử về trình duyệt, luôn sử dụng các API phân trang và projection phía server:
- Danh sách Endpoint: `GET /api/admin/endpoints/page?page=0&size=20&status=online&q=...` ([AdminEndpointController.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/admin-server/src/main/java/com/group/usbshield/server/endpoint/AdminEndpointController.java))
- Danh sách Whitelist: `GET /api/admin/whitelist/page?page=0&size=20&endpointId=...&q=...` ([AdminWhitelistPageController.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/admin-server/src/main/java/com/group/usbshield/server/policy/AdminWhitelistPageController.java) — dùng `NamedParameterJdbcTemplate` với SQL thuần)
- Trạng thái Thiết bị & Tổng quan Dashboard: `GET /api/admin/device-states?page=0&size=20&...` và `GET /api/admin/dashboard/summary?from=...&to=...` ([AdminScalabilityController.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/admin-server/src/main/java/com/group/usbshield/server/event/AdminScalabilityController.java) — dùng `NamedParameterJdbcTemplate` với CTE PostgreSQL `ROW_NUMBER()` để chọn sự kiện mới nhất của mỗi thiết bị trên từng endpoint)
- Danh sách Sự kiện: `GET /api/admin/events?page=0&size=20&...` ([AdminEventController.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/admin-server/src/main/java/com/group/usbshield/server/event/AdminEventController.java))
*(Lưu ý: API cũ `GET /api/admin/endpoints` được đánh dấu `@Deprecated`, chỉ giữ lại cho tương thích ngược với giới hạn cứng tối đa 100 bản ghi).*

#### Quy tắc viết Flyway Migration:
- Thư mục: `admin-server/src/main/resources/db/migration/`.
- Định dạng tên file: `V<Version>__<Mo_ta_ngan_gon>.sql` (2 dấu gạch dưới). Hiện tại dự án đã có `V1` và `V2`. Migration mới tiếp theo sẽ là `V3`.
- **Tuyệt đối không dùng `DROP TABLE` hoặc `TRUNCATE`** trong migration lịch sử.
- Khi thêm cột mới, luôn dùng `ADD COLUMN IF NOT EXISTS` để hỗ trợ tương thích ngược.

---

### 3.2. Phân hệ Client Agent (`client-agent/`)

#### Nguyên tắc Giảm thiểu Đặc quyền (Least Privilege):
- Client Agent chạy nền như một systemd service trên Ubuntu nhưng **chạy dưới tài khoản người dùng hệ thống không đặc quyền `usbshield`** (`User=usbshield`, `Group=usbshield`), với các cờ bảo vệ `ProtectSystem=full`, `ProtectHome=read-only`, `NoNewPrivileges=true`.
- Quyền giao tiếp với daemon USBGuard được cấp riêng cho user `usbshield` thông qua tệp cấu hình IPC của USBGuard (`/etc/usbguard/IPCAccessControl.d/`).

#### Kiến trúc Giao tiếp Hệ thống (USBGuard CLI & Active User Resolver):
- [UsbGuardGateway](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardGateway.java):
  - [AdaptiveUsbGuardGateway.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/usbguard/AdaptiveUsbGuardGateway.java) (`@Primary`, bean `realUsbGuardGateway`) bọc [UsbGuardCliGateway.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardCliGateway.java) để thực thi lệnh CLI `usbguard` (`list-devices`, `list-rules`, `append-rule`, `remove-rule`, `get-parameter`) và [UsbGuardEventListener.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardEventListener.java) chạy `usbguard watch`.
  - [MockUsbGuardGateway.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/usbguard/MockUsbGuardGateway.java) dùng `@ConditionalOnMissingBean(name = "realUsbGuardGateway")` (hữu ích cho unit test / bean thay thế khi không dùng `realUsbGuardGateway`).
- **Bảo vệ quyền sở hữu rule (`usbshield-managed` & thu hồi rule lạ):**
  - Mọi rule `allow` do USBShield tạo ra đều gắn nhãn `label "usbshield-managed"` ([UsbGuardRuleBuilder.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/usbguard/UsbGuardRuleBuilder.java)).
  - Tác vụ định kỳ `withdrawForeignAllows()` mỗi 15 giây ([AdaptiveUsbGuardGateway.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/usbguard/AdaptiveUsbGuardGateway.java#L12-L23)) kiểm tra `ImplicitPolicyTarget=block` và xóa mọi rule `allow` ngoại lai trên máy trạm ngay cả khi mất kết nối tới Admin Server.
- [ActiveUserResolver](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/session/ActiveUserResolver.java):
  - [AdaptiveActiveUserResolver.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/session/AdaptiveActiveUserResolver.java) tự chuyển sang `MockActiveUserResolver` khi chạy trên OS không phải Linux.
  - Trên Linux, [LoginctlActiveUserResolver.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/session/LoginctlActiveUserResolver.java) kết hợp [ActiveSessionSelector.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/session/ActiveSessionSelector.java) phân tích output từ `loginctl list-sessions` và `loginctl show-session <id>`, chỉ nhận diện phiên đồ họa (`wayland`/`x11`) đang active trên `seat0`; nếu không xác định được duy nhất thì trả về `ActiveUserInfo.UNKNOWN`.

#### Enrollment An toàn & Bảo mật Truyền tải (`live` profile):
Xem [EndpointManager.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/endpoint/EndpointManager.java) và [AdminServerClient.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/sync/AdminServerClient.java):
- Trước khi gọi `/api/agent/enroll`, Agent sinh và lưu trước một `agentToken` 64 ký tự hex (`PendingEnrollment`) xuống đĩa để hỗ trợ idempotent retry nếu mất phản hồi HTTP giữa chừng. Thông tin sau khi đăng ký thành công được lưu tại `${usbshield.agent.data-dir}/endpoint.json`.
- Khi chạy với profile `live` ([application-live.yml](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/resources/application-live.yml)):
  - Bắt buộc `server-url` dùng `https://` (`require-https: true`), tắt HTTP redirect (`setInstanceFollowRedirects(false)`), hỗ trợ CA nội bộ dạng PEM qua `USBSHIELD_TLS_CA_CERTIFICATE` (vẫn giữ nguyên xác thực hostname).
  - Bắt buộc `data-dir` là đường dẫn tuyệt đối (`require-absolute-storage: true`, mặc định `/var/lib/usbshield-agent`).

#### Kiến trúc Thông báo Màn hình (Desktop Notification Helper & Socket IPC):
Do Agent chạy dưới quyền dịch vụ nền không có quyền truy cập trực tiếp vào DBUS / X11 / Wayland session của người dùng để gọi lệnh `notify-send`:
1. Dự án sử dụng tiến trình trợ lý GUI nhẹ [DesktopNotificationClientMain.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/notification/DesktopNotificationClientMain.java) chạy trong phiên đăng nhập của người dùng (tự khởi động qua XDG autostart `.desktop`).
2. Helper tạo một Unix Domain Socket cục bộ tại `/run/user/<uid>/usbshield-notifier/notice.sock` và cấp quyền ghi cho user dịch vụ `usbshield` bằng POSIX ACL (`u:usbshield:rw-`), đồng thời xác thực peer bằng `SO_PEERCRED` ([DesktopNotificationClient.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/notification/DesktopNotificationClient.java), [UnixSocketNotificationPublisher.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/notification/UnixSocketNotificationPublisher.java)).
3. Lớp [BlockNotificationPolicy.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/notification/BlockNotificationPolicy.java) thực hiện debounce / rate-limit để chống bão thông báo khi thiết bị USB bị cắm rút liên tục.

#### Cơ chế Lưu trữ Ngoại tuyến (Offline Spooling - Outbox):
Xem [OutboxSpoolService.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/outbox/OutboxSpoolService.java) và [AgentProperties.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/config/AgentProperties.java):
1. Khi phát hiện sự kiện cắm/rút USB, Agent tạo file JSON trong thư mục `${data-dir}/spool/`: `event_<timestamp>_<uuid>.json`.
2. Ghi file an toàn: Ghi vào file ẩn tạm `.event_*.tmp`, gọi `fd.sync()`, thực hiện **Atomic Move / Rename** và đồng bộ thư mục (`fsync` directory trên Linux) để đảm bảo không bị corrupt dữ liệu khi mất nguồn đột ngột.
3. Luồng nền (`EventSenderService` / `HeartbeatCoordinator`) sẽ drain các file theo thứ tự thời gian (FIFO), gửi lên Admin Server và xóa file sau khi Server phản hồi thành công.
4. **Cách ly lỗi vĩnh viễn (`quarantine/`):** Nếu file JSON bị lỗi cú pháp, mang `endpointId` của một endpoint thực khác, hoặc Server từ chối vĩnh viễn với mã HTTP **`400`**, **`409`**, **`413`** hoặc **`422`**, file được chuyển sang thư mục `spool/quarantine/` để không làm nghẽn hàng đợi. (Các lỗi tạm thời như `401`, `429`, `5xx`, mất mạng sẽ giữ nguyên đầu hàng đợi để thử lại sau).
5. **Giới hạn tài nguyên:** Hàng đợi outbox (tính cả `quarantine/`) được giới hạn dung lượng tối đa `64MB` (`outbox-max-bytes`) và `10,000` sự kiện (`outbox-max-events`). Khi chạm ngưỡng, Agent sẽ từ chối ghi telemetry mới nhưng **vẫn giữ nguyên chính sách chặn của USBGuard** (không bao giờ tự động mở USB).

---

### 3.3. Phân hệ Frontend (`frontend/`)

#### Cấu trúc thư mục mã nguồn:
```
frontend/src/
├── components/          # Reusable UI components (Common.tsx, ConfirmDialog.tsx, DataTable.tsx, Filters.tsx, Icon.tsx, Toast.tsx)
│   ├── endpoint/        # Components cho Endpoint (EndpointDetailModal, EndpointStatusBadge, EndpointWhitelistPanel, UsbGuardStatus)
│   └── event/           # Components hiển thị sự kiện (EventTable, EventFilters, events.css)
├── hooks/               # Custom React Hooks (AuthProvider, useAuth, useRoute, useToast, useResource)
├── layouts/             # Layout giao diện chính (AdminLayout.tsx)
├── mocks/               # Dữ liệu giả lập cho chế độ /demo (dashboard.ts, endpoints.ts, events.ts)
├── pages/               # Trang ứng dụng (Dashboard, Devices, Endpoints, Events, Login)
├── services/            # Layer giao tiếp API (api.ts, demo.ts, http.ts, models.ts, mappers/)
│   └── mappers/         # Chuyển đổi DTO từ Backend sang ViewModel (endpointMapper, eventMapper, policyMapper)
├── types/               # TypeScript interface và type definitions (endpoint.ts, event.ts, policy.ts)
└── utils/               # Hàm tiện ích format ngày giờ, bộ lọc event (eventFilters.ts, format.ts)
```

#### Kiến trúc Điều hướng (Custom Hash Router & Chế độ `/demo`):
Frontend không phụ thuộc vào thư viện bên ngoài như `react-router-dom` mà sử dụng hook nhẹ [useRoute.ts](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/frontend/src/hooks/useRoute.ts) kết hợp `useSyncExternalStore` để lắng nghe sự kiện `hashchange` (xem [App.tsx](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/frontend/src/App.tsx)):
- `#/login`: Trang đăng nhập Admin.
- `#/dashboard`: Trang tổng quan số liệu, biểu đồ sự kiện và cảnh báo.
- `#/devices`: Quản lý danh mục USB đã nhận diện trên toàn hệ thống (dùng `/api/admin/device-states`).
- `#/whitelist`: Quản lý danh sách Whitelist tập trung (sử dụng component [Devices.tsx](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/frontend/src/pages/Devices.tsx) ở chế độ `whitelist`).
- `#/events`: Lịch sử cắm/rút USB (phân trang phía server 10 dòng/trang qua component `LiveEvents`).
- `#/endpoints` & `#/endpoints/:id`: Quản lý máy trạm, trạng thái kết nối, sức khỏe USBGuard và mở modal chi tiết/whitelist từng máy (`EndpointDetailModal`).
- `/demo`: Đường dẫn riêng kích hoạt chế độ demo chỉ đọc với dữ liệu mock trong `frontend/src/mocks/` (không cần backend).

#### Quy tắc gọi API và xử lý CSRF:
- Tất cả request gọi qua [http.ts](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/frontend/src/services/http.ts) và [api.ts](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/frontend/src/services/api.ts).
- Sau khi xác thực hoặc khởi tạo phiên, `refreshCsrfToken()` gọi `GET /api/auth/csrf`, lưu `token` vào bộ nhớ JS (không đọc từ `document.cookie`) và tự động đính kèm header **`X-CSRF-TOKEN`** cho mọi request khác `GET`.
- Tuyệt đối không hardcode URL backend (`http://localhost:8080`) trong code frontend. Luôn dùng relative path `/api/...` để Vite proxy trong lúc dev và dùng chung origin trong lúc production.
- Trong `api.ts`, đối tượng `capabilities` khai báo `block: false` — Admin UI không hỗ trợ nút "Block" thủ công (mọi USB chưa cấp phép mặc định bị chặn fail-closed bởi USBGuard; Admin chỉ thao tác `Allow` hoặc `Revoke`).

#### Đóng gói Static Assets vào Spring Boot JAR (`with-frontend`):
Khi đóng gói để chạy độc lập hoặc phát hành:
```powershell
# 1. Cài đặt dependency và build production bundle (sinh ra thư mục frontend/dist)
npm --prefix frontend ci
npm --prefix frontend run build

# 2. Đóng gói JAR có nhúng static bundle từ frontend/dist vào classpath /static
mvn -f admin-server/pom.xml -Pwith-frontend -DskipTests clean package
```

#### Quy chuẩn xử lý trạng thái & UI:
- Không reload trang khi cập nhật dữ liệu. Sử dụng hook `useResource` để quản lý trạng thái tải dữ liệu, abort signal và làm mới mượt mà.
- Luôn sử dụng API phân trang (`/api/admin/endpoints/page`, `/api/admin/whitelist/page`, `/api/admin/device-states`, `/api/admin/events`) kèm các tham số `page` và `size`.

---

### 3.4. Phân hệ Packaging & Triển khai (`packaging/`)

Khi chỉnh sửa cấu hình triển khai Ubuntu, systemd, USBGuard hoặc Nginx, cần tuân thủ các hợp đồng nguồn (được kiểm tra tự động bởi `verify-windows.ps1`):
- **Debian Package (`packaging/agent-deb/`):**
  - [DEBIAN/control](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/packaging/agent-deb/DEBIAN/control): Yêu cầu phụ thuộc `openjdk-21-jre-headless`, `usbguard`, `libnotify-bin`, `acl`. Không tự ý thay đổi dòng `Version: 1.0.0` tĩnh trong source vì script build sẽ chèn timestamp version động (`1.0.0+build<UTC>`).
  - [DEBIAN/postinst](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/packaging/agent-deb/DEBIAN/postinst): Tạo system user/group `usbshield`, đặt quyền `chmod 750 /etc/usbshield-agent`, `chmod 700 /var/lib/usbshield-agent`, cấu hình USBGuard `ImplicitPolicyTarget=block` và `IPCAccessControlFiles` cho `usbshield` (tuyệt đối không thêm `usbshield` vào group `usbguard` hay dùng `chmod 1777`).
  - [DEBIAN/prerm](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/packaging/agent-deb/DEBIAN/prerm): Dừng và disable `usbshield-agent.service`, **không xóa** dữ liệu lịch sử `/var/lib/usbshield-agent` khi gỡ/nâng cấp.
- **Systemd & Desktop Autostart:**
  - [usbshield-agent.service](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/packaging/systemd/usbshield-agent.service) chạy ở profile `--spring.profiles.active=live`, đọc `EnvironmentFile=-/etc/usbshield-agent/agent.env` (file cấu hình mẫu: [agent.env.example](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/packaging/agent-deb/etc/usbshield-agent/agent.env.example)).
  - [usbshield-notifier.desktop](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/packaging/desktop/usbshield-notifier.desktop) & [usbshield-notifier-launcher.sh](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/packaging/desktop/usbshield-notifier-launcher.sh) khởi chạy `DesktopNotificationClientMain` bằng `PropertiesLauncher` trong phiên đăng nhập đồ họa của user.
- **Nginx Reverse Proxy & IP ACL:**
  - [usbshield-https-acl.conf.example](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/packaging/nginx/usbshield-https-acl.conf.example) tách rõ `location ^~ /api/agent/` (chỉ mở cho `CLIENT_CIDR` và `ADMIN_CIDR`) và `location /` (chỉ mở cho `ADMIN_CIDR`, chặn toàn bộ máy Client truy cập Web UI/Admin API từ tầng mạng) rồi proxy về `127.0.0.1:18080`.
- **Scripts đóng gói & kiểm tra trên Linux:**
  - Build `.deb` trên Windows: `pwsh -File ./packaging/scripts/build-deb.ps1`
  - Build `.deb` trên Ubuntu amd64: `./packaging/scripts/build-deb.sh`
  - Kiểm tra trên Ubuntu thật / HTTPS ACL: `packaging/tests/check-usbshield-core.sh`, `packaging/tests/test-notifier-ipc-ubuntu.sh`, `packaging/tests/verify-phase3-https-acl.sh`.

---

## 4. Quy chuẩn Viết Code & Best Practices (Coding Conventions)

### 4.1. Quy chuẩn đặt tên (Naming Conventions)

| Đối tượng | Quy tắc | Ví dụ |
|---|---|---|
| **Java Class / Interface** | PascalCase, danh từ hoặc tính từ | `EndpointPolicyService`, `UsbEventPayload` |
| **Java Method / Variable** | camelCase | `syncPolicy()`, `appliedPolicyVersion` |
| **Java Constant** | UPPER_SNAKE_CASE | `MAX_OUTPUT_BYTES`, `MARKER` |
| **Database Table** | snake_case, số nhiều | `admin_users`, `endpoint_whitelist_entries` |
| **Database Column** | snake_case | `endpoint_id`, `policy_version` |
| **REST API Path** | kebab-case hoặc noun plural | `/api/admin/endpoints/{id}/whitelist` |
| **TypeScript Type / Interface** | PascalCase | `Endpoint`, `Device`, `WhitelistEntry` |
| **React Component** | PascalCase | `EndpointDetailModal.tsx` |
| **React Hook** | camelCase, tiền tố `use` | `useAuth.ts`, `useResource.ts` |

### 4.2. DTO & Validation Layer

- **Không bao giờ trả Entity JPA trực tiếp ra ngoài Controller API.** Luôn map sang DTO (`record` hoặc DTO class) để tránh lộ trường nhạy cảm (`agentTokenHash`, `passwordHash`) hoặc gây lỗi vòng lặp tuần tự hóa JSON do lazy-loading.
- **Ràng buộc nghiệp vụ cấp phép:** Hệ thống USBShield **không cho phép cấp phép trước (pre-authorization)** bằng raw Vendor ID / Product ID / Device Hash tùy ý. Thiết bị USB bắt buộc phải được máy trạm cắm và báo cáo về hệ thống trước đó (`events.existsByEndpointIdAndUsbDeviceId`), từ đó Admin chọn cấp phép thông qua `usbDeviceId` đã lưu trong danh mục thiết bị.
- DTO tiếp nhận request cấp phép hiện tại trong [EndpointPolicyService.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/admin-server/src/main/java/com/group/usbshield/server/policy/EndpointPolicyService.java#L19-L20):
  ```java
  public record AllowRequest(Long usbDeviceId) {}
  ```
  *(Lưu ý: Nếu bổ sung Bean Validation annotation như `@NotNull` hoặc `@Size` vào `AllowRequest`, phải thêm `@Valid` vào tham số `@RequestBody` tại [AdminEndpointWhitelistController.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/admin-server/src/main/java/com/group/usbshield/server/policy/AdminEndpointWhitelistController.java#L9)).*
- DTO trả về thông tin bản ghi Whitelist (`EntryResponse`):
  ```java
  public record EntryResponse(
      Long id,
      String endpointId,
      Long usbDeviceId,
      String fingerprintType,
      String fingerprintValue,
      Boolean enabled,
      String name,
      String vendorId,
      String productId,
      String serial,
      Instant createdAt
  ) {}
  ```

### 4.3. Quản lý Exception & HTTP Status Codes

Hệ thống sử dụng [ApiExceptionHandler.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/admin-server/src/main/java/com/group/usbshield/server/common/ApiExceptionHandler.java) để chuyển đổi exception thành định dạng JSON chuẩn gồm 3 trường (`status`, `message`, `timestamp`):

```json
{
  "status": 400,
  "message": "Invalid request fields",
  "timestamp": "2026-11-20T11:45:00Z"
}
```

Bảng mã lỗi chuẩn cần trả về:
- `400 Bad Request`: Dữ liệu đầu vào không hợp lệ hoặc payload sai định dạng.
- `401 Unauthorized`: Thiếu hoặc sai thông tin xác thực (Agent Token hoặc Admin Session).
- `403 Forbidden`: Quyền không đủ, sai token CSRF (`X-CSRF-TOKEN`), hoặc Agent này cố tình gửi `endpointId` của Agent khác.
- `404 Not Found`: Không tìm thấy Endpoint, Device hoặc Whitelist entry.
- `409 Conflict`: Xung đột dữ liệu (Ví dụ: Đăng ký trùng `machine_id`, ACK policy version cũ hơn hoặc lớn hơn version hiện tại, hoặc cấp phép USB chưa từng xuất hiện trên Endpoint).

### 4.4. Quy chuẩn Logging & Bảo mật thông tin

> [!WARNING]
> **Tuyệt đối không log các thông tin sau:**
> 1. Mật khẩu dạng rõ (plaintext password) hoặc chuỗi token (`agent_token`, `enrollment_token`).
> 2. Khóa hash của token/password (`agent_token_hash`, `password_hash`).
> 3. Tên người dùng (`linuxUsername`), UID, session ID, serial USB và device hash không được ghi vào log ở mức `INFO` trong các luồng xử lý sự kiện thông thường (xem [AgentEventController.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/admin-server/src/main/java/com/group/usbshield/server/agentapi/AgentEventController.java#L122-L123) và [EventSenderService.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/client-agent/src/main/java/com/group/usbshield/agent/event/EventSenderService.java#L130-L133)).

**Đúng chuẩn:**
```java
// TỐT (Admin Server): Log ngắn gọn, chỉ gồm ID không nhạy cảm
log.info("Accepted USB event id={} endpointId={}", usbEvent.getId(), effectiveEndpointId);

// TỐT (Client Agent): Không log username, serial, device hash
log.info("[USB-EVENT] type={} decision={}", payload.getEventType(), payload.getDecision());

// VI PHẠM: Lộ token bí mật trong log
log.debug("Agent authentication header token: {}", rawToken); // CẤM!
```

---

## 5. Hướng dẫn Thực chiến: Cách Thêm Một Tính Năng Mới

Giả sử bạn cần thêm một tính năng: **"Bổ sung trường ghi chú lý do cấp phép (`allow_reason`) khi Admin thêm USB vào Whitelist"**. Dưới đây là các bước chuẩn theo vòng đời mã nguồn USBShield:

### Bước 1: Viết Flyway Migration (Backend Database)
Tạo file migration mới tại `admin-server/src/main/resources/db/migration/V3__add_whitelist_allow_reason.sql`:
```sql
ALTER TABLE endpoint_whitelist_entries
    ADD COLUMN IF NOT EXISTS allow_reason varchar(255);
```

### Bước 2: Cập nhật Entity, Controller, Service/DTO & JDBC Query (Admin Server)
1. Thêm trường `allowReason` vào [EndpointWhitelistEntry.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/admin-server/src/main/java/com/group/usbshield/server/policy/EndpointWhitelistEntry.java):
   ```java
   @Column(name = "allow_reason", length = 255)
   private String allowReason;
   ```
2. Cập nhật `AllowRequest` và `EntryResponse` trong [EndpointPolicyService.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/admin-server/src/main/java/com/group/usbshield/server/policy/EndpointPolicyService.java):
   ```java
   public record AllowRequest(
       @NotNull(message = "USB Device ID is required")
       Long usbDeviceId,

       @Size(max = 255, message = "Allow reason must not exceed 255 characters")
       String allowReason
   ) {}
   ```
   - Thêm `@Valid` vào tham số `@RequestBody EndpointPolicyService.AllowRequest request` trong [AdminEndpointWhitelistController.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/admin-server/src/main/java/com/group/usbshield/server/policy/AdminEndpointWhitelistController.java#L9).
   - Trong `EndpointPolicyService.allow(...)`, gán `entry.setAllowReason(...)` cả khi tạo mới lẫn khi kích hoạt lại entry cũ từng bị revoke (`enabled == false`), và cập nhật `view(...)` để trả `allowReason` trong `EntryResponse`.
3. Cập nhật truy vấn SQL thuần trong [AdminWhitelistPageController.java](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/admin-server/src/main/java/com/group/usbshield/server/policy/AdminWhitelistPageController.java) để `SELECT w.allow_reason` và map vào `EntryResponse` (vì endpoint `/api/admin/whitelist/page` dùng `NamedParameterJdbcTemplate` chứ không dùng JPA entity).

### Bước 3: Cập nhật Frontend API Adapter & UI
1. Mở [frontend/src/types/policy.ts](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/frontend/src/types/policy.ts), cập nhật interface `WhitelistEntry`:
   ```typescript
   export interface WhitelistEntry {
     id: string
     endpointId: string
     vendorId: string
     productId: string
     serial?: string
     name?: string
     hash?: string
     fingerprintType?: string
     fingerprintValue?: string
     allowReason?: string // <-- Bổ sung thuộc tính mới
     addedAt?: string
     addedBy?: string
   }
   ```
2. Mở [frontend/src/services/mappers/policyMapper.ts](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/frontend/src/services/mappers/policyMapper.ts), map trường `allowReason` từ backend DTO (`allowReason: readText(data.allowReason)`).
3. Mở [frontend/src/services/api.ts](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/frontend/src/services/api.ts#L22), cập nhật interface `AllowRequest` và hàm `allowOnEndpoint(...)` để truyền thêm `allowReason` trong JSON body gửi lên `/api/admin/endpoints/{endpointId}/whitelist`.
4. Thêm ô nhập liệu "Lý do cấp phép" trong luồng xác nhận Allow (ví dụ [Devices.tsx](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/frontend/src/pages/Devices.tsx) / [EndpointDetailModal.tsx](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/frontend/src/components/endpoint/EndpointDetailModal.tsx)) và hiển thị cột `Lý do` trên bảng danh sách Whitelist ([EndpointWhitelistPanel.tsx](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/frontend/src/components/endpoint/EndpointWhitelistPanel.tsx) và bảng Whitelist ở `Devices.tsx`).

### Bước 4: Kiểm thử tính năng (Testing)
1. Viết Integration/Unit Test cho `EndpointPolicyService` & `AdminWhitelistPageController`: kiểm tra lưu đúng `allowReason`, validation vượt quá 255 ký tự trả `400`, và tăng `policyVersion`.
2. Cập nhật mapper/component tests ở `frontend/tests/` và chạy `npm run lint` + `npm test`.
3. Chạy kiểm thử tổng hợp với PostgreSQL test (`verify-windows.ps1 -AdminOnly`).

---

## 6. Chiến lược Kiểm thử (Testing & QA)

Dự án áp dụng mô hình kiểm thử phân lớp chặt chẽ:

### 6.1. Backend Tests (`admin-server/`)
- **Unit Tests:** Kiểm tra logic nghiệp vụ thuần túy bằng Mockito, không cần bật database.
- **Integration Tests:** Bật Spring Context với PostgreSQL thật.
  - Sử dụng schema cách ly: Mỗi class test dùng một schema ngẫu nhiên `usbshield_it_*` trong database `usbshield_test` do Flyway tự tạo và tự xóa sau khi chạy xong.
  - **Bắt buộc dùng `with-env.ps1`** để nạp cấu hình `USBSHIELD_TEST_DB_*` an toàn:
    ```powershell
    .\scripts\with-env.ps1 -RequireTestDb -Command { mvn -f admin-server/pom.xml clean test }
    ```

### 6.2. Frontend Tests (`frontend/`)
Frontend sử dụng bộ test runner gốc của Node.js (`--experimental-strip-types`) kết hợp Oxlint:
```powershell
# 1. Kiểm tra cú pháp và quy chuẩn TypeScript/React
npm --prefix frontend run lint

# 2. Chạy bộ unit & contract tests (14 test suites trong package.json)
npm --prefix frontend test

# 3. Chạy kiểm thử UI trên trình duyệt với dữ liệu fixture (không cần PostgreSQL)
npm --prefix frontend run test:browser

# 4. Chạy kiểm thử tích hợp Live Browser/HTTP với Spring Boot JAR & PostgreSQL usbshield_browser_test
# (Yêu cầu đã package Admin JAR có frontend trước đó, và bắt buộc nạp .env qua with-env.ps1)
.\scripts\with-env.ps1 -RequireTestDb -Command { npm --prefix frontend run test:browser:live }
.\scripts\with-env.ps1 -RequireTestDb -Command { npm --prefix frontend run test:live }
```

### 6.3. Client Agent Tests (`client-agent/`)
- Kiểm tra tính đúng đắn của logic phân tích USBGuard rule (`UsbGuardEventParserTest`, `UsbGuardPolicySafetyValidationTest`, ...).
- Kiểm tra cơ chế hàng đợi Outbox spooling (`OutboxSpoolServiceTest` và các bài `Outbox*RegressionTest`).
- Chạy test Agent trên Windows:
  ```powershell
  mvn -f client-agent/pom.xml clean test
  ```
  *(Lưu ý: 3 test phụ thuộc vào môi trường native Linux thật trong `NotifierStaleSocketRecoveryTest` có `@EnabledOnOs(OS.LINUX)` nên sẽ tự động SKIP khi chạy trên Windows — điều này là bình thường và không thay thế kiểm thử Linux).*

### 6.4. Bộ kiểm thử tự động toàn diện trên Windows (`verify-windows.ps1`)

Trước khi tạo Pull Request hoặc phát hành bản release, hãy chạy script kiểm thử tự động toàn diện của dự án:
```powershell
# 1. Chạy nhanh cho dev Backend & Frontend (Chỉ kiểm tra Admin Server + React + Live suites, bỏ qua Agent & build .deb):
pwsh -File ./packaging/tests/verify-windows.ps1 -AdminOnly

# 2. Chạy toàn bộ gates (Admin Server, Client Agent, Frontend và kiểm tra đóng gói .deb):
pwsh -File ./packaging/tests/verify-windows.ps1
```
Script này sẽ tự động:
1. **Kiểm tra Whitespace & Source contracts:** Kiểm tra `git diff --check` và `git diff --cached --check`, kiểm tra file nhạy cảm (`.env`, `.env.e2e.local`) không bị commit, kiểm tra các ràng buộc bảo mật nguồn (`ddl-auto: validate`, `baseline-on-migrate: false`, user `usbshield`, socket ACL `u:usbshield:rw-`, `SO_PEERCRED`, v.v.).
2. Chạy toàn bộ test của `admin-server` và `client-agent`.
3. Chạy `npm ci`, `npm test`, `npm run lint` và `npm run build` của `frontend`.
4. Đóng gói static web vào JAR Admin Server và chạy `test:browser:live` + `test:live` trên schema tạm `usbshield_e2e_*` của database `usbshield_browser_test`.
5. Build và kiểm tra cấu trúc/hash/manifest gói `.deb` của Client Agent (nếu không bật `-AdminOnly` hoặc `-SkipDebBuild`).

---

## 7. Quy trình Làm việc với Git & Release

### 7.1. Quy ước Nhánh (Branching Strategy)
- `main` / `master`: Nhánh production, luôn ở trạng thái build pass và có thể deploy.
- `feature/<ten-tinh-nang>`: Nhánh phát triển tính năng mới.
- `fix/<ma-loi>`: Nhánh sửa lỗi.

### 7.2. Quy ước Commit Message
Tuân thủ Conventional Commits:
- `feat: add reason field to whitelist entry`
- `fix: resolve stale session resolution in ActiveUserResolver`
- `docs: update developer coding guide`
- `test: add integration test for agent policy ack`

### 7.3. Build Artifact Phát hành (Release Workflow)
- **Admin Server JAR (`admin-server/build-release.ps1`):**
  ```powershell
  # Yêu cầu clean Git working tree; sinh JAR + .manifest.json + .sha256 tại admin-server/target/release/
  .\admin-server\build-release.ps1
  # Hoặc chỉ định thư mục xuất riêng:
  .\admin-server\build-release.ps1 -OutputDirectory 'C:\USBShieldReleases'
  ```
  *(Lưu ý: Flag `-AllowDirtyTree` hoặc `-SkipTests` chỉ dùng cho bản build thử nghiệm nội bộ, không đạt chuẩn release chính thức).*
- **Client Agent DEB:**
  - Trên Windows: `pwsh -File ./packaging/scripts/build-deb.ps1`
  - Trên Ubuntu amd64: `./packaging/scripts/build-deb.sh`
  - Output sinh ra tại `packaging/dist/usbshield-agent_<version>_amd64.deb` kèm `usbshield-agent.release.json`.

### 7.4. Checklist trước khi tạo Pull Request (Pre-PR Checklist)
- [ ] Đã chạy `git diff --check` và `git diff --cached --check` đảm bảo không có lỗi whitespace hay conflict CRLF/LF thừa.
- [ ] Mã nguồn đã được format sạch sẽ, không có import thừa.
- [ ] Không có file nhạy cảm (`.env`, `.env.e2e.local`, mật khẩu, certificate private key) hoặc file tạm (như `~$*.docx`) bị thêm vào Git (`git status`).
- [ ] `.\scripts\with-env.ps1 -RequireTestDb -Command { mvn -f admin-server/pom.xml clean test }` và `mvn -f client-agent/pom.xml clean test` đều PASS.
- [ ] `npm --prefix frontend run lint` và `npm --prefix frontend test` đều PASS.
- [ ] Chạy `pwsh -File ./packaging/tests/verify-windows.ps1 -AdminOnly` (hoặc bản đầy đủ) PASS toàn bộ.
- [ ] Đã thêm Flyway Migration script nếu có thay đổi schema trong cơ sở dữ liệu.

---

## 8. Xử lý Sự cố Thường gặp khi Lập trình (Troubleshooting)

### 1. Lỗi Lombok không compile được (`Fatal error compiling ... TypeTag :: UNKNOWN`)
- **Nguyên nhân:** Đang sử dụng JDK 24+. Phiên bản Lombok 1.18.36 trong dự án chưa tương thích với JDK 24.
- **Khắc phục:** Trỏ biến môi trường `JAVA_HOME` về JDK 21 hoặc JDK 22 trước khi build:
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  ```

### 2. Lỗi Flyway: `Found non-empty schema(s) "public" but no schema history table`
- **Nguyên nhân:** Database PostgreSQL đã có sẵn các bảng từ trước nhưng chưa được quản lý bởi Flyway.
- **Khắc phục:** Đối với database dev mới, hãy drop database và tạo lại sạch sẽ. Đối với database đã có dữ liệu quan trọng, xem tài liệu [POSTGRES_FLYWAY_BASELINE.md](file:///d:/CNTT-SGU/HK7/Seminar/USBProtection/admin-server/ops/POSTGRES_FLYWAY_BASELINE.md) để chạy baseline.

### 3. Lỗi 403 Forbidden khi gọi API Admin từ Frontend
- **Nguyên nhân:** Thiếu cookie session (`JSESSIONID`) hoặc thiếu/sai header `X-CSRF-TOKEN`.
- **Khắc phục:** Đảm bảo bạn đã lấy CSRF token từ `GET /api/auth/csrf` và gửi kèm trong header `X-CSRF-TOKEN` khi gọi `POST /api/auth/login` cũng như các request `POST`, `PUT`, `DELETE` sau đăng nhập (giữ nguyên session cookie `JSESSIONID` giữa các request).

### 4. Agent gặp lỗi 409 Conflict khi Enrollment
- **Nguyên nhân:** `machine_id` của máy trạm này đã từng đăng ký trên server trước đó nhưng file credential cục bộ (`endpoint.json`) bị mất hoặc khác token đã băm trên server. Hệ thống không cho phép tự động ghi đè token để tránh giả mạo endpoint.
- **Khắc phục:** Trong môi trường dev, xóa dòng endpoint tương ứng trong bảng `endpoints` của database đồng thời xóa file `${data-dir}/endpoint.json` (và file staged enrollment nếu có trong `client-agent/data/`), hoặc cấu hình `machine_id` giả lập mới.

### 5. Lỗi Backend test: `IllegalStateException: Integration tests require valid USBSHIELD_TEST_DB_HOST/PORT...`
- **Nguyên nhân:** Chạy `mvn test` hoặc `test:browser:live` trực tiếp mà chưa nạp biến môi trường từ file `.env`.
- **Khắc phục:** Luôn chạy qua script hỗ trợ:
  ```powershell
  .\scripts\with-env.ps1 -RequireTestDb -Command { mvn -f admin-server/pom.xml test }
  ```

### 6. Lỗi màn hình trắng / 404 static assets khi khởi chạy file JAR Admin Server
- **Nguyên nhân:** Đóng gói JAR bằng lệnh `mvn package` thông thường mà không bật profile `with-frontend`, hoặc chạy `mvn clean` sau khi đóng gói khiến thư mục `frontend/dist` chưa được copy vào JAR.
- **Khắc phục:** Thực hiện đúng quy trình 2 bước (hoặc chạy sau `mvn clean test`):
  ```powershell
  npm --prefix frontend ci
  npm --prefix frontend run build
  mvn -f admin-server/pom.xml -Pwith-frontend -DskipTests clean package
  ```
  Hoặc sử dụng trực tiếp script build chính thức: `.\admin-server\build-release.ps1 -AllowDirtyTree`.

### 7. Script `verify-windows.ps1` báo lỗi ngay ở Phase 1: `FAIL: Unstaged whitespace check`
- **Nguyên nhân:** Có file mã nguồn chứa khoảng trắng thừa cuối dòng (trailing whitespace) hoặc sai định dạng xuống dòng CRLF/LF.
- **Khắc phục:** Chạy lệnh `git diff --check` trong terminal để xác định chính xác vị trí dòng bị lỗi, xóa khoảng trắng thừa rồi chạy lại verifier.
