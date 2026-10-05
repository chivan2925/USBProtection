# USBShield Ubuntu — Project Blueprint

> **Đề tài:** Xây dựng hệ thống ngăn chặn và quản lý USB Flash Disk trên hệ điều hành Ubuntu  
> **Thời gian:** 4 tuần  
> **Nhân sự:** 5 thành viên  
> **Frontend:** React + Vite + TypeScript — Admin Web Console  
> **Backend:** Java 21 + Spring Boot — Admin Server + Ubuntu Client Agent  
> **Hệ điều hành client mục tiêu MVP:** Ubuntu 24.04 LTS amd64  
> **Cơ chế enforcement trên client:** USBGuard + systemd  
> **Database Admin Server:** H2 File Mode (embedded) cho MVP  
> **Phân phối client:** `usbshield-agent_1.0.0_amd64.deb`  
> **Java runtime trên client:** phương án A — `.deb` khai báo `openjdk-21-jre-headless` là runtime dependency, người dùng không phải tự cài Java bằng tay.

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


# 2. Tên đề tài nhóm chốt

## Xây dựng hệ thống ngăn chặn và quản lý USB Flash Disk trên hệ điều hành Ubuntu

Tên sản phẩm nội bộ có thể dùng:

**USBShield Ubuntu**

Tên này không làm thay đổi bài toán giảng viên giao. Phần “quản lý” được bổ sung để sản phẩm có giao diện quản trị, whitelist và lịch sử hoạt động thay vì chỉ có một script chặn USB.

---

# 3. Phát biểu bài toán

USB Flash Disk có thể được dùng để đưa dữ liệu không kiểm soát vào máy hoặc sao chép dữ liệu ra ngoài. Trong môi trường trường học/công ty/bệnh viện, người dùng cuối không nên tự ý sử dụng USB Mass Storage chưa được cấp phép.

USBShield cần giải quyết đồng thời 4 bài toán:

1. **Enforcement tại endpoint:** unknown USB Mass Storage bị BLOCK thật bằng USBGuard.
2. **Không phá peripheral:** keyboard/mouse và USB không phải Mass Storage không bị ảnh hưởng.
3. **User attribution:** khi có USB event, Agent xác định tài khoản Ubuntu đang active.
4. **Central administration:** Admin Web xem event của các endpoint và quản lý whitelist **riêng cho từng endpoint**.

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
Agent cập nhật USBGuard local policy
        ↓
LAB-PC-01: Kingston → ALLOW
LAB-PC-02: Kingston → vẫn BLOCK
```

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

Agent nên retry gửi event khi server quay lại. Nếu chưa hoàn thiện durable outbox trong MVP, phải ghi rõ giới hạn và tuyệt đối không làm mất khả năng enforcement local.

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


# 6. Scope chính thức — 16 hạng mục bắt buộc

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

MVP dùng H2 File Mode trên Admin Server để giảm deployment complexity. Nếu mở rộng thực tế nhiều endpoint/production mới chuyển PostgreSQL.

USBGuard local policy vẫn là enforcement source trên client.

## 13. Client `.deb` installer + uninstall

```text
usbshield-agent_1.0.0_amd64.deb
```

Dependencies:

```text
openjdk-21-jre-headless
usbguard
```

Installer phải cấu hình:

- Agent;
- systemd;
- USBGuard base policy;
- server URL/enrollment information;
- permissions.

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
→ Agent A syncs
→ Endpoint A ALLOW
→ Endpoint B unchanged
```

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
- chứng minh danh tính vật lý của người cắm USB ngoài active Ubuntu session.

Bonus nếu core xong sớm:

- realtime Admin UI bằng SSE/WebSocket;
- export CSV;
- durable agent outbox/retry nâng cao;
- desktop notification cho Admin;
- charts.

---


# 8. Definition of Done

## DoD-01: Admin Server

```text
Spring Boot Admin Server start
→ React Web truy cập được
→ Admin login thành công
→ H2 persistence hoạt động
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
→ Agent LAB-PC-01 sync policy
→ USB X ALLOWED tại LAB-PC-01
```

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

---


# 9. Kiến trúc tổng thể

