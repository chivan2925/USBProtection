# USBShield — Owner acceptance: Ubuntu Minimal Core E2E (09/11/2026)

> **Trạng thái: `MINIMAL_CORE_E2E_OWNER_REPORTED_PASS` (phạm vi P0 + C1–C5); `EXTENDED_UBUNTU_ACCEPTANCE_DEFERRED`.** Đây là ghi nhận kết quả owner báo cáo trong cuộc trao đổi ngày 09/11/2026, **không phải re-run độc lập**, không phải test suite Ubuntu đầy đủ và không có bộ raw evidence C1–C5 được đính kèm. Không áp dụng kết quả này tự động cho commit UI/build tương lai.

## 1. Baseline / topology của lần kiểm thử

- **Source repo đối chiếu khi closeout:** `chivan2925/USBProtection`, branch `vinhkhang`, HEAD `6923be001890bee9a303a8cee8e7dcc2cf2db180` (docs/packaging sync, 09/11/2026). **Không đồng nhất source HEAD với chính xác bytecode đang chạy** nếu không có JAR/DEB SHA và `git status` tại thời điểm build.
- **Admin host:** Windows 11; PostgreSQL `usbshield` ở loopback `127.0.0.1:5432`; Spring Boot JAR `prod` ở `127.0.0.1:18080`; Windows Nginx `https://usbprotection.lab` trên VMnet8 `192.168.23.1:443`.
- **Clients:** Ubuntu 24.04 A `usbshield-lab` / `192.168.23.133`; B `usbshield-b` / `192.168.23.141`; một NIC NAT VMnet8 trên mỗi VM, không có NIC Host-only.
- **DEB:** hai log Ubuntu đã cung cấp hiển thị `usbshield-agent 1.0.0+build20261109091706653` trên **cả A và B**. Version trùng không tự chứng minh package SHA256 trùng; nếu có file manifest/`sha256sum` của lượt chạy cuối, lưu trong evidence riêng.
- **USB:** một Kingston, chuyển vật lý/VMware passthrough từng VM một, không gắn đồng thời hai VM.

## 2. Kết quả HTTPS/ACL trước E2E

| Gate | Kết quả theo thông tin owner | Phạm vi chứng minh |
|---|---|---|
| Windows Nginx TLS + Admin Browser | `PASS` — browser mở, login Admin thật thành công; `curl` `/api/auth/csrf` → 200, unauth `/api/admin/endpoints/page` → 401 | Admin Web qua TLS; Windows curl Schannel của CA lab dùng `--ssl-revoke-best-effort`, **không** dùng `-k` |
| Ubuntu A/B HTTP ACL | `PASS` — `/`, `/api/auth/csrf`, `/api/admin/endpoints/page`, `/api/agent/policy` → `403/403/403/401` trên cả hai VM | Nginx route-level source-IP isolation + Spring Agent auth reject unauthenticated request |
| Bypass protection | `PASS` — A/B không truy cập được TCP/18080 và TCP/5432 từ VMnet8 | Không truy cập trực tiếp backend/DB theo phép thử đã làm |
| Nginx source IP | `PASS` — log nhận diện A `.133` và B `.141` riêng biệt | NAT-only lab có thể thực thi allowlist `/32` trong lần kiểm thử |
| Agent Java TLS + enrollment | `PASS` **suy ra trong phạm vi Core flow đã báo PASS**; không có log xác thực enrollment/heartbeat sau remediation đính kèm | Không dùng log heartbeat 401 trước remediation làm bằng chứng PASS. Khi cần audit độc lập, thu thập Java Agent 2xx/ACK/log đã redact |

**Sự cố P0 trước remediation:** lúc 16:57–16:59 ngày 09/11, Nginx ghi nhiều `POST /api/agent/heartbeat` → `401` từ cả `.133` và `.141`. Log Agent cho thấy `endpoint.json` lưu ID của đợt 02/11; câu SQL tra hai ID đó trong DB hiện tại trả **0 dòng**. Đây là bằng chứng identity cũ không còn hiện diện trong database đang tra. Owner về sau báo hoàn thành toàn bộ C1–C5; **không có output remediation chi tiết kèm theo**. Không kết luận từ log 401 rằng TLS hay Nginx ACL hỏng, và không tuyên bố chính xác thao tác reset nào đã được thực hiện.

