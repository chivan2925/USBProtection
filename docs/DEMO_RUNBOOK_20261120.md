# USBProtection — Kịch bản demo hiện hành (20/11/2026)

> **Source checklist:** `main` `c7381fc7b9ba34aa561083431ce7c68b9a514a51`. Đây là **kịch bản trình diễn dự kiến**, không phải bằng chứng đã thực thi các bước này trên HEAD vừa nêu. [DEMO_SCRIPT.md](DEMO_SCRIPT.md) tiếp tục lưu kịch bản rehearsal Week 4 ngày 28/10–01/11 và kết quả tại thời điểm đó; không sửa hoặc tái gán các log cũ.

## 1. Chuẩn bị trước demo (không thực hiện các bước phá hủy DB)

- Đặt máy Admin Server và Ubuntu A/B trong LAN lab đã được kiểm tra routing. PostgreSQL application DB `usbshield` là **database dùng chạy demo**, khác `usbshield_test` và `usbshield_browser_test` dành cho kiểm thử.
- Trước khi tạo bản JAR phát hành từ source sạch, chạy `packaging/tests/verify-windows.ps1` và `admin-server/build-release.ps1` theo [runbook release](ADMIN_SERVER_RELEASE.md). Báo PASS chỉ cho các gate **thực sự được chạy**; Linux-only skip trên Windows cần ghi nhận tách biệt. **Không** chạy test DB cleanup vào database demo. Bản `releases/usbshield-admin-server_b12bb597d452_20261120T144326Z.jar` có manifest **dirty tree** và không phải build đã release-qualified từ HEAD `c7381fc7`; không sử dụng nó như bằng chứng cho HEAD này.
- Admin Server `prod` phải nạp biến runtime từ `.env.e2e.local` của **máy owner** hoặc môi trường OS/service đã cấu hình. `java -jar --spring.profiles.active=prod` không tự tìm `.env.e2e.local`; thiếu `USBSHIELD_DB_URL` có thể gây lỗi Hikari JDBC URL chưa được resolve.
- Kiểm chứng Nginx HTTPS/CA/SAN, server `127.0.0.1:18080`, Admin IP allowlist và Client IP chỉ vào `/api/agent/**`. PostgreSQL TCP/5432 và backend loopback không được lộ cho Ubuntu Clients; không ghi credentials/private keys vào video.
- Ubuntu A/B phải có Agent `.deb` riêng đúng phiên bản, USBGuard active, hostname/CA trust, token enroll đúng, identity riêng cho từng máy. Không copy `endpoint.json` hoặc spool từ A sang B.
- Dùng USB Mass Storage thật (vd. USB Kingston), kèm một HID không lưu trữ nếu cần minh họa rule non-storage. Không yêu cầu thay đổi quyết định trên USB **đang** cắm; thao tác kiểm tra sau Allow/Revoke là **rút/cắm lại**.

## 2. Khởi động ứng dụng

Tại root repo trên Admin Server trong PowerShell:

```powershell
# Test-only credentials trong .env KHÔNG được dùng để khởi động app.
# File .env.e2e.local chứa runtime settings, không commit/phân phối.
.\scripts\with-env.ps1 -EnvFile .env.e2e.local -Command {
    java -jar admin-server/target/usbshield-admin-server.jar --spring.profiles.active=prod
}
```

Nếu chạy một JAR phát hành có tên kèm commit/timestamp, thay đường dẫn JAR bằng đúng artifact ở `target/release/` hay thư mục nhận file. PostgreSQL phải được khởi động trước, và JAR phải được build từ frontend **hiện hành**. Khi nạp secrets sẵn vào process environment, có thể chạy `java -jar ... --spring.profiles.active=prod` trực tiếp mà không dùng `with-env.ps1`.

## 3. Trình diễn 8–10 phút

