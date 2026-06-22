# Task Manager API

**Java 21 · Spring Boot 4 · Maven · Spring Security · JWT · OpenAPI**

A REST API for managing personal tasks, organized by user and category.
It is the working project of my self-taught Java backend learning roadmap,
applying the patterns covered throughout: layered architecture, validation,
global error handling, JWT-based authentication, and owner-based authorization
so each user can only access their own resources.

The API ships with OpenAPI documentation for interactive exploration and
returns errors as RFC 7807 `ProblemDetail` responses — consistent and
machine-readable. Authentication is implemented as a complete JWT subsystem:
short-lived access tokens, opaque refresh tokens with single-use rotation
and reuse detection, and rate limiting on `/auth/login` to defend against
brute force and credential stuffing.

Persistence is currently in-memory. The design is structured so that
swapping the repositories for JPA-backed implementations in Phase 6
requires no changes to services or controllers.


## Architecture

This project uses **polyglot persistence**:

- **PostgreSQL** is the primary store for transactional data: `users`,
  `tasks`, `categories`. The domain is inherently relational and
  benefits from engine-level referential integrity, schema enforcement
  and routine multi-row transactions.
- **MongoDB** stores the activity audit log (`activity_events`
  collection). The audit log is append-only, has heterogeneous
  per-event payloads, and is queried by time range per user — a shape
  where MongoDB's schema flexibility and write profile fit better than
  a relational table.

The full rationale, trade-offs accepted and conditions under which the
decision would be revisited are documented in
[`docs/adr-001-polyglot-persistence.md`](docs/adr-001-polyglot-persistence.md).




## Tech stack

- **Java 21** — modern language features (records, pattern matching, virtual threads support).
- **Spring Boot 4.0.6** — application framework and dependency injection.
- **Spring Security 6** — authentication and authorization infrastructure.
- **jjwt 0.12.6** — JWT signing (HMAC-SHA256) and verification.
- **Bucket4j 8.10.1** — token-bucket rate limiting on `/auth/login`.
- **BCrypt** — password hashing.
- **Maven** — build and dependency management.
- **MapStruct 1.6.3** — compile-time DTO ↔ entity mapping with strict `ReportingPolicy.ERROR` for fail-fast on field changes.
- **springdoc-openapi 3.0.3** — interactive API documentation via Swagger UI.
- **SLF4J + Logback** — structured logging with MDC for per-request correlation IDs.
- **Spring Boot Actuator** — health and metrics endpoints.
- **Jakarta Validation** — request body validation via annotations.

## Getting started

### Requirements

- Java 21
- Maven 3.9+
- A JWT signing secret exported as an environment variable

### Configure the JWT secret

The application requires a Base64-encoded HMAC-SHA256 secret. Generate one and
export it before running the app:

```bash
export JWT_SECRET=$(openssl rand -base64 48)
```

The secret must be at least 32 bytes after decoding (the app fails fast at
startup otherwise — this is the minimum required by HS256 per JWA RFC 7518).

### Run

```bash
git clone https://github.com/Toleflaco/task-manager-api.git
cd task-manager-api
./mvnw spring-boot:run
```

The API starts on `http://localhost:8081`.

### Explore the API

- **Swagger UI**: http://localhost:8081/swagger-ui.html
- **OpenAPI spec**: http://localhost:8081/v3/api-docs
- **Health check**: http://localhost:8081/actuator/health

## API Endpoints

