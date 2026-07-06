
Markdown View
AA
ADR-003 · Repository Integration Testing with @DataJpaTest and Testcontainers
Status: Accepted
Date: 2026-07-06
Author: @Toleflaco
Scope: task-manager-api
Format inspired by Michael Nygard's Documenting Architecture Decisions (2011). Each ADR captures one decision with its context and consequences so that the rationale survives the people who made it.

Context
ADR-002 established that repositories are tested with integration tests against a real database. Executing that commitment opened four concrete questions that this ADR resolves:
Which database to use in the integration tests.
Which test annotation to apply to the persistence slice.
How to make auditing work inside the slice.
How to guarantee reproducibility between dev and CI environments.
Database for integration tests
Three options were on the table: H2 in-memory, a native Postgres instance (for example, the one running in the developer's local environment), or Testcontainers spinning up a real Postgres per test.
H2 is discarded because it is not Postgres: it has its own SQL dialect, does not support TIMESTAMPTZ, does not honour @SQLRestriction nor the @SQLDelete pattern with NOW(), and some Specifications with compound JOINs are translated to different SQL. A test that passes against H2 does not guarantee it will pass against Postgres. Verifying against H2 verifies H2, not the production database.
Native Postgres is discarded on reproducibility grounds. One developer might have postgres:14, another postgres:16, and the CI pipeline might not have it installed at all. Tests would depend on machine state, not on the repository.
Testcontainers with postgres:14 (fixed tag, not latest) resolves both problems: real Postgres SQL, and an identical image in dev and CI. The container startup cost per test class (~10–15 s locally, slightly more on CI the first time due to image pull) is accepted.
Test annotation for the slice
@SpringBootTest boots the full context — all @Configuration classes, all @Component beans, security filters active — to verify a single repository. It is slow by design and mixes layers: a failing test could be caused by security wiring, web autoconfig, or mapper injection, not by the repository under test.
@DataJpaTest is the minimal slice: it loads only @Entity classes, Spring Data JPA repositories, and the JPA infrastructure (EntityManager, transactions). It excludes regular @Component beans and application @Configuration. In addition, every test method runs in its own transaction with automatic rollback at the end, which allows sharing the same Postgres container between tests without data collisions.
Auditing inside the slice
Audited entities use @CreatedDate, @LastModifiedDate, @CreatedBy, and @LastModifiedBy together with @EntityListeners(AuditingEntityListener.class). In production these fields are populated because AuditingConfig in main activates @EnableJpaAuditing and exposes the beans securityAuditorAware and auditingDateTimeProvider. Inside @DataJpaTest that configuration is not loaded — the slice excludes regular @Component beans and application @Configuration. Without auditing active, every INSERT on an audited entity fails with a NOT NULL constraint violation on created_at. The first iterations on this problem discarded incorrect routes (@Primary on beans, double @EnableJpaAuditing, override by bean name) before identifying the canonical pattern documented in the Decision section.
Reproducibility between dev and CI
Two developers cloning the repository and running the same command should get the same result. The CI pipeline should get the same result as a developer running locally. Without that property, a red build on CI is impossible to diagnose without access to the runner.
Reproducibility is guaranteed by three concrete decisions: (1) fixing the image tag (postgres:14, not postgres nor postgres:latest); (2) using @ServiceConnection on the @Container field of Testcontainers, which resolves the datasource wire-up without custom code and without depending on the environment; (3) keeping the build command identical between local and CI (./mvnw clean verify -B).

Decision
A fixed pattern is adopted for repository integration tests, with four decisions acting in concert: a real database via a container with a pinned image, a minimal slice with @DataJpaTest, auditing enabled inside the slice through a dedicated @TestConfiguration, and an identical build command between local and CI.
Database
Testcontainers with image postgres:14 (fixed tag, not latest). Testcontainers BOM 2.0.5 — the minimum version compatible with Docker Engine 29. One container per test class, declared as a static field annotated with @Container, not one per method — container startup is a cost paid once per class, and per-method transactions with rollback guarantee isolation without needing to recreate the infrastructure.
Test annotation
@DataJpaTest on the class, combined with @AutoConfigureTestDatabase(replace = Replace.NONE) to prevent Spring Boot from replacing the datasource with an in-memory H2 by default. @ServiceConnection on the @Container field — Spring Boot detects the container type (PostgreSQLContainer) and configures the datasource automatically, without wire-up code or @DynamicPropertySource.
Auditing inside the slice
A TestAuditorConfig marked as @TestConfiguration is created, containing:
The annotation @EnableJpaAuditing(auditorAwareRef = "testAuditorAware", dateTimeProviderRef = "testDateTimeProvider") on the class itself, pointing to local beans — not to the main beans.
A @Bean AuditorAware<Long> testAuditorAware() that returns a constant TEST_AUDITOR_ID (1L), exposed as public static final for reuse in asserts.
A @Bean DateTimeProvider testDateTimeProvider() that returns OffsetDateTime.now(ZoneOffset.UTC) on each call.
@Primary is not used — it adds nothing in a context where there are no main beans to compete with. The local names (testAuditorAware, testDateTimeProvider) are deliberately different from the main names (securityAuditorAware, auditingDateTimeProvider) to avoid future collisions if the slice ever ends up loading the main configuration.
Every integration test imports this configuration with @Import(TestAuditorConfig.class). The JPA listener is activated within the slice context, audited fields are populated on every persist, and TEST_AUDITOR_ID is written to created_by and last_modified_by — deterministic and verifiable in asserts.
Dev/CI reproducibility
Three elements combined: a pinned Postgres image tag (postgres:14, declared as a constant on the @Container), @ServiceConnection for the datasource wire-up, and an identical command between local and CI (./mvnw clean verify -B). The chosen runner for GitHub Actions is ubuntu-22.04, which ships with Docker preinstalled and started by default — no additional setup step is required in the pipeline.

Consequences
What is gained
SQL fidelity. Integration tests verify behaviour against real Postgres, not an approximated dialect. This includes TIMESTAMPTZ for timezone-aware auditing, @SQLDelete with NOW() for soft delete resolved at the database level, @SQLRestriction injected into the ON clause of JOINs, and partial unique indexes with expressions like LOWER(name). None of these behaviours can be verified correctly against H2.
Free test isolation. Automatic per-method rollback allows sharing the same Postgres container across all tests in a class without data collisions. There is no need to reset the database between tests, no need to randomise emails to avoid UNIQUE violations, no need to order tests to avoid implicit dependencies.
Dev/CI reproducibility. Two developers cloning the repository and running the same command get the same result. The CI pipeline runs the identical command and gets the same result. A red build on CI can be diagnosed locally without ambiguity.
Extensible pattern. Any new integration test added to the suite follows the same skeleton: @DataJpaTest + @Testcontainers + @Import(TestAuditorConfig.class). Format is not up for discussion in every PR.
Trade-offs explicitly accepted
Trade-off
Mitigation
Integration tests are slower than unit tests (~10–20 s of container startup per class).
Accepted in exchange for fidelity. Unit tests remain the majority of the suite.
Docker is required in dev to run integration tests.
Documented in README.md. Most Java developers already have it installed for other uses.
Conceptual complexity of the auditing pattern inside the slice.
This ADR and CLAUDE.md document the pattern. TestAuditorConfig serves as a working example.
A fixed Postgres tag requires manual updates in the future.
Accepted — it is the price of reproducibility. Updating is a conscious decision when the time comes.
When this decision would be revisited
If container startup starts to dominate CI pipeline time. Testcontainers supports reuse across classes with reuse=true, or sharing a single container across the whole suite. Not urgent today.
If Spring Boot introduces a canonical mechanism to activate auditing inside @DataJpaTest without needing a dedicated @TestConfiguration. Revisit the pattern then.
If the project migrates to a different database engine, the Testcontainers image is re-evaluated but the overall integration test strategy stands.

Alternatives considered
1. Overriding the main AuditorAware via @Primary on a same-named bean
   Declaring in TestAuditorConfig a @Bean AuditorAware<Long> with the same name as the @Component SecurityAuditorAware in main (securityAuditorAware), marked with @Primary, expecting Spring to pick it as the preferred candidate when the slice loaded the main configuration.
   Rejected. Inside the @DataJpaTest slice the main @Component is not loaded — the @TypeExcludeFilters(DataJpaTypeExcludeFilter.class) annotation excludes regular components. There is therefore no bean to compete with, and @Primary adds nothing. Worse, keeping identical names between main and test leaves a latent time bomb: if the context ever evolves (for instance, migrating to @SpringBootTest for a different kind of integration test that does load main), having two beans with the same name triggers BeanDefinitionOverrideException because since Spring Boot 2.1 the default is spring.main.allow-bean-definition-overriding=false.
2. Double @EnableJpaAuditing (main + test)
   Keeping the @EnableJpaAuditing already present in main AuditingConfig and adding a new one in TestAuditorConfig pointing to test beans, expecting Spring to merge both activations with the more specific one prevailing.
   Rejected. @EnableJpaAuditing is not a bean — it is a configuration annotation that internally activates the AuditingBeanFactoryPostProcessor and registers auditing infrastructure with parameters. There is no resolution "by primary" or "by override" between two @EnableJpaAuditing annotations: two activations in the same context produce double registration, with symptoms such as "No bean named 'testAuditingDateTimeProvider' available" during bootstrap. The framework is not designed for composition of two @EnableJpaAuditing on the same context.
3. H2 in-memory with spring.jpa.hibernate.ddl-auto=create-drop
   Skipping Testcontainers altogether and using H2 in-memory, delegating schema creation to Hibernate in create-drop mode. This is the fastest route and the one that appears in most tutorials.
   Rejected. H2 does not speak the Postgres dialect. TIMESTAMPTZ, @SQLRestriction, @SQLDelete with SQL literals like NOW(), and partial unique indexes with expressions (UNIQUE (user_id, LOWER(name))) do not behave the same way — some are not supported at all. A green test against H2 does not guarantee the same outcome against Postgres, and does not verify what ADR-002 asks to be verified: real behaviour of the persistence layer against the production database. The speed gain does not offset the loss of fidelity for the purpose of the test.
4. Testcontainers with @DynamicPropertySource instead of @ServiceConnection
   Spinning up the PostgreSQLContainer and manually exposing its properties (getJdbcUrl(), getUsername(), getPassword()) to Spring via @DynamicPropertySource. This was the canonical pattern before Spring Boot 3.1.
   Rejected. @ServiceConnection (Spring Boot 3.1+) does the same thing without wire-up code: Spring Boot detects the container type (PostgreSQLContainer, MongoDBContainer, KafkaContainer, etc.) and configures the connection automatically. @DynamicPropertySource is still valid for properties that do not come from a known container, but for our concrete case it is verbose without adding anything. Documented as a recognised alternative in case a container outside Spring Boot's auto-detection catalog is introduced in the future.

Out of scope
Some test scenarios are explicitly left outside the scope of this strategy, either because another layer covers them better or because they add no coverage of a distinct contract:
Tests of global cross-cutting mechanisms (validation, JSON serialization, security filters). These live in @WebMvcTest or @SpringBootTest where appropriate — not in the persistence slice. Putting validation into a repository integration test blurs what is being verified.
Tests of service-layer business rules against a real database. These are the responsibility of unit tests with Mockito (ADR-002). An integration test that reproduces business rules against real Postgres is slow and fails for reasons a unit test would catch earlier.
MapStruct mapping behaviour verification. Mappers have their own strategy (ADR-002). A repository integration test should not verify entity↔DTO mapping — only persistence and queries.
Coverage of fixtures or factories. TestDataBuilder classes (aUser(), aTask(), aCategory()) are test code, not production code. They are not counted in JaCoCo metrics.
The common criterion: each integration test verifies persistence contract (entity↔schema mapping, Specifications translated to real SQL, ORM behaviour). Everything else belongs to another layer or to another type of test.

References
Implementation: com.mtole.taskmanager.tasks.TaskRepositoryIT as the first reference integration test. Pattern replicable for CategoryRepositoryIT, etc.
Test configuration: com.mtole.taskmanager.config.TestAuditorConfig.
Application properties for tests: src/test/resources/application.yml with SQL logging enabled (hibernate.SQL=DEBUG, hibernate.orm.jdbc.bind=WARN).
CI pipeline: .github/workflows/ci.yml, step Build and test runs ./mvnw clean verify -B.
Related decisions: ADR-001 — Polyglot Persistence, ADR-002 — Layered Testing Strategy.

ADRs are append-only by convention. If this decision is reversed or superseded, do not edit this file — open a new ADR that links back here and explains why.
