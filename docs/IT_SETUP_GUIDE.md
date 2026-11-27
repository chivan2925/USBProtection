# Cài đặt và vận hành baseline

## Chuẩn bị

- Central Windows chạy Java 21, PostgreSQL và Nginx. Để build JAR, dùng JDK 21, Maven và Node `^20.19.0 || >=22.12.0` kèm npm theo [frontend lockfile](../frontend/package-lock.json). Dùng Node 22.12+ để chạy bộ test frontend sử dụng `--experimental-strip-types`.
- Script build Windows `build-release.ps1` và `build-deb.ps1` yêu cầu PowerShell 7.4+. [Windows verifier](../packaging/tests/verify-windows.ps1) yêu cầu PowerShell 7.6+.
- Builder `.deb` trên Ubuntu 24.04 amd64 cần JDK 21, Maven và `dpkg-deb`. Endpoint chạy Agent dùng Ubuntu 24.04 amd64, systemd, USBGuard, OpenJDK 21 JRE headless, libnotify và ACL.
- Chuẩn bị DNS/hosts, chứng chỉ có SAN đúng DNS name, CA được tin cậy và CIDR cho Admin/Client. Dùng endpoint lab hoặc cửa sổ bảo trì khi thay đổi rules USBGuard.

## Central Windows

1. Từ root repo, chạy `npm --prefix frontend ci` và `npm --prefix frontend run build`. Sau đó chạy `mvn -f admin-server/pom.xml -Pwith-frontend -DskipTests package` để tạo JAR. Đây là build nhanh. Quy trình kèm test và manifest dùng [build-release.ps1](../admin-server/build-release.ps1) với JDK và DB test cấu hình theo [Developer guide](DEVELOPER_GUIDE.md).
2. Tạo database và role PostgreSQL riêng cho USBShield, chỉ truy cập từ Central. Role cần quyền tạo và cập nhật schema ứng dụng theo Flyway. V3 áp dụng cho schema vận hành trống. Nếu DB cũ có endpoint, catalog, event hoặc whitelist, dừng trước nâng cấp và giữ bản sao lưu đến khi có mapping và cửa sổ cutover đã duyệt. [Runbook PostgreSQL](../admin-server/ops/POSTGRES_FLYWAY_BASELINE.md)
3. Cấp `USBSHIELD_DB_URL`, `USBSHIELD_DB_USERNAME` và `USBSHIELD_DB_PASSWORD` trong cấu hình service/process được bảo vệ. `USBSHIELD_ADMIN_USERNAME` và `USBSHIELD_ADMIN_PASSWORD` chỉ cấp tài khoản cùng workspace đầu tiên khi chưa có Admin. Đặt `USBSHIELD_BIND_ADDRESS=127.0.0.1` và `PORT=18080` để khớp proxy mẫu. [Profile prod](../admin-server/src/main/resources/application-prod.yml)
4. Chạy `java -jar admin-server/target/usbshield-admin-server.jar --spring.profiles.active=prod` từ repository root hoặc dùng đường dẫn JAR phát hành tương ứng. Kiểm tra tên chính xác trong `target/` hay manifest của gói tải về.
5. Điền tất cả placeholder trong [Nginx template](../packaging/nginx/usbshield-https-acl.conf.example). `ADMIN_CIDR` phải loại trừ toàn bộ Client IP mà Nginx nhìn thấy. Trên LAN chung, dùng `/32` của máy Admin hoặc VLAN Admin riêng. Kiểm tra `nginx -t` trước reload, mở HTTPS 443 theo CIDR được duyệt và giữ backend/DB trên loopback. Chạy [ACL verifier](../packaging/tests/verify-phase3-https-acl.sh) từ máy Admin với mode `admin` và từ máy Client với mode `client`, dùng URL HTTPS thực và CA nội bộ nếu cần. Từ máy Admin, mở `https://<dns-name>/#/login` sau khi kiểm tra đạt.

Dùng phiên bảo trì trên Central host đã xác thực ở tầng OS để cấp thêm Admin. Với cấu hình DB đã xác minh, chạy JAR với `--spring.profiles.active=prod,admin-provision` và cấp tên/mật khẩu qua biến process được bảo vệ `USBSHIELD_ADMIN_PROVISION_USERNAME` và `USBSHIELD_ADMIN_PROVISION_PASSWORD`. Profile chạy non-web và tạo tài khoản, workspace và audit trong cùng transaction. Xóa biến bảo trì sau khi hoàn tất. [Profile provision](../admin-server/src/main/resources/application-admin-provision.yml)

Nếu quên mật khẩu, dừng tiến trình Central và dùng cùng cấu hình DB để chạy JAR với `--spring.profiles.active=prod,admin-reset`. Cấp mật khẩu mới trong `USBSHIELD_ADMIN_RESET_PASSWORD` và chọn tài khoản bằng `USBSHIELD_ADMIN_RESET_USERNAME` nếu cần. Phiên cũ mất hiệu lực sau khi lưu mật khẩu. Bỏ biến reset và khởi động lại với riêng profile `prod`. [Profile reset](../admin-server/src/main/resources/application-admin-reset.yml)

## Endpoint Ubuntu

### Kiểm tra trước khi cài trên máy đã có USBGuard

