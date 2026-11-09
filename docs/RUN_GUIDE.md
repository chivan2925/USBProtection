# USBShield — Hướng dẫn chạy và triển khai chung (Admin Server + Ubuntu Client)

> **Phạm vi:** Hướng dẫn triển khai và phân quyền truy cập; **không phải bằng chứng đã chạy Ubuntu E2E**. Source baseline đã đối chiếu: `vinhkhang` `4d2216c7dd277c7ef4af1962e0b04638e3971d09` (07/11/2026); Windows verifier log PASS ngày 09/11/2026 được owner cung cấp, bản sửa thứ tự JAR chưa thuộc HEAD này. Đây là **hướng dẫn chạy duy nhất đang có hiệu lực** cho cả Admin Server và Ubuntu Agent `.deb`. Ubuntu E2E vẫn **NOT EXECUTED/DEFERRED**.

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

### Flyway cho PostgreSQL: bắt buộc phân biệt database mới/cũ (F07 thuộc ba đợt 04/11, không phải review F07 mới)

Runtime hiện dùng **Flyway V1/V2** và Hibernate `ddl-auto=validate` (`clean-disabled=true`, `baseline-on-migrate=false`), **không còn `update`**. Nếu `usbshield` mới và trống: chạy bình thường để Flyway tự migrate; **không baseline**. Nếu `usbshield` hiện hữu đã có năm bảng do Hibernate `update` trước đây tạo ra: **dừng server, backup có thể restore, chạy preflight và baseline V1 đúng một lần** theo [POSTGRES_FLYWAY_BASELINE.md](../admin-server/ops/POSTGRES_FLYWAY_BASELINE.md), rồi để Flyway chạy V2. Không baseline schema test hoặc tự bật `baseline-on-migrate=true` thường trực.

## 3. Build React + Spring Boot và chạy Admin Server

Yêu cầu: JDK 21, Maven, Node/npm theo [Developer Guide](DEVELOPER_GUIDE.md), PostgreSQL đang chạy. Chạy PowerShell tại root repository. IntelliJ Database không tự chuyển mật khẩu cho Maven.

**Khởi tạo cấu hình test một lần:** `Copy-Item .env.example .env`, điền `USBSHIELD_TEST_DB_USERNAME=usbshield_test` và `USBSHIELD_TEST_DB_PASSWORD` thật vào file `.env` **không được commit**. `scripts/with-env.ps1` đọc `.env` như dữ liệu, không thực thi shell expressions. Các lệnh Maven/npm chạy trực tiếp không tự load `.env`.

**Verifier Windows hiện hành sau package đồng bộ:** chạy `./packaging/tests/verify-windows.ps1` trong PowerShell 7.4+ (owner dùng 7.6.9). Script này thay cho `verify-week2-windows.ps1`, `verify-week3-windows.ps1`, `verify-week4-windows.ps1` và các staging-verifier cũ. Chạy trên source checkout hiện hành; không dùng `git add .` thay cho kiểm thử. Đối với Ubuntu: `packaging/tests/check-usbshield-core.sh` chỉ là kiểm tra read-only và không thay thế E2E vật lý.

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

**Thứ tự bắt buộc:** `mvn ... clean test` xóa `admin-server/target/`; vì vậy build React rồi `mvn -f admin-server/pom.xml -Pwith-frontend -DskipTests package` **sau** bước clean test và **trước** `test:browser:live` / `test:live`. Bản `verify-windows.ps1` trong package thực hiện chính thứ tự này. Không cần build JAR thủ công ngay trước verifier. Trên log owner 09/11: Admin 60 tests PASS, Agent 276 tests (3 Linux-only SKIP), frontend 116 tests PASS, cả hai browser-live suites PASS, DEB structure/hash PASS. Đây là kết quả máy owner, không phải test mới của package này.

Admin test Java dùng `usbshield_test`/schema riêng; browser-live dùng `usbshield_browser_test`/schema riêng. Agent notifier **3 Linux-only tests sẽ skip trên Windows**; cần chạy trên Ubuntu để nghiệm thu F09. Các bộ browser gửi HTTP events/ACK tổng hợp, không xác nhận USBGuard thật.

`admin-server/target/usbshield-admin-server.jar` là **executable JAR** có React static bundle và API cùng origin. `npm run test:live` là bộ browser/Spring khác có thể chạy riêng; `test:browser:live` tự kích hoạt build frontend (`pretest:browser:live`) và dùng `usbshield_browser_test`/schema riêng với event HTTP tổng hợp. Không đánh đồng các test này với thiết bị Ubuntu thật. Không dùng database `usbshield` cho QA.

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

