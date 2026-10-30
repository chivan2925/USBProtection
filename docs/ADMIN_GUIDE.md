# USBShield Admin Web — Hướng dẫn quản trị

**FINAL — Week 4, 30/10/2026.** Hướng dẫn đã kiểm tra trên Windows với backend Spring Boot/H2; việc chặn USB thật cần Agent/USBGuard trên Ubuntu.

## Mở giao diện

Với môi trường phát triển mặc định:

- Admin Web: `http://127.0.0.1:5173/`.
- Backend API: `http://127.0.0.1:8080`.
- Demo chỉ đọc: `http://127.0.0.1:5173/?demo=1#/dashboard`.

Người phụ trách backend cấp tài khoản Admin qua cấu hình ngoài repository. Không có mật khẩu production mặc định. Bootstrap chỉ tạo tài khoản chưa tồn tại; đổi biến môi trường không đổi mật khẩu tài khoản đã có.

Nếu cần chạy frontend, mở PowerShell ở thư mục `frontend` rồi chạy:

```powershell
npm ci
npm run dev -- --host 127.0.0.1
```

Backend chạy riêng theo README và hướng dẫn M1. Frontend chuyển `/api` đến backend qua Vite proxy cùng origin. Đổi đích proxy bằng biến `API_PROXY_TARGET` nếu backend dùng URL khác.

Nếu `npm ci` báo EPERM với native module đang dùng, dừng tiến trình Vite của project rồi thử lại. Không cần xóa source hoặc đổi lockfile.

## Đăng nhập và đăng xuất

1. Mở giao diện và nhập tên đăng nhập, mật khẩu do backend cấp.
2. Nhấn **Đăng nhập**. Nếu thông tin sai, sửa và nhập lại mật khẩu.
3. Khi hết phiên, giao diện quay về Login, thông báo cần đăng nhập lại và gỡ nội dung cần quyền Admin.
4. Đăng xuất bằng nút tài khoản ở cuối thanh điều hướng. Trên mobile, mở menu trước.

Cookie phiên do trình duyệt quản lý. Frontend không lưu mật khẩu hoặc token phiên vào localStorage/sessionStorage.

## Đọc Dashboard và danh sách endpoint

- Dashboard hiển thị thống kê từ lịch sử API đã tải. Số 0 nghĩa là không có bản ghi; dấu `—` nghĩa là chưa có dữ liệu xác định.
- Protection/service **Chưa xác định** nghĩa là API chưa cung cấp bằng chứng trạng thái bảo vệ.
- Endpoints hiển thị hostname, ID, ONLINE/OFFLINE, người dùng nếu có, phiên bản policy và thời điểm kết nối gần nhất.
- ONLINE nghĩa là backend còn thấy heartbeat trong ngưỡng cấu hình. Trạng thái này không tự chứng minh USBGuard đã áp dụng policy.
- Dùng tìm kiếm/bộ lọc, sau đó nhấn **Xem chi tiết** để quản lý đúng máy.

## Hiểu desired, applied và ACK

| Trường / trạng thái | Ý nghĩa |
| --- | --- |
| Policy version / desired | Phiên bản whitelist server muốn Agent áp dụng. |
| Last Applied Version / applied | Phiên bản Agent đã báo ACK với server. |
| Desired khác applied | Chưa xác nhận đồng bộ phiên bản mong muốn. |
| Agent đã ACK phiên bản này | Server nhận ACK cho đúng phiên bản; đây là báo cáo của Agent. |
| Chưa có dữ liệu ACK | Chưa nhận đủ thông tin phiên bản để xác nhận đồng bộ. |
| Endpoint offline, đang chờ | Máy không có heartbeat gần đây; policy chưa đồng bộ phải chờ Agent kết nối và áp dụng. |

Nhấn **Refresh dữ liệu** để kiểm tra tiến độ. Chưa có ACK không tự đồng nghĩa apply thất bại. Backend hiện chưa có trường mô tả lỗi apply riêng; UI không đoán lý do.

## Allow USB trên một endpoint

