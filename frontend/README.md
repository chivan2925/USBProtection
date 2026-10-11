# USB Protection — Frontend

Giao diện quản trị React + TypeScript + Vite, giữ nguyên stack hiện có. Không thêm dependency.

## Chạy và kiểm tra

Dùng Node.js 22.20+ (môi trường đã kiểm tra) và npm. Chạy trong `frontend/`:

```sh
npm ci
npm run dev -- --host 127.0.0.1
npm run build
npm run lint
npm test
npm run test:browser
```

- `build`: kiểm tra TypeScript và tạo bản production trong `dist/`.
- `lint`: Oxlint, không có cảnh báo trong lần kiểm tra bàn giao.
- `test`: 11 kiểm thử logic và component bằng Node test runner, React server renderer và Vite đã có sẵn.
- `test:browser`: chạy bản build bằng Vite preview trên `127.0.0.1:4173`, mở Chrome/Edge headless, kiểm tra điều hướng/refresh/filter/login/responsive và lỗi JavaScript. Chạy build trước. Cần cổng 4173 trống. Tự đóng các tiến trình sau khi xong.
- Trình duyệt được tìm tại vị trí cài đặt Windows thông thường; có thể đặt `CHROME_PATH` để chỉ định executable. Không tải trình duyệt hay thêm thư viện automation.
- Ảnh kiểm tra lưu trong `.artifacts/` (gitignored). Profile trình duyệt tạm nằm trong workspace và được xóa sau kiểm tra.
- Windows sandbox hạn chế tạo tiến trình có thể báo `spawn EPERM`; build/test cần môi trường cho phép Node/Vite chạy tiến trình con.

## Kết quả phân tích backend

Đã kiểm tra `backend/src`, `pom.xml` và `application.yaml`:

- Java 21, Spring Boot, Maven; dependencies Web MVC, JPA, MySQL, Lombok.
- Chỉ có `UsbProtectionApplication` và test `contextLoads`.
- Không có Controller, Service, Repository, Entity hoặc endpoint HTTP nghiệp vụ.
- `application.yaml` chỉ khai báo tên ứng dụng.
- Không có API xác thực, thống kê, service, USB, endpoints, whitelist/blacklist, lịch sử hoặc settings.

**API đã kết nối: chưa có, vì backend chưa cung cấp API.** Không thay đổi backend trong đợt triển khai FE này.

## Các màn hình

Dùng hash routing để truy cập trực tiếp, reload và Back/Forward mà không cần cấu hình rewrite trên web server.

| Đường dẫn | Nội dung |
| --- | --- |
| `#/dashboard` | Trạng thái bảo vệ/service; 4 thẻ thống kê; hoạt động gần nhất; endpoints online/offline; người dùng gần đây; refresh |
| `#/devices` | Bảng thiết bị, VID/PID, serial, endpoint, trạng thái, thời gian; tìm kiếm không phân biệt dấu/hoa thường; lọc trạng thái |
| `#/whitelist` | Thiết bị được phép; tìm kiếm và lọc endpoint; cấu trúc thao tác thu hồi quyền có xác nhận |
| `#/blocked` | USB bị chặn; lý do/thời gian; tìm kiếm và lọc endpoint; cấu trúc cho phép thiết bị có xác nhận |
| `#/events` | Cắm/rút/chặn/cho phép USB và sự kiện service; tìm kiếm; lọc loại; mới nhất trước; phân trang 10 dòng |
| `#/endpoints` | Máy Ubuntu, hostname, online/offline, user, policy version, last seen; tìm kiếm/lọc và liên kết chi tiết |
| `#/endpoints/:id` | Chi tiết một máy; thiết bị và sự kiện chỉ thuộc endpoint đó |
| `#/settings` | Khả năng tích hợp thực tế và danh sách chức năng chờ backend; không tạo setting vô tác dụng |
| `#/login` | Form username/password với trạng thái chưa có API xác thực; liên kết xem giao diện quản lý |
| Đường dẫn khác | Trang 404 và liên kết về Dashboard |

Giao diện responsive có sidebar, highlight trang hiện tại, topbar, menu mobile, focus bàn phím, loading/skeleton, trạng thái rỗng, lỗi có thử lại, toast, dialog xác nhận native và hỗ trợ reduced motion.

## Dữ liệu và giới hạn hiện tại

