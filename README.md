# Digital Wallet API

A robust, production-grade Spring Boot 4 RESTful API providing digital wallet management, double-entry transfer processing, JWT authentication, deterministic concurrency control, and paginated transaction history.

---

## Table of Contents

- [Overview & Purpose](#overview--purpose)
- [System Architecture](#system-architecture)
  - [Request Lifecycle](#request-lifecycle)
  - [Authentication & Security Flow](#authentication--security-flow)
- [Core Engineering Mechanisms](#core-engineering-mechanisms)
  - [1. Deterministic Concurrency & Deadlock Prevention](#1-deterministic-concurrency--deadlock-prevention)
  - [2. Strict Idempotency Guarantees](#2-strict-idempotency-guarantees)
  - [3. Financial Precision & Balance Integrity](#3-financial-precision--balance-integrity)
  - [4. Bidirectional Transaction Ledger & Pagination](#4-bidirectional-transaction-ledger--pagination)
  - [5. Centralized Error Handling](#5-centralized-error-handling)
- [API Reference](#api-reference)
  - [Authentication](#authentication)
  - [Wallet](#wallet)
  - [Transactions](#transactions)
- [HTTP Status Code Mapping](#http-status-code-mapping)
- [Configuration Reference](#configuration-reference)
- [Local Development & Setup](#local-development--setup)
- [Test Suite & Quality Verification](#test-suite--quality-verification)

---

## Overview & Purpose

The **Digital Wallet API** is designed to model core financial ledger operations securely and reliably. It provides:
1. **User Identity & Multi-tenancy**: Secure user registration, authentication, and 1-to-1 digital wallet provisioning.
2. **Double-Entry Transfers**: Atomic money movement between wallets where the sender is debited and the recipient is credited within a single database transaction.
3. **Pessimistic Concurrency**: Complete protection against race conditions, double-spending, and balance overdrafts under heavy concurrent traffic.
4. **Idempotent API Requests**: Safe payment retry semantics protecting clients against duplicate debits due to network timeouts.
5. **Full Ledger Auditability**: Complete, paginated transaction history showing both outgoing and incoming transfers ordered chronologically.

---

## System Architecture

### Request Lifecycle

```
[ Client Request ]
       │
       ▼
[ Security Filter Chain ] ──▶ JwtAuthFilter (Extracts 'Bearer <token>', verifies Nimbus HMAC-SHA256)
       │
       ▼
[ Spring Security Context ] ──▶ Authenticated Principal (User email extracted & stored in SecurityContext)
       │
       ▼
[ Controller Layer ] ───────▶ DTO Validation (@Valid, @Positive, @DecimalMin, @NotBlank)
       │
       ▼
[ Service Layer ] ──────────▶ Business Logic & Transaction Boundary (@Transactional)
       │                       ├── Lock Acquisition (Ordered IDs via Pessimistic Write Lock)
       │                       ├── Idempotency Check (Existing transaction verification)
       │                       └── Domain Mutations (Wallet debit/credit + Transaction record)
       ▼
[ JPA Repositories ] ───────▶ Spring Data JPA / Hibernate (H2 / Relational Persistence)
       │
       ▼
[ Database (H2) ] ──────────▶ Atomic Commit / Rollback
```

### Authentication & Security Flow

- **Token Technology**: Stateless JSON Web Tokens (JWT) signed via HMAC-SHA256 (`MacAlgorithm.HS256`) using Spring Security's Nimbus JWT engine (`JwtEncoder` and `JwtDecoder`).
- **Subject Identity**: The token's subject (`sub`) claim stores the user's normalized email address (`email.trim().toLowerCase(Locale.ROOT)`).
- **Password Security**: Passwords are encrypted using Spring Security's `BCryptPasswordEncoder`. Byte-length validation enforces the 72-byte limit imposed by the BCrypt algorithm.
- **Context Enforcement**: Controllers resolve wallet ownership directly from `Principal.getName()`. Clients cannot supply a spoofed `fromWalletId`; all actions originate strictly from the authenticated identity.

---

## Core Engineering Mechanisms

### 1. Deterministic Concurrency & Deadlock Prevention

Concurrent transfers between two users (e.g., User A transferring to User B simultaneously while User B transfers to User A) can easily cause database deadlocks if locks are acquired in arbitrary order:

```
Thread 1: Locks Wallet A, waits for Wallet B.
Thread 2: Locks Wallet B, waits for Wallet A.  ==> DEADLOCK (AB-BA cycle)
```

To eliminate deadlocks, `TransferService` enforces **strict lock ordering by numeric ID**:
```java
// Always acquire wallet locks in ID order:
Wallet first = lock(Math.min(senderId, recipientId));
Wallet second = lock(Math.max(senderId, recipientId));
```
Both wallets are locked pessimistically using `LockModeType.PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`), serializing concurrent transactions across the two accounts without deadlocks.

### 2. Strict Idempotency Guarantees

In financial systems, clients frequently retry network requests when experiencing timeouts. The API guarantees safety through a database-backed idempotency mechanism:
- A unique database constraint on `(from_wallet_id, idempotency_key)` prevents duplicate writes at the storage layer.
- **Exact Replay**: When a transfer request arrives with an idempotency key that already completed for that sender with the identical recipient and amount, the API returns the original `TransactionResponse` without altering balances or creating duplicate ledger rows.
- **Conflict Detection**: If the same idempotency key is reused by the sender with a different amount or different recipient, the API aborts the request with `HTTP 409 Conflict`.
- **Sender Scoping**: Idempotency keys are scoped to the sender's wallet; two distinct users can safely use the same idempotency key without collision.

### 3. Financial Precision & Balance Integrity

- **Fixed-Point Arithmetic**: All monetary fields use `BigDecimal` with explicit scale checks (`scale <= 2`). Floating-point numbers (`float`, `double`) are strictly prohibited to prevent IEEE-754 rounding errors.
- **Balance Limits**: Wallets enforce a maximum balance limit of `999,999,999,999.99` (12 integer digits and 2 decimal places), matching the database schema `DECIMAL(14, 2)`.
- **Overdraft Protection**: Any transfer attempt exceeding the available balance raises `InsufficientBalanceException`, rolling back all changes within the transaction.

### 4. Bidirectional Transaction Ledger & Pagination

Transaction history (`/api/transaction`) retrieves both incoming and outgoing payments for the authenticated user's wallet:
- Query: `SELECT t FROM Transaction t WHERE t.fromWallet.id = :id OR t.toWallet.id = :id`
- **Sorting**: Consistent descending sort order: `createdAt DESC, id DESC`, ensuring the latest transactions are always presented first.
- **Paging Metadata**: Responses include `page`, `size`, `totalElements` (total count across all pages), and `totalPages`.

### 5. Centralized Error Handling

All controller and domain exceptions are intercepted by `GlobalExceptionHandler` to produce consistent JSON error payloads:
```json
{
  "timestamp": "2026-11-02T15:30:00.000Z",
  "status": 400,
  "message": "amount: must be greater than or equal to 0.01"
}
```

---

## API Reference

### Authentication

#### 1. Register Account
Creates a new user account and automatically provisions a digital wallet with a zero balance.

- **Endpoint**: `POST /api/auth/register`
- **Status**: `201 Created`
- **Request Body**:
```json
{
  "name": "Alice Smith",
  "email": "alice@example.com",
  "password": "Password123"
}
```
- **Response Body**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsIn...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "userId": 1,
  "walletId": 1
}
```

#### 2. User Login
Authenticates user credentials and returns a signed JWT.

- **Endpoint**: `POST /api/auth/login`
- **Status**: `200 OK`
- **Request Body**:
```json
{
  "email": "alice@example.com",
  "password": "Password123"
}
```
- **Response Body**: Same as Register.

---

### Wallet

#### Get Wallet Balance
Fetches the current user's wallet identifier and available balance.

- **Endpoint**: `GET /api/wallet`
- **Headers**: `Authorization: Bearer <token>`
- **Status**: `200 OK`
- **Response Body**:
```json
{
  "id": 1,
  "balance": 150.75
}
```

---

### Transactions

#### 1. Transfer Funds
Transfers money from the authenticated user's wallet to a recipient wallet.

- **Endpoint**: `POST /api/transaction/transfer`
- **Headers**: `Authorization: Bearer <token>`
- **Status**: `200 OK`
- **Request Body**:
```json
{
  "toWalletId": 2,
  "amount": 25.50,
  "idempotencyKey": "tx-uuid-9843-abcd"
}
```
- **Response Body**:
```json
{
  "id": 101,
  "fromWalletId": 1,
  "toWalletId": 2,
  "amount": 25.50,
  "type": "TRANSFER",
  "status": "SUCCESS",
  "createdAt": "2026-11-02T15:30:00"
}
```

#### 2. Transaction History
Returns a paginated list of all incoming and outgoing transactions for the authenticated user.

- **Endpoint**: `GET /api/transaction?page=0&size=20`
- **Headers**: `Authorization: Bearer <token>`
- **Query Parameters**:
  - `page`: Zero-based page index (default: `0`).
  - `size`: Page size between `1` and `100` (default: `20`).
- **Status**: `200 OK`
- **Response Body**:
```json
{
  "content": [
    {
      "id": 101,
      "fromWalletId": 1,
      "toWalletId": 2,
      "amount": 25.50,
      "type": "TRANSFER",
      "status": "SUCCESS",
      "createdAt": "2026-11-02T15:30:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

## HTTP Status Code Mapping

| Status Code | Meaning | Triggers |
| --- | --- | --- |
| `200 OK` | Success | Successful login, balance lookup, transfer, or history query |
| `201 Created` | Created | Successful user registration and wallet creation |
| `400 Bad Request` | Validation Failure | Invalid request body, negative amount, fractional cents beyond 2 decimal places, self-transfer, or insufficient balance |
| `401 Unauthorized` | Auth Failure | Missing, invalid, expired bearer token, or wrong login credentials |
| `403 Forbidden` | Access Denied | Authenticated principal lacks permission |
| `404 Not Found` | Resource Missing | Recipient wallet does not exist |
| `409 Conflict` | State Conflict | Duplicate email registration, reused idempotency key with conflicting payload, or concurrent row locking failure |
| `500 Internal Error` | Server Fault | Unexpected infrastructure failure |

---

## Configuration Reference

The application is configured via `src/main/resources/application.properties` and supports environment variable overrides:

| Variable | Property Key | Default | Description |
| --- | --- | --- | --- |
| `JWT_SECRET` | `app.jwt.secret` | *(Required)* | HMAC-SHA256 signing secret (minimum 32 bytes) |
| `JWT_EXPIRATION_SECONDS` | `app.jwt.expiration-seconds` | `3600` | JWT validity lifetime in seconds (1 hour) |
| `DB_URL` | `spring.datasource.url` | `jdbc:h2:file:./data/wallet` | JDBC connection URL (persisted H2 file) |
| `DB_USERNAME` | `spring.datasource.username` | `sa` | Database username |
| `DB_PASSWORD` | `spring.datasource.password` | *(empty)* | Database password |

---

## Local Development & Setup

### Prerequisites
- **Java**: JDK 21+
- **Maven**: Bundled Maven wrapper (`./mvnw`)

### Running the API

```bash
# Generate a secure 256-bit signing key and run
export JWT_SECRET="$(openssl rand -hex 32)"
./mvnw spring-boot:run
```

The server listens on `http://localhost:8080`.

---

## Test Suite & Quality Verification

The test suite covers unit tests, HTTP web layer tests, concurrency simulations, and targeted regression assertions:

```bash
# Execute entire test suite
./mvnw test
```

### Key Test Categories
- **Concurrency & Race Conditions** (`TransferIntegrationTest`): Uses a multi-threaded `CountDownLatch` executor to simulate concurrent debits and verifies overdraft prevention.
- **Idempotent Replays** (`TransferIntegrationTest`, `HttpIssueRegressionTest`): Verifies that replayed requests with identical keys return original payloads without double-debiting.
- **Isolation & Deadlock Resilience**: Asserts deterministic lock ordering across opposing cross-wallet transfers.
- **Regression Suite** (`regression/*`): Validates fixes for all 15 previously identified defects (documented in `ISSUES.md`).
