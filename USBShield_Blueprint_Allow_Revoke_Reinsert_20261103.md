# USBShield Ubuntu — Project Blueprint

> **Đề tài:** Xây dựng hệ thống ngăn chặn và quản lý USB Flash Disk trên Ubuntu  
> **Bối cảnh:** Đồ án 4 tuần / 5 thành viên; kế hoạch Week 1–4 bên dưới là lịch sử, không phải bảng tiến độ hiện tại.  
> **Admin Web:** React + Vite + TypeScript, source `frontend/`; production build tích hợp vào Spring Boot JAR.  
> **Backend:** Java 21 + Spring Boot, Admin Server có Web/API và Ubuntu Agent non-web.  
> **Ubuntu Client:** 24.04 LTS amd64; enforcement bằng USBGuard và quản lý lifecycle bằng systemd.  
> **Database:** PostgreSQL, Flyway V1/V2, Hibernate `ddl-auto=validate` (không còn H2 runtime).  
> **Phân phối:** Admin Server JAR; Ubuntu Agent `.deb` có tên phiên bản động và SHA-256/manifest.  
> **Đối chiếu source:** `chivan2925/USBProtection` `main` **`721f164f63533461b6c18b62e1607baa4ab1b731` (20/11/2026)**.  
> **Phạm vi kết quả:** Không coi việc cập nhật tài liệu hay kiểm tra source là một lượt chạy test mới.

## Trạng thái triển khai tại source 20/11/2026 — phần ưu tiên khi đọc blueprint

**Phân biệt bằng chứng:** (1) *Source fact* là điều đã đối chiếu với cây mã nguồn/config ở SHA trên; (2) *owner-reported* là kết quả báo cáo của người chạy lab ngày 09/11; (3) *deferred/not executed* chưa được chứng minh. Kết quả 09/11 không tự chuyển sang JAR/`.deb` được build từ HEAD 20/11. Để nghiệm thu một binary, phải ghi cả commit, working-tree state, JAR/DEB SHA-256, cấu hình, thời điểm và log thực tế.

| Hạng mục | Thiết kế/source hiện tại | Ranh giới kiểm chứng |
|---|---|---|
| Enforcement | USBGuard local, non-storage base allow và `ImplicitPolicyTarget=block`; whitelist per endpoint; Agent pull/reconcile rồi ACK | ACK và heartbeat là báo cáo phần mềm, không phải chứng cứ USB đang cắm bị đổi trạng thái |
| Backend & DB | Spring Boot, PostgreSQL `usbshield`; Flyway V1/V2 tạo 5 bảng, Hibernate `validate` | Database `usbshield_test` và `usbshield_browser_test` dùng riêng; không chạy destructive test vào app DB |
| Authentication | Admin session/cookie + CSRF; Agent enrollment token và endpoint-bound Agent token; reset mật khẩu Admin bằng maintenance mode không mở HTTP | Không có mật khẩu production mặc định; không trao Agent token cho trình duyệt |
| Network | Nginx HTTPS TLS/443 và ACL theo source IP/route; backend HTTP bind loopback; `/api/agent/**` tách Web/Auth/Admin | HTTPS/ACL ở lab 09/11 **PASS theo owner**; không có rate limiting theo quyết định owner |
| Frontend | Login, Dashboard, Endpoints, Devices, Whitelist, Event Logs, Settings; các list chính có filter/phân trang tại backend | Trang Blocked độc lập đã bỏ; dùng lọc Blocked của Devices; dữ liệu demo tách live |
| Client | Java Agent `live`, systemd service, local event spool/quarantine, USBGuard health telemetry, desktop notifier best-effort qua user session | GUI/native notifier IPC và các negative/offline/reboot cases nâng cao chưa được xác nhận đầy đủ |
| Release | `admin-server/build-release.ps1`, `packaging/tests/verify-windows.ps1`, `scripts/with-env.ps1`; `.deb` build bằng `packaging/scripts/build-deb.{ps1,sh}` | Build thành công ≠ full Windows verifier PASS ≠ Ubuntu physical E2E PASS |

**Bằng chứng lab 09/11/2026:** Windows verifier và HTTPS/ACL được báo PASS theo log/báo cáo owner; Ubuntu **Minimal Core P0 + C1–C5** (A BLOCK → Allow A + ACK + rút/cắm lại ALLOW → B BLOCK → Revoke A + ACK + rút/cắm lại BLOCK) **PASS theo báo cáo owner**, thiếu raw log từng checkpoint để kiểm chứng độc lập. **Ubuntu extended acceptance** (`.deb` upgrade/remove, reboot, offline recovery, negative TLS, notifier IPC, stress/security) **DEFERRED BY OWNER / NOT EXECUTED**. Không gọi đây là full production release acceptance.

**Tài liệu vận hành/source-of-truth:** [RUN_GUIDE](docs/RUN_GUIDE.md) · [ADMIN_SERVER_RELEASE](docs/ADMIN_SERVER_RELEASE.md) · [ARCHITECTURE](docs/ARCHITECTURE.md) · [API v0.2 frozen](docs/API.md) + [Phase 1–3 addendum](docs/API_PHASE1_TO_PHASE3_ADDENDUM.md) · [ADMIN_GUIDE](docs/ADMIN_GUIDE.md) · [DOCUMENT_INDEX](docs/DOCUMENT_INDEX.md). [Trạng thái 09/11](docs/IMPLEMENTATION_STATUS_20261109.md) và [owner acceptance](docs/qa/2026-11-09-ubuntu-minimal-core-owner-acceptance.md) là snapshot/evidence lịch sử.

## Lịch sử quyết định — giữ nguyên thời điểm

- **Week 1:** Đề bài gốc và contract khởi đầu; không hồi tố yêu cầu desktop notification.
- **Week 2:** M2 tiếp quản core USBGuard/session từ M3; bổ sung desktop notification; Agent–Server wire contract v0.2. Phần mô tả tuần ở cuối tài liệu là **kế hoạch thời điểm đó**.
- **03–04/11:** Chốt cách vận hành demo, đổi H2 sang PostgreSQL, Flyway V1/V2, bổ sung TLS/ACL, HASH policy, health và pagination. Các ID finding cũ **khác** ID của review 07–09/11.
- **09/11:** Owner báo Windows, HTTPS/ACL và Ubuntu Minimal Core P0+C1–C5 PASS ở lab, nhưng hoãn nghiệm thu Ubuntu mở rộng.
- **20/11:** Trên `main`, dùng scripts build/release và Windows verifier mới; các script `admin-server/verify-week1.ps1`, `verify-week2.ps1`, `verify-week3.ps1` không còn. Không đổi API wire v0.2 chỉ vì cập nhật blueprint.

---

## 0. Mục đích của tài liệu này

Tài liệu này là **blueprint nội bộ của nhóm**, dùng để tất cả thành viên hiểu thống nhất:

- Đề tài thực sự phải giải quyết vấn đề gì.
- Làm đến mức nào thì được xem là **đủ yêu cầu**.
- Kiến trúc Client Agent ↔ Admin Server ↔ Admin Web hoạt động ra sao.
- Whitelist được quản lý **theo từng máy Ubuntu/endpoint**, không phải một whitelist global.
- Khi có USB event, hệ thống phải ghi nhận **máy nào + tài khoản Ubuntu nào đang active + USB nào + quyết định ALLOW/BLOCK**.
- Mỗi thành viên chịu trách nhiệm phần nào.
- Từng tuần phải tạo ra kết quả gì.
- Cách cài client bằng `.deb`, chạy Admin Server và dùng Admin Web.
- Tài liệu phải có để đáp ứng yêu cầu giảng viên: sản phẩm, code, Developer Guide, User/Admin Guide và Test/Demo.

> **Nguyên tắc quan trọng nhất:** USBShield không phải website CRUD. Core là USB Flash Disk lạ phải thực sự bị Ubuntu chặn, protection phải tự chạy, normal user không được bypass, và Admin phải nhìn thấy được **endpoint + user Ubuntu đang active tại thời điểm USB được cắm**.

---


# 1. Bối cảnh và yêu cầu gốc

## 1.1. Yêu cầu ban đầu của giảng viên

**Tên ban đầu:**

> Ngăn chặn người dùng cắm USB Flash Disk vào máy tính Ubuntu.

**Nội dung:**

> Xây dựng ứng dụng ngăn người dùng cắm USB Flash Disk vào máy hệ điều hành Ubuntu, ứng dụng được chạy như dịch vụ không tắt được.

**Tiêu chí giảng viên quan tâm:**

1. Sản phẩm.
2. Code.
3. Công nghệ/kỹ thuật sử dụng phải có lập luận hợp lý.
4. Hướng dẫn code cho developer.
5. Hướng dẫn sử dụng cho người dùng.

## 1.2. Yêu cầu bổ sung mới của giảng viên

> Khi người dùng cắm USB vào máy Ubuntu, Admin phải nhận được thông tin người dùng nào đã cắm USB.

Nhóm diễn giải yêu cầu này theo cách kỹ thuật có thể kiểm chứng:

> USBShield ghi nhận **tài khoản Ubuntu có session local đang active tại thời điểm USB event xảy ra**.

Ví dụ:

```text
Endpoint: LAB-PC-07
Active Ubuntu user: student01
USB: Kingston DataTraveler
Decision: BLOCKED
Time: 10:25:13
```

### Giới hạn phải nói đúng

USBShield có thể xác định **tài khoản/session đang active**, không thể chứng minh bằng phần mềm rằng chính con người đang đăng nhập đó là người đã dùng tay cắm USB.

## 1.3. Bối cảnh sử dụng được chốt

Đề tài được triển khai theo mô hình tổ chức nhỏ/LAN, ví dụ:

- phòng máy trường học;
- công ty;
- bệnh viện;
- phòng lab.

Mỗi máy Ubuntu cần bảo vệ là một **endpoint/client**. Admin quản lý tập trung bằng Web Console.

---


## 1.4. Chốt phạm vi demo và vận hành (03/11/2026)

- **Mục tiêu là đồ án seminar/demonstration**, không phải một sản phẩm đã được triển khai thật tại trường học, bệnh viện hoặc công ty. Các tổ chức nêu trong đề là **bối cảnh áp dụng minh họa**.
- Giữ kiến trúc USBGuard + systemd Agent + một Spring Boot Admin Server + React. Không bổ sung microservices, Kafka, Redis, Kubernetes hoặc hệ quản trị nhiều server chỉ vì tính "enterprise". Chấp nhận global USB catalog lock và một PostgreSQL database trên một server demo; không đưa ra cam kết throughput nhiều endpoint.
- **Outbox** giữ giới hạn bộ nhớ đệm/bằng chứng và báo lỗi khi không bền vững; cache quota cho single-writer Agent và quét lại khi thư mục thay đổi/ghi lỗi, không xây dựng queue engine mới. Operator không sửa in-place các file spool khi Agent đang chạy; cần dừng/khởi động lại sau can thiệp.
- **Frontend** ưu tiên API-backed request tests và loại bỏ request trùng. Dashboard dùng tổng hợp backend; danh sách thiết bị dùng API phân trang; không tải toàn bộ lịch sử USB để dựng UI.
- **Admin Server không bắt buộc Docker.** Một JAR Spring Boot được build với `-Pwith-frontend` là đủ để chạy React và API cùng origin trong LAN. PostgreSQL quản lý dữ liệu ngoài JAR/repo (không embedded); dùng HTTPS reverse proxy nếu truy cập Admin Web từ máy khác. Xem [Hướng dẫn Admin Server LAN](docs/RUN_GUIDE.md).
- **Client Agent** cài bằng gói `.deb` được build và kiểm chứng, không mặc định GitHub URL chứa artifact sẵn. `server-url` trỏ về Admin Server trong LAN/VPN, không dùng `localhost` từ Client.
- **Dịch vụ "không tắt được"** giới hạn với người dùng Ubuntu thông thường **không có sudo/root và không có quyền sửa USBGuard IPC/policy**. Root và quản trị viên hệ điều hành vẫn có quyền dừng/sửa dịch vụ.
- **Chốt Allow/Revoke theo lần cắm kế tiếp:** Admin thay đổi whitelist của đúng endpoint → Agent kéo và áp dụng local USBGuard policy → xác nhận policy version đã áp dụng (ACK) → người dùng **rút USB ra rồi cắm lại** để USBGuard đánh giá theo policy mới. **Không triển khai** `allow-device`/`block-device` tức thời cho USB vẫn đang cắm trong phạm vi demo. ACK xác nhận áp dụng rule, **không chứng minh** trạng thái của thiết bị đang kết nối đã đổi.
- **Ubuntu Minimal Core P0+C1–C5 PASS theo báo cáo owner ngày 09/11**, không phải độc lập xác minh cho HEAD 20/11. **Ubuntu extended/hardening E2E vẫn DEFERRED**; chưa được phép đánh dấu full release PASS. HTTPS/TLS/ACL vẫn là điều kiện thực tế qua LAN.

