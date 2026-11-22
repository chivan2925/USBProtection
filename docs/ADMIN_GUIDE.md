# USBProtection Admin Web — Hướng dẫn sử dụng và quản trị

> **Đối chiếu giao diện:** `main` `c7381fc7b9ba34aa561083431ce7c68b9a514a51` (20/11/2026). Hướng dẫn này dành cho **người sử dụng Admin Web**, không phải hướng dẫn build, phát hành JAR hay cài Ubuntu Agent. Xem [RUN_GUIDE.md](RUN_GUIDE.md) và [ADMIN_SERVER_RELEASE.md](ADMIN_SERVER_RELEASE.md) khi cần triển khai. Các số liệu kiểm thử Week 4 ở cuối là **hồ sơ lịch sử**, không phải một lần nghiệm thu mới của source hiện tại.

## 1. Truy cập Admin Web và phân biệt môi trường

- **Admin Web đã đóng gói:** trên máy Admin được phép truy cập, mở `https://usbprotection.lab/` khi sử dụng cấu hình lab/Nginx/CA như hướng dẫn [HTTPS/ACL](USBShield_HTTPS_ACL_Setup_usbprotection.lab_vinhkhang_4d2216c_20261109.md). Tên miền thực tế có thể khác, nhưng phải khớp chứng chỉ TLS và cấu hình ACL. Với môi trường khác, dùng HTTPS origin đã được quản trị viên cung cấp.
- **Phát triển frontend:** `http://127.0.0.1:5173/` khi Vite đang chạy; backend mặc định lắng nghe HTTP loopback `127.0.0.1:8080` trong profile phát triển. Cổng backend theo cấu hình có thể thay đổi.
- **Demo chỉ đọc:** `/demo` hoặc `/demo/` tại origin tương ứng; trang con qua hash như `/demo#/events`. Demo là mock và không gọi API thực; nút thay đổi policy bị khóa. Giao diện Admin chính dùng API thật, không tự thay dữ liệu lỗi bằng mock và không dùng `?demo=1` để bật demo.
- **Client Ubuntu:** chỉ Agent được truy cập `/api/agent/**` theo HTTPS và source-IP ACL. Người dùng ở Client không được xem trang Web/Auth/Admin API theo mô hình phân quyền mạng của lab. Spring Security vẫn kiểm tra session/token; ACL không phải biện pháp xác thực thay thế.

Nếu chạy Vite để phát triển:

```powershell
cd frontend
npm ci
npm run dev -- --host 127.0.0.1
```

Vite proxy chuyển `/api` đến backend; thay đích proxy bằng `API_PROXY_TARGET` khi cần. Nếu `npm ci` báo EPERM vì native module đang dùng, dừng Vite của project rồi chạy lại. Không cần cài Node/npm/Vite khi chỉ sử dụng JAR phát hành. **Không đưa thông tin tài khoản, token hay `.env.e2e.local` cho người dùng chỉ có quyền truy cập Web.**

## 2. Đăng nhập, phiên và đăng xuất

1. Mở Admin Web bằng HTTPS đã được cấu hình, nhập username/password của Admin. Không có mật khẩu production mặc định được dùng công khai.
2. Sau đăng nhập, Web lấy dữ liệu từ các API quản trị thuộc session hiện tại. Các thay đổi yêu cầu CSRF token hợp lệ.
3. Nếu mật khẩu sai, thử lại bằng thông tin được cấp; không tự thay đổi `USBSHIELD_ADMIN_PASSWORD` với kỳ vọng reset tài khoản cũ. Biến này chỉ phục vụ **bootstrap ban đầu**.
4. Khi hết hạn hoặc bị thu hồi phiên, Web chuyển về Login và không giữ dữ liệu quản trị cần quyền truy cập. Đăng xuất qua nút **Đăng xuất** ở thanh điều hướng (trên mobile có thể cần mở menu trước).

Session cookie do browser quản lý, frontend không lưu mật khẩu hay token phiên trong localStorage/sessionStorage. Không chia sẻ phiên Admin với Ubuntu Client.

## 3. Dashboard và Endpoints

- **Dashboard:** hiển thị các tổng hợp do backend cung cấp (thiết bị phát hiện, quyết định gần nhất ALLOWED/BLOCKED, sự kiện, v.v.); không tự tính tổng hệ thống từ một trang phân trang. `0` là số đếm thực, `—` nghĩa là chưa đủ dữ liệu.
- **Endpoints:** hiển thị hostname, ID, trạng thái ONLINE/OFFLINE, current user (nếu có), thời điểm heartbeat và thông tin policy; hỗ trợ tìm kiếm tên/ID và lọc ONLINE/OFFLINE. Kết quả live được **backend lọc trước, phân trang sau**.
- Nhấn **Xem chi tiết** hoặc mở đường dẫn endpoint để quản lý đúng một máy. Chi tiết endpoint và thay đổi policy không áp dụng cho toàn hệ thống.
- **ONLINE** chỉ thể hiện có heartbeat gần đây, **không** chứng minh USBGuard đang hoạt động hoặc policy đã thực sự bảo vệ USB. Trạng thái bảo vệ phải đọc cùng telemetry USBGuard và desired/applied version.

