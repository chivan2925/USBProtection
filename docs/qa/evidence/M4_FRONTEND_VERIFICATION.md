# Member 4 — Admin Web: Week 1 verification

Ngày kiểm tra: 2026-10-11 (Asia/Saigon). Người kiểm tra: Codex. Môi trường: Windows, Node.js 24.21.0, dependencies có sẵn và Chrome headless. Phạm vi: mục Member 4 và kế hoạch M4 Week 1 trong `USBShield_Project_Blueprint_Updated_AdminCentral_PerEndpoint (1).md`.

**Kết luận: PASS cho Admin Web skeleton/wireframe Week 1 sau khi bổ sung phần tích hợp UI.** Đây không phải PASS cho toàn bộ Member 4 trong bốn tuần hoặc system gate end-to-end.

## Đối chiếu nhận xét ban đầu

| Hạng mục | Trạng thái khi kiểm tra | Kết quả |
| --- | --- | --- |
| Merge markers trong `types/event.ts`, `mocks/events.ts` | Không còn marker trong checkout hiện tại; build ban đầu đã PASS | Không cần sửa hai file này; quét lại toàn frontend và build sau thay đổi |
| `EndpointWhitelistPanel` | Có component/test riêng nhưng màn Endpoint Detail chưa sử dụng | Đã tích hợp vào màn thực tế; chỉ hiển thị whitelist của endpoint đang chọn |
| API adapter trả `BackendUnavailableError` | Đúng; Dashboard/Endpoints chưa hiển thị fixture | Giữ adapter và bổ sung chế độ demo rõ nhãn, không gọi HTTP hay tạo auth giả |
| Login | Wireframe, form chờ API | Có liên kết mở demo; credentials không được gửi/lưu |
| Dashboard/Endpoints/Detail | Có UI skeleton | Có snapshot demo và ánh xạ ID/last seen để xem được dữ liệu và điều hướng |

## Thay đổi

- Snapshot có whitelist theo endpoint; Endpoint Detail dùng `EndpointWhitelistPanel` và không chuyển người xem sang danh sách tổng để xem policy riêng.
- Demo mở qua **Mở demo**, **Xem demo Admin Web** hoặc `?demo=1#/dashboard`; giữ tham số demo khi điều hướng hash và có **Thoát demo**.
- Cùng USB Kingston có status allowed ở LAB-PC-01, blocked ở LAB-PC-02. LAB-PC-02 không nhận whitelist của LAB-PC-01.
- Allow/Revoke vẫn disabled và giải thích chờ API; component cũng disabled khi thiếu callback. Không mô phỏng thành công thay đổi policy thật.
- Sửa tài liệu/comment cũ: `admin-server` đã có controller dành cho Agent, nhưng Admin Web API chưa được tích hợp.

## Kết quả thực thi

| Kiểm tra | Kết quả |
| --- | --- |
| Quét `<<<<<<<`, `=======`, `>>>>>>>` trong frontend | Không có unresolved merge markers |
| `npm run build` | PASS — TypeScript và Vite production bundle |
| `npm run lint` | PASS |
| `npm test` | PASS — 34/34, gồm 3 test mới về panel trong màn thực tế, trạng thái chưa có endpoint và snapshot demo |
| `npm run test:browser` | PASS — trạng thái chờ API và demo, desktop 1440px/mobile 375px |
| Quan sát ảnh Endpoint Detail desktop/mobile | PASS — panel whitelist và trạng thái rỗng rõ ràng, bảng nằm trong vùng cuộn |

Browser smoke kiểm tra Dashboard có số liệu mock, liên kết danh sách/event đến Endpoint Detail, whitelist tách theo endpoint, nút disabled, thoát demo, điều hướng/404, refresh, bộ lọc/reset, Login không lưu credentials, menu mobile, không tràn ngang, không có lỗi runtime/console hoặc Fetch/XHR.

Hai lần chạy browser trong sandbox timeout khi kết nối `Runtime.enable`; lần chạy với quyền cho phép Chrome hoạt động đã PASS. Ảnh được tạo cục bộ trong `frontend/.artifacts/`, không commit: `demo-dashboard-desktop.png`, `demo-endpoint-desktop.png`, `demo-endpoint-mobile.png`.

## Giới hạn

Chưa tích hợp Admin authentication/session và Admin REST API thật. Chưa kiểm thử Allow/Revoke → Agent sync → USBGuard enforcement, Java runtime, USB vật lý hoặc Ubuntu. Các phần này thuộc tích hợp tiếp theo; không phải điều kiện để PASS wireframe M4 Week 1.