---

# 2. Tên đề tài nhóm chốt

## Xây dựng hệ thống ngăn chặn và quản lý USB Flash Disk trên hệ điều hành Ubuntu

Tên sản phẩm nội bộ có thể dùng:

**USBShield Ubuntu**

Tên này không làm thay đổi bài toán giảng viên giao. Phần “quản lý” được bổ sung để sản phẩm có giao diện quản trị, whitelist và lịch sử hoạt động thay vì chỉ có một script chặn USB.

---

# 3. Phát biểu bài toán

USB Flash Disk có thể được dùng để đưa dữ liệu không kiểm soát vào máy hoặc sao chép dữ liệu ra ngoài. Trong môi trường trường học/công ty/bệnh viện, người dùng cuối không nên tự ý sử dụng USB Mass Storage chưa được cấp phép.

USBShield cần giải quyết đồng thời 5 bài toán:

1. **Enforcement tại endpoint:** unknown USB Mass Storage bị BLOCK thật bằng USBGuard.
2. **Không phá peripheral:** keyboard/mouse và USB không phải Mass Storage không bị ảnh hưởng.
3. **User attribution:** khi có USB event, Agent xác định tài khoản Ubuntu đang active.
4. **Central administration:** Admin Web xem event của các endpoint và quản lý whitelist **riêng cho từng endpoint**.
5. **Client desktop feedback (bắt đầu Week 2):** Người dùng Ubuntu có session đồ họa đang active nhận desktop notification khi **USB Mass Storage bị BLOCK**; notification không thay thế enforcement, không đòi Admin Web hoặc network.

Policy cơ bản trên mỗi endpoint:

```text
USB Mass Storage nằm trong whitelist của ENDPOINT ĐÓ → ALLOW
Unknown Mass Storage trên endpoint đó                 → BLOCK
Non-Mass-Storage USB                                  → ALLOW
```

Ví dụ cùng một USB:

```text
LAB-PC-01 → SanDisk A → ALLOW
LAB-PC-02 → SanDisk A → BLOCK
```

Điều này là chủ ý: **không có global whitelist trong MVP**.

---


# 4. Mục tiêu sản phẩm

## 4.1. Flow khi người dùng cắm USB

```text
Ubuntu Client boot
        ↓
USBGuard + USBShield Agent tự khởi động
        ↓
student01 đăng nhập Ubuntu
        ↓
student01 cắm USB Flash Disk lạ
        ↓
USBGuard áp local policy
        ↓
USB bị BLOCK ngay tại client
        ↓
Agent nhận USB event
        ↓
Agent lấy active Linux session/user
        ↓
Agent tạo event:
endpoint + username + USB + decision + timestamp
        ├── Nếu INSERT + Mass Storage + BLOCKED
        │   → phát notification tới graphical session đang active
        │     "USB Mass Storage chưa được cấp phép. Thiết bị đã bị chặn."
        │     (bắt đầu Week 2; không làm chậm chặn USB/gửi event)
        ↓
POST event về Admin Server
        ↓
Admin Web hiển thị:
LAB-PC-01 | student01 | Kingston | BLOCKED
```

## 4.2. Flow khi Admin whitelist cho một máy

```text
Admin đăng nhập USBShield Web
        ↓
Endpoints → LAB-PC-01
        ↓
chọn Kingston → Allow on this endpoint
        ↓
Admin Server lưu whitelist cho LAB-PC-01
        ↓
policyVersion của LAB-PC-01 tăng
        ↓
USBShield Agent LAB-PC-01 đồng bộ policy
        ↓
Agent cập nhật USBGuard local policy và gửi ACK thành công
        ↓
Người dùng rút Kingston khỏi LAB-PC-01, sau đó cắm lại
        ↓
USBGuard đánh giá lần cắm mới theo policy đã áp dụng
        ↓
LAB-PC-01: Kingston → ALLOW
LAB-PC-02: Kingston → vẫn BLOCK
```

**Revoke:** Admin Revoke Kingston trên LAB-PC-01 → Agent đồng bộ rule và gửi ACK → người dùng rút Kingston rồi cắm lại trên LAB-PC-01 → USBGuard `BLOCK` ở lần cắm mới. Không yêu cầu rút/chặn tức thời thiết bị đang được sử dụng. Nếu chưa thấy policy version áp dụng, **không** coi Allow/Revoke đã có hiệu lực trên client.

## 4.3. Khi Admin Server tạm thời DOWN

```text
Admin Server DOWN
        ↓
USBGuard trên client vẫn chạy
        ↓
last-known local policy vẫn enforce
        ↓
unknown USB không tự nhiên được ALLOW
```

Agent hiện có disk outbox/retry/quarantine và cơ chế giới hạn dung lượng. Khi API/network lỗi, bảo toàn event trong giới hạn đã cấu hình và xử lý theo phân loại lỗi; không bảo đảm gửi thành công tuyệt đối nếu mất đĩa/đầy quota/hỏng dữ liệu. USBGuard local enforcement không được phụ thuộc thành công của HTTP hay desktop notification.

---


# 5. Giới hạn bảo mật cần nói rõ

## 5.1. “Dịch vụ không tắt được”

Diễn giải đúng:

> Normal Ubuntu user, không có root/sudo, không được phép dừng USBGuard/USBShield Agent, sửa local policy hoặc thay đổi cấu hình protection.

Không tuyên bố:

> Root cũng không thể tắt.

Root/sudo vẫn là chủ sở hữu cuối cùng của máy Linux.

## 5.2. Các identity trong hệ thống

### Client Ubuntu user

Ví dụ `student01`, `employee07`.

- không có USBShield account;
- chỉ sử dụng máy Ubuntu;
- Agent ghi nhận username/session khi USB event xảy ra.

### USBShield Admin

- có account đăng nhập **Admin Web Console**;
- xem endpoints/events;
- Allow/Revoke USB **theo từng endpoint**;
- đây là application account trên Admin Server.

### Linux system user `usbshield`

- identity kỹ thuật chạy Client Agent;
- không phải account người dùng;
- chỉ có privilege tối thiểu cần thiết.

### Endpoint identity

Mỗi Client Agent phải có identity/token riêng để gửi heartbeat/event và lấy policy của chính endpoint đó.

## 5.3. User attribution

USBShield ghi nhận **active Ubuntu session** tại thời điểm USB event. Trường dữ liệu nên gồm:

```text
linux_username
linux_uid
session_id
session_type
seat
```

MVP có thể lấy thông tin qua `systemd-logind/loginctl`. Production-quality implementation có thể chuyển sang D-Bus API của logind sau.

---


# 6. Scope chốt của đồ án — 17 hạng mục (yêu cầu; trạng thái triển khai xem phần đầu)

## 1. Nhận diện USB Flash Disk / Mass Storage

Agent phải đọc được tối thiểu:

- USBGuard runtime ID;
- VID/PID;
- name;
- serial nếu có;
- USBGuard hash;
- interface list;
- state.

Mass Storage class dùng class code `08`.

## 2. Mặc định block USB Flash Disk chưa được phép

```text
Whitelisted Mass Storage trên endpoint → ALLOW
Unknown Mass Storage                    → BLOCK
Non-Mass-Storage USB                    → ALLOW
```

## 3. Không ảnh hưởng keyboard/mouse USB

Test physical mouse/keyboard nếu có hardware thật.

## 4. Allow/Revoke một USB cụ thể THEO TỪNG ENDPOINT

Admin phải chọn endpoint trước khi thay đổi whitelist.

```text
Endpoint A allow USB X
≠
Endpoint B allow USB X
```

**Quy trình sử dụng của demo:** Admin Allow/Revoke → đợi Agent áp dụng rule (theo `appliedPolicyVersion`/ACK) → rút USB và cắm lại trên endpoint đó → kiểm tra quyết định mới. Không yêu cầu `allow-device`/`block-device` trên USB còn đang kết nối.

## 5. Per-endpoint whitelist persist sau reboot

Whitelist được lưu ở Admin Server và materialize thành local USBGuard policy trên đúng endpoint. Last-known applied policy phải còn tác dụng sau client reboot.

## 6. Event connect/disconnect/allowed/blocked + active Ubuntu user

Mỗi event tối thiểu có:

```text
endpoint_id
hostname
linux_username
linux_uid
session_id
device fingerprint/name
event_type
decision
occurred_at
```

Nếu lúc event không có graphical/local active user, lưu `UNKNOWN/NONE` thay vì đoán.

## 7. Central React Admin Web Console

Các màn hình MVP:

1. Admin Login.
2. Dashboard.
3. Endpoints.
4. Endpoint Detail.
5. Per-endpoint Whitelist/Policy.
6. Event History.

## 8. Spring Boot Admin Server + Ubuntu Client Agent

### Admin Server

- authentication;
- endpoint registry/heartbeat;
- event ingestion;
- central persistence;
- per-endpoint desired policy;
- Admin REST API;
- serve React production build.

### Client Agent

- USBGuard integration;
- active-user resolution;
- **publish BLOCKED Mass Storage notification to active graphical desktop session (Week 2+);**
- event sender;
- heartbeat;
- policy sync/reconciliation;
- local enforcement.

## 9. Admin authentication + Agent authentication

### Admin

Dùng Spring Security; MVP ưu tiên session/cookie cùng origin thay vì tự làm phức tạp JWT nếu không cần.

### Agent

Mỗi endpoint dùng token/credential riêng sau enrollment. Không cho anonymous client giả event/pull policy.

## 10. `systemd` auto-start + auto-restart trên Client

`usbshield-agent.service` và `usbguard.service` tự chạy khi boot.

## 11. Normal user không được stop/chỉnh local policy

Normal user không có quyền:

- stop agent;
- stop USBGuard;
- sửa `/etc/usbguard/rules.conf`;
- sửa `/etc/usbshield-agent`;
- dùng USBGuard modify IPC.

## 12. Central persistence

Admin Server dùng **PostgreSQL** từ 04/11/2026 (owner yêu cầu); database service và backup được quản lý riêng, dù vẫn chỉ có một Admin Server. H2 và preflight/schema migration H2 không còn sử dụng; dữ liệu test cũ được phép bỏ. PostgreSQL là **persistence source**, không phải enforcement source. USBGuard local policy vẫn là enforcement source trên client.

Database ứng dụng là `usbshield` (role `usbshield`); các integration/browser tests dùng **database riêng**, không được phép xóa/tạo bảng trên database ứng dụng.

## 13. Client `.deb` installer + uninstall

Gói phát hành nằm ở `packaging/dist/`, dùng **version theo build** (`usbshield-agent_<version>_amd64.deb`); đi kèm `usbshield-agent.release.json` và SHA-256. `1.0.0` là base package version, **không** phải tên artifact cố định.

Dependencies khai báo trong `packaging/agent-deb/DEBIAN/control`:

```text
openjdk-21-jre-headless
usbguard
libnotify-bin
acl
```

Installer phải đóng gói Agent + systemd + USBGuard base policy + notifier helper; kiểm tra quyền USBGuard IPC và fail-closed policy. **Trước khi cài**, operator tạo `/etc/usbshield-agent/agent.env` (root-owned, `0600`) chứa URL HTTPS, enrollment token và cấu hình CA nếu cần; `postinst` có thể khởi động service ngay. Không nhúng secret thật vào `.deb`.

## 14. Developer Guide

Phải giải thích cả Admin Server, Agent, USBGuard, active-user attribution, network protocol và build/deploy.

## 15. Admin/User Guide

Tách rõ:

- Admin Guide: login, endpoints, events, per-endpoint Allow/Revoke.
- Client/Installation Guide: cài `.deb`, cấu hình server URL, kiểm tra service.

Normal client user không cần mở app USBShield.

## 16. Test Plan + Demo Scenarios

Phải có test end-to-end:

```text
Client user plugs USB
→ local BLOCK
→ Admin receives endpoint + username + USB event
→ Admin Allow on Endpoint A
→ Agent A syncs and ACKs the new policy
→ User removes USB from Endpoint A, then reinserts it
→ Endpoint A ALLOW on the new insertion
→ Endpoint B unchanged
```

---


## 17. Local desktop notification khi USB Mass Storage bị BLOCK — bắt đầu Week 2

- Ubuntu Client phải hiện **desktop notification native** cho **đúng graphical/local active session** khi USBGuard xác nhận sự kiện **`PresenceChanged / Insert` + class `08` + target `block`/`reject`**.
- Nội dung mặc định: **USBShield — USB Mass Storage bị chặn**; **Thiết bị lưu trữ USB chưa được cấp phép. Vui lòng liên hệ quản trị viên.** Có thể bổ sung tên thiết bị đã sanitize.
- Chỉ hiện **một notification cho mỗi lần cắm**, không tạo thêm notification từ `PolicyChanged`, `PolicyApplied`, polling hoặc heartbeat; xử lý giảm spam/reconnect liên tiếp.
- **Không hiện** khi thiết bị `ALLOWED`, USB non-storage/HID class `03`, hay lúc rút USB.
- **Không có active graphical session / resolver trả `UNKNOWN`:** không đoán user, không hiển thị vào session khác; log lý do và vẫn gửi event về server.
- **Thiếu DBus user session / desktop notification daemon / không có GUI:** chỉ thất bại phần hiển thị. Không làm thay đổi USBGuard enforcement, không chặn listener/event sender, không tự bật Allow.
- MVP dùng **freedesktop.org Desktop Notifications** (Ubuntu `libnotify-bin`/`notify-send`) thông qua **user-session notifier helper**. Agent chạy bằng system user `usbshield`, không được giả định gọi `notify-send` trực tiếp từ service là sẽ xuất hiện popup trên desktop user.
- **Không làm modal ép xác nhận** hoặc thêm nút cấp quyền USB trên client; quyền Allow/Revoke vẫn chỉ nằm trên Admin Web theo endpoint.
- Đây là **chức năng bắt buộc bắt đầu từ Week 2**, **không thuộc Week 1 Gate**.

---

# 7. Out of Scope — cố ý KHÔNG làm trong 1 tháng

Không làm:

- Windows/macOS client;
- mobile app;
- cloud SaaS;
- quản lý hàng trăm/hàng nghìn endpoint hoặc multi-site;
- Global/Group Policy áp một rule cho toàn bộ máy;
- antivirus/malware scan;
- DLP/file-content inspection;
- encrypt USB;
- custom kernel driver;
- tự viết lại USBGuard;
- AI/ML;
- OAuth/SSO;
- multi-tenant;
- microservices;
- Kafka/RabbitMQ;
- Kubernetes;
- remote desktop;
- email notification bắt buộc;
- cam kết chống lại root;
- điều khiển `allow-device`/`block-device` tức thời đối với USB đang kết nối;
- chứng minh danh tính vật lý của người cắm USB ngoài active Ubuntu session.

Bonus nếu core xong sớm:

- realtime Admin UI bằng SSE/WebSocket;
- export CSV;
- message broker hoặc queue engine mới (Agent đã có disk spool/retry/quarantine với giới hạn rõ ràng);
- desktop notification cho **Admin** (bonus, khác với client BLOCK notification bắt buộc từ Week 2);
- charts.

---


# 8. Definition of Done

## DoD-01: Admin Server

```text
Spring Boot Admin Server start
→ React Web truy cập được
→ Admin login thành công
→ PostgreSQL persistence hoạt động và dữ liệu vẫn còn sau restart JAR
```

## DoD-02: Client install/register

```text
Fresh Ubuntu 24.04
→ apt install usbshield-agent.deb
→ USBGuard active
→ Agent active
→ endpoint xuất hiện trên Admin Web
→ heartbeat ONLINE
```

## DoD-03: Unknown Flash + user attribution

```text
student01 active trên LAB-PC-01
→ cắm USB chưa whitelist
→ BLOCK
→ Admin Event History thấy:
   LAB-PC-01 | student01 | USB | BLOCKED
```

## DoD-04: Keyboard/Mouse

Non-Mass-Storage peripheral vẫn hoạt động.

## DoD-05: Per-endpoint Allow

```text
Admin → LAB-PC-01 → Allow USB X
→ Agent LAB-PC-01 sync policy and ACK applied version
→ User removes USB X, then reinserts it on LAB-PC-01
→ USB X ALLOWED on the new insertion at LAB-PC-01
```

**Revoke acceptance:** Admin Revoke USB X → Agent sync/ACK → rút và cắm lại USB X → `BLOCKED` trên lần cắm mới. Không yêu cầu chuyển trạng thái tức thời khi USB vẫn đang cắm.

## DoD-06: Per-endpoint isolation

USB X được allow trên LAB-PC-01 không tự động trở thành allow trên LAB-PC-02.

## DoD-07: Reboot persistence

Client reboot:

- services auto-start;
- local policy còn hiệu lực;
- endpoint heartbeat trở lại;
- whitelist của endpoint vẫn đúng.

## DoD-08: Service protection

Normal user không stop agent/USBGuard hoặc sửa policy/config.

## DoD-09: Admin Server unavailable

Server tạm DOWN không làm local protection chuyển sang fail-open.

## DoD-10: Documentation

Repo có ít nhất:

- README;
- Architecture;
- Developer Guide;
- Admin/User Guide;
- Client Installation;
- API/Protocol;
- Test Plan;
- Demo Script.

## DoD-11: Client blocked-USB notification (từ Week 2)

```text
Ubuntu graphical user đang active
→ cắm USB Mass Storage chưa whitelist
→ USBGuard BLOCK thật
→ một desktop notification trên đúng session của người dùng
→ sự kiện BLOCKED vẫn được gửi/lưu; mất notification không làm fail-open
```

Không có GUI hoặc không resolve được user → chỉ ghi log + gửi event, **không chọn nhầm session**.

---


# 9. Kiến trúc tổng thể (runtime hiện tại)

```text
          ADMIN WORKSTATION (IP được allow)
             HTTPS/443, browser + Admin session/CSRF
                              │
                              ▼
                    NGINX TLS + IP/route ACL
                        (Windows lab server)
                   ┌──────────┴───────────────┐
            Web, /api/auth/**             /api/agent/**
             /api/admin/**                 (Agent-only)
                   │                           ▲
                   ▼                           │ HTTPS/443
             127.0.0.1:18080          UBUNTU CLIENT A / B
                SPRING BOOT JAR        ├─ systemd + Java Agent non-web
             ├─ React static bundle   ├─ USBGuard IPC/rules (enforcement)
             ├─ Admin & Agent API     ├─ logind local-user attribution
             ├─ auth / CSRF / token   ├─ durable outbox/retry/quarantine
             └─ Flyway + JPA          └─ desktop notifier best-effort
                   │
                   ▼
          PostgreSQL `usbshield` (không mở 5432 cho Client)
```

- Từ máy Ubuntu Client, chỉ `/api/agent/**` được phép đi qua network ACL; không có Admin Web/Session/API cho Client. Spring Security vẫn xác thực riêng Admin và Agent. Nginx/host firewall phải chặn truy cập trực tiếp backend và DB; IP ACL phải kiểm bằng **source IP thật**, tránh cấu hình NAT chung vô hiệu hóa phân tách.
- Admin Web và REST API được phục vụ từ **một** executable JAR (React trong static resources); trong dev Vite chạy riêng ở `127.0.0.1:5173` và proxy `/api` về backend.
- USBGuard trên client, không phải PostgreSQL/React, quyết định USB có được sử dụng. Khi server mất mạng, chính sách USBGuard local vẫn là nguồn enforcement.
- Token và private key không có trong source/JAR/`.deb`. Agent `live` yêu cầu HTTPS và xác thực certificate CA/SAN; Admin browser truy cập HTTPS để Secure cookie hoạt động.
- Sơ đồ là thiết kế source/deployment; muốn chứng minh behavior phải có logs Windows, Nginx/ACL và Ubuntu physical E2E tương ứng.

## Policy sync model

```text
Admin Allow/Revoke đúng endpoint
  → server cập nhật whitelist/desired policyVersion
  → Agent endpoint đó heartbeat/pull policy
  → validate toàn bộ rule và reconcile USBGuard local
  → Agent gửi ACK cho appliedPolicyVersion
  → Admin phân biệt desired / applied / USBGuard health / online
  → người dùng rút USB rồi cắm lại
  → USBGuard đánh giá lần cắm mới: ALLOW hoặc BLOCK
```

**ACK** chỉ là báo cáo Agent đã áp dụng phiên bản policy, **không** xác nhận state của USB đang cắm chuyển ngay. USBGuard `HEALTHY` là health do Agent báo, có kiểm tra mới/cũ, chứ không phải chứng thực độc lập. Không có global Allow hay push-port inbound mở trên Ubuntu Client.

---

# 10. Vì sao kiến trúc này hợp lý?

## React

React là **Admin Web Console tập trung**, không chạy trên từng client.

## Spring Boot Admin Server

Là control plane trung tâm:

- Admin auth;
- endpoint registry;
- event ingestion;
- desired per-endpoint policy;
- audit;
- REST API;
- serve React.

## Spring Boot/Java Client Agent

Là local integration layer:

- giao tiếp USBGuard;
- resolve active Ubuntu user;
- gửi event;
- heartbeat;
- lấy policy riêng của endpoint;
- reconcile local rules.

## USBGuard

Là enforcement plane tại từng client. Server/Agent DOWN không được làm unknown USB tự động được allow.

## `systemd-logind` / `loginctl`

MVP dùng để xác định session local đang active. `loginctl show-session` phù hợp cho machine-parsable session properties. Nếu có nhiều session, resolver phải ưu tiên local + active session và không tự suy đoán khi không xác định được.

## systemd

Quản lý lifecycle của Agent và USBGuard tại client.

## PostgreSQL

Là central persistence cho MVP, chạy bằng một database service riêng; không nằm trong JAR. Không phải source of enforcement; USBGuard local policy mới quyết định thiết bị có dùng được hay không.

---


# 11. Policy design quan trọng nhất

Một thiết kế đơn giản và an toàn cho scope đề tài:

```text
Rule nhóm 1: ALLOW các Mass Storage đã whitelist cụ thể

Rule nhóm 2:
ALLOW các USB không có Mass Storage interface

Nếu không match rule:
Implicit default = BLOCK
```

Concept:

```text
allow <specific-whitelisted-device>

allow with-interface none-of { 08:*:* }

# Không cần generic "block storage" nếu ImplicitPolicyTarget=block
```

Kết quả:

```text
USB Flash Disk whitelist             → match specific allow → ALLOW
USB Flash Disk lạ                    → không match          → BLOCK
Keyboard không có class 08           → match non-storage    → ALLOW
Mouse không có class 08              → match non-storage    → ALLOW
Composite USB có Storage + Keyboard  → không match none-of  → BLOCK
```

Ưu điểm của cách này:

- requirement rõ ràng;
- không block keyboard/mouse;
- một thiết bị composite có Mass Storage vẫn bị xem là storage;
- USB whitelist có thể được thêm thành device-specific allow rule;
- USBGuard implicit policy giữ trạng thái fail-closed đối với Mass Storage chưa cấp phép.

> **Chú ý:** Đây là policy cho project seminar “chặn USB Flash Disk”. Nó không được tuyên bố là một policy endpoint-security hoàn chỉnh cho môi trường doanh nghiệp.

---

# 12. Nhận dạng một USB cụ thể

Server lưu VID/PID, tên thiết bị, serial và USBGuard device HASH để hỗ trợ hiển thị/audit. **Đối với whitelist/policy hiện hành, chỉ chấp nhận fingerprint kiểu `HASH` hợp lệ**. Tên USB, VID/PID riêng lẻ hoặc serial+VID/PID **không đủ** để cấp quyền vì nguy cơ đồng nhất nhiều USB cùng model. Thiết bị không có serial vẫn có thể được quản lý nếu có HASH hợp lệ.

Luồng Allow thực tế: Admin chọn một thiết bị đã được Agent ghi nhận trên **endpoint đích** → server lấy `usbDeviceId` và HASH hợp lệ → ghi `endpoint_whitelist_entries` cho endpoint đó → Agent nhận policy `{policyVersion, whitelistedDevices}` và kiểm tra toàn bộ whitelist trước khi mutate rules. Whitelist sai/không hợp lệ không được áp dụng một phần rồi ACK. Mọi HASH nhận qua HTTP phải được xác thực và đối chiếu server ↔ Agent.

