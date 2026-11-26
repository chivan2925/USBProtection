# Sử dụng Admin Web baseline

Mở URL HTTPS do IT cung cấp từ mạng Admin. Đăng nhập bằng tài khoản USBShield. Dashboard tóm tắt endpoint và sự kiện. Endpoints hiển thị health, last seen, desired/applied policy và chi tiết từng máy. Devices, Whitelist và Events giúp tra cứu thiết bị đã quan sát, quyền cho phép và sự kiện theo endpoint.

## Cho phép và thu hồi

1. Mở endpoint đích và chọn thiết bị mà máy đó đã báo cáo. Đọc tên, HASH và endpoint trước khi xác nhận.
2. Dùng **Allow on this endpoint** hoặc **Revoke on this endpoint**. Thao tác đổi desired whitelist của endpoint được chọn. Nếu API từ chối vì thiết bị chưa được thấy tại endpoint, kiểm tra sự kiện ở đúng máy.
3. Kiểm tra desired/applied version và USBGuard health. Chờ Agent đồng bộ. Sau đó rút USB và cắm lại để kiểm tra hiệu lực baseline. ACK là xác nhận revision rules. Kiểm tra kết quả trên USB qua hành vi tại máy và sự kiện tương ứng.
4. Khi máy offline hoặc version chưa khớp, theo dõi pending và last seen. Máy có thể tiếp tục dùng policy áp dụng trước đó. Sự kiện bị giữ ở outbox có thể xuất hiện sau khi nối mạng lại.

## Đọc sự kiện và lỗi

USB Events cho biết thời điểm, quyết định và thiết bị khi có dữ liệu. Attribution là user/session Ubuntu đang hoạt động theo Agent hoặc `UNKNOWN`. Notification desktop là best-effort. Nếu không thấy sự kiện, kiểm tra health, kết nối và bộ lọc endpoint/time. Chuyển sang [IT setup](IT_SETUP_GUIDE.md) để kiểm tra kết nối hoặc reset mật khẩu.

Mỗi tài khoản Admin có một workspace. Dashboard, Endpoints, Whitelist, Devices và Events hiển thị dữ liệu của workspace đã đăng nhập. IT cấp endpoint vào workspace qua vé enrollment. JavaFX, Revoke trực tiếp và file activity thuộc [mục tiêu v2.5](../BLUEPRINT.md). [Quy trình cấp endpoint](IT_SETUP_GUIDE.md)
