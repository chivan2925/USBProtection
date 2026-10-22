# M4 — Week 3 UI/UX hardening

Ngày kiểm tra: **22/10/2026**, múi giờ **Asia/Saigon**. Môi trường: Windows 11, Java 21.0.9, Node.js 24.21.0, npm 11.19.0.

Revision gốc: `559d0b6a9662baa3291511763d043b7ffed702d5`; kết quả bên dưới chạy trên working tree có thay đổi M4, chưa commit. [Metadata môi trường](week3-evidence/00-environment.json) và [SHA-256 source/test](week3-evidence/09-source-sha256.txt) xác định bản đã kiểm tra.

## Phạm vi và ownership

Chỉ sửa source/test M4 và tài liệu `docs/admin-web/**`, `docs/ADMIN_GUIDE.md`. Không sửa Admin Server, Client Agent, packaging, QA của M5 hoặc Event History.

Giữ nguyên các file dùng chung: `frontend/src/services/api.ts`, `frontend/tests/browser.mjs`, `frontend/tests/liveServer.mjs`, `frontend/package.json`, `frontend/vite.config.ts`. Các suite có sẵn được chạy để kiểm tra tương thích, không chỉnh nội dung của M5. API contract unchanged.

## Checklist màn hình và chức năng

| Phần việc | Kết quả rà soát / thay đổi |
| --- | --- |
| App shell / route | Route cần phiên Admin; logout/401 gỡ nội dung được bảo vệ. Mỗi route quản lý có snapshot mới, bắt đầu bằng loading thay vì giữ dữ liệu trang trước. Mobile menu, bàn phím và bố cục được kiểm tra bằng suite browser. |
| Login / session | Sai mật khẩu có lỗi rõ; đúng mật khẩu xác minh `/me` và CSRF mới. Reload khôi phục phiên. Thêm thông báo hết phiên ở Login; xóa CSRF và hủy kiểm tra phiên cũ khi nhận 401. Không lưu credential trong localStorage/sessionStorage. |
| Dashboard | Giữ dữ liệu API thật, thống kê và số endpoint; empty/error không chuyển sang mock. Protection/service giữ unknown khi thiếu API xác minh. Không cần sửa màn hình đã đạt. |
| Endpoints | Hiển thị ONLINE/OFFLINE, lastSeen, desired và applied riêng. Không tự tính lại status bằng đồng hồ trình duyệt; status do backend cung cấp. |
| Endpoint Detail | Giữ thiết bị, whitelist và sự kiện theo endpoint. Bổ sung giải thích desired/applied, nhãn pending khi offline, hướng dẫn Refresh và cảnh báo chưa có ACK chưa chứng minh apply thất bại. |
| Allow / Revoke | Có xác nhận endpoint/HASH/entry. Whitelist server lưu chưa đồng nghĩa Agent đã áp dụng. Hộp xác nhận khóa đồng bộ bằng ref để chặn nhấn lặp trước khi React render; lỗi giữ dialog để người dùng thử lại. |
| Devices / Whitelist / Blocked | Whitelist lấy từ rule server, trạng thái thiết bị lấy từ sự kiện gần nhất; điều hướng về endpoint để thay đổi quyền. Không có global Allow. Không cần sửa phần đã đạt. |
| API mapping | Endpoint mapper từ chối lastSeen sai, version âm/lẻ/không an toàn và applied lớn hơn desired. Whitelist mapper từ chối fingerprint khác HASH, HASH rỗng, enabled sai kiểu và dữ liệu nhầm endpoint. Không suy ra quyền từ serial hoặc tên USB. |
| Lỗi và retry | 401/403/409/5xx, HTML/JSON sai, timeout, mất mạng và hủy request đã có xử lý; regression PASS. Loading/error không giữ protected rows hoặc chuyển sang demo. |
| Settings | Chỉ hiển thị thông tin; API cấu hình dịch vụ chưa có được giải thích rõ, không có điều khiển giả. |

## Kết quả thực thi Windows

Đã kiểm tra ở `frontend/` với Vite 8.3.5 hiện có. Để xác minh đúng lockfile, chạy lại lint/test/build và các suite từ bản sao source tại `frontend/.artifacts/week3-clean-install` với Vite **8.3.1** do `npm ci` cài. Các log bên dưới được chốt từ lượt chạy đúng lockfile; mọi hàng PASS có exit code **0**.

