<<<<<<< HEAD
# Task Manager API

**Java 21 · Spring Boot 4 · Maven · OpenAPI**

This is a REST API for managing personal tasks, organized by user and category. It’s the final project of Phase 5 in my Java backend learning roadmap, where I’ve been applying everything learned so far without overcomplicating things: layered architecture, validations, clean error handling, and owner‑based authorization so each user only touches their own stuff.

The API already includes OpenAPI documentation to explore and test the endpoints easily, and it has a global error‑handling system using **ProblemDetail (RFC 9457)**, so error responses are clean, consistent, and easy to understand.

Right now it works with in‑memory data, but it’s **fully prepared to grow once I add JPA**: the layers are properly separated, services don’t depend on infrastructure details, and the whole design is ready for plugging in real repositories without breaking anything.

In short: a simple, organized API that’s ready to level up when persistence comes into play.# Task Manager API

**Java 21 · Spring Boot 4 · Maven · OpenAPI**

A REST API for managing personal tasks, organized by user and category. 
It is the capstone project of Phase 5 of my Java backend learning roadmap, 
applying the patterns covered throughout: layered architecture, validation, 
global error handling, and owner-based authorization so each user can only 
access their own resources.

The API ships with OpenAPI documentation for interactive exploration, and 
returns errors as RFC 9457 ProblemDetail responses — consistent and 
machine-readable.

Persistence is currently in-memory. The design is structured so that 
swapping the repositories for JPA-backed implementations in Phase 6 
requires no changes to services or controllers.

## Tech stack

- **Java 21** — modern language features (records, pattern matching, virtual threads support).
- **Spring Boot 4.0.6** — application framework and dependency injection.
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

