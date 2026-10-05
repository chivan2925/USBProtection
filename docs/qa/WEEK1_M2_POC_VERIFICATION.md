# Week 1 — Member 2 PoC Verification

## 1. Phạm vi xác minh

Ngày review: 2026-10-05 (Asia/Bangkok).

Phương pháp: đọc và đối chiếu evidence trong repository trên Windows. Đây là **evidence review**, không chạy lại USBGuard, reboot, USB thật hoặc session resolver trên Ubuntu. PASS dưới đây chỉ có nghĩa evidence phù hợp với expected result trong phạm vi đã ghi; không chứng minh tính xác thực của log hoặc kết quả E2E.

Không sửa nguồn evidence trong `docs/usbguard/`, `packaging/usbguard/` hoặc sample JSON. Review thực hiện bởi Codex theo yêu cầu người dùng; chưa phải xác nhận thực thi độc lập của Member 5.

## 2. Verification Checklist

| ID | Verification Item | Expected Result | Evidence review | Evidence | Note / giới hạn |
|---|---|---|---|---|---|
| M2-01 | Mass Storage class detection | Detect class 08 | PASS | [Blocked device](../usbguard/evidence/05-blocked-devices.txt) | Interface 08:06:50; review log, không chạy parser lại |
| M2-02 | Unknown flash | Flash chưa whitelist BLOCK | PASS | [Blocked device](../usbguard/evidence/05-blocked-devices.txt), [base policy](../../packaging/usbguard/rules-v0.1.conf), [PoC](../usbguard/USBGuard_POC.md) | DataTraveler 0951:1665 có target block; trạng thái chưa whitelist dựa vào mô tả/setup PoC |
| M2-03 | Physical mouse | Mouse class 03 ALLOW | PASS | [Mouse log](../usbguard/evidence/12-hid-mouse-test.txt) | Optical Mouse 093a:2510, interface 03:01:02, target allow; không suy ra keyboard đã test |
| M2-04 | Reboot persistence | Permanent allow còn sau reboot | PASS | [Post-reboot rules](../usbguard/evidence/10-rules-after-reboot.txt), [PoC](../usbguard/USBGuard_POC.md) | Rule DataTraveler còn và ghi nhận ALLOW; thao tác reboot do M2 báo cáo, không review được boot identity từ log này |
| M2-05 | USBGuard watch | Có event stream cắm/rút/policy | PASS | [Watch log](../usbguard/evidence/13-usbguard-watch-full.txt) | Có Remove, Insert, PolicyChanged, PolicyApplied với target block |
| M2-06 | Active user | Resolve đúng active local user | PASS | [Session list](../usbguard/evidence/16-loginctl-list-sessions.txt), [detail](../usbguard/evidence/17-active-session-detail.txt), [resolver](../usbguard/evidence/18-active-user-resolver-poc.txt) | Khớp usbdev, UID 1000, session 2, seat0; Active=yes, Remote=no, wayland |
| M2-07 | Second user | Resolve user thứ hai | PASS | [student01](../usbguard/evidence/19-active-user-student01.txt), [PoC](../usbguard/USBGuard_POC.md) | Output student01, UID 1001, session 19; chưa có loginctl detail riêng để đối chiếu độc lập |
| M2-08 | Ambiguous session | UNKNOWN, không đoán user | PASS | [Ambiguity](../usbguard/evidence/resolver-ambiguity.txt) | SIMULATED LOGIC TEST: hai candidate, reason=ambiguous-active-local-sessions; không phải hai session thật |
| M2-09 | Sample Agent Event | Endpoint + user + USB + decision + timestamp | PASS | [Sample JSON](../agent-contract/sample-usb-event.json), [input](../usbguard/evidence/20-combined-event-input.txt), [contract](../agent-contract/README.md), [mouse](../usbguard/evidence/12-hid-mouse-test.txt) | JSON parse được; user/session/host/time và USB khớp evidence; endpoint placeholder, sample tạo thủ công; không chứng minh POST/deserialize Server hoặc contract freeze |

## 3. Các kiểm tra đã thực hiện

- Đọc log target/interface của flash và mouse, đối chiếu base policy và báo cáo PoC.
- Đọc post-reboot rule và watch event sequence.
- Đối chiếu loginctl list/detail với resolver output cho user usbdev.
- Review output second user và ambiguity; giữ riêng phạm vi simulated logic.
- Parse sample JSON, kiểm tra trường bắt buộc, giá trị user/device/decision/time và đối chiếu combined input.
- Kiểm tra các đường dẫn Markdown trong tài liệu tồn tại.

## 4. Quy tắc kết quả

- PASS: evidence đã review hỗ trợ expected result trong phạm vi được nêu.
- FAIL: evidence đã review trái với expected result.
- NOT EXECUTED: chưa thực hiện evidence review.
- Kết quả chạy lại Ubuntu và QA VERIFIED là trạng thái riêng; không chuyển các kết quả này thành runtime PASS.

## 5. Tổng kết và việc còn lại

Evidence review: **9/9 PASS, 0/9 FAIL, 0/9 NOT EXECUTED**.

Runtime rerun trong lần review này: **0/9**. Không kết luận hệ thống E2E PASS.

Hạn chế: evidence reboot và second user chủ yếu là output/ghi nhận của M2; cần môi trường Ubuntu để xác minh độc lập. Keyboard và nhiều flash không thuộc chín mục trên, vẫn chưa có kết quả đủ để đánh dấu PASS.

Hành động tiếp theo:

1. Member 5 xác nhận evidence review và ghi tên, ngày, commit khi review hoặc chạy lại.
2. Nếu cần mức VERIFIED runtime, chạy lại trên Ubuntu, bổ sung boot identity trước/sau reboot và loginctl detail cho second user.
3. Xác minh event pipeline thật và Server POST riêng trong integration Week 2.

[Test Inventory](WEEK1_TEST_INVENTORY.md) và [QA Summary](WEEK1_QA_SUMMARY.md) vẫn phân biệt kết quả M2 reported với QA runtime verification; báo cáo này chỉ bổ sung lớp evidence review.