Bản ghi audit có thể giữ `usb_device_id` tham chiếu lịch sử ngay cả khi metadata không còn; không tự đặt foreign key xóa lịch sử để “làm sạch” schema. Xem [API](docs/API.md), [addendum](docs/API_PHASE1_TO_PHASE3_ADDENDUM.md) và [finding F07](docs/qa/FINDING_F07_RELATIONAL_CONTRACT.md).

---

# 13. USBGuard + Active User integration strategy

## 13.1. USBGuard Gateway nằm trong Client Agent

Interface gợi ý:

```java
public interface UsbGuardGateway {
    List<UsbDeviceInfo> listDevices();
    List<UsbGuardRule> listRules();
    void applyWhitelist(List<DeviceFingerprint> desiredWhitelist);
    UsbGuardHealth health();
}
```

Implementation đầu:

```text
UsbGuardCliGateway
```

Java `ProcessBuilder` gọi official `usbguard` CLI. Không rải shell command trong controller/service khác.

## 13.2. Event listener

Ưu tiên event-driven:

```text
usbguard watch
→ UsbGuardEventListener
→ UsbDeviceMapper
→ ActiveUserResolver
→ ClientUsbEvent
→ EventSender
```

## 13.3. Active user resolver

Week 1 PoC có thể dùng:

```bash
loginctl list-sessions --no-legend
loginctl show-session <id> \
  -p Name -p User -p Active -p Seat -p Type -p Remote
```

Rule chọn session:

1. `Active=yes`;
2. local (`Remote=no`);
3. ưu tiên graphical session/seat0;
4. nếu không xác định duy nhất → `UNKNOWN` thay vì đoán.

Class gợi ý:

```text
ActiveUserResolver
LoginctlActiveUserResolver
ActiveUserInfo
```

## 13.4. Agent ↔ Server

Ví dụ payload ở thời điểm thiết kế; **source of truth của wire contract hiện hành là `docs/API.md`**, gồm endpoint-bound credentials và DTO v0.2. Agent gửi:

```json
{
  "endpointId": "...",
  "hostname": "LAB-PC-01",
  "linuxUsername": "student01",
  "linuxUid": 1001,
  "sessionId": "3",
  "device": { "vendorId": "0951", "productId": "1666", "serial": "..." },
  "eventType": "CONNECTED",
  "decision": "BLOCKED",
  "occurredAt": "..."
}
```

## 13.5. Principle of least privilege

- normal users: không USBGuard modify permission;
- Agent service user: chỉ quyền cần thiết để listen/list/reconcile policy;
- endpoint token lưu root-owned;
- Admin policy change xảy ra trên central server, nhưng chỉ Agent của endpoint tương ứng được nhận desired policy của endpoint đó.

---


## 13.6. Local notification integration — tách system service và user desktop session

```text
USBGuard PresenceChanged / Insert (class 08, BLOCKED)
      ↓
M2 UsbGuardEventListener + active-user resolver
      ├── M3 EventSender (network/central log, không bị phụ thuộc notification)
      └── M2 BlockNotificationPolicy + ClientNotificationPublisher
                   ↓ local-only IPC; message chỉ là notification text/metadata
          M2 DesktopNotificationClient (chạy bằng UID của desktop user)
                   ↓ user's session D-Bus
          notify-send / org.freedesktop.Notifications
                   ↓
          Ubuntu desktop notification
```

- **M2 owns** event classification, notification payload, dedup/cooldown logic, notification publisher/desktop client code và tests trong `client-agent/.../notification/`, plus USBGuard/session core đang bàn giao.
- **M3 owns** transport lifecycle/config của Agent, provisioning quyền IPC giữa system service và GUI session, `packaging/desktop/` (XDG autostart/user-session helper launch), `.deb` dependency `libnotify-bin` và integration với unit `systemd`.
- **M1/M4/M5** không phải sửa API/DB/Admin Web vì tính năng thông báo trên client dùng event đã có; M5 bổ sung test case/evidence từ Week 2.
- Thiết kế IPC giữa `usbshield` system service và notifier user session phải **local-only**, xác thực/giới hạn peer, gắn notification với UID đã resolve và **không cấp khả năng quản lý USBGuard/policy cho desktop user**. Có thể dùng Unix-domain socket có kiểm soát quyền; không dùng port HTTP mở mạng hay file world-writable.
- `notify-send` chỉ được gọi **trong môi trường D-Bus của session đồ họa đích**. Không chạy `sudo`/`bash -c` trong Java; không hard-code `DISPLAY=:0`, UID 1000 hay username cố định.
- Nếu user-session helper chưa sẵn sàng, trả trạng thái `SKIPPED_NO_GUI`/`DELIVERY_FAILED` để log; enforcement và event posting vẫn độc lập.

---

# 14. Tech stack chính thức

| Layer | Công nghệ | Lý do |
|---|---|---|
| Client OS | Ubuntu 24.04 LTS amd64 | Freeze target |
| Admin Frontend | React + Vite + TypeScript | Central web console |
| UI | React components + CSS nội bộ | Giao diện hiện tại không dùng MUI/Ant Design |
| Admin Server | Java 21 + Spring Boot | REST, Security, persistence |
| Client Agent | Java 21 + Spring Boot non-web (`live` trên Ubuntu) | Agent service, không cung cấp Web UI |
| Admin Auth | Spring Security + session/cookie | Web cùng origin, đơn giản hơn JWT |
| Agent Auth | Endpoint token / enrollment token | Xác thực client |
| ORM | Spring Data JPA | Central persistence |
| DB MVP | PostgreSQL + Flyway V1/V2; Hibernate `validate` | DB ứng dụng và các DB test tách biệt |
| User attribution | systemd-logind / `loginctl` MVP | Active Linux session |
| Ubuntu Client notification (Week 2+) | `libnotify-bin` / `notify-send` + user-session D-Bus | Native desktop feedback for BLOCKED Mass Storage |
| USB enforcement | USBGuard | Linux USB authorization |
| Client lifecycle | systemd | Auto-start/restart |
| Client packaging | Debian `.deb` + SHA-256/release manifest | Native Ubuntu install, nguồn `.deb` theo từng build |
| Java client runtime | `openjdk-21-jre-headless` dependency | APT xử lý |
| API docs | springdoc/OpenAPI | Chỉ bật local/dev; profile `prod` tắt Swagger/OpenAPI |
| Testing | JUnit 5 + Mockito + Spring Boot Test | Unit/integration |
| Hardware test | USB thật + VMware passthrough | Core requirement |
| VCS | Git + GitHub | Collaboration |

> Freeze major versions từ Week 1.

---


# 15. Vì sao React + Vite thay vì Next.js?

Admin Console là một SPA nội bộ:

```text
https://<admin-server-lan-dns>
```

Không cần SEO/SSR/Server Components/edge deployment.

Production:

```text
React build
→ Spring Boot static resources
→ Admin Server phục vụ UI + REST cùng origin
```

Admin chỉ cần browser. Client Ubuntu **không chạy React UI**.

---


# 16. Frontend screens — Admin Web (source `frontend/` hiện hành)

| Màn hình/route | Hành vi thực tế |
|---|---|
| `/#/login` | Admin session/cookie; lấy CSRF; khi chưa đăng nhập không mở trang live quản trị |
| `/#/dashboard` | Các tổng hợp server-side (thiết bị, quyết định, số sự kiện) và preview sự kiện gần đây; không suy số liệu toàn cục từ trang dữ liệu đầu tiên |
| `/#/endpoints` | Tìm kiếm/filter ONLINE/OFFLINE, phân trang server; mở endpoint detail, xem policy desired/applied và health |
| `/#/endpoints/{endpointId}` | Modal/chi tiết endpoint; Allow/Revoke có xác nhận, HASH từ USB đã quan sát; xem sự kiện gần đây và ACK |
| `/#/devices` | Trạng thái USB gần nhất theo từng endpoint, tìm kiếm/filter/phân trang; **Blocked là filter ở đây**, không còn trang Blocked độc lập |
| `/#/whitelist` | Danh sách whitelist tổng hợp có phân trang; thao tác thay đổi vẫn ở một endpoint cụ thể |
| `/#/events` | Event Logs, filter endpoint/user/USB/event type/decision/date; server-side pagination, mặc định **10 bản ghi mỗi trang** |
| `/#/settings` | Thông tin kết nối và đổi mật khẩu Admin; đổi xong các session cũ bị vô hiệu; chưa có API web để chỉnh USBGuard daemon |

- Frontend dùng React/Vite/TypeScript với components/CSS trong repo; **không có dependency MUI/Ant Design ở HEAD này**. Production là static bundle nhúng trong Admin Server JAR, API cùng origin, không chạy Vite trên Ubuntu Client.
- Demo ở `/demo` có dữ liệu minh họa **chỉ đọc**. Không thay kết quả API thật bằng mock khi live đang rỗng/lỗi.
- Online (heartbeat), desired/applied ACK và USBGuard health (`HEALTHY/DEGRADED/UNAVAILABLE/UNKNOWN`, checkedAt/stale) là **ba loại tín hiệu khác nhau**; không hiển thị thành công giả.
- Các bộ lọc và số đếm phải đi qua API PostgreSQL phân trang; không tải toàn bộ event history vào browser để mô phỏng tìm kiếm toàn hệ thống.

Xem [ADMIN_GUIDE](docs/ADMIN_GUIDE.md), [API addendum](docs/API_PHASE1_TO_PHASE3_ADDENDUM.md) và [M4/M5 UI guardrails](docs/admin-web/POST_CORE_E2E_UI_CHANGE_GUARDRAILS.md).

---

# 17. Các module/package đúng theo source hiện tại

## 17.1. Admin Server

Base package: `admin-server/src/main/java/com/group/usbshield/server/` (Spring Boot).

```text
agentapi/   enrollment, credential-bound Agent auth, heartbeat, Agent event DTO
             (Agent policy endpoint nằm dưới policy/)
auth/       Admin session/login/logout, bootstrap, change/reset password,
             credential-version session invalidation
config/     Security, OpenAPI local-only, SPA routing
device/     USB device catalog và persistence
endpoint/   endpoint model, controller, registry, online/offline
event/      events/history, dashboard summary, device states và paging
policy/     endpoint whitelist, HASH identity, policy version/ACK, paging
common/     API exception handling
resources/  application.yml, application-prod.yml, Flyway migrations V1/V2
```

API Admin session và Agent credentials tách biệt. Admin mutation phải qua CSRF; Agent sử dụng `X-Endpoint-Id` + `X-Agent-Token` (trừ enrollment). Bootstrap token chỉ để enroll lần đầu; không auto-rotate trùng `machineId`.

## 17.2. Client Agent

Base package: `client-agent/src/main/java/com/group/usbshield/agent/` (Spring Boot **non-web**, profile `live` trên Ubuntu).

```text
config/        AgentProperties, cấu hình runtime
endpoint/      identity/enrollment management
event/         event enrichment, event sender, payload
notification/  block notification policy, local notifier IPC, desktop helper
outbox/        disk spool/retry/quarantine/retention bounds
session/       logind session resolution, active user selection
sync/          HTTPS transport, heartbeat, policy sync, USBGuard health probe
system/        local host/OS metadata
usbguard/      gateway, list/watch, policy reconcile, rule validation
```

- M2/M3 ownership theo Week 2 là **phân công lịch sử**, không phải hướng dẫn tạo thêm class từ sơ đồ cũ. Tái sử dụng implementation đã tồn tại.
- **USBGuard daemon** là enforcement plane; Agent reconcile bằng USBGuard CLI/IPC. `notification/` chạy best-effort tới đúng graphical session, không biến GUI user thành principal có quyền sửa USBGuard.
- `live` không fallback HTTP, không bỏ xác thực CA/hostname, không sử dụng storage tương đối. Ranh giới lỗi Agent/outbox không được làm USBGuard fail-open.

---

# 18. REST API thực tế — contract v0.2 + Phase 1–3 addendum

**Không sử dụng route nháp `/api/endpoints` hoặc `/api/events`.** Các route Admin hiện hành nằm dưới `/api/admin/**`; phần chi tiết wire (DTO, mã lỗi, giới hạn fields) được bảo quản ở [API v0.2](docs/API.md) và [Phase 1–3 addendum](docs/API_PHASE1_TO_PHASE3_ADDENDUM.md). Cập nhật blueprint **không bump** wire contract lên v0.3.

