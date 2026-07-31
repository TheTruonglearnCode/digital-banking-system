# Database Design — Digital Banking System

## Mục tiêu

Thiết kế cơ sở dữ liệu cho hệ thống ngân hàng số theo kiến trúc Microservices.

Mỗi service quản lý dữ liệu của riêng mình, tránh phụ thuộc trực tiếp vào database của service khác.

Database được thiết kế theo chuẩn chuẩn hóa dữ liệu (3NF), đảm bảo tính toàn vẹn, bảo mật và khả năng mở rộng.

| Bảng             | Mục đích                   |
| ---------------- |----------------------------|
| users            | Người dùng                 |
| roles            | Vai trò                    |
| permissions      | Quyền                      |
| role_permissions | Liên kết Role - Permission |
| refresh_tokens   | Refresh Token              |
| accounts         | Tài khoản ngân hàng        |
| transactions     | Giao dịch                  |
| outbox_events    | Hộp thư giao dịch          |
| notifications    | Thông báo                  |
| audit_logs       | Nhật ký hệ thống           |


## 1. Danh sách bảng chi tiết

### 1.1 `users` (Auth Service / User Service)

| Cột                     | Kiểu           | Ràng buộc                | Ghi chú |
|-------------------------|----------------|--------------------------|---|
| `id`                    | `BIGINT`       | PK, auto increment       | |
| `email`                 | `VARCHAR(255)` | UNIQUE, NOT NULL         | |
| `phone_number`          | `VARCHAR(20)`  | UNIQUE, NOT NULL         | |
| `password_hash`         | `VARCHAR(255)` | NOT NULL                 | BCrypt hash, không lưu plaintext |
| `full_name`             | `VARCHAR(255)` | NOT NULL                 | |
| `avatar_url`            | `VARCHAR(500)` | NULLABLE                 | |
| `email_verified`        | `BOOLEAN`      | DEFAULT false            | |
| `status`                | `VARCHAR(20)`  | NOT NULL                 | `PENDING / ACTIVE / LOCKED` |
| `failed_login_attempts` | `INT`          | DEFAULT 0                | phục vụ rule khóa sau 5 lần sai |
| `locked_until`          | `TIMESTAMP`    | NULLABLE                 | |
| `created_at`            | `TIMESTAMP`    | NOT NULL                 | |
| `updated_at`            | `TIMESTAMP`    | NOT NULL                 | |
| `role_id`               | `BIGINT`       | FK -> roles.id, NOT NULL | |

### 1.2 `roles`

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `BIGINT` | PK |
| `name` | `VARCHAR(50)` | UNIQUE — `CUSTOMER`, `ADMIN` |

### 1.3 `permissions`

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `BIGINT` | PK |
| `name` | `VARCHAR(100)` | UNIQUE — ví dụ `ACCOUNT_LOCK`, `USER_VIEW_ALL` |


### 1.4 `role_permissions` (bảng trung gian)

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| `role_id` | `BIGINT` | FK → `roles.id` |
| `permission_id` | `BIGINT` | FK → `permissions.id` |
| | | PK composite |

> User được gán trực tiếp một Role duy nhất thông qua cột role_id. 
> Role tiếp tục được gán nhiều Permission.

### 1.5 `refresh_tokens`

| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `id` | `BIGINT` | PK | |
| `user_id` | `BIGINT` | FK → `users.id`, NOT NULL | |
| `token` | `VARCHAR(500)` | UNIQUE, NOT NULL | nên lưu hash của token, không lưu raw |
| `expires_at` | `TIMESTAMP` | NOT NULL | |
| `revoked` | `BOOLEAN` | DEFAULT false | |
| `device_info` | `VARCHAR(255)` | NULLABLE | User-Agent, phục vụ "quản lý thiết bị đăng nhập" |
| `created_at` | `TIMESTAMP` | NOT NULL | |

Index: `(user_id, revoked)` — truy vấn nhanh danh sách token còn hiệu lực của 1 user.

### 1.6 `accounts` (Account Service)

| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `id` | `BIGINT` | PK | |
| `account_number` | `VARCHAR(20)` | UNIQUE, NOT NULL | sinh tự động |
| `user_id` | `BIGINT` | NOT NULL | không đặt FK cứng nếu Account Service tách DB riêng khỏi User Service — chỉ lưu id tham chiếu logic |
| `balance` | `DECIMAL(19,4)` | NOT NULL, DEFAULT 0 | **không dùng double/float** |
| `currency` | `VARCHAR(3)` | DEFAULT 'VND' | |
| `status` | `VARCHAR(20)` | NOT NULL | `PENDING / ACTIVE / LOCKED / CLOSED` |
| `version` | `INT` | DEFAULT 0 | optimistic locking (`@Version`) |
| `created_at` | `TIMESTAMP` | NOT NULL | |
| `updated_at` | `TIMESTAMP` | NOT NULL | |

Index: `user_id`, `account_number` (unique).

### 1.7 `transactions` (Transaction Service)

| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `id` | `BIGINT` | PK | |
| `transaction_ref` | `VARCHAR(50)` | UNIQUE, NOT NULL | mã giao dịch hiển thị cho user |
| `idempotency_key` | `VARCHAR(100)` | UNIQUE, NOT NULL | chống double-submit |
| `from_account_number` | `VARCHAR(20)` | NOT NULL | |
| `to_account_number` | `VARCHAR(20)` | NOT NULL | |
| `amount` | `DECIMAL(19,4)` | NOT NULL | |
| `type` | `VARCHAR(20)` | NOT NULL | `INTERNAL_TRANSFER / EXTERNAL_TRANSFER` |
| `status` | `VARCHAR(20)` | NOT NULL | `PENDING / PROCESSING / SUCCESS / FAILED` |
| `failure_reason` | `VARCHAR(255)` | NULLABLE | |
| `otp_required` | `BOOLEAN` | DEFAULT false | |
| `otp_verified` | `BOOLEAN` | DEFAULT false | |
| `created_at` | `TIMESTAMP` | NOT NULL | |
| `completed_at` | `TIMESTAMP` | NULLABLE | |

Index: `(from_account_number, created_at)`, `(to_account_number, created_at)` — phục vụ query lịch sử giao dịch nhanh; `idempotency_key` unique để DB tự chặn insert trùng.

### 1.8 `outbox_events` (Transaction Service — Transactional Outbox Pattern)

| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `id` | `BIGINT` | PK | |
| `aggregate_type` | `VARCHAR(50)` | NOT NULL | ví dụ `TRANSACTION` |
| `aggregate_id` | `VARCHAR(50)` | NOT NULL | id của transaction liên quan |
| `event_type` | `VARCHAR(50)` | NOT NULL | `transfer.success`, `transfer.failed` |
| `payload` | `JSONB` | NOT NULL | dữ liệu event, publish nguyên vẹn lên Kafka |
| `published` | `BOOLEAN` | DEFAULT false | Outbox Publisher đánh dấu true sau khi publish thành công |
| `created_at` | `TIMESTAMP` | NOT NULL | |
| `published_at` | `TIMESTAMP` | NULLABLE | |

> Bảng này nằm **cùng schema với `transactions`**, để việc ghi `transactions` (status SUCCESS/FAILED) và ghi `outbox_events` xảy ra trong **cùng 1 `@Transactional`** — đây chính là điều kiện tiên quyết để Transactional Outbox Pattern hoạt động đúng. Outbox Publisher (1 scheduled job hoặc Debezium CDC nếu muốn nâng cao) đọc các dòng `published = false`, publish lên Kafka, rồi cập nhật `published = true`.

### 1.9 `notifications` (Notification Service)

| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `id` | `BIGINT` | PK | |
| `user_id` | `BIGINT` | NOT NULL | |
| `type` | `VARCHAR(30)` | NOT NULL | `EMAIL / SMS / PUSH` |
| `channel_event` | `VARCHAR(50)` | NOT NULL | `TRANSFER_SUCCESS`, `OTP_REQUESTED`... |
| `content` | `TEXT` | NOT NULL | |
| `status` | `VARCHAR(20)` | NOT NULL | `SENT / FAILED / RETRYING` |
| `retry_count` | `INT` | DEFAULT 0 | |
| `created_at` | `TIMESTAMP` | NOT NULL | |

### 1.10 `audit_logs` (dùng chung, ghi từ mọi service qua Kafka event hoặc gọi trực tiếp)

| Cột | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `id` | `BIGINT` | PK | |
| `actor_id` | `BIGINT` | NULLABLE | ai thực hiện (null nếu là SYSTEM) |
| `actor_type` | `VARCHAR(20)` | NOT NULL | `CUSTOMER / ADMIN / SYSTEM` |
| `action` | `VARCHAR(100)` | NOT NULL | `ACCOUNT_LOCK`, `LOGIN_FAILED`, `TRANSFER_SUCCESS`... |
| `target_type` | `VARCHAR(50)` | NULLABLE | `ACCOUNT`, `USER`, `TRANSACTION` |
| `target_id` | `VARCHAR(50)` | NULLABLE | |
| `metadata` | `JSONB` | NULLABLE | chi tiết bổ sung dạng JSON (IP, amount, lý do...) |
| `created_at` | `TIMESTAMP` | NOT NULL | |

> Bảng này **chỉ INSERT, không bao giờ UPDATE/DELETE** — nên cân nhắc revoke quyền UPDATE/DELETE ở DB level cho service account, thể hiện tư duy bảo mật nghiêm túc.

---

## 2. Quan hệ giữa các bảng (tóm tắt)

```
users *───1 roles 1───* role_permissions *───1 permissions
users 1───* refresh_tokens
users 1───* accounts        (tham chiếu logic, khác database)
accounts 1───* transactions (qua account_number, không FK cứng cross-service)
users 1───* notifications
(mọi service) *───* audit_logs (ghi nhận sự kiện, không có quan hệ FK chặt)
```

**Lưu ý quan trọng:** vì mỗi service có database/schema riêng, **không dùng FOREIGN KEY vật lý xuyên service** (ví dụ `accounts.user_id` không FK tới `users.id` vì 2 bảng có thể ở 2 schema/database khác nhau). Chỉ dùng FK cứng cho các bảng trong cùng 1 service (`users.role_id`, `role_permissions`, `refresh_tokens`)

---
