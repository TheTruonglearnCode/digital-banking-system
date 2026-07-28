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
# Requirement — Digital Banking System

---

## 4. Account Service

### 4.1 Chức năng

| Method | Endpoint | Mô tả | Actor |
|---|---|---|---|
| POST | `/accounts` | Mở tài khoản ngân hàng mới | CUSTOMER |
| GET | `/accounts/me` | Xem danh sách tài khoản của mình | CUSTOMER |
| GET | `/accounts/{accountNumber}` | Xem chi tiết 1 tài khoản | CUSTOMER (chủ tài khoản), ADMIN |
| GET | `/accounts/{accountNumber}/balance` | Xem số dư | CUSTOMER (chủ tài khoản) |
| PUT | `/accounts/{accountNumber}/lock` | Khóa tài khoản | ADMIN |
| PUT | `/accounts/{accountNumber}/unlock` | Mở khóa tài khoản | ADMIN |
| PUT | `/accounts/{accountNumber}/close` | Đóng tài khoản | CUSTOMER, ADMIN |

### 4.2 Business rule
- Số tài khoản (`accountNumber`) sinh tự động, duy nhất, không cho client tự đặt
- Trạng thái tài khoản: `PENDING → ACTIVE → LOCKED / CLOSED`
    - Chỉ được chuyển `LOCKED → ACTIVE`, không được chuyển ngược từ `CLOSED`
- Tài khoản `LOCKED` hoặc `CLOSED`: từ chối mọi giao dịch chuyển/nhận tiền
- Đóng tài khoản chỉ được phép khi số dư = 0
- **Không có API nào cho phép client set trực tiếp số dư** — số dư chỉ được thay đổi thông qua Transaction Service (internal call), Account Service chỉ đọc và validate

### 4.3 Non-functional requirement
- Trường `balance` dùng kiểu `DECIMAL(19,4)` trong database, dùng `BigDecimal` trong code — tuyệt đối không dùng `double`/`float`
- Cập nhật số dư phải dùng optimistic locking (`@Version`) để tránh mất dữ liệu khi có nhiều giao dịch đồng thời trên cùng 1 tài khoản

---


## 5. Transaction Service

### 5.1 Chức năng

| Method | Endpoint | Mô tả | Actor |
|---|---|---|---|
| POST | `/transactions/transfer/internal` | Chuyển tiền giữa 2 tài khoản trong cùng hệ thống | CUSTOMER |
| POST | `/transactions/transfer/external` | Chuyển tiền ra ngân hàng khác | CUSTOMER |
| GET | `/transactions/me` | Xem lịch sử giao dịch của mình | CUSTOMER |
| GET | `/transactions/{id}` | Xem chi tiết 1 giao dịch | CUSTOMER (liên quan), ADMIN |
| POST | `/transactions/{id}/otp/verify` | Xác thực OTP cho giao dịch lớn | CUSTOMER |

### 5.2 Business rule
- Không được chuyển tiền vượt quá số dư khả dụng (`available balance = balance - số tiền đang giữ tạm cho giao dịch pending khác`)
- Giao dịch trên **10.000.000 VNĐ** bắt buộc xác thực OTP gửi qua email/SMS trước khi thực hiện
- Trạng thái giao dịch: `PENDING → PROCESSING → SUCCESS / FAILED`
- Nếu giao dịch thất bại giữa chừng (ví dụ lỗi khi cộng tiền cho tài khoản nhận), **toàn bộ phải rollback**, tài khoản gửi không bị trừ tiền
- Mỗi request chuyển tiền phải kèm `Idempotency-Key` — nếu client gửi lại cùng key trong vòng 24h, hệ thống trả về kết quả giao dịch cũ, **không xử lý lại lần 2**
- Mọi giao dịch (thành công hay thất bại) đều phải ghi vào `audit_logs`, không được xóa lịch sử giao dịch dưới bất kỳ hình thức nào

### 5.3 Non-functional requirement
- Giao dịch phải đảm bảo tính **ACID** — dùng `@Transactional` đúng propagation, kiểm thử kỹ trường hợp rollback
- Xử lý race condition khi 2 giao dịch cùng lúc thao tác trên 1 tài khoản (pessimistic lock hoặc optimistic lock + retry)
- Thời gian xử lý 1 giao dịch nội bộ (internal transfer) phải dưới 2 giây trong điều kiện bình thường

---


## 6. Notification Service

### 6.1 Chức năng

| Sự kiện lắng nghe (Kafka topic) | Hành động |
|---|---|
| `transfer.success` | Gửi email + push notification xác nhận giao dịch thành công |
| `transfer.failed` | Gửi email thông báo giao dịch thất bại, kèm lý do |
| `otp.requested` | Gửi mã OTP qua email/SMS |
| `account.locked` | Gửi email cảnh báo tài khoản bị khóa |

### 6.2 Business rule
- OTP có hiệu lực **5 phút**, tối đa 3 lần nhập sai trước khi bị vô hiệu hóa và phải yêu cầu OTP mới
- Nếu gửi email thất bại (SMTP lỗi), message phải được đẩy vào Dead Letter Topic để xử lý lại sau, không được để mất thông báo giao dịch thành công/thất bại

### 6.3 Non-functional requirement
- Notification Service không được làm chậm luồng giao dịch chính — giao tiếp với Transaction Service **hoàn toàn bất đồng bộ qua Kafka**, không gọi đồng bộ (REST) trong luồng transfer

---


## 7. Non-functional requirement chung toàn hệ thống

| Hạng mục | Yêu cầu |
|---|---|
| Bảo mật | Toàn bộ API (trừ register/login) yêu cầu JWT hợp lệ; dữ liệu nhạy cảm mã hóa at-rest |
| Hiệu năng | API đọc (GET) phản hồi < 300ms trong điều kiện bình thường (có cache) |
| Khả năng mở rộng | Mỗi service scale độc lập, stateless (session lưu ở Redis, không lưu ở memory service) |
| Độ tin cậy | Không được mất giao dịch/thông báo dù service phụ (Notification) bị down tạm thời |
| Khả năng audit | Mọi thao tác on tiền, khóa/mở tài khoản, đổi quyền đều ghi log không thể sửa/xóa |
| Khả năng quan sát | Mỗi service expose `/actuator/health` và metrics cho Prometheus |

---