**Cấu hình Nginx/IP ACL thực thi trong lab:** sử dụng [HTTPS `usbprotection.lab` — Windows + Ubuntu A/B](USBShield_HTTPS_ACL_Setup_usbprotection.lab_vinhkhang_4d2216c_20261109.md) và template [Nginx](../packaging/nginx/usbshield-https-acl.conf.example). Điền IP Admin và hai IP Ubuntu theo **`/32` riêng**, không dùng allowlist `/24` bao trùm cả Admin và Client. Luồng cố định: Client/Admin → Nginx **HTTPS 443** → Spring Boot **HTTP loopback `127.0.0.1:18080`**. Không đặt proxy trên Ubuntu Client và không mở backend/DB ra LAN. Kiểm tra bằng script `packaging/tests/verify-phase3-https-acl.sh` từ IP nguồn thực tế; Nginx `-t` PASS không đủ để chốt gate.

Ubuntu Agent `live` **không chấp nhận URL HTTP hoặc thiếu URL**, và không tự follow HTTP redirect mang Agent token. `/etc/usbshield-agent/application.yml` dùng biến từ file `/etc/usbshield-agent/agent.env` **root-owned 0600** qua systemd `EnvironmentFile=-...`; cấu hình file này **trước khi cài/upgrade `.deb`**, vì `postinst` có thể restart Agent ngay. Không commit credential vào repository:

```dotenv
USBSHIELD_SERVER_URL=https://usbprotection.lab
USBSHIELD_ENROLLMENT_TOKEN=<deployment-secret>
# Chỉ cần với private CA, là absolute path tới CA public certificate (PEM):
USBSHIELD_TLS_CA_CERTIFICATE=/etc/usbshield-agent/usbshield-ca.pem
```

Chứng chỉ phải có DNS/IP trong SAN trùng URL, và được **trình duyệt Admin/JVM trên Agent tin cậy**. CA private key không được phân phối; không tắt certificate/hostname verification. Đọc [Phase 3 HTTPS/ACL](PHASE3_HTTPS_ACL_AND_PAGING.md) để kiểm thử từ hai máy riêng biệt.

## 5. Cài `.deb` khác với tải một file bất kỳ từ GitHub

**Gói `.deb` build một lần và chuyển sang cả Ubuntu A/B.** Trên **Windows 11 PowerShell 7.6.9**, dùng đường build đã được owner chạy PASS:

```powershell
.\packaging\tests\verify-windows.ps1
# Verifier tạo .deb và release manifest; không phải build lại JAR trước verifier.
$release = Get-Content .\packaging\dist\usbshield-agent.release.json -Raw | ConvertFrom-Json
$deb = Join-Path .\packaging\dist $release.filename
Get-FileHash $deb -Algorithm SHA256
```

Trên **Ubuntu/Linux builder** (JDK 21, Maven, `dpkg-deb`, GNU coreutils và Git), lựa chọn thay thế:

```bash
mvn -f client-agent/pom.xml clean test
bash packaging/scripts/build-deb.sh
python3 - <<'PYCODE'
import json, pathlib
m = json.loads(pathlib.Path('packaging/dist/usbshield-agent.release.json').read_text())
p = pathlib.Path('packaging/dist') / m['filename']
print(p, m['sha256'])
PYCODE
# Sau đó dùng sha256sum và dpkg-deb -I/-c trên đúng filename trong manifest.
```

Cả hai builder hiện sinh `Version: 1.0.0+build<UTC 17 chữ số>`, file `usbshield-agent_<version>_amd64.deb` và `packaging/dist/usbshield-agent.release.json` chứa `version`, `filename`, `sha256`, `gitHead`, `builtAtUtc`; cả hai cũng có `control/md5sums` cho data payload. `gitHead` chỉ là commit gốc: **nếu working tree còn sửa chưa commit, SHA này không chứng minh toàn bộ artifact đến từ commit sạch**. Khi nghiệm thu cần ghi thêm `git status --short` và hash `.deb`. Không giả định GitHub đã phát hành file `.deb`.

Phân phối artifact đã xác minh qua kênh kiểm soát (SCP/SFTP hoặc shared folder lab), không sao chép `endpoint.json`, token Agent hay spool giữa máy. Trên Ubuntu A/B, đặt tên file chuyển đến đơn giản là `usbshield-agent.deb` nếu muốn.

Trên **từng Ubuntu Client** (operator có sudo/root, trước khi cài hoặc nâng cấp):

```bash
sudo install -d -o root -g root -m 0750 /etc/usbshield-agent
# Tạo secret file trước, rồi sửa riêng trên từng máy.
if ! sudo test -e /etc/usbshield-agent/agent.env; then
  sudo install -o root -g root -m 0600 /dev/null /etc/usbshield-agent/agent.env
fi
sudoedit /etc/usbshield-agent/agent.env
# Nhập URL HTTPS, enrollment token và đường dẫn public CA PEM; giữ kín token.
sudo chown root:root /etc/usbshield-agent/agent.env
sudo chmod 0600 /etc/usbshield-agent/agent.env
sha256sum ./usbshield-agent.deb
sudo dpkg-deb -I ./usbshield-agent.deb
sudo dpkg-deb -c ./usbshield-agent.deb
sudo apt install -y ./usbshield-agent.deb
systemctl status usbguard usbshield-agent --no-pager
```