1. Vào **Endpoints**, mở đúng máy cần cấp quyền.
2. Nhấn **Allow on this endpoint**.
3. Chọn USB đã xuất hiện trong sự kiện của endpoint và có ID/HASH từ backend. Thiết bị không có serial vẫn có thể dùng HASH hợp lệ.
4. Kiểm tra endpoint và HASH trong hộp xác nhận rồi nhấn **Xác nhận thay đổi**.
5. Server lưu whitelist; kiểm tra desired/applied bằng **Refresh dữ liệu** cho đến khi Agent ACK phiên bản mới.

Nếu chưa có USB phù hợp, cần Agent gửi sự kiện trước. Frontend không cho nhập tên/serial để tự tạo danh tính yếu. Cấp quyền cho endpoint A không cấp quyền cho B.

## Revoke quyền USB

1. Trong chi tiết endpoint, tìm entry cần thu hồi ở **Whitelist thiết bị**.
2. Nhấn **Revoke on this endpoint**, kiểm tra endpoint/entry rồi xác nhận.
3. Theo dõi phiên bản mới và ACK. Lưu Revoke thành công chưa chứng minh máy thật đã chặn lại USB.

Nút xác nhận bị khóa khi đang gửi. Nếu có lỗi, đọc thông báo và thử lại; không coi lỗi mạng là bằng chứng thao tác chưa được server lưu. Refresh để kiểm tra trạng thái hiện tại.

## Devices, Whitelist, Blocked Devices và Settings

- **Devices:** thiết bị được ghi nhận từ lịch sử event, theo từng endpoint.
- **Whitelist:** rule server đã lưu; mở endpoint để Allow/Revoke. Không có quyền Allow toàn hệ thống.
- **Blocked Devices:** quyết định BLOCKED trong sự kiện gần nhất, không phải phép đo USBGuard thời gian thực.
- **Event Logs:** xem sự kiện và bộ lọc do phần Event History của M5 cung cấp.
- **Settings:** thông tin phiên và phạm vi hỗ trợ. Cấu hình dịch vụ chưa có API sẽ được giải thích là chưa khả dụng.

## Khi gặp lỗi

| Tình huống | Cách xử lý |
| --- | --- |
| Không kết nối / timeout | Kiểm tra backend, URL proxy và mạng; nhấn Thử lại hoặc Refresh. |
| HTTP 401 | Đăng nhập lại; phiên không còn hợp lệ. |
| HTTP 403 | Kiểm tra quyền và tải lại trang để lấy CSRF mới. |
| HTTP 409 | Dữ liệu bị trùng hoặc đã thay đổi; tải lại trước khi thao tác tiếp. |
| HTTP 5xx | Backend gặp lỗi; thử lại và chuyển log cho người phụ trách server nếu lặp lại. |
| Payload API sai | UI từ chối dữ liệu thay vì hiển thị thành công giả; chuyển lỗi cho M1/M4. |
| Danh sách rỗng | Có thể chưa có endpoint/event; live UI không tự dùng mock thay thế. |

Demo có nhãn **Dữ liệu mock**, chỉ để xem giao diện và luôn khóa thay đổi policy. Dùng **Thoát demo** để trở lại API thật.

## Xác nhận M4 Week 4

- `npm test`: 96/96 PASS.
- `npm run lint`: PASS.
- `npm run build`: PASS; frontend production bundle tích hợp được vào server JAR.
- `npm run test:browser`: PASS; auth, CSRF, loading/empty/error, Allow/Revoke A/B, demo read-only và responsive routes.
- `npm run test:live`: PASS; Spring Boot/Spring Security/H2 thật, session, policy version, A/B isolation, controlled HTTP ACK và mobile.
- Screenshots và log: [Week 4 evidence](qa/week4-evidence/2026-10-28/windows-regression/README.md).

Giới hạn: test trên Windows dùng HTTP/H2 và dữ liệu tổng hợp. Ubuntu/USB vật lý/USBGuard/systemd/reboot/native GUI notification chưa thực thi; không suy ra hardware PASS từ demo hoặc HTTP test.
