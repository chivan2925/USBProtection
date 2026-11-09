# USBShield — Ubuntu 24.04 Minimal Core E2E (Windows build `.deb`)

> **Mục tiêu:** kiểm thử nhanh luồng **BLOCK → Allow A → A ALLOW → B BLOCK → Revoke A → A BLOCK** bằng **một USB Kingston**, hai VM Ubuntu Desktop 24.04 A/B và Admin Server chạy Windows 11. Phần lệnh bên dưới là **hướng dẫn có thể tái chạy**; kết quả thực tế 09/11 được lưu riêng trong [owner closeout](qa/2026-11-09-ubuntu-minimal-core-owner-acceptance.md).
> **Source hiện hành đối chiếu:** `vinhkhang` `6923be001890bee9a303a8cee8e7dcc2cf2db180` (09/11/2026). Filename `f446c04` giữ để không gãy liên kết; **đừng hiểu tên file là commit test**. Windows verifier PASS theo log; Ubuntu C1–C5 PASS theo báo cáo owner, không phải independent re-run. Không dùng `apply-fixes.ps1`.
> **HTTPS cố định:** `https://usbprotection.lab`. Cấu hình CA/SAN, Nginx/ACL, firewall và `agent.env` theo [HTTPS setup (current)](USBShield_HTTPS_ACL_Setup_usbprotection.lab_vinhkhang_4d2216c_20261109.md), **nằm cùng package**.
> **Lịch sử:** lần Ubuntu E2E cũ FAIL ở Phase 6, root cause chưa được xác minh đầy đủ. Ngày 09/11 owner báo **đã PASS Minimal Core C1–C5 sau khi khắc phục sự cố P0 stale identity/401**; điều này không tự chứng minh mọi failure path của Phase 6 lịch sử đã được sửa hoặc regression-free.

## 0. Phạm vi & nguyên tắc

- **Windows:** clone/update repository, build Admin Server và **một `.deb` trực tiếp bằng PowerShell 7**. Không cần WSL, Linux, `dpkg-deb` hoặc `tar.exe` để **build**. Cần Java JDK 21 và Maven cho JAR, Node/npm nếu build frontend.
- **Ubuntu A/B:** **không clone repo, không Maven**, chỉ nhận **cùng file `.deb`** và `check-usbshield-core.sh`; cài `.deb` từ file đã chuyển qua VMware Shared Folders hoặc SCP.
- **Trước khi bắt đầu:** hoàn thành HTTPS theo tài liệu riêng; PostgreSQL chạy trên Windows; máy A/B có GUI GNOME, mạng tới `usbprotection.lab`, quyền `sudo` lúc cài và một USB không chứa dữ liệu quan trọng. VM A/B phải có hostname/machine-id riêng.
- **Không bắt đầu Core E2E nếu** implicit USBGuard khác `block`, thiếu base rule `allow with-interface none-of { 08:*:* }`, Agent chưa kết nối HTTPS hoặc Client đọc được Admin Web. Không dùng `curl -k`, `usbguard allow-device` để giả lập Admin Allow hoặc cho Agent chạy root.
- **Tối giản nhưng không tự động 100%:** thao tác **Allow/Revoke qua Admin Web**, **rút/cắm Kingston hoặc VMware passthrough**, và **xác minh popup GUI** vẫn phải thực hiện thật. `list-rules`, ACK hay script PASS *không* tự chứng minh USB được ALLOW/BLOCK vật lý.

**Đường dẫn tài liệu/script trên `vinhkhang` sau khi áp dụng package** (copy script Client sang Ubuntu):

```text
docs/USBShield_Ubuntu24_Minimal_Core_E2E_vinhkhang_f446c04_20261107.md
packaging/scripts/build-deb.ps1
packaging/tests/check-usbshield-core.sh
```

## 1. Setup **một lần** (không tính vào Core E2E)

### 1.1. Windows — source và build bằng PowerShell thuần

Mở **PowerShell 7.4+ (`pwsh.exe`)** tại root repo trên Windows:

