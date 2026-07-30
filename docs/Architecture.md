# Architecture Design — Digital Banking System

## 1. Các thành phần trong kiến trúc

| Thành phần | Vai trò | Công nghệ |
|---|---|---|
| API Gateway | Cổng vào duy nhất, routing, verify JWT | Spring Cloud Gateway |
| Config Server | Quản lý tập trung config cho tất cả service | Spring Cloud Config |
| Auth Service | Đăng ký/đăng nhập, ký JWT | Spring Boot + Spring Security |
| User Service | Hồ sơ người dùng, phân quyền | Spring Boot |
| Account Service | Quản lý tài khoản ngân hàng, số dư | Spring Boot |
| Transaction Service | Xử lý giao dịch chuyển tiền, Outbox Publisher | Spring Boot |
| Notification Service | Gửi email/SMS khi có sự kiện | Spring Boot + Kafka Consumer |
| PostgreSQL | Lưu trữ dữ liệu, mỗi service 1 schema + 1 DB user riêng | PostgreSQL 16 |
| Redis | OTP, rate limit, idempotency cache, token blacklist | Redis 7 |
| Kafka | Message broker cho giao tiếp bất đồng bộ | Kafka (KRaft mode) |

---

## 2. Quyết định kiến trúc

### 2.1 Database Architecture

Hệ thống tuân theo nguyên tắc **Database per Service**.

Để phục vụ phát triển nội bộ, tất cả cơ sở dữ liệu dịch vụ đều được lưu trữ trên một phiên bản PostgreSQL duy nhất nhằm giảm thiểu chi phí cơ sở hạ tầng. Mỗi dịch vụ sở hữu một lược đồ PostgreSQL riêng biệt , được truy cập thông qua một người dùng cơ sở dữ liệu chuyên dụng với quyền hạn chỉ giới hạn trong lược đồ đó:Để phục vụ phát triển nội bộ, tất cả cơ sở dữ liệu dịch vụ đều được lưu trữ trên một phiên bản PostgreSQL duy nhất nhằm giảm thiểu chi phí cơ sở hạ tầng. Mỗi dịch vụ sở hữu một lược đồ PostgreSQL riêng biệt , được truy cập thông qua một người dùng cơ sở dữ liệu chuyên dụng với quyền hạn chỉ giới hạn trong lược đồ đó:

```
                PostgreSQL
┌────────────────────────────────────┐
│ auth_schema                        │ (owner: auth_user)
│ user_schema                        │ (owner: user_user)
│ account_schema                     │ (owner: account_user)
│ transaction_schema                 │ (owner: transaction_user)
│ notification_schema                │ (owner: notification_user)
└────────────────────────────────────┘
```

Ví dụ, `account_user` **không** được quyền:
```sql
SELECT * FROM user_schema.users;
```
Các dịch vụ không được trực tiếp truy vấn hoặc sửa đổi cơ sở dữ liệu của dịch vụ khác. Việc giao tiếp giữa các dịch vụ phải sử dụng API REST hoặc sự kiện Kafka.
### 2.2 Giao tiếp giữa các service

Đồng bộ (REST/OpenFeign) cho truy vấn cần kết quả ngay (ví dụ Transaction Service kiểm tra số dư ở Account Service). Bất đồng bộ (Kafka) cho sự kiện không cần phản hồi ngay (ví dụ báo Notification Service gửi email). Resilience4j (Circuit Breaker) bọc quanh mọi Feign call để tránh cascade failure khi service phụ thuộc down.

### 2.3 Service Discovery

Bỏ qua Eureka ở phiên bản đầu — dùng static routing (service name của Docker Compose) vì mỗi service chỉ chạy 1 instance cố định ở môi trường local.

### 2.4 Redis

Redis is used for:
- OTP storage with TTL
- Rate limiting (login, transfer)
- Idempotency cache
- Short-lived caching (account/profile read)
- Token revocation / blacklist

(Không nhấn mạnh session — vì hệ thống dùng JWT stateless, không cần session server-side.)

### 2.5 JWT Signing

Auth Service ký JWT Access Token bằng Asymmetric Key Pair (RSA/EC) thay vì sử dụng Symmetric Secret dùng chung.
```
Auth Service ── (Private Key) ──> Sign JWT
API Gateway  ── (Public Key)  ──> Verify JWT
```

- Auth Service sở hữu private key, không service nào khác có
- Gateway (và các service khác nếu cần verify trực tiếp) chỉ giữ public key
- Private key **không bao giờ commit vào Git** — inject qua secret manager hoặc Docker secret

---

## 3. Transaction Consistency — Saga & Compensation

### 3.1 Vì sao không dùng 1 `@Transactional` xuyên service

`@Transactional` chỉ quản lý transaction trong phạm vi một resource/database context.

Trong hệ thống này:

- Transaction Service sở hữu database (schema) riêng.
- Account Service cũng sở hữu database (schema) riêng.

Vì vậy, không thể sử dụng một local transaction để rollback xuyên qua nhiều service

Hệ thống không sử dụng Distributed Database Transaction giữa các service.

Thay vào đó:

- Mỗi service chỉ quản lý Local Database Transaction của chính mình.
- Quy trình chuyển tiền tuân theo Saga Pattern.

Cụ thể:

- Account Service thực hiện debit và credit trong một local transaction của chính service đó.
- Nếu các bước phía sau thất bại, hệ thống sẽ thực hiện Compensating Transaction thay vì cố gắng rollback một transaction đã được commit ở service khác.
### 3.2 Compensation

```
Debit sender
     ↓
Credit receiver fails
     ↓
Compensation
     ↓
Refund sender
```
Trong trường hợp này:

Hệ thống không rollback transaction đã commit, mà thực hiện một Compensating Transaction để hoàn tiền lại cho người gửi (Refund Sender).
Nói cách khác:

Nếu một quy trình gồm nhiều bước thất bại sau khi một thao tác trước đó đã được commit, hệ thống sẽ thực hiện Compensation thay vì Distributed Rollback.
### 3.3 Transactional Outbox Pattern

Vấn đề nếu publish Kafka trực tiếp sau khi save DB:
```
Transaction DB: save SUCCESS
        ↓
Kafka publish
        ↓
Kafka chết → event bị mất, nhưng DB đã ghi SUCCESS
```
Điều gì xảy ra?

- Database đã lưu transaction thành công (SUCCESS).
- Nhưng Kafka bị lỗi.
- Event không được publish.
- Notification Service sẽ không bao giờ biết rằng giao dịch đã thành công.

Điều này dẫn đến:

- Dữ liệu trong Database đúng.
- Nhưng các service khác không nhận được event.

Hệ thống trở nên không nhất quán (Inconsistent).
Giải pháp — Transaction Service áp dụng Transactional Outbox Pattern:

```
Transaction Service
        │
        ├── transactions        
        │
        └── outbox_events      
                  │
                  ▼
           Outbox Publisher     (background job, đọc event chưa publish)
                  │
                  ▼
                Kafka
```
Trong mô hình này:

Bước 1

Trong cùng một Local Database Transaction, hệ thống sẽ:

- Ghi transaction vào bảng transactions.
- Đồng thời ghi một event vào bảng outbox_events.

Nếu transaction rollback thì:

- Transaction không được lưu.
- Event cũng không được lưu.

Nếu transaction commit thì:

- Cả transaction và event đều được lưu thành công.

Nhờ đó hai dữ liệu luôn nhất quán.

Bước 2

Một `Background Job` (gọi là `Outbox Publisher`) sẽ liên tục:

- Đọc các event chưa được publish trong bảng `outbox_events`.
- Publish các event đó lên Kafka.
- Đánh dấu event đã publish thành công.

Nhờ vậy, nếu Kafka tạm thời bị lỗi:

- Event vẫn còn trong bảng `outbox_events`.
- Khi Kafka hoạt động trở lại, `Outbox Publisher` sẽ tiếp tục publish.

Không có event nào bị mất.

---

## 4. Sequence Diagram — Internal Transfer 

1. `Client` gửi yêu cầu `POST /transactions/transfer/internal` kèm theo JWT.
2. `API Gateway` xác thực (validate) JWT rồi chuyển tiếp (forward) request.
3. `Transaction Service` kiểm tra `Idempotency Key` để đảm bảo request không bị xử lý nhiều lần.
4. `Transaction Service` tạo một transaction với trạng thái `PROCESSING`.
5. `Transaction Service` gọi `Account Service` thông qua `OpenFeign`.
6. `Account Service` kiểm tra tính hợp lệ của cả tài khoản gửi và tài khoản nhận, đồng thời xác minh trạng thái của chúng.
7. `Account Service` thực hiện `debit` và `credit` trong `Local Database Transaction` của chính service.
8. `Optimistic Locking` được sử dụng để ngăn chặn việc nhiều transaction cùng lúc cập nhật số dư của một tài khoản.
9. `Account Service` trả kết quả chuyển tiền về cho `Transaction Service`.
10. `Transaction Service` cập nhật trạng thái transaction thành `SUCCESS` hoặc `FAILED`.
11. Trong cùng `Local Database Transaction`, `Transaction Service` ghi thêm một `Outbox Event` vào bảng `outbox_events`.
12. `Outbox Publisher` đọc event và publish lên `Kafka`.
13. `Notification Service` consume event từ `Kafka`.
14. `Notification Service` gửi Email hoặc Push Notification một cách bất đồng bộ.

Nếu bước 6-7 thất bại giữa chừng (debit thành công, credit thất bại): `Account Service` chạy compensation (refund sender) → trả lỗi về `Transaction Service` → `Transaction Service` cập nhật `FAILED` → ghi outbox event `transfer.failed`.

---

## 5. Transaction State Machine
Luồng chuyển trạng thái của transaction:
```
PENDING → PROCESSING → SUCCESS
                    ↘ FAILED
```

Không phải mọi trạng thái đều được phép chuyển đổi.

Ví dụ **không được phép**:
- `SUCCESS → PROCESSING` ❌
- `FAILED → SUCCESS` ❌

Việc kiểm tra này nên được thực hiện ngay trong các phương thức của Entity.

Nếu trạng thái hiện tại không hợp lệ để chuyển tiếp, phương thức sẽ throw Exception.

Không nên chỉ kiểm tra ở Service Layer, vì điều đó dễ bị bỏ sót khi Entity được sử dụng ở nhiều nơi khác nhau.

---

## 6. Kiến trúc tổng thể

```
                               ┌─────────────────┐
                               │     Client      │
                               └────────┬────────┘
                                        │
                                        ▼
                               ┌─────────────────┐
                               │   API Gateway   │
                               └────────┬────────┘
                                        │
  ┬──────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
  │              ▼              ▼              ▼              ▼              │ 
  │             Auth          User          Account      Transaction         │   
  │            Service       Service        Service         Service          │   
  │                                                   (+ Outbox Publisher)   │
  │              │              │              │                 │           │
  │              ▼              ▼              ▼                 ▼           │   
  │         auth_schema  user_schema  account_schema  transaction_schema     │
  └──────────────────────────────────────────────────────────────────────────┘            
                                        │
                                        ▼
                                      Kafka
                                        │
                                        ▼
                              Notification Service
                                        │
                                        ▼
                              notification_schema

Infrastructure: Redis • Kafka • Config Server • PostgreSQL
```