**Không chạy lệnh `install /dev/null` ở trên nếu file `agent.env` đã có bí mật cần giữ.** Khi đã tồn tại, chỉ kiểm quyền/sửa bằng `sudoedit`. Installer `postinst` khởi động Agent, nên phải chuẩn bị CA/URL/token **trước khi** gọi `apt install`; xem hướng dẫn HTTPS riêng.

`apt install ./...deb` cài dependency và service, **không tự thiết lập Server URL/token đúng cho từng môi trường**. Đảm bảo DNS, CA trust và `agent.env` đã chuẩn bị **trước** vì `postinst` khởi động service; gói fail-closed nếu chưa cấu hình hợp lệ. Nếu đang triển khai Client bằng file `.deb` mới, phải có quyền sudo/root lúc cài và cấu hình; sau đó normal user không được sửa service. **Ubuntu install/E2E vẫn chưa được thực hiện**, nên đây là quy trình đề xuất, không phải kết quả kiểm thử.

## 6. Khi nào mới cần Docker?

Có thể bổ sung Docker Compose trong một đợt riêng nếu mục tiêu là server restart, PostgreSQL data volume, log và TLS proxy reproducible. Với demo 1 Admin Server + vài Client, Java JAR + systemd/Windows service đã đủ. Docker **không tự làm server có thể truy cập từ mạng khác, không thay thế TLS, và không bảo vệ USB** (USBGuard nằm ở từng Ubuntu Client).

## 7. Trạng thái nghiệm thu và giới hạn

- Windows Maven/frontend/API mock tests có thể chứng minh contract/code, **không chứng minh USB vật lý**.
- Ubuntu E2E (USB flash, chuột/phím, `.deb`, reboot, policy persistence, GUI popup, service/ACL, Allow/Revoke theo endpoint) **DEFERRED/NOT EXECUTED**.
- `usbguard allow-device <runtime-id>` và `usbguard block-device <runtime-id>` là CLI của USBGuard cho thiết bị đang hiện diện, **nhưng bản hiện tại mới đồng bộ whitelist/rules; chưa kết luận đã điều khiển ngay USB đang cắm**. Triển khai chức năng này cần cơ chế kiểm tra lại runtime ID/hash, quyền IPC (`Devices=modify`), chống race, báo lỗi/ACK và kiểm chứng Ubuntu trước khi bổ sung vào release.

Các tham chiếu: [Developer Guide](DEVELOPER_GUIDE.md), [Admin API](API.md), [Ubuntu Acceptance](qa/WEEK4_UBUNTU_ACCEPTANCE.md).

## 8. Lưu ý vận hành outbox sau tối ưu

Accounting cache giả định **một tiến trình Agent là writer duy nhất**, các thư mục spool và quarantine được bảo vệ khỏi normal user. Khi phát hiện thay đổi metadata thư mục hoặc ghi lỗi, Agent quét lại quota (bao gồm quarantine). Outbox gửi lại các lỗi mạng/5xx tạm thời; phản hồi **400/409/413/422 vĩnh viễn** (trừ 400 future-skew có thể hồi phục) được **cách ly giữ bằng chứng** và không khóa vô hạn FIFO. Không xóa tay quarantine để che lỗi xác thực/payload. **Không sửa nội dung của một file spool đang tồn tại khi Agent còn chạy**: thao tác sửa *in-place* không nhất thiết làm thay đổi mtime của thư mục và có thể làm cache quota tạm thời sai. Nếu operator cần can thiệp thủ công, dừng Agent có kiểm soát, sao lưu bằng chứng, sửa và khởi động lại để ép full scan. Không tự xóa audit/quarantine để né quota.

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

## 11. Đồng bộ review 09/11/2026 và phạm vi cần chứng minh

- Review F01–F14 gần nhất là nhóm **khác** với finding F01–F14 của ba đợt 04/11. Đọc [status hiện tại](IMPLEMENTATION_STATUS_20261109.md) và [ledger đối chiếu](qa/2026-11-09-review-and-packaging-sync.md).
- `packaging/scripts/build-deb.ps1` và `build-deb.sh` hiện dùng tên .deb/version/manifest đồng bộ; **không dùng lại** tên cố định `usbshield-agent_1.0.0_amd64.deb`. `packaging/tests/verify-windows.ps1` là gate Windows; `check-usbshield-core.sh`, `verify-phase3-https-acl.sh`, `test-notifier-ipc-ubuntu.sh` là kiểm thử Linux/Ubuntu chuyên biệt.
- Lần chạy Windows verifier owner 09/11 PASS, nhưng **chưa chạy** Linux packaging E2E, `prerm` cleanup, TLS/ACL từ hai nguồn IP và USB thật. B01–B05/các kiểm tra native không được đánh dấu PASS bằng bằng chứng Windows.
