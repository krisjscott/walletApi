# Digital wallet API

One Spring Boot app with registration, login, JWT authentication, wallet balances, transfers, and paginated transaction history. Requires Java 21. The Maven wrapper downloads Maven.

## Run locally

Set a signing secret with at least 32 bytes, then start the app:

```bash
export JWT_SECRET="$(openssl rand -hex 32)"
./mvnw spring-boot:run
```

The API listens on port 8080 and stores its H2 database under `./data/`. Keep the same secret across restarts to preserve existing tokens. Tokens expire after 3,600 seconds by default.

Optional environment variables are `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_EXPIRATION_SECONDS`. The default database username is `sa` with an empty password, for local development. The H2 console is disabled.

## API reference

| Method | Path | Request or result |
| --- | --- | --- |
| POST | `/api/auth/register` | `{ "name": "Alice", "email": "alice@example.com", "password": "password123" }` |
| POST | `/api/auth/login` | `{ "email": "alice@example.com", "password": "password123" }` |
| GET | `/api/wallet` | Current user's wallet ID and balance |
| POST | `/api/transaction/transfer` | `{ "toWalletId": 2, "amount": 10.50, "idempotencyKey": "payment-001" }` |
| GET | `/api/transaction?page=0&size=20` | Current user's incoming and outgoing transactions, newest first |

Registration returns HTTP 201. Login and successful transfers return HTTP 200. Authentication responses contain `token`, `tokenType`, `expiresIn`, `userId`, and `walletId`.

All wallet and transaction endpoints require `Authorization: Bearer <token>`. The sender comes from the authenticated user. Clients cannot select another sender or read another user's history. Page numbers start at zero, and page sizes range from 1 to 100. History responses contain `content`, `page`, `size`, `totalElements`, and `totalPages`.

Amounts must be positive, with at most two decimal places and 12 integer digits. A transfer changes both balances and writes one successful transaction in one database transaction. Wallet locks use ID order. Concurrent retries with the same sender and idempotency key return the original transaction. Reusing that key with a different amount or recipient returns HTTP 409. Different senders can reuse the same key.

Validation errors, self transfers, and insufficient funds return HTTP 400. Invalid credentials and invalid or expired tokens return HTTP 401. Missing wallets return HTTP 404. Duplicate registration returns HTTP 409. Failed transfers roll back all changes and do not create history entries.

New wallets start at zero. This app has no funding endpoint or payment-provider integration. Transfer tests seed balances directly in the test database. Existing `TOPUP` and `FAILED` enum values remain available, but these flows do not create those records.

## Run tests

```bash
./mvnw test
```

Tests use `src/test/resources/application-test.properties` with a separate in-memory H2 database and a test signing secret. They cover HTTP authentication and validation, wallet creation, transfer history, rollback, idempotent retries, and concurrent debits.

JWT signing and validation use Spring Security's Nimbus support. See the [Spring Security JWT reference](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