| Lệnh / ca kiểm tra | Kết quả | Evidence |
| --- | --- | --- |
| `npm run lint` | PASS | [01-lint.txt](week3-evidence/01-lint.txt) |
| `npm test` | **96/96 PASS**, gồm M4 và regression M5; thêm 8 test M4 so với 88 test ban đầu | [02-tests.txt](week3-evidence/02-tests.txt) |
| `npm run build` | PASS; production bundle trong `frontend/dist` | [03-build.txt](week3-evidence/03-build.txt) |
| `npm run test:browser` | PASS; fixture HTTP, auth, A/B, dialog, lỗi/rỗng/mạng, demo, responsive | [04-browser-fixture.txt](week3-evidence/04-browser-fixture.txt) |
| `npm run test:live` | PASS; Spring Security/H2 thật, Allow/Revoke, CSRF, session, HTTP ACK, restart/persistence | [05-browser-real-backend.txt](week3-evidence/05-browser-real-backend.txt) |
| `npm run test:browser:live` | PASS; regression Event History có sẵn của M5 với 28 event HTTP tổng hợp | [06-browser-events-regression.txt](week3-evidence/06-browser-events-regression.txt) |
| `node --test tests/m4_policy_live.test.mjs` | **1/1 PASS**; Revoke lặp idempotent, ACK tương lai 409 không đổi applied và endpoint B giữ nguyên | [07-policy-real-backend.txt](week3-evidence/07-policy-real-backend.txt) |
| `npm ci --prefix .artifacts/week3-clean-install --no-audit --no-fund` | PASS sau khi copy package.json và package-lock.json vào thư mục thử riêng | [08-clean-install.txt](week3-evidence/08-clean-install.txt) |

`npm ci` ngay trong thư mục frontend từng gặp EPERM khi Windows khóa native module đang dùng. Kiểm tra clean install thực hiện trong thư mục artifact riêng để giữ Vite đang phục vụ giao diện. Package manifest và lockfile gốc giữ nguyên; phiên bản Vite của hai nơi nằm trong metadata môi trường.

Bản sao kiểm thử chứa nguyên source/test/config frontend, fixture `docs/agent-contract/sample-usb-event.json` và JAR server đã build ở đúng vị trí tương đối mà harness yêu cầu. Đây là artifact cục bộ, không chỉnh file gốc của owner khác. Source/test của bản sao được đối chiếu SHA-256 với source bàn giao. `frontend/dist` nhận production build đã kiểm tra từ bản sao này.

Fixture cũ xóa entry khi Revoke nên lần Revoke thứ hai trả 404. Test M4 xác minh lỗi này không tăng desired lần nữa. Test live riêng xác minh backend thật giữ entry đã disable và trả 204 idempotent; không sửa fixture dùng chung để làm hai hành vi trông giống nhau.

## Ảnh chụp đã kiểm tra

- [Endpoint A: desired = 2, applied = 0, đang chờ Agent](week3-evidence/m4-live-allow-a.png).
- [Hết phiên: thông báo Login và không còn protected rows](week3-evidence/m4-live-expired-session.png).
- [Devices trên mobile](week3-evidence/m4-mobile.png).
- [Lỗi mạng: có retry, không hiển thị mock](week3-evidence/m4-network.png).

## Tính năng chưa khả dụng và test hoãn

- Backend chưa cung cấp API health USBGuard/protection hoặc cấu hình dịch vụ. Live UI giữ trạng thái chưa xác định; demo có nhãn dữ liệu mock.
- Backend chưa có trường mô tả lỗi apply/ACK từ Agent. UI chỉ hiển thị pending/không có ACK, không tự gán ACK failure từ thời gian chờ hoặc OFFLINE.
- Kiểm thử Agent thật, USBGuard, USB vật lý, native GUI notification, systemd/reboot và clean install `.deb`: **DEFERRED UNTIL AFTER WEEK 4**. HTTP ACK trong test không chứng minh USB đã được phép hoặc bị chặn trên máy thật.
- Kết quả này xác nhận phần M4 trong phạm vi Windows, không chốt full release gate hoặc trạng thái hoàn thành của M1/M2/M3/M5.

## Bàn giao M1 / M5

- M1 nhận `frontend/dist` từ `npm run build`; tích hợp static UI vào server thuộc M1. Bundle là artifact build, không bắt buộc commit.
- M5 dùng các log/ảnh ở đây làm evidence M4 và chạy lại regression trên commit tích hợp. Không thay lịch sử Week 1–2.
- Hướng dẫn thao tác: [ADMIN_GUIDE.md — DRAFT Week 3](../ADMIN_GUIDE.md). Bản final thuộc Week 4.
- Code mới dùng state/ref React hiện có, hàm ngắn và kiểm tra trực tiếp; không thêm dependency, abstraction hoặc cấu hình mới. Refresh vẫn do người dùng bấm; chỉ thêm polling khi có yêu cầu cập nhật tự động và contract phù hợp.