All endpoints except those marked **Public** require a valid JWT access token
in the `Authorization: Bearer <token>` header. See the
[Authentication](#authentication) section for the full flow.

### Authentication

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| `POST` | `/auth/login` | Authenticate with email and password; returns access + refresh token pair | Public, **rate-limited** |
| `POST` | `/auth/refresh` | Exchange a valid refresh token for a new access + refresh token pair (rotation) | Public |

### Users

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| `POST` | `/users` | Register a new user (BCrypt-hashed password) | Public |
| `GET` | `/users` | List users (paginated) | Required |
| `GET` | `/users/{id}` | Get user by id | Required |
| `DELETE` | `/users/{id}` | Delete user and cascade-remove their tasks and categories | Required |

### Categories

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| `POST` | `/categories` | Create a category | Required |
| `GET` | `/categories` | List own categories (paginated) | Required |
| `GET` | `/categories/{id}` | Get own category by id | Required |
| `PUT` | `/categories/{id}` | Update own category | Required |
| `DELETE` | `/categories/{id}` | Delete own category | Required |

### Tasks

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| `POST` | `/tasks` | Create a task | Required |
| `GET` | `/tasks` | List own tasks (filterable, paginated) | Required |
| `GET` | `/tasks/{id}` | Get own task by id | Required |
| `PUT` | `/tasks/{id}` | Update own task | Required |
| `POST` | `/tasks/{id}/complete` | Mark task as completed | Required |
| `POST` | `/tasks/{id}/cancel` | Mark task as cancelled | Required |
| `DELETE` | `/tasks/{id}` | Delete own task | Required |

> Full request/response schemas and examples are available via Swagger UI at
> `http://localhost:8081/swagger-ui.html`.

## Authentication

Authentication uses JWT bearer tokens issued by `/auth/login` after validating
email and password (BCrypt-hashed at registration). The login response follows
the OAuth 2.0 token response shape (RFC 6749 §5.1):

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "expiresIn": 60,
  "tokenType": "Bearer"
}
```

The client sends the access token on every protected request:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

When the access token expires, the client exchanges its refresh token at
`/auth/refresh` to obtain a new pair. The endpoint is public because the
client cannot present a valid access token at that point — its credential is
the refresh token in the body, not the header.

A missing or invalid `Authorization` header on a protected endpoint returns
`401 Unauthorized` with a `WWW-Authenticate: Bearer` header (RFC 6750) and
a `ProblemDetail` body.

Resources are scoped to their owner: attempting to access another user's
resource returns `404 Not Found`, not `403 Forbidden`, to avoid leaking
information about the existence of resources owned by other users.

## Architectural decisions

### Package-by-feature

The codebase is organized by feature (`users`, `categories`, `tasks`, `auth`),
not by technical layer (`controllers`, `services`, `repositories`). Each
feature is self-contained, including its own DTOs and mapper. Cross-cutting
concerns live in `common` (`RequestIdFilter`, `GlobalExceptionHandler`,
`PagedResponse`) or `security` (filters, JWT, rate limiting).

Rationale: features evolve as units. When a feature is removed or modified,
all related code is in one place. Layer-based packaging scatters changes
across the codebase.

Cross-feature dependencies go through repositories, not services. For example,
`TaskService` injects `CategoryRepository` (not `CategoryService`) to validate
category ownership — this keeps the dependency surface minimal and prevents
cyclic service dependencies.

### JWT-based authentication, not OAuth2

The system uses JWT directly for stateless session authentication. The same
backend issues and consumes the tokens — there is no third-party client
requesting access to user resources, so the OAuth 2.0 authorization-delegation
protocol does not apply. This is a deliberate decision documented as a
distinction many candidates conflate in interviews: *JWT is a token format
(RFC 7519); OAuth 2.0 is an authorization-delegation protocol (RFC 6749).
OAuth 2.0 may use JWTs as its tokens, but using JWTs does not imply
implementing OAuth 2.0*.

Access tokens are signed with HMAC-SHA256 (HS256). The secret lives in the
`JWT_SECRET` environment variable, never in source control. The signing key
is loaded once at application startup and validated for minimum length (32
bytes for HS256) — fail-fast if the operator misconfigures the deployment.

The `JwtAuthenticationFilter` runs early in the Spring Security chain. It
parses the `Authorization: Bearer ...` header, verifies the signature and
expiration, and populates the `SecurityContext` with the authenticated user
id. When the header is missing or the token is invalid, the filter does not
short-circuit with `401` directly — it lets the request pass with an empty
`SecurityContext` so that the downstream `AuthorizationFilter` decides
whether the path requires authentication. This separation keeps public
endpoints reachable even when malformed Authorization headers appear.

A `JwtAuthenticationEntryPoint` translates `AuthenticationException` raised
by the authorization phase into `401 Unauthorized` with an idiomatic
`ProblemDetail` body and `WWW-Authenticate: Bearer` header.

### Refresh tokens with single-use rotation and reuse detection

The token model is intentionally asymmetric:

- **Access token**: short-lived (1 minute in dev, 15 minutes in production),
  stateless JWT. Carries the user id as `sub`. Validated cryptographically
  on every request — no server-side lookup.
- **Refresh token**: long-lived (5 minutes in dev, 7 days in production),
  opaque UUID, persisted server-side. Stored with `userId`, `familyId`,
  `expiresAt`, and a `revoked` flag.

Each call to `/auth/refresh` performs **single-use rotation**: the presented
refresh token is marked as `revoked = true`, and a new access + refresh
pair is issued. The new refresh token **inherits the `familyId`** of the
old one, forming a chain of tokens belonging to the same login session.

When a request to `/auth/refresh` presents a token that is already revoked,
**reuse detection** is triggered: the system assumes one of two scenarios:
either an attacker has obtained the token and is using it after the
legitimate client rotated, or the legitimate client is replaying a stale
token. Since these cases are indistinguishable from the server's
perspective, the **entire family of refresh tokens is revoked**
(`WHERE familyId = ? SET revoked = true`), forcing the legitimate user to
re-authenticate via `/auth/login`. The collateral cost of forcing relogin
on the legitimate user is accepted because the alternative is leaving an
attacker authenticated.

The three failure modes of `/auth/refresh` (unknown token, revoked token,
expired token) all return the same generic `401` response with the same
message. Distinguishing them would allow an attacker to enumerate the
state of the token store.

### Rate limiting on `/auth/login` with Bucket4j

A `RateLimitingFilter` runs **before** the JWT filter — applying the
fail-fast, fail-cheap principle: an attacker who would be rate-limited
should not consume CPU on JWT parsing. The bucket configuration is
exposed in `application.yml` (`rate-limit.login.capacity` and
`refill-period`) so it can be tuned per environment without recompiling.

The Token Bucket algorithm allows legitimate burst usage (5 attempts in
rapid succession after a refill window) while economically deterring brute
force and credential stuffing. Rate limiting does not prevent these attacks
— it makes them economically infeasible by multiplying their cost.

Limitations documented for future work:

- **Per-IP only**: false positives behind corporate NAT where many users
  share a single public IP. The professional pattern is to combine per-IP
  with per-email/user buckets, but per-user requires persistent storage
  (in-memory is reset on every app restart).
- **`request.getRemoteAddr()` only**: in production behind a proxy or CDN,
  the real client IP arrives in `X-Forwarded-For`. A `trust-proxy-headers`
  flag should gate that lookup so the header is honored only when a
  trusted proxy is in front.
- **In-memory only**: across multiple replicas behind a load balancer, the
  in-memory `ConcurrentHashMap` is per-instance, so the same client gets
  multiple buckets. Bucket4j supports Redis / JCache / JDBC backends for
  this — migration changes only the bucket source, not the filter logic.

### `429 Too Many Requests` written directly in the filter

Exceptions thrown from inside a servlet filter do not reach
`@RestControllerAdvice` — `@ExceptionHandler` only catches exceptions on
the MVC side. The filter writes the response (status, headers including
`Retry-After`, `application/problem+json` body, UTF-8 charset) directly
via `HttpServletResponse`. The same pattern is used by
`JwtAuthenticationEntryPoint` for the same structural reason.

### Owner-based authorization with `404 Not Found`

When a user attempts to access a resource owned by another user, the API
responds with `404 Not Found`, not `403 Forbidden`. This follows the
principle of **least information leakage**: a `403` would confirm the
resource exists, helping an attacker enumerate ids.

Filtering happens at the repository layer (`findByIdAndUserId`,
`deleteByIdAndUserId`), not by loading and post-checking — the repository
never returns resources that don't belong to the caller.

### `userId` is never accepted from the client

For any resource bound to a user, the `userId` is assigned server-side from
the authenticated principal (`SecurityUtils.currentUserId()`), never read
from the request body. This prevents **IDOR** (Insecure Direct Object
Reference): a client cannot impersonate another user by manipulating the
body. The same principle applies to fields like `status` (assigned by
business logic in the service, never by the client).

### Task state machine

Task status transitions are explicitly validated in `TaskService`. A task
can be completed or cancelled only from `PENDING` or `IN_PROGRESS`. Illegal
transitions (e.g., completing an already-cancelled task) throw an
`InvalidTaskStateException` mapped to `409 Conflict`.

This prevents silent history corruption — for example, re-completing an
already-completed task would otherwise overwrite the original `completedAt`
timestamp with a new value, falsifying audit history.

### `POST` for state-changing actions, not `PUT`

Completing or cancelling a task is exposed as `POST /tasks/{id}/complete` and
`POST /tasks/{id}/cancel`, not as a `PUT` updating the `status` field.

Three reasons:

1. **REST semantics**: `PUT` means *replace the resource with the provided
   representation*. Completing a task is a **command**, not a replacement.
   This pattern is sometimes called the *controller pattern* or *action
   sub-resource*.
2. **No body to send**: `PUT` expects a representation in the body.
   Completing a task needs no client input — just the action.
3. **Idempotency contract**: `PUT` must be idempotent by HTTP contract.
   Calling `complete` twice on the same task returns `200` the first time
   and `409` the second — that violates `PUT` semantics, but is valid for
   `POST`.

The DTO `TaskUpdateRequest` (used by `PUT /tasks/{id}`) explicitly excludes
the `status` field. Status changes flow through dedicated action endpoints
only, never through the general update path.

### Records for DTOs, classes for entities

DTOs (`*Request`, `*Response`) are Java `record`s — immutable, no setters,
no risk of accidental mutation in the controller or service layer. Note
that Spring Boot Jackson serializes only the canonical constructor
components of records, not extra methods — so fields that must appear in
the JSON response (e.g., `tokenType` on `LoginResponse`) are constructor
parameters, not methods.

Entities are mutable classes because they evolve (a task gets completed,
its status changes; a refresh token gets revoked). Records would force
creating a new instance on every change, which is unnecessary friction for
in-memory and future JPA-managed entities.

### MapStruct over Lombok or manual mapping

MapStruct is configured with `unmappedTargetPolicy = ReportingPolicy.ERROR`.
Adding a field to an entity or DTO without updating the mapper causes a
**compile-time failure**, not a silent null at runtime.

Lombok was deliberately not used. Three reasons:

1. **`@Data` on JPA entities** triggers lazy-loading exceptions, infinite
   `toString` cycles, and unstable `hashCode` — three serious bugs that
   appear only in integration tests.
2. Explicit code is easier for newcomers to read and debug.
3. Less build-time magic, fewer surprises with annotation processor ordering.

### Paginated responses via a generic `PagedResponse<T>` wrapper

List endpoints return a `PagedResponse<T>` record containing the items plus
pagination metadata: `page`, `pageSize`, and `total`. A raw `List<T>` would
leave the client unaware of total elements or remaining pages.

The wrapper is generic and lives in the `common` package, reusable across
features. It belongs there — not inside any feature package — because
pagination is a cross-cutting presentation concern, not domain logic.

### Global error handling with RFC 7807 ProblemDetail

All exceptions are caught by a single `@RestControllerAdvice`. Each handler
returns a `ProblemDetail` (RFC 7807): consistent, machine-readable, and
self-documenting via the `type` field.

| Exception                             | HTTP Status                                                     |
| ------------------------------------- | --------------------------------------------------------------- |
| `ResourceNotFoundException`           | `404 Not Found`                                                 |
| `MethodArgumentNotValidException`     | `400 Bad Request` (with `fields` listing all validation errors) |
| `MissingRequestHeaderException`       | `400 Bad Request`                                               |
| `MethodArgumentTypeMismatchException` | `400 Bad Request`                                               |
| `BadCredentialsException`             | `401 Unauthorized` (login with wrong password)                  |
| `InvalidRefreshTokenException`        | `401 Unauthorized` (refresh failures)                           |
| `InvalidTaskStateException`           | `409 Conflict`                                                  |
| `DuplicateEmailException`             | `409 Conflict` (registration with already-used email)           |
| `Exception` (catch-all)               | `500 Internal Server Error`                                     |

Rate-limit `429` responses are written directly by `RateLimitingFilter`
because filter exceptions do not reach `@RestControllerAdvice`.

### Structured logging with MDC

Every HTTP request is assigned a UUID by `RequestIdFilter` and stored in
SLF4J's MDC. The log pattern includes `[%X{requestId}]`, so every log line
produced during a request can be correlated — crucial for production
debugging.

Write operations log intent before and outcome after (`Creating task...` →
`Task created with id=42`). Read operations are not logged to avoid noise.
Sensitive fields (passwords, raw tokens) are never logged.

### In-memory persistence (current) — JPA-ready (Phase 6)

Repositories are interfaces with in-memory implementations
(`InMemoryUserRepository`, `InMemoryTaskRepository`, etc.) using
`ConcurrentHashMap` and `AtomicLong`. The `InMemoryRefreshTokenRepository`
uses two separate `AtomicLong` counters — one for entity id, one for
`familyId` — to keep semantic boundaries that will map naturally to
separate columns in JPA.

The contract of every repository method is **structurally compatible** with
its future JPA equivalent. For example:

- `findByIdAndUserId(Long, Long)` → maps directly to a Spring Data derived query.
- `deleteAllByUserId(Long)` → same.
- `revokeFamily(Long)` → will become a `@Modifying @Query` update statement.

Migrating to JPA in Phase 6 means replacing the implementation class, not
the interface — and not touching any service or controller.

## Roadmap

### ✅ Phase 5 — REST architecture, validation, error handling (complete)

Layered architecture, package-by-feature organization, owner-based
authorization, paginated responses, `ProblemDetail`-based error handling,
OpenAPI documentation, structured logging with MDC.

### ✅ Phase 5.5 — Authentication & authorization (complete)

JWT bearer authentication, BCrypt password hashing, refresh tokens with
single-use rotation and reuse detection, rate limiting with Bucket4j,
`/auth/login` and `/auth/refresh` endpoints, complete migration off the
`X-User-Id` simulation. OAuth 2.0 / OpenID Connect studied conceptually
and documented as out-of-scope for this project's threat model.

### 🔜 Phase 6 — Persistence (next)

Replace the in-memory repositories with JPA-backed implementations:

- Hibernate as the JPA provider.
- PostgreSQL for production, H2 for tests.
- Flyway for schema migrations.
- `@Transactional` boundaries on multi-step service operations (notably the
  cascade in `UserService.deleteById` and the family revocation in
  `AuthService.refresh`).
- Switch `findByIdAndUserId` to `existsByIdAndUserId` where existence is the
  only concern (avoids hydrating entities unnecessarily).

The repository interfaces are already shaped for this migration — no service
or controller changes will be needed.

### Future improvements

- **Custom `UserDetails`** exposing `getUserId()` to remove the second
  `findByEmail` lookup in `AuthService.login`.
- **`DELETE /users/me`** as the idiomatic alternative to `DELETE /users/{id}`.
- **Combined rate limiting** by IP + by email to address NAT false positives.
- **Block escalation** (1h → 24h → weeks) after persistent failed logins.
- **Audit log** for state transitions (who completed what, when).
- **Test coverage**: the project currently has no automated tests; integration
  tests with `@SpringBootTest` and Testcontainers will be added alongside
  Phase 6.
- **Containerization** with Docker for local and CI environments.
- **CI pipeline** with GitHub Actions (build, lint, test).
- **Per-environment configuration** via `application-dev.yml` and
  `application-prod.yml` with active profile.

## Author

**Tole** ([@Toleflaco](https://github.com/Toleflaco))

Self-taught backend developer following a structured Java learning roadmap.
This project is the work-in-progress of that journey.

## License

This project is released under the MIT License. See [LICENSE](LICENSE) for details.
