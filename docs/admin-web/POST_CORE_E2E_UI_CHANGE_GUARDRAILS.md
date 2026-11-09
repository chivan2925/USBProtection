# USBShield — M4/M5 UI guardrails after Minimal Core PASS

> **Đề xuất giao việc sau kết quả owner 09/11/2026:** M4/M5 có thể chỉnh Admin Web sau khi C1–C5 PASS **theo báo cáo owner**, nếu owner chấp thuận ranh giới scope và regression bên dưới. Không tự coi UI commit tương lai đã được Ubuntu E2E chứng minh. Code baseline tham chiếu: `vinhkhang` HEAD `6923be001890bee9a303a8cee8e7dcc2cf2db180`.

## Phân công

| Người | Mặc định được sửa | Khi nào cần review chéo |
|---|---|---|
| M4 | Layout/CSS, Login/Endpoints/Devices/Settings, responsive, table và phân trang | Đổi auth/CSRF, endpoint selection, Allow/Revoke, API DTO/mapping |
| M5 | Dashboard/Events, biểu đồ/bộ lọc, trình bày event và audit history | Đổi event mapping, source attribution, server filters/pagination hoặc shared API |
| M4 + M5 | `frontend/src/services/{http,api}.ts`, `services/mappers/**`, `hooks/AuthProvider.tsx`, `pages/Endpoints.tsx`, `components/endpoint/**` | **Shared/high-risk:** phải được M2/owner review và có targeted regression trước merge |

## Những contract KHÔNG được phá vỡ

1. **Same-origin HTTPS** qua `https://usbprotection.lab` và `/api/...`. Không thêm `http://` fallback, direct 18080, lưu credentials trong frontend, bỏ `credentials: include` hoặc lược bỏ CSRF.
2. **Endpoint isolation:** Allow/Revoke đúng endpoint A, đúng USB fingerprint/hash nhận từ event A; B không được ảnh hưởng. Không cho Allow chỉ bằng VID/PID, không tự đổi API method/path/payload/response status hay biến request lỗi thành success.
3. **Desired != applied:** `policyVersion` và `appliedPolicyVersion` là hai trạng thái; ACK/version khớp không đồng nghĩa USBGuard `HEALTHY`. `UNKNOWN`, `STALE` không được mặc định đổi thành bảo vệ thành công.
4. **Audit:** Giữ event source, endpoint/user, timestamp và trạng thái lịch sử; một event BLOCK cũ không phải trạng thái hiện tại của USB. Không tự tạo event hoặc fallback fixture vào live mode; phân trang và filter tại server giữ nguyên.
5. **Confirm/error UI:** người dùng nhìn rõ endpoint và USB trước Allow/Revoke, loading/disabled state tránh gửi mutation trùng; hiển thị lỗi 401/403/409 và failure thay vì tô trạng thái xanh lạc quan.
6. **UI-only scope:** không sửa `client-agent/`, `admin-server/src/main/`, `packaging/`, PostgreSQL schema, Nginx/CA/firewall và không reset DB/`endpoint.json` nếu owner không phê duyệt scope riêng.

## Risk-based acceptance

| Mức | Ví dụ | Regression yêu cầu |
|---|---|---|
| **A — CSS/presentation only** | Màu, font, spacing, responsive, bảng không đổi logic | `npm test`, lint, build + manual browser HTTPS: login, A/B Endpoints, Events, Whitelist. Không bắt buộc re-run USB C1–C5 nếu chứng minh logic unchanged |
| **B — UI state/interaction** | Sửa render, phân trang, filter, confirm dialog/refresh | Gate A + targeted Endpoints/Events/Ubuntu regression + browser-live trên **test DB riêng**, manual smoke lỗi/loading/ACK; nếu đổi Allow/Revoke interaction thì nâng lên C |
| **C — Flow/contract critical** | Allow/Revoke, endpoint ID/hash mapping, auth, CSRF, HTTP transport/API | Gate B + review M2 và **re-run C1–C5** trên JAR/UI mới trước khi tuyên bố baseline mới PASS |

### Lệnh áp dụng (PowerShell tại root repo)

```powershell
npm --prefix frontend ci
npm --prefix frontend test
npm --prefix frontend run lint
npm --prefix frontend run build
# Browser-live dùng .env TEST, không dùng DB usbshield của demo:
.\scripts\with-env.ps1 -RequireTestDb -Command { npm --prefix frontend run test:browser:live }
# Khi owner chủ động triển khai UI mới trong Admin JAR (JAR demo đã dừng):
mvn -f admin-server/pom.xml -Pwith-frontend -DskipTests package
```

Các test hiện hữu liên quan: `frontend/tests/ubuntu-e2e-regression.test.mjs`, `m4_endpoints.test.mjs`, `m4_admin_api.test.mjs`, `m4_auth.test.mjs`, `m5_events.test.mjs`, `m5_event_api.test.mjs`, `phase3_endpoint_whitelist_pagination.test.mjs`. `m4_policy_live.test.mjs` và `usbguard-health-contract.test.mjs` tồn tại nhưng **không được liệt kê trong** `frontend/package.json` `test` tại HEAD tham chiếu; nếu cần chạy thêm, xét điều kiện fixture/DB test trước, không chạy mọi file vô điều kiện.

**Triển khai:** React Vite build **không đổi giao diện đang được phục vụ bởi Admin JAR hiện tại**. Phải dừng JAR demo một cách có kiểm soát, package lại `-Pwith-frontend`, khởi động lại profile `prod` với `.env.e2e.local` hiện có. Giữ nguyên PostgreSQL, Nginx, CA, A/B và `.deb` đối với UI-only change. Không chạy `mvn clean` hoặc Windows verifier làm xóa `target` trong lúc JAR chạy từ đó. Với UI-only không cần reinstall Agent/re-enroll.

## Quy trình PR

1. M4/M5 mở branch từ HEAD `vinhkhang` mới nhất, PR nhỏ riêng biệt; shared files cần review chéo. Không push trực tiếp vào baseline đã nghiệm thu.
2. PR ghi **base SHA, file diff, nhóm A/B/C, screenshot trước/sau, output tests**, và xác nhận rõ có/không thay auth/CSRF/Allow/Revoke/API mapping.
3. M2/owner rà diff và xác nhận scope. Sau merge/build lại JAR, thực hiện live browser smoke; khi chưa smoke ghi `UI_CHANGED_AWAITING_SMOKE`, không kế thừa PASS C1–C5 cho code mới.
4. Nếu C1–C5 phải re-run vì PR mức C, chỉ chạy đúng Minimal Core; các test nâng cao vẫn **DEFERRED BY OWNER**.

Tham chiếu: [E2E owner closeout](../qa/2026-11-09-ubuntu-minimal-core-owner-acceptance.md), [Canonical E2E](../USBShield_Ubuntu24_Minimal_Core_E2E_vinhkhang_f446c04_20261107.md), [Run Guide](../RUN_GUIDE.md).
