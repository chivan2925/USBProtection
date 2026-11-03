# USBShield Admin Server — chạy bản demo trong LAN (không bắt buộc Docker)

> **Phạm vi:** Hướng dẫn triển khai và phân quyền truy cập; **không phải bằng chứng đã chạy Ubuntu E2E**. Source baseline: `main` `0e6a29cf10947f7fc79ff8dd9972bda07a60a071` (03/11/2026). Admin Server là ứng dụng Java/Spring Boot độc lập với Ubuntu Agent `.deb`.

## 1. Chọn mô hình tối giản

- **Một máy Admin Server** (Windows hoặc Ubuntu, JDK 21) chạy **một executable JAR Spring Boot**, H2 file mode trên ổ đĩa riêng. React được đóng gói vào JAR; không cần Vite/NPM ở runtime.
- **Admin** dùng **trình duyệt + tài khoản Admin** để truy cập React Web và các API quản trị `/api/auth/**`, `/api/admin/**`; **normal Ubuntu users không được cấp tài khoản Admin**.
- **Các Ubuntu Client** chỉ chạy Agent `.deb` ở chế độ non-web và gửi request **tới `/api/agent/**`**, không cài/khởi chạy Admin Web. `server-url` phải trỏ đến **DNS/IP của Admin Server trên LAN**, không dùng `127.0.0.1`/`localhost` ở máy khác.
- **Phân biệt authentication và network access:** Spring Security bảo vệ dữ liệu/hành động Admin, nhưng nếu cùng một origin được mở cho cả LAN, máy Client vẫn có thể tải *trang đăng nhập/tài nguyên tĩnh*. Muốn **Client không mở được Web ngay ở tầng mạng**, phải cấu hình reverse proxy/firewall giới hạn route theo IP/VLAN (mục 4); không thể chỉ dựa vào việc máy Client có `.deb`.
- Với truy cập từ máy khác, dùng một **HTTPS reverse proxy** trên máy Server; Spring Boot giữ bind `127.0.0.1:18080` ở phía sau proxy (trùng `PORT` tại mục 3). Chỉ mở cổng TLS cần thiết cho mạng nội bộ.
- **Không yêu cầu Docker.** Docker Compose chỉ thuận tiện khi muốn quản lý lifecycle/volume/log tập trung; với một server H2 và bài demo, JAR đơn giản hơn.

## 2. Build React + Spring Boot (Windows PowerShell, từ root repository)

Yêu cầu: Java 21, Maven phù hợp, Node.js/npm theo `docs/DEVELOPER_GUIDE.md`. Không đóng gói `node_modules`, DB, token hoặc password trong artifact.

```powershell
cd frontend
npm ci
npm test
npm run lint
npm run build
cd ..

mvn -f admin-server/pom.xml clean test
mvn -f admin-server/pom.xml -Pwith-frontend -DskipTests package
```

Artifact: `admin-server/target/usbshield-admin-server.jar`. `-Pwith-frontend` copy `frontend/dist` vào `classpath:/static/`; web và API dùng **cùng origin**. Truy cập root URL để mở React; không cần chạy `npm run dev` trên máy Server sau khi build.

## 3. Kiểm tra localhost trước khi cho các Client kết nối

Tạo folder H2 bên ngoài repository, cấp quyền ghi chỉ cho tài khoản chạy server. Đặt secrets bên ngoài Git; các dòng dưới dùng **placeholder**, không phải password/token mẫu nên đưa vào môi trường thật.

```powershell
# Trong PowerShell chỉ dành cho thử nghiệm cô lập; không lưu secrets vào transcript/commit.
$env:USBSHIELD_DB_URL = 'jdbc:h2:file:C:/USBShield-data/usbshield;DB_CLOSE_ON_EXIT=FALSE'
$env:USBSHIELD_DB_USERNAME = 'sa'
$env:USBSHIELD_DB_PASSWORD = '<PRIVATE_H2_PASSWORD>'
$env:USBSHIELD_ADMIN_USERNAME = 'admin'
$env:USBSHIELD_ADMIN_PASSWORD = '<PRIVATE_INITIAL_ADMIN_PASSWORD>'
$bytes = [byte[]]::new(32)
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$token = [Convert]::ToHexString($bytes).ToLowerInvariant()
$env:USBSHIELD_ENROLLMENT_TOKEN = $token
$env:USBSHIELD_BIND_ADDRESS = '127.0.0.1'
$env:PORT = '18080'

java -jar admin-server/target/usbshield-admin-server.jar --spring.profiles.active=prod
```

