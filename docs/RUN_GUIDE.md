# USBShield — Hướng dẫn chạy và triển khai chung (Admin Server + Ubuntu Client)

> **Phạm vi:** Hướng dẫn triển khai và phân quyền truy cập; **không phải bằng chứng đã chạy Ubuntu E2E**. Source baseline: `vinhkhang` `035b8ed5a4f5b111dace06cf839cf5f1c33f1142` (04/11/2026, chưa merge `main`). Đây là **hướng dẫn chạy duy nhất đang có hiệu lực** cho cả Admin Server và Ubuntu Agent `.deb`. Ubuntu E2E vẫn **NOT EXECUTED/DEFERRED**.

## 1. Chọn mô hình tối giản

- **Một máy Admin Server** (Windows hoặc Ubuntu, JDK 21) chạy **một executable JAR Spring Boot**, PostgreSQL chạy như dịch vụ riêng (cùng máy hoặc máy database được bảo vệ). React được đóng gói vào JAR; không cần Vite/NPM ở runtime.
- **Admin** dùng **trình duyệt + tài khoản Admin** để truy cập React Web và các API quản trị `/api/auth/**`, `/api/admin/**`; **normal Ubuntu users không được cấp tài khoản Admin**.
- **Các Ubuntu Client** chỉ chạy Agent `.deb` ở chế độ non-web và gửi request **tới `/api/agent/**`**, không cài/khởi chạy Admin Web. `server-url` phải trỏ đến **DNS/IP của Admin Server trên LAN**, không dùng `127.0.0.1`/`localhost` ở máy khác.
- **Phân biệt authentication và network access:** Spring Security bảo vệ dữ liệu/hành động Admin, nhưng nếu cùng một origin được mở cho cả LAN, máy Client vẫn có thể tải *trang đăng nhập/tài nguyên tĩnh*. Muốn **Client không mở được Web ngay ở tầng mạng**, phải cấu hình reverse proxy/firewall giới hạn route theo IP/VLAN (mục 4); không thể chỉ dựa vào việc máy Client có `.deb`.
- Với truy cập từ máy khác, dùng một **HTTPS reverse proxy** trên máy Server; Spring Boot giữ bind `127.0.0.1:18080` ở phía sau proxy (trùng `PORT` tại mục 3). Chỉ mở cổng TLS cần thiết cho mạng nội bộ.
- **Không yêu cầu Docker.** Docker Compose chỉ thuận tiện khi muốn quản lý lifecycle/volume/log tập trung; với một Admin Server và PostgreSQL cho bài demo, JAR đơn giản hơn.

## 2. Chuẩn bị PostgreSQL (một lần; database trống theo quyết định owner)

PostgreSQL Server cần được **cài và đang chạy**, không được nhúng vào JAR hay `.deb`. **Chỉ chạy SQL tạo role/database trên bản cài đặt mới**; nếu các role/database đã được tạo trong IntelliJ, không chạy lại. Có thể dùng PostgreSQL Query Console của IntelliJ IDEA (kết nối `postgres` bằng tài khoản quản trị) hoặc `psql`. Chạy từng `CREATE DATABASE` **ngoài transaction**. Thay các password placeholder và giữ secrets ngoài Git.

```sql
CREATE ROLE usbshield LOGIN PASSWORD '<APP_DB_PASSWORD>';
CREATE DATABASE usbshield OWNER usbshield;

CREATE ROLE usbshield_test LOGIN PASSWORD '<TEST_DB_PASSWORD>';
CREATE DATABASE usbshield_test OWNER usbshield_test;
CREATE DATABASE usbshield_browser_test OWNER usbshield_test;
```