## 4. Policy version, ACK và USBGuard health

| Tín hiệu | Ý nghĩa |
|---|---|
| **Policy version / desired** | Phiên bản whitelist mà Server muốn Agent sử dụng. |
| **Last Applied Version / applied** | Phiên bản Agent đã báo ACK sau bước apply; dữ kiện do Agent gửi về. |
| **desired ≠ applied** | Chưa xác nhận Agent áp dụng đúng phiên bản mong muốn; kiểm tra đồng bộ và trạng thái Client. |
| **ACK phiên bản hiện tại** | Server chấp nhận thông báo ACK hợp lệ; **không** phải bằng chứng độc lập USB vật lý đã chuyển trạng thái khi vẫn đang cắm. |
| **Chưa có ACK / endpoint offline** | Cần chờ Agent kết nối, áp dụng và báo lại; không tự suy luận apply thất bại hoặc thành công. |

**USBGuard health** do Agent probe và Server tổng hợp, có thể hiển thị:

- **Healthy:** Agent báo daemon/rules/reconciliation đáp ứng yêu cầu, báo cáo còn mới, endpoint online và phiên bản desired/applied phù hợp.
- **Degraded:** có dấu hiệu tình trạng bảo vệ không thỏa các điều kiện, chẳng hạn version không đồng bộ.
- **Unavailable:** Agent báo không thể sử dụng hoặc kiểm tra USBGuard ở thời điểm probe.
- **Unknown:** thiếu báo cáo, dữ liệu stale/offline, Agent cũ chưa cung cấp trường health hoặc không đủ bằng chứng để kết luận.

Đọc `usbGuardCheckedAt`, `usbGuardStale`, `lastSeen`, trạng thái kết nối và phiên bản song song. ACK hay ONLINE riêng lẻ không được chuyển thành kết luận Healthy. Để nghiệm thu thực tế cần kiểm tra USBGuard và USB thật trên Ubuntu.

## 5. Allow USB trên một endpoint

1. Mở **Endpoints → chi tiết đúng endpoint**.
2. Chọn **Allow on this endpoint**. Chỉ chọn thiết bị đã được hệ thống nhận diện từ sự kiện/thiết bị của endpoint với ID và HASH hợp lệ; không nhập tùy ý serial/tên để tự tạo một danh tính yếu.
3. Kiểm tra **endpoint**, USB ID và HASH trong hộp thoại xác nhận; nhấn **Xác nhận thay đổi**.
4. Server lưu thay đổi whitelist và tăng desired version. Theo dõi Agent đồng bộ và ACK version mới.
5. **Rút USB và cắm lại** tại đúng endpoint để USBGuard đánh giá quyền ALLOW theo policy mới. Không yêu cầu hoặc mặc định có tác động tức thời trên USB vẫn đang cắm.

Nếu USB chưa từng xuất hiện, Agent cần ghi nhận event để Server có danh tính đủ mạnh. Thiết bị không có serial vẫn có thể hợp lệ khi nhận diện bằng HASH theo contract. Allow trên endpoint A **không** đồng nghĩa Allow trên endpoint B.

## 6. Revoke quyền USB

1. Trong chi tiết endpoint, tìm entry trong **Whitelist thiết bị**.
2. Nhấn **Revoke on this endpoint**, kiểm tra đúng endpoint/entry rồi xác nhận.
3. Chờ Agent áp dụng và ACK version mới, sau đó **rút/cắm lại USB** để kiểm tra BLOCK tại endpoint đó.

Nút xác nhận bị khóa khi request đang được xử lý. Nếu timeout/network error, không tự kết luận thao tác đã rollback — tải lại danh sách và version để kiểm tra trạng thái persisted trước khi thử lại.

## 7. Devices, Whitelist và Event Logs

**Devices:** hiển thị thiết bị đã được ghi nhận theo endpoint, cho phép tìm kiếm thiết bị theo tên/ID và lọc quyết định gần nhất. `BLOCKED`/`ALLOWED` phản ánh event/trạng thái được ghi nhận, **không** phải kết quả USBGuard đo ngay lúc người dùng nhìn trang. **Blocked Devices** không còn là trang độc lập; dùng bộ lọc **Bị chặn** trong Devices. Trạng thái **Chưa xác định** phản ánh dữ liệu `detected` của API.

