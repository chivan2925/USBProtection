# Sử dụng Central Admin Desktop

Mở USBShield trên máy Windows Central bằng launcher do IT cung cấp. Đăng nhập bằng tài khoản workspace. Tổng quan hiển thị số thiết bị và sự kiện. Chọn khoảng ngày theo giờ Windows rồi bấm **Áp dụng** để xem thống kê trong kỳ.

- **Máy trạm** hiển thị trạng thái kết nối, USBGuard, người dùng Ubuntu và hai phiên bản policy. Chọn máy để xem chi tiết, các thiết bị đã thấy hoặc danh sách cho phép của máy đó.
- **Thiết bị USB** hiển thị trạng thái ghi nhận gần nhất của thiết bị theo từng máy. **Danh sách cho phép** hiển thị quy tắc desired theo endpoint.
- **Sự kiện USB** có bộ lọc máy, thiết bị, người dùng, loại sự kiện, quyết định và ngày. Chọn sự kiện để xem thời điểm, attribution và phiên Ubuntu. **Nhật ký quản trị** ghi các thao tác cấp tài khoản, slot, vé và thu hồi credential trong workspace.

## Cho phép và thu hồi quyền USB

1. Chọn máy trạm, mở **Thiết bị đã thấy**, chọn thiết bị rồi bấm **Cho phép tại máy này**. Central chỉ cấp quyền cho HASH hợp lệ mà máy đích đã quan sát.
2. Muốn thu hồi, mở **Quyền cho phép**, chọn quy tắc rồi bấm **Thu hồi quyền cho phép**.
3. Xem **Mong muốn** và **Đã ACK** tại **Máy trạm**. Máy mất LAN tiếp tục dùng policy đã áp dụng. Khi Agent đã đồng bộ, rút và cắm lại USB để kiểm tra hiệu lực hiện tại. ACK cho biết phiên bản rules Agent đã áp dụng.

## Đăng ký và vận hành

Bấm **Tạo slot đăng ký** với installation UUID do IT xác minh. Chọn slot trong **Máy trạm** rồi bấm **Cấp vé**. Dùng **Thu hồi credential** khi IT cần vô hiệu hóa kết nối của Agent và cấp lại vé. Hai thao tác thu hồi credential và thu hồi quyền USB có tác động khác nhau. [Quy trình IT](IT_SETUP_GUIDE.md)

Dùng **Tải lại** sau khi kết nối về Central gián đoạn. Nếu thao tác ghi báo lỗi hoặc timeout, kiểm tra trạng thái hiện tại trước khi thao tác lại. Central có thể xử lý request trước khi đường truyền trả phản hồi. Đăng xuất khi kết thúc.

## Cài đặt tài khoản

Mở **Cài đặt tài khoản** và nhập mật khẩu hiện tại. Điền tên đăng nhập mới, mật khẩu mới hoặc cả hai. Để trống mục cần giữ nguyên. Nếu đổi mật khẩu, nhập lại mật khẩu mới ở ô xác nhận. Mật khẩu mới cần ít nhất 12 ký tự Unicode, tối đa 72 byte UTF-8 và không chứa ký tự điều khiển ISO.

Sau khi lưu, đăng nhập lại bằng thông tin mới. Mọi phiên Admin đã mở của tài khoản này hết hiệu lực. Tài khoản giữ nguyên workspace, máy trạm và dữ liệu. Nếu quên mật khẩu, liên hệ IT để đặt lại bằng quy trình bảo trì trên máy Central. [Hướng dẫn IT](IT_SETUP_GUIDE.md)