```powershell
git status --short                 # nếu có chỉnh sửa chưa commit: xử lý trước
git fetch origin
git switch vinhkhang
git rev-parse HEAD                  # baseline đã đối chiếu: 4d2216c7...; sau commit docs SHA có thể mới
# Chạy verifier PowerShell 7.6.9 (Admin/Agent/frontend/browser + build/inspect .deb):
.\packaging\tests\verify-windows.ps1
# Verifier tạo gói .deb và manifest. Không build JAR trước khi chạy clean test.
# Nếu cần build lại chỉ .deb sau đó: .\packaging\scripts\build-deb.ps1
$release = Get-Content ./packaging/dist/usbshield-agent.release.json -Raw | ConvertFrom-Json
Get-FileHash (Join-Path ./packaging/dist $release.filename) -Algorithm SHA256
# Chuyển chính file $release.filename sang A/B; có thể đổi tên thành usbshield-agent.deb trên VM.

# Admin JAR có React đã được verifier build/kiểm ngay sau Maven clean test.
# Chỉ build thủ công nếu cố ý bỏ qua verifier, không phải bước bắt buộc thứ hai.
Get-FileHash ./admin-server/target/usbshield-admin-server.jar -Algorithm SHA256
```

**Ghi chú build:** script mới dùng **PowerShell 7 + `.NET System.Formats.Tar` và `ar` writer**, tạo `debian-binary`, `control.tar.gz`, `data.tar.gz`, giữ owner `root:root` và executable modes cho `postinst/prerm/notifier`. **Không phải** script `packaging/scripts/build-deb.sh` cũ. Script **không cài Agent trên Windows**; chỉ tạo artifact Linux `amd64`.

**Log Windows owner 09/11:** Admin 60 PASS; Agent 276 tests, 3 Linux-only SKIP; Frontend 116 PASS + lint/build; Browser Live và HTTP Live PostgreSQL test databases PASS; `.deb` archive/manifest hash PASS. Verifier được sửa để đóng gói Admin JAR **sau** `clean test`, **trước** browser live tests. Đó không phải Linux native acceptance.

Nếu Maven/Java/Node chưa cài, cài trên Windows trước. Nếu build thất bại, **không** dùng artifact từ lần build trước. Windows/Linux build script mới sinh Debian `Version` mới `1.0.0+build<UTC timestamp>`, kèm file `packaging/dist/usbshield-agent.release.json` chứa SHA-256, Git HEAD và tên gói; không commit các artifact này.

### 1.2. Windows — xóa cả ba PostgreSQL DB demo, tạo lại sạch

**Chỉ làm ở bước SETUP và chỉ khi chắc chắn đây là PostgreSQL instance lab.** Sếp đã xác nhận dữ liệu cũ không quan trọng. Thao tác này **xóa vĩnh viễn** dữ liệu ở ba DB: `usbshield`, `usbshield_test`, `usbshield_browser_test` — không ảnh hưởng DB khác.

Dừng Admin JAR, test harness và Agent trên A/B trước; **giữ PostgreSQL service đang chạy**. Từ PowerShell, kết nối **maintenance DB**:

```powershell
psql -h 127.0.0.1 -p 5432 -U postgres -d postgres
```

Trong `psql`, kiểm tra **đúng server/database** trước khi DROP:

```sql
SELECT current_database(), inet_server_addr(), inet_server_port(), current_user;
SELECT datname, pg_get_userbyid(datdba) AS owner FROM pg_database
WHERE datname IN ('usbshield','usbshield_test','usbshield_browser_test');
SELECT rolname FROM pg_roles WHERE rolname IN ('usbshield','usbshield_test');
```

Chỉ sau khi xác nhận ba database đều disposable, chạy **ngoài transaction**:

```sql
DROP DATABASE IF EXISTS usbshield WITH (FORCE);
DROP DATABASE IF EXISTS usbshield_test WITH (FORCE);
DROP DATABASE IF EXISTS usbshield_browser_test WITH (FORCE);
```

Nếu role chưa tồn tại thì **chỉ chạy dòng tương ứng**:

```sql
CREATE ROLE usbshield LOGIN;
CREATE ROLE usbshield_test LOGIN;
```

Đặt mật khẩu bảo mật bằng `\password usbshield` và `\password usbshield_test` (lần lượt). Sau đó:

```sql
CREATE DATABASE usbshield OWNER usbshield;
CREATE DATABASE usbshield_test OWNER usbshield_test;
CREATE DATABASE usbshield_browser_test OWNER usbshield_test;
```

Gõ `\q` để thoát. Lưu password ứng dụng vào `.env.e2e.local` và password test vào `.env` (hai file local, không commit). **Không dùng Flyway baseline** trên DB trống: Spring Boot tự chạy Flyway V1/V2 và Hibernate `validate` khi boot.

### 1.3. Windows — start Admin Server sau khi HTTPS đã cấu hình

Trong `.env.e2e.local` (giữ bí mật; chỉ để local):

