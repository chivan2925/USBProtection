# USBShield — Hướng dẫn cấu hình HTTPS `usbprotection.lab` và IP ACL (Windows 11 + Ubuntu 24.04 A/B)

> **Mục đích:** runbook cấu hình DNS/hosts → CA/chứng chỉ → Nginx HTTPS → ACL → Firewall → Agent Ubuntu. **Owner đã thực hiện HTTPS/ACL gate thực tế trên Windows và A/B, PASS theo trao đổi 09/11**, độc lập với Windows verifier; xem [owner acceptance](qa/2026-11-09-ubuntu-minimal-core-owner-acceptance.md). Đây vẫn là hướng dẫn tái thực hiện, không phải raw test log.
> **Source baseline đối chiếu:** `chivan2925/USBProtection` branch `vinhkhang`, HEAD `6923be001890bee9a303a8cee8e7dcc2cf2db180` (09/11/2026). Filename giữ SHA cũ để tránh gãy liên kết.
> **Hostname cố định:** `https://usbprotection.lab` — SAN của server cert **bắt buộc** chứa `DNS:usbprotection.lab`.
> **Tài liệu E2E trên `vinhkhang`:** [`docs/USBShield_Ubuntu24_Minimal_Core_E2E_vinhkhang_f446c04_20261107.md`](https://github.com/chivan2925/USBProtection/blob/4d2216c7dd277c7ef4af1962e0b04638e3971d09/docs/USBShield_Ubuntu24_Minimal_Core_E2E_vinhkhang_f446c04_20261107.md). File E2E giữ tên lịch sử `f446c04` nhưng **nội dung đã đồng bộ** với HEAD và verifier hiện tại trong cùng package. Chạy từ checkout đã giải nén các bản sửa docs/scripts; không sử dụng script apply fix.
> **Topology giữ nguyên:** Windows 11 host chạy PostgreSQL, Spring Boot JAR `prod` trên `127.0.0.1:18080` và Nginx TLS proxy tại cùng host; Ubuntu A/B chạy Agent `.deb` + USBGuard. **Không đưa proxy sang VM A.**
> **Trạng thái:** Windows verifier PASS; Nginx TLS + Admin browser + A/B ACL + blocked bypass **PASS theo owner**; Minimal Core USB Kingston C1–C5 **PASS theo owner**. Extended tests **NOT EXECUTED**. Luồng vẫn là **HTTPS Client → Nginx, HTTP loopback Nginx → Spring Boot**.

## 1. Checklist và nguyên tắc bảo mật

Cần có quyền quản trị Windows, VMware networking đã cấu hình, PostgreSQL và JAR phía Server, và quyền `sudo` ở Ubuntu A/B. **OpenSSL CLI** và **Nginx Windows binary** phải được cài riêng trên Windows; repository chỉ chứa Nginx **template**, không tự cài Nginx/CA.

- HTTPS được terminate tại Nginx `443`; Nginx → JAR là **HTTP loopback nội bộ** `127.0.0.1:18080`, không qua LAN. Không mở `18080` hoặc PostgreSQL `5432` từ Ubuntu VM.
- Ubuntu A/B chỉ được gọi `/api/agent/**`. Admin Browser có quyền đi tới Web và `/api/admin/**`, nhưng vẫn phải đăng nhập/đáp ứng CSRF; các Agent API vẫn cần Agent credentials.
- **IP ACL bắt buộc dựa trên IP nguồn thật** do Nginx thấy. `ADMIN_IP` không được bao phủ A/B; không cho chung subnet `/24` nếu có Client trong đó. Nếu NAT/proxy làm nguồn Admin và Client xuất hiện cùng IP, **STOP** và sửa topology; không dựa vào `X-Forwarded-For` do Client tự cung cấp.
- CA dùng trong tài liệu là **CA riêng của lab**. Chỉ chuyển **public CA certificate** tới Ubuntu; không chia sẻ CA private key hoặc server private key. Không sử dụng `curl -k`, SSL trust-all, HTTP fallback hoặc bypass cert warning.
- Hostname `.lab` là DNS nội bộ do chính operator ánh xạ, **không phải DNS public đã được cấp sẵn**. Phải cài `hosts` hoặc có DNS nội bộ cho cả Windows và Ubuntu.

## 2. Khảo sát địa chỉ, thiết kế đường đi (trước khi cài TLS)

Trên Windows PowerShell:

```powershell
Get-NetIPAddress -AddressFamily IPv4 | Select-Object InterfaceAlias,IPAddress,PrefixLength
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
  Where-Object LocalPort -in 443,18080,5432 |
  Select-Object LocalAddress,LocalPort,OwningProcess
```

Trên **từng** Ubuntu:

```bash
ip -br -4 addr
ip -4 route
hostnamectl --static
cat /etc/machine-id
```

Ghi riêng trong private lab note: `<WINDOWS_PROXY_IP>`, `<ADMIN_SOURCE_IP>` (có thể là Windows host nếu browser chạy cùng máy), `<UBUNTU_A_IP>`, `<UBUNTU_B_IP>`. Lab owner **đã chạy thành công với một NIC NAT VMnet8 mỗi Ubuntu** (A `192.168.23.133`, B `.141`, Windows VMnet8 `.1`), IP nguồn riêng đúng trên Nginx và ACL PASS. Không bắt buộc thêm Host-only khi NAT không gộp source IP; giữ console VMware khi USBGuard hoạt động. Giữ console VMware để không mất đường cứu hộ khi USBGuard hoạt động.

**Điểm dừng:** nếu Windows không route được từ A/B, hoặc Nginx sẽ thấy nguồn A/B giống nguồn Admin do NAT, chưa thể thiết lập IP ACL đúng. Không dùng một `CLIENT_CIDR` rộng bao trùm Admin; đặt hai `allow <UBUNTU_*_IP>/32` riêng.

## 3. Cấp chứng chỉ TLS cho `usbprotection.lab` bằng CA nội bộ (Windows)

Các lệnh dưới đây dùng PowerShell **và `openssl.exe` 1.1.1+/3.x có trong PATH**. Nếu chưa có CLI thì cài OpenSSL từ nguồn đáng tin cậy, xác thực executable trước khi dùng; **không** mặc định Windows hay Git có `openssl`.

```powershell
openssl version
$CaDir = 'C:\usbshield-lab-ca'                # chỉ operator quản lý; ngoài repo
$CertDir = 'C:\nginx\conf\certs'            # Nginx đọc server private key
New-Item -ItemType Directory -Force -Path $CaDir, $CertDir | Out-Null

# 1) Tạo CA root PRIVATE và certificate CA PUBLIC. Chỉ dùng trong lab.
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$CaDir\lab-ca.key"
openssl req -x509 -new -sha256 -days 365 -key "$CaDir\lab-ca.key" `
  -out "$CaDir\lab-ca.crt" -subj '/CN=USBProtection Lab CA' `
  -addext 'basicConstraints=critical,CA:TRUE' `
  -addext 'keyUsage=critical,keyCertSign,cRLSign'

# 2) Tạo khóa server PRIVATE và certificate signing request.
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$CertDir\usbprotection.lab.key"
openssl req -new -key "$CertDir\usbprotection.lab.key" `
  -out "$CertDir\usbprotection.lab.csr" -subj '/CN=usbprotection.lab'

# 3) SAN phải là DNS:usbprotection.lab; EKU là TLS Web Server Authentication.
@'
subjectAltName=DNS:usbprotection.lab
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
'@ | Set-Content -Encoding ascii -Path "$CertDir\usbprotection.lab.ext"

openssl x509 -req -sha256 -days 180 `
  -in "$CertDir\usbprotection.lab.csr" `
  -CA "$CaDir\lab-ca.crt" -CAkey "$CaDir\lab-ca.key" -CAcreateserial `
  -out "$CertDir\usbprotection.lab.crt" -extfile "$CertDir\usbprotection.lab.ext"

# 4) Kiểm chứng CA chain, mục đích server TLS và hostname tự động.
openssl verify -CAfile "$CaDir\lab-ca.crt" -purpose sslserver `
  -verify_hostname usbprotection.lab "$CertDir\usbprotection.lab.crt"
if ($LASTEXITCODE -ne 0) { throw "TLS certificate verification failed" }
openssl x509 -in "$CertDir\usbprotection.lab.crt" -noout -subject -issuer -dates -ext subjectAltName
if ($LASTEXITCODE -ne 0) { throw "Cannot inspect TLS certificate" }
```

**Expected:** `...: OK`, xác thực hostname `usbprotection.lab` và mục đích `sslserver` PASS; kết quả SAN chứa `DNS:usbprotection.lab`, chứng chỉ chưa hết hạn. File `lab-ca.key` là khóa **có thể ký mọi chứng chỉ được máy đã trust CA chấp nhận**: bảo vệ bằng NTFS ACL chỉ cho operator đáng tin cậy, không commit, không bỏ trong folder chia sẻ. File `usbprotection.lab.key` chỉ cho Nginx process/operator được đọc. Nếu không biết rõ Nginx chạy dưới tài khoản nào, không khóa ACL làm mất quyền đọc trước khi kiểm `nginx -t`. Không tắt certificate verification để sửa nhanh lỗi quyền/chứng chỉ.

Không bắt buộc dùng CA tự tạo nếu lab đã có CA nội bộ thực và server cert SAN đúng; khi đó dùng certificate/private key được cấp sẵn và **CA public tương ứng**, không trộn hai chain.

## 4. Phân giải `usbprotection.lab` đến **Windows HTTPS proxy**

Phải điền đúng `<WINDOWS_PROXY_IP>` từ Phase 2; không map `usbprotection.lab` thành `127.0.0.1` trên Ubuntu A/B.

**Windows host (PowerShell chạy as Administrator):** mở file `C:\Windows\System32\drivers\etc\hosts` bằng editor chạy quyền Administrator; thêm **một** dòng (không duplicate):

```text
<WINDOWS_PROXY_IP> usbprotection.lab
```

**Ubuntu A và B:** sửa `/etc/hosts` bằng `sudoedit /etc/hosts`, thêm đúng cùng mapping:

```text
<WINDOWS_PROXY_IP> usbprotection.lab
```

Đối chiếu:

```powershell
# Windows
[System.Net.Dns]::GetHostAddresses('usbprotection.lab') | Select-Object IPAddressToString
# Kiểm theo resolver của ứng dụng; Resolve-DnsName có thể kiểm DNS riêng.
Test-NetConnection usbprotection.lab -Port 443  # chỉ PASS khi Nginx được chạy ở bước kế tiếp
```

```bash
# Ubuntu A/B
getent ahostsv4 usbprotection.lab
# Phải ra WINDOWS_PROXY_IP, không phải 127.0.0.1 hoặc IP của A/B
```

Nếu có DNS nội bộ quản trị được, tạo record A thay cho hosts. Chỉ truy cập bằng hostname `usbprotection.lab`, không gõ HTTPS theo IP khi cert SAN chỉ có tên DNS.

## 5. Nginx trên **cùng Windows host** với Spring Boot

1. Cài Nginx Windows vào `C:\nginx` (ví dụ); package của repo **không** tạo Windows service tự động. Giữ Nginx run-loop/session có thể kiểm tra và điều khiển. Đừng cài Nginx trên A/B rồi proxy về `127.0.0.1`: đó là loopback của A/B.
2. Đảm bảo JAR `prod` được chạy bằng `.env.e2e.local` theo **mục 1.2–1.3** của Minimal Core E2E trên `vinhkhang`, với `USBSHIELD_BIND_ADDRESS=127.0.0.1`, `PORT=18080`, profile `prod` và PostgreSQL riêng. Nginx dùng HTTP loopback `127.0.0.1:18080` tới JAR; đây không phải HTTP LAN cho Agent.
3. Dựa trên [template nguồn `packaging/nginx/usbshield-https-acl.conf.example` ở HEAD `vinhkhang`](https://github.com/chivan2925/USBProtection/blob/4d2216c7dd277c7ef4af1962e0b04638e3971d09/packaging/nginx/usbshield-https-acl.conf.example), tạo **một** file site `C:\nginx\conf\sites\usbprotection.lab.conf`. Ví dụ bên dưới **không dùng được nguyên xi**: bắt buộc thay ba IP placeholder bằng IPv4 thật `/32`:

```nginx
server {
    listen 443 ssl;
    # Chỉ bật IPv6 nếu có thể allowlist đúng IPv6 source IP của Admin/A/B.
    # listen [::]:443 ssl;

    server_name usbprotection.lab;
    ssl_certificate     C:/nginx/conf/certs/usbprotection.lab.crt;
    ssl_certificate_key C:/nginx/conf/certs/usbprotection.lab.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    server_tokens off;
    client_max_body_size 1m;

    location ^~ /api/agent/ {
        allow <UBUNTU_A_IP>/32;
        allow <UBUNTU_B_IP>/32;
        allow <ADMIN_SOURCE_IP>/32;
        # Nếu Admin browser chạy trên cùng host và Nginx thấy 127.0.0.1,
        # chỉ khi đó thêm dòng: allow 127.0.0.1/32;
        deny all;
        proxy_pass http://127.0.0.1:18080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-For $remote_addr;
    }

    location / {
        allow <ADMIN_SOURCE_IP>/32;
        # Nếu và chỉ nếu Nginx thấy Admin source 127.0.0.1:
        # allow 127.0.0.1/32;
        deny all;
        proxy_pass http://127.0.0.1:18080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-For $remote_addr;
    }
}
```

4. Trong `C:\nginx\conf\nginx.conf`, bảo đảm có `include sites/usbprotection.lab.conf;` **bên trong `http { ... }`** (đường dẫn include tính từ `C:\nginx\conf`; **không** viết `conf/sites` để tránh thành `conf/conf/sites`); tạo thư mục `C:\nginx\conf\sites` nếu chưa có. Không thay cả file `nginx.conf` tùy tiện, không include site hai lần. Không để nguyên bất kỳ `<...>` hay `@@...@@` placeholder.
5. Trên Windows PowerShell:

```powershell
Push-Location 'C:\nginx'
.\nginx.exe -t
# Chỉ nếu cấu hình PASS:
# Start-Process .\nginx.exe -WorkingDirectory 'C:\nginx'  # lần đầu
# .\nginx.exe -s reload                               # khi đang chạy
Pop-Location
Get-NetTCPConnection -State Listen | Where-Object LocalPort -eq 443 |
  Select-Object LocalAddress,LocalPort,OwningProcess
```

`nginx -t` chỉ chứng minh **syntax**, không chứng minh hostname/TLS trust và ACL chính xác. Nếu máy đã có Nginx/site khác bind `443`, kiểm tra tránh xung đột `server_name`, default server và cấu hình không mong muốn. Đảm bảo `nginx -T`/file cấu hình cuối cùng khớp thiết kế; không có fallback/HTTP listener làm lộ Admin Web.

## 6. Windows Firewall cho TCP/443 và đóng đường bypass backend

Mở PowerShell **as Administrator**, kiểm tất cả rule inbound 443/18080/5432 hiện hữu trước khi thêm rule. Quy tắc bên dưới là ví dụ có điều kiện, không phải lệnh phải chạy bất chấp topology:

```powershell
$AdminIP = '<ADMIN_SOURCE_IP>'
$ClientA = '<UBUNTU_A_IP>'
$ClientB = '<UBUNTU_B_IP>'
New-NetFirewallRule -DisplayName 'USBShield LAB HTTPS 443 allowlisted' `
  -Direction Inbound -Action Allow -Protocol TCP -LocalPort 443 `
  -RemoteAddress $AdminIP,$ClientA,$ClientB
```

- Nếu Admin browser chạy ngay trên Windows Server, thử từ localhost và chỉ mở từ A/B qua LAN khi cần; Windows loopback traffic không nhất thiết cần inbound rule.
- Windows Firewall phải chặn **external** TCP/18080 và 5432; loopback 18080 và PostgreSQL local vẫn hoạt động. Đừng tạo rule `Allow Any` cho toàn LAN. Nếu đã có rule TCP/443 rộng hơn, rule mới **không tự vô hiệu hóa** rule cũ; rà soát effective policy. Giữ Windows management/VMware console hoạt động.
- Nếu thiết lập chỉ dùng IPv4, rà soát **IPv6** để tránh một đường truy cập không được ACL kiểm soát. Nginx site mẫu cố ý không bật `listen [::]:443` cho lab IPv4-only.

## 7. Trust CA và cấu hình HTTPS trên Ubuntu A/B **trước khi cài `.deb`**

### 7.1. Chuyển **chỉ public CA** từ Windows tới từng Ubuntu

File cần chuyển: `C:\usbshield-lab-ca\lab-ca.crt`. **Không chuyển** `lab-ca.key` hoặc `usbprotection.lab.key`. Dùng SCP/SFTP/VMware shared folder tạm hoặc kênh nội bộ được kiểm soát. Trên Ubuntu A/B, sau khi có file public CA tại đường dẫn riêng của user, cài vào vị trí root-managed:

```bash
sudo mkdir -p /etc/usbshield-agent
sudo install -o root -g root -m 0644 "$HOME/lab-ca.crt" \
  /etc/usbshield-agent/usbshield-ca.pem
sudo test -s /etc/usbshield-agent/usbshield-ca.pem
```

Kiểm CA qua curl (không cần Agent đã chạy):

```bash
getent ahostsv4 usbprotection.lab
curl --cacert /etc/usbshield-agent/usbshield-ca.pem -sS -o /dev/null \
  -w '%{http_code}\n' https://usbprotection.lab/api/agent/policy
# Expected từ A/B: HTTP 401 (proxy cho qua Agent path; Spring từ chối thiếu Agent auth)
```

Nếu trả 403 thì kiểm Nginx source-IP ACL (hoặc nhầm Spring 403), kiểm access/error log ở Windows. Nếu TLS verification fail, kiểm DNS, SAN, CA đúng issuer, ngày giờ hệ thống; **không** dùng `-k` để tiếp tục. Public CA PEM này phục vụ `curl` và Agent Java qua cấu hình bên dưới.

### 7.2. Chuẩn bị `agent.env` root-owned trước `apt install` / `apt --reinstall`

`packaging/agent-deb/DEBIAN/postinst` sẽ khởi động Agent ngay, vì thế **đặt HTTPS URL/CA/enrollment secret trước**. Dùng tài khoản operator có `sudo` trên từng VM; không đọc/in token vào log:

```bash
sudo mkdir -p /etc/usbshield-agent
if ! sudo test -e /etc/usbshield-agent/agent.env; then
    sudo install -o root -g root -m 0600 /dev/null /etc/usbshield-agent/agent.env
fi
sudo chown root:root /etc/usbshield-agent/agent.env
sudo chmod 0600 /etc/usbshield-agent/agent.env
sudoedit /etc/usbshield-agent/agent.env
```

Các biến cần có, **điền enrollment secret thật tại chỗ** (giữ giống `USBSHIELD_ENROLLMENT_TOKEN` trên server mới), không đưa vào repo/chat:

```dotenv
USBSHIELD_SERVER_URL=https://usbprotection.lab
USBSHIELD_ENROLLMENT_TOKEN=<NEW_STABLE_SECRET_FROM_ADMIN_SERVER>
USBSHIELD_TLS_CA_CERTIFICATE=/etc/usbshield-agent/usbshield-ca.pem
```

`agent.env` là file `KEY=value` đơn giản; không có spaces quanh `=`, không quote kiểu shell, không chép secret vào screenshot. Chỉ public CA `.pem` được đưa lên Ubuntu; **không** cài server private key. Đối với VM đã cài Agent từ DB cũ: việc đổi `agent.env` **không tự đăng ký endpoint mới**; xem **mục 1.4** của Minimal Core E2E trên `vinhkhang` để reset Agent test state **trước enrollment mới**, chỉ khi dữ liệu cũ thực sự là dữ liệu lab có thể bỏ, hoặc dùng VM snapshot pre-enrollment. Không tái sử dụng `endpoint.json`/Agent token từ database đã bị bỏ.

```bash
sudo stat -c '%U:%G %a %n' /etc/usbshield-agent/agent.env
sudo test -r /etc/usbshield-agent/agent.env
# Chỉ sau khi đã cài package và có user usbshield:
sudo -u usbshield test -r /etc/usbshield-agent/usbshield-ca.pem && echo 'Agent can read public CA'
```

`agent.env` cần `root:root 0600` để systemd đọc, CA public cert cần đọc được bởi service `usbshield`. Nếu dùng CA bên ngoài có sẵn trong JVM trust store, có thể không cần `USBSHIELD_TLS_CA_CERTIFICATE`; lab này **đang dùng CA riêng**, vì vậy nên khai báo rõ.

## 8. Xác nhận gate HTTPS/ACL (KHÔNG thay thế USB E2E)

Đầu tiên, start JAR `prod` với PostgreSQL sạch (theo Phase 2 E2E), sau đó start/reload Nginx. Trust CA cho **Admin browser trên Windows**: chỉ nhập **CA public cert** `C:\usbshield-lab-ca\lab-ca.crt` vào `Cert:\CurrentUser\Root` khi được operator lab chấp thuận. Ví dụ PowerShell (CurrentUser trust store):

```powershell
Import-Certificate -FilePath 'C:\usbshield-lab-ca\lab-ca.crt' `
  -CertStoreLocation 'Cert:\CurrentUser\Root'
```

**Lưu ý:** tin cậy CA root là cấp quyền tin cậy mạnh, chỉ sử dụng trong môi trường lab cô lập; sau lab có thể gỡ certificate theo thumbprint đã ghi lại. Không import **CA private key**. Một số browser có trust store riêng; cần xác nhận trong browser thực, không bỏ qua warning.

### 8.1. Kiểm từ Admin Windows (IP thực tế nằm trong allowlist)

- Mở `https://usbprotection.lab/` trên browser Admin. Certificate chain/SAN phải hợp lệ, không có cảnh báo; login bằng tài khoản Admin từ DB mới, không dùng `?demo=1`.
- Trước login, `/api/auth/csrf` → 200, `/api/admin/endpoints/page` → 401. Sau login, Admin Web hoạt động và phân trang Endpoints/Whitelist được.
- Kiểm Nginx logs (private) xác nhận IP nguồn Admin được allowlist. Nếu truy cập từ chính Windows host qua `usbprotection.lab`, remote IP mà Nginx thấy có thể là loopback hoặc IP Windows host; chỉ thêm **đúng** IP thực tế đó vào Admin allowlist.

### 8.2. Kiểm từ **Ubuntu Client thật** A và B

```bash
CA=/etc/usbshield-agent/usbshield-ca.pem
BASE=https://usbprotection.lab
for path in / /api/auth/csrf /api/admin/endpoints/page /api/agent/policy; do
  printf '%s ' "$path"
  curl --cacert "$CA" --silent --show-error --output /dev/null \
    --write-out '%{http_code}\n' "$BASE$path"
done
# Expected: 403,403,403,401 (theo thứ tự). Không curl -k.

# Nếu đã copy riêng script từ repo/package lên Ubuntu A/B (không cần clone repo):
bash "$HOME/verify-phase3-https-acl.sh" client "$BASE" "$CA"
```

Từ Admin host có Bash có thể dùng cùng script với `admin https://usbprotection.lab <CA_CERT_PATH>`; trên Windows không có Bash thì kiểm bằng `curl.exe`/browser. **Chạy từ IP nguồn thật**: kiểm thành công trên chính Windows host không chứng minh rằng Client bị chặn đúng.

### 8.3. Kiểm đường bypass và Agent TLS thực

- Từ Ubuntu thử `http://<WINDOWS_PROXY_IP>:18080/` và TCP `5432`: **không được truy cập từ LAN**, mặc dù JAR và PostgreSQL phải hoạt động trên loopback Windows.
- `nginx.exe -t` PASS không đủ: nhìn status theo từng path và access log. `/api/agent/policy` 401 khi không có token **phải đến từ Spring Agent auth**, không phải proxy 403.
- Sau khi cấu hình A/B và cài `.deb` ở E2E Phase 3, Agent enrollment/heartbeat/policy phải đi qua **validated Java TLS** tới `usbprotection.lab`; `curl` PASS **không đảm bảo** trust của Java Agent đã PASS.
- Negative CA hoặc SAN sai chỉ thử trên disposable VM/snapshot và khôi phục cấu hình chính xác ngay sau đó; không giảm TLS security trên Agent vận hành.

## 9. Troubleshooting, STOP và bàn giao cho E2E

| Triệu chứng | Chẩn đoán ưu tiên | Hành động hợp lệ |
|---|---|---|
| `Could not resolve host` | `/etc/hosts`, Windows hosts, NIC, DNS A record | Sửa mapping `usbprotection.lab` đến proxy Windows |
| `PKIX path building failed` / `unknown CA` | Agent trỏ CA sai hoặc file CA không đọc được | Kiểm issuer, PEM, file permission, Java Agent logs |
| `certificate verify failed` / hostname mismatch | SAN không chứa `usbprotection.lab`, sai SNI/hostname | Cấp lại cert SAN đúng; không `-k` |
| `502 Bad Gateway` | Nginx kết nối tới `127.0.0.1:18080` thất bại | Kiểm Spring Boot JAR/prod/DB, `proxy_pass` và port |
| Admin bị `403` | Admin source IP thực tế chưa được allow | Xem access log, chỉ thêm đúng `/32` được xác minh |
| Client thấy Web (`200`) | ACL lọt, vhost/server block khác hoặc nguồn bị NAT gộp | **STOP**, sửa ACL/source-IP; không tiếp tục enrollment |
| Agent route từ Client `403` | Client source IP chưa allow; vhost sai | Đối chiếu Nginx log/route, không mở Web |
| Agent route `401` khi không có token | Kết quả mong đợi từ Spring | Tiếp tục kiểm Agent credential thật ở E2E |
| A/B có cùng source-IP Nginx với Admin | NAT làm ACL không phân biệt được | **STOP** và sửa mạng/VLAN/topology |
| Client truy cập `18080` hoặc `5432` | Backend/DB bypass proxy | **STOP**, sửa bind/firewall trước E2E |

**Gate hoàn tất** (chỉ ghi PASS sau khi tự chạy): CA+SAN hợp lệ, `https://usbprotection.lab` phân giải đúng trên Windows/A/B, Admin truy cập Web qua TLS, Client bị 403 với Web/Admin path, Agent path chuyển tới Spring auth, backend/DB không expose, và Agent Java dùng TLS hợp lệ. Ghi `PASS/FAIL/BLOCKED`, nguồn IP thật, thời điểm, lệnh, ảnh/log đã redact. **HTTPS riêng không đồng nghĩa USB E2E; tuy nhiên owner sau đó đã báo C1–C5 PASS ở đợt thực tế 09/11 — xem closeout.**

Minimal Core đã owner báo PASS; hướng dẫn tái chạy là [USBShield Ubuntu 24.04 Minimal Core E2E](USBShield_Ubuntu24_Minimal_Core_E2E_vinhkhang_f446c04_20261107.md): mục 1.2–1.4 chuẩn bị PostgreSQL, Admin Server và Agent A/B; sau đó checkpoints **C1–C5** (BLOCK → Allow A → reconnect ALLOW A → B BLOCK → Revoke A/reconnect BLOCK). Lần E2E cũ FAILED ở Phase 6 chưa có root cause được chứng minh; C1–C5 ngày 09/11 PASS theo owner không chứng minh mọi failure mode cũ đã được giải quyết. Kiểm thử `.deb` remove/upgrade, bao gồm cleanup managed Allow trong `prerm`, và Linux-only notifier socket vẫn chưa chạy trên Ubuntu.

## Source đối chiếu

- `packaging/nginx/usbshield-https-acl.conf.example`, `packaging/firewall/README.md`, `packaging/tests/verify-phase3-https-acl.sh` tại HEAD `vinhkhang` đã nêu ở đầu tài liệu.
- `admin-server/src/main/resources/application.yml`, `application-prod.yml`; `packaging/agent-deb/etc/usbshield-agent/application.yml`, `packaging/agent-deb/DEBIAN/postinst`; `docs/PHASE3_HTTPS_ACL_AND_PAGING.md` tại SHA đó.
- Chứng chỉ/cert/key mới tạo theo hướng dẫn là **local lab artifacts**, không phải file trong repo hoặc bằng chứng đã tạo thành công trên máy operator.

### Ghi chú lab sau triển khai (09/11)

- Windows `curl.exe` dùng Schannel có thể báo `curl (60) revocation status unknown` với CA lab không công bố CRL/OCSP. Owner đã kiểm `--cacert ... --ssl-revoke-best-effort` và nhận `200/401`; **không dùng `-k`**, tùy chọn này chỉ nới phần thông tin revocation không sẵn có, không tắt chain/hostname verification. Không mở rộng cách này thành quy trình TLS production.
- Trên Ubuntu, file CA `644 root:root` vẫn có thể gây `curl (77)` cho `usbdev` nếu thư mục `/etc/usbshield-agent` là `750 root:usbshield`. Owner chạy `sudo curl --cacert ...` để kiểm, không mở rộng quyền thư mục chứa secret. Agent service vẫn là `User=usbshield`; kiểm Java Agent riêng.
- Windows Nginx config site thực nằm `C:\nginx\conf\sites\usbprotection.lab.conf`, có listener IPv4; không thêm IPv6 listener khi chưa allowlist source IPv6.
- Để demo lại sau reboot chỉ cần PostgreSQL service đang chạy, Admin JAR `prod` khởi động bằng `.env.e2e.local`, Nginx process được start; không tạo lại CA/hosts/firewall. Nếu DHCP đổi IP A/B, cập nhật hai `/32` allowlist và firewall, sau đó retest ACL.