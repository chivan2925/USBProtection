# USBShield Admin Web

React/Vite là UI triển khai ở baseline. Xem [Admin guide](../docs/ADMIN_GUIDE.md) để vận hành, [IT setup](../docs/IT_SETUP_GUIDE.md) để đóng gói và [Developer guide](../docs/DEVELOPER_GUIDE.md) để truy vết mã nguồn và test.

Trong thư mục này, chạy `npm ci`, `npm run dev` hoặc `npm run build`. Vite phục vụ dev tại `http://127.0.0.1:5173/`. [Proxy của Vite](vite.config.ts) dùng HTTPS và kiểm tra chứng chỉ. Cấu hình `API_PROXY_TARGET` hoặc `USBSHIELD_API_TARGET` theo môi trường được cấp quyền. Ví dụ dùng CA và SAN của `usbprotection.lab` nếu làm việc trong lab hiện tại.

Admin Web đóng gói cùng Spring Boot qua Maven profile `with-frontend` sau khi build `dist/`. Đường dẫn `/demo/` dùng dữ liệu mock chỉ đọc. [Blueprint](../BLUEPRINT.md) ghi mục tiêu Desktop và điều kiện thay thế frontend.
