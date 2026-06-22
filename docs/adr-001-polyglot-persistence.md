# ADR-001 · Polyglot Persistence — PostgreSQL + MongoDB

- **Status:** Accepted
- **Date:** 2026-06-22
- **Author:** [@Toleflaco](https://github.com/Toleflaco)
- **Scope:** `task-manager-api`

> Format inspired by Michael Nygard's [_Documenting Architecture
> Decisions_](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
> (2011). Each ADR captures one decision with its context and
> consequences so that the rationale survives the people who made it.

---

## Context

`task-manager-api` persists two kinds of data with very different
shapes and access patterns:

1. **Transactional data** — `users`, `tasks`, `categories`. Highly
   relational (a task belongs to a user and a category; a category
   belongs to a user), mutable, queried with joins and filters, with
   business rules that depend on referential integrity (e.g. soft
   delete, ownership checks, optimistic locking).
2. **Activity audit log** — a stream of domain events emitted whenever
   a task or category is created, updated, deleted or transitions
   state. The shape of each event's `before` / `after` snapshot
   depends on the event type. The collection is append-only, queried
   almost exclusively as _"the activity of this user in this date
   range"_.

A single-database approach forces one of these two workloads into a
tool that was not designed for it. Picking one storage engine for
both means paying its weaknesses on whichever workload doesn't match.

---

## Decision

Use **polyglot persistence**:

- **PostgreSQL 14** for transactional data — `users`, `tasks`,
  `categories`.
- **MongoDB Atlas (M0 free tier, AWS Ireland)** for the activity audit
  log — collection `activity_events`.

Both databases live behind the same Spring Boot application. The
activity module subscribes to domain events via
`ApplicationEventPublisher` and writes synchronously inside the JPA
transaction of the publisher, so an audit write failure rolls the
business operation back.

---

## Rationale — Why MongoDB for the audit log

### 1. Heterogeneous payloads without migrations

Every event type stores different fields in its `before` / `after`
snapshots: a `TaskStatusChanged` event has a small payload, a
`CategoryDeleted` event has a snapshot of the deleted entity, a
`TaskUpdated` event may eventually carry a structured diff. In
MongoDB this is a `Map<String, Object>` on the entity and a flexible
sub-document on the wire, with no migrations when a new event type is
added.

In PostgreSQL the three available shapes are all worse here:

- One table per event type → table explosion proportional to the
  domain.
- One wide table with nullable columns for every possible field → data
  quality nightmare and weak typing.
- One table with a `JSONB` payload column → PostgreSQL imitating
  MongoDB, with weaker querying ergonomics around JSON paths and
  without the indexing affordances MongoDB has for embedded fields.

### 2. Append-only write pattern

Events are inserted, never updated, never deleted. This pattern leaves
most of PostgreSQL's strengths on the table: ACID transactions across
multiple rows, referential integrity, complex updates with constraints
— none of them are used by an append-only log. MongoDB's strengths
match the pattern directly: cheap single-document writes, easy
horizontal sharding by date or by user when volume eventually demands
it, no need for multi-document transactions on the hot path.

### 3. Query pattern: time range per user

The dominant read is _"show me my activity in this date range, with
optional filters"_. A compound index on `(userId asc, timestamp
desc)` covers it efficiently and was verified with `explain()`. No
join with `users` is needed at read time because the `userId` comes
from the JWT on every authenticated request.

The aggregation endpoint (`GET /me/activity/stats`) computes counts
grouped by `action` over a date range in a single `$facet` pipeline.
The same shape would be possible in PostgreSQL with `GROUP BY`, but
it is not the bottleneck — what justifies MongoDB is the first two
points.

### 4. Operational independence

The audit log grows without bound. Decoupling it from the
transactional database means backup sizes, restore times, vacuum
behaviour, and (eventually) sharding strategy for the audit log are
decided independently of what happens to user and task data. The
transactional database stays small and fast; the audit collection
grows where it should.

---

## Rationale — Why PostgreSQL is kept for the rest

### 1. The domain is inherently relational

A task belongs to a user and to a category; a category belongs to a
user. These foreign keys are not metadata — they _are_ the structure
of the domain. Modelling them in MongoDB with embedding or referencing
adds friction without benefit.

### 2. Referential integrity guaranteed by the engine

`ON DELETE CASCADE`, `NOT NULL` foreign keys, unique constraints,
check constraints. Moving these guarantees to application code means
writing — and maintaining — code that replicates what the relational
engine already does correctly, with bugs latent at every layer.

### 3. Multi-row transactions are routine

Creating a task and updating an aggregated counter, soft-deleting a
category and its tasks, applying optimistic locking on update — these
all benefit from cheap ACID transactions out of the box. MongoDB has
multi-document transactions since v4.0 but they are an _exception_, not
the routine pattern, with measurable performance cost.

### 4. Strict schema catches bugs early

Column types, length constraints, `NOT NULL`, foreign keys. These
catch wrong data at insertion time in development, before it reaches
production. MongoDB's `schema-on-read` model is liberating for the
audit log but a liability for transactional data.

---

## Consequences

### What is gained

- The right tool for each workload — neither database is fighting its
  own design.
- The audit log can grow independently without affecting transactional
  database operations.
- Each event type can evolve its payload without a Flyway migration.
- A single Spring Boot application speaks both stores through Spring
  Data, so the operational surface is one process, not two.

### Trade-offs explicitly accepted

| Trade-off | Mitigation |
|---|---|
| No engine-level join between `users` and `activity_events`. | The `userId` is denormalised onto every event from the JWT. The audit response does not enrich user data; if a name is ever needed, it is fetched in a second query from PostgreSQL. |
| Referential integrity from user to event is not engine-enforced. If a user is deleted, their audit events remain. | Accepted as desirable for an audit log: historical accuracy outweighs referential consistency. Compliance scenarios (GDPR right-to-erasure) would be handled by a separate process that anonymises events, not by cascading delete. |
| Risk of inconsistency between the two databases if one write succeeds and the other fails. | The activity listener runs synchronously inside the JPA transaction. A MongoDB write failure throws and rolls the JPA transaction back. The remaining inconsistency window — MongoDB write succeeds, then JPA commit fails — is small but non-zero. Accepted for the current scale; the upgrade path is the transactional outbox pattern if it becomes observable. |
| Two databases to operate, back up and monitor. | Operational cost accepted explicitly. The simplification of each individual data model is worth the extra operational surface. |
| Spring Data with both modules required explicit package-based scanning to avoid cross-module warnings. | Resolved with `@EnableJpaRepositories(basePackages = ...)` and `@EnableMongoRepositories(basePackages = ...)` in `RepositoryScanConfig`. |

### When this decision would be revisited

- **If the audit log stays small forever** (say, under ~100k total
  events for the project's lifetime), MongoDB is overkill — a single
  PostgreSQL table with a `JSONB` payload would be simpler.
- **If analytical queries that join audit events with user or task
  data become routine**, the operational cost of cross-database
  reads — or of building a separate analytics pipeline — would tilt
  the balance toward a single store.
- **If application-level integrity guarantees become a recurring
  source of bugs**, the trade-off would be reassessed in favour of the
  engine-level guarantees PostgreSQL provides.

### When MongoDB would _not_ be considered for this kind of data

If the audit log needed to be the source of truth for billing, legal
compliance, or any workflow that requires transactional consistency
with the transactional database, MongoDB's relaxed integrity
guarantees would be the wrong trade-off. The current use case —
internal user-facing activity history with eventual consistency
acceptable — is precisely where its strengths apply.

---

## References

- **Implementation:** `com.mtole.taskmanager.activity` package.
- **Entity:** `ActivityEvent` with `@Document` and
  `@CompoundIndex({"userId asc, timestamp desc"})`.
- **Listener:** `ActivityEventListener`, synchronous, inside the
  publisher's JPA transaction.
- **Endpoints:** `GET /me/activity` (paginated listing with optional
  filters), `GET /me/activity/stats` (aggregation pipeline with
  `$facet`).
- **Configuration:** `MongoConfig` declares `MongoClient` and
  `MongoDatabaseFactory` explicitly; `RepositoryScanConfig` keeps the
  JPA and MongoDB repository scans on disjoint packages.

---

> _ADRs are append-only by convention. If this decision is reversed
> or superseded, do not edit this file — open a new ADR that links
> back here and explains why._