Sao lưu daemon config, rules và IPC ACL trong cửa sổ bảo trì. Dùng `sudo usbguard list-rules` để ghi nhận các Allow hiện hữu cần giữ. Sau khi Agent chạy trên Linux, tác vụ định kỳ có thể gỡ Allow nằm ngoài base rule và tập rule do USBShield quản lý, kể cả khi Central mất kết nối. Lập kế hoạch áp dụng lại các quyền USB cần thiết qua danh sách cho phép theo endpoint trên Central, sau đó xác minh với thiết bị mẫu trước khi bàn giao.

- Trong `/etc/usbguard/usbguard-daemon.conf`, nếu đã khai báo `ImplicitPolicyTarget`, chỉ để một khai báo có hiệu lực với giá trị `block`. Installer bổ sung `block` khi chưa có khai báo.
- Nếu `/etc/usbguard/rules.conf` đã có nội dung, xác minh có base rule `allow with-interface none-of { 08:*:* }` theo [rules mẫu](../packaging/usbguard/rules-v0.1.conf). Installer chỉ khởi tạo rules khi file thiếu hoặc rỗng.
- Nếu đã khai báo `IPCAccessControlFiles`, giữ một khai báo trỏ tới `/etc/usbguard/IPCAccessControl.d/`. Thư mục hiện có phải thuộc root, là thư mục thực và group/other không có quyền ghi. Nếu ACL `usbshield` đã tồn tại, xác minh file thường thuộc root và có đúng bốn dòng theo thứ tự `Devices=list,listen`, `Policy=list,modify`, `Parameters=list`, `Exceptions=listen`. Installer dừng khi gặp ACL tùy biến hoặc symlink. Nếu có xung đột, thống nhất cách chuyển cấu hình với quản trị viên trước khi cài hoặc thử lại. [Kiểm tra trong postinst](../packaging/agent-deb/DEBIAN/postinst)

### Cài đặt và kiểm tra

1. Dùng gói có manifest và checksum. Builder Ubuntu chạy `bash packaging/scripts/build-deb.sh` tại repo root; builder Windows có [build-deb.ps1](../packaging/scripts/build-deb.ps1). Kiểm tra SHA-256 và danh sách tệp bằng `dpkg-deb -I` và `dpkg-deb -c` trước cài.
2. Tạo thư mục `/etc/usbshield-agent/` nếu chưa có. Chuẩn bị `agent.env` với owner root và mode `0600`. Đặt URL HTTPS và đường dẫn PEM CA nội bộ nếu cần. CA phải đọc được bởi Agent service. Kiểm tra SAN và giờ hệ thống. Để trống vé khi cài máy mới để Agent tạo installation ID. [Mẫu agent.env](../packaging/agent-deb/etc/usbshield-agent/agent.env.example)
3. Cài gói đã kiểm chứng bằng `sudo apt install ./<artifact>.deb`. Script cài đặt thiết lập service user, IPC ACL và base rules khi cần. Nếu bước cấu hình gói dừng, đối chiếu lỗi với preflight và nhật ký service, xử lý nguyên nhân rồi chạy `sudo dpkg --configure usbshield-agent` nếu gói đang chờ cấu hình.
4. Đọc UUID trong `installation-id.json` tại data directory của Agent (mặc định `/var/lib/usbshield-agent`). IT xác minh UUID trên máy định cấp. Dùng session Admin của workspace đích và API để tạo slot, xin vé rồi chuyển vé qua kênh bảo vệ. Điền vé vào `USBSHIELD_ENROLLMENT_TOKEN` trong `agent.env` và khởi động lại service trước khi vé hết hạn. Sau khi Central nhận heartbeat, bỏ vé đã dùng khỏi cấu hình. [Hợp đồng API](API.md)
5. Kiểm tra `systemctl status usbguard usbshield-agent`, `journalctl -u usbshield-agent -u usbguard`, rồi xác nhận đúng endpoint online trong workspace. Với lab `usbprotection.lab`, dùng [core checker](../packaging/tests/check-usbshield-core.sh) và kiểm tra với thiết bị mẫu phù hợp. Nếu lỗi enrollment, kiểm tra DNS, CA/SAN, CIDR, phiên bản giao thức và hiệu lực vé. Nếu mất notification, kiểm tra phiên desktop và launcher riêng với USBGuard enforcement.

Khi khôi phục credential, IT xác minh máy và workspace trước khi thu hồi rồi cấp vé cho cùng slot. Dừng Agent và cất `endpoint.json` cũ vào nơi bảo vệ. Giữ cả `installation-id.json` và `enrollment-binding.json` trong data directory. Binding giữ machine ID đã gửi lúc claim, kể cả khi khả năng đọc machine ID của hệ điều hành thay đổi. Nếu binding bị mất, hỏng hoặc machine ID đã biết đổi sang giá trị khác, IT cần xác minh lại máy và slot trước khi tiếp tục.

Trước khi khởi động lại Agent, kiểm tra outbox trong data directory (mặc định `/var/lib/usbshield-agent/spool`). Chỉ để lại sự kiện đã xác minh cùng endpoint và cách ly các bản ghi chưa rõ nguồn, kể cả `UNENROLLED` hoặc `POC-UNENROLLED-*`. Agent có thể gắn các ID tạm đó vào endpoint hiện tại khi drain. Agent v1 hoặc state legacy cần kế hoạch nâng cấp và mapping riêng.
