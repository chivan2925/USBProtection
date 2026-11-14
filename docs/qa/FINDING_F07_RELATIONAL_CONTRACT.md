# F07 — Quan hệ PostgreSQL và bản ghi audit lịch sử

**Disposition:** KHÔNG thêm foreign key bắt buộc vào `usb_events` tại thời điểm này.
Đây là chỉnh lại một nhận định từ static review, không phải bỏ sót sửa lỗi.

`admin-server/src/test/java/com/group/usbshield/server/EventSearchIntegrationTest.java`
`numericSearchRetainsHistoricalRowsWithoutDeviceDetails()` khẳng định một event lịch sử
được phép tham chiếu `usb_device_id` đã không còn trong catalog và UI vẫn hiển thị
sự kiện. Thêm FK vào `usb_events.usb_device_id` sẽ làm test/contract này hỏng.

Endpoint token được xác minh trong Agent API, còn `EndpointPolicyService.allow()`
kiểm tra USB đã từng được báo từ đúng endpoint trước khi ghi whitelist. Vì vậy
các quan hệ này đang được ràng buộc ở tầng ứng dụng, không đồng nghĩa SQL bất kỳ
có thể thoải mái ghi các row orphan.

Nếu tương lai cần FK ở cấp database: chốt lại yêu cầu lưu audit khi catalog/endpoint
bị xóa, có migration dữ liệu và đổi integration tests trước. Không tự sửa dữ liệu
lịch sử hoặc khai báo FK xung đột với contract đã triển khai.
