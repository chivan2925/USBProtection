# USBShield Client Agent — Ubuntu Acceptance Test Checklist (Post-Week 4 Execution)

> **Mục đích:** Chuẩn hóa quy trình kiểm thử nghiệm thu thực tế trên máy trạm Ubuntu 24.04 LTS sau Week 4, bàn giao cho Member 5 (QA) và toàn nhóm.  
> **Căn cứ kế hoạch:** `USBShield_Week4_Work_Plan_M1_M2_M3_M4_M5.md` — Mục 5 (W4-M3-06) và Mục 9.2.  
> **Trạng thái hiện tại:** **SPECIFIED & DEFERRED UNTIL POST-WEEK 4 (Không tự nhận PASS trước khi chạy máy thật)**.  

---

## 1. Môi trường kiểm thử nghiệm thu mục tiêu

* **Hệ điều hành:** Ubuntu 24.04 Desktop LTS (Kernel 6.8+, môi trường đồ họa GNOME / Wayland hoặc X11).
* **Quyền kiểm thử:** Phải có 1 tài khoản quản trị có quyền `sudo`, và ít nhất 1 tài khoản người dùng thường (`UID >= 1000`) đăng nhập phiên đồ họa.
* **Mạng:** Kết nối LAN tới máy chủ Admin Server (đã triển khai JAR với H2 database).
* **Thiết bị USB thử nghiệm:**
  * 01 Chuột hoặc Bàn phím USB (HID Device — Class `03`).
  * 01 USB Flash Drive thông dụng (Mass Storage — Class `08`).
  * 01 USB Flash Drive đã được thêm vào Whitelist theo Endpoint.

---

## 2. Danh mục các ca kiểm thử chấp nhận (Ubuntu Acceptance Test Matrix)

