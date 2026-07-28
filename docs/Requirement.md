# Digital Banking System

## Version

1.0

## Author

Thế Trường

## Project Description

Digital Banking System is a backend banking application developed using Java Spring Boot Microservices architecture.

The system provides secure authentication, account management, money transfer, transaction history, notification and administration features.

---

## Scope

The project focuses on backend development only.

Frontend is not included.

---
# Services

1. Tổng quan hệ thống
2. Authentication Service
3. User Service
4. Account Service
5. Transaction Service
6. Notification Service
7. Non-functional requirement chung toàn hệ thống
8. Ngoài phạm vi (Out of scope)


## 1. Tổng quan hệ thống

Digital Banking System là hệ thống ngân hàng số cho phép khách hàng đăng ký tài khoản, quản lý tài khoản ngân hàng, thực hiện giao dịch chuyển tiền, và nhận thông báo real-time. Hệ thống xây dựng theo kiến trúc microservices gồm 5 service chính: Auth, User, Account, Transaction, Notification.

### Actor trong hệ thống
| Actor | Mô tả |
|---|---|
| `CUSTOMER` | Khách hàng, chủ tài khoản ngân hàng |
| `ADMIN` | Nhân viên vận hành, có quyền khóa/mở tài khoản, xem audit log |
| `SYSTEM` | Các tác vụ tự động (cron job tính lãi, hệ thống gửi thông báo, hệ thống ghi audit) |

---

## 2. Auth Service

### 2.1 Chức năng

| Method | Endpoint | Mô tả | Actor |
|---|---|---|---|
| POST | `/auth/register` | Đăng ký tài khoản mới | CUSTOMER |
| POST | `/auth/login` | Đăng nhập, trả về access token + refresh token | CUSTOMER, ADMIN |
| POST | `/auth/refresh-token` | Cấp lại access token mới từ refresh token | CUSTOMER, ADMIN |
| POST | `/auth/logout` | Đăng xuất, thu hồi refresh token | CUSTOMER, ADMIN |
| POST | `/auth/verify-email` | Xác thực email qua OTP/link | CUSTOMER |
| POST | `/auth/forgot-password` | Gửi email đặt lại mật khẩu | CUSTOMER |
| POST | `/auth/reset-password` | Đặt lại mật khẩu bằng token | CUSTOMER |

### 2.2 Business rule
- Email và số điện thoại phải là duy nhất trong hệ thống
- Mật khẩu tối thiểu 8 ký tự, bắt buộc có chữ hoa, chữ thường, số
- Tài khoản chưa xác thực email (`emailVerified = false`) chỉ được đăng nhập nhưng **không được** thực hiện giao dịch
- Access token có hiệu lực **15 phút**, refresh token có hiệu lực **7 ngày**
- Sau khi logout, refresh token phải bị vô hiệu hóa ngay lập tức (không dùng lại được dù chưa hết hạn)
- Đăng nhập sai **5 lần liên tiếp** trong 15 phút → khóa tạm thời tài khoản 30 phút, ghi audit log
- Link đặt lại mật khẩu có hiệu lực **15 phút**, dùng 1 lần duy nhất

### 2.3 Non-functional requirement
- Mật khẩu phải được hash bằng BCrypt (cost factor ≥ 10), không bao giờ lưu plaintext hoặc log ra plaintext
- JWT ký bằng RS256, các service khác chỉ cần public key để verify (không cần gọi lại Auth Service mỗi request)
- API login phải chống được brute-force (rate limit theo IP + theo tài khoản)

---

## 3. User Service

### 3.1 Chức năng

| Method | Endpoint | Mô tả | Actor |
|---|---|---|---|
| GET | `/users/me` | Xem thông tin cá nhân | CUSTOMER |
| PUT | `/users/me` | Cập nhật thông tin cá nhân | CUSTOMER |
| POST | `/users/me/avatar` | Upload ảnh đại diện | CUSTOMER |
| PUT | `/users/me/change-password` | Đổi mật khẩu (yêu cầu mật khẩu cũ) | CUSTOMER |
| GET | `/users/{id}` | Xem thông tin user bất kỳ | ADMIN |
| PUT | `/users/{id}/role` | Gán quyền cho user | ADMIN |
| PUT | `/users/{id}/status` | Khóa/mở khóa tài khoản người dùng | ADMIN |

### 3.2 Business rule
- Số điện thoại, CMND/CCCD không được sửa sau khi đã xác thực KYC (Know Your Customer) — chỉ ADMIN mới sửa được, và phải ghi audit log
- Avatar giới hạn 2MB, định dạng jpg/png
- Đổi mật khẩu bắt buộc phải nhập đúng mật khẩu cũ, và sau khi đổi thành công, **thu hồi toàn bộ refresh token hiện có** (buộc đăng nhập lại trên mọi thiết bị)
- Chỉ ADMIN mới được xem thông tin của user khác

### 3.3 Non-functional requirement
- Thông tin cá nhân (CMND/CCCD, số điện thoại) cần được mã hóa khi lưu trong database (encryption at rest)

