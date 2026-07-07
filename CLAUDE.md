# CLAUDE.md — Task Manager API

Context file for Claude Code. Read this before making any changes to the codebase.

---

## 1. Stack tecnológico

| Componente | Versión |
|---|---|
| Java | 21 (preview features habilitados en `maven-compiler-plugin`) |
| Spring Boot | 4.0.6 |
| Spring Security | incluido en Spring Boot 4.0.6 |
| Spring Data JPA + Hibernate | incluido en Spring Boot 4.0.6 |
| PostgreSQL (driver JDBC) | runtime, versión gestionada por Boot |
| Flyway | `spring-boot-starter-flyway` + `flyway-database-postgresql` |
| jjwt | 0.12.6 (api + impl + jackson) |
| Bucket4j | 8.10.1 |
| MapStruct | 1.6.3 (procesador de anotaciones en `maven-compiler-plugin`) |
| SpringDoc OpenAPI (Swagger UI) | 3.0.3 |
| Build | Maven Wrapper (`./mvnw`) |

**Sin Lombok.** El proyecto no usa Lombok. Getters, setters y constructores son siempre manuales.

---

## 2. Convenciones de código

### DTOs: Java records
Todos los DTOs son `record`, sin excepción. No usar clases con Lombok ni builders.

```java
// Correcto
public record TaskCreateRequest(@NotBlank String title, ...) {}

// Incorrecto
@Data public class TaskCreateRequest { ... }
```

### Entidades: clases con constructor `protected`
Las entidades JPA son clases ordinarias. El constructor sin argumentos es `protected` (requisito de JPA, no de uso externo).

### Campos infra-managed: sin setter público
Los campos gestionados por infraestructura (`id`, `createdAt`, `updatedAt`, `version`, `createdBy`, `lastModifiedBy`, `deletedAt`) **no tienen setter público**. Solo tienen getter. El setter de `id` existe en las entidades actuales por razones de bootstrap de JPA, pero los campos de auditoría no tienen setter en absoluto.

### MapStruct: política ERROR en campos no mapeados
Los mappers usan `unmappedTargetPolicy = ReportingPolicy.ERROR` y `componentModel = "spring"`. Cualquier campo nuevo en una entidad debe añadirse explícitamente al mapper (mapeado o ignorado con `@Mapping(target = "...", ignore = true)`). Los campos infra-managed se ignoran siempre en los mappers de entrada.

