# M4 — Week 2 Admin Web integration

Cập nhật: 2026-10-16, Asia/Saigon.

Đã kiểm tra nhiệm vụ M4 theo mục 6 tài liệu phân công. Admin Web đã chạy với
Spring Boot/Spring Security và H2 File Mode của M1. Nhận định cũ
“repo chưa có API Admin đầy đủ” đã hết hiệu lực.

**M4 frontend/backend integration: PASS. Week 2 toàn nhóm chưa đóng.**
Agent apply/ACK thật, USBGuard, USB vật lý và desktop notification vẫn
**NOT EXECUTED**. Event và ACK trong test được tạo bằng script HTTP;
không phải quan sát USB thật hoặc ACK từ tiến trình M3.

## Contract đang sử dụng

Nguồn canonical: [API v0.2 của M1](../API.md). Không sửa backend hoặc mapper Events M5.

| Chức năng | Contract M1 | Xử lý M4 |
|---|---|---|
| CSRF | `GET /api/auth/csrf` → `{token,headerName}` | Token trong bộ nhớ; lấy trước login và sau login/khôi phục phiên |
| Login | `POST /api/auth/login`, `{username,password}` | Cookie phiên + CSRF; sai password 401 |
| Phiên/logout | `GET /api/auth/me`, `POST /api/auth/logout` | Kiểm tra khi reload; logout invalidate server session |
| Endpoints | `GET /api/admin/endpoints` → array; detail `/{endpointId}` | Map ID, hostname/IP, status, lastSeen, desired/applied |
| Events | `GET /api/admin/events?page=0&size=100` | Envelope `{content,page,size,totalElements,totalPages}`; lấy đủ trang |
| Whitelist | `GET /api/admin/endpoints/{endpointId}/whitelist` | Entries đúng endpoint; timestamp thiếu giữ `—` |
| Allow | `POST` cùng whitelist URL, `{usbDeviceId}` | Chọn USB có HASH và ID từ events của endpoint |
| Revoke | `DELETE .../whitelist/{entryId}` | Refetch whitelist và versions sau 204 |

M1 chỉ hỗ trợ HASH; form không còn VID:PID hoặc fingerprint tự nhập.
Allow trùng trả entry hiện có, không tăng desired version. M4 lấy `usbDeviceId`
ở adapter chung, giữ nguyên view model/filter/mapper Event History M5.

HTTP dùng cookie cùng origin, `credentials: 'include'`, `cache: 'no-store'`,
timeout 15 giây và hủy request. Không retry mutation hoặc nhận HTML login như
JSON thành công. Lỗi 400/401/403/404/409/5xx/network/timeout được báo riêng.
Phân trang kiểm tra page/totals/ID trùng; giới hạn 100 trang mỗi resource.
Khi vượt giới hạn cần API filtering/metrics server. Không hiển thị tổng một phần.

Live rỗng/lỗi không chuyển sang mock. Protection/service giữ unknown vì chưa
có health API chứng minh USBGuard đang bảo vệ. Admin 2xx chỉ xác nhận server lưu;
Agent cần apply/ACK để thay đổi quyền thật. `?demo=1` chỉ đọc, có nhãn mock và
không gọi API hoặc tạo phiên Admin.

## Chạy và kiểm thử

1. Build M1 bằng `mvn package -DskipTests` trong `admin-server`, với Java 21.
   Đặt `USBSHIELD_ADMIN_USERNAME`, `USBSHIELD_ADMIN_PASSWORD` và
   `USBSHIELD_ENROLLMENT_TOKEN` ngoài repo, rồi chạy
   `java -jar target/usbshield-admin-server.jar`.
2. Trong `frontend`, chạy `npm ci` nếu cần, rồi `npm run dev`. Vite proxy `/api`
   tới `http://127.0.0.1:8080`; đặt `API_PROXY_TARGET` trong `.env.local` nếu khác.
   Production phục vụ Web/API cùng origin.
3. Mở Web không có `?demo=1`; login, kiểm tra Dashboard/Endpoints/Events.
   Chọn endpoint, chọn USB đã ghi nhận, xác nhận Allow/Revoke; đối chiếu
   whitelist và desired/applied. Không ghi secret vào log/evidence.

Trong `frontend`:

```powershell
npm run build
npm run lint
npm test
npm run test:browser
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
npm run test:live
```

`test:browser` dùng HTTP fixture để kiểm tra lỗi/rỗng/demo/network và responsive.
`test:live` chạy JAR M1 thật, H2 riêng trong `frontend/.artifacts/live-*`,
credential ngẫu nhiên, enroll A/B qua HTTP, rồi Chrome/Edge headless qua Vite.
Không dùng database `admin-server/data/usbshield.mv.db`.

Live test xác minh login sai/đúng, CSRF rotation, reload, empty Dashboard,
endpoint/event mapping, Allow/idempotency/Revoke A, B isolation, policy fetch,
future ACK bị từ chối, ACK HTTP hiển thị đúng, dữ liệu tồn tại sau restart,
session hết hạn, logout, không lưu credential và mobile. ACK HTTP chỉ chứng
minh contract/persistence/UI, không chứng minh Agent apply.

Giữ port 4173 trống, cần Chrome/Edge cài sẵn; có thể đặt `CHROME_PATH`.
Test tự đóng tiến trình và xóa profile Chrome tạm; H2/log ở `.artifacts/`
gitignored. Evidence: [báo cáo M4](week2-evidence/README.md).

## Handoff Ubuntu cho M2/M3/M5

1. Chạy Agent M3 + USBGuard M2 trên Ubuntu, credential hợp lệ; cắm USB thật.
2. Lưu event ID/endpoint/Linux user/session/HASH/time; đối chiếu Web với backend
   và log USBGuard. Không thay payload sample M2 đã freeze.
3. Allow A qua Web; xác minh rule USBGuard, ACK do Agent gửi, applied version
   trên server/UI và B không đổi.
4. Revoke A; xác minh USB BLOCK lại và ACK mới. Kiểm tra đúng một native
   notification cho BLOCKED Mass Storage Insert trên đúng graphical user.
5. Gửi evidence cho M5 nghiệm thu; chỉ đổi NOT EXECUTED sau execution thật.

Máy hiện tại chạy Windows; kiểm tra WSL ngoài sandbox chưa được cấp quyền.
Lượt này chưa có evidence USBGuard/hardware.