- `usbshield`: dữ liệu **ứng dụng**; chạy bằng role `usbshield`. Không chạy `ddl-auto=create`, `create-drop` hay browser harness vào database này.
- `usbshield_test`: Maven Admin integration tests; **disposable**; mỗi test class dùng schema ngẫu nhiên `usbshield_it_*` và dọn khi kết thúc. Không cho test role quyền DDL trên `usbshield`.
- `usbshield_browser_test`: browser-live và các script HTTP QA; **disposable**. Browser harness tạo schema `usbshield_e2e_<id>` riêng cho mỗi lượt. Role `usbshield_test` phải có quyền `CREATE` trên database test này (để tạo schema riêng); tạo database `OWNER usbshield_test` là cách mặc định. Các script HTTP QA cũ có thể thao tác bảng trong schema `public` của **test database**, vì vậy chỉ dùng database test trống/disposable. Schema test được tự cleanup khi chạy bình thường; nếu tiến trình bị kill có thể còn schema orphan. Chỉ cleanup có kiểm tra đúng database/schema, owner và test không còn chạy.
- Tránh dùng role `postgres`/superuser cho ứng dụng hay test. Nếu lỗi `permission denied for schema public`, xác nhận **đúng database và role** trước khi cấp quyền. Nếu lỗi `must be owner of table`, có bảng cũ thuộc role khác; không cấp quyền vào database ứng dụng để chữa lỗi test. Không sao chép cấu hình test vào production.
- Không migrate dữ liệu H2 vì owner đã quyết định bỏ dữ liệu demo cũ. PostgreSQL được tạo mới, không có dữ liệu cũ.

### Flyway cho PostgreSQL: bắt buộc phân biệt database mới/cũ (F07)

Runtime hiện dùng **Flyway V1/V2** và Hibernate `ddl-auto=validate` (`clean-disabled=true`, `baseline-on-migrate=false`), **không còn `update`**. Nếu `usbshield` mới và trống: chạy bình thường để Flyway tự migrate; **không baseline**. Nếu `usbshield` hiện hữu đã có năm bảng do Hibernate `update` trước đây tạo ra: **dừng server, backup có thể restore, chạy preflight và baseline V1 đúng một lần** theo [POSTGRES_FLYWAY_BASELINE.md](../admin-server/ops/POSTGRES_FLYWAY_BASELINE.md), rồi để Flyway chạy V2. Không baseline schema test hoặc tự bật `baseline-on-migrate=true` thường trực.

## 3. Build React + Spring Boot và chạy Admin Server

Yêu cầu: JDK 21, Maven, Node/npm theo [Developer Guide](DEVELOPER_GUIDE.md), PostgreSQL đang chạy. Chạy PowerShell tại root repository. IntelliJ Database không tự chuyển mật khẩu cho Maven.

**Khởi tạo cấu hình test một lần:** `Copy-Item .env.example .env`, điền `USBSHIELD_TEST_DB_USERNAME=usbshield_test` và `USBSHIELD_TEST_DB_PASSWORD` thật vào file `.env` **không được commit**. `scripts/with-env.ps1` đọc `.env` như dữ liệu, không thực thi shell expressions. Các lệnh Maven/npm chạy trực tiếp không tự load `.env`.

```powershell
npm --prefix frontend ci
.\scripts\with-env.ps1 -RequireTestDb -Command { mvn -f admin-server/pom.xml clean test }
mvn -f client-agent/pom.xml clean test
npm --prefix frontend test
npm --prefix frontend run lint
npm --prefix frontend run build
mvn -f admin-server/pom.xml -Pwith-frontend -DskipTests package
.\scripts\with-env.ps1 -RequireTestDb -Command { npm --prefix frontend run test:browser:live }
.\scripts\with-env.ps1 -RequireTestDb -Command { npm --prefix frontend run test:live }
git diff --check
```

Admin test Java dùng `usbshield_test`/schema riêng; browser-live dùng `usbshield_browser_test`/schema riêng. Agent notifier **3 Linux-only tests sẽ skip trên Windows**; cần chạy trên Ubuntu để nghiệm thu F09. Các bộ browser gửi HTTP events/ACK tổng hợp, không xác nhận USBGuard thật.

`admin-server/target/usbshield-admin-server.jar` vẫn là **executable JAR** có React static bundle và API cùng origin. `npm run test:live` là bộ browser/Spring khác có thể chạy riêng; `test:browser:live` tự kích hoạt build frontend (`pretest:browser:live`) và dùng `usbshield_browser_test`/schema riêng với event HTTP tổng hợp. Không đánh đồng các test này với thiết bị Ubuntu thật. Không dùng database `usbshield` cho QA.