## 3. Checkpoint owner báo PASS

| Checkpoint | Máy | Trạng thái | Acceptance contract — owner báo đạt toàn bộ |
|---|---|---|---|
| **P0** | A+B | **PASS theo owner** (suy ra từ việc hoàn thành C1–C5) | Agent online và đủ điều kiện chạy Core test. Chưa có snapshot online/lastSeen cuối đính kèm |
| **C1** | A | **PASS theo owner** | Kingston BLOCK, không truy cập storage; event và GUI BLOCK theo runbook |
| **C2** | A | **PASS theo owner** | Admin Allow riêng A; managed USBGuard rule và policy ACK sau thay đổi |
| **C3** | A | **PASS theo owner** | Rút/cắm lại A: Kingston thực sự ALLOW, được OS nhận storage |
| **C4** | B | **PASS theo owner** | Chuyển Kingston sang B: vẫn BLOCK; whitelist của A không mở B |
| **C5** | A | **PASS theo owner** | Revoke riêng A; managed Allow được gỡ và ACK mới; replug A BLOCK trở lại |

**Mức chứng cứ:** owner tuyên bố “đã test C1–C5 và toàn bộ đều PASS hết”; bảng nêu các acceptance criteria của kịch bản, **không phải khẳng định chúng đã được người biên soạn quan sát hoặc đo độc lập**. Giữ screenshot terminal, web version/ACK, popup và event đã redact tại máy owner nếu cần kiểm toán/bảo vệ đồ án; không lưu token, CA private key hoặc raw `endpoint.json` vào repository.

## 4. Quyết định đóng phạm vi / các bài test KHÔNG chạy

Owner **quyết định không thực hiện** các scenario Ubuntu phức tạp ngoài Minimal Core E2E tại thời điểm 09/11/2026. Ghi `DEFERRED BY OWNER / NOT EXECUTED`, tuyệt đối không ghi `PASS` cho:

- Offline spool/replay, mất mạng và phục hồi, server-down, token rotation, TLS negative/invalid CA/SAN.
- Ubuntu reboot, restart USBGuard, persistence sau reboot, stress/race, nguồn USB lạ/HID và acceptance T01–T28/X01–X18 ngoài C1–C5.
- DEB remove/upgrade, `prerm` managed-Allow cleanup, Linux-specific notifier IPC/regression suite; GUI popup trong C1–C5 không tương đương bảo đảm delivery khi socket lỗi.
- Full release hardening, security/permission audits, B01–B05 còn mở và những finding owner-deferred F01/F12/F13 trong review 07–09/11.

**Kết luận:** `MINIMAL CORE DEMO READY` **cho đúng baseline và môi trường đã thử** theo báo cáo owner, không phải `FULL PRODUCTION RELEASE READY`. Giữ các rủi ro/deferred items được công khai; không đòi chạy hết suite mở rộng nếu owner chủ động bỏ scope.

## 5. Change control sau sign-off

Sau mốc PASS, M4/M5 có thể cải thiện giao diện theo [UI change guardrails](../admin-web/POST_CORE_E2E_UI_CHANGE_GUARDRAILS.md). UI-only presentation changes vẫn cần lint/unit/browser regression và smoke qua HTTPS; **nếu đụng đường Allow/Revoke, mapping ID/hash, auth, CSRF hoặc API contract thì phải re-run ít nhất C1–C5 trên artifact mới trước khi chuyển mốc PASS sang baseline đó**. Không biến “đã PASS ngày 09/11” thành bằng chứng cho code mới chưa kiểm thử.

## 6. Handover / hồ sơ

- Runbook canonical: [Minimal Core E2E](../USBShield_Ubuntu24_Minimal_Core_E2E_vinhkhang_f446c04_20261107.md).
- HTTPS guide: [Windows Nginx + Ubuntu A/B](../USBShield_HTTPS_ACL_Setup_usbprotection.lab_vinhkhang_4d2216c_20261109.md).
- Windows verifier 09/11: [Implementation status](../IMPLEMENTATION_STATUS_20261109.md).
- Chưa cập nhật history các snapshot Week 1–4 thành PASS. Báo cáo mới này ghi **sự kiện tiếp theo theo thời gian**, không viết lại acceptance cũ.