All endpoints except `POST /users` require an `X-User-Id` header simulating
authentication. See the [Authentication](#authentication) section for details.

### Users

| Method | Path | Description | Auth |
| --- | --- | --- | --- |
| `POST` | `/users` | Register a new user | Public |
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

Authentication is **simulated via an `X-User-Id` header** that carries the
authenticated user's id. This is a deliberate placeholder: the project focuses
on REST architecture, validation, and error handling — proper authentication
with Spring Security and JWT is scheduled for Phase 5.5.

Every endpoint except `POST /users` (public registration) requires this header.
A missing header returns a `400 Bad Request` with a clear ProblemDetail
response.

Resources are scoped to their owner: attempting to access another user's
resource returns `404 Not Found`, not `403 Forbidden`, to avoid leaking
information about the existence of resources owned by others.

## Architectural decisions

### Package-by-feature

The codebase is organized by feature (`users`, `categories`, `tasks`), not by
technical layer (`controllers`, `services`, `repositories`). Each feature is
self-contained, including its own DTOs and mapper. Cross-cutting concerns
live in a `common` package.

Rationale: features evolve as units. When a feature is removed or modified,
all related code is in one place. Layer-based packaging scatters changes
across the codebase.

Cross-feature dependencies go through repositories, not services. For example,
`TaskService` injects `CategoryRepository` (not `CategoryService`) to validate
category ownership — this keeps the dependency surface minimal and prevents
cyclic service dependencies.

### Owner-based authorization with `404 Not Found`

When a user attempts to access a resource owned by another user, the API
responds with `404 Not Found`, not `403 Forbidden`. This follows the
principle of **least information leakage**: a `403` would confirm the
resource exists, helping an attacker enumerate ids.

Filtering happens at the repository layer (`findByIdAndUserId`,
`deleteByIdAndUserId`), not by loading and post-checking — the database
never returns resources that don't belong to the caller.

### `userId` is never accepted from the client

For any resource bound to a user, the `userId` is assigned server-side from
the authenticated session, never read from the request body. This prevents
**IDOR** (Insecure Direct Object Reference): a client cannot impersonate
another user by manipulating the body. The same principle applies to fields
like `status` (assigned by business logic in the service, never by the client).

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
  This pattern is sometimes called the "controller pattern" or *action
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
no risk of accidental mutation in the controller or service layer.

Entities are mutable classes because they evolve (a task gets completed, its
status changes). Records would force creating a new instance on every change,
which is unnecessary friction for in-memory and future JPA-managed entities.

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

### Global error handling with RFC 9457 ProblemDetail

All exceptions are caught by a single `@RestControllerAdvice`. Each handler
returns a `ProblemDetail` (RFC 9457): consistent, machine-readable, and
self-documenting via the `type` field.

| Exception | HTTP Status |
| --- | --- |
| `ResourceNotFoundException` | `404 Not Found` |
| `MethodArgumentNotValidException` | `400 Bad Request` (with `fields` listing all validation errors) |
| `MissingRequestHeaderException` | `400 Bad Request` |
| `MethodArgumentTypeMismatchException` | `400 Bad Request` |
| `InvalidTaskStateException` | `409 Conflict` |
| `Exception` (catch-all) | `500 Internal Server Error` |

### Structured logging with MDC

Every HTTP request is assigned a UUID by `RequestIdFilter` and stored in
SLF4J's MDC. The log pattern includes `[%X{requestId}]`, so every log line
produced during a request can be correlated — crucial for production
debugging.

Write operations log intent before and outcome after (`Creating task...` →
`Task created with id=42`). Read operations are not logged to avoid noise.
Sensitive fields are never logged.

### In-memory persistence (current) — JPA-ready (Phase 6)

Repositories are interfaces with in-memory implementations
(`InMemoryUserRepository`, etc.) using `ConcurrentHashMap` and `AtomicLong`.

The contract of every repository method is **structurally compatible** with
its future JPA equivalent. For example:

- `findByIdAndUserId(Long, Long)` → maps directly to a Spring Data derived query.
- `deleteAllByUserId(Long)` → same.

Migrating to JPA in Phase 6 means replacing the implementation class, not
the interface — and not touching any service or controller.

## Roadmap

### Phase 5.5 — Authentication & Authorization (next)

Replace the simulated `X-User-Id` header with real authentication:

- Spring Security with JWT bearer tokens.
- BCrypt password hashing on `User` (the `password` field is currently absent).
- Refresh token rotation.
- Role-based access (admin endpoints, e.g., listing all users).

The current owner-based authorization checks at the service layer will remain
as-is — they protect against IDOR regardless of the auth mechanism in front.

### Phase 6 — Persistence (after 5.5)

Replace the in-memory repositories with JPA-backed implementations:

- Hibernate as the JPA provider.
- PostgreSQL for production, H2 for tests.
- Flyway for schema migrations.
- `@Transactional` boundaries on multi-step service operations (notably the
  cascade in `UserService.deleteById`).
- Switch `findByIdAndUserId` to `existsByIdAndUserId` where existence is the
  only concern (avoids hydrating entities unnecessarily).

The repository interfaces are already shaped for this migration — no service
or controller changes will be needed.

### Phase 4.5 — Document storage (after 6)

Add MongoDB for unstructured data (task attachments, activity history).

### Future improvements

- **Rate limiting** on public endpoints (`POST /users`).
- **Audit log** for state transitions (who completed what, when).
- **Test coverage**: the project currently has no automated tests; integration
  tests with `@SpringBootTest` and Testcontainers will be added alongside
  Phase 6.
- **Containerization** with Docker for local and CI environments.
- **CI pipeline** with GitHub Actions (build, lint, test).

## Author

**Tole** ([@Toleflaco](https://github.com/Toleflaco))

Self-taught backend developer following a structured Java learning roadmap.
This project is part of that journey.

## License

This project is released under the MIT License. See [LICENSE](LICENSE) for details.

###
=======
# Task Manager API

**Java 21 · Spring Boot 4 · Maven · OpenAPI**

This is a REST API for managing personal tasks, organized by user and category. It’s the final project of Phase 5 in my Java backend learning roadmap, where I’ve been applying everything learned so far without overcomplicating things: layered architecture, validations, clean error handling, and owner‑based authorization so each user only touches their own stuff.

The API already includes OpenAPI documentation to explore and test the endpoints easily, and it has a global error‑handling system using **ProblemDetail (RFC 9457)**, so error responses are clean, consistent, and easy to understand.

Right now it works with in‑memory data, but it’s **fully prepared to grow once I add JPA**: the layers are properly separated, services don’t depend on infrastructure details, and the whole design is ready for plugging in real repositories without breaking anything.

In short: a simple, organized API that’s ready to level up when persistence comes into play.# Task Manager API

**Java 21 · Spring Boot 4 · Maven · OpenAPI**

A REST API for managing personal tasks, organized by user and category. 
It is the capstone project of Phase 5 of my Java backend learning roadmap, 
applying the patterns covered throughout: layered architecture, validation, 
global error handling, and owner-based authorization so each user can only 
access their own resources.

The API ships with OpenAPI documentation for interactive exploration, and 
returns errors as RFC 9457 ProblemDetail responses — consistent and 
machine-readable.

Persistence is currently in-memory. The design is structured so that 
swapping the repositories for JPA-backed implementations in Phase 6 
requires no changes to services or controllers.

## Tech stack

- **Java 21** — modern language features (records, pattern matching, virtual threads support).
- **Spring Boot 4.0.6** — application framework and dependency injection.
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

All endpoints except `POST /users` require an `X-User-Id` header simulating
authentication. See the [Authentication](#authentication) section for details.

### Users

| Method   | Path          | Description                                               | Auth     |
| -------- | ------------- | --------------------------------------------------------- | -------- |
| `POST`   | `/users`      | Register a new user                                       | Public   |
| `GET`    | `/users`      | List users (paginated)                                    | Required |
| `GET`    | `/users/{id}` | Get user by id                                            | Required |
| `DELETE` | `/users/{id}` | Delete user and cascade-remove their tasks and categories | Required |

### Categories

| Method   | Path               | Description                     | Auth     |
| -------- | ------------------ | ------------------------------- | -------- |
| `POST`   | `/categories`      | Create a category               | Required |
| `GET`    | `/categories`      | List own categories (paginated) | Required |
| `GET`    | `/categories/{id}` | Get own category by id          | Required |
| `PUT`    | `/categories/{id}` | Update own category             | Required |
| `DELETE` | `/categories/{id}` | Delete own category             | Required |

### Tasks

| Method   | Path                   | Description                            | Auth     |
| -------- | ---------------------- | -------------------------------------- | -------- |
| `POST`   | `/tasks`               | Create a task                          | Required |
| `GET`    | `/tasks`               | List own tasks (filterable, paginated) | Required |
| `GET`    | `/tasks/{id}`          | Get own task by id                     | Required |
| `PUT`    | `/tasks/{id}`          | Update own task                        | Required |
| `POST`   | `/tasks/{id}/complete` | Mark task as completed                 | Required |
| `POST`   | `/tasks/{id}/cancel`   | Mark task as cancelled                 | Required |
| `DELETE` | `/tasks/{id}`          | Delete own task                        | Required |

> Full request/response schemas and examples are available via Swagger UI at
> `http://localhost:8081/swagger-ui.html`.



## Authentication

Authentication is **simulated via an `X-User-Id` header** that carries the
authenticated user's id. This is a deliberate placeholder: the project focuses
on REST architecture, validation, and error handling — proper authentication
with Spring Security and JWT is scheduled for Phase 5.5.

Every endpoint except `POST /users` (public registration) requires this header.
A missing header returns a `400 Bad Request` with a clear ProblemDetail
response.

Resources are scoped to their owner: attempting to access another user's
resource returns `404 Not Found`, not `403 Forbidden`, to avoid leaking
information about the existence of resources owned by others.



## Architectural decisions

### Package-by-feature

The codebase is organized by feature (`users`, `categories`, `tasks`), not by
technical layer (`controllers`, `services`, `repositories`). Each feature is
self-contained, including its own DTOs and mapper. Cross-cutting concerns
live in a `common` package.

Rationale: features evolve as units. When a feature is removed or modified,
all related code is in one place. Layer-based packaging scatters changes
across the codebase.

Cross-feature dependencies go through repositories, not services. For example,
`TaskService` injects `CategoryRepository` (not `CategoryService`) to validate
category ownership — this keeps the dependency surface minimal and prevents
cyclic service dependencies.

### Owner-based authorization with `404 Not Found`

When a user attempts to access a resource owned by another user, the API
responds with `404 Not Found`, not `403 Forbidden`. This follows the
principle of **least information leakage**: a `403` would confirm the
resource exists, helping an attacker enumerate ids.

Filtering happens at the repository layer (`findByIdAndUserId`,
`deleteByIdAndUserId`), not by loading and post-checking — the database
never returns resources that don't belong to the caller.

### `userId` is never accepted from the client

For any resource bound to a user, the `userId` is assigned server-side from
the authenticated session, never read from the request body. This prevents
**IDOR** (Insecure Direct Object Reference): a client cannot impersonate
another user by manipulating the body. The same principle applies to fields
like `status` (assigned by business logic in the service, never by the client).

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
   This pattern is sometimes called the "controller pattern" or *action
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
no risk of accidental mutation in the controller or service layer.

Entities are mutable classes because they evolve (a task gets completed, its
status changes). Records would force creating a new instance on every change,
which is unnecessary friction for in-memory and future JPA-managed entities.

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

### Global error handling with RFC 9457 ProblemDetail

All exceptions are caught by a single `@RestControllerAdvice`. Each handler
returns a `ProblemDetail` (RFC 9457): consistent, machine-readable, and
self-documenting via the `type` field.

| Exception                             | HTTP Status                                                     |
| ------------------------------------- | --------------------------------------------------------------- |
| `ResourceNotFoundException`           | `404 Not Found`                                                 |
| `MethodArgumentNotValidException`     | `400 Bad Request` (with `fields` listing all validation errors) |
| `MissingRequestHeaderException`       | `400 Bad Request`                                               |
| `MethodArgumentTypeMismatchException` | `400 Bad Request`                                               |
| `InvalidTaskStateException`           | `409 Conflict`                                                  |
| `Exception` (catch-all)               | `500 Internal Server Error`                                     |

### Structured logging with MDC

Every HTTP request is assigned a UUID by `RequestIdFilter` and stored in
SLF4J's MDC. The log pattern includes `[%X{requestId}]`, so every log line
produced during a request can be correlated — crucial for production
debugging.

Write operations log intent before and outcome after (`Creating task...` →
`Task created with id=42`). Read operations are not logged to avoid noise.
Sensitive fields are never logged.

### In-memory persistence (current) — JPA-ready (Phase 6)

Repositories are interfaces with in-memory implementations
(`InMemoryUserRepository`, etc.) using `ConcurrentHashMap` and `AtomicLong`.

The contract of every repository method is **structurally compatible** with
its future JPA equivalent. For example:

- `findByIdAndUserId(Long, Long)` → maps directly to a Spring Data derived query.
- `deleteAllByUserId(Long)` → same.

Migrating to JPA in Phase 6 means replacing the implementation class, not
the interface — and not touching any service or controller.



## Roadmap

### Phase 5.5 — Authentication & Authorization (next)

Replace the simulated `X-User-Id` header with real authentication:

- Spring Security with JWT bearer tokens.
- BCrypt password hashing on `User` (the `password` field is currently absent).
- Refresh token rotation.
- Role-based access (admin endpoints, e.g., listing all users).

The current owner-based authorization checks at the service layer will remain
as-is — they protect against IDOR regardless of the auth mechanism in front.

### Phase 6 — Persistence (after 5.5)

Replace the in-memory repositories with JPA-backed implementations:

- Hibernate as the JPA provider.
- PostgreSQL for production, H2 for tests.
- Flyway for schema migrations.
- `@Transactional` boundaries on multi-step service operations (notably the
  cascade in `UserService.deleteById`).
- Switch `findByIdAndUserId` to `existsByIdAndUserId` where existence is the
  only concern (avoids hydrating entities unnecessarily).

The repository interfaces are already shaped for this migration — no service
or controller changes will be needed.

### Phase 4.5 — Document storage (after 6)

Add MongoDB for unstructured data (task attachments, activity history).

### Future improvements

- **Rate limiting** on public endpoints (`POST /users`).
- **Audit log** for state transitions (who completed what, when).
- **Test coverage**: the project currently has no automated tests; integration
  tests with `@SpringBootTest` and Testcontainers will be added alongside
  Phase 6.
- **Containerization** with Docker for local and CI environments.
- **CI pipeline** with GitHub Actions (build, lint, test).



## Author

**Tole** ([@Toleflaco](https://github.com/Toleflaco))

Self-taught backend developer following a structured Java learning roadmap.
This project is part of that journey.

## License

This project is released under the MIT License. See [LICENSE](LICENSE) for details.

### 


>>>>>>> 4c59982 (README.md)