**Chạy profile `prod`** từ thư mục root repository trong PowerShell, sau khi đã cấp các secrets cần thiết:

```powershell
$env:USBSHIELD_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/usbshield'
$env:USBSHIELD_DB_USERNAME = 'usbshield'
$env:USBSHIELD_DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Password cua usbshield' -AsSecureString)).Password
$env:USBSHIELD_ADMIN_USERNAME = 'admin'
$env:USBSHIELD_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Initial Admin password' -AsSecureString)).Password
$bytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$env:USBSHIELD_ENROLLMENT_TOKEN = [Convert]::ToHexString($bytes).ToLowerInvariant()
# Chỉ tạo token lần đầu; lưu trong secret store và tái sử dụng ổn định sau restart.
$env:USBSHIELD_BIND_ADDRESS = '127.0.0.1'
$env:PORT = '18080'

java -jar admin-server/target/usbshield-admin-server.jar --spring.profiles.active=prod
```

Profile mặc định `application.yml` dùng URL `jdbc:postgresql://${USBSHIELD_DB_HOST:127.0.0.1}:${USBSHIELD_DB_PORT:5432}/${USBSHIELD_DB_NAME:usbshield}`, username `usbshield` và password từ `USBSHIELD_DB_PASSWORD`. **Profile `prod` yêu cầu `USBSHIELD_DB_URL` và `USBSHIELD_DB_PASSWORD`**; không mặc định dùng `USBSHIELD_DB_HOST/NAME` để thay thế URL này. Flyway tạo/nâng cấp schema V1/V2 trước khi Hibernate chạy `ddl-auto=validate`. **Không** đặt `ddl-auto=update/create-drop` cho ứng dụng. Database hiện hữu chưa versioned cần baseline một lần theo mục 2; không tự baseline bằng cấu hình toàn cục.

Admin Server muốn khởi động cần PostgreSQL sẵn sàng và role ứng dụng có quyền tạo/cập nhật schema ban đầu. Password bootstrap Admin **chỉ tạo tài khoản nếu chưa có**, không đổi mật khẩu của account đã tồn tại. `USBSHIELD_ENROLLMENT_TOKEN` phải được quản lý như bí mật và chuyển an toàn đến IT cấu hình Client; **không đưa vào ZIP/commit/screenshot**.

`prod` bật Secure session cookie nên browser Admin phải truy cập qua **HTTPS reverse proxy** ở LAN. Chỉ dùng HTTP loopback để kiểm thử cô lập; không coi HTTP qua LAN với token thật là bảo mật. PostgreSQL port `5432` chỉ dành cho Admin Server ↔ database, **không mở cho Ubuntu Client**, và phải có backup độc lập.

### Tồn tại dữ liệu, backup và khôi phục

PostgreSQL lưu dữ liệu vào data directory của PostgreSQL service, **không nằm trong JAR hay file H2 `.mv.db`**. Restart JAR không làm mất dữ liệu khi vẫn dùng cùng PostgreSQL database. Dừng JAR không đồng nghĩa dừng PostgreSQL. Dùng cơ chế backup PostgreSQL như `pg_dump`/`pg_restore` (cộng với bảo vệ secrets và kiểm thử restore); **không sao chép file dữ liệu đang mở** và không xóa database `usbshield` khi dọn test. Đảm bảo backup và role DB được giới hạn quyền truy cập.

## 4. Phân quyền Admin Web và Agent API trên LAN

**Tách hai luồng truy cập**, dù cùng chạy trong một Spring Boot JAR:

```text
Máy Admin (browser, IP/VLAN quản trị) ---> HTTPS proxy ---> /, /api/auth/**, /api/admin/**
Máy Ubuntu (Agent .deb, mạng Client) --> HTTPS proxy ---> /api/agent/**
                                                       |
                                                       v
                                                127.0.0.1:18080
                                                Spring Boot + PostgreSQL
```

