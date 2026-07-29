# Database Design — Digital Banking System

## Mục tiêu

Thiết kế cơ sở dữ liệu cho hệ thống ngân hàng số theo kiến trúc Microservices.

Mỗi service quản lý dữ liệu của riêng mình, tránh phụ thuộc trực tiếp vào database của service khác.

Database được thiết kế theo chuẩn chuẩn hóa dữ liệu (3NF), đảm bảo tính toàn vẹn, bảo mật và khả năng mở rộng.

| Bảng             | Mục đích                   |
| ---------------- | -------------------------- |
| users            | Người dùng                 |
| roles            | Vai trò                    |
| permissions      | Quyền                      |
| user_roles       | Liên kết User - Role       |
| role_permissions | Liên kết Role - Permission |
| refresh_tokens   | Refresh Token              |
| accounts         | Tài khoản ngân hàng        |
| transactions     | Giao dịch                  |
| notifications    | Thông báo                  |
| audit_logs       | Nhật ký hệ thống           |