## 18.1. Admin session, CSRF, password

```http
GET  /api/auth/csrf
POST /api/auth/login
GET  /api/auth/me
POST /api/auth/logout
POST /api/admin/account/password
```

Admin login dùng cookie `JSESSIONID` + CSRF token được làm mới phù hợp với phiên. Các Admin mutation yêu cầu phiên hợp lệ và CSRF. Prod có Secure/HttpOnly/SameSite Strict cookie, tắt Swagger/OpenAPI; reset khẩn cấp không qua HTTP public route.

## 18.2. Endpoint, whitelist, event và dashboard

```http
GET    /api/admin/endpoints/page?page=0&size=20&q=&status=online
GET    /api/admin/endpoints                   # legacy, tối đa 100
GET    /api/admin/endpoints/{endpointId}
GET    /api/admin/endpoints/{endpointId}/whitelist
POST   /api/admin/endpoints/{endpointId}/whitelist
DELETE /api/admin/endpoints/{endpointId}/whitelist/{entryId}
GET    /api/admin/whitelist/page?page=0&size=20
GET    /api/admin/events?page=0&size=20
GET    /api/admin/events/{id}
GET    /api/admin/dashboard/summary
GET    /api/admin/device-states?page=0&size=20
```

Các API list lọc và đếm **trước phân trang tại backend**. UI Event Logs đặt page size 10, khác default chung của API Events (20). Allow `POST` nhận `usbDeviceId` đã quan sát; không có global Allow, endpoint delete hay tự cấp quyền bằng VID/PID/name. Duplicate Allow và Revoke đã có idempotency theo contract. Server tăng desired policy của đúng endpoint khi cần; không tự coi server update là applied trên USBGuard.

## 18.3. Agent HTTP API

```http
POST /api/agent/enroll         # body enrollmentToken; chưa có Agent token
POST /api/agent/heartbeat      # headers X-Endpoint-Id + X-Agent-Token
POST /api/agent/events         # cùng headers và endpoint identity
GET  /api/agent/policy         # cùng headers; whitelistedDevices
POST /api/agent/policy/ack     # version đã áp dụng; cùng headers
```

- Enrollment trả về `{endpointId, agentToken, currentPolicyVersion}`; Agent token lưu hash ở Server và không phát lại công khai. Trùng machineId bị từ chối, không âm thầm thay token.
- Heartbeat là telemetry/online và có thể mang USBGuard health; **không** cập nhật applied version. Chỉ ACK hợp lệ mới cập nhật applied: future/regressive ACK bị từ chối, duplicate hợp lệ idempotent.
- Policy trả `whitelistedDevices` (không phải tên `allowedDevices` từ v0.1), HASH-only; payload rule sai bị validate/reject, không ACK giả.
- Agent `live` yêu cầu HTTPS và certificate đúng; ngoại lệ CSRF cho Agent APIs là do chúng dùng Agent token, **không** cho Agent truy cập Admin API.

---

# 19. PostgreSQL schema thực tế (Flyway V1/V2)

Database ứng dụng: **`usbshield`**, role **`usbshield`**. Bảng nghiệp vụ thực tế (không phải sơ đồ gợi ý Week 1) được tạo trong `admin-server/src/main/resources/db/migration/`:

| Bảng | Cột/ý nghĩa tiêu biểu |
|---|---|
| `admin_users` | `id`, `username` UNIQUE, `password_hash`, `created_at`; V2 thêm `credential_version` |
| `endpoints` | `endpoint_id` UNIQUE, `hostname`, `machine_id` UNIQUE, `agent_token_hash`, `ip_address`, `operating_system`, `active_username`, `status`, `last_seen`, `policy_version`, `applied_policy_version`; V2 thêm `usbguard_reported_status`, `usbguard_checked_at` |
| `usb_devices` | VID/PID, serial, HASH (`device_hash` UNIQUE), name, interfaces, `created_at` |
| `usb_events` | `endpoint_id`, `event_id`, `payload_digest`, `usb_device_id`, hostname, Linux username/UID/session, type/decision, `occurred_at`, `received_at`; `(endpoint_id,event_id)` UNIQUE |
| `endpoint_whitelist_entries` | `endpoint_id`, `usb_device_id`, `fingerprint_type`, `fingerprint_value`, `enabled`, `created_at`; UNIQUE `(endpoint_id, fingerprint_value)` |

**Không có bảng `admins`, `endpoint_whitelist` hay `audit_logs` độc lập trong Flyway V1/V2.** Audit sự kiện USB tập trung qua `usb_events`; không suy diễn mọi hành động Admin đã có bảng audit riêng. Bảng whitelist dùng **số nhiều** `endpoint_whitelist_entries`, khác tên nháp ở thiết kế Week 1.

- Flyway chạy V1/V2 trên **database mới/trống**; với PostgreSQL **đã tồn tại schema chưa được version**, phải backup và chạy preflight/baseline V1 theo quy trình reviewed, không đặt `baseline-on-migrate=true` tràn lan.
- Hibernate `ddl-auto=validate`, **không** `create`, `create-drop`, `update` trên DB ứng dụng. Database và credentials đặt ngoài JAR; restart JAR không xóa dữ liệu khi PostgreSQL vẫn lưu bền vững.
- **Maven integration:** database `usbshield_test`, các schema `usbshield_it_*`; **browser/HTTP-live:** database `usbshield_browser_test`, schema `usbshield_e2e_*`, role `usbshield_test` và cleanup có kiểm soát. Không cấp test role quyền DDL trên DB `usbshield`.
- Không tự thêm FK cho `usb_events.usb_device_id` làm mất event orphan lịch sử; giữ quyết định audit retention trong review F07. Xem [schema baseline](admin-server/ops/POSTGRES_FLYWAY_BASELINE.md) và [F07 contract](docs/qa/FINDING_F07_RELATIONAL_CONTRACT.md).

---

# 20. Packaging và deployment hiện tại

## 20.1. Client Ubuntu 24.04 LTS amd64 — `.deb`

- Builder: `packaging/scripts/build-deb.ps1` (Windows) hoặc `packaging/scripts/build-deb.sh` (Ubuntu); phát hành dưới `packaging/dist/` với version theo build, `usbshield-agent.release.json`/SHA-256. Không dùng tên artifact `usbshield-agent_1.0.0_amd64.deb` như filename luôn tồn tại.
- Dependencies theo control file: **`openjdk-21-jre-headless`, `usbguard`, `libnotify-bin`, `acl`**. Java runtime không được bundle vào `.deb`; APT xử lý dependency.
- Trước `apt install`, operator cấp `/etc/usbshield-agent/agent.env` **root:root, mode 0600** với `USBSHIELD_SERVER_URL=https://<DNS đúng SAN>`, `USBSHIELD_ENROLLMENT_TOKEN` và `USBSHIELD_TLS_CA_CERTIFICATE` nếu dùng private CA. Không commit secret, không copy `endpoint.json` giữa các client.
- Gói chứa Agent JAR, `application.yml`, systemd unit, USBGuard base rule, notifier desktop helper/autostart. Agent data ở `/var/lib/usbshield-agent/`; executable JAR ở `/opt/usbshield-agent/usbshield-agent.jar`.
- `usbshield-agent.service` chạy **`--spring.profiles.active=live`**, thêm config ở `/etc/usbshield-agent/application.yml`, User/Group `usbshield`, tự restart khi lỗi; USBGuard phải có `ImplicitPolicyTarget=block` và IPC ACL thích hợp. Installer có check, nhưng **format `.deb` PASS trên Windows không chứng minh postinst/upgrade/remove trên Ubuntu PASS**.

Ví dụ install khi artifact đã có, test trong VM được chỉ định:

```bash
# Đảm bảo thư mục chỉ chứa đúng một gói `.deb` đã kiểm SHA-256.
sudo apt install ./usbshield-agent_*_amd64.deb
systemctl status usbguard.service usbshield-agent.service
journalctl -u usbshield-agent.service -n 100 --no-pager
```

## 20.2. Admin Server JAR — build và Windows verification

Tại root repository, PowerShell **7.6+ trên Windows** cho canonical verifier:

```powershell
# Admin/frontend + JAR + Browser-live/HTTP-live; bỏ Agent và .deb
.\packaging\tests\verify-windows.ps1 -AdminOnly

# Toàn bộ gate Windows: Admin, Agent, frontend, browser/HTTP-live và .deb
.\packaging\tests\verify-windows.ps1
```

Tạo JAR phát hành từ clean Git tree (PowerShell 7 trên Windows, JDK 21, Maven, Node/npm, Git, PostgreSQL test DB khi chạy tests):

```powershell
.\admin-server\build-release.ps1
# hoặc: .\admin-server\build-release.ps1 -OutputDirectory 'C:\USBShieldReleases'
```

Script build React mới, Maven `-Pwith-frontend clean package`, kiểm tra JAR static assets/Flyway, sinh `admin-server/target/usbshield-admin-server.jar` và copy bản release `usbshield-admin-server_<commit12>_<UTC>.jar` cùng JSON manifest/SHA-256 (mặc định `admin-server/target/release/`). `-AllowDirtyTree` cho build dev chưa được release-qualified; `-SkipTests` giảm mức độ bằng chứng. **Không coi Maven BUILD SUCCESS là verifier PASS.** `admin-server/verify-week1/2/3.ps1` không còn trong HEAD.

## 20.3. Biến môi trường test khác runtime `prod`

| File/cách nạp | Dùng cho | Lưu ý |
|---|---|---|
| `.env` (untracked, từ `.env.example`) | PostgreSQL **test** (`USBSHIELD_TEST_DB_*`) khi `build-release.ps1` chạy tests, `verify-windows.ps1` | Không thay thế runtime `USBSHIELD_DB_URL` và không ghi vào JAR |
| `.env.e2e.local` (untracked của operator) | Lab runtime `prod`: `USBSHIELD_DB_URL`, `USBSHIELD_DB_PASSWORD`, Admin bootstrap/enrollment secret, bind/port | Lab `prod` profile **không đồng nghĩa production acceptance** |
| `scripts/with-env.ps1` | Nạp một `.env`-style file vào tiến trình thực thi lệnh | Không đóng gói vào JAR; không giúp các lần chạy Java khác tự biết biến |

```powershell
# Khi runtime credentials đang nằm trong .env.e2e.local:
.\scripts\with-env.ps1 -EnvFile .env.e2e.local -Command {
    java -jar admin-server/target/usbshield-admin-server.jar --spring.profiles.active=prod
}

# Chỉ dùng được khi USBSHIELD_DB_URL/password và secrets đã có trong process environment:
java -jar admin-server/target/usbshield-admin-server.jar --spring.profiles.active=prod
```

`--spring.profiles.active=prod` **không tự load `.env.e2e.local`**. Thiếu `USBSHIELD_DB_URL` gây lỗi Hikari/Driver nhận literal `${USBSHIELD_DB_URL}`; đó là lỗi cấu hình môi trường, không phải bằng chứng JAR không được build.

## 20.4. Deploy qua LAN và phân phối JAR

- Spring Boot `prod` bind `127.0.0.1` theo mặc định, port có thể đặt `PORT=18080` cho Nginx lab. Admin browser truy cập **HTTPS** qua Nginx/IP ACL; Client Agent chỉ gọi `/api/agent/**`; DB 5432 không mở ra Client. Phân biệt Secure cookie, Agent TLS verification và HTTPS proxy.
- JAR có thể đặt ngoài `target/` và đổi tên theo release; chạy bằng **đúng đường dẫn thực tế**. Không cần Maven/npm/repository source trên máy người nhận JAR; cần Java runtime, PostgreSQL, secrets và nếu truy cập LAN, TLS/Nginx/firewall. Nên phát hành trên GitHub Releases kèm SHA-256 + manifest thay vì commit binary JAR vào source.
- Không gửi `.env.e2e.local` thật, enrollment token, Agent token hoặc CA private key cho người khác; người nhận tự cấu hình môi trường được phân quyền.
- Ubuntu Minimal Core P0+C1–C5 đã có **owner-reported PASS** trong lab 09/11. `.deb` native upgrade/remove, reboot/offline, negative TLS và notifier IPC vẫn deferred; không suy từ source hoặc Windows verifier.

