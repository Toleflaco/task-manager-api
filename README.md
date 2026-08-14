# Task Manager API

[![CI](https://github.com/Toleflaco/task-manager-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Toleflaco/task-manager-api/actions/workflows/ci.yml)

**Java 21 · Spring Boot 4 · PostgreSQL · MongoDB · AWS S3 · Docker · Spring Security · JWT · OpenAPI**

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

Persistence is polyglot: PostgreSQL for transactional data (users, tasks,
categories), MongoDB for the activity audit log. The full application is
containerized with a multi-stage Dockerfile and orchestrated locally with
Docker Compose, so a fresh clone runs end-to-end with a single command.
Continuous integration runs on GitHub Actions on every push and pull request.

File uploads are integrated with Amazon S3 via the AWS SDK for Java v2.
Credentials are sourced from an EC2 Instance Profile through IMDSv2 —
there are no static access keys anywhere in the codebase, in environment
variables, or in configuration files. Downloads are served via S3
presigned URLs so clients fetch objects directly from S3 with a
time-bounded credential, without proxying binary payloads through the
application. The deployed stack runs on AWS EC2 (Ubuntu 24.04) with
PostgreSQL migrated to Amazon RDS, and S3 traffic routed through a VPC
Endpoint Gateway to keep it on the AWS backbone.

## Tech stack

- **Java 21** — modern language features (records, pattern matching, virtual threads support).
- **Spring Boot 4.0.6** — application framework and dependency injection.
- **Spring Data JPA + Hibernate 6** — ORM, JPA Specifications, entity graphs, optimistic locking.
- **PostgreSQL 14** — transactional store for users, tasks, categories.
- **Flyway** — versioned schema migrations, `ddl-auto: validate` in every environment.
- **Spring Data MongoDB** — audit log persistence (`activity_events` collection).
- **MongoDB Atlas M0** — managed MongoDB (AWS Ireland) for the audit log.
- **AWS SDK for Java v2** — `software.amazon.awssdk:s3` and `software.amazon.awssdk:s3-transfer-manager` for uploads; `S3Presigner` (declared as a separate Spring bean) for presigned URL generation.
- **AWS EC2 Instance Profile via IMDSv2** — credentials resolved automatically by the SDK's default credential provider chain. No `aws-access-key-id`, `aws-secret-access-key`, or `AWS_PROFILE` anywhere in the codebase or runtime environment.
- **Amazon RDS for PostgreSQL** — managed database in a private subnet, reached from EC2 through a security-group reference (not a CIDR range).
- **Spring Security 6** — authentication and authorization infrastructure.
- **jjwt 0.12.6** — JWT signing (HMAC-SHA256) and verification.
- **Bucket4j 8.10.1** — token-bucket rate limiting on `/auth/login`.
- **BCrypt** — password hashing.
- **Docker + Docker Compose** — multi-stage build (Temurin JDK builder + JRE runtime), non-root user, orchestrated local environment with persistent volume.
- **GitHub Actions** — CI pipeline running `./mvnw verify` on push and pull request, with Maven cache and test artifacts.
- **JaCoCo 0.8.12** — code coverage with per-package quality gate on business packages.
- **JUnit 5 + Mockito + AssertJ** — unit tests with BDDMockito style; `ArgumentCaptor`, `InOrder`, and Spring Data projection mocking patterns established.
- **Maven** — build and dependency management via wrapper.
- **MapStruct 1.6.3** — compile-time DTO ↔ entity mapping with strict `ReportingPolicy.ERROR` for fail-fast on field changes.
- **springdoc-openapi 3.0.3** — interactive API documentation via Swagger UI.
- **SLF4J + Logback** — structured logging with MDC for per-request correlation IDs.
- **Spring Boot Actuator** — health and metrics endpoints.
- **Jakarta Validation** — request body validation via annotations.

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

Testing strategy is layered — one tool per layer, matched to that layer's
responsibility. The rationale is documented in
[`docs/adr-002-layered-testing-strategy.md`](docs/adr-002-layered-testing-strategy.md).

## Getting started

The recommended way to run the application is with Docker Compose. It
brings up PostgreSQL and the API in a single command, on any machine
with Docker installed, with no local Java or PostgreSQL required.

### Requirements

- Docker Desktop (or Docker Engine on Linux) with Docker Compose v2.
- A MongoDB Atlas connection (or any reachable MongoDB instance).
- Three environment variables exported in the shell that runs Compose:
  - `DB_PASSWORD` — password for the containerized PostgreSQL user.
  - `MONGODB_PASSWORD` — password for the MongoDB Atlas user.
  - `JWT_SECRET` — Base64-encoded HMAC-SHA256 secret, at least 32 bytes after decoding. Generate one with:

    ```bash
    export JWT_SECRET=$(openssl rand -base64 48)
    ```


The app fails fast at startup if `JWT_SECRET` is missing or too short
(minimum required by HS256 per JWA RFC 7518).

### Run with Docker Compose

```bash
git clone https://github.com/Toleflaco/task-manager-api.git
cd task-manager-api
docker compose up --build
```

The first build compiles the app inside a multi-stage Dockerfile
(~1–2 min); subsequent builds are cached (~30s). PostgreSQL data is
persisted in a Docker-managed volume (`postgres_data`) so it survives
container recreation.

The API starts on `http://localhost:8081` and connects automatically
to the containerized PostgreSQL and to MongoDB Atlas.

### Run without Docker (fallback)

If you already have Java 21 and PostgreSQL 14 installed locally:

```bash
./mvnw spring-boot:run
```

The `application-dev.yml` profile defaults to `localhost:5432` for
PostgreSQL when the Compose-injected `DB_URL` and `DB_USERNAME` are
absent, so the same codebase runs either way with no configuration
changes.

### Explore the API

- **Swagger UI**: http://localhost:8081/swagger-ui.html
- **OpenAPI spec**: http://localhost:8081/v3/api-docs
- **Health check**: http://localhost:8081/actuator/health (requires authentication)

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

### Files (S3-backed)

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| `POST` | `/files` | Upload a file to S3 (`multipart/form-data`, part name `file`). Returns the S3 object key. | Required |
| `GET` | `/files/download-url?key={key}` | Generate an S3 presigned URL for direct client-side download. Response includes `key`, `url`, and `expiresAt` (ISO-8601). | Required |

### Activity (audit log)

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| `GET` | `/me/activity` | List own activity events (paginated, filterable by date range and action) | Required |
| `GET` | `/me/activity/stats` | Aggregated event counts grouped by action over a date range (`$facet` pipeline) | Required |

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

The codebase is organized by feature (`users`, `categories`, `tasks`, `auth`,
`activity`), not by technical layer (`controllers`, `services`, `repositories`).
Each feature is self-contained, including its own DTOs and mapper. Cross-cutting
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
JPA-managed entities.

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

| Exception | HTTP Status |
| --- | --- |
| `ResourceNotFoundException` | `404 Not Found` |
| `MethodArgumentNotValidException` | `400 Bad Request` (with `fields` listing all validation errors) |
| `MissingRequestHeaderException` | `400 Bad Request` |
| `MethodArgumentTypeMismatchException` | `400 Bad Request` |
| `BadCredentialsException` | `401 Unauthorized` (login with wrong password) |
| `InvalidRefreshTokenException` | `401 Unauthorized` (refresh failures) |
| `InvalidTaskStateException` | `409 Conflict` |
| `DuplicateEmailException` | `409 Conflict` (registration with already-used email) |
| `Exception` (catch-all) | `500 Internal Server Error` |

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

### JPA-backed persistence with Flyway-owned schema

All entities (`User`, `Category`, `Task`, `RefreshToken`) are persisted
through Spring Data JPA with Hibernate as the provider. The schema is
owned by Flyway migrations (V1–V8) and Hibernate is configured with
`ddl-auto: validate` in every environment — the ORM never modifies the
schema at runtime.

Key patterns applied across the persistence layer:

- **Soft delete** via `@SQLDelete` + `@SQLRestriction` with a `deleted_at`
  column, so deleted rows are not returned by any read path without
  developer opt-in.
- **JPA auditing** via `@CreatedDate` / `@LastModifiedDate` /
  `@CreatedBy` / `@LastModifiedBy`, backed by a `SecurityAuditorAware`
  that reads the authenticated user id from the `SecurityContext`.
- **Optimistic locking** via `@Version` on every entity, with explicit
  `OptimisticLockingFailureException` handling in the service layer
  (Hibernate ignores manual `setVersion()` on managed entities, so the
  check has to be reasserted programmatically before save).
- **JPA Specifications** with `JpaSpecificationExecutor` for dynamic
  filtering on `GET /tasks`.
- **Interface-based and class-based projections** for read-only queries
  that don't need full entity hydration.
- **`@EntityGraph`** to control fetch semantics (LEFT vs INNER JOIN)
  and eliminate N+1 patterns where they were detected.
- **`open-in-view: false`** — the anti-pattern is disabled from day one
  to force explicit transaction boundaries and catch N+1 problems early.

### Activity log as an event-driven side stream

Domain events (task created, updated, deleted, state transition; category
created, updated, deleted) are published via Spring's
`ApplicationEventPublisher` from `TaskService` and `CategoryService`.
An `ActivityEventListener` with seven `@EventListener` methods subscribes
to these events and writes an `ActivityEvent` document to MongoDB.

Writes run synchronously inside the JPA transaction of the publisher — a
failed audit write rolls back the business operation. This choice trades
maximum throughput for the strongest available consistency between the
transactional store and the audit stream at the current scale; the
upgrade path (transactional outbox) is documented in ADR-001.

The `ActivityEvent` model uses `Map<String, Object>` for its `before` /
`after` snapshots. Each event type stores different fields without
requiring a schema migration — the flexibility MongoDB provides for this
shape is exactly the argument for using it here.

### File uploads to S3 with zero static credentials

File upload is delegated to Amazon S3 rather than stored on the
application server or in PostgreSQL. This decouples binary storage from
the transactional store, keeps the application stateless, and avoids
turning the database into a de-facto blob store.

Credentials are resolved by the AWS SDK v2 default credential provider
chain. In production the chain terminates at the **EC2 Instance Profile**
attached to the host, which exposes rotating short-lived credentials via
IMDSv2. The application code never touches an access key, a secret, or a
profile name. There is no `AWS_ACCESS_KEY_ID` in the environment, no
`~/.aws/credentials` on the instance, and no key material in the JAR or
in the container image. The same code runs locally against a developer's
`~/.aws/credentials` without any conditional path.

Uploaded files are stored under a date-based S3 key layout
(`yyyy/MM/dd/{uuid}-{safeName}`). The date prefix opens the door to
lifecycle rules that auto-expire or transition old objects to cheaper
storage classes without touching application code, and gives operators
a natural chronological navigation of the bucket. The UUID guarantees
per-object uniqueness even when two clients upload files with the same
name at the same second.

Filenames are sanitized against an explicit character whitelist:
anything outside `[a-zA-Z0-9._-]` is replaced with a hyphen before the
name reaches the S3 key. Null or blank filenames fall back to a fixed
literal. Path separators, control characters, and non-ASCII payloads
therefore cannot escape the intended prefix or inject unexpected key
structure — the sanitizer runs upstream of the S3 client call, so a
malicious filename never reaches the SDK.

### Downloads via S3 presigned URLs, not proxied bytes

The application does not proxy S3 downloads. When a client requests a
file, it calls `GET /files/download-url?key={key}` and receives an S3
presigned URL with a bounded TTL (externalized as
`aws.s3.presign.download-ttl` in `application-aws.yml`). The client
then fetches the object directly from S3.

The trade-off accepted: presigned URLs shift access control from the
application to a time-bounded signed credential. Anyone in possession of
a valid URL can download the object until it expires. The TTL is
therefore sized against the download-latency requirements of legitimate
clients (minutes, not hours), and the URL is never logged or persisted.
In exchange, the application avoids streaming binary payloads through
its own JVM heap, its own network interface, and its own CPU — S3
handles that at cloud scale.

TTL enforcement was verified end-to-end: an expired URL returns HTTP
403 from S3 directly, without the application being involved.

### S3 traffic routed through a VPC Endpoint Gateway

The EC2 subnet's route tables include a **VPC Endpoint Gateway** for S3
(`com.amazonaws.eu-west-1.s3`). S3 API calls from the application
resolve to a private target inside the AWS network and never traverse
the public internet, without requiring a NAT Gateway.

The mechanism is a route table entry `pl-<service> → vpce-<endpoint>`
that AWS provisions automatically when the endpoint is attached to a
route table. The destination is an **AWS-managed prefix list** — a
dynamic set of the current S3 IP ranges for the region, maintained and
updated by AWS without operator intervention. Coexistence with the
pre-existing `0.0.0.0/0 → IGW` default route works by **longest prefix
match**: S3-bound traffic matches the more specific prefix list and
takes the endpoint, all other outbound traffic keeps using the
internet gateway. The pattern is additive, not destructive.

Two benefits taken: traffic stays inside AWS's network boundary —
useful for compliance framings that ask "does customer data ever leave
the AWS backbone?" the answer is no for S3 access from this
application — and latency is lower and jitter narrower than the public
route. Cost side: VPC Endpoint Gateways (available for S3 and
DynamoDB) are free, unlike Interface Endpoints, which run as ENIs with
private IPs in the subnet and charge per hour plus per GB processed.
This choice adds security and performance without recurring cost.

### Testing strategy

The testing strategy is layered — one tool per layer. Full rationale in
[ADR-002](docs/adr-002-layered-testing-strategy.md).

- **Service layer**: unit tests with JUnit 5 and Mockito. Business rules,
  orchestration between collaborators, and events emitted are verified
  with mocked dependencies. Established patterns include
  `ArgumentCaptor<Specification<Task>>`, `PageImpl` for `Page<T>`,
  Spring Data projection interface mocking, `InOrder` for verifying
  ordered interactions, and BDDMockito syntax throughout
  (`given()` / `then()`).
- **Mapper layer**: pure-function unit tests. Mappers are instantiated via
  `Mappers.getMapper(...)` and asserted directly against known inputs.
  No mocks — mocking a mapper would verify Mockito, not the transformation.
- **Repository layer**: integration tests with Testcontainers running
  PostgreSQL. Exercises real SQL, constraints, cascades, and entity
  mapping. *Planned for Phase 8.5.*
- **Controller layer**: end-to-end tests with `MockMvc`. Route wiring,
  JSON (de)serialization, `ProblemDetail` payloads, security filter
  behaviour, and HTTP status codes. *Planned for Phase 8.5.*

JaCoCo is configured with a per-package quality gate on business
packages (`tasks`, `categories`, `auth`): LINE ≥ 0.85, BRANCH ≥ 0.80.
`haltOnFailure=false` while Phase 8.5 is pending; will flip to `true`
once controller and repository tests are in.

### Containerization and CI/CD

The application is packaged with a **multi-stage Dockerfile**:

- **Builder stage** — `eclipse-temurin:21-jdk-jammy`, runs the Maven
  Wrapper to produce the `.jar`. The `pom.xml` is copied before `src/`
  and `dependency:go-offline` runs in a separate layer, so Maven
  dependencies are cached across builds and only re-downloaded when
  `pom.xml` changes.
- **Runtime stage** — `eclipse-temurin:21-jre-jammy`, contains only the
  JRE and the compiled `.jar`. A non-root user (`appuser`, UID 1001,
  shell `nologin`) owns the process. `ENTRYPOINT` uses exec form so
  Java runs as PID 1 and receives `SIGTERM` directly on `docker stop`,
  allowing Spring's shutdown hook to run.

Final image weight: ~166 MB. Compilation tools and sources are not
present in the runtime image.

`docker-compose.yml` orchestrates the API alongside a `postgres:14`
container with a healthcheck (`pg_isready`) and a Docker-managed volume
(`postgres_data`) for persistence. `depends_on: condition:
service_healthy` prevents the API from starting until PostgreSQL accepts
connections. Configuration is externalized via environment variables
(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `MONGODB_PASSWORD`,
`JWT_SECRET`) using the `${VAR:default}` syntax in `application-dev.yml`,
so the same image runs against a local container or a managed database
with only the environment changing.

**GitHub Actions** runs the CI pipeline on every push to `main` and on
every pull request: checkout, JDK 21 Temurin setup with Maven cache,
`./mvnw verify` (compile, test, JaCoCo check), and upload of test
reports as artifacts. Runner pinned to `ubuntu-22.04` for stability.

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

### ✅ Phase 6 — JPA persistence (complete)

Migration of all four entities to Spring Data JPA + PostgreSQL, Flyway
V1–V8 migrations, soft delete with `@SQLDelete` / `@SQLRestriction`,
JPA auditing, optimistic locking with `@Version`, JPA Specifications,
projections, `@EntityGraph`, N+1 detection and resolution,
bidirectionality decisions locked per relationship.

### ✅ Phase 6.5 — Polyglot persistence with MongoDB (complete)

Integration of MongoDB Atlas alongside PostgreSQL. Activity audit log
as an event-driven side stream via `ApplicationEventPublisher` and
`@EventListener`, with compound index `(userId asc, timestamp desc)`
and `$facet` aggregation for stats. Architectural rationale in
[ADR-001](docs/adr-001-polyglot-persistence.md).

### ✅ Phase 8 — Testing (complete for unit layer)

Unit test suite for `TaskService`, `CategoryService`, `TaskMapper`,
`CategoryMapper`. JaCoCo configured with per-package quality gate on
business packages. Layered testing strategy documented in
[ADR-002](docs/adr-002-layered-testing-strategy.md).

### ✅ Phase 9 — Containerization & CI/CD (complete)

Multi-stage Dockerfile, `docker-compose.yml` with healthcheck and
persistent volume, environment-based configuration, GitHub Actions
pipeline running on push and pull request.

### ✅ Phase 9.5 — AWS deployment (complete)

Application deployed on AWS EC2 (Ubuntu 24.04, `eu-west-1`) with
PostgreSQL migrated to Amazon RDS (`db.t4g.micro`, private subnet,
security-group-referenced access). File uploads integrated with Amazon
S3 via AWS SDK for Java v2, using EC2 Instance Profile credentials
sourced through IMDSv2 (no static keys anywhere in code, environment,
or image). Downloads served via S3 presigned URLs with externalized
TTL; TTL enforcement verified end-to-end (HTTP 403 from S3 after
expiry). S3 traffic routed through a VPC Endpoint Gateway to keep it
on the AWS backbone without traversing the public internet or
requiring a NAT Gateway.

### 🔜 Phase 8.5 — Integration & E2E tests (next)

- Repository integration tests with `@DataJpaTest` + Testcontainers
  (real PostgreSQL) — exercises JPA Specifications, projections,
  cascades, constraints.
- Controller end-to-end tests with `@WebMvcTest` + `MockMvc` — exercises
  routing, `ProblemDetail` payloads, security filter behaviour, HTTP
  status codes.
- Flip JaCoCo `haltOnFailure` from `false` to `true` once new tests
  bring the business packages above threshold.

### Future improvements

- **Custom `UserDetails`** exposing `getUserId()` to remove the second
  `findByEmail` lookup in `AuthService.login`.
- **`DELETE /users/me`** as the idiomatic alternative to `DELETE /users/{id}`.
- **Combined rate limiting** by IP + by email to address NAT false positives.
- **Block escalation** (1h → 24h → weeks) after persistent failed logins.
- **Transactional outbox** for the audit log if inconsistency between
  PostgreSQL and MongoDB becomes observable at scale.
- **Publish Docker image to GHCR** in the CI pipeline.
- **Per-environment configuration** for a `prod` profile alongside `dev`.

## Author

**Manuel Toledano** ([@Toleflaco](https://github.com/Toleflaco))

Self-taught backend developer following a structured Java learning roadmap.
This project is the work-in-progress of that journey.

## License

This project is released under the MIT License. See [LICENSE](LICENSE) for details.
