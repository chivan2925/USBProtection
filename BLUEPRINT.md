# USBShield — Project Blueprint v2.5

**Ngày:** 26/11/2026  
**Trạng thái:** Target architecture / Owner cập nhật phạm vi: chỉ Central Admin Desktop, vận hành không cần Internet, Revoke trực tiếp; chi tiết kỹ thuật vẫn cần ADR/review; chưa triển khai, chưa nghiệm thu. Bản v2.5 thay thế v2.4, cập nhật tên repository đã đổi và xác minh baseline; giữ nguyên kiến trúc, quy chuẩn tài liệu/sơ đồ và review tự động bằng subagent.  
**Baseline:** `chivan2925/USBShield` (repository ID `1384491487`), `main` và `vinhkhang` cùng HEAD `da15f1e9c078ec62d3394d421091ee84ebaf8213`, xác minh qua GitHub ngày 26/11/2026; không thay đổi so với baseline v2.4.  
**Blueprint tiền nhiệm:** `USBShield_Blueprint_Allow_Revoke_Reinsert_20261103.md`, blob `bcc2e07752f3374abdeeadd5673f55a43477ed0f` (2305 dòng). Có thể truy xuất tại baseline Git; không cần giữ một bản sao tài liệu lịch sử trong cây làm việc mới.