- **Admin Web không cài trên Client**. Chỉ quản trị viên có tài khoản và phiên đăng nhập hợp lệ mới xem/đổi chính sách. Agent dùng `X-Endpoint-Id`/`X-Agent-Token` và enrollment token theo API contract, **không có tài khoản web**.
- **Điều kiện chặn hẳn trang Web từ máy Client:** bảo vệ đường dẫn `/`, static assets, `/api/auth/**`, `/api/admin/**` bằng IP/VLAN allowlist ở proxy; **chỉ mở `/api/agent/**`** cho địa chỉ/subnet Agent được phép. Khi chưa có proxy restriction, Client vẫn có thể nhìn thấy trang đăng nhập, nhưng không được truy cập API quản trị nếu không xác thực.
- Với demo local trên **chính máy Server**, có thể kiểm thử HTTP loopback trước; chưa cần proxy để chạy unit/browser harness. Trên mạng LAN hoặc vận hành thực tế, **HTTPS** bảo vệ cả Admin session lẫn Agent credentials. `USBSHIELD_COOKIE_SECURE=true` trong `prod` yêu cầu truy cập Web qua HTTPS.

**Ví dụ Nginx khái niệm bên dưới chỉ minh họa. Dùng cấu hình file đã triển khai và thay đủ placeholder tại** [`packaging/nginx/usbshield-https-acl.conf.example`](../packaging/nginx/usbshield-https-acl.conf.example), cùng hướng dẫn [HTTPS/ACL](PHASE3_HTTPS_ACL_AND_PAGING.md). Không sử dụng cấu hình giả định IP này trực tiếp trên LAN:

```nginx
server {
    listen 443 ssl;
    server_name usbshield.lab;
    ssl_certificate     /etc/nginx/certs/usbshield.crt;
    ssl_certificate_key /etc/nginx/certs/usbshield.key;

    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto https;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

    # Client subnet: ONLY Agent HTTP API, not static Web or Admin API.
    location ^~ /api/agent/ {
        allow 192.168.10.0/24;
        deny all;
        proxy_pass http://127.0.0.1:18080;
    }

    # Admin browser + auth/admin APIs: ONLY authorized Admin workstation.
    location / {
        allow 127.0.0.1;
        allow 192.168.10.5;
        deny all;
        proxy_pass http://127.0.0.1:18080;
    }
}
```

Đây là **mẫu cho một máy Admin** (`192.168.10.5`) và mạng Client (`192.168.10.0/24`), không phải cấu hình đã được kiểm thử. Đảm bảo proxy là **đường truy cập LAN duy nhất**: server Java vẫn bind loopback, firewall không công khai port 18080; tránh bỏ qua proxy. Nếu có nhiều máy Admin, thay bằng mạng quản trị/VPN hoặc allowlist phù hợp. IP allowlist **bổ sung**, không thay thế Admin authentication/Agent token. Không đặt Admin credentials trong file `.deb`.

Ubuntu Agent `live` **không chấp nhận URL HTTP hoặc thiếu URL**, và không tự follow HTTP redirect mang Agent token. `/etc/usbshield-agent/application.yml` dùng biến từ file `/etc/usbshield-agent/agent.env` **root-owned 0600** qua systemd `EnvironmentFile=-...`; cấu hình file này **trước khi cài/upgrade `.deb`**, vì `postinst` có thể restart Agent ngay. Không commit credential vào repository:

```dotenv
USBSHIELD_SERVER_URL=https://usbshield.example.internal
USBSHIELD_ENROLLMENT_TOKEN=<deployment-secret>
# Chỉ cần với private CA, là absolute path tới CA public certificate (PEM):
USBSHIELD_TLS_CA_CERTIFICATE=/etc/usbshield-agent/usbshield-ca.pem
```

Chứng chỉ phải có DNS/IP trong SAN trùng URL, và được **trình duyệt Admin/JVM trên Agent tin cậy**. CA private key không được phân phối; không tắt certificate/hostname verification. Đọc [Phase 3 HTTPS/ACL](PHASE3_HTTPS_ACL_AND_PAGING.md) để kiểm thử từ hai máy riêng biệt.

## 5. Cài `.deb` khác với tải một file bất kỳ từ GitHub

