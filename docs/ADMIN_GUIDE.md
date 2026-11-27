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
