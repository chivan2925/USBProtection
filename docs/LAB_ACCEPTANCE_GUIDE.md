# Nghiệm thu USBShield trên VMware

Chạy Central trực tiếp trên máy Windows 11 và Agent trong Ubuntu guest VMware. Provision theo [hướng dẫn IT](IT_SETUP_GUIDE.md). Dùng một USB thử có dữ liệu có thể mất. Gắn USB passthrough vào Ubuntu guest và chọn đúng endpoint trong Desktop. Nếu cần test tenant hoặc endpoint isolation, chuẩn bị workspace và guest thứ hai với credential riêng.

## Mạng lab trên Windows 11

Nối Ubuntu guest vào mạng VMware **Host-only** dùng chung với host Windows. Dùng IP tĩnh hoặc DHCP reservation cho địa chỉ Central trên adapter host-only. Trong lab, cấu hình IP listener Nginx, CIDR Agent và firewall theo đúng subnet này. DNS nội bộ hoặc cấu hình hosts của guest cần phân giải tên TLS của Agent về IP host-only. Desktop vẫn kết nối qua `127.0.0.1` với CA đã provision. [Mạng VMware Workstation](https://knowledge.broadcom.com/external/article?articleNumber=309842)

Nếu dùng thêm adapter NAT cho tải gói, ngắt adapter đó khi nghiệm thu offline. Ngắt đường Internet vật lý của host trong lúc kiểm thử và giữ adapter host-only. Xác minh Windows và Ubuntu đều mất Internet, đồng thời Agent vẫn tới được listener Central trước khi chạy các ca LAN. Để kiểm riêng mất LAN, ngắt kết nối host-only của guest rồi nối lại.

## Hai lệnh để bắt đầu

1. Trên Windows, từ repository root chạy `./packaging/tests/verify-windows.ps1` bằng PowerShell 7.6+ và PostgreSQL test cô lập theo [Developer guide](DEVELOPER_GUIDE.md). Lệnh chạy regression, build bundle và smoke trên loopback. Để test gói Windows đã provision, chạy `Start-USBShield.ps1` trong phiên tài khoản thường và xác nhận Desktop mở được.
2. Trên Ubuntu guest, xác định VID:PID từ USB rồi chạy từ repository root. Dùng Python 3 và quyền sudo để đọc USBGuard. Script hướng dẫn rút/cắm, Allow và Revoke, tự chờ trạng thái ở từng bước.

```bash
python3 -B packaging/tests/verify-vmware-usb.py --usb-id 0951:1665
```

Thay VID:PID trong ví dụ bằng ID thiết bị thử từ `lsusb`. Thêm `--hash` để chọn đúng HASH khi có thiết bị cùng VID:PID. Mặc định `--revoke replug` dành cho P2. Sau khi có bản hỗ trợ Revoke trực tiếp, dùng `--revoke direct` và giữ USB gắn vào cùng guest trong bước Revoke. Script kiểm HASH/cổng USBGuard, managed Allow, kernel authorization và thiết bị khối. Ở bước giữ nguyên kết nối, script cũng đối chiếu runtime ID và địa chỉ bus/device của Linux. Script lấy mẫu, vì vậy người thao tác phải giữ kết nối USB trong bước đó. Script chỉ đọc. Nó thoát mã 1 nếu không có quan sát đạt trong thời gian chờ.

## Những bước phải có quan sát bổ sung

| Tình huống | Quan sát cần đối chiếu |
| --- | --- |
| Allow/Revoke | Windows hiển thị đúng endpoint và USB, desired tăng khi đổi policy, applied bắt kịp. USB event xuất hiện ở đúng workspace và có attribution nếu xác định được. Khi cắm USB bị chặn hoặc bị reject, kiểm notifier nếu Agent xác định được phiên X11/Wayland phù hợp. Khi bản phát hành có actual status, đối chiếu status với quan sát kernel. |
| Cài đặt tài khoản | Dùng tài khoản lab. Đổi tên đăng nhập, mật khẩu hoặc cả hai. Xác minh thông tin cũ bị từ chối, phiên cũ hết hiệu lực và đăng nhập bằng thông tin mới vẫn thấy cùng máy trạm và dữ liệu workspace. |
| Ngắt Internet, giữ LAN | Giữ kết nối nội bộ Windows–Ubuntu. Xác minh WAN đã ngắt qua cấu hình lab. Chạy [ACL verifier](../packaging/tests/verify-phase3-https-acl.sh) từ Central và từ Ubuntu Agent với CA phù hợp. Mở Desktop, lưu policy, xem heartbeat/pull/ACK và thực hiện lại luồng USB. |
| Ngắt LAN và nối lại | Ghi desired/applied trước khi ngắt riêng đường Agent–Central. Trên Central tạo một thay đổi whitelist thật cho endpoint đó. Trên Agent, cắm hoặc rút USB mẫu và ghi lại thời điểm để đối chiếu event. Kiểm desired mới còn pending và USBGuard dùng policy cũ. Nối lại, đối chiếu revision và event ở Central, kiểm event chỉ xuất hiện một lần. Lặp qua reboot trong phiên nghiệm thu recovery. |
| Cô lập workspace và máy trạm | Dùng hai workspace và hai endpoint. Xác minh Allow A không cấp quyền cho B, dữ liệu và chức năng của A không xuất hiện dưới B. |
| USB đang bận và file monitoring | Khi bản cần nghiệm thu đã có tính năng, dùng USB scratch để kiểm Revoke khi I/O đang chạy, nhiều phân vùng, offline/replay và các tình huống GNOME Files/cp/mv. Đối chiếu các ca tương ứng trong [ma trận nghiệm thu](../BLUEPRINT.md). |
| Lifecycle và quyền | Chạy cài/upgrade/restart/uninstall trên VM sạch cùng các bước privilege/TLS thuộc ma trận nghiệm thu. Ghi riêng bước chưa chạy. |

Giữ kết quả theo từng ca trong chat hoặc log ngoài repository. Lưu phiên bản gói, pha mạng và kết quả quan sát. PASS của script áp dụng cho các checkpoint USBGuard/kernel mà script đã chạy.