Runbook canonical: [RUN_GUIDE](docs/RUN_GUIDE.md), [ADMIN_SERVER_RELEASE](docs/ADMIN_SERVER_RELEASE.md), [Ubuntu Minimal Core](docs/USBShield_Ubuntu24_Minimal_Core_E2E_vinhkhang_f446c04_20261107.md), [HTTPS lab](docs/USBShield_HTTPS_ACL_Setup_usbprotection.lab_vinhkhang_4d2216c_20261109.md).

---

# 21. Repo structure thực tế (`main`, 20/11/2026)

```text
USBProtection/
├── README.md
├── USBShield_Blueprint_Allow_Revoke_Reinsert_20261103.md
├── .env.example                  # template test; .env thật không commit
├── scripts/
│   └── with-env.ps1
├── admin-server/
│   ├── pom.xml
│   ├── build-release.ps1
│   ├── ops/POSTGRES_FLYWAY_BASELINE.md
│   └── src/main/{java,resources}/  # Flyway V1/V2, prod profile, controllers
├── client-agent/
│   ├── pom.xml
│   └── src/main/{java,resources}/  # live non-web, USBGuard, outbox, notifier
├── frontend/
│   ├── package.json
│   ├── src/{pages,components,services,hooks,...}/
│   └── tests/
├── packaging/
│   ├── agent-deb/DEBIAN/{control,postinst,prerm}
│   ├── desktop/
│   ├── firewall/
│   ├── nginx/
│   ├── scripts/build-deb.{ps1,sh}
│   ├── systemd/usbshield-agent.service
│   ├── tests/{verify-windows.ps1,check-usbshield-core.sh,...}
│   └── usbguard/rules-v0.1.conf
└── docs/
    ├── DOCUMENT_INDEX.md
    ├── RUN_GUIDE.md
    ├── ADMIN_SERVER_RELEASE.md
    ├── DEVELOPER_GUIDE.md
    ├── ADMIN_GUIDE.md
    ├── ARCHITECTURE.md
    ├── API.md
    ├── API_PHASE1_TO_PHASE3_ADDENDUM.md
    ├── AGENT_SERVER_PROTOCOL.md
    ├── TEST_CATALOG.md
    ├── TEST_PLAN.md              # lịch sử
    ├── DEMO_RUNBOOK_20261120.md  # hiện hành
    ├── DEMO_SCRIPT.md           # Week 4 rehearsal lịch sử
    └── {qa,rc,client-agent,admin-web,usbguard,member1,...}/
```

`target/`, `frontend/dist/`, `frontend/node_modules/` và `packaging/dist/` là đầu ra build/test hoặc phụ thuộc máy chạy; không phải nguồn versioned cần commit. **Không có thư mục source `admin-web/`** ở HEAD: tên module hiện hành là `frontend/` (thư mục `docs/admin-web/` chỉ chứa tài liệu frontend). Đường dẫn `USBGUARD_CORE.md` từng ghi trong blueprint là placeholder kế hoạch; thông tin M2 hiện có trong `docs/usbguard/`.

---

# 22. Phân chia công việc 5 người (ownership/kế hoạch lịch sử)

## Member 1 — Admin Server Lead / Architecture / Integration Owner

### Phải làm

- Spring Boot Admin Server structure.
- PostgreSQL + JPA schema (thay cho H2 đã dùng ở thời điểm Week 1–4).
- Admin auth bằng Spring Security.
- Endpoint enrollment/registry/heartbeat.
- Event ingestion API.
- Per-endpoint whitelist/policy service.
- policy version + ack model.
- Admin REST API/OpenAPI.
- contracts dùng chung với Agent.
- merge/integration owner.

### Deliverables

```text
Admin login
endpoint registry
agent APIs
event ingestion
per-endpoint policy APIs
central DB
OpenAPI
server JAR
```

---

## Member 2 — Linux/USBGuard Core + Active User Attribution Engineer

Đây vẫn là owner technical core.

### A. USBGuard

- install/config USBGuard;
- class `08` detection;
- base allowlist policy;
- unknown Flash BLOCK;
- mouse/keyboard ALLOW;
- specific allow/revoke;
- reboot/reconnect tests.
- từ Week 2: desktop notification khi USB class `08` bị BLOCK, không hiển thị cho HID/ALLOWED.

### B. Java USBGuard integration

```text
UsbGuardGateway
UsbGuardCliGateway
UsbGuardCommandRunner
UsbGuardOutputParser
UsbGuardDeviceMapper
UsbGuardEventListener
UsbGuardPolicySynchronizer
```

**Từ Week 2, toàn bộ class đã có trong `client-agent/src/main/java/com/group/usbshield/agent/usbguard/` được M3 bàn giao nguyên trạng cho M2.** M2 review/refactor/maintain trực tiếp, M3 không tiếp tục chỉnh các class này (kể cả `AdaptiveUsbGuardGateway`, `UsbGuardEventParser`, `MockUsbGuardGateway`, `UsbDeviceInfo`).

### C. Active user attribution — yêu cầu mới

PoC và implement:

```text
ActiveUserResolver
LoginctlActiveUserResolver
ActiveUserInfo
```

Khi USB event xảy ra phải trả được:

```text
username
uid
sessionId
sessionType
seat
```

Nếu không xác định được thì `UNKNOWN`, không đoán.

**Từ Week 2, toàn bộ class đã có trong `client-agent/src/main/java/com/group/usbshield/agent/session/` được M3 bàn giao nguyên trạng cho M2.** M2 sửa trên code hiện hữu (`LinuxActiveUserResolver`, `AdaptiveActiveUserResolver`, `MockActiveUserResolver`, `ActiveUserResolver`, `ActiveUserInfo`), không nhân đôi interface/model.

### D. Client desktop notification (Week 2)

- M2 sở hữu bộ lọc `Insert` + class `08` + `BLOCKED`, lấy UID/session từ resolver, dedup, notification publisher và user-session notification client.
- M2 unit test và hardware verify popup/notification hiển thị đúng user trong Ubuntu GUI; không thay thế bằng console log.
- M3 cung cấp XDG autostart/IPC permissions/`libnotify-bin` packaging; không sửa trực tiếp Java notification/USBGuard/session của M2.

### E. Handoff

Gửi cho M1/M3 event contract:

```text
USB event + active user + device fingerprint
```

### Deliverables

```text
USBGuard_POC.md
base policy
real hardware evidence
USBGuard Java adapter
active-user attribution PoC/code
Week 2 desktop BLOCK notification + unit/hardware evidence
sample Agent event payload
```

---

## Member 3 — Client Agent / System Service / Packaging Engineer

### Phải làm

- Client Agent Spring Boot/non-web service shell.
- endpoint enrollment.
- endpoint token/config.
- heartbeat.
- event POST/retry.
- policy pull/version sync.
- gọi M2 policy synchronizer.
- systemd service.
- service user/permissions.
- USBGuard IPC ACL.
- `.deb` installer/uninstall.
- server URL configuration.
- clean VM install test.
- từ Week 2, xây dựng/cấu hình desktop user-session helper auto-start và IPC permissions theo contract M2, không sửa code M2 trong `usbguard/`, `session/`, `notification/`.
- Week 3 `.deb` bổ sung `libnotify-bin` và file launcher XDG autostart của notifier.

### Deliverables

```text
client-agent.jar
usbshield-agent.service
packaging/desktop/* (user-session notification helper launcher)
agent↔server communication
policy sync
.deb
clean-install evidence
```

---

## Member 4 — Admin Frontend Lead

### Phải làm

- React/Vite/TS structure.
- Login.
- Dashboard.
- Endpoints list.
- Endpoint Detail.
- Per-endpoint whitelist UI.
- Allow/Revoke UX.
- API integration.
- auth/session handling.
- loading/error states.

### Deliverables

```text
Admin Web
endpoint management
per-endpoint whitelist UI
production React build
```

---

## Member 5 — Event Frontend / QA / Release Validation

### Phải làm

- Event History UI.
- filter theo endpoint/user/device/decision/date.
- endpoint online/offline/status components.
- test plan end-to-end.
- test user attribution.
- test per-endpoint isolation.
- clean install/reboot/server-down scenarios.
- Demo Script.
- release validation.

### Deliverables

```text
Event History
filters
QA evidence
TEST_PLAN.md
DEMO_SCRIPT.md
release validation report
```

> Documentation vẫn chia theo owner module; không có người chỉ làm docs.

---


# 23. Documentation ownership (bảng phân công lịch sử)

| Tài liệu | Owner | Contributor |
|---|---|---|
| README | M1 | tất cả |
| ARCHITECTURE | M1 | M2, M3 |
| USBGUARD_CORE (placeholder lịch sử; hiện tham khảo `docs/usbguard/`) | M2 | M3 |
| Active-user attribution notes | M2 | M3 |
| AGENT_SERVER_PROTOCOL | M1 | M3, M2 |
| DEVELOPER_GUIDE | M1 | tất cả |
| RUN_GUIDE (Client + Admin) | M1, M3 | M2 |
| ADMIN_GUIDE | M4 | M5 |
| TEST_PLAN | M5 | tất cả |
| DEMO_SCRIPT | M5 | M1–M4 |
| API | M1 | M3 |

Ai code module nào phải cung cấp nội dung kỹ thuật của module đó.

---


# 24. Kế hoạch 4 tuần (snapshot lúc lập kế hoạch; không phải trạng thái nghiệm thu)

## WEEK 1 — Prove Core + Freeze Client/Server Contract

### Mục tiêu

Cuối tuần phải chứng minh:

```text
Unknown USB Mass Storage → BLOCK
USB mouse/keyboard → ALLOW
USB event → lấy được active Ubuntu username/session
Admin Server/Agent event contract đã chốt
```

### M1

- repo structure mới;
- Admin Server skeleton;
- PostgreSQL schema (thay H2 schema draft lịch sử);
- Admin auth skeleton;
- endpoint/event/policy DTO contract;
- OpenAPI draft;
- Architecture v0.1.

### M2

- toàn bộ USBGuard Week 1 PoC;
- class 08;
- base policy;
- hardware test;
- `usbguard watch`;
- Java parser PoC;
- **thêm `loginctl` active-user attribution PoC**;
- sample combined event payload.

### M3

- Client Agent skeleton;
- config `server-url`;
- dummy systemd service;
- PoC POST heartbeat/event tới Admin Server;
- packaging/IPC research.

### M4

- Admin Web skeleton;
- Login wireframe;
- Dashboard;
- Endpoints;
- Endpoint Detail/Whitelist wireframe.

### M5

- Test Plan v0.1;
- Event History mock với columns Endpoint + Linux User + USB + Decision;
- test inventory;
- verify M2 PoC.

### WEEK 1 GATE

- [ ] unknown Flash thật BLOCK.
- [ ] USB mouse/keyboard ALLOW.
- [ ] active Ubuntu username/session lấy được.
- [ ] sample event có endpoint + user + USB + decision.
- [ ] Admin Server + Agent contract freeze v0.1.
- [ ] FE/BE skeleton chạy.

---


# 25. WEEK 2 — Agent ↔ Server + Real Event + Per-Endpoint Policy

## Mục tiêu

```text
Real Client USB event
→ BLOCK local
→ Admin Server receives
→ Admin Web sees endpoint + username + device
→ Admin Allow on that endpoint
→ Agent syncs and ACKs
→ local USBGuard rules change
→ user removes and reinserts USB
→ new insertion is ALLOWED on that endpoint
```

### M1

- finish Admin auth;
- endpoints registry/heartbeat;
- event ingestion/persistence;
- per-endpoint whitelist;
- policy version/ack;
- Admin APIs.

### M2

- implement USBGuard gateway/parser/listener;
- implement ActiveUserResolver;
- combine USB event + active user;
- policy synchronizer operations;
- hardware integration tests;
- tiếp quản, review và sửa trực tiếp **toàn bộ class hiện có** trong `client-agent/.../usbguard/` và `client-agent/.../session/` do M3 bàn giao;
- **client BLOCK notification**: M2 sở hữu detector + payload + dedup + publisher + desktop notifier code/tests; chỉ báo cho session graphical active và không tạo duplicate từ PolicyChanged/PolicyApplied.

### M3

- enrollment/token;
- heartbeat;
- event sender/retry;
- policy fetch/version sync;
- ack;
- non-root Agent service draft;
- tích hợp/khởi chạy desktop notification helper trên Ubuntu user session, local IPC permission, và nghiên cứu/cập nhật packaging; **không sửa `usbguard/`, `session/`, `notification/` của M2**;
- vô hiệu hóa auto fake-event runner khi chạy chế độ hardware/live để không phát notification hoặc event giả.