Bản `prod` mặc định `Secure` session cookie (`USBSHIELD_COOKIE_SECURE=true`), do đó cần **HTTPS** cho browser session. Mở Admin Web từ địa chỉ HTTPS của proxy; không kết luận đăng nhập lỗi chỉ vì thử HTTP với Secure cookie.

- Admin password môi trường **chỉ bootstrap tài khoản lần đầu**, không reset mật khẩu tài khoản đã có trong H2.
- DB URL/password phải giữ ổn định giữa các lần restart; sao lưu H2 khi server đã dừng.
- Chỉ mở cổng proxy (ví dụ 443) cho mạng LAN quản trị; **không expose** H2 console, 8080 nội bộ, Swagger production hoặc các cổng debug.

## 4. Phân quyền Admin Web và Agent API trên LAN

**Tách hai luồng truy cập**, dù cùng chạy trong một Spring Boot JAR:

```text
Máy Admin (browser, IP/VLAN quản trị) ---> HTTPS proxy ---> /, /api/auth/**, /api/admin/**
Máy Ubuntu (Agent .deb, mạng Client) --> HTTPS proxy ---> /api/agent/**
                                                       |
                                                       v
                                                127.0.0.1:18080
                                                Spring Boot + H2
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

Có thể bổ sung Docker Compose trong một đợt riêng nếu mục tiêu là server restart, volume H2, log và TLS proxy reproducible. Với demo 1 Admin Server + vài Client, Java JAR + systemd/Windows service đã đủ. Docker **không tự làm server có thể truy cập từ mạng khác, không thay thế TLS, và không bảo vệ USB** (USBGuard nằm ở từng Ubuntu Client).

## 7. Trạng thái nghiệm thu và giới hạn

- Windows Maven/frontend/API mock tests có thể chứng minh contract/code, **không chứng minh USB vật lý**.
- Ubuntu E2E (USB flash, chuột/phím, `.deb`, reboot, policy persistence, GUI popup, service/ACL, Allow/Revoke theo endpoint) **DEFERRED/NOT EXECUTED**.
- `usbguard allow-device <runtime-id>` và `usbguard block-device <runtime-id>` là CLI của USBGuard cho thiết bị đang hiện diện, **nhưng bản hiện tại mới đồng bộ whitelist/rules; chưa kết luận đã điều khiển ngay USB đang cắm**. Triển khai chức năng này cần cơ chế kiểm tra lại runtime ID/hash, quyền IPC (`Devices=modify`), chống race, báo lỗi/ACK và kiểm chứng Ubuntu trước khi bổ sung vào release.

Các tham chiếu: [Developer Guide](DEVELOPER_GUIDE.md), [Client Installation](CLIENT_INSTALLATION.md), [Admin API](API.md), [Ubuntu Acceptance](qa/WEEK4_UBUNTU_ACCEPTANCE.md).

## 8. Lưu ý vận hành outbox sau tối ưu

Accounting cache giả định **một tiến trình Agent là writer duy nhất**, các thư mục spool và quarantine được bảo vệ khỏi normal user. Khi phát hiện thay đổi metadata thư mục hoặc ghi lỗi, Agent quét lại quota (bao gồm quarantine). **Không sửa nội dung của một file spool đang tồn tại khi Agent còn chạy**: thao tác sửa *in-place* không nhất thiết làm thay đổi mtime của thư mục và có thể làm cache quota tạm thời sai. Nếu operator cần can thiệp thủ công, dừng Agent có kiểm soát, sao lưu bằng chứng, sửa và khởi động lại để ép full scan. Không tự xóa audit/quarantine để né quota.
