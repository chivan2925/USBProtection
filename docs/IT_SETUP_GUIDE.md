# Cài đặt và vận hành USBShield

## Chuẩn bị

- Central cần Windows x64, PostgreSQL và Nginx. Builder cần JDK 21 có `jpackage` và Maven. Ứng dụng Desktop dùng Java runtime có sẵn trong app image.
- Script build Windows `build-release.ps1` và `build-deb.ps1` yêu cầu PowerShell 7.4+. [Windows verifier](../packaging/tests/verify-windows.ps1) yêu cầu PowerShell 7.6+.
- Builder `.deb` trên Ubuntu 24.04 amd64 cần JDK 21, Maven và `dpkg-deb`. Endpoint chạy Agent dùng Ubuntu 24.04 amd64, systemd, USBGuard, OpenJDK 21 JRE headless, libnotify và ACL.
- Chuẩn bị IP LAN cố định, DNS/hosts nội bộ, CIDR của Agent và PKI. Chứng chỉ proxy cần có cả IP SAN `127.0.0.1` và DNS SAN của Central trên LAN. Dùng endpoint lab khi kiểm tra thay đổi rules USBGuard.

## Central Windows

1. Chạy `./admin-server/build-release.ps1` trên Windows với JDK 21 và DB test theo [Developer guide](DEVELOPER_GUIDE.md). Kiểm SHA-256 và giải nén gói Central tại thư mục cài đặt do IT quản lý. Gói có API JAR, app image `USBShield` và launcher `Start-USBShield.ps1`. PostgreSQL, backend và proxy cần provision bằng dịch vụ Windows đã kiểm tra tại máy Central.
2. Tạo database và role PostgreSQL riêng cho USBShield, chỉ truy cập từ Central. Role cần quyền tạo và cập nhật schema ứng dụng theo Flyway. V3 áp dụng cho schema vận hành trống. Nếu DB cũ có endpoint, catalog, event hoặc whitelist, dừng trước nâng cấp và giữ bản sao lưu đến khi có mapping và cửa sổ cutover đã duyệt. [Runbook PostgreSQL](../admin-server/ops/POSTGRES_FLYWAY_BASELINE.md)
3. Cấp `USBSHIELD_DB_URL`, `USBSHIELD_DB_USERNAME` và `USBSHIELD_DB_PASSWORD` trong cấu hình service/process được bảo vệ. `USBSHIELD_ADMIN_USERNAME` và `USBSHIELD_ADMIN_PASSWORD` cấp workspace đầu tiên khi chưa có Admin. Backend mặc định dùng loopback cổng 18080, PostgreSQL dùng loopback. [Profile prod](../admin-server/src/main/resources/application-prod.yml)
4. Cấu hình dịch vụ backend chạy `java -jar usbshield-admin-server.jar --spring.profiles.active=prod` với JDK/JRE 21 tại Central. Điền mọi placeholder trong [Nginx template](../packaging/nginx/usbshield-https-acl.conf.example), cho tài khoản proxy quyền đọc private key và chạy `nginx -t` trước khi nạp cấu hình. Chỉ mở TCP/443 trên IP LAN cho CIDR Agent.
5. Từ phiên Windows thường chạy `./Start-USBShield.ps1 -CaFile 'C:\ProgramData\USBShield\pki\central-ca.pem'`. Nếu tên dịch vụ khác mặc định, truyền `-DatabaseService`, `-ServerService` và `-ProxyService`. Launcher kiểm dịch vụ và HTTPS trước khi mở Desktop. Để khởi động dịch vụ, IT chạy riêng với `-StartServices -CheckOnly` trong phiên có đủ quyền, sau đó mở Desktop từ phiên thường. `-CheckOnly` kiểm tra mà không mở cửa sổ.
6. Dùng [quy trình nghiệm thu VMware](LAB_ACCEPTANCE_GUIDE.md) khi kiểm Desktop, LAN, USB và các tình huống phục hồi trên lab. Native launcher dùng các dịch vụ đã provision. Cài đặt, upgrade và uninstall tự động thuộc gate phát hành.

Dùng phiên bảo trì trên Central host đã xác thực ở tầng OS để cấp thêm Admin. Với cấu hình DB đã xác minh, chạy JAR với `--spring.profiles.active=prod,admin-provision` và cấp tên/mật khẩu qua biến process được bảo vệ `USBSHIELD_ADMIN_PROVISION_USERNAME` và `USBSHIELD_ADMIN_PROVISION_PASSWORD`. Profile chạy non-web và tạo tài khoản, workspace và audit trong cùng transaction. Xóa biến bảo trì sau khi hoàn tất. [Profile provision](../admin-server/src/main/resources/application-admin-provision.yml)

Nếu quên mật khẩu, dừng tiến trình Central và dùng cùng cấu hình DB để chạy JAR với `--spring.profiles.active=prod,admin-reset`. Cấp mật khẩu mới trong `USBSHIELD_ADMIN_RESET_PASSWORD` và chọn tài khoản bằng `USBSHIELD_ADMIN_RESET_USERNAME`. Nếu tên đăng nhập đã đổi trong Desktop, dùng tên hiện tại. Phiên cũ mất hiệu lực sau khi lưu mật khẩu. Bỏ biến reset và khởi động lại với riêng profile `prod`. [Profile reset](../admin-server/src/main/resources/application-admin-reset.yml)

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
4. Đọc UUID trong `installation-id.json` tại data directory của Agent (mặc định `/var/lib/usbshield-agent`). IT xác minh UUID và dùng Desktop của workspace đích để tạo slot và cấp vé. Chuyển vé qua kênh bảo vệ, điền vào `USBSHIELD_ENROLLMENT_TOKEN` trong `agent.env` rồi khởi động lại service trước khi vé hết hạn. Sau khi Central nhận heartbeat, bỏ vé đã dùng khỏi cấu hình. [Hợp đồng API](API.md)
5. Kiểm tra `systemctl status usbguard usbshield-agent`, `journalctl -u usbshield-agent -u usbguard`, rồi xác nhận đúng endpoint online trong workspace. Với lab `usbprotection.lab`, dùng [core checker](../packaging/tests/check-usbshield-core.sh) và kiểm tra với thiết bị mẫu phù hợp. Nếu lỗi enrollment, kiểm tra DNS, CA/SAN, CIDR, phiên bản giao thức và hiệu lực vé. Nếu mất notification, kiểm tra phiên desktop và launcher riêng với USBGuard enforcement.

Khi khôi phục credential, IT xác minh máy và workspace trước khi thu hồi rồi cấp vé cho cùng slot. Dừng Agent và cất `endpoint.json` cũ vào nơi bảo vệ. Giữ cả `installation-id.json` và `enrollment-binding.json` trong data directory. Binding giữ machine ID đã gửi lúc claim, kể cả khi khả năng đọc machine ID của hệ điều hành thay đổi. Nếu binding bị mất, hỏng hoặc machine ID đã biết đổi sang giá trị khác, IT cần xác minh lại máy và slot trước khi tiếp tục.

Trước khi khởi động lại Agent, kiểm tra outbox trong data directory (mặc định `/var/lib/usbshield-agent/spool`). Chỉ để lại sự kiện đã xác minh cùng endpoint và cách ly các bản ghi chưa rõ nguồn, kể cả `UNENROLLED` hoặc `POC-UNENROLLED-*`. Agent có thể gắn các ID tạm đó vào endpoint hiện tại khi drain. Agent v1 hoặc state legacy cần kế hoạch nâng cấp và mapping riêng.