- Không có dữ liệu mẫu trong ứng dụng. Dữ liệu fixture chỉ nằm trong tests.
- Thống kê chưa biết hiển thị `—`, không hiển thị số 0 hay trạng thái “đang bảo vệ” giả.
- `services/api.ts` là adapter tập trung. Khi chưa có backend, adapter trả `BackendUnavailableError` và không gọi URL tự đặt.
- `services/models.ts` là view model của FE, **không phải hợp đồng API đã được backend triển khai**.
- `useResource` xử lý tải, lỗi, thiếu API, hủy request cũ và chống cập nhật dữ liệu sau khi unmount.
- Chính sách allow/block/revoke và đăng nhập bị vô hiệu hóa bằng capability hiện tại. Không tạo token giả, không lưu username/password và không tự cấp quyền admin.
- Dialog xác nhận và thông báo thành công/thất bại cho thay đổi chính sách đã có cấu trúc xử lý, nhưng không thể thực hiện thao tác cho đến khi có API thật.
- Không có chức năng bật/tắt bảo vệ, auto-block, cập nhật settings hoặc sửa service hoạt động thực tế do backend chưa hỗ trợ.
- Bộ lọc và phân trang xử lý trên tập dữ liệu adapter cung cấp. Khi backend bổ sung phân trang phía server, phải điều chỉnh adapter/hook theo hợp đồng thật; không dùng một trang kết quả làm toàn bộ dữ liệu.
- FE hiện là giao diện chờ tích hợp; không thực hiện phát hiện/chặn USB ở hệ điều hành và chưa có cơ chế xác thực end-to-end.

## Cấu trúc và danh sách file

Các file mới:

```text
src/
  components/
    Common.tsx          # Badge, PageHeading, Panel, EmptyState, LoadingState, ResourceNotice
    ConfirmDialog.tsx   # Xác nhận, khóa khi xử lý, lỗi và trả focus
    DataTable.tsx       # Bảng chung, trạng thái, phân trang
    Filters.tsx         # Tìm kiếm, select, reset
    Icon.tsx            # Icon SVG dùng chung, không tải từ bên ngoài
    Toast.tsx           # Toast provider, tự đóng và live region
  hooks/
    useResource.ts      # Vòng đời đọc dữ liệu và refresh
    useRoute.ts         # Hash route, Back/Forward
    useToast.ts         # Toast context và hook
  layouts/
    AdminLayout.tsx     # Sidebar, topbar, responsive shell
  pages/
    Dashboard.tsx
    Devices.tsx         # Tái sử dụng cho Devices, Whitelist, Blocked Devices
    Endpoints.tsx       # Danh sách và chi tiết endpoint
    Events.tsx
    Login.tsx
    Settings.tsx
    types.ts
  services/
    api.ts
    models.ts
  utils/
    format.ts           # Thời gian, thống kê, tìm kiếm, sắp xếp, phân trang
tests/
  core.test.mjs
  components.test.mjs
  browser.mjs
```

Các file hiện có được sửa:

- `src/App.tsx`: router, điều phối dữ liệu, toast provider.
- `src/App.css`, `src/index.css`: thay giao diện mẫu bằng admin dashboard.
- `index.html`: ngôn ngữ tiếng Việt, title, description, theme color.
- `public/favicon.svg`: nhận diện USB Protection.
- `package.json`: thêm script test/test:browser; dependencies giữ nguyên.
- `.gitignore`: loại trừ `.artifacts/`.
- `README.md`: tài liệu này.

Các asset của template không còn được import; không xóa dữ liệu có sẵn của project. `package-lock.json` không đổi vì không có dependency mới.

## Tích hợp khi backend sẵn sàng

1. Xác nhận endpoint thực tế, schema, phân trang, timezone, định nghĩa “sự kiện gần đây” và quyền truy cập.
2. Thay các method ở `services/api.ts` bằng HTTP request tới endpoint thật; validate/map dữ liệu vào view model. Truyền `AbortSignal` và xử lý lỗi HTTP, xác thực, response không hợp lệ, timeout theo backend.
3. Bổ sung session/auth guard/logout và hiển thị trạng thái người dùng thật khi backend có xác thực. Chế độ xem giao diện hiện tại không được dùng làm cơ chế phân quyền.
4. Bật từng capability tương ứng sau khi API đã hoạt động; xử lý policy theo đúng scope endpoint và định danh thiết bị backend quy định.
5. Bổ sung form settings dựa trên schema thật và trạng thái lưu thành công/thất bại; không chỉ bật công tắc ở FE.
6. Chạy lại build/lint/tests và thêm kiểm thử tích hợp với backend.

## Kiểm tra đã thực hiện

- Production build và TypeScript: thành công.
- Oxlint: không lỗi/cảnh báo.
- 11 unit/component tests: thành công (trạng thái không xác định, tìm kiếm tiếng Việt, thứ tự thời gian, phân trang, hủy request, không gọi API giả, tách whitelist/blocked, tách endpoint, render text an toàn, các trạng thái bảng).
- Chrome headless desktop/mobile: điều hướng các màn hình, URL sai, refresh toast, filter/reset, login vô hiệu hóa, không lưu credentials, menu mobile, không tràn ngang, không có lỗi runtime hoặc Fetch/XHR đến API không tồn tại.
- Chưa kiểm thử thao tác nghiệp vụ end-to-end vì chưa có API backend.
