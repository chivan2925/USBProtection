# USBShield Central Admin Desktop

Ứng dụng vận hành Windows cho quản trị viên workspace. Ưu tiên nhận diện đúng máy trạm, USB và phiên bản policy. Bảng dữ liệu, trạng thái và thao tác phục hồi cần dễ đọc trong phiên làm việc dài.

## Giao diện hiện hành

| Thành phần | Quy ước |
| --- | --- |
| Khung | Thanh điều hướng xanh đậm, nội dung nền sáng. Tiêu đề, bộ lọc, bảng và điều khiển phân trang theo thứ tự thao tác. |
| Chữ | Segoe UI có sẵn trên Windows. Cỡ cơ sở 13 px, tiêu đề trang 27 px, tiêu đề nhóm 16 px. Giữ nguyên dấu tiếng Việt và hiển thị đầy đủ ID dài khi mở chi tiết. |
| Màu | Nền nội dung **#F3F6FA**, bề mặt **#FFFFFF**, chữ **#203143**, phụ **#516378**, điều hướng **#132B3D**, thao tác chính **#13775D**, lỗi **#A02D2B**. |
| Điều khiển | Giữ vùng focus nhìn thấy được. Nhãn có nghĩa khi đứng riêng. Dùng chữ hoặc dấu hiệu ngoài màu cho lỗi, offline, pending và hành động nguy hiểm. |
| Bảng | Tên và trạng thái trước, định danh kỹ thuật sau. Dùng chi tiết để hiển thị nguyên HASH/ID. Giữ filter, page và trạng thái tải nhất quán khi trả lời lỗi hoặc hủy thao tác đọc. |

[FXML](src/main/resources/com/group/usbshield/desktop/desktop.fxml) và [CSS JavaFX](src/main/resources/com/group/usbshield/desktop/desktop.css) là nguồn giá trị runtime. Tái sử dụng các lớp .primary, .nav, .muted và .status-bar. Khi thêm biến màu dùng JavaFX looked-up colors. Dùng font và asset local để giữ vận hành offline.

## Trạng thái và tác vụ

- Kịch bản chính là đăng nhập, tìm endpoint, xác nhận USB đúng máy, lưu Allow/Revoke và đọc trạng thái sau thay đổi.
- Giữ khác biệt giữa **Mong muốn**, **Đã ACK** và actual enforcement khi API có bằng chứng tương ứng. Ghi rõ tuổi của trạng thái stale. Allow cần cắm lại. Nội dung Revoke theo khả năng phiên bản đang chạy.
- **Cài đặt tài khoản** chỉ phục vụ đổi tên đăng nhập và mật khẩu. Xác nhận bằng mật khẩu hiện tại, dùng trường mật khẩu che ký tự, xóa dữ liệu nhạy cảm khi gửi hoặc rời trang và yêu cầu đăng nhập lại sau khi đổi.
- Kiểm trạng thái loading, rỗng, lỗi, timeout và kết quả ghi chưa chắc chắn. Hộp thoại xác nhận cho thao tác nhạy cảm phải có nhận diện đối tượng và lựa chọn hủy. Hiển thị khi phiên hết hạn, chặn thao tác trùng và để người dùng kiểm lại trạng thái sau timeout.

## Nghiệm thu giao diện

Xem JavaFX thật tại kích thước cửa sổ mặc định 1180 × 760 và tối thiểu 970 × 650 logical pixels. Kiểm Windows scale 100% và 150%, tab order, Enter/Escape, visible focus và nội dung tiếng Việt dài. Dùng dữ liệu synthetic cho màn hình tìm máy, cảnh báo lỗi và xác nhận policy. Mục tiêu contrast là 4.5:1 cho chữ thông thường, 3:1 cho chữ lớn và ranh giới điều khiển quan trọng. Kiểm riêng text selection và focus trên nền thật.

[DesktopViewTest](src/test/java/com/group/usbshield/desktop/DesktopViewTest.java) kiểm đường chạy của FXML trên Windows. Kiểm cơ học và trình tự review nằm trong [skill thiết kế](../.agents/skills/usbshield-design/SKILL.md). Kết quả thị giác, DPI và keyboard cần evidence từ cửa sổ chạy thật.
