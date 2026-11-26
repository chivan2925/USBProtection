# Cài đặt và vận hành baseline

## Chuẩn bị

Cần máy central Windows với JDK 21, Maven, Node/npm khi build, PostgreSQL và Nginx. Endpoint mục tiêu của gói hiện hành là Ubuntu 24.04 amd64, systemd, USBGuard, OpenJDK 21 JRE headless, libnotify và ACL. Chuẩn bị DNS/hosts, chứng chỉ có SAN đúng DNS name, CA được tin cậy và CIDR cho Admin/Client. Dùng endpoint lab hoặc cửa sổ bảo trì khi thay đổi rules USBGuard.

## Central Windows

1. Từ root repo, chạy `npm --prefix frontend ci` và `npm --prefix frontend run build`. Sau đó chạy `mvn -f admin-server/pom.xml -Pwith-frontend -DskipTests package` để tạo JAR. Đây là build nhanh. Quy trình kèm test và manifest dùng [build-release.ps1](../admin-server/build-release.ps1) với JDK và DB test cấu hình theo [Developer guide](DEVELOPER_GUIDE.md).
2. Tạo database và role PostgreSQL riêng cho USBShield, chỉ truy cập từ central. Role cần quyền tạo và cập nhật schema ứng dụng theo Flyway. Với DB có schema từ trước, theo [hướng dẫn Flyway baseline](../admin-server/ops/POSTGRES_FLYWAY_BASELINE.md) và sao lưu trước maintenance.
3. Cấp cấu hình trong môi trường chạy service/process. Dùng `USBSHIELD_DB_URL`, `USBSHIELD_DB_USERNAME`, `USBSHIELD_DB_PASSWORD`, `USBSHIELD_ENROLLMENT_TOKEN` và `USBSHIELD_ADMIN_USERNAME`/`USBSHIELD_ADMIN_PASSWORD` cho bootstrap Admin. Mật khẩu bootstrap dành cho lần tạo tài khoản đầu. Đặt `USBSHIELD_BIND_ADDRESS=127.0.0.1` và `PORT=18080` để khớp proxy mẫu. Xem [profile prod](../admin-server/src/main/resources/application-prod.yml) và [profile mặc định](../admin-server/src/main/resources/application.yml). Giữ secret trong cấu hình cục bộ được bảo vệ.
4. Chạy `java -jar admin-server/target/usbshield-admin-server.jar --spring.profiles.active=prod` từ repository root hoặc dùng đường dẫn JAR phát hành tương ứng. Kiểm tra tên chính xác trong `target/` hay manifest của gói tải về.
5. Điền tất cả placeholder trong [Nginx template](../packaging/nginx/usbshield-https-acl.conf.example). Kiểm tra `nginx -t` trước reload. Mở HTTPS 443 theo CIDR cần thiết và giữ backend/DB trên loopback. Từ mạng Admin, vào `https://<dns-name>/#/login`. Từ mạng Client, xác minh route Agent chịu ACL và token, route Admin bị từ chối.

Nếu quên mật khẩu, dừng tiến trình central và dùng cùng cấu hình DB prod để chạy JAR với `--spring.profiles.active=prod,admin-reset`. Cấp mật khẩu mới trong `USBSHIELD_ADMIN_RESET_PASSWORD` và chọn tài khoản bằng `USBSHIELD_ADMIN_RESET_USERNAME` nếu cần. [Profile reset](../admin-server/src/main/resources/application-admin-reset.yml) chạy non-web và [runner](../admin-server/src/main/java/com/group/usbshield/server/auth/AdminPasswordResetRunner.java) vô hiệu hóa các phiên cũ sau khi lưu mật khẩu. Bỏ biến reset và khởi động lại với riêng profile `prod`.

## Endpoint Ubuntu

1. Dùng gói có manifest và checksum. Builder Ubuntu chạy `bash packaging/scripts/build-deb.sh` tại repo root; builder Windows có [build-deb.ps1](../packaging/scripts/build-deb.ps1). Kiểm tra SHA-256 và danh sách tệp bằng `dpkg-deb -I` và `dpkg-deb -c` trước cài.
2. Chuẩn bị `/etc/usbshield-agent/agent.env` của root với mode `0600` theo [mẫu](../packaging/agent-deb/etc/usbshield-agent/agent.env.example). Đặt URL HTTPS, secret enrollment tương ứng với central và đường dẫn PEM CA nội bộ nếu cần. CA phải đọc được bởi Agent service. Kiểm tra SAN và giờ hệ thống.
3. Cài gói đã kiểm chứng bằng `sudo apt install ./<artifact>.deb`. Script cài đặt thiết lập service user, IPC ACL và base rules khi cần. Nếu host đã có USBGuard rules, đánh giá tác động và backup trước cài.
4. Kiểm tra `systemctl status usbguard usbshield-agent`, `journalctl -u usbshield-agent -u usbguard`, rồi kiểm tra endpoint xuất hiện trong Admin. Khi xem log để chia sẻ, loại bỏ thông tin nhạy cảm. Với lab `usbprotection.lab`, dùng [core checker](../packaging/tests/check-usbshield-core.sh) và kiểm tra với thiết bị mẫu phù hợp.
5. Khi USBGuard báo lỗi, kiểm tra khả năng `sudo -u usbshield usbguard list-rules`, trạng thái daemon và cấu hình ACL. Nếu enrollment/heartbeat lỗi, kiểm tra DNS, CA/SAN, CIDR, URL và tình trạng secret/token. Nếu không thấy notification, kiểm tra phiên desktop, launcher và log. Kiểm tra USBGuard enforcement và sự kiện độc lập với notification.