```text
                         ADMIN MACHINE / SERVER
┌──────────────────────────────────────────────────────────┐
│ React Admin Web                                          │
│ Login | Endpoints | Endpoint Detail | Events | Whitelist │
│                         │                                │
│                         ▼                                │
│              Spring Boot Admin Server                    │
│ Auth | Endpoint | Event | Policy | Audit | Agent API     │
│                         │                                │
│                         ▼                                │
│                    H2 File DB                            │
└─────────────────────────┬────────────────────────────────┘
                          │ LAN / HTTP(S)
              events ↑    │    ↓ policy/heartbeat response
                          │
             ┌────────────┴─────────────┐
             │                          │
             ▼                          ▼
      UBUNTU CLIENT A             UBUNTU CLIENT B
┌──────────────────────┐    ┌──────────────────────┐
│ student01 active     │    │ employee07 active    │
│                      │    │                      │
│ USBShield Agent      │    │ USBShield Agent      │
│ ├─ USB event listener│    │ ├─ user resolver     │
│ ├─ ActiveUserResolver│    │ ├─ policy sync       │
│ ├─ PolicySync        │    │ └─ event sender      │
│ └─ EventSender       │    │          │           │
│          │           │    │          ▼           │
│          ▼           │    │      USBGuard        │
│      USBGuard        │    │          │           │
│          │           │    │          ▼           │
│          ▼           │    │       USB device     │
│       USB device     │    └──────────────────────┘
└──────────────────────┘
```

## Policy sync model

Admin Server giữ **desired whitelist theo endpoint**.

Mỗi Agent định kỳ heartbeat/pull policy version:

```text
Agent local policyVersion = 4
Server desired policyVersion = 5
        ↓
Agent fetch whitelist for itself
        ↓
reconcile USBGuard rules
        ↓
ack version 5
```

Mô hình pull giúp client không cần mở public inbound management port.

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

## H2

Chỉ là central persistence cho MVP. Không phải source of enforcement; USBGuard local policy mới quyết định thiết bị có dùng được hay không.

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

Không chỉ dùng `VID:PID`, vì nhiều thiết bị cùng model có thể giống nhau.

Thông tin lưu:

```text
vendor_id
product_id
serial_number
device_hash
name
interfaces
```

Ưu tiên fingerprint:

1. USBGuard hash;
2. serial + VID + PID;
3. các attributes khác nếu thiết bị thiếu serial.

App không nên coi tên USB là unique identifier.

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

Agent gửi:

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


# 14. Tech stack chính thức

| Layer | Công nghệ | Lý do |
|---|---|---|
| Client OS | Ubuntu 24.04 LTS amd64 | Freeze target |
| Admin Frontend | React + Vite + TypeScript | Central web console |
| UI | MUI hoặc Ant Design | Dashboard nhanh |
| Admin Server | Java 21 + Spring Boot | REST, Security, persistence |
| Client Agent | Java 21 + Spring Boot hoặc Spring Boot non-web profile | Reuse Java/Spring stack |
| Admin Auth | Spring Security + session/cookie | Web cùng origin, đơn giản hơn JWT |
| Agent Auth | Endpoint token / enrollment token | Xác thực client |
| ORM | Spring Data JPA | Central persistence |
| DB MVP | H2 File Mode trên Admin Server | Ít deployment dependency |
| User attribution | systemd-logind / `loginctl` MVP | Active Linux session |
| USB enforcement | USBGuard | Linux USB authorization |
| Client lifecycle | systemd | Auto-start/restart |
| Client packaging | Debian `.deb` | Native Ubuntu install |
| Java client runtime | `openjdk-21-jre-headless` dependency | APT xử lý |
| API docs | springdoc/OpenAPI | Dev contract |
| Testing | JUnit 5 + Mockito + Spring Boot Test | Unit/integration |
| Hardware test | USB thật + VMware passthrough | Core requirement |
| VCS | Git + GitHub | Collaboration |

> Freeze major versions từ Week 1.

---


# 15. Vì sao React + Vite thay vì Next.js?

Admin Console là một SPA nội bộ:

```text
http://<admin-server>:8080
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


# 16. Frontend screens — Admin Web

## 16.1. Login

Admin username/password.

## 16.2. Dashboard

Hiển thị:

- endpoint online/offline;
- blocked today;
- recent USB events;
- recent users;
- policy sync failures nếu có.

## 16.3. Endpoints

Columns:

```text
Hostname
Endpoint ID
IP
Status
Last Seen
Policy Version
Last Applied Version
```

## 16.4. Endpoint Detail

Ví dụ `LAB-PC-01`:

- status/heartbeat;
- active/recent users;
- recent USB devices;
- whitelist của **LAB-PC-01**;
- Allow/Revoke trên máy này.

## 16.5. Event History

Filter:

- endpoint;
- Linux username;
- date range;
- USB/device;
- event type;
- ALLOWED/BLOCKED.

Table:

```text
Timestamp | Endpoint | Linux User | USB | Event | Decision
```

## 16.6. Whitelist UX

Admin luôn thao tác trong context endpoint:

```text
Endpoints
→ LAB-PC-01
→ Whitelist
→ Allow SanDisk A
```

Không có nút “Allow on all endpoints” trong MVP.

---


# 17. Backend modules

## 17.1. Admin Server packages

```text
com.group.usbshield.server
├── auth
├── admin
├── endpoint
├── device
├── policy
├── event
├── audit
├── agentapi
├── config
└── common
```

### `auth`

- Admin login/logout;
- password hashing;
- Spring Security session.

### `endpoint`

- register/enroll;
- heartbeat;
- online/offline;
- policy version.

### `policy`

- per-endpoint whitelist;
- desired policy;
- policy version increment;
- applied-version ack.

### `event`

- ingest Client USB events;
- query/filter for Admin.

### `agentapi`

- endpoint authentication;
- heartbeat;
- event ingestion;
- policy sync.

## 17.2. Client Agent packages

```text
com.group.usbshield.agent
├── usbguard
├── session
├── event
├── sync
├── endpoint
├── outbox
├── system
├── config
└── common
```

### `usbguard`

```text
UsbGuardGateway
UsbGuardCliGateway
UsbGuardCommandRunner
UsbGuardOutputParser
UsbGuardEventListener
UsbGuardPolicySynchronizer
```

### `session`

```text
ActiveUserResolver
LoginctlActiveUserResolver
ActiveUserInfo
```

### `sync`

- heartbeat;
- policy version check;
- fetch desired whitelist;
- reconcile USBGuard;
- ack applied version.

### `outbox`

Nếu event POST fail, retry. MVP có thể dùng lightweight disk spool thay vì thêm message broker.

---


# 18. REST API gợi ý

## 18.1. Admin authentication

```http
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/me
```

## 18.2. Admin endpoints

```http
GET /api/endpoints
GET /api/endpoints/{endpointId}
GET /api/endpoints/{endpointId}/events
GET /api/endpoints/{endpointId}/whitelist
POST /api/endpoints/{endpointId}/whitelist
DELETE /api/endpoints/{endpointId}/whitelist/{whitelistId}
```

Whitelist request phải chứa fingerprint/device cụ thể; server luôn gắn `endpointId` từ URL/context.

## 18.3. Event query

```http
GET /api/events?endpointId=&username=&decision=&from=&to=
```

## 18.4. Agent API

```http
POST /api/agent/enroll
POST /api/agent/heartbeat
POST /api/agent/events
GET  /api/agent/policy
POST /api/agent/policy/ack
```

Agent requests dùng endpoint credential/token riêng.

## 18.5. Policy sync semantics

Heartbeat response có thể trả:

```json
{
  "serverPolicyVersion": 5,
  "lastAppliedPolicyVersion": 4
}
```

Agent thấy version khác thì gọi `/api/agent/policy`, apply rồi ack.

---


# 19. Database model gợi ý

## `admins`

```text
id
username
password_hash
created_at
```

## `endpoints`

```text
id
hostname
machine_id
ip_address
agent_version
agent_token_hash
status
last_seen
policy_version
last_applied_policy_version
created_at
```

## `usb_devices`

```text
id
vendor_id
product_id
serial_number
device_hash
device_name
interfaces
```

## `endpoint_whitelist`

```text
id
endpoint_id
usb_device_id
fingerprint_type
fingerprint_value
enabled
created_by_admin_id
created_at
```

Unique logic tối thiểu:

```text
(endpoint_id, fingerprint_value)
```

Đây là điểm bảo đảm whitelist **per endpoint**.

## `usb_events`

```text
id
endpoint_id
usb_device_id
linux_username
linux_uid
session_id
session_type
seat
event_type
decision
occurred_at
received_at
details
```

## `audit_logs`

```text
id
admin_id
action
endpoint_id
target_type
target_id
details
occurred_at
```

### Important

Central DB không phải nơi duy nhất quyết định enforcement. Agent phải materialize desired whitelist thành local USBGuard policy; policy local tiếp tục chạy khi server mất kết nối.

---


# 20. Packaging và deployment

## 20.1. Client Ubuntu

Admin/IT cài:

```bash
sudo apt install ./usbshield-agent_1.0.0_amd64.deb
```

Package dependency:

```text
openjdk-21-jre-headless
usbguard
```

Client sau cài có:

```text
/opt/usbshield-agent/usbshield-agent.jar
/etc/usbshield-agent/application.yml
/var/lib/usbshield-agent/
/usr/lib/systemd/system/usbshield-agent.service
/etc/usbguard/rules.conf
```

Config tối thiểu:

```yaml
usbshield:
  server-url: http://<admin-server>:8080
  enrollment-token: ...
