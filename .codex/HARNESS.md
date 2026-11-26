# USBShield Codex harness

Đọc [AGENTS.md](../AGENTS.md) và [BLUEPRINT.md](../BLUEPRINT.md) trước khi chạy. Script dưới đây cần Git và Python 3.11+ (standard library). Trên Ubuntu dùng `python3` thay `python`.

| Capability | Cấu hình và cách xác minh |
| --- | --- |
| Instructions và skills | Root `AGENTS.md`, repo `.agents/skills/`. Kiểm tra prompt/skill catalog mới của client. Khi catalog không hiện, đọc trực tiếp theo link trong AGENTS. |
| Reviewers | `.codex/agents/*.toml` theo project agents schema của Codex. Phiên nào chưa thấy role thì dùng native built-in read-only brief. Nếu không delegate được, áp dụng fallback và stop gate ở mục 14.4. |
| Rules | `.codex/rules/usbshield.rules` ở trusted project layer. Kiểm tra bằng `codex execpolicy check --rules .codex/rules/usbshield.rules -- git reset --hard HEAD`. Lệnh phía sau `--` là input kiểm tra. |
| Hooks | `.codex/hooks.json` đăng ký `PreToolUse` cho Bash. Tool cần trusted repo và hook trust. Trên Desktop mở Settings → Hooks hoặc Review hooks trong composer. Trên CLI dùng /hooks. Xem hook project và guard.py trước khi chọn Trust cho từng định nghĩa đã kiểm tra. Dùng synthetic tests và chạy gate thủ công khi session chưa nạp hook hoặc surface không hỗ trợ command hook cục bộ. |

Rules so khớp prefix Git; hook nhận command thật của Bash và kiểm tra mẫu lệnh phổ biến. Các helper không xử lý mọi wrapper, alias, shell expansion hoặc công cụ ngoài Bash. Quyền OS/sandbox, branch protection, scope và kiểm tra candidate vẫn áp dụng. Hook không tự thực thi lệnh được kiểm tra, không sửa file và không tự xuất bản. Heuristic secret check chọn các file nhạy cảm và một số dạng token/private key. Reviewer vẫn cần kiểm tra secrets trong diff.

## Kiểm tra trước commit

```text
python -B .codex/test_guard.py
python -B .codex/check.py --baseline <sha> --h0
python -B .codex/check.py --baseline <sha> --scope docs/ --scope README.md
```

Dùng dòng `--h0` riêng cho candidate reset được Owner cấp. Với slice khác, lặp `--scope` cho từng file hoặc prefix thư mục của scope được cấp. Stage đúng delta trên `vinhkhang`, bao gồm file mới. Candidate gate kiểm baseline, branch, origin, code/docs scope, relative link paths, secret heuristics và whitespace. H0 giữ nguyên blueprint theo hash; ngoại lệ trailing spaces của attachment chỉ áp dụng cho file đó. Gate in SHA-256 của staged binary diff để giao cho reviewer. Trước commit, chạy lại với `--reviewed-fingerprint <sha256>`.

Mỗi lần xuất bản cần review thực tế, test evidence phù hợp, xác nhận quyền và ref upstream mới. Tối đa ba vòng review subagent mỗi slice, tính cả lần đầu. Xem [skill review](../.agents/skills/usbshield-review/SKILL.md) để tính vòng và xử lý gate còn mở. Hook và script chỉ kiểm được điều kiện cơ học qua dữ kiện mà chúng nhận. Ghi evidence và reviewer verdict trong chat. Dùng cùng gate khi hook chưa load hoặc ở cloud và ghi rõ khả năng tương ứng.
