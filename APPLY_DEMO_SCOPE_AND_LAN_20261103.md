# USBProtection — demo-scope frontend/outbox/blueprint patch

**Baseline chính xác:** `main` HEAD `0e6a29cf10947f7fc79ff8dd9972bda07a60a071` (merge PR #59). Nội dung chỉ gồm file mới hoặc file chỉnh sửa. Không tự push, không tạo PR, không merge.

## Phạm vi thực hiện

- **Giữ global USB catalog lock và H2.** Không thiết kế lại concurrency, không chuyển DB hay microservices.
- **Frontend duplicate requests:** chỉ refresh riêng trang thiết bị khi parent snapshot mới được refresh; đổi filter/page do `useResource` tự tải. Snapshot endpoint không fetch 100 device state dư; EndpointDetail fetch riêng page 20.
- **Frontend production-path tests:** mở rộng real Spring Boot/H2 + Chromium `frontend/tests/browser-live.mjs` với Devices/EndpointDetail/Search/1-request checks; API fixture tests bổ sung scoping. Không thay thế physical USB E2E.
- **Outbox:** cache quota khi enqueue thường, quét lại khi filesystem directory timestamp đổi hoặc write bị lỗi, giữ toàn bộ quarantine và fail-closed semantics. Không chấp nhận file edits thủ công trong lúc Agent đang chạy; khi operator can thiệp phải restart Agent.
- **Blueprint:** chốt demo-only, Docker tùy chọn, JAR + React cùng origin, quyền normal user, phạm vi hiện tại và E2E deferred.
- **Không sửa USBGuard runtime Allow/Revoke:** current-device action cần quyền IPC `Devices=modify`, chống runtime-ID reuse và E2E Ubuntu trước khi tuyên bố immediate. Phần này chỉ cập nhật quyết định trong blueprint.

## Áp dụng thủ công

1. Kiểm tra `git rev-parse HEAD` bằng SHA baseline trên và `git status --short` sạch.
2. Giải nén ZIP vào root `USBProtection` và ghi đè đúng các file tương ứng.
3. Kiểm tra `git diff --check`, `git status --short` và `git diff --stat` trước khi commit.
4. Test trên Windows:

```powershell
mvn -f admin-server/pom.xml clean test
mvn -f client-agent/pom.xml clean test
cd frontend
npm ci
npm test
npm run lint
npm run build
cd ..
# Browser production path cần frontend/dist + Admin Server JAR + Chromium:
mvn -f admin-server/pom.xml -Pwith-frontend -DskipTests package
cd frontend
npm run test:browser:live
cd ..
git diff --check
```

**Lưu ý:** Browser test chạy Spring Boot/H2 và Chromium local qua HTTP với tài khoản QA synthetic; không xác minh TLS/LAN/hardware. Cần `CHROME_PATH` nếu browser không nằm ở vị trí mà harness tìm kiếm. Ubuntu E2E và TLS deployment xác minh riêng, **chưa thực hiện** trong lượt patch.

## Trạng thái kiểm chứng ở môi trường tạo patch

- Baseline các file modified được đối chiếu byte-for-byte với blob SHA của `main` (LF-normalized cho source Markdown/CRLF).
- Node API tests chọn lọc: **16/16 PASS**.
- Frontend `tsc -p tsconfig.app.json --noEmit`: **PASS**.
- `node --check` browser và tests: **PASS**.
- Java 21 standalone compilation + quota/cache/out-of-band/drain smoke: **PASS** (stubs thay dependencies, **không phải JUnit**).
- Full Maven, full frontend tests/lint/build, production browser và Ubuntu E2E: **NOT EXECUTED**.

Xem [`docs/ADMIN_SERVER_LAN_SETUP.md`](docs/ADMIN_SERVER_LAN_SETUP.md) cho hướng dẫn build và chạy Admin Server qua JAR trong LAN, không bắt buộc Docker.
