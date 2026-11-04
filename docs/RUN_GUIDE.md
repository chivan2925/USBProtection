# USBShield — Hướng dẫn chạy và triển khai chung (Admin Server + Ubuntu Client)

> **Phạm vi:** Hướng dẫn triển khai và phân quyền truy cập; **không phải bằng chứng đã chạy Ubuntu E2E**. Source baseline: `vinhkhang` `db125d28861acdb6d07aaed96c53cddfcba07408` (04/11/2026, chưa merge `main`). Đây là **hướng dẫn chạy duy nhất đang có hiệu lực** cho cả Admin Server và Ubuntu Agent `.deb`. Ubuntu E2E vẫn **NOT EXECUTED/DEFERRED**.

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
- `usbshield_test`: Maven Admin integration tests; **disposable**; Hibernate có thể xóa/tạo lại các bảng.
- `usbshield_browser_test`: browser-live và các script HTTP QA; **disposable**. Browser harness tạo schema `usbshield_e2e_<id>` riêng cho mỗi lượt. Role `usbshield_test` phải có quyền `CREATE` trên database test này (để tạo schema riêng); tạo database `OWNER usbshield_test` là cách mặc định. Các script HTTP QA cũ có thể thao tác bảng trong schema `public` của **test database**, vì vậy chỉ dùng database test trống/disposable. Schema test cũ không tự dọn, chỉ xóa sau khi xác nhận đúng database/schema.
- Tránh dùng role `postgres`/superuser cho ứng dụng hay test. Nếu lỗi `permission denied for schema public`, xác nhận **đúng database và role** trước khi cấp quyền. Nếu lỗi `must be owner of table`, có bảng cũ thuộc role khác; không cấp quyền vào database ứng dụng để chữa lỗi test. Không sao chép cấu hình test vào production.
- Không migrate dữ liệu H2 vì owner đã quyết định bỏ dữ liệu demo cũ. PostgreSQL được tạo mới, không có dữ liệu cũ.

## 3. Build React + Spring Boot và chạy Admin Server

Yêu cầu: JDK 21, Maven, Node/npm theo [Developer Guide](DEVELOPER_GUIDE.md), PostgreSQL đang chạy. Chạy PowerShell tại root repository. **Các biến môi trường đặt trong cùng terminal chạy Maven/JAR**; IntelliJ Database lưu mật khẩu riêng, Maven không tự đọc được.

```powershell
# Nhập mật khẩu thay vì ghi trực tiếp giá trị thực vào source/command history.
$env:USBSHIELD_TEST_DB_USERNAME = 'usbshield_test'
$env:USBSHIELD_TEST_DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Password cua usbshield_test' -AsSecureString)).Password
# Tùy chọn: USBSHIELD_TEST_DB_HOST=127.0.0.1, USBSHIELD_TEST_DB_PORT=5432

mvn -f admin-server/pom.xml clean test
mvn -f client-agent/pom.xml clean test
cd frontend
npm ci
npm test
npm run lint
npm run build
cd ..
mvn -f admin-server/pom.xml -Pwith-frontend -DskipTests package
cd frontend
npm run test:browser:live
cd ..
```

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
$env:USBSHIELD_BIND_ADDRESS = '127.0.0.1'
$env:PORT = '18080'

java -jar admin-server/target/usbshield-admin-server.jar --spring.profiles.active=prod
```

Profile mặc định `application.yml` dùng URL `jdbc:postgresql://${USBSHIELD_DB_HOST:127.0.0.1}:${USBSHIELD_DB_PORT:5432}/${USBSHIELD_DB_NAME:usbshield}`, username `usbshield` và password từ `USBSHIELD_DB_PASSWORD`. **Profile `prod` yêu cầu `USBSHIELD_DB_URL` và `USBSHIELD_DB_PASSWORD`**; không mặc định dùng `USBSHIELD_DB_HOST/NAME` để thay thế URL này. Hibernate đang dùng `ddl-auto=update` để khởi tạo schema cho **demo**. Đây không phải một chiến lược quản lý database migration chặt chẽ cho production doanh nghiệp.

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

**Ví dụ khái niệm cho Nginx (chỉ áp dụng khi triển khai LAN; sửa IP/cert/domain đúng môi trường):**

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

Agent dùng `/etc/usbshield-agent/application.yml` do root quản lý (`root:usbshield`, `0640`):

```yaml
usbshield:
  agent:
    server-url: "https://usbshield.lab"
    enrollment-token: "<PRIVATE_BOOTSTRAP_TOKEN>"
    heartbeat-interval-ms: 5000
    data-dir: /var/lib/usbshield-agent
    config-dir: /etc/usbshield-agent
    simulation-enabled: false
```

Cần chứng chỉ HTTPS được **trình duyệt Admin và JDK trên Agent tin cậy**; CA tự cấp không tự động được Java tin cậy. Không tắt xác minh chứng chỉ để vượt qua lỗi. Client chỉ cần outbound đến domain/cổng API; user thường không cần biết URL Admin Web.

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
sudo apt update
sudo apt install ./usbshield-agent_1.0.0_amd64.deb
sudoedit /etc/usbshield-agent/application.yml
# Cấu hình server-url HTTPS nội bộ và bootstrap enrollment token.
sudo systemctl restart usbshield-agent
systemctl status usbguard usbshield-agent --no-pager
```

`apt install ./...deb` cài dependency và service, **không tự thiết lập Server URL/token đúng cho từng môi trường**. Nếu đang triển khai Client bằng file `.deb` mới, phải có quyền sudo/root lúc cài và cấu hình; sau đó normal user không được sửa service. **Ubuntu install/E2E vẫn chưa được thực hiện**, nên đây là quy trình đề xuất, không phải kết quả kiểm thử.

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
- Sau khi cài `.deb`, cấu hình đúng `usbshield.agent.server-url` và bootstrap enrollment token trong `/etc/usbshield-agent/application.yml`; dùng chính DNS/IP LAN Server, **không dùng localhost** từ Client. File này do root quản lý, không đưa token lên Git.

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
4. Chỉ khi kết nối LAN dùng chung hoặc đưa dữ liệu thật vào hệ thống mới triển khai HTTPS/reverse proxy, CA/trust cho Agent và IP allowlist quản trị. HTTP có thể là ngoại lệ demo **cô lập và dùng credential thử nghiệm**, tuyệt đối không ghi thành cấu hình production đã an toàn.

Các trường hợp Ubuntu trong `docs/qa/WEEK4_UBUNTU_ACCEPTANCE.md` hiện **DEFERRED**; không chấm FULL PASS nếu chưa chạy.