```

Sau enrollment lưu endpoint credential ở root-owned config/data.

## 20.2. Admin Server

MVP có thể chạy trên laptop Admin/development machine:

```bash
java -jar usbshield-server.jar
```

JAR chứa React production build và H2 central DB.

Admin mở browser:

```text
http://<admin-server>:8080
```

Trong demo một laptop có thể chạy Admin Server trên host, Ubuntu VM làm Client. Khi demo nhiều endpoint, có thể dùng thêm VM/laptop trong cùng LAN.

## 20.3. Client systemd concept

```ini
[Unit]
Description=USBShield Client Agent
After=network-online.target usbguard.service
Wants=network-online.target
Requires=usbguard.service

[Service]
Type=simple
User=usbshield
ExecStart=/usr/bin/java -jar /opt/usbshield-agent/usbshield-agent.jar
Restart=on-failure
RestartSec=2

[Install]
WantedBy=multi-user.target
```

## 20.4. Install flow

```text
1. APT resolve Java + USBGuard
2. create system user usbshield
3. copy Agent JAR/config
4. configure USBGuard base policy
5. configure permissions/IPC
6. configure Admin Server URL + enrollment
7. install systemd unit
8. enable/start USBGuard
9. enable/start Agent
10. enroll endpoint
11. verify heartbeat on Admin Web
```

## 20.5. Java runtime — phương án A giữ nguyên

Client không bundle JRE trong version đầu; `.deb` khai báo OpenJDK 21 runtime dependency.

---


# 21. Repo structure đề xuất

```text
usbshield/
├── admin-server/
│   ├── pom.xml
│   └── src/
├── client-agent/
│   ├── pom.xml
│   └── src/
├── admin-web/
│   ├── package.json
│   └── src/
├── packaging/
│   ├── agent-deb/
│   ├── systemd/
│   ├── usbguard/
│   └── scripts/
├── docs/
│   ├── ARCHITECTURE.md
│   ├── DEVELOPER_GUIDE.md
│   ├── ADMIN_GUIDE.md
│   ├── CLIENT_INSTALLATION.md
│   ├── USBGUARD_CORE.md
│   ├── AGENT_SERVER_PROTOCOL.md
│   ├── TEST_PLAN.md
│   ├── DEMO_SCRIPT.md
│   └── API.md
├── .github/workflows/
└── README.md
```

---


# 22. Phân chia công việc 5 người

## Member 1 — Admin Server Lead / Architecture / Integration Owner

### Phải làm

- Spring Boot Admin Server structure.
- H2 + JPA schema.
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

### D. Handoff

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

### Deliverables

```text
client-agent.jar
usbshield-agent.service
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


# 23. Documentation ownership

| Tài liệu | Owner | Contributor |
|---|---|---|
| README | M1 | tất cả |
| ARCHITECTURE | M1 | M2, M3 |
| USBGUARD_CORE | M2 | M3 |
| Active-user attribution notes | M2 | M3 |
| AGENT_SERVER_PROTOCOL | M1 | M3, M2 |
| DEVELOPER_GUIDE | M1 | tất cả |
| CLIENT_INSTALLATION | M3 | M2 |
| ADMIN_GUIDE | M4 | M5 |
| TEST_PLAN | M5 | tất cả |
| DEMO_SCRIPT | M5 | M1–M4 |
| API | M1 | M3 |

Ai code module nào phải cung cấp nội dung kỹ thuật của module đó.

---


# 24. Kế hoạch 4 tuần

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
- H2 schema draft;
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
→ Agent syncs
→ local USBGuard policy changes
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
- hardware integration tests.