```dotenv
USBSHIELD_DB_URL=jdbc:postgresql://127.0.0.1:5432/usbshield
USBSHIELD_DB_USERNAME=usbshield
USBSHIELD_DB_PASSWORD=<APP_PASSWORD>
USBSHIELD_ADMIN_USERNAME=admin
USBSHIELD_ADMIN_PASSWORD=<NEW_BOOTSTRAP_ADMIN_PASSWORD>
USBSHIELD_ENROLLMENT_TOKEN=<NEW_STABLE_SECRET_FOR_A_AND_B>
USBSHIELD_BIND_ADDRESS=127.0.0.1
PORT=18080
```

Trong một PowerShell riêng ở root repo:

```powershell
./scripts/with-env.ps1 -EnvFile (Join-Path (Get-Location) '.env.e2e.local') -Command {
    java -jar admin-server/target/usbshield-admin-server.jar --spring.profiles.active=prod
}
```

Yêu cầu: Flyway **V1/V2** success, Web login tại `https://usbprotection.lab`, Client A/B **403** ở Web/Admin route nhưng Agent route 401 nếu không token. **Không** expose `18080` hay `5432` trên LAN. Mọi cài đặt TLS/CA/ACL nằm trong hướng dẫn HTTPS riêng.

### 1.4. Ubuntu A/B — xóa nhanh **Agent test state cũ** rồi cài cùng `.deb`

**Trả lời trực tiếp:** **Được xóa `endpoint.json` cũ** khi DB cũ đã bị bỏ, **nhưng chỉ xóa trước lần enrollment mới**. Do `EndpointManager` của HEAD hiện tại không tự re-enroll khi `endpoint.json` còn, phải xóa thêm `enrollment-pending.json` để tránh staged token cũ; spool event cũ cũng không còn giá trị. **Không** đụng `.env`, CA, systemd service, `machine-id` hoặc token ở máy kia.

Trên **mỗi VM** đã cài Agent cũ, **rút Kingston, mở VMware console**, và chạy trong terminal Ubuntu (chỉ áp dụng khi toàn bộ state là dữ liệu demo):

```bash
sudo systemctl stop usbshield-agent
# USBGuard vẫn phải đang chạy và fail-closed:
systemctl is-active usbguard
sudo usbguard get-parameter ImplicitPolicyTarget   # phải chính xác: block

# XÓA identity/pending của database cũ, trước enrollment mới:
sudo rm -f -- /var/lib/usbshield-agent/endpoint.json \
  /var/lib/usbshield-agent/enrollment-pending.json
# XÓA các event test chưa gửi; không cần copy sang DB mới:
if sudo test -d /var/lib/usbshield-agent/spool; then
  sudo find /var/lib/usbshield-agent/spool -type f -delete
fi
```

**Nếu A/B còn USBGuard Allow rule từ test cũ**, do *toàn bộ USBGuard rules cũng chỉ dành cho lab này*, có thể reset về base fail-closed **trước test** bằng lệnh sau (bỏ qua nếu rules chỉ có base rule):

```bash
# CHỈ TRÊN VM LAB: lệnh này ghi đè tất cả USBGuard rules tùy chỉnh hiện hữu.
# Thực hiện khi Kingston đã rút và có VMware console hoạt động.
sudo install -o root -g root -m 0600 \
  /etc/usbshield-agent/rules-v0.1.conf /etc/usbguard/rules.conf
sudo systemctl restart usbguard
sudo usbguard get-parameter ImplicitPolicyTarget  # block
sudo usbguard list-rules                          # chỉ base non-storage
```

Nếu A/B **mới hoàn toàn**, bỏ qua hai đoạn reset trên. Không dùng bước reset này để chữa một lần Core E2E thất bại; khi đã enroll lại, giữ nguyên state để điều tra.

Theo tài liệu HTTPS riêng, **trước khi cài `.deb`** phải có `/etc/usbshield-agent/agent.env` **root-owned 0600**, URL `https://usbprotection.lab`, CA public PEM đúng và enrollment secret trùng server; vì `postinst` tự khởi động Agent. **Không** chép `endpoint.json` giữa A/B.

Copy **cùng file `.deb`** từ Windows vào thư mục làm việc trên A/B (qua Shared Folder hoặc SCP), rồi trên từng VM:

```bash
# Không cần clone repo hay cài Maven trên Ubuntu.
sha256sum ./usbshield-agent.deb  # so với SHA256 của Windows
sudo dpkg-deb -I ./usbshield-agent.deb
sudo dpkg-deb -c ./usbshield-agent.deb

# Nếu VM chưa cài Agent:
sudo apt install -y ./usbshield-agent.deb
# Mỗi build phát hành Version riêng. Nếu cài lại CHÍNH CÙNG artifact, dùng:
# sudo apt install --reinstall -y ./usbshield-agent.deb
```

