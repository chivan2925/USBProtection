# USBShield — M2 Week 4 Technical Q&A / Demo Handoff

**Owner:** M2 · **Work ID:** W4-M2-06 · **Status:** PREPARED; native Ubuntu evidence deferred after Week 4.

| Câu hỏi khi demo | Trả lời kỹ thuật cần nói đúng |
|---|---|
| **USBGuard hay Agent chặn USB?** | **USBGuard daemon** quyết định kernel authorization bằng local policy. Agent nghe event, đồng bộ chính sách và gửi telemetry; không thay thế cơ chế chặn kernel. |
| **Vì sao tắt Admin Server vẫn chặn USB lạ?** | USBGuard áp policy đã lưu trên client; `ImplicitPolicyTarget=block` và base rule không phụ thuộc kết nối mạng. Mất mạng ảnh hưởng sync/event gửi đi, không được tạo fallback `allow`. Chỉ có thể xác nhận thực tế bằng Ubuntu E2E. |
| **Làm thế nào để không chặn chuột/bàn phím?** | Rule `allow with-interface none-of { 08:*:* }` cho thiết bị không có Mass Storage interface. Thứ tự rule là quan trọng; Deny đứng trước có thể phá ngoại vi. |
| **Thiết bị composite chứa HID + Mass Storage thì sao?** | Không match base rule non-storage vì có class `08`, nên mặc định BLOCK trừ khi có grant cụ thể, đúng phạm vi bài toán. |
| **USB X được Allow trên Client A có tự được dùng ở B không?** | Không. Admin lưu desired whitelist **theo endpoint**; Agent mỗi máy chỉ lấy và áp policy của endpoint mình. Cần test trên hai Ubuntu Clients để chứng minh. |
| **Vì sao không whitelist chỉ theo VID:PID?** | VID:PID thường trùng giữa nhiều USB cùng model; dùng hash hoặc serial+VID/PID để ràng buộc thiết bị cụ thể. |
| **Nếu USB thiếu serial?** | Có thể dùng USBGuard hash nếu hợp lệ. Không có hash/serial đủ tin cậy thì hệ thống từ chối cấp grant cụ thể thay vì allow cả model. |
| **Rule ID có cố định sau xóa/reboot không?** | Không. Sau mỗi lần xóa, M2 tra lại rule list và tìm ID hiện hành theo managed fingerprint, kể cả khi rollback. |
| **Có thể thêm ngoại lệ USBGuard ngoài Web không?** | Admin/root trên client có thể sửa policy ngoài USBShield. Reconciler bảo toàn foreign rules có thể xác minh và fail trên rule nguy hiểm/không hiểu; vì vậy không được tuyên bố toàn policy chắc chắn chặn thiết bị lạ nếu có foreign Allow. |
| **Admin biết chính xác ai cắm USB không?** | Chỉ biết tài khoản Ubuntu active-local/session tại thời điểm event, **không chứng minh** con người trực tiếp cắm. Ambiguous/remote-only trả `UNKNOWN`. |
| **Không có GUI hoặc notifier chết thì sao?** | Không popup; USBGuard vẫn enforce và Agent vẫn cố gửi event. `DELIVERED` của IPC không tương đương với ảnh popup thật. |
| **Có thể tắt service không?** | Normal user không có sudo/USBGuard modify IPC không được phép dừng/sửa protection. Root hoặc sudo vẫn có quyền quản trị hệ thống. |
| **Test Windows đã đủ để nghiệm thu toàn bộ chưa?** | Chưa. Windows Maven/fixtures chứng minh mã và contract trong môi trường giả lập; phải kiểm tra USB thật, `systemd`, GUI, `.deb`, reboot và daemon trên Ubuntu sau Week 4. |

## Demo checklist M2 → M5

- Bắt đầu bằng topology Server + Ubuntu A, bổ sung Ubuntu B cho endpoint isolation.
- Hiển thị hai rule nguyên tắc: canonical non-storage Allow và daemon default BLOCK; sau đó demo Insert USB thật, event + active username, Allow/Revoke A, B unchanged.
- Thử HID, unknown USB, serialless hash case, logout/switch/UNKNOWN, server offline, reboot, popup bị BLOCK và bỏ qua khi ALLOWED/HID/Remove.
- Với mọi test, ghi commit, Ubuntu/USBGuard version, endpoint ID, timestamp, expected versus observed, log đã che PII/credential.
- Khi trình chiếu Windows fixtures trước Ubuntu E2E, ghi rõ đây là synthetic/mock evidence, **không giả làm physical USB result**.
