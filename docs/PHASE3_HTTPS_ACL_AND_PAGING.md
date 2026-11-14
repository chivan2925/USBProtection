# USBShield — Phase 3 HTTPS, network ACL và Admin API pagination

> **Source baseline:** `vinhkhang` `6923be001890bee9a303a8cee8e7dcc2cf2db180` (09/11/2026); đây là **deployment contract**. Owner báo HTTPS/ACL VMnet8 và Ubuntu C1–C5 **PASS**; xem [owner closeout](qa/2026-11-09-ubuntu-minimal-core-owner-acceptance.md), không suy ra từ contract. Hướng dẫn lệnh đầy đủ: [HTTPS `usbprotection.lab` — Windows + Ubuntu A/B](USBShield_HTTPS_ACL_Setup_usbprotection.lab_vinhkhang_4d2216c_20261109.md).

## Network boundary

**Mô hình:** Admin browser và Ubuntu Client → `https://usbprotection.lab:443` trên **Nginx ở Windows Server** → Spring Boot JAR `prod` **HTTP loopback `127.0.0.1:18080`**; PostgreSQL chạy riêng. Client không được truy cập TCP/18080 hoặc TCP/5432 qua LAN. TLS termination ở Nginx là chủ đích; không cần cấu hình hai lớp TLS trên cùng host cho bài lab.

- **Admin workstation source IP `/32`**: proxy cho phép Web/static, `/api/auth/**`, `/api/admin/**` và `/api/agent/**` (Agent API vẫn cần token). Auth/Admin vẫn phải qua Spring Security + CSRF.
- **Ubuntu A/B source IP `/32`**: proxy **chỉ cho phép `/api/agent/**`**, tất cả đường Web/static/Auth/Admin trả **403 từ Nginx**. Không dùng Admin allowlist CIDR rộng `/24` chứa Client; NAT gom tất cả về cùng IP khiến ACL theo source IP không có tác dụng.
- `packaging/nginx/usbshield-https-acl.conf.example` chỉ là **template**, có `@@...@@` và `listen [::]:443 ssl;`; khi làm IPv4-only trên Windows phải chỉnh/bỏ IPv6 listener nếu chưa định nghĩa IPv6 ACL. Trong file site thực ở `C:\nginx`, không được còn placeholder.
- Nginx phải dùng source IP **thật**; không tin `X-Forwarded-For` do Client gửi để quyết định ACL. `USBSHIELD_BIND_ADDRESS=127.0.0.1`, `PORT=18080`, profile `prod` bắt buộc; firewall không cho Client bypass proxy.
- **Không có rate-limiting** theo phạm vi owner chấp nhận; ACL/auth/CSRF và TLS vẫn là điều kiện bắt buộc, nhưng không loại bỏ toàn bộ tấn công từ Admin IP cho phép.

## TLS, certificate và Agent

- Server certificate SAN chứa **`DNS:usbprotection.lab`**; browser và Java Agent phải validate CA chain/hostname. Dùng CA nội bộ lab: chỉ chuyển **CA public PEM** sang Ubuntu A/B; không đưa CA private key/server private key vào package/repo/VM.
- Client `live`: `USBSHIELD_SERVER_URL=https://usbprotection.lab`; tùy CA dùng `USBSHIELD_TLS_CA_CERTIFICATE=/etc/usbshield-agent/usbshield-ca.pem`. Không HTTP fallback, không disable SSL/hostname check hoặc follow redirects mang Agent token.
- `systemd` đọc `/etc/usbshield-agent/agent.env` trước khi drop quyền; file phải root-owned mode `0600`. Đặt đủ URL/CA/enrollment token **trước** `apt install`, vì `postinst` khởi động service ngay. Thông tin enroll cũ không được sao chép giữa VM; reset identity thử nghiệm theo hướng dẫn Minimal Core nếu DB đã được reset.
- Nginx proxy HTTP tới loopback không đồng nghĩa HTTP qua LAN. Nếu Nginx và JAR chạy khác host, phải xem lại bảo mật upstream.

## Acceptance — kiểm từ máy thật, không thay thế bằng `nginx -t`

| Nguồn | Đường đi | Expected |
|---|---|---|
| Admin (source IP thật) | `GET https://usbprotection.lab/` | Web login tải bằng TLS hợp lệ |
| Admin (trước login) | `/api/auth/csrf`, `/api/admin/endpoints/page` | **200**, **401** |
| Ubuntu A, B | `/`, `/api/auth/csrf`, `/api/admin/endpoints/page` | **403**, **403**, **403** ở Nginx |
| Ubuntu A, B | `/api/agent/policy` không token | **401 từ Spring** (proxy cho qua) |
| Ubuntu Agent thực | enroll/heartbeat/policy/ACK/events | HTTPS chain/SAN PASS và API auth PASS |
| Bất kỳ Ubuntu Client | `http://<WINDOWS_PROXY_IP>:18080`, TCP/5432 | Block/refused, không bypass proxy |
| Negative TLS trên VM disposable | Cert/CA/SAN sai | Fail TLS, không fallback |

`packaging/tests/verify-phase3-https-acl.sh` kiểm expected HTTP paths khi chạy riêng từ **Admin** và **Client**. Có thể copy riêng script lên Ubuntu, không cần clone toàn bộ repo. Nếu CA private, truyền `CA_PUBLIC_PEM` vào script. Kiểm `nginx.exe -t` trên Windows chỉ là syntax; xác nhận bằng source-IP và access log thực, không gửi token log ra chat.

## Admin pagination/API contract (đã có trong code)

- `GET /api/admin/endpoints/page?page=0&size=20&q=...&status=online|offline` trả `{content,page,size,totalElements,totalPages,onlineEndpoints,offlineEndpoints}`. Page size tối đa 100; filter và tổng kết quả tại PostgreSQL.
- `GET /api/admin/whitelist/page?page=0&size=20&endpointId=...&q=...` trả `{content,page,size,totalElements,totalPages}`. Tìm/phan trang tại server; không làm N requests để lấy từng USB metadata.
- `GET /api/admin/events` nhận pagination/query bounded (review F06), filter & ordering theo contract trong [API](API.md); không tải dữ liệu không giới hạn. Hệ thống vẫn giữ event lịch sử orphan `usb_device_id` theo [F07 audit contract](qa/FINDING_F07_RELATIONAL_CONTRACT.md).
- Legacy `GET /api/admin/endpoints` còn vì tương thích nhưng giới hạn 100. Admin UI mới không lệ thuộc API danh sách vô hạn. Trạng thái protection `UNKNOWN` không được biểu diễn là Healthy hoặc bảo vệ đã được chứng minh.

## Verification source và giới hạn

Theo **log owner ngày 09/11**, `verify-windows.ps1` PASS (60 Admin tests; 276 Agent tests với 3 Linux-only skips; 116 frontend tests, browser HTTP live PostgreSQL, DEB format/hash). **Riêng thực tế lab:** Owner xác nhận Nginx/CA/Admin browser và ACL `403/403/403/401` từ A/B, backend/DB không bypass được; C1–C5 USB Kingston **PASS theo owner**, không phải independent review. Các tình huống negative TLS, offline/reboot, native DEB remove/upgrade và Linux notifier IPC **DEFERRED BY OWNER / NOT EXECUTED**; Windows verifier không thay thế các gate native này.

Xem [Run Guide](RUN_GUIDE.md), [Implementation status 09/11](IMPLEMENTATION_STATUS_20261109.md) và [Minimal Core E2E](USBShield_Ubuntu24_Minimal_Core_E2E_vinhkhang_f446c04_20261107.md).