### equals / hashCode en entidades
Basado únicamente en `id`. Si `id == null`, los objetos no son iguales entre sí. Patrón JPA estándar:

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Task other)) return false;
    return id != null && id.equals(other.id);
}
@Override
public int hashCode() { return getClass().hashCode(); }
```

### Inyección de dependencias
Constructor injection en todas las clases. Sin `@Autowired` en campos.

---

## 3. Arquitectura

### Package-by-feature (vertical slice)

```
com.mtole.taskmanager/
├── auth/           → login, refresh token, JWT
├── users/          → registro y gestión de usuarios
├── tasks/          → CRUD, filtros, estadísticas, transiciones de estado
│   └── dto/        → TaskCreateRequest, TaskUpdateRequest, TaskResponse, ...
├── categories/     → CRUD de categorías
│   └── dto/
├── security/       → JwtFilter, RateLimitingFilter, SecurityConfig, JwtService
├── config/         → AuditingConfig, SecurityAuditorAware
└── common/         → GlobalExceptionHandler, ResourceNotFoundException, PagedResponse
```

### Capas por feature
`Controller → Service → Repository → Entity`

- **Controller**: validación de entrada (`@Valid`), obtención del `currentUserId` del contexto de seguridad, delegación al Service, mapeo a DTO de respuesta.
- **Service**: lógica de negocio, transaccionalidad, comprobaciones de propiedad (IDOR), lanzamiento de excepciones de dominio.
- **Repository**: interfaces Spring Data JPA. Queries derivadas o JPQL. Sin lógica de negocio.
- **Entity**: estado persistido. Sin lógica de negocio salvo invariantes de construcción.

### DTOs separados de entidades
Las entidades nunca salen del service. El controller recibe y devuelve siempre DTOs. MapStruct gestiona la conversión.

---

## 4. Persistencia

### ddl-auto: validate
`spring.jpa.hibernate.ddl-auto=validate`. Hibernate **nunca** modifica el schema. Solo valida que las entidades casan con las tablas. El schema lo gestiona exclusivamente Flyway.

### Flyway: convención de naming
```
src/main/resources/db/migration/V{N}__{descripcion_en_snake_case}.sql
```
Ejemplos reales: `V1__create_users.sql`, `V8__add_deleted_at_to_soft_deletable_tables.sql`.
- `V` mayúscula, número secuencial, doble guion bajo, snake_case, extensión `.sql`.
- Cada migración es aditiva. Nunca modificar una migración ya aplicada.

### Patrón de migraciones aditivas en tres tiempos
Para añadir columnas `NOT NULL` a tablas con datos existentes, el patrón es:

```sql
-- 1. Añadir como nullable
ALTER TABLE tasks ADD COLUMN created_at TIMESTAMP;
-- 2. Backfill con valor sensato
UPDATE tasks SET created_at = NOW() WHERE created_at IS NULL;
-- 3. Fijar NOT NULL
ALTER TABLE tasks ALTER COLUMN created_at SET NOT NULL;
```

Migraciones que usan este patrón: `V5` (timestamps de auditoría), `V6` (`version`), `V7` (`created_by` / `last_modified_by`). `V8` (`deleted_at`) **no** lo usa porque la columna es nullable por diseño.

### open-in-view: false
`spring.jpa.open-in-view=false`. Decisión explícita desde el inicio para evitar N+1 ocultos y conexiones abiertas durante la serialización. Nunca reactivar.

### Sin cascades JPA
No se usan `cascade = CascadeType.*` en las relaciones `@OneToMany` ni `@ManyToOne`. Las restricciones de integridad referencial están definidas en el schema SQL:
- `fk_tasks_user`: `ON DELETE CASCADE` (si se borra un usuario, se borran sus tareas).
- `fk_tasks_category`: `ON DELETE SET NULL` (si se borra una categoría, las tareas quedan sin categoría).

### Transaccionalidad
- `@Transactional` en métodos de escritura del service.
- `@Transactional(readOnly = true)` en métodos de solo lectura.
- Los repositories no llevan `@Transactional` propio salvo necesidad explícita.

### getReferenceById vs findById
Para asignar relaciones de FK sin cargar el objeto completo, se usa `repository.getReferenceById(id)` (proxy de Hibernate). Solo se usa `findById` cuando se necesita leer campos del objeto.

En `TaskService` y `CategoryService`, se usa `userRepository.getReferenceById(currentUserId)` para asignar el user a la nueva entidad sin disparar un `SELECT`. El proxy de Hibernate solo carga el objeto si se accede a sus campos; como solo se usa como FK (`setUser(proxy)`), no genera `SELECT` — solo guarda el id al hacer flush.

**No usar** `findById(id).get()` para asignar FKs: introduce un `SELECT` innecesario en cada creación.

---

## 5. Seguridad

### JWT stateless
- Sesión HTTP: `STATELESS`. No hay cookies ni sesiones en servidor.
- Filtro: `JwtAuthenticationFilter` se ejecuta antes de `UsernamePasswordAuthenticationFilter`.
- El `userId` (Long) va embebido en el claim del access token.
- Duraciones: producción `15m` (access) / `7d` (refresh). Dev override: `60m` (access).

### Refresh tokens con rotación y detección de reuso
- Los refresh tokens se persisten en la tabla `refresh_tokens` con un `familyId` (UUID).
- Cada uso genera un token nuevo (rotación). El token anterior se marca `revoked = true`.
- Si se presenta un token ya revocado → **toda la familia queda revocada** (detección de reuso/robo) → 401.

### IDOR: cierre en el repository
Toda query de recurso individual incluye el `userId` del usuario autenticado:
```java
taskRepository.findByIdAndUserId(id, currentUserId)
categoryRepository.findByIdAndUserId(id, currentUserId)
```
Nunca hacer `findById(id)` y verificar la propiedad después. El `currentUserId` se obtiene siempre de `SecurityUtils.currentUserId()` (del SecurityContext), nunca del path o del body.

### Rate limiting
`RateLimitingFilter` (Bucket4j) protege `POST /auth/login`: 5 tokens en ráfaga, recarga 1 token/minuto. Configurado en `application.yml` bajo `rate-limit.login`.

### Endpoints públicos
```
POST /users
POST /auth/login
POST /auth/refresh
GET  /swagger-ui/**
GET  /v3/api-docs/**
```
Todo lo demás requiere JWT válido.

### Contraseñas
BCrypt (`BCryptPasswordEncoder`). Se instancia con `new BCryptPasswordEncoder()` sin argumentos, lo que aplica el coste default de Spring: **10 vueltas (2¹⁰ = 1024 iteraciones)**. No modificar sin medir el impacto en latencia de login.

---

## 6. Auditoría y campos infra-managed

Los siguientes campos son **propiedad exclusiva de la infraestructura**. Nunca se reciben del cliente, nunca se mapean desde un DTO de entrada, y no tienen setter público.

| Campo | Mecanismo | Entidades |
|---|---|---|
| `id` | `@GeneratedValue(IDENTITY)` | Todas |
| `createdAt` | `@CreatedDate` (JPA Auditing) | Task, Category, User |
| `updatedAt` | `@LastModifiedDate` (JPA Auditing) | Task, Category, User |
| `version` | `@Version` (optimistic locking) | Task, Category, User |
| `createdBy` | `@CreatedBy` → `SecurityAuditorAware` | Task, Category |
| `lastModifiedBy` | `@LastModifiedBy` → `SecurityAuditorAware` | Task, Category |
| `deletedAt` | `@SQLDelete` | Task, Category |

`SecurityAuditorAware` implementa `AuditorAware<Long>` y devuelve el `userId` del `SecurityContext`.

En los mappers, todos estos campos aparecen con `@Mapping(target = "...", ignore = true)` en los métodos de entrada (`toEntity`, `updateFromRequest`).

### Optimistic locking
El cliente debe enviar `version` en las peticiones de actualización (`TaskUpdateRequest`, etc.). El service valida manualmente que `request.version().equals(existing.getVersion())` antes de persistir. Si no coinciden, lanza `OptimisticLockingFailureException` → 409.

### Decisiones específicas sobre el campo `version`
- **Tipo `Long` (objeto), no `long` (primitivo).** Permite `null` antes del primer INSERT; si fuera primitivo, Hibernate no podría distinguir "sin versión" de "versión 0".
- **Sin inicialización en la declaración** (`private Long version;`, no `= 0L`). Hibernate lo gestiona.
- **Sin setter público.** La autoridad sobre `version` es Hibernate vía dirty-checking.
- **El `@SQLDelete` no incrementa `version`** (el SQL no incluye `SET version = version + 1`). La versión queda con su valor previo tras el soft delete. Decisión consciente: en el modelo actual no hay flujo de restauración con modificaciones intermedias.

### Por qué `User` está excluido de `@CreatedBy` y `@LastModifiedBy`
En `POST /users` (auto-registro), el `SecurityContext` está vacío — el usuario aún no se ha autenticado. `SecurityAuditorAware` devolvería `Optional.empty()` y, si `User` llevara `@CreatedBy` con columna `NOT NULL`, el `INSERT` fallaría.

Conceptualmente, un user no tiene "creador" en sentido auditoría: se crea a sí mismo. Por eso la tabla `users` no tiene columnas `created_by` ni `last_modified_by`, y la entidad `User` no lleva esas anotaciones. Decisión arquitectónica deliberada, no un oversight.

---

## 7. Soft delete

### Implementación
`@SQLDelete` sobreescribe el DELETE de Hibernate con un UPDATE que pone `deleted_at = NOW()`. El `WHERE` incluye `AND version = ?` para respetar el optimistic lock.

`@SQLRestriction("deleted_at IS NULL")` añade el filtro a todas las queries generadas por Hibernate para esa entidad (incluidas las relaciones).

```java
@SQLDelete(sql = "UPDATE tasks SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
```

### Cómo borrar entidades soft-deletables
**Usar** derived queries (`deleteByXxx`) o `repository.delete(entity)` con entidad managed. Ambas pasan por el ciclo de vida JPA y disparan `@SQLDelete`, convirtiendo el DELETE en un UPDATE de `deleted_at`.

**Nunca usar** `@Modifying @Query("DELETE FROM X WHERE ...")` JPQL ni `deleteAllInBatch()`. Van directamente a SQL, saltan el ciclo de vida JPA y **no disparan `@SQLDelete`**: el registro se borraría físicamente en lugar de marcarse con `deleted_at`.

Regla práctica: si el código pasa por una entidad managed en algún punto, el ciclo de vida JPA se aplica. Si manda SQL directo a la BBDD, no.

**Excepción aceptable:** `taskRepository.disassociateFromCategory` usa `@Modifying @Query` JPQL deliberadamente para hacer un `UPDATE` en masa (no un `DELETE`). Está bien: no está borrando entidades, está actualizando una FK a `null`.

### Entidades con soft delete
- `Task`
- `Category`

`User` **no** tiene soft delete: no tiene columna `deleted_at`, ni `@SQLDelete`, ni `@SQLRestriction`. El ciclo de vida del user es distinto — GDPR (Right to be Forgotten) requiere hard delete real, no marcado lógico. Si en el futuro se añade un endpoint de cancelación de cuenta, será una operación separada del DELETE habitual.

### Disociación de tasks al borrar Category
Antes de hacer soft delete de una categoría, el service llama a:
```java
taskRepository.disassociateFromCategory(categoryId); // UPDATE Task SET category = null WHERE category.id = ?
categoryRepository.deleteByIdAndUserId(id, currentUserId);
```
Esto evita que tareas vivas queden con una referencia a una categoría borrada. Todo en la misma transacción.

---

## 8. Manejo de errores

### RFC 7807 ProblemDetail
Todas las respuestas de error usan `ProblemDetail` (nativo en Spring 6+). El `GlobalExceptionHandler` (`@RestControllerAdvice`) centraliza todos los casos.

Todos los `ProblemDetail` incluyen la propiedad extra `timestamp` (OffsetDateTime UTC).

### Mapa de excepciones

| Excepción | HTTP | Título |
|---|---|---|
| `ResourceNotFoundException` | 404 | Resource Not Found |
| `MethodArgumentNotValidException` | 400 | Invalid Data (+ mapa `fields`) |
| `InvalidTaskStateException` | 409 | Invalid Task State |
| `BadCredentialsException` | 401 | Authentication failed |
| `InvalidRefreshTokenException` | 401 | Invalid Refresh Token |
| `DuplicateEmailException` | 409 | Email already registered |
| `DataIntegrityViolationException` (duplicate key) | 409 | Resource Conflict |
| `DataIntegrityViolationException` (otros) | 500 | Internal Server Error |
| `OptimisticLockingFailureException` | 409 | Conflict |
| `HttpMediaTypeNotSupportedException` | 415 | Unsupported Media Type |
| `MissingRequestHeaderException` | 400 | Missing Required Header |
| `Exception` (catch-all) | 500 | Internal Server Error |

### Logging en excepciones
- Errores esperados y de negocio: `log.warn(...)`.
- Errores inesperados: `log.error("...", ex)` (con stacktrace).
- Nunca loguear credenciales ni tokens.

---

## 9. Convenciones de commits

El proyecto sigue **Conventional Commits**:

```
<type>(<scope>): <descripción en imperativo, minúsculas>
```

**Types usados**: `feat`, `chore`, `docs`  
**Scopes usados**: `tasks`, `users`, `auth`, `categories`, `persistence`, `audit`, `soft-delete`, `config`, `build`

Ejemplos reales del historial:
```
feat(soft-delete): add soft delete to Task and Category with @SQLDelete and @SQLRestriction
feat(audit): add optimistic locking with @Version and client-driven conflict detection
chore(config): move JWT expiration to dev profile, prod defaults to 15m/7d
docs: rewrite README to reflect Phase 5.5 state
```

---

## 10. Comandos habituales

### Variables de entorno requeridas
```bash
export DB_PASSWORD=<password>
export JWT_SECRET=<secret-base64-256bits>
```

### Arrancar en dev local
```bash
./mvnw spring-boot:run
# El perfil 'dev' se activa por defecto (application.yml → spring.profiles.active=dev)
```

### Compilar y empaquetar
```bash
./mvnw clean package
```

### Ejecutar tests
```bash
./mvnw test
```

### Migraciones Flyway
Se ejecutan automáticamente al arrancar la aplicación. No hay comando manual separado. Los scripts están en:
```
src/main/resources/db/migration/
```

### Puertos y URLs locales
| Recurso | URL |
|---|---|
| API | `http://localhost:8081` |
| Swagger UI | `http://localhost:8081/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8081/v3/api-docs` |

### Ubicaciones importantes
| Qué | Dónde |
|---|---|
| Configuración base | `src/main/resources/application.yml` |
| Configuración dev | `src/main/resources/application-dev.yml` |
| Migraciones SQL | `src/main/resources/db/migration/` |
| Punto de entrada | `com.mtole.taskmanager.TaskManagerApiApplication` |
| Handler de errores | `com.mtole.taskmanager.common.GlobalExceptionHandler` |
| Configuración seguridad | `com.mtole.taskmanager.security.SecurityConfig` |

---

## 11. Testing

### 11.1 Unit tests (Fase 8)

**Stack y convenciones generales**

JUnit 5 + Mockito + AssertJ. La sintaxis de Mockito es siempre BDDMockito (`given(…).willReturn(…)`, `then(…).should(…)`). El stubbing se escribe únicamente cuando el valor de retorno se consume; no hay stubs de cortesía para silenciar interacciones no relevantes para el test.

Naming de métodos: `scenarioUnderTest_expectedResult` (`saveTask_persistsTaskWithAuditingFieldsPopulated`, `deleteCategory_disassociatesTasksBeforeDelete`). Los tests relacionados se agrupan con `@Nested`. Los ficheros de referencia son `TaskServiceTest.java`, `CategoryServiceTest.java` y `TaskMapperTest.java`.

**Test data builders**

Hay un builder por entidad raíz: `UserTestDataBuilder`, `TaskTestDataBuilder`, `CategoryTestDataBuilder` y `RefreshTokenTestDataBuilder`. Todos exponen una static factory (`aUser()`, `aTask()`, `aCategory()`, `aRefreshToken()`), tienen defaults sensatos y son fluent:

```java
User user = aUser().withEmail("other@example.com").build();
Task task  = aTask().withUser(user).withStatus(COMPLETED).build();
```

Los builders son código de test; no se cuentan en las métricas de JaCoCo ni deben tener tests propios.

**Patrones clave en service tests**

_ArgumentCaptor_ — se usa para capturar los argumentos que el service pasa a sus colaboradores y verificar su contenido sin necesidad de igualdad por referencia. Los usos habituales son eventos publicados (`ArgumentCaptor<TaskCreatedEvent>`) y `Specification<Task>` capturada antes de pasarla al repositorio:

```java
ArgumentCaptor<Specification<Task>> specCaptor = ArgumentCaptor.forClass(Specification.class);
then(taskRepository).should().findAllSummariesBy(specCaptor.capture(), eq(pageable));
Specification<Task> captured = specCaptor.getValue();
```

_RETURNS\_DEEP\_STUBS_ — `Root<Task>` y `CriteriaBuilder` se mockean con `RETURNS_DEEP_STUBS` para ejercer la lógica de una `Specification` directamente sin arrancar un contexto JPA:

```java
Root<Task> root = mock(Root.class, RETURNS_DEEP_STUBS);
CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
```

_InOrder_ — en `CategoryServiceTest` se verifica con `InOrder` que `taskRepository.disassociateFromCategory(categoryId)` se llama **antes** que `categoryRepository.delete(entity)`. Este orden es una invariante de integridad referencial: sin la disociación previa, las tareas vivas quedarían con una FK a una categoría borrada.

```java
InOrder inOrder = inOrder(taskRepository, categoryRepository);
inOrder.verify(taskRepository).disassociateFromCategory(categoryId);
inOrder.verify(categoryRepository).delete(existingCategory);
```

**Mapper tests**

Los mappers se instancian directamente con `Mappers.getMapper(TaskMapper.class)`. Sin mocks, sin Spring, sin contexto. Es una instancia real del mapper generado por MapStruct: verifica la transformación, no un stub.

**JaCoCo**

La cobertura se mide con granularidad `PACKAGE`. Los tres packages en scope son `com.mtole.taskmanager.tasks`, `com.mtole.taskmanager.categories` y `com.mtole.taskmanager.auth`. Los umbrales son LINE ≥ 0.85 y BRANCH ≥ 0.80. `haltOnFailure=false` es deuda técnica consciente, documentada en `pom.xml`:

> *"haltOnFailure kept false until coverage reaches honest thresholds. Do not flip without also raising Controller and category-service coverage. See ADR-002."*

No se persigue cobertura en: null guards generados por MapStruct (`if (x == null) return null`), ramas finales simétricas del state machine de tasks, clases de configuración / `main`, ni `equals`/`hashCode` de records. El criterio es: una línea verde que no protege un contrato nuevo no vale la pena.

---

### 11.2 Integration tests de repositorio (Fase 8.5)

**Stack y anotaciones del slice**

```java
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestAuditorConfig.class)
class TaskRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14");
    ...
}
```

`@DataJpaTest` carga únicamente entidades JPA, repositorios Spring Data y la infraestructura JPA (EntityManager, transacciones). Excluye `@Component` normales y `@Configuration` del main. `@AutoConfigureTestDatabase(replace = Replace.NONE)` impide que Spring Boot reemplace el datasource por H2. `@ServiceConnection` en el contenedor le dice a Spring Boot que configure el datasource automáticamente a partir del `PostgreSQLContainer`, sin `@DynamicPropertySource`. Un contenedor por clase (campo `static`); el aislamiento entre tests se garantiza con rollback automático por método.

**Nota de paquetes (Spring Boot 4.x)** — las importaciones difieren de Boot 3.x:

| Clase | Paquete en Boot 4.x |
|---|---|
| `@DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `@AutoConfigureTestDatabase` | `org.springframework.boot.jdbc.test.autoconfigure` |
| `TestEntityManager` | `org.springframework.boot.jpa.test.autoconfigure` |

**Versión de Testcontainers**

Testcontainers BOM 2.0.5 es el mínimo compatible con Docker Engine 29 (que requiere API 1.44+). La versión 1.20.4 no arrancaba con ese engine.

**TestAuditorConfig**

`@DataJpaTest` no carga la `AuditingConfig` del main. Sin auditing activo, cada `INSERT` sobre una entidad auditada falla con violación `NOT NULL` en `created_at`. La solución es una `@TestConfiguration` dedicada que activa `@EnableJpaAuditing` con beans locales:

```java
@TestConfiguration
@EnableJpaAuditing(
        auditorAwareRef = "testAuditorAware",
        dateTimeProviderRef = "testDateTimeProvider"
)
public class TestAuditorConfig {

    public static final Long TEST_AUDITOR_ID = 1L;

    @Bean
    public AuditorAware<Long> testAuditorAware() {
        return () -> Optional.of(TEST_AUDITOR_ID);
    }

    @Bean
    public DateTimeProvider testDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
```

Sin `@Primary` (no hay beans del main con los que competir en el slice). Los nombres de bean (`testAuditorAware`, `testDateTimeProvider`) difieren deliberadamente de los del main (`securityAuditorAware`, `auditingDateTimeProvider`) para evitar colisiones si el contexto del test evolucionase. `TEST_AUDITOR_ID` es una constante pública reutilizable en los asserts para verificar `createdBy` y `lastModifiedBy`.

**Verificación de soft delete**

Los tests de soft delete combinan una native query para confirmar que la fila existe físicamente con `deleted_at != null`, y un `findById` para confirmar que `@SQLRestriction` la filtra:

```java
Object deletedAt = entityManager.getEntityManager()
        .createNativeQuery("SELECT deleted_at FROM tasks WHERE id = ?")
        .setParameter(1, taskId)
        .getSingleResult();
Optional<Task> foundViaOrm = taskRepository.findById(taskId);

assertThat(deletedAt).isNotNull();
assertThat(foundViaOrm).isEmpty();
```

**SecurityAuditorAware y el null-guard**

El guard del `SecurityContext` usa `||`, no `&&`:

```java
if (auth == null || !auth.isAuthenticated()) {
    return Optional.empty();
}
```

Esta es la forma correcta: salir si el objeto es nulo _o_ si no está autenticado. Con `&&` la segunda condición evaluaría sobre un `auth` que podría ser `null`, lanzando `NullPointerException`.

**Dev/CI parity**

El comando es idéntico en local y en GitHub Actions: `./mvnw clean verify -B`. El runner es `ubuntu-22.04`, que tiene Docker preinstalado y arrancado por defecto; no se necesita ningún step adicional en el pipeline. Testcontainers arranca la imagen `postgres:14` (tag fijo) en ambos entornos: dos desarrolladores que clonen el repositorio y ejecuten el mismo comando obtienen el mismo resultado.

Referencia: `docs/ADR-003 · Repository Integration Testing with @DataJpaTest and Testcontainers.md`.

---

## 12. Persistencia polyglot

### Separación de stores

El sistema usa dos bases de datos con responsabilidades disjuntas:

| Store | Versión / tier | Datos |
|---|---|---|
| PostgreSQL | 14 | `users`, `tasks`, `categories`, `refresh_tokens` — datos transaccionales, relacionales, mutables |
| MongoDB Atlas | M0 free tier (AWS Ireland) | `activity_events` — audit log append-only |

La elección se basa en el patrón de acceso de cada workload: los datos transaccionales necesitan integridad referencial, joins y transacciones ACID multi-fila; el audit log es append-only, con payloads heterogéneos por tipo de evento y consultado casi exclusivamente por rango de fechas del usuario. Cada base de datos resuelve exactamente el problema para el que fue diseñada.

### MongoConfig: workaround manual

Spring Boot 4.0.6 + driver mongo 5.6.5 tiene un bug en la autoconfiguración: la URI llega al contexto pero no se aplica al cliente. Además, al construir el cliente manualmente, Spring Data pierde la conexión con `spring.data.mongodb.database` y cae en su fallback hardcoded `"test"`. Por eso `MongoConfig` declara dos beans explícitamente:

```java
@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Bean
    public MongoClient mongoClient() {
        ConnectionString connectionString = new ConnectionString(mongoUri);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build();
        return MongoClients.create(settings);
    }

    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory(MongoClient client) {
        return new SimpleMongoClientDatabaseFactory(client, "taskmanager");
    }
}
```

Si en el futuro se actualiza Spring Boot o el driver Mongo y la autoconfiguración vuelve a funcionar correctamente, esta clase puede eliminarse y la URI inyectarse vía `spring.data.mongodb.uri` y la database vía `spring.data.mongodb.database` como es habitual.

**Scanning de repositorios** — para que Spring Data no mezcle repositorios JPA y Mongo, `RepositoryScanConfig` declara `@EnableJpaRepositories(basePackages = …)` y `@EnableMongoRepositories(basePackages = …)` apuntando a paquetes disjuntos.

### ActivityEvent como documento

```java
@Document(collection = "activity_events")
@CompoundIndex(name = "userId_1_timestamp_-1", def = "{'userId': 1, 'timestamp': -1}")
public class ActivityEvent {
    @Id private String id;          // asignado por MongoDB al insertar
    private Long userId;
    private String action;          // String, no enum — ver nota abajo
    private String resourceType;    // String, no enum — ver nota abajo
    private Long resourceId;
    private Map<String, Object> before;
    private Map<String, Object> after;
    private Instant timestamp;
    ...
}
```

`action` y `resourceType` son `String` en lugar de enum por una razón de forward compatibility: si en el futuro se renombra o elimina un tipo de acción, los documentos viejos en MongoDB siguen deserializándose correctamente. Un enum haría fallar la deserialización de cualquier evento cuyo valor ya no existiera en el código.

`before` y `after` son `Map<String, Object>` porque cada tipo de acción guarda campos distintos. Aprovechar la naturaleza document-store de MongoDB elimina la necesidad de migraciones cuando se añade un nuevo tipo de evento.

El documento es append-only e inmutable: no lleva @Version (no hay updates concurrentes que proteger) ni anotaciones de auditoría (el propio evento es el registro de auditoría).

### Modelo event-driven síncrono

El flujo es: service transaccional → `ApplicationEventPublisher.publishEvent(…)` → `@EventListener` en `ActivityEventListener` → `MongoRepository.save(event)`.

El listener corre síncronamente dentro de la transacción JPA del publicador. Esto tiene una consecuencia importante: si la escritura en Mongo falla, la excepción se propaga y la transacción JPA hace rollback. No se confirma la operación de negocio sin confirmar también el audit log.

La ventana de inconsistencia residual —Mongo escribe con éxito, luego el commit JPA falla— existe y es aceptada para la escala actual. El upgrade path si esta ventana se vuelve observable es el patrón transactional outbox.

### Compound index y query pattern

El índice `{'userId': 1, 'timestamp': -1}` cubre el query pattern dominante: *"dame la actividad de este usuario en este rango de fechas, ordenada de más reciente a más antigua"*. El `userId` viene siempre del JWT del request autenticado; no hay join con la tabla `users` en tiempo de lectura.

### Endpoints de activity

| Endpoint | Descripción |
|---|---|
| `GET /me/activity` | Listing paginado con Criteria dinámica (filtros opcionales por rango de fechas, tipo de acción, tipo de recurso) |
| `GET /me/activity/stats` | Aggregation con pipeline `$facet` para contar eventos agrupados por `action` en un rango de fechas |

### Trade-offs aceptados

| Trade-off | Mitigación |
|---|---|
| Sin join a nivel de motor entre `users` y `activity_events` | `userId` desnormalizado en cada evento desde el JWT. Si se necesita el nombre del usuario en la respuesta, se hace una segunda query a PostgreSQL. |
| Integridad referencial user→event no garantizada por el motor | Aceptado: un audit log debe preservar precisión histórica. Un hard delete de usuario no borra sus eventos; GDPR Right-to-Erasure se trataría con un proceso de anonimización separado. |
| Ventana de inconsistencia pequeña entre los dos stores | Aceptada a la escala actual. Upgrade path: transactional outbox. |
| Dos bases de datos que operar, respaldar y monitorizar | El coste operacional está explícitamente aceptado. La simplificación de cada modelo de datos compensa la superficie extra. |

Referencia: `docs/adr-001-polyglot-persistence.md`.
