# Làm việc với USBShield

## Nhánh và phạm vi

- Chỉ làm trên `vinhkhang` tại `chivan2925/USBShield` (ID `1384491487`).
- Xác minh `git status`, branch, URL fetch/push, fetch và remote refs trước khi sửa.
- URL canonical là `https://github.com/chivan2925/USBShield.git`. Dừng nếu còn remote cũ.
- Owner cập nhật remote nếu cần. Dừng khi diverged, dirty tree hoặc scope xung đột.
- Chỉ làm slice Owner cấp quyền. Xác định baseline SHA, thay đổi được phép và stop gate.
- Giữ nguyên user edits. Cấm force-push, reset phá hủy, push main, tạo/merge PR.
- Đọc [BLUEPRINT.md](BLUEPRINT.md), mục 14 và skill liên quan trước khi làm.
- Source/test mô tả baseline. Blueprint mô tả mục tiêu và ghi rõ ADR còn mở.
- A1 và các slice sau cần quyền riêng. Dừng nếu chi tiết phải duyệt còn thiếu.

## Dependency direction và bảo mật

- Giữ ranh giới Central, Agent Ubuntu, USBGuard và notifier.
- Domain/use case giữ invariant. HTTP, DB, USBGuard, IPC và UI là adapter.
- Giữ behavior cũ khi refactor và kiểm tra qua entry point thực.
- Policy owner là Server. Agent reconcile snapshot và báo version/health.
- ACK của rules và actual block trên thiết bị đang cắm cần chứng cứ riêng.
- Workspace, endpoint và quyền lấy từ identity phía Server khi triển khai isolation.
- Không mở admin API local. Notifier IPC chỉ dùng để gửi thông báo.
- Giữ TLS/CA/SAN, least privilege, durable state và outbox theo contract.
- Không in/commit secret, token, private key, dữ liệu DB hay nội dung file người dùng.
- Không reset dữ liệu, cài thêm runtime dependency hoặc đổi schema ngoài scope đã duyệt.

## Tài liệu và sơ đồ

- `docs/` dành cho Markdown sản phẩm/kỹ thuật đang được duy trì.
- Ngoại lệ fixture phải có consumer thực tế và được giữ nguyên đến slice xử lý.
- Đồng bộ guide và flowchart cùng thay đổi code. Gắn nhãn baseline/target rõ ràng.
- Duy trì sơ đồ USB insert/enforcement/event/notifier và Admin policy/pull/ACK.
- Bổ sung flow file monitor khi triển khai. Theo quy trình mục 14.2 khi vẽ hoặc sửa.
- Evidence, review, findings và kết quả test ở Codex chat. Giữ lịch sử qua Git.
- Cấu hình và hướng dẫn Codex ở ngoài `docs/`.

## Kiểm chứng và review

- Chạy test sát scope. Ghi command, môi trường, SHA/candidate và kết quả thật.
- Phân biệt PASS, FAIL, SKIP, NOT EXECUTED, UNSUPPORTED và CONFIGURED_NOT_VERIFIED.
- Native USB, TLS/LAN, Windows installer cần môi trường thật để nghiệm thu.
- Sau khi implement và test, đóng băng candidate (bao gồm file mới) trước commit.
- Tự gọi reviewer read-only trong cùng task theo mục 14.4. Owner đã cho phép.
- Chọn chuyên môn theo delta. Ưu tiên custom đã load, hoặc reviewer native built-in.
- Gửi baseline, fingerprint và exact diff, acceptance, test evidence và giới hạn quyền.
- Xác nhận reviewer đọc đúng candidate. Không chỉnh candidate khi reviewer đang đọc.
- Chờ kết quả, kiểm chứng và xử lý findings trong scope, test lại và re-review delta cuối.
- Reviewer báo severity và path/line trong chat. Reviewer không sửa/commit/push.
- Nếu delegation bất khả thi, ghi SELF_REVIEW_ONLY hoặc SUBAGENT_UNAVAILABLE và dừng trước publication khi gate cần independent review.

## Xuất bản

- Theo [hướng dẫn harness](.codex/HARNESS.md) để chạy gate trên staged candidate.
- Kiểm tra scope, index, branch, canonical remote, quyền push và kết quả review/test.
- Chỉ commit và normal push `origin vinhkhang` khi được cấp quyền và gate đạt.
- Xác minh remote SHA sau push. Owner tự tạo và merge PR.
- Dừng ở cuối slice. Báo next step với actor, scope, gate và authorization.

## Skills

- [Intake và resume](.agents/skills/usbshield-intake/SKILL.md)
- [Kiến trúc và bảo mật](.agents/skills/usbshield-architecture/SKILL.md)
- [Verification và evidence](.agents/skills/usbshield-verify/SKILL.md)
- [Review độc lập](.agents/skills/usbshield-review/SKILL.md)
- [Đồng bộ docs và sơ đồ](.agents/skills/usbshield-docs/SKILL.md)
- [Commit và push](.agents/skills/usbshield-publish/SKILL.md)