Tên sản phẩm đích: **USBShield**. Tên đề tài học thuật: Ngăn chặn người dùng cắm USB Flash Disk vào máy tính Ubuntu. Chủ repository đã đổi tên repository hiện hữu từ `chivan2925/USBProtection` thành [`chivan2925/USBShield`](https://github.com/chivan2925/USBShield); không tạo repo mới. H0 và các slice được Owner authorize làm việc trên repository mới tên này, nhánh `vinhkhang`. Canonical clone URL: `https://github.com/chivan2925/USBShield.git`. Codex phải xác minh remote thực tế, không tự rename hoặc thay remote khi chưa được phép; nếu connection/remote cũ còn dùng tên USBProtection thì yêu cầu Owner cập nhật trước khi tiếp tục. Tên đề tài trên báo cáo chính thức do nhóm đối chiếu với giảng viên.

## 1. Phạm vi, nguồn sự thật và trạng thái

Blueprint này thay kế hoạch Web/Week 1–4 cũ bằng đặc tả mục tiêu mới. Không phải báo cáo tính năng đã có. Source tại baseline, test và lịch sử Git là nguồn chứng cứ hành vi cũ; sau khi triển khai, contract/API/schema và test hiện hành phải được cập nhật cùng code. Các tài liệu và kết quả lịch sử có thể truy xuất từ Git, nhưng không được dùng làm bằng chứng nghiệm thu phiên bản mới.

| Nội dung | Baseline cũ | Target v2 |
|---|---|---|
| Quản trị trung tâm | React/Vite Web trong Spring Boot JAR | Native JavaFX Desktop trên Windows, có bộ cài |
| Quản trị tại endpoint | Chưa có | Không có giao diện quản trị trên Ubuntu; Agent và notifier chạy local |
| Enforcement | USBGuard, default block storage chưa được phép, allow non-storage | Giữ và mở rộng với Revoke thiết bị đang cắm |
| Đồng bộ | Server authoritative, heartbeat/pull/reconcile/ACK | Giữ Server authoritative, heartbeat/pull/reconcile/ACK; bổ sung kiểm chứng Revoke đang cắm |
| Isolation | Admin cùng ROLE_ADMIN, dữ liệu chung | Dữ liệu riêng theo phạm vi Admin, account-owned workspace (đã chốt) |
| File activity | Event USB và active session | Inventory USB, file metadata, read/write/delete/rename, copy-direction inference |
| Notifier | Helper user-session trong client-agent, IPC local | Giữ hành vi native; tách boundary/module helper nếu có lợi, không nhập quyền quản trị vào notifier |
| Persistence | PostgreSQL/Flyway + durable file outbox | PostgreSQL trung tâm + local policy state và durable outbox; SQLite không bắt buộc |
| Packaging | JAR + .deb | Windows installer + Ubuntu .deb; manifest, checksums và clean-install tests |
| Dev workflow | Repo hiện tại, docs lịch sử | Codex harness có kiểm chứng, branch vinhkhang; PR/merge main do Owner thực hiện hoặc ủy quyền |

Các yêu cầu Owner đã nêu: bỏ giao diện Web; sử dụng đầy đủ khi mất Internet nhưng LAN còn; bỏ Local Admin; duy trì policy enforcement và lưu log khi mất LAN; theo dõi file copy hai chiều; dữ liệu Admin riêng; refactor SOLID/Design Patterns/Clean Architecture/Clean Code; harness đầy đủ nhưng AGENTS.md ngắn; dọn README và docs cũ, chỉ giữ tài liệu sản phẩm/kỹ thuật Markdown cần thiết, có sơ đồ luồng và không lưu báo cáo review vào docs/; thống nhất USBShield; bỏ frontend hiện tại; cho phép reset dữ liệu test khi cần. JavaFX là stack định hướng. Bỏ multi-writer policy/Revoke Wins vì chỉ Server có quyền ghi policy; giữ versioning hiện tại. Revoke trực tiếp, không safe-eject và chấp nhận gián đoạn I/O/mất dữ liệu. Các quyết định ở mục 3; các chi tiết thiết kế chưa chốt phải được review trước implementation.

Baseline đã có USBGuard, systemd service non-root, logind active-user attribution, HASH whitelist theo endpoint, policy ACK, health, spool/retry/quarantine, Nginx HTTPS/IP ACL, PostgreSQL/Flyway, offline maintenance password reset. Kết quả Minimal Core Ubuntu 09/11 là owner-reported; extended native acceptance trước đây chưa đầy đủ. Không kế thừa PASS cho v2.

## 2. Mục tiêu sản phẩm và non-goals

- Ngăn USB Mass Storage (interface class 08) chưa được cấp phép; không chặn bàn phím/chuột USB bình thường. Composite có storage được xử lý theo storage. Normal user không thể tắt service hoặc sửa policy; root/sudo/OS administrator có thể can thiệp, không hứa chống root.
- Nhận diện USB bằng HASH đúng contract; VID/PID/serial/name là metadata, không tự cấp quyền theo VID/PID. Chỉ cấp quyền thiết bị đã được quan sát ở endpoint đích (đã được Agent báo cáo và lưu tại Server).
- Central Admin quản lý endpoint thuộc workspace của mình qua Desktop App trên Windows. Không có Web UI hoặc giao diện quản trị trên Ubuntu.
- Sau provisioning, không cần Internet để vận hành nếu LAN còn. Khi mất kết nối tới Server, endpoint tiếp tục enforcement theo policy đã áp dụng và lưu log bền vững trong giới hạn quota; Central Desktop xem dữ liệu đã nhận và lưu desired policy, nhưng không thể điều khiển máy bị ngắt kết nối cho đến khi kết nối lại. Không cấp quyền sửa policy local cho người dùng Ubuntu.
- Khi thiết bị bị block, người dùng nhận native desktop notification best-effort; log vẫn độc lập với GUI. Ghi active local Ubuntu session hoặc UNKNOWN, không suy diễn con người nào dùng tay cắm.
- USB được allow và mounted: xem inventory metadata và hoạt động file liên quan USB ↔ Ubuntu, gồm tên/đường dẫn hai đầu khi quan sát được, hướng và độ tin cậy. Không thu nội dung file mặc định, không giám sát toàn bộ đời sống người dùng không liên quan USB.
- Đồ án/demo một central server, không microservices/Kafka/Redis/Kubernetes/CRDT phức tạp nếu không có yêu cầu chứng minh. Không cam kết enterprise DLP, mọi phương thức copy, real-time remote enforcement khi disconnected, chống root hay chống USB firmware giả mạo.

## 3. Quyết định Owner và các chi tiết cần review trước implementation

| ID | Vấn đề | Quyết định / chi tiết thiết kế |
|---|---|---|
| D01 | Dữ liệu riêng của mỗi Admin | **ĐÃ CHỐT:** Một account sở hữu một workspace; endpoint thuộc duy nhất một workspace, không share/multi-owner; không super-admin xem mọi dữ liệu. |
| D02 | Provisioning và authentication | **CẬP NHẬT v2.2:** Bỏ Local Admin và Polkit/PAM dành cho quản trị local. IT cài Agent và enroll lần đầu qua LAN bằng token scope workspace, bind workspace + endpoint; ưu tiên token một lần. Central Admin xác thực bằng tài khoản USBShield; Agent xác thực riêng bằng endpoint-bound token. |
| D03 | Offline và policy authority | **CẬP NHẬT v2.2:** Hoạt động đầy đủ khi mất Internet nhưng LAN còn. Server là single source of truth; giữ Policy Versioning + heartbeat/pull/reconcile/ACK; bỏ local policy edits, two-way operation merge và Revoke Wins. Khi mất LAN, giữ policy đã áp dụng, lưu log và retry; không quản trị tại Ubuntu. |
| D04 | Copy monitoring | **ĐÃ CHỐT:** MVP GNOME Files và CLI `cp`, `mv` giữa USB mount và thư mục Ubuntu thuộc phạm vi test, một hoặc nhiều file; log observed vs inferred, không kết luận copy nếu không đủ evidence. Spike bắt buộc trước chốt collector. |
| D05 | Hiệu lực Allow/Revoke | **CẬP NHẬT v2.2:** Allow cho lần cắm tiếp theo; Revoke chặn USB đang cắm trực tiếp ngay khi Agent nhận và thực thi policy, không chờ rút/cắm, không safe-eject/unmount/flush trước khi block. Owner chấp nhận gián đoạn I/O, mất dữ liệu hoặc hỏng filesystem. Vẫn phải xác minh actual block, ghi lỗi và retry nếu thất bại. |
| D06 | Central installer | **ĐÃ CHỐT:** Một bộ cài hoặc launcher tích hợp Windows app + local backend + PostgreSQL + Nginx, quản lý thiết lập/khởi động theo quy trình rõ ràng. Không coi jpackage tự cài DB/TLS/services; toolchain cụ thể cần review. |
| D07 | Công nghệ | JDK 21, JavaFX 21-compatible, FXML/CSS, Maven; giữ Spring Boot/PostgreSQL; tận dụng local persistence/outbox hiện tại, không bắt buộc SQLite. Ưu tiên UDisks2 + Linux Audit spike, cân nhắc fanotify native helper nếu cần. |
| D08 | Dữ liệu file | Chỉ metadata, không capture nội dung. Retention/quota, phạm vi thư mục được giám sát, quyền xem/export log được cấu hình và review; log thiếu/overflow phải hiển thị. |

Owner cập nhật ngày 26/11/2026: D02/D03/D05 ở bản này thay thế quyết định tương ứng của v2.1. Việc bỏ Revoke Wins và bỏ SQLite bắt buộc là đơn giản hóa kiến trúc suy ra từ việc không còn Local Admin; không bỏ versioning hoặc durable outbox. D01/D04/D06 giữ nguyên; D07 là stack định hướng, D08 là đề xuất metadata-only và giới hạn privacy. Chi tiết collector, quota/retention, wire/schema, xác thực/enrollment, actual revoke và installer còn cần review. H0 không triển khai các quyết định này; A1 lập ADR và Owner duyệt thiết kế cụ thể trước các slice liên quan.

## 4. Kiến trúc runtime đích

```text
WINDOWS CENTRAL HOST (per installation)
  JavaFX Admin Desktop (unprivileged)
      | local HTTPS REST (loopback route, session/CSRF)
      v
  Nginx TLS / source-IP ACL --> Spring Boot Admin Server (loopback HTTP)
                                    | PostgreSQL loopback
                                    v
                             workspace-scoped state + audit
      ^ HTTPS/443 /api/agent/** (endpoint-bound token; CA/SAN validation)
      | LAN/VPN; no Internet dependency
      v
UBUNTU 24.04 LTS amd64 ENDPOINT (belongs to one workspace)
  Java Client Agent (systemd; no admin GUI)
      | local applied policy state + durable event outbox
      | USBGuard gateway --> USBGuard
      | mount/file monitor adapter
      | notification publisher
      v
  local-only notification IPC --> user-session notifier --> native notification
```

Nginx tiếp tục thực thi IP/route ACL; Client Agent không được vào Central Admin APIs. Spring Security vẫn tách Admin và Agent. Admin Desktop đặt trên cùng Windows host; giữ HTTPS local và trust CA phù hợp để cookie Secure/CSRF vẫn đúng. DNS/hosts nội bộ và certificate CA/SAN phải hoạt động không cần Internet; không vô hiệu hóa xác thực TLS. Hostname lab có thể giữ `usbprotection.lab` trong giai đoạn chuyển tiếp; tên đích đề xuất `usbshield.lab` chỉ đổi đồng bộ DNS/hosts, SAN, docs, scripts, allowlists và kiểm tra TLS. Không mở 18080 hoặc 5432 ra LAN.

Agent không nhận lệnh quản trị từ user Ubuntu; không mở local admin socket/API. Notification channel chỉ truyền thông báo tới đúng session; không dùng làm admin command channel. Monitor native nếu có privilege riêng tối thiểu, interface hẹp, không nhận lệnh shell tùy ý. Agent không phụ thuộc GUI để bảo vệ USB.

| Trạng thái | Central Desktop | Ubuntu Agent / USBGuard | Sync |
|---|---|---|---|
| Không Internet, LAN còn | Đầy đủ dữ liệu và điều khiển qua LAN | Enforcement, monitor, notifier | Hoạt động |
| Mất LAN tới endpoint | Xem dữ liệu đã nhận, lưu desired change, hiển thị offline/stale | Giữ policy đã áp dụng; tiếp tục monitor, lưu log local | Outbox pending; policy mới chờ mạng |
| Server/DB dừng | UI báo unavailable, không giả commit | Enforcement local tiếp tục | Retry |
| Ubuntu reboot offline sau provisioning | Dữ liệu central cũ | Khôi phục policy bền vững, bảo vệ USB; không có local admin UI | Chờ mạng |
| Kết nối lại | Nhận log, desired/applied/revoke status | Pull latest canonical policy, reconcile, ACK và replay log | Idempotent retry |

Mất Internet khác mất LAN, và mất LAN khác server process chết. Offline install hoàn toàn hoặc first-time enrollment không LAN không nằm trong MVP. Không có yêu cầu điều khiển endpoint từ xa khi mất LAN.

## 5. Dữ liệu và quyền riêng của Admin

D01 đã chốt: workspace là security boundary; trong MVP mỗi Central Admin sở hữu duy nhất một workspace. Không dùng username mutable làm khóa. `admin_users` quản lý identity/hash/credential version; accountId và workspaceId là ID ổn định. Agent enrollment phải gắn workspace do Server xác thực; credential của endpoint bind workspace/endpoint. Không dùng enrollment token global cho mọi account.

- Endpoint thuộc một workspace, một endpoint vật lý chỉ có một effective policy. Không cho Admin A và B là hai tenant độc lập cùng quản lý một Ubuntu host đồng thời. Transfer/re-enroll sang workspace khác là thao tác IT được review riêng, không tái dùng identity/token hoặc state của tenant trước.
- Dashboard, endpoint details, USB inventory, device catalog, whitelist, USB events, file activity, admin audit, pagination/counts và exports phải scope theo workspace đã xác thực ở backend. Không chỉ filter UI; không lấy workspace từ request làm authority. Query bằng ID của tenant khác phải không lộ dữ liệu (404/403 theo contract). Device hash giống nhau giữa tenant không tạo leak, key catalog theo workspace.
- Admin actor, Agent identity và active Ubuntu session là các trường khác nhau; active Linux user không có quyền quản trị USBShield. Agent chỉ được báo telemetry/events và ACK cho endpoint của mình; không có quyền thay desired policy.
- App logout xóa session/credential cache không cần thiết; state/token của Agent được bảo vệ bằng quyền OS/service; không plaintext password/token trong logs; Central Admin passwords dùng BCrypt hoặc encoder đã review. Tạo account/enrollment/reset cần flow rõ, không mở public registration tùy ý.
- Cài đặt tài khoản trong Central Desktop chỉ phục vụ đổi username và mật khẩu của Admin đang đăng nhập. Xác nhận bằng mật khẩu hiện tại, giữ nguyên accountId/workspaceId và vô hiệu hóa các phiên cũ sau khi đổi.

Schema logic (không phải migration đã thực hiện): `workspaces`, `admin_users`, `endpoints`, `usb_devices`, `endpoint_policy_entries`, `usb_events`, `file_activity_events`, `admin_audit`, `enrollment_credentials` (tùy design). Scope và uniqueness/FK theo workspace, endpoint, hash. PostgreSQL Flyway migration mới hoặc clean baseline sau khi Owner cho phép xóa test DB; không tự bật ddl-auto=create/drop. Tận dụng local applied state/outbox hiện tại; nếu file activity cần SQLite hoặc storage khác thì đánh giá durability, bounded storage, integrity, permissions và migration trong A1/F0; không thêm DB chỉ để phục vụ chức năng Local Admin đã bỏ. Secrets không commit.

## 6. Policy versioning, immediate Revoke và recovery

### 6.1 Mô hình single-writer

Server là nguồn duy nhất được phép thay đổi desired policy. Giữ `policyVersion` tăng đơn điệu theo endpoint và `appliedPolicyVersion` (revision Agent đã reconcile/xác minh và ACK). Policy key = `(workspaceId, endpointId, deviceHash)`. Whitelist HASH-only; ABSENT mặc định BLOCK đối với Mass Storage. Agent chỉ áp dụng snapshot do Server xác thực, không tự tạo revision hoặc thay đổi whitelist theo local user. Không cần local operation journal, localSequence, multi-writer merge, tombstone phục vụ conflict hoặc Revoke Wins.

Central mutation kiểm tra quyền workspace và endpoint, HASH đã quan sát, transaction/locking phù hợp; duplicate operation idempotent. Nếu nhiều phiên Central Admin cùng thao tác, xử lý bằng transaction và optimistic precondition nếu UI stale; không dùng wall-clock timestamp để quyết định policy. Audit ghi actor, action, HASH, endpoint, revision, UTC timestamp và kết quả.

### 6.2 Hiệu lực Allow và Revoke (D05)

- Allow: cập nhật HASH whitelist bền vững, reconcile rule, thông báo cần rút và cắm lại. Không tự authorize thiết bị đang cắm.
- Revoke: persist deny bằng cách loại HASH khỏi managed allow, reconcile rule và chặn trực tiếp mọi instance Mass Storage đang kết nối của HASH bị revoke trên đúng endpoint (ví dụ USBGuard `block-device`, exact target identification). Không đợi người dùng rút/cắm; không thực hiện safe-eject, unmount/flush bắt buộc, đợi ứng dụng ghi xong hoặc rollback dữ liệu. Owner chấp nhận gián đoạn I/O, mất dữ liệu và hỏng filesystem.
- Kiểm chứng native cơ chế block thực tế, bao gồm nhiều partition, thiết bị idle/busy, USB tháo trong lúc xử lý và failure/reboot. Không chặn nhầm HASH khác hoặc non-storage; không tự chạy các lệnh ghi/xóa/format filesystem để thực hiện Revoke. Dùng USB/test files có thể mất dữ liệu cho destructive E2E, không dùng dữ liệu thật. Không phát triển workflow bảo toàn dữ liệu/safe-eject.
- Revoke từ Central Admin vẫn phụ thuộc thời điểm Agent nhận policy (heartbeat/pull), không hứa tức thì từ thời điểm click; khi mất LAN chỉ pending đến khi reconnect. Ghi lỗi, retry có kiểm soát và không báo thành công nếu actual block chưa được xác minh. Policy rule ACK và connected-device enforcement status là các tín hiệu riêng; ACK không tự chứng minh USB đang cắm đã bị chặn. Log actor, HASH, revision, block result và error/status; giám sát file phải phản ánh I/O interrupted/unknown khi không đủ evidence.

### 6.3 Luồng đồng bộ

1. Central Admin Allow/Revoke đúng endpoint → Server validate/authorize, cập nhật whitelist và tăng desired `policyVersion` khi thay đổi thực sự; audit transactional.
2. Agent heartbeat báo health và revision đã biết; khi có version mới hoặc cần periodic verify, GET policy của đúng endpoint qua HTTPS. Kiểm tra snapshot toàn bộ: version hợp lệ, không regression, HASH/duplicate/conflict validation; không apply partial hoặc fallback sang HTTP.
3. Agent reconcile USBGuard managed rules; chặn trực tiếp các thiết bị đang cắm đã bị Revoke hoặc không còn được phép theo canonical policy, không tự Allow thiết bị đang cắm. Native spike phải phân biệt thiết bị thực sự bị revoke với USB storage đã block/không thuộc target; bảo đảm non-storage không bị ảnh hưởng. Nếu các update trung gian bị bỏ qua khi endpoint offline, chỉ áp dụng snapshot canonical mới nhất.
4. Persist applied state và kết quả enforcement bền vững; USBGuard mutation ngoài DB/file state nên cần recovery khi crash giữa apply và persist. ACK chỉ revision rule đã reconcile/xác minh; report riêng pending/failed/confirmed cho actual block, không báo hoàn tất Revoke khi chưa xác minh. Nếu ACK/network lỗi, retry idempotent sau verify. Heartbeat không thay ACK. Periodic reconciliation phát hiện drift kể cả version không đổi.
5. Khi server/LAN unavailable, giữ policy đã áp dụng, USBGuard hoạt động độc lập, spool logs trong quota; không nhận local policy mutation. Reconnect kéo policy mới, apply và ACK, replay logs idempotent. UI phân biệt desired/applied, offline/stale, USBGuard health, Revoke enforcement status và Allow cần replug.

Test version monotonicity, stale/invalid payload, duplicate/retry, concurrent central requests, independent keys/endpoints, reboot/crash trước/sau apply, lost ACK, actual daemon drift, cross-workspace request, pending Revoke khi mất LAN, direct block đang ghi. Không dựa timestamp để ordering và không thêm thuật toán merge policy hai chiều.

## 7. USB file inventory và activity monitoring

### 7.1 Phân biệt requirement

- Inventory: liệt kê metadata file/thư mục trong volume USB được phép, không đọc nội dung mặc định. Inventory snapshot/paged scan có bounds, xử lý mount/unmount, permission denied, symlink, path rename và nhiều partition. Không mount USB bị block để scan; không bypass USBGuard.
- Activity: observed READ/OPEN, WRITE/MODIFY, CREATE, DELETE, RENAME/MOVE, CLOSE_WRITE khi collector hỗ trợ; record actual operation và success/unknown theo evidence. Copy là inferred/verified workflow, không suy từ một READ/WRITE đơn lẻ.
- Direction enum rõ: `HOST_TO_USB` = copy từ Ubuntu vào USB; `USB_TO_HOST` = copy từ USB ra Ubuntu; `USB_TO_USB` (nếu sau này hỗ trợ); `UNKNOWN`/`NOT_APPLICABLE`. Không dùng COPY IN/OUT mơ hồ. Lưu source/destination path khi quan sát được, không tự chế path còn thiếu.

### 7.2 Spike bắt buộc (trước chọn collector)

Trên Ubuntu 24.04 thật/VM, USB đã allow, thử FAT32/exFAT và filesystem được target chọn; UDisks2 D-Bus hoặc udev + mountinfo để map block device, partition, mount id, filesystem và USB hash/endpoint. Đánh giá Linux Audit (`auditd`/audit rules, audit records theo syscall/path/process/user) với rule scoped, tránh log mọi filesystem bừa bãi. Đánh giá `fanotify` nếu Audit không đủ attribution/correlation; native C helper tối thiểu hoặc binding đã review, chỉ privilege cần thiết. `inotify`/Java WatchService có thể hỗ trợ inventory invalidation, không đủ để kết luận ai copy hoặc hướng copy. Không chọn eBPF mặc định, chỉ đánh giá nếu thực sự cần.

Thử GNOME Files (copy/paste, drag-drop), `cp`, `mv` cross-filesystem, rename cùng volume, copy thư mục, nhiều copy đồng thời, đọc chỉ để xem, chỉnh sửa in-place, copy bị hủy, file trùng tên, buffered/fast-copy syscalls, USB tháo giữa thao tác. Đánh giá độ bao phủ, event loss, performance, false positives/false negatives và attribution. Nếu dùng fanotify, lưu ý giới hạn mmap và queue overflow; filesystem/mount support cần test, không giả định mọi filesystem có file handle support. Audit open/read/write metadata không tự cung cấp quan hệ source→destination hoặc chứng minh bytes copy xong. Không nói hoàn thành copy chỉ vì close/write nếu chưa đủ bằng chứng.

Nếu không đủ bằng chứng để xác định hai đường dẫn/hướng với GNOME Files và CLI, ghi rõ gap; lựa chọn bổ sung controlled copy workflow có instrumentation và giới hạn phạm vi phải được Owner duyệt, không thay requirement bằng mock. Không thực hiện build collector đặc quyền trên cloud rồi gọi đó là native PASS.

### 7.3 Pipeline

Mount observer → inventory service → collector adapter → normalized observed events → correlation theo PID/process start identity, actor, mount, inode/path, temporal window, read/write pattern và completion evidence → activity classification → durable local outbox → HTTPS ingestion idempotent → central logs scoped.

Mỗi activity có `eventId`, endpoint/workspace, USB hash/volume/mount identity, Linux UID/AUID hoặc UNKNOWN, process identity, timestamp, operation, direction, sourcePath, destinationPath, optional bytes/size (observed only), status, confidence/evidence type (`OBSERVED`, `INFERRED`, `UNKNOWN`), correlation ID, error/gap flags. Giảm dữ liệu nhạy cảm: không nội dung file, không full disk indexing, không đưa paths ngoài scope nếu không cần. Không hash toàn bộ nội dung mặc định; khi dùng kiểm chứng test thì chỉ file fixture. Data path/Unicode/path traversal không được dùng để chạy shell hoặc truy cập tùy ý; không follow symlink ra ngoài volume khi inventory. Quota, retention, overflow indicator, permission loss, unmount cleanup và graceful degradation. Monitor fail không tắt USBGuard; UI báo degraded, không giả coverage. UI có filter endpoint/USB/user/direction/time/status; phân biệt copy suy luận và observed activity.

## 8. Desktop UI và notification

Central JavaFX: Login, Dashboard scoped, Endpoints/details/health, Devices, Whitelist, USB Events, USB File Inventory/Activity, Policy Sync/Enforcement/Audit; account provisioning/logout và maintenance workflow theo quyền. Admin có thể lưu desired policy cho endpoint offline, không hiển thị Applied giả. Allow cần replug; Revoke status thể hiện pending/failed/confirmed. Không nhúng WebView/React để giả native UI. FXML/CSS, background Task/Service/Executor cho network/IO, update UI trên FX thread, cancellation/timeout/pagination và error states.

Ubuntu không có Local Admin GUI, local admin login, policy editor hoặc endpoint administration IPC. Người dùng Ubuntu chỉ nhận native notification; enforcement, monitoring, outbox và sync chạy nền dù không có desktop session.

Notifier: giữ native Ubuntu `notify-send`/D-Bus user session, cooldown/dedup và peer-bound IPC; nên tách `client-notifier/` thành helper build artifact sau khi xem dependency. Nếu tách Maven module không mang lại lợi ích, giữ package boundary rõ và entrypoint riêng trong client-agent. Agent system service, notifier user session và file-monitor privilege là các trust boundaries; không gộp quyền. Helper không chạy root, không giả định DISPLAY/UID cố định. Launcher/autostart nằm packaging.

## 9. Tech stack và module boundaries

| Component | Technology (target) |
|---|---|
| Central Desktop (Windows) | JavaFX compatible JDK 21 (ưu tiên JavaFX 21 line), FXML, CSS, Maven, Scene Builder optional |
| Central service | Java 21, Spring Boot, Spring Security, Spring Data JPA, PostgreSQL, Flyway |
| Client agent | Java 21, Spring Boot non-web hoặc lightweight equivalent nếu refactor chứng minh lợi ích, USBGuard gateway, systemd, existing local state/outbox |
| Local IPC | Existing notification-only IPC; privileged monitor interface nếu cần, không có local admin API |
| Linux monitoring | UDisks2/udev/mountinfo; auditd candidate, fanotify fallback; optional minimal native helper |
| Notification | Existing user-session helper, libnotify/notify-send + D-Bus |
| Transport | HTTPS REST, Nginx IP ACL, endpoint-bound agent authentication, admin session/CSRF |
| Packaging | jpackage/jlink where appropriate, Windows native MSI/EXE (target toolchain), Ubuntu .deb; platform-specific builds |
| Testing | JUnit 5, Mockito where useful, Spring integration, PostgreSQL isolated tests + local state/outbox tests, ArchUnit if useful; native Linux/Windows acceptance |

Proposed root layout (Codex validates actual build graph first):

```text
USBShield/
  BLUEPRINT.md
  AGENTS.md
  README.md
  .agents/skills/                # repo-scoped reusable skills (verify current runtime)
  .codex/                        # supported project config/agents/rules/hooks only
  admin-server/                  # central domain/application/adapters
  client-agent/                  # endpoint service, local state/sync, USBGuard
  admin-desktop/                 # Central JavaFX UI (Windows)
  client-notifier/               # optional extracted user-session helper
  usb-file-monitor/              # optional isolated Linux collector helper
  shared-contracts/              # optional stable wire DTO/value objects, not shared domain internals
  packaging/                     # Windows installer, DEB, systemd, nginx, desktop launcher
  scripts/                       # deterministic build/validation
  docs/                          # living Markdown product/developer documentation only
```

No `frontend/` in final target. Remove it atomically with its Maven/Node/static assets, browser tests, scripts/CI references and update packaging; preserve regression coverage as API/desktop tests rather than simply deleting tests. Do not remove it in docs-only H0 if that breaks current builds. Git history keeps baseline. Root Maven aggregator optional; no mandatory JPMS if dependencies unsuitable. `jpackage` packages app/runtime, not PostgreSQL/Nginx orchestration; test dependencies/permissions, Windows service start/stop, DB setup, local TLS certificate, credentials, Ubuntu installer and uninstall/upgrade. Offline operation after install; distribution offline dependency bundle is separate requirement. Windows and Ubuntu installers built/tested on appropriate native OS; cloud can test only accessible parts. No Docker requirement for end users.

## 10. Clean Architecture, SOLID, patterns and refactoring

Refactor incrementally by bounded context/use case, preserve behavior with characterization tests before changing. Architecture is dependency direction, not a folder naming exercise. Domain model pure Java (no HTTP/UI/SQL/framework dependencies where practical), use cases orchestrate domain/ports, adapters implement persistence/transport/OS, composition root wires dependencies. Central policy authority and endpoint enforcement separate; do not put whole system in a generic shared service. Presentation maps DTO/view models; validation in boundaries plus domain invariants, transactions at application/infrastructure boundary.

Use Adapter for USBGuard, file collector, network/local persistence; Strategy for collector variants if real alternative exists; Repository for persistence ports; State Machine for sync/retry lifecycle; Observer/event publisher for independent notification/event consumers; dependency injection for composition. Avoid pattern quota, interfaces for every trivial class, inheritance abuse, generic CRUD abstractions, god classes, circular dependencies, hidden global state. Explicit errors, naming, immutable values, small coherent methods, no duplicate policy rules, no catch-and-ignore, no secrets in logs. Thread safety for UI/sync/collector, transactional invariants, test seams. Define architecture test/ADR when valuable. Behavior-changing refactors explicitly identified, not bundled silently into formatting.

## 11. Protocol and security contracts

Current baseline APIs include `/api/auth/{csrf,login,me,logout}`, `/api/admin/**`, `/api/agent/{enroll,heartbeat,events,policy,policy/ack}`. Target paths/DTOs are NOT frozen yet: retain heartbeat/pull/ACK semantics, extend scoped enrollment, file activity ingestion/inventory snapshot query, actual Revoke enforcement status and admin audit. No local policy write/sync operations or delegation APIs. Keep existing stable semantics where compatible; explicitly bump incompatible protocol with installer rollout/new clients or reject old incompatible agents safely. No remote admin route from Ubuntu Agent. Auth scopes server-derived, cross-tenant resource checks for all reads/writes including list counts and error responses, no IDOR. Session lifecycle/logout/reset; keep bootstrap/reset maintenance without unsafe direct DB manipulation. TLS hostname validation and CA trust mandatory for live agent; no `curl -k`/HTTP fallback; no external DNS/Internet dependency for installed LAN operation. Existing remote IP ACL is defense in depth, not tenant isolation. Re-enrollment/transfer requires explicit procedures. No normal-user ability to modify state, stop daemon, manipulate monitor or forge privileged IPC; root threat out of scope. Data deletion allowed for test scope only, never automatic on startup.

## 12. Roadmap and delivery gates (not executed)

| Slice | Scope | Gate |
|---|---|---|
| H0 | Documentation reset + Codex harness; canonical BLUEPRINT, concise README, minimal source-grounded Markdown docs and harness state outside docs/; inspect baseline dependency refs | Scope diff, branch guard, harness configuration/load tests or honest NOT EXECUTED, links, no runtime code or data changes |
| A1 | Read-only architecture/security baseline audit; ADRs D01–D08, characterize tests, contract/schema/installer plan | Owner reviews ADRs, remaining design details and migration plan (v2.2 decisions already confirmed/superseded as in section 3) |
| P1 | USBShield naming/refactor foundation and tenant isolation, schema + account/enroll/auth | Cross-tenant tests incl identical USB hash and adversarial IDs; regression USBGuard contract |
| P2 | Central JavaFX, native launcher, API/auth parity; remove React frontend atomically | API + desktop behavior, build refs/CI clean; no Web UI; local no-Internet run |
| P3 | Single-writer policy sync hardening + direct Revoke; preserve notifier and durable outbox | Native direct block idle/busy, versioning/retry/crash matrix, tenant binding, native A/B LAN disconnect/reconnect and reboot |
| F0 | Linux mount/file observation spike | Recorded actual coverage/limitations, collector choice approved before production code |
| F1 | Inventory and file event/copy inference pipeline + screens | USB native copy matrix with observed/unknown distinctions, no data leakage, quota/overflow |
| R1 | Packaging, docs, regression/security review, clean install | Windows native installer + Ubuntu .deb + offline/physical USB acceptance evidence |

Roadmap is dependency order, not calendar estimates. Split implementation into reviewable commits; F0 may run earlier after Owner authorization to de-risk copy monitoring. No phase automatically authorized by completion of previous phase. Within each authorized slice, Codex itself dispatches suitable read-only reviewer subagents in the same task/session, waits for results, fixes actionable findings, retests and re-reviews before publication (section 14.4). No separate Owner-started review chat by default. Each slice reports scope, baseline and resulting SHA, changed files, commands/outcomes, blockers, SKIP/NOT EXECUTED, acceptance and next action in the Codex conversation; do not create per-slice review/audit/evidence/handoff reports in docs/ or move such archives elsewhere in the repository. Keep only one minimal current resumability state in the harness outside docs/ if needed. Owner may delegate acceptance and PR/merge for authorized work. Record any specific native gate deferred by Owner as NOT EXECUTED and include it in consolidated acceptance.

## 13. Acceptance test matrix (target)

| ID | Case | Expected |
|---|---|---|
| E01 | Clean Ubuntu install, unknown storage, keyboard/mouse | BLOCK storage, non-storage usable, active-user event, notification best-effort |
| E02 | Allow A, same USB on B, revoke A while attached | A allowed after reconcile/replug, B blocked, A revoked without replug once received; actual device access denied |
| E03 | Admin A vs Admin B, same device HASH | All lists/details/counts/ops/logs isolated; cross-account API attempts denied |
| E04 | No Internet, LAN live | Central Desktop, HTTPS, Agent and sync operate without external dependency |
| E05 | LAN cut, central desired update | Central pending, local enforcement continues, no fake applied |
| E06 | LAN cut, Ubuntu reboot after provisioning | Last applied policy persists, normal user cannot mutate policy, event queue survives |
| E07 | Central REVOKE while Agent disconnected | Pending until reconnect; latest desired policy applied, actual block without replug verified, correct ACK/status |
| E08 | Different USB keys/endpoints and concurrent central requests | Correct isolated changes, transactional versioning, no lost update |
| E09 | Duplicate mutation/retry, stale/invalid snapshot, clock skew | Idempotent/reject safely, no regression or timestamp-based policy ordering |
| E10 | Crash after rule apply before persist, server commit before response, failed ACK | Resume/reconcile, no lost committed policy or false ACK, actual rules checked |
| E11 | Agent requests Admin API or another workspace; unauthorized IPC | Denied by appropriate layers; no cross-tenant data |
| E12 | TLS invalid CA/hostname, direct backend access | Fail closed; no plaintext fallback; firewall/ACL correct |
| E13 | File inventory USB with nested dirs/symlinks | Scoped metadata only; no escape or unauthorized scan |
| E14 | GNOME Files + cp/mv both directions | Source/dest when observable, direction and confidence correct; no false copy for read/edit |
| E15 | Multiple copy, cancelled/partial, removal, overflow | distinguish ambiguity/failed/unknown; gap visible, enforcement unchanged |
| E16 | File events while offline, retry | durable bounded local spool, idempotent ingest, correct workspace |
| E17 | Windows installer/restart; Ubuntu .deb install/upgrade/uninstall | reproducible native artifacts, correct privilege and no silent data loss |
| E18 | No GUI/user session, notifier failure | protection/audit independent; no unauthorized commands via notifier |
| E19 | Revoke while USB idle/busy, active copy, multiple partitions, failure/reboot | actual block verified without replug, direct block without safe-eject, incomplete I/O/data-loss risk accepted and disclosed, failure never reported as success, retry and deny on reconnect |
| E20 | Normal Ubuntu user attempts policy change or forges notifier IPC | denied even without LAN; no local admin interface; no access to other endpoints/workspaces |

Use unit, integration, architecture and end-to-end tests; tests must fail when invariant broken. Native gate must use actual Ubuntu USBGuard and USB storage, Windows packaging/runtime as applicable. CI/cloud mock only supplementary. Report target versions, commands, exact SHA, clean/dirty, artifact hash, environment and actual results in the Codex conversation (or existing CI output). Living test documentation explains how to reproduce the tests and expected outcomes, not per-run reports. No claim of full PASS from Windows-only or simulated tests. Historical evidence accessible from Git, not copied as current test results.

## 14. Documentation reset, living documentation, diagrams and Codex harness

### 14.1 Documentation policy (Owner, v2.3)

H0: preserve canonical blueprint as `BLUEPRINT.md` in root (copy this file exactly or record Owner-approved decisions); retire old root blueprint after recording baseline pointer. Delete tracked old `docs/` and `README.md` content; recreate only necessary new documentation. Before deletion inspect build/CI/test dependencies (docs fixtures/scripts can be functional inputs). If deleting a referenced fixture breaks behavior, report exact dependency in the conversation and defer that path or handle in an approved code slice; don't silently alter tests/production in H0. Git history preserves old docs; do not rewrite history.

`docs/` is for maintained product and technical documentation in Markdown (`.md`) only, aimed at IT deployment/configuration staff, Central Admin users and developers. No review reports, audit reports, findings archives, implementation logs, test-run reports, phase/slice closeouts, handoff packages, dated status snapshots, Codex workflow logs or generated evidence dumps. Do not relocate such archives to another tracked folder to evade the rule. Review, testing and verification still take place; communicate actionable findings, decisions needing approval, actual test results, blockers and next steps in the Codex conversation, not review files. Keep existing CI output where appropriate. Security/admin audit logs are runtime product data, unaffected by this documentation policy.

Suggested living document set (names and grouping flexible; avoid redundant files):

- `ARCHITECTURE.md`: actual components, responsibilities, trust/dependency boundaries, technology choices and rationale, deployment/network topology, data flow, policy and offline behavior. Clearly label target design vs implemented behavior. Durable architecture decisions may be incorporated here or in focused ADRs only when genuinely needed; no audit/review history or duplicate roadmap.
- `IT_SETUP_GUIDE.md`: prerequisites, verified build/package/install and upgrade/uninstall as relevant, configuration (Windows central service, PostgreSQL, Nginx, LAN/DNS/TLS/CA, firewall/ACL), account/enrollment provisioning, Ubuntu Agent service, verification and operational troubleshooting. IT staff must be able to configure a clean installation without reading source code.
- `ADMIN_GUIDE.md`: login, account scope, endpoint/USB/whitelist management, Allow/replug vs direct Revoke, logs, inventory and file activity interpretation, health/ACK/pending/error indicators, offline behavior and operational limits. Write for Admin users, not developers. Include real UI screenshots only when available/necessary; do not fabricate UI or instructions for planned features.
- `DEVELOPER_GUIDE.md`: a practical guide to reading, understanding and extending the code, not just a generic coding-convention list. Cover repo/module/package structure, key files/classes and why they belong there, dependency direction, entry points, use-case execution paths, where/how to implement a new feature, examples grounded in actual source, SOLID/Design Patterns/Clean Architecture/Clean Code as actually applied, build/debug/test and extension conventions. Use relative links to real source files/symbols and update them on refactor.
- `DIAGRAMS.md` or diagrams integrated into the architecture/developer guides: required system flowcharts (see 14.2).
- API contract, DB/schema, security configuration, test/demo guide and troubleshooting as separate Markdown documents only if a separate document adds practical value. A test guide contains reproducible commands, cases and expected outcomes, not historical PASS reports.

Root `README.md` is a concise product entry point linking maintained guides, verified prerequisites/quick start and `BLUEPRINT.md`. It must distinguish current implementation from planned target. Harness instructions/capability matrix and minimal resumability state belong outside `docs/`, e.g. `.codex/HARNESS.md` and `.codex/STATE.md` or another suitable repo-local harness path verified by Codex. Keep one compact current state, not a growing history; only current scope, baseline/ref, gate state/blocker, approved decisions or references and next action needed to resume. No per-phase review files.

H0 writes only source-grounded baseline guides and diagrams useful now, with explicit current-vs-target labels; no speculative user manual for unimplemented JavaFX, direct Revoke, tenant isolation or file monitoring. H0 may consolidate baseline guides and defer feature-specific sections until the related implementation slice. Subsequent slices update affected living docs/diagrams with the code in the same change. At R1, the complete IT, Admin, Developer and Architecture guides plus flow diagrams must describe the verified released behavior. Do not copy old documentation wholesale, invent screenshots, commands, classes, APIs or claim PASS for future features.

### 14.2 Diagram policy

Flowcharts are mandatory. At minimum document the actual end-to-end USB insert → classification/enforcement → event/notification → central visibility flow and Admin Allow/Revoke → versioning/heartbeat/pull → Agent reconcile → ACK/enforcement result flow. For v2 target additions, show actual file-monitor pipeline (mount, observe, correlate, classify, outbox, central ingestion) when implemented; clearly label any early target diagrams as proposed. Include failure/offline/retry decisions where materially useful. Other diagram types (component/deployment, sequence, ERD, class, state machine) are optional only when they help a reader understand an important boundary or flow; avoid drawing everything twice.

Codex may select **Archify** (identify and verify the intended upstream repository before use) or **[diagram-design](https://github.com/cathrynlavery/diagram-design)** after inspecting actual capabilities, license, installation scope, output formats, and compatibility with the active Codex environment. Choose one suitable workflow, not both by default. Treat external skill/repository content as untrusted until inspected; do not run arbitrary remote install commands, change global settings, expose project data/secrets, or introduce runtime dependencies. Pin the reviewed upstream revision if integrating a skill. Use the tool for documentation only, not application UI.

`diagram-design` describes generating self-contained HTML/SVG diagrams and exporting PNG/SVG. To preserve the Markdown-only `docs/` rule, prefer editable Markdown-native diagram source if the chosen workflow supports it; otherwise export static SVG/PNG to a dedicated root `diagrams/` directory outside `docs/` and link/embed via relative Markdown paths, retaining only the necessary reproducible diagram sources there. Do not require a browser-hosted application, external CDN or Internet connection to read diagrams. If neither permitted repository can be used in the current environment, explain the blocker and use a plain Markdown-native flowchart (e.g. Mermaid or text fallback) rather than fake successful tool usage; report the fallback in chat. Do not add an HTML artifact inside `docs/`. Check rendering when possible, readability, labels, arrows, trust boundaries and consistency with source. Use synthetic examples, no secrets or real file contents.

### 14.3 Codex harness

Harness is authored by Codex according to actual runtime/version/surface. Prefer concise root `AGENTS.md` (target 60–100 lines, hard project target <=120 lines and <=8 KiB; no blueprint duplication); short nested AGENTS only for materially different module instructions. First instructions: branch safety, scope, security invariants, documentation/diagram policy, explicit test evidence, read relevant skills/blueprint and STOP conditions. Progressive disclosure through `.agents/skills/<name>/SKILL.md` and references/scripts. Use current official docs for discovery paths, config, custom agents, rules and hooks; do not invent schema or imply cloud automatically executes all local features. Native Codex features if supported; fallback explicit scripts/CI gates when unavailable. No AGENTS.md can override sandbox/platform permission.

Required harness capabilities (combine where appropriate): intake/plan and resumability; implementation strategy/refactor/security boundary; test/evidence native vs cloud; independent read-only review; documentation/diagram sync; release/commit/push. Suggested agents: architecture reviewer, security/tenant+sync reviewer, Linux USB reviewer, QA/release reviewer, implementation agent if useful. Scope each, no arbitrary write/commit authority for reviewers. Review findings and test evidence go to chat, not persistent review files. Rules/hooks should guard branch and destructive commands, secret paths, scope/test evidence and changed-file checks; deterministic scripts test allow/deny cases and safe failure; no auto force-push, no bypass, no claim hooks enforced until runtime verification. Use versioned portable checks with shell/PowerShell compatibility or clear prerequisites. Avoid overbuilt workflow engine.

H0 outputs: `AGENTS.md`, skills, supported agents/rules/hooks/config, harness validation scripts/tests, concise harness usage/capability information outside `docs/`, optional single minimal harness state outside `docs/`, README, canonical BLUEPRINT, necessary baseline Markdown documentation and flow diagrams, and a conversational cleanup/dependency/gate report with baseline SHA. Harness and docs are not implementation of new features. H0 gate checks Markdown-only `docs/`, no review/phase/evidence archives, documentation source grounding, flowchart presence/renderability or honest fallback, relative links, harness and branch constraints. Diagram assets, if needed, are documentation assets outside `docs/`, not runtime dependencies.

### 14.4 Same-session automatic subagent review (Owner, v2.4)

Owner explicitly authorizes Codex to spawn/delegate to reviewer subagents automatically for H0 and every authorized implementation slice, without a separate Owner prompt or independent review chat. Encode this trigger in concise `AGENTS.md` and the review skill; use actual native delegation tools when available, not role-play by the implementing agent. A separate agent context is useful independence of review, not a guarantee of correctness or an external human audit.

- After implementation and initial relevant verification, freeze the candidate diff (baseline SHA, staged/unstaged changes, relevant new files, acceptance criteria, test commands/results). Main agent delegates to one or more suitable read-only reviewers in the same task; use architecture/security/Linux/QA specialties only as appropriate to the changed scope. Provide access to the actual candidate and required source context, not only the implementer's summary. Verify the reviewer sees the intended revision/diff; never review an old HEAD while uncommitted changes are missing. No reviewer edits, publication, destructive commands or permission escalation.
- Main agent waits for all required results, checks findings against source, resolves actionable findings within scope, reruns relevant tests and requests re-review of the corrected delta/final candidate. No concurrent modification while a reviewer evaluates the candidate; material changes after acceptance invalidate the relevant review. Track and report unresolved findings with path/line, severity, reason and disposition. Limit repeated correction loops sensibly; stop/report unresolved blockers or decisions requiring Owner rather than auto-expanding scope.
- H0 must attempt an actual read-only subagent invocation after creating the harness. Check actual runtime discovery, permissions and visibility of candidate files. Prefer newly configured custom reviewers when available; if not loaded yet, use native built-in subagent with explicit reviewer scope/read-only constraints if available. Do not assume newly written config or AGENTS.md has been reloaded in the current session. Distinguish custom-agent activation from native delegation. A reload/new implementation session may be needed if supported by the active client; no routine separate review chat required.
- If native delegation is unavailable or permission/candidate visibility cannot be verified, perform a separate read-only self-review pass as fallback and mark `SELF_REVIEW_ONLY` / `SUBAGENT_UNAVAILABLE` or `CONFIGURED_NOT_VERIFIED` accurately, with blocker in chat. Never call this independent agent review. Follow existing acceptance gates: do not publish a blocked slice as accepted; if independent agent review is required and cannot be achieved, stop for Owner decision rather than silently downgrade. Native custom agents/permissions depend on the actual Codex surface (local vs cloud); no global config changes or sandbox bypass.
- Main agent reports review mode, reviewer roles actually invoked, reviewed revision/diff, findings and re-review outcome in the same Codex chat; no persistent review files or report archive. Commit and normal push require Owner publication authorization, independent review and all applicable gates to pass. Owner may defer specific native gates to consolidated acceptance. Record them as NOT EXECUTED. Create or merge PR only under Owner delegation. Do not start the next slice automatically.

## 15. Git authority and destructive changes

Owner authorizes Codex to commit and normal push to `vinhkhang` only. Verify current branch, fetch remote state, expected upstream, clean tree; do not touch user changes, no force push/reset, no direct main commit/push, no branch protection or repo rename. Use an authorized GitHub connector for delegated PRs from vinhkhang to main. Shell PR mutation remains blocked by the repo guard. If the connector is unavailable or lacks permission, stop that PR operation and report. If upstream diverges, uncommitted changes exist, or credentials/network prevent push, stop/report; do not bypass platform approval or claim success. The repository has already been renamed by its owner; use the canonical repository `chivan2925/USBShield` (GitHub repository ID `1384491487`), clone URL `https://github.com/chivan2925/USBShield.git`. Verify repository identity, actual remote URL, permissions and branch HEAD before work and publication. If the environment still points to the former name `chivan2925/USBProtection`, ask Owner to update the remote or refresh the Codex repository connection; do not silently modify the remote or rely on redirects. Preserve Git history, branches and PRs; do not create a new repository. Update active repository references in the authorized scope, preserve historical facts. Standardize user-facing product USBShield and identifiers where sensible; existing `usbshield`, `USBSHIELD_*` already align, don't rename unnecessarily. Repository rename does not change the lab hostname; change hostname only with full TLS migration.

Owner permits deletion/reset of existing TEST data/database when needed in approved implementation slice; identify exact DB/path and environment first, no blanket deletion or automatic on startup. Never delete unrelated DBs/users, repo history, machine directories, private keys or real data. H0 is no database reset, no file copy monitoring/GUI/security schema implementation, no removing React runtime dependencies; deleting legacy docs specifically authorized, not arbitrary source deletion. Preserve tests unless replaced with equal/stronger coverage within approved change.

## 16. References and verification boundaries

- Source baseline: https://github.com/chivan2925/USBShield/tree/da15f1e9c078ec62d3394d421091ee84ebaf8213
- Old blueprint: https://github.com/chivan2925/USBShield/blob/da15f1e9c078ec62d3394d421091ee84ebaf8213/USBShield_Blueprint_Allow_Revoke_Reinsert_20261103.md
- OpenJFX: https://openjfx.io/openjfx-docs/ ; packaging: https://openjfx.io/openjfx-docs/modular
- Ubuntu fanotify limitations: https://manpages.ubuntu.com/manpages/noble/man7/fanotify.7.html
- Codex subagents: https://developers.openai.com/codex/subagents ; GitHub repository rename: https://docs.github.com/en/repositories/creating-and-managing-repositories/renaming-a-repository
- OpenAI Codex workflow guidance: https://developers.openai.com/cookbook/examples/codex/iterating-development-workflows-with-codex ; repo skills: https://developers.openai.com/blog/skills-agents-sdk ; plugin capabilities may vary by surface: https://developers.openai.com/plugins/concepts/plugins

This blueprint is a replacement target specification drafted from the old blueprint's baseline, selected source details and Owner's new requirements. No runtime test/build/commit/push was performed for this document. Owner v2.2 scope replaces earlier local administration decisions; v2.3 changes documentation/harness conventions only; v2.4 adds same-session delegated review; v2.5 records the completed repository rename and verified unchanged baseline, without changing product architecture; exact schema, wire DTOs, privilege mechanism, direct Revoke implementation/verification, native monitoring and installer orchestration remain design/implementation gates, not accomplished facts.
