# ADR-002 · Layered Testing Strategy

- **Status:** Accepted
- **Date:** 2026-07-01
- **Author:** [@Toleflaco](https://github.com/Toleflaco)
- **Scope:** `task-manager-api`

> Format inspired by Michael Nygard's [_Documenting Architecture
> Decisions_](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
> (2011). Each ADR captures one decision with its context and
> consequences so that the rationale survives the people who made it.

---

## Context

Since each layer has a distinct responsibility, each requires a
different testing strategy.

The service layer orchestrates business logic and coordinates calls
to repositories, mappers, and event publishers. Testing with Mockito
allows verifying that coordination without the need for
infrastructure.

Mappers are pure transformations with no business logic and no
dependencies. Their tests use `Mappers.getMapper()` directly, without
mocks.

Repositories translate the domain into JPA queries against
PostgreSQL. Their behaviour depends on real SQL execution, constraint
enforcement, and entity mapping, so they are tested as integration
tests with Testcontainers, not mocked.

Controllers expose HTTP endpoints and delegate to services. Their
tests must exercise JSON serialization, security filters, and HTTP
status codes end-to-end, which is what MockMvc provides.

A single, uniform testing tool for every layer would either overshoot
(booting the full Spring context to test a pure function) or
undershoot (mocking away the very SQL execution that a repository
test is supposed to verify). The strategy below matches each tool to
the responsibility of the layer it targets.

---

## Decision

Adopt a layered testing strategy in which each layer is tested with
the tool that matches its responsibility:

- **Service layer.** Unit tests with JUnit 5 and Mockito. Business
  rules, orchestration between collaborators, and events emitted are
  verified with mocked dependencies. No Spring context is loaded.
- **Mapper layer.** Pure-function unit tests. The mapper is
  instantiated via `Mappers.getMapper(XxxMapper.class)` and its
  transformation is asserted directly against known inputs and
  outputs. No Mockito, no Spring, no mocks of any kind.
- **Repository layer.** Integration tests with `@DataJpaTest` and
  Testcontainers running a real PostgreSQL image. JPQL queries,
  Specifications, projections, cascades, and constraints are
  exercised against actual SQL. _Planned for Phase 8.5._
- **Controller layer.** End-to-end tests with `@WebMvcTest` and
  `MockMvc`. Route wiring, JSON (de)serialization, validation
  responses, `ProblemDetail` payloads, security filter behaviour, and
  HTTP status codes are verified against the actual dispatcher.
  _Planned for Phase 8.5._

Test naming, layout and conventions locked earlier in Phase 8 remain
authoritative across all four layers: `XxxTest.java` for unit,
`XxxIT.java` for integration and end-to-end,
`scenarioUnderTest_expectedResult` method naming, `@Nested` grouping,
BDDMockito syntax where Mockito applies, stubbing only when the
return value is consumed.

JaCoCo enforcement is configured per package with a quality gate on
`tasks`, `categories`, and `auth` (see `pom.xml`, `check`
execution). The gate uses `haltOnFailure=false` while Phase 8.5 is
pending: the WARNINGs are expected and are the correct signal that
controller and repository coverage is not yet in place. Once
Phase 8.5 closes, the flag will be flipped to `true` and the
thresholds re-evaluated.

---

## Consequences

### What is gained

- Each layer is tested at the level that matches its responsibility.
  Fast tests where speed is achievable (services, mappers), slow
  tests only where realism is required (repositories, controllers).
- JaCoCo coverage becomes interpretable per package rather than as a
  single global percentage. The quality gate applies only to packages
  with a unit-testing responsibility today (`tasks`, `categories`,
  `auth`); other packages are excluded on purpose, not by oversight.
- New collaborators can reason about "what kind of test should this
  be?" from the layer alone, without inspecting existing tests to
  infer the convention.
- Refactors within one layer do not force rewrites in the others: a
  change to a repository implementation does not break a service
  test, because the service test never depended on the real SQL.

### Trade-offs explicitly accepted

| Trade-off | Mitigation |
|---|---|
| Four different test setups to learn at onboarding (Mockito, `Mappers.getMapper()`, Testcontainers, MockMvc). | Documented here and in `CLAUDE.md`. Each layer's first test in the codebase serves as a reference example. |
| Discipline required to keep the layers honest — nothing prevents a service test from being written as an integration test by accident. | Reviewed at PR level. Naming convention (`Test` vs `IT`) makes drift visible. |
| Some behaviour is verified in two places (e.g. a service call that ultimately hits a repository). | Accepted: unit tests verify the contract of the service against a mocked repository; integration tests verify the repository against a real database. They test different contracts against different collaborators, not the same one twice. |
| Coverage numbers are not directly comparable across layers. A mapper package trivially reaches 100%; an auth package with security filters may not. | Interpreted per layer, not as a single aggregate metric. |

### When this decision would be revisited

- **If the service layer starts absorbing responsibilities that
  belong elsewhere** (raw SQL, HTTP concerns), the boundary — not the
  testing strategy — would be the thing to fix first.
- **If Testcontainers startup times become a bottleneck on CI**, the
  strategy for repository tests would be reassessed (shared container
  across the suite, or a lighter alternative), but the layered
  principle would stand.
- **If application-level integration bugs start slipping through
  despite green tests at every layer**, an additional thin
  end-to-end suite hitting the running application would be added on
  top — not as a replacement for the layered strategy but as a
  complement.

---

## Alternatives considered

### 1. `@SpringBootTest` for everything

Booting the full Spring context and a real database for every test,
regardless of layer.

_Rejected._ Two reasons. First, speed: a full-context suite is
orders of magnitude slower than a pure Mockito unit test, and the
cost compounds across hundreds of tests. Second, and more important:
this approach masks bugs of orchestration and unit responsibility
under the noise of a working end-to-end path. When such a test
fails, the failure does not localise cleanly to a layer, and the
diagnostic cost per red test is high.

### 2. Unit tests with mocked mappers

Writing service tests where the mapper is mocked like any other
collaborator, returning a canned DTO.

_Rejected._ A mocked mapper verifies that Mockito returns what it
was told to return — a tautology. The actual transformation logic
would go untested unless a separate mapper test exists anyway, which
means the service test's mock adds ceremony without adding coverage.
Direct instantiation via `Mappers.getMapper()` is cheap enough that
the mapper participates in the service test as a real collaborator,
and the mapper's own tests verify the transformation in isolation.

### 3. A single global JaCoCo coverage threshold

Configuring one project-wide minimum coverage percentage instead of
per-package rules.

_Rejected._ A global threshold treats a mapper package (trivial to
cover) and a security filter package (harder to cover with unit
tests alone) as if they carried the same risk, and rewards padding
easy packages to compensate for hard ones. Per-package rules make
the intent visible: these packages must hold this bar, those are
covered elsewhere, and the excluded ones are excluded on purpose.

---

## Out of scope

Some code is deliberately not covered by tests, either because the
responsibility belongs elsewhere or because there is nothing
domain-specific to exercise:

- **MapStruct-generated null guards.** `CategoryMapperImpl` and
  `TaskMapperImpl` contain `if (X == null) return null;` branches
  emitted by the annotation processor. Testing them verifies the
  contract of MapStruct itself, not the behaviour of this
  application. The generator's correctness is MapStruct's
  responsibility, not the domain's.
- **Redundant final branches of a state machine.** In
  `TaskService#complete` and `TaskService#cancel`, the `CANCELLED`
  branch mirrors a happy path already tested in a sibling method.
  Covering it again adds a green line to the report without
  documenting a new contract or protecting against a new class of
  bug.
- **Spring configuration classes, the application `main`, and
  trivial exception types.** These have no logic to exercise. Their
  correctness is verified indirectly by the tests of the components
  that depend on them (a broken `SecurityConfig` would fail every
  MockMvc test in Phase 8.5) or by application startup itself.
- **Auto-generated `equals` / `hashCode` on records used as DTOs.**
  The compiler is responsible for their correctness.

The common criterion: raising a coverage number without protecting a
new contract is gaming the metric, not testing the system.

---

## References

- **Implementation so far:** `com.mtole.taskmanager.tasks`,
  `com.mtole.taskmanager.categories`,
  `com.mtole.taskmanager.auth` — unit tests for services and
  mappers.
- **JaCoCo configuration:** `pom.xml`, `jacoco-maven-plugin`
  executions `prepare-agent`, `report`, `check`.
- **Current quality gate:** PACKAGE granularity, includes
  `tasks`, `categories`, `auth`, thresholds `LINE ≥ 0.85` and
  `BRANCH ≥ 0.80`, `haltOnFailure=false` while Phase 8.5 is pending.
- **Related decision:** [ADR-001 — Polyglot Persistence](./adr-001-polyglot-persistence.md).

---

> _ADRs are append-only by convention. If this decision is reversed
> or superseded, do not edit this file — open a new ADR that links
> back here and explains why._