**Whitelist:** các rule còn hiệu lực có thể được tìm kiếm/phân trang trên backend; muốn Allow/Revoke thì mở đúng endpoint. Không có thao tác Allow toàn hệ thống.

**Event Logs:** trong chế độ live, sử dụng API `/api/admin/events` với phân trang máy chủ (mỗi trang **10 bản ghi**). Các bộ lọc gồm Endpoint (tên/ID), Linux User (tìm phần tên không phân biệt hoa/thường), USB (tên/ID), Event Type, Decision (ALLOW/BLOCK), Từ ngày và Đến ngày. Khoảng ngày được hiểu theo ngày lịch local của frontend để tạo giới hạn thời gian truy vấn; không tự sửa UTC dữ liệu nguồn. Có thể thấy 14 bản ghi phù hợp nhưng trang đầu chỉ hiện 10: đó là **phân trang bình thường**, không phải thiếu dữ liệu. Khi bộ lọc không có kết quả, Web hiển thị “Không tìm thấy kết quả”.

**Settings đã được xóa:** Menu, trang `/#/settings` và API `POST /api/admin/account/password` không còn tồn tại ở HEAD `c7381fc7`. Truy cập trực tiếp hash route cũ hiển thị trang không tìm thấy. Admin **không đổi mật khẩu qua Web**. Nếu quên mật khẩu, liên hệ người quản trị máy Server để thực hiện quy trình reset **offline, không HTTP** qua profile `admin-reset`; không thay `USBSHIELD_ADMIN_PASSWORD` rồi restart vì đây chỉ là biến bootstrap. Chỉnh cấu hình USBGuard trực tiếp từ Web cũng không được hỗ trợ; telemetry health không phải thao tác cấu hình enforcement.

## 8. Khi gặp lỗi

| Biểu hiện | Xử lý |
|---|---|
| Không tải được dữ liệu, timeout, không kết nối | Kiểm tra Server/Nginx/HTTPS/network ACL; dùng nút thử lại nếu có hoặc tải lại trang. |
| HTTP 401 / phiên hết hạn | Đăng nhập lại, không tiếp tục với dữ liệu cũ. |
| HTTP 403 | Kiểm tra quyền, session và CSRF; tải lại trang để lấy CSRF mới. |
| HTTP 404 | Kiểm tra endpoint/đường dẫn và dữ liệu không còn tồn tại. |
| HTTP 409 | Có thay đổi đồng thời hoặc dữ liệu trùng/cũ; tải lại trang rồi xác nhận trạng thái trước khi thử lại. |
| HTTP 5xx | Server lỗi; lưu thông tin thời điểm/request phù hợp nhưng không chụp credentials hay token. |
| Payload API sai | Web từ chối hiển thị thành công giả; báo lỗi cho người phụ trách backend/frontend. |
| Không có endpoint/event | Không tự suy ra lỗi: hệ thống mới chưa có dữ liệu hoặc Agent chưa kết nối. |
| `prod` không mở được Swagger | Đây là mặc định bảo mật; `/api-docs` và `/swagger-ui.html` bị vô hiệu hóa. |

## 9. Ranh giới kiểm thử và hồ sơ lịch sử

**Hồ sơ M4 Week 4 ngày 28–30/10/2026, không phải lần chạy mới của HEAD hiện tại:** `npm test` 96 PASS; lint/build PASS; `test:browser` PASS; `test:live` khi đó dùng HTTP/H2 và event mô phỏng, với bằng chứng tại [Week 4 evidence](qa/week4-evidence/2026-10-28/windows-regression/README.md). Không sửa hoặc ghi lại các con số lịch sử này thành kết quả PostgreSQL mới.

**Báo cáo owner ngày 09/11/2026** ghi Windows verifier PASS, HTTPS/ACL lab PASS và Ubuntu Minimal Core P0/C1–C5 PASS theo owner. Các test Ubuntu mở rộng (reboot/offline/negative TLS/remove-upgrade/native IPC) chưa hoàn thành theo scope. Source `main` tại `c7381fc7` có thay đổi xóa Settings. **Owner đã báo `verify-windows.ps1` PASS các gate được thực thi** sau khi sửa test frontend, nhưng điều này không thay thế thử nghiệm HTTPS/ACL và Ubuntu USBGuard native. JAR trong `releases/` được build từ working tree dirty và không đạt trạng thái full release qualification. Tham khảo [owner closeout](qa/2026-11-09-ubuntu-minimal-core-owner-acceptance.md), [status snapshot](IMPLEMENTATION_STATUS_20261109.md) và [release workflow](ADMIN_SERVER_RELEASE.md).
