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
BCrypt (`BCryptPasswordEncoder`). Sin parámetros de coste explícito (usa el default de Spring).

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

---

## 7. Soft delete

### Implementación
`@SQLDelete` sobreescribe el DELETE de Hibernate con un UPDATE que pone `deleted_at = NOW()`. El `WHERE` incluye `AND version = ?` para respetar el optimistic lock.

`@SQLRestriction("deleted_at IS NULL")` añade el filtro a todas las queries generadas por Hibernate para esa entidad (incluidas las relaciones).

```java
@SQLDelete(sql = "UPDATE tasks SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
```

### Entidades con soft delete
- `Task`
- `Category`

`User` **no** tiene soft delete.

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