### M4

- login integrate;
- Dashboard/Endpoints integrate;
- Endpoint Detail;
- per-endpoint Allow/Revoke.

### M5

- Event History integrate;
- filters;
- E2E test Client → Server → Web;
- verify correct username attribution;
- kiểm tra client notification hiển thị đúng user, một lần cho một Insert bị BLOCK, không popup khi ALLOWED/HID/Remove và không ảnh hưởng chặn USB lúc GUI không sẵn sàng.

### WEEK 2 GATE

- [ ] real USB event reaches central server.
- [ ] Event row shows correct endpoint + active Linux user.
- [ ] Admin can Allow/Revoke for selected endpoint.
- [ ] Agent applies and ACKs server desired policy.
- [ ] Sau khi rút USB và cắm lại, quyết định Allow/Revoke mới có hiệu lực trên endpoint; không yêu cầu tác động tức thời lên USB đang cắm.
- [ ] event stored centrally.
- [ ] unknown storage stays fail-closed.
- [ ] Ubuntu graphical user nhận **một native desktop notification** khi unknown Mass Storage bị BLOCK trên máy đó (không phải notification ở Admin Web).
- [ ] GUI không active, `UNKNOWN` hoặc notifier lỗi → không báo sai user; BLOCK/event processing không bị ảnh hưởng.

---


# 26. WEEK 3 — Productization + Per-Endpoint Isolation + `.deb`

## M1

- harden auth/agent token;
- endpoint status/offline logic;
- policy version reconciliation;
- central production profile;
- integrate React build.

## M2

- reboot/reconnect;
- device without serial;
- parser robustness;
- active-user resolver edge cases;
- user switch/session tests;
- help M3 with USBGuard installer config.

## M3

- final systemd/permissions;
- `.deb` agent;
- enrollment config;
- clean install;
- đóng gói `libnotify-bin`, user-session notifier launcher (XDG autostart) và local IPC permissions thành `.deb`;
- retry behavior;
- server-down fail-safe test.

## M4

- complete Admin UI;
- endpoint whitelist UX;
- auth UX;
- errors/policy-sync status.

## M5

- full regression;
- clean install;
- reboot;
- user attribution;
- per-endpoint isolation;
- server unavailable;
- uninstall/reinstall;
- Demo Script draft.

### WEEK 3 GATE

Một clean Ubuntu client phải:

```text
install .deb
→ register Admin Server
→ appear ONLINE
→ block unknown USB
→ send username event
→ receive per-endpoint policy
→ survive reboot
```

---


# 27. WEEK 4 — Stabilization, Documentation, Demo, Release

Không thêm feature lớn.

### M1

- API freeze;
- security/code review;
- Architecture/Developer Guide;
- final server JAR/tag.

### M2

- stress reconnect;
- validate USBGuard policy;
- validate active-user attribution;
- document class 08/rule order/hash/session resolution;
- prepare technical Q&A.

### M3

- final `.deb`;
- clean install final;
- service/reboot/logs;
- Client Installation Guide.

### M4

- UI bug fixes;
- Admin Guide/screenshots;
- no mock data.

### M5

- final test matrix;
- demo rehearsal;
- backup evidence/video;
- release validation.

### RELEASE GATE

- [ ] Admin auth PASS.
- [ ] endpoint enrollment/heartbeat PASS.
- [ ] USB core PASS.
- [ ] correct active username in event PASS.
- [ ] per-endpoint whitelist PASS.
- [ ] endpoint isolation PASS.
- [ ] reboot/service protection PASS.
- [ ] `.deb` clean install PASS.
- [ ] docs/demo PASS.

---


# 28. Integration dependencies giữa thành viên

```text
M2 USBGuard + Active User Core
          │
          ▼
M3 Client Agent / Policy Sync / Packaging
          │
          ├──────────────► M1 Admin Server / DB / APIs
          │                         │
          │                         ▼
          │                  M4 Admin Web
          │                         │
          └──────────────┬──────────┘
                         ▼
                   M5 E2E QA / Events
```

Integration phải bắt đầu từ Week 1–2, không đợi tuần cuối.

---


# 29. Git workflow (proposal lịch sử, không mặc định là Git branching policy hiện hành)

Đề xuất lúc lập kế hoạch:

```text
main
develop
feature/<issue>-<short-name>
```

Ví dụ:

```text
feature/12-usbguard-list-devices
feature/18-policy-authorization
feature/25-event-history-ui
```

Rule:

1. Không push feature trực tiếp `main`.
2. Pull Request vào `develop`.
3. Ít nhất 1 member review.
4. `main` chỉ nhận release candidate ổn định.
5. Mọi bug quan trọng tạo GitHub Issue.
6. Commit nhỏ, mô tả đúng việc.

Không giữ code một tuần trên máy cá nhân mới push.

---

# 30. Test matrix tối thiểu

| ID | Test | Expected |
|---|---|---|
| T01 | Client enroll | endpoint xuất hiện |
| T02 | Heartbeat | endpoint ONLINE |
| T03 | `student01` cắm unknown Flash A | local BLOCK |
| T04 | Event T03 | Admin thấy endpoint + `student01` + USB + BLOCKED |
| T05 | Mouse/keyboard USB | hoạt động |
| T06 | Admin Allow A trên Endpoint 1 | `desiredPolicyVersion` tăng; không khẳng định trạng thái USB đang cắm đã đổi |
| T07 | Agent 1 sync policy | local rule cập nhật, `appliedPolicyVersion`/ACK xác nhận; chưa yêu cầu USB đang cắm ALLOWED |
| T08 | Rút A rồi cắm lại trên Endpoint 1 sau T07 | lần cắm mới ALLOWED |
| T09 | Cùng A cắm trên Endpoint 2 (không whitelist) | vẫn BLOCKED |
| T10 | Admin Revoke A trên Endpoint 1 → Agent sync/ACK → rút rồi cắm lại A | lần cắm mới BLOCKED; không kiểm tra block tức thời |
| T11 | Client reboot | Agent/USBGuard auto-start |
| T12 | Whitelist sau reboot | vẫn đúng |
| T13 | User switch sang `student02`, cắm USB | event ghi `student02` |
| T14 | Không có active local session | username UNKNOWN/NONE |
| T15 | Disconnect | DISCONNECTED event |
| T16 | Agent crash | systemd restart |
| T17 | Admin Server down | local USBGuard vẫn enforce |
| T18 | Normal user stop Agent | bị từ chối |
| T19 | Normal user sửa rules/config | permission denied |
| T20 | Invalid agent token | server reject |
| T21 | Admin chưa login gọi Admin API | 401/403 |
| T22 | Fresh `.deb` install | success |
| T23 | Uninstall | service/app remove đúng |
| T24 | USBGuard unavailable | Agent/Admin báo UNAVAILABLE/DEGRADED/UNKNOWN theo dữ liệu thực và staleness, không fake HEALTHY |
| T25 | User GUI `student01` cắm Mass Storage lạ | USBGuard BLOCK và **một native desktop notification** hiện đúng session `student01` |
| T26 | USB Mass Storage đã Allow, HID mouse hoặc Remove | Không hiển thị cảnh báo BLOCK |
| T27 | Mất GUI/no active local session/notifier unavailable | Không báo nhầm session; USBGuard tiếp tục enforce; event vẫn được xử lý |
| T28 | Insert bị BLOCK sinh PolicyChanged/PolicyApplied | Chỉ một notification; không duplicate/spam |

---


# 31. Demo script đề xuất

## Bước 1 — Admin Web

Admin login, mở Endpoints và cho thấy `LAB-PC-01` ONLINE.

## Bước 2 — Client user

Trên Ubuntu Client đăng nhập:

```text
student01
```

Cho thấy:

```bash
systemctl status usbguard
systemctl status usbshield-agent
```

## Bước 3 — USB lạ

Cắm Flash Disk chưa whitelist.

Client:

```text
storage không usable
USBGuard = BLOCK
Ubuntu GUI: USBShield — USB Mass Storage bị chặn
Thiết bị lưu trữ USB chưa được cấp phép. Vui lòng liên hệ quản trị viên.
```

Notification được bắt đầu triển khai từ Week 2, hiển thị đúng active desktop user; không bật cửa sổ modal. Admin Web phải xuất hiện event:

```text
LAB-PC-01 | student01 | Kingston | CONNECTED | BLOCKED
```

Đây là demo trực tiếp yêu cầu bổ sung của thầy.

## Bước 4 — Peripheral

Cho thấy mouse/keyboard USB vẫn hoạt động.

## Bước 5 — Per-endpoint whitelist

Admin:

```text
Endpoints → LAB-PC-01 → Kingston → Allow
```

Đợi Agent đồng bộ local rule và Admin Web hiển thị đúng `appliedPolicyVersion`/ACK. **Rút USB Kingston ra, cắm lại** trên LAB-PC-01 rồi chứng minh lần cắm mới `ALLOWED`.

Tiếp tục **Revoke** Kingston trên LAB-PC-01 → đợi Agent đồng bộ và ACK → rút rồi cắm lại → chứng minh `BLOCKED`. Không thực hiện hoặc tuyên bố `allow-device`/`block-device` tức thời trên thiết bị đang cắm.

Nhấn mạnh:

> Rule chỉ thuộc LAB-PC-01.

Nếu có Endpoint 2, dùng cùng USB trên Endpoint 2 để chứng minh vẫn BLOCK.

## Bước 6 — Reboot/fail-safe

Reboot client; services + whitelist còn đúng.

Có thể chứng minh server tạm DOWN nhưng USBGuard local vẫn block unknown device.

## Bước 7 — Architecture

```text
USBGuard = enforcement
Client Agent = event/user attribution/policy sync
Spring Boot Admin Server = central control/persistence
React = Admin Web
systemd = client lifecycle
.deb = client distribution
```

---


# 32. Những lỗi thiết kế cần tránh

1. Chỉ unmount USB sau khi mount — phải authorization bằng USBGuard.
2. Block toàn bộ USB — sẽ phá peripheral.
3. Chỉ đổi DB status — Linux vẫn dùng được thì vô nghĩa.
4. Global whitelist vô tình áp cho mọi endpoint — trái scope đã chốt.
5. Event chỉ có USB nhưng không có endpoint/user — không đáp ứng yêu cầu mới.
6. Gọi `whoami` trong Agent để suy ra user — chỉ ra service user, không phải active desktop user.
7. Khi nhiều session mà tự chọn bừa username — phải dùng active/local session logic hoặc UNKNOWN.
8. Cho client mở inbound admin API không cần thiết — ưu tiên Agent pull policy.
9. Agent/Server down làm USB fail-open — enforcement phải nằm local USBGuard.
10. Chạy toàn Agent bằng root chỉ vì dễ.
11. Đợi Week 4 mới test network/clean install.
12. Tuyên bố root không thể tắt service.

---


# 33. Risk register

## R1 — USB passthrough không ổn định
High. Test thật từ Week 1.

## R2 — Policy block nhầm peripheral
High. Base rule allow non-storage; test mouse/keyboard.

## R3 — Active-user attribution sai
High. Không dùng `whoami`; dùng logind session state, test user switch, UNKNOWN khi ambiguous.

## R4 — Per-endpoint policy bị áp nhầm máy
High. Mọi whitelist row phải có `endpoint_id`; Agent chỉ fetch policy của identity mình.

## R5 — Agent token bị dùng sai
Medium/High. Token riêng từng endpoint, root-owned config, server validate.

## R6 — Admin Server mất kết nối
Medium. USBGuard local vẫn enforce; Agent retry heartbeat/event.

## R7 — `.deb` fail trên clean client
High. Clean VM từ Week 3.

## R8 — PostgreSQL tăng phụ thuộc triển khai so với H2
Medium cho demo: database service, role/secret, connectivity/permissions, backup và kiểm thử tích hợp. Giảm rủi ro bằng database test riêng; không cho Client kết nối trực tiếp PostgreSQL.

## R9 — Group merge cuối kỳ
High. Contract freeze Week 1, integration Week 2.

## R10 — Thiếu hardware USB
High. Có ít nhất một Flash Disk thật; test multiple device nếu mượn được.