| Thời điểm gợi ý | Trình diễn | Tín hiệu cần quan sát |
|---|---|---|
| 0–1 phút | Mở HTTPS Admin origin, đăng nhập và Dashboard | CA/SAN hợp lệ, đăng nhập thành công, số liệu API thật và phân biệt `0` với `—` |
| 1–2 phút | Endpoints, tìm kiếm/phân trang, mở máy A và B | Endpoint ID/hostname riêng, heartbeat ONLINE/OFFLINE và USBGuard health độc lập |
| 2–3 phút | Trạng thái policy version và ACK | Desired khác applied khi chưa có ACK, không gọi ONLINE/ACK là Healthy |
| 3–4 phút | Cắm USB Mass Storage chưa cấp quyền tại A | USBGuard BLOCK ở Ubuntu; server nhận event có endpoint, source timestamp và user/`UNKNOWN` phù hợp |
| 4–6 phút | Allow chỉ tại A, chờ ACK rồi rút/cắm lại tại A | USB được ALLOW trên A khi policy đã áp dụng; không tuyên bố USB vẫn cắm được đổi tức thời |
| 6–7 phút | Cắm cùng USB tại B | B vẫn BLOCK nếu chưa được Allow riêng, chứng minh endpoint isolation |
| 7–8 phút | Revoke tại A, chờ ACK rồi rút/cắm lại | A trở về BLOCK sau replug; không dùng riêng API 200/ACK làm bằng chứng vật lý |
| 8–9 phút | Event Logs: tìm kiếm Linux User, tên/ID USB, Decision BLOCK/ALLOW, phân trang | Backend-filtered results, 10 dòng/trang; ngày lọc từ/đến hợp lệ |
| 9–10 phút | Đăng xuất và kết luận | Đăng xuất kết thúc phiên Admin, đăng nhập lại cần credentials hợp lệ. **Không trình diễn Settings/đổi mật khẩu Web** vì đã xóa route/API; nêu rõ phạm vi Ubuntu extended chưa test. |

## 4. Tùy chọn negative/security checks (chỉ nếu có sẵn môi trường xác minh)

- **Từ máy Ubuntu Client:** thử đường dẫn Web/static, `/api/auth/**`, `/api/admin/**` và kiểm tra ACL chặn đúng nguồn; **không** truyền Admin cookie/token trong request từ Client.
- **Từ máy Admin:** xác minh API Admin trước login trả 401 khi yêu cầu auth; sau login dùng session + CSRF; Agent endpoints vẫn yêu cầu endpoint-bound credential riêng.
- **Không** thử mở port DB hoặc sửa firewall bừa bãi trên máy demo đang chạy. Không in hoặc ghi lại `.env`, private CA keys, password, enrollment token, endpoint secret hay session cookie.
- Nếu demo chỉ hiển thị synthetic HTTP events thì ghi **software HTTP/UI demonstration**, không ghi “USBGuard physical E2E PASS”.

## 5. Kết quả demo và ranh giới evidence

Điền riêng mốc `git rev-parse HEAD`, `git status --short`, `Get-FileHash` của JAR/`.deb`, thời điểm, Windows/verifier logs, Nginx/ACL outputs, Ubuntu/USBGuard logs và verdict từng bước. **Kịch bản chưa thực hiện không được đánh dấu PASS.**

Báo cáo owner 09/11/2026 có Minimal Core P0/C1–C5 PASS ở lab trên baseline lúc đó. Những bài native nâng cao (Agent install/upgrade/remove, offline recovery, reboot, negative TLS, notifier IPC, stress/race) vẫn là **DEFERRED/NOT EXECUTED** trừ khi có log mới xác nhận. Xem [owner acceptance](qa/2026-11-09-ubuntu-minimal-core-owner-acceptance.md), [Ubuntu Minimal Core](USBShield_Ubuntu24_Minimal_Core_E2E_vinhkhang_f446c04_20261107.md) và [Ubuntu acceptance extended](qa/WEEK4_UBUNTU_ACCEPTANCE.md).