| Mã ca test | Mục tiêu kiểm thử | Các bước thực hiện trên Ubuntu | Kết quả mong đợi | Bằng chứng cần thu thập | Trạng thái Week 4 |
|---|---|---|---|---|---|
| **TC-U01** | Cài đặt mới gói `.deb` sạch sẽ | `sudo apt update`<br>`sudo apt install -y ./usbshield-agent_1.0.0_amd64.deb` | Gói cài đặt thành công không lỗi; tự động kéo Java 21, USBGuard, libnotify. User `usbshield` được tạo. | Terminal log của lệnh `apt install`; output `id usbshield` | `DEFERRED_UBUNTU` |
| **TC-U02** | Xác minh cấu trúc phân quyền hệ thống | `ls -ld /opt/usbshield-agent /etc/usbshield-agent /var/lib/usbshield-agent /run/usbshield-notifier` | `/opt`: 755 root:usbshield<br>`/etc`: 750 root:usbshield<br>`/var/lib`: 700 usbshield:usbshield<br>`/run/usbshield-notifier`: 1777 root:root | Output `ls -ld` và `ls -la` các thư mục | `DEFERRED_UBUNTU` |
| **TC-U03** | Khởi chạy dịch vụ systemd & bảo vệ tiến trình | `sudo systemctl status usbshield-agent`<br>`sudo systemctl status usbguard` | Cả 2 dịch vụ đều ở trạng thái `active (running)`. Tiến trình agent chạy dưới quyền user `usbshield`. | Ảnh chụp hoặc log `systemctl status` | `DEFERRED_UBUNTU` |
| **TC-U04** | Đăng ký Agent tự động (Enrollment) & Lưu trữ bền vững | Cấu hình server URL và token trong `/etc/usbshield-agent/application.yml`, khởi động lại service: `sudo systemctl restart usbshield-agent` | Agent gửi yêu cầu enroll thành công. Tệp `/var/lib/usbshield-agent/endpoint.json` xuất hiện với `endpointId` và `agentToken`. | Nội dung tệp `endpoint.json`; log `journalctl -u usbshield-agent -n 50` | `DEFERRED_UBUNTU` |
| **TC-U05** | Thiết bị HID (Bàn phím/Chuột) hoạt động bình thường | Cắm chuột hoặc bàn phím USB vào máy trạm. | Thiết bị điều khiển chuột/gõ phím hoạt động trơn tru; không bị USBGuard chặn. | Log USBGuard daemon; giao diện người dùng không bị gián đoạn | `DEFERRED_UBUNTU` |
| **TC-U06** | USB Mass Storage lạ bị chặn triệt để (Fail-Closed) | Cắm USB Flash Drive chưa khai báo vào cổng USB. | USB không xuất hiện trong File Manager (Nautilus), không mount phân vùng lưu trữ. Kernel/USBGuard ghi nhận `block`. | Output lệnh `lsblk`; log `journalctl -u usbguard` | `DEFERRED_UBUNTU` |
| **TC-U07** | Nhận diện đúng Active User và Session | Kiểm tra sự kiện cắm USB gửi về Admin Server. | Payload sự kiện ghi nhận chính xác username của user đang đăng nhập (`UID >= 1000`), không bị nhầm lẫn thành root. | Dòng sự kiện trên Admin Server Event History (M5 xác nhận) | `DEFERRED_UBUNTU` |
| **TC-U08** | Desktop Notification cảnh báo chặn trên phiên GUI | Trong phiên đồ họa GNOME của user thường, cắm USB lạ bị chặn. | Thông báo Pop-up xuất hiện góc trên bên phải màn hình: *"Thiết bị USB bị chặn: USB Mass Storage..."*. Không popup với HID. | Ảnh chụp màn hình desktop popup (Screenshot) | `DEFERRED_UBUNTU` |
| **TC-U09** | Xử lý mất mạng (Offline Spooling) | Ngắt cáp mạng hoặc tắt Admin Server. Cắm USB Mass Storage. | Sự kiện được lưu vào `/var/lib/usbshield-agent/spool/event_*.json`. Tiến trình không crash. | Danh sách tệp `ls -la /var/lib/usbshield-agent/spool/` | `DEFERRED_UBUNTU` |
| **TC-U10** | Khôi phục mạng & Drain sự kiện FIFO | Bật lại kết nối mạng tới Admin Server. Chờ 1 chu kỳ heartbeat (5-10s). | Toàn bộ tệp spool được gửi lên server và tự động xóa khỏi đĩa. Thứ tự thời gian được bảo toàn. | Log `[OUTBOX-DRAIN] Successfully delivered`; thư mục `spool/` trống | `DEFERRED_UBUNTU` |
| **TC-U11** | Nạp chính sách Whitelist theo Endpoint | Admin cấp phép (Allow) USB trên Admin Web Console cho Endpoint này. | Heartbeat phát hiện policy version mới; agent nạp rule vào USBGuard; USB được phép truy cập. Endpoint khác không bị ảnh hưởng. | Output `usbguard list-rules`; USB mount thành công | `DEFERRED_UBUNTU` |
| **TC-U12** | Khởi động lại máy trạm (Reboot Resilience) | Khởi động lại máy: `sudo reboot`. | Sau khi reboot, dịch vụ tự chạy lại; chính sách whitelist đã lưu vẫn có hiệu lực; không cần re-enroll. | Trạng thái dịch vụ sau khởi động; `cat /var/lib/usbshield-agent/endpoint.json` | `DEFERRED_UBUNTU` |
| **TC-U13** | Nâng cấp gói phần mềm an toàn (Upgrade) | Cài đặt gói nâng cấp: `sudo apt install ./usbshield-agent_new.deb` | Dịch vụ được khởi động lại; cấu hình và tệp `endpoint.json` không bị ghi đè hay mất mát. | File `endpoint.json` còn nguyên vẹn; endpointId không đổi | `DEFERRED_UBUNTU` |
| **TC-U14** | Gỡ cài đặt hoàn toàn (Uninstall & Purge) | `sudo apt purge -y usbshield-agent` | Dịch vụ dừng và bị gỡ bỏ; script dọn dẹp sạch sẽ tài nguyên. | `systemctl status usbshield-agent` báo `Unit could not be found` | `DEFERRED_UBUNTU` |

---

## 3. Hướng dẫn M5 (QA) thu thập bằng chứng bàn giao

Mỗi ca test khi thực hiện trên Ubuntu 24.04 cần lưu vết vào thư mục `docs/qa/evidence/` kèm theo:
1. File log trích xuất từ `sudo journalctl -u usbshield-agent --since "10 minutes ago" > tc_xxx_agent.log`.
2. File log USBGuard: `sudo journalctl -u usbguard --since "10 minutes ago" > tc_xxx_usbguard.log`.
3. Ảnh chụp màn hình thông báo GUI (nếu là ca TC-U08).
4. Mã băm SHA-256 của gói `.deb` đã dùng để chạy test.