### M3

- enrollment/token;
- heartbeat;
- event sender/retry;
- policy fetch/version sync;
- ack;
- non-root Agent service draft.

### M4

- login integrate;
- Dashboard/Endpoints integrate;
- Endpoint Detail;
- per-endpoint Allow/Revoke.

### M5

- Event History integrate;
- filters;
- E2E test Client → Server → Web;
- verify correct username attribution.

### WEEK 2 GATE

- [ ] real USB event reaches central server.
- [ ] Event row shows correct endpoint + active Linux user.
- [ ] Admin can Allow/Revoke for selected endpoint.
- [ ] Agent applies server desired policy.
- [ ] event stored centrally.
- [ ] unknown storage stays fail-closed.

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


# 29. Git workflow

Đề xuất:

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
| T06 | Admin Allow A trên Endpoint 1 | policy version tăng |
| T07 | Agent 1 sync policy | A ALLOWED trên Endpoint 1 |
| T08 | Reconnect A trên Endpoint 1 | ALLOWED |
| T09 | Cùng A trên Endpoint 2 | vẫn BLOCKED |
| T10 | Admin Revoke A Endpoint 1 | trở lại BLOCKED |
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
| T24 | USBGuard unavailable | Agent/Admin báo DEGRADED, không fake success |

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
```

Admin Web phải xuất hiện event:

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

Agent sync; reconnect USB và chứng minh ALLOWED.

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

## R8 — H2 không phù hợp quy mô lớn
Low cho seminar. Ghi rõ MVP; production scale mới PostgreSQL.

## R9 — Group merge cuối kỳ
High. Contract freeze Week 1, integration Week 2.

## R10 — Thiếu hardware USB
High. Có ít nhất một Flash Disk thật; test multiple device nếu mượn được.

---


# 34. Developer Guide phải có gì?

`docs/DEVELOPER_GUIDE.md`

- architecture Client Agent ↔ Admin Server;
- Admin Server local setup;
- Client Agent local setup;
- USBGuard config/rules/IPC;
- `loginctl`/logind active-user resolution;
- endpoint enrollment/token;
- event payload;
- policy version/sync;
- H2 schema;
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

## `CLIENT_INSTALLATION.md`

1. Ubuntu requirements.
2. Install `.deb`.
3. Set Admin Server URL/enrollment token.
4. Verify Agent/USBGuard services.
5. Verify endpoint appears on Admin Web.
6. Uninstall.

Normal Ubuntu user không cần mở USBShield UI.

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

Whitelist **theo endpoint**. Cùng một USB được Allow ở PC-A vẫn có thể bị Block ở PC-B.

## “Spring Boot làm gì?”

Có hai vai trò: Admin Server quản lý auth/endpoints/events/policy; Client Agent tích hợp USBGuard, resolve active user và đồng bộ policy.

## “Tại sao Agent pull policy?”

Client không cần mở inbound admin port. Agent outbound heartbeat/pull desired policy của chính endpoint rồi reconcile USBGuard local rules.

## “Tại sao H2?”

MVP seminar có một Admin Server và số endpoint nhỏ. H2 giảm deployment; production scale mới chuyển PostgreSQL.

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


# 42. Final project checklist

## Client core

- [ ] class 08 detection.
- [ ] unknown storage BLOCK.
- [ ] mouse/keyboard unaffected.
- [ ] USBGuard event listener.
- [ ] active-user resolver.
- [ ] user/session fields correct.
- [ ] local policy persist.

## Agent

- [ ] enrollment/token.
- [ ] heartbeat.
- [ ] event sender/retry.
- [ ] policy pull/sync.
- [ ] policy ack/version.
- [ ] systemd.
- [ ] normal-user restriction.
- [ ] `.deb`.

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
- [ ] Endpoint A ALLOW.
- [ ] Endpoint B unchanged.
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
Agent endpoint đó đồng bộ local policy
```

Điểm quan trọng nhất:

- enforcement luôn ở client;
- Admin quản lý tập trung;
- user attribution dựa trên active Ubuntu session;
- whitelist **không global**, mà gắn với từng endpoint;
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

Nếu toàn bộ flow trên chạy ổn định từ file `.deb` trên một máy Ubuntu sạch, project đã đáp ứng đúng trọng tâm đề tài và tạo ra một sản phẩm có thể trình bày, kiểm thử và bàn giao được.