**Có thể build `.deb` một lần trên máy Server và phân phối đến nhiều Ubuntu clients**, miễn môi trường build chạy Linux/Ubuntu (hoặc Windows Server có Ubuntu VM/WSL2 với toolchain Linux), gói dành cho cùng kiến trúc `amd64`, và đã xác minh build. **Admin Server JAR và Client Agent `.deb` là hai artifact khác nhau**; máy Server không cần cài Agent/USBGuard để phục vụ API.

Repository chứa **source và script build**, không được giả định URL GitHub luôn cung cấp `.deb` đã build. Trên **Ubuntu builder** có JDK 21, Maven, `dpkg-deb` và dependency build:

```bash
# Chạy tại root repository, trước khi phân phối
mvn -f client-agent/pom.xml clean test
bash packaging/scripts/build-deb.sh
sha256sum packaging/dist/usbshield-agent_1.0.0_amd64.deb
dpkg-deb -I packaging/dist/usbshield-agent_1.0.0_amd64.deb
```

Phân phối artifact đã kiểm chứng qua **SCP/SFTP, file share nội bộ hoặc HTTPS có kiểm soát**. Có thể dùng một bản `.deb` cho tất cả máy Ubuntu 24.04 amd64 tương thích; **không sao chép `endpoint.json`, Agent token hay thư mục spool** từ máy này sang máy khác. Mỗi Client phải tự enroll để có endpoint identity/credential riêng.

Trên **từng Ubuntu Client** (do IT/admin cài, không phải user thường):

```bash
# Sao chép .deb đã build trước khi chạy các bước sau.
# Tạo root-managed config dir trước postinst (không phụ thuộc group usbshield).
sudo install -d -o root -g root -m 0750 /etc/usbshield-agent
sudoedit /etc/usbshield-agent/agent.env
# Điền URL HTTPS/cert CA và enrollment token thực; không sao chép secret vào Git.
sudo chown root:root /etc/usbshield-agent/agent.env
sudo chmod 0600 /etc/usbshield-agent/agent.env
sudo apt update
sudo apt install ./usbshield-agent_1.0.0_amd64.deb
sudo systemctl restart usbshield-agent
systemctl status usbguard usbshield-agent --no-pager
```

`apt install ./...deb` cài dependency và service, **không tự thiết lập Server URL/token đúng cho từng môi trường**. Đảm bảo DNS, CA trust và `agent.env` đã chuẩn bị **trước** vì `postinst` khởi động service; gói fail-closed nếu chưa cấu hình hợp lệ. Nếu đang triển khai Client bằng file `.deb` mới, phải có quyền sudo/root lúc cài và cấu hình; sau đó normal user không được sửa service. **Ubuntu install/E2E vẫn chưa được thực hiện**, nên đây là quy trình đề xuất, không phải kết quả kiểm thử.

## 6. Khi nào mới cần Docker?

Có thể bổ sung Docker Compose trong một đợt riêng nếu mục tiêu là server restart, PostgreSQL data volume, log và TLS proxy reproducible. Với demo 1 Admin Server + vài Client, Java JAR + systemd/Windows service đã đủ. Docker **không tự làm server có thể truy cập từ mạng khác, không thay thế TLS, và không bảo vệ USB** (USBGuard nằm ở từng Ubuntu Client).

## 7. Trạng thái nghiệm thu và giới hạn

- Windows Maven/frontend/API mock tests có thể chứng minh contract/code, **không chứng minh USB vật lý**.
- Ubuntu E2E (USB flash, chuột/phím, `.deb`, reboot, policy persistence, GUI popup, service/ACL, Allow/Revoke theo endpoint) **DEFERRED/NOT EXECUTED**.
- `usbguard allow-device <runtime-id>` và `usbguard block-device <runtime-id>` là CLI của USBGuard cho thiết bị đang hiện diện, **nhưng bản hiện tại mới đồng bộ whitelist/rules; chưa kết luận đã điều khiển ngay USB đang cắm**. Triển khai chức năng này cần cơ chế kiểm tra lại runtime ID/hash, quyền IPC (`Devices=modify`), chống race, báo lỗi/ACK và kiểm chứng Ubuntu trước khi bổ sung vào release.