Kiểm tra `dpkg-deb -c`: `postinst`, `prerm` nằm ở control archive (kiểm qua `dpkg-deb -I`), JAR, `application.yml`, `systemd service`, base rule và notifier có mặt, file launcher executable. A/B chạy cùng SHA-256 của `.deb` và có **hai endpoint ID khác nhau** trên Admin Web. **Không đánh dấu PASS** nếu enrollment, Agent service hoặc USBGuard fail.

Copy file `packaging/tests/check-usbshield-core.sh` từ checkout/package đã đồng bộ vào **mỗi VM**, ví dụ đặt ở `~/check-usbshield-core.sh`; chạy bằng `bash` nên không cần `chmod +x`.

---

## 2. Minimal Core E2E — chỉ 5 bước

**Trước bước 1**, trên **cả A và B** chạy:

```bash
bash ~/check-usbshield-core.sh
```

Phải có `0 FAIL`. Trên Admin Web `https://usbprotection.lab`: A/B có `ONLINE`, `lastSeen` mới, endpoint IDs khác nhau. Trên hai VM `hostname` và `/etc/machine-id` khác nhau (không đăng hoặc gửi `machine-id` đầy đủ lên chat). Vào GNOME desktop thật để quan sát notifier. **Chỉ gắn Kingston vào một VM tại một thời điểm.**

| Bước | Thao tác và lệnh trên Ubuntu | Điều kiện PASS bắt buộc |
|---|---|---|
| **C1 — BLOCK ban đầu** | Gắn Kingston vào A; `bash ~/check-usbshield-core.sh --expect block` | USBGuard A `block/reject`, không truy cập được storage; Admin Web có event A, popup BLOCK xuất hiện đúng GNOME user |
| **C2 — Allow A** | Admin Web → Endpoints → A → chọn **USB đã xuất hiện trong event A** → **Allow on this endpoint**; chờ `policyVersion=appliedPolicyVersion`, Agent rule có `usbshield-managed` | Server cấp whitelist A đúng hash, Agent rule/ACK khớp; chưa gọi USB hiện đang cắm là ALLOW |
| **C3 — ALLOW A thật** | **Rút Kingston khỏi A rồi cắm lại A**; `bash ~/check-usbshield-core.sh --expect allow` | USBGuard A `allow`, OS nhận thiết bị storage (`lsblk`), không có popup BLOCK mới cho lượt allow |
| **C4 — B vẫn BLOCK** | Rút Kingston khỏi A, gắn vào B; `bash ~/check-usbshield-core.sh --expect block` | B `block/reject`, không mount storage, event/popup thuộc B; trên Web chỉ A có whitelist enabled. Nếu B ALLOW thì STOP — lỗi isolation |
| **C5 — Revoke A** | Rút Kingston khỏi B; Admin Web → A → **Revoke** đúng USB; chờ ACK mới và rule A không còn managed Allow; gắn lại Kingston vào A; `bash ~/check-usbshield-core.sh --expect block` | A `block/reject` trở lại, storage không truy cập được, event/popup phù hợp; B không bị thay policy |

**Tại C2/C5 phải kiểm tra trong Admin Web** `policyVersion` (desired) và `appliedPolicyVersion` (ACK) bằng nhau **sau khi thay đổi**, không chỉ thấy HTTP 2xx. Khi C2 gặp `409`, phải kiểm USB đã được A báo event với **device hash hợp lệ**; không dùng VID/PID-only hoặc danh sách thiết bị của B để ép Allow.

### Ghi kết quả tối giản

| Checkpoint | A/B | Kết quả (`PASS/FAIL/BLOCKED`) | Evidence ngắn |
|---|---|---|---|
| P0 — setup, 2 endpoint HTTPS và Agent | A+B | | SHA package, Web online, preflight script |
| C1 — default BLOCK | A | | terminal, event, popup |
| C2 — Allow + rule + ACK | A | | Web version, whitelist, rule |
| C3 — reconnect ALLOW | A | | `list-devices`, `lsblk` |
| C4 — isolation BLOCK | B | | `list-devices`, Web B, popup |
| C5 — Revoke + reconnect BLOCK | A | | ACK mới, rule, `list-devices` |