## R11 — Không hiển thị notification vì Agent là system service
High. `usbshield` system user không có mặc định graphical user-session D-Bus. M2 viết notifier trong user session, M3 đảm nhiệm khởi chạy/permissions local IPC và `libnotify-bin`; test trên Ubuntu 24.04 desktop thật. Failure chỉ làm mất notification, không làm fail-open.

## R12 — M2/M3 trùng ownership Java Client Core
High. Week 2 bàn giao nguyên trạng class dưới `client-agent/.../usbguard/` và `session/` cho M2; M3 ngừng sửa hai package, chỉ tích hợp qua API/contract và M3-owned shell/sync/packaging.

---


# 34. Developer Guide phải có gì?

`docs/DEVELOPER_GUIDE.md`

- architecture Client Agent ↔ Admin Server;
- Admin Server local setup;
- Client Agent local setup;
- USBGuard config/rules/IPC;
- `loginctl`/logind active-user resolution;
- cách triển khai `notification/`, UID-targeting, local IPC và `notify-send` trong user graphical D-Bus (Week 2);
- endpoint enrollment/token;
- event payload;
- policy version/sync;
- PostgreSQL schema;
- React routes/API;
- build server JAR;
- build client `.deb`;
- debug network/Agent/USBGuard;
- common failures.

---


# 35. Admin/User Guide phải có gì?

## `ADMIN_GUIDE.md`

1. Start Admin Server.
2. Open Admin Web.
3. Login.
4. View endpoint ONLINE/OFFLINE.
5. View USB event + Linux username.
6. Select endpoint.
7. Allow USB on this endpoint.
8. Revoke.
9. Event filters.
10. Troubleshooting policy sync.

## `RUN_GUIDE.md`

1. Ubuntu requirements.
2. Install `.deb`.
3. Set Admin Server URL/enrollment token.
4. Verify Agent/USBGuard services.
5. Verify endpoint appears on Admin Web.
6. Check `libnotify-bin` / desktop notifier autostart và test cảnh báo khi USB bị BLOCK.
7. Troubleshoot trường hợp không có graphical session hoặc không hiển thị notification.
8. Uninstall.

Normal Ubuntu user không cần mở USBShield UI; cảnh báo là desktop notification tự động.

---


# 36. README cuối cùng nên cực kỳ rõ

```text
Project overview
Use case: school/company/hospital endpoints
Architecture
Core features
Admin Server quick start
Client .deb quick install
Demo screenshots
Docs links
Team
```

Nên có sơ đồ:

```text
Ubuntu Client Agent → Admin Server → React Admin Web
```

---


# 37. Tiêu chí để biết project “đủ nặng”

Độ sâu hiện tại gồm:

```text
USB hardware
→ Linux USB subsystem
→ USBGuard authorization
→ active Linux session attribution
→ Agent/systemd/privilege
→ Agent↔Server protocol
→ per-endpoint policy sync
→ central persistence
→ Spring Security Admin auth
→ React Admin Web
→ Debian packaging
```

Không cần thêm AI/Cloud/microservices để làm đề tài “khó hơn”.

---


# 38. Priority order khi thiếu thời gian

## P0 — không cắt

1. Mass Storage detection.
2. Unknown storage BLOCK.
3. Peripheral unaffected.
4. Active Ubuntu user attribution.
5. Event gửi về Admin Server.
6. Endpoint identification.
7. Per-endpoint Allow/Revoke.
8. Local policy persistence/fail-safe.
9. Agent systemd + normal-user restriction.
10. Client `.deb`.
11. Admin Web tối thiểu xem endpoint/event và policy.
12. Docs/test.
13. Ubuntu Client native BLOCK notification cho active graphical user (bắt đầu Week 2; không hồi tố Week 1).

## P1

- charts;
- advanced filters;
- rich audit UI;
- durable outbox nâng cao.

## P2

- SSE/WebSocket realtime;
- desktop notification;
- CSV export;
- animations.

---


# 39. First 2 days — checklist khởi động

## Day 1

- [ ] Repo + M1–M5.
- [ ] Freeze Ubuntu 24.04.
- [ ] Có ít nhất 1 USB Flash Disk thật.
- [ ] M2 USBGuard environment.
- [ ] M1 Admin Server skeleton.
- [ ] M3 Agent skeleton.
- [ ] M4 Admin Web skeleton.
- [ ] Chốt event DTO có endpoint + user + USB + decision.

## Day 2

- [ ] M2 unknown Flash BLOCK.
- [ ] M2 mouse/keyboard ALLOW.
- [ ] M2 `loginctl` active-user PoC.
- [ ] M3 POST dummy event/heartbeat tới M1 server.
- [ ] M1 persist dummy event.
- [ ] M4/M5 wireframe endpoint/event UI.
- [ ] Commit PoC evidence.

---


# 40. Câu trả lời kỹ thuật ngắn khi giảng viên hỏi

## “Tại sao dùng USBGuard?”

USBGuard là enforcement framework chuyên dụng cho Linux USB authorization; nhóm không tự viết lại kernel-level authorization.

## “Làm sao biết người nào cắm USB?”

Khi USB event xảy ra, Client Agent lấy **active local Ubuntu session** từ systemd-logind/loginctl và gắn username/UID/session ID vào event. Đây là attribution theo session đang active, không phải xác thực danh tính vật lý người cắm.

## “Admin chạy app hay web?”

Admin dùng **React Web Console** trên Admin Server. Client Ubuntu chỉ chạy Agent dưới systemd, normal user không cần mở UI.

## “Whitelist áp thế nào?”

Whitelist **theo endpoint**. Cùng một USB được Allow ở PC-A vẫn có thể bị Block ở PC-B. Sau khi Admin Allow/Revoke và Agent đồng bộ/ACK, người dùng **rút USB rồi cắm lại** để USBGuard áp quyết định mới; demo không hỗ trợ đổi trạng thái tức thời trên USB đang cắm.

## “Spring Boot làm gì?”

Có hai vai trò: Admin Server quản lý auth/endpoints/events/policy; Client Agent tích hợp USBGuard, resolve active user và đồng bộ policy.

## “Tại sao Agent pull policy?”

Client không cần mở inbound admin port. Agent outbound heartbeat/pull desired policy của chính endpoint rồi reconcile USBGuard local rules.

## “Tại sao dùng PostgreSQL thay H2?”

Theo yêu cầu giảng viên, nhóm chuyển central persistence sang PostgreSQL. Spring Data JPA giữ phần lớn entity/repository; PostgreSQL quản lý database độc lập với Admin Server JAR, phù hợp kiểm tra SQL/transaction và backup. Đổi lại cần cài dịch vụ DB, cấp role và vận hành an toàn. **Không tuyên bố HA/throughput enterprise hay Ubuntu E2E chỉ vì đổi database.**

## “Dịch vụ có thật sự không tắt được?”

Normal user không được stop/sửa; root/sudo vẫn kiểm soát hệ thống.

---


# 41. Official references nên đọc

## USBGuard

- https://usbguard.github.io/
- https://usbguard.github.io/documentation/rule-language
- https://usbguard.github.io/documentation/configuration
- https://packages.ubuntu.com/usbguard

## systemd / login sessions

- `man loginctl`
- `man systemd-logind.service`
- https://www.freedesktop.org/software/systemd/man/latest/loginctl.html

`loginctl show-session` dùng cho machine-parsable session properties.

## Ubuntu Java

- https://packages.ubuntu.com/noble/openjdk-21-jre-headless

## Debian packaging

- https://wiki.debian.org/Packaging/Intro
- https://www.debian.org/doc/manuals/debian-reference/ch02

## Spring

- Spring Boot documentation
- Spring Security documentation

---


# 42. Final project checklist (DoD mục tiêu, không tự đánh dấu PASS)

> Các checkbox chưa tick là **điều kiện nghiệm thu cần evidence**, không chứng minh chức năng chưa có code. Owner 09/11 đã báo Minimal Core P0+C1–C5 PASS trên baseline lúc đó; các bài native extended còn deferred và HEAD 20/11 chưa có log rerun. Xem bảng trạng thái ở đầu blueprint.

## Client core

- [ ] class 08 detection.
- [ ] unknown storage BLOCK.
- [ ] mouse/keyboard unaffected.
- [ ] USBGuard event listener.
- [ ] active-user resolver.
- [ ] user/session fields correct.
- [ ] local policy persist.
- [ ] blocked Mass Storage emits exactly one desktop notification to correct active GUI user (Week 2+).
- [ ] no false alerts for HID/ALLOWED/Remove and no fail-open if GUI unavailable.

## Agent

- [ ] enrollment/token.
- [ ] heartbeat.
- [ ] event sender/retry.
- [ ] policy pull/sync.
- [ ] policy ack/version.
- [ ] systemd.
- [ ] normal-user restriction.
- [ ] `.deb`.
- [ ] `libnotify-bin` and user-session notifier autostart/secure IPC packaged.

## Admin Server

- [ ] Admin login.
- [ ] endpoint registry.
- [ ] events central DB.
- [ ] per-endpoint whitelist.
- [ ] endpoint policy version.
- [ ] Agent auth.
- [ ] API tests.

## Admin Web

- [ ] Login.
- [ ] Dashboard.
- [ ] Endpoints.
- [ ] Endpoint Detail.
- [ ] Per-endpoint whitelist.
- [ ] Event History.
- [ ] Linux username visible.
- [ ] filters/error/loading.

## End-to-end

- [ ] real user plugs USB.
- [ ] local BLOCK.
- [ ] Admin sees correct endpoint + user.
- [ ] Allow Endpoint A only.
- [ ] Agent A sync/ACK đúng policy version.
- [ ] Rút USB, cắm lại trên Endpoint A → ALLOW.
- [ ] Revoke trên A → Agent sync/ACK → rút và cắm lại → BLOCK.
- [ ] Endpoint B unchanged (vẫn BLOCK khi không whitelist).
- [ ] reboot PASS.
- [ ] server-down fail-safe PASS.

## Documentation

- [ ] README.
- [ ] Architecture.
- [ ] Developer Guide.
- [ ] USBGuard Core.
- [ ] Agent–Server Protocol.
- [ ] Admin Guide.
- [ ] Client Installation.
- [ ] API.
- [ ] Test Plan.
- [ ] Demo Script.

---


# 43. Kết luận

Scope mới của USBShield là một hệ thống **client-agent + central Admin web** vừa đủ cho 5 người/4 tuần:

```text
Ubuntu user cắm USB
↓
USBGuard local enforce
↓
Client Agent gắn active Ubuntu user vào event
↓
Admin Server nhận endpoint + user + USB + decision
↓
Admin Web quan sát và thay whitelist theo từng endpoint
↓
Agent endpoint đó đồng bộ local policy và gửi ACK
↓
Người dùng rút USB, cắm lại
↓
USBGuard áp quyết định mới trên lần cắm kế tiếp
```

Điểm quan trọng nhất:

- enforcement luôn ở client;
- Admin quản lý tập trung;
- user attribution dựa trên active Ubuntu session;
- whitelist **không global**, mà gắn với từng endpoint;
- Allow/Revoke áp qua policy sync, **kiểm chứng sau khi rút và cắm lại**, không đổi trạng thái tức thời USB đang cắm;
- normal user không bypass protection;
- client phân phối bằng `.deb`;
- Admin dùng web browser.

Nếu flow trên chạy ổn định với USB thật và ít nhất một Ubuntu client clean install, project đáp ứng cả yêu cầu ban đầu lẫn yêu cầu bổ sung mới của giảng viên.

---



Trong 4 tuần, nhóm không đặt mục tiêu xây một hệ thống EDR/DLP quy mô doanh nghiệp.

Mục tiêu là tạo một **Ubuntu USB protection product hoàn chỉnh theo chiều sâu**:

```text
install được
↓
tự chạy
↓
phân biệt USB storage
↓
block thật
↓
không phá keyboard/mouse
↓
người có quyền quản trị Ubuntu quản lý được
↓
policy tồn tại sau reboot
↓
normal user không vô hiệu hóa được
↓
có UI + API + log
↓
có package + docs + test
```

Nếu toàn bộ flow trên chạy ổn định từ `.deb` trên Ubuntu sạch **và được ghi bằng chứng cho đúng artifact/source**, project đáp ứng trọng tâm kỹ thuật đề tài. Tại baseline 09/11 chỉ có **Minimal Core P0+C1–C5 PASS theo báo cáo owner**; không suy ra full Week 4/native extended acceptance hoặc chất lượng production trên HEAD 20/11.