Các tham chiếu: [Developer Guide](DEVELOPER_GUIDE.md), [Admin API](API.md), [Ubuntu Acceptance](qa/WEEK4_UBUNTU_ACCEPTANCE.md).

## 8. Lưu ý vận hành outbox sau tối ưu

Accounting cache giả định **một tiến trình Agent là writer duy nhất**, các thư mục spool và quarantine được bảo vệ khỏi normal user. Khi phát hiện thay đổi metadata thư mục hoặc ghi lỗi, Agent quét lại quota (bao gồm quarantine). **Không sửa nội dung của một file spool đang tồn tại khi Agent còn chạy**: thao tác sửa *in-place* không nhất thiết làm thay đổi mtime của thư mục và có thể làm cache quota tạm thời sai. Nếu operator cần can thiệp thủ công, dừng Agent có kiểm soát, sao lưu bằng chứng, sửa và khởi động lại để ép full scan. Không tự xóa audit/quarantine để né quota.

## 9. Kiểm tra Ubuntu Client sau cài và xử lý Allow/Revoke

- **Admin Web** chỉ sử dụng trên máy Admin, đăng nhập bằng Admin account. Ubuntu Client không cài Web; Agent sử dụng `/api/agent/**`.
- Trước khi cài/nâng cấp `.deb`, tạo `/etc/usbshield-agent/agent.env` mode `0600` chứa `USBSHIELD_SERVER_URL` HTTPS, bootstrap token và CA PEM tùy chọn; `application.yml` đọc biến môi trường; dùng tên DNS khớp SAN, **không dùng localhost** từ Client. File này do root quản lý, không đưa token lên Git.

```bash
sudo systemctl is-enabled usbguard usbshield-agent
sudo systemctl status usbguard usbshield-agent --no-pager
sudo journalctl -u usbshield-agent -u usbguard --since '10 minutes ago' --no-pager
sudo -u usbshield usbguard get-parameter ImplicitPolicyTarget
sudo -u usbshield usbguard list-devices
```

- Admin kiểm tra Client đã enroll/heartbeat, policy version và ACK. Đừng đọc/in `endpoint.json` vì chứa Agent credential.
- **Luồng đã chốt theo blueprint:** khi Admin Allow/Revoke, Agent đồng bộ local rules, ACK; người dùng **rút USB và cắm lại** để thấy thay đổi ALLOW/BLOCK. Không yêu cầu runtime `allow-device`/`block-device` trên USB đang kết nối.
- **Service không tắt được** được hiểu là normal Ubuntu user không có root/sudo hoặc quyền USBGuard IPC sửa policy không được tắt/sửa. Quản trị viên root vẫn có thể quản trị hệ thống.
- Một `.deb` amd64 đã build/kiểm tra có thể phân phối cho nhiều máy Ubuntu cùng nền tảng; **không copy `endpoint.json`** từ Client này sang Client khác. USB E2E/hardware/systemd/popup/reboot chưa được xác nhận bằng test Windows.

## 10. Thứ tự kiểm chứng cho seminar

1. Maven Admin + Agent và frontend tests/lint/build trên Windows.
2. Đóng gói JAR Admin cùng React, kiểm tra loopback/browser test trên host.
3. Trên mạng lab cô lập: cài `.deb` Ubuntu, test thiết bị thật, policy A/B, Allow/Revoke sau khi rút/cắm lại, event active-user, popup, server-down, reboot và quyền normal user.
4. **Trước khi chạy Agent `live` trên LAN**, triển khai HTTPS reverse proxy, CA/hostname verification và Admin/Client IP ACL. Chạy [`verify-phase3-https-acl.sh`](../packaging/tests/verify-phase3-https-acl.sh) từ máy Admin và Client thực; test chứng chỉ sai phải thất bại, không fallback HTTP. HTTP loopback chỉ áp dụng cho harness/dev tách khỏi live deployment.

Các trường hợp Ubuntu trong `docs/qa/WEEK4_UBUNTU_ACCEPTANCE.md` hiện **DEFERRED**; không chấm FULL PASS nếu chưa chạy.