**Core E2E chỉ PASS khi tất cả checkpoints PASS.** Owner báo P0+C1–C5 đều PASS ngày 09/11; xem [closeout có mức evidence](qa/2026-11-09-ubuntu-minimal-core-owner-acceptance.md). Script tự kiểm tra service/HTTPS/USBGuard nhưng **không** tự kiểm Web/ACK, popup thực, mount hoặc physical USB; các điều kiện này thuộc thao tác quan sát của owner. Không tuyên bố full M1–M5 acceptance chỉ vì Core E2E PASS.

### Nếu bất kỳ bước nào FAIL

Dừng tại bước đó; **không** xóa `endpoint.json`, DROP DB, chỉnh policy bằng USBGuard CLI hoặc restart service để “làm PASS”. Chụp thời gian, Web desired/applied, `sudo usbguard list-rules`, `sudo usbguard list-devices` và lỗi Agent gần nhất tại VM thất bại (log có thể chứa dữ liệu riêng tư; không đăng raw secrets):

```bash
sudo journalctl -u usbshield-agent --since '5 minutes ago' --no-pager | tail -n 80
```

Phân loại nhanh: Admin Allow lỗi → **C2 Server**; desired tăng nhưng Agent không fetch → **C2 sync/HTTPS/token**; local rule sai → **C2 apply/USBGuard**; ACK cũ → **C2 ACK**; rule+ACK đúng nhưng reconnect vẫn BLOCK → **C3 thiết bị/hash/VMware**; B lại ALLOW → **C4 isolation**; revoke không block lại → **C5 revoke**. Chỉ kiểm tiếp sau khi biết chính xác điểm lỗi.

---

## 3. Nhắc việc SAU khi Core E2E PASS — chưa hướng dẫn ở file này

Owner quyết định **không chạy** các kiểm thử chuyên sâu ở đợt này. Đánh dấu **DEFERRED BY OWNER / NOT EXECUTED** (không tự lên lịch hoặc yêu cầu chạy thêm), gồm: recovery, TLS negative, lifecycle remove/reinstall, bảo mật quyền/ACL, restart USBGuard, reboot Agent/VM, offline spool và offline recovery, persistence sau restart, các tình huống lỗi và regression đầy đủ (bao gồm T01–T28, X01–X18 từ guide cũ). **Không có lệnh/hướng dẫn thực thi các test này trong tài liệu Minimal Core E2E.**

**Điểm kết thúc scope demo (owner PASS 09/11):** tạo `.deb` trên Windows → cài A/B → **A BLOCK → Allow+replug A ALLOW → B BLOCK → Revoke+replug A BLOCK**. Những phần hoãn lại không được ghi PASS khi chưa chạy. M4/M5 UI phải tuân theo [guardrails sau E2E](admin-web/POST_CORE_E2E_UI_CHANGE_GUARDRAILS.md).

### Chốt mã finding và fail-closed trong Core E2E

- **F02 (outbox):** HTTP 400 (trừ future-skew), 409, 413, 422 được quarantine giữ audit; lỗi mạng/5xx, 401/403 và 400 future-skew vẫn retry; không xóa chứng cứ để unblock demo.
- **F04 (USBGuard health):** ACK/version trùng không thay cho `HEALTHY`; phải kiểm tra **thứ tự rule** và không có DENY/Allow ngoại lai chặn/mở ngoài ý muốn.
- **F05 (Debian removal):** `prerm` có cleanup Allow managed; test remove/upgrade thật vẫn **DEFERRED**, không dùng Windows archive inspection để đánh dấu PASS.
- **F07:** Audit cho phép lịch sử event chứa `usb_device_id` orphan có chủ đích; không tự thêm FK `usb_events -> usb_devices` và xóa dữ liệu cũ.
- **F01 upgrade Spring Boot, F12 frontend duplicate calls, F13 best-effort notification:** owner chấp nhận giữ scope hiện tại cho demo, không tuyên bố là đã sửa.

### Lưu ý khi tái chạy script preflight trên VM này

Script `check-usbshield-core.sh` HEAD kiểm CA bằng `[[ -r /etc/usbshield-agent/usbshield-ca.pem ]]` dưới user gọi script. Do thư mục `/etc/usbshield-agent` có thể là `root:usbshield 750`, `usbdev` không đọc được CA dù `root`/Agent có quyền. Khi cần kiểm tra read-only có thể chạy `sudo bash ~/check-usbshield-core.sh`; vẫn phải bảo đảm **Java Agent service chạy `User=usbshield`**, không phải root, và kiểm GUl bằng user GNOME thật. Không đổi quyền folder secrets hoặc vô hiệu hóa TLS chỉ để làm script PASS.