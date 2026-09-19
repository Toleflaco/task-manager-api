# Task Manager API

REST API para gestión de tareas con autenticación JWT, persistencia poliglota (PostgreSQL + MongoDB), y operaciones CRUD con filtering y pagination.

## Stack técnico

- **Java 21**
- **Spring Boot 4.0** — Spring Security 6, Spring Data JPA, Spring Data MongoDB
- **PostgreSQL** — datos transaccionales (tareas, usuarios)
- **MongoDB** — log de actividad inmutable
- **JWT (HS256)** — autenticación con refresh tokens de rotación single-use
- **Bucket4j** — rate limiting
- **Docker Compose** — infraestructura local
- **GitHub Actions** — CI/CD con Testcontainers
- **JUnit 5 + Mockito** — 51 unit tests + 6 integration tests
- **JaCoCo** — quality gates (LINE 0.85 / BRANCH 0.80)
- **Terraform** — IaC para AWS (EC2 + RDS)

## Arquitectura

```
┌────────────────────┐
│   Cliente REST     │
└────────┬───────────┘
         │ JWT Bearer Token
         v
┌────────────────────────────────────────┐
│         API REST (Spring Boot)         │
│  ├─ Spring Security (JWT validation)   │
│  ├─ DTO projections (JPA)              │
│  ├─ Auditoría automática               │
│  └─ Soft delete + Optimistic locking   │
└────────┬───────────────────────────────┘
         │
      ┌──┴──────────────────────────┐
      │                             │
      v                             v
┌──────────────────┐      ┌──────────────────┐
│   PostgreSQL     │      │    MongoDB       │
│ (transaccional)  │      │  (audit logs)    │
│ • users          │      │  • activity      │
│ • tasks          │      │  • immutable     │
│ • audit_trail    │      └──────────────────┘
└──────────────────┘
```

## Quick Start

### Requisitos

- Java 21+
- Maven 3.9+
- Docker y Docker Compose
- Git

### Instalación local (con Docker)

1. Clona el repositorio:
   ```bash
   git clone https://github.com/Toleflaco/task-manager-api.git
   cd task-manager-api
   ```

2. Copia el archivo de configuración de ejemplo:
   ```bash
   cp .env.example .env
   ```

   Edita `.env` si necesitas cambiar puertos o credenciales:
   ```
   POSTGRES_USER=taskmanager
   POSTGRES_PASSWORD=secret
   POSTGRES_DB=taskmanager_db
   MONGO_INITDB_ROOT_USERNAME=taskmanager
   MONGO_INITDB_ROOT_PASSWORD=secret
   JWT_SECRET=tu-secret-key-muy-largo-y-complejo-aqui
   ```

3. Levanta PostgreSQL + MongoDB + Redis:
   ```bash
   docker compose up -d
   ```

4. Arranca la aplicación:
   ```bash
   ./mvnw spring-boot:run
   ```

5. Verifica que está UP:
   ```bash
   curl http://localhost:8080/actuator/health
   ```

### Instalación local (sin Docker)

Si prefieres PostgreSQL + MongoDB locales en lugar de contenedores:

1. Crea base de datos PostgreSQL:
   ```bash
   psql -U postgres -c "CREATE DATABASE taskmanager_db;"
   ```

2. Inicia MongoDB local:
   ```bash
   mongod
   ```

3. Edita `src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/taskmanager_db
   spring.datasource.username=postgres
   spring.datasource.password=your-password
   spring.data.mongodb.uri=mongodb://localhost:27017/taskmanager
   spring.jpa.hibernate.ddl-auto=update
   ```

4. Arranca la app:
   ```bash
   ./mvnw spring-boot:run
   ```

### Pruebas de endpoints

Todos los endpoints requieren JWT. Primero regístrate:

```bash
# Registro
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Password123!"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Password123!"}'
```

Copia el token de la respuesta y úsalo:

```bash
TOKEN="eyJhbGc..."

# Crear tarea
curl -X POST http://localhost:8080/api/tasks \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Implementar login","description":"OAuth2 + Keycloak"}'

# Listar tareas (con pagination)
curl "http://localhost:8080/api/tasks?page=0&size=10&sort=createdDate,desc" \
  -H "Authorization: Bearer $TOKEN"

# Obtener tarea por ID
curl http://localhost:8080/api/tasks/{taskId} \
  -H "Authorization: Bearer $TOKEN"

# Actualizar tarea
curl -X PUT http://localhost:8080/api/tasks/{taskId} \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Implementar logout"}'

# Eliminar tarea (soft delete)
curl -X DELETE http://localhost:8080/api/tasks/{taskId} \
  -H "Authorization: Bearer $TOKEN"
```

### Tests

Ejecuta la suite de tests (unit + integration con Testcontainers):

```bash
./mvnw verify
```

Coverage JaCoCo:

```bash
open target/site/jacoco/index.html
```

## Características principales

### Autenticación y Autorización

- **JWT (HS256)** — tokens sin estado
- **Refresh tokens con rotación single-use** — detección de reuso por familia de tokens
- **Auditoría automática** — @CreatedDate, @LastModifiedDate, @CreatedBy en todas las entidades
- **Soft delete** — tareas nunca se eliminan, solo se marcan como deleted

### Persistencia poliglota

- **PostgreSQL** — datos transaccionales (tareas, usuarios, logs auditables)
- **MongoDB** — log de actividad inmutable, sin joins, optimizado para lectura temporal

### Resiliencia y calidad

- **JPA Specifications** — queries dinámicas sin Query DSL
- **Optimistic locking** — previene lost updates en concurrencia
- **DTO projections** — fetching selectivo de columnas (N+1 prevention)
- **Bucket4j** — rate limiting configurable por endpoint
- **JaCoCo** — quality gates enforzadas (LINE 0.85 / BRANCH 0.80)

### CI/CD

- **GitHub Actions** — build, tests, Docker image push
- **Testcontainers** — integration tests con PostgreSQL + MongoDB reales
- **Quality gates** — JaCoCo + SonarQube ready

## Decisiones arquitectónicas

### Package-by-Feature

```
src/main/java/com/taskmanager/
├── task/
│   ├── domain/
│   │   ├── Task.java
│   │   └── TaskRepository.java
│   ├── application/
│   │   ├── TaskService.java
│   │   └── TaskDTO.java
│   └── presentation/
│       └── TaskController.java
├── auth/
│   ├── domain/
│   ├── application/
│   └── presentation/
└── shared/
    ├── security/
    ├── error/
    └── audit/
```

Ventaja: cambios en un feature no rozan otros packages.

### Auditoría inmutable en MongoDB

Cada acción (create, update, delete) genera un documento en `activity_log` de MongoDB:

```json
{
  "_id": ObjectId(...),
  "aggregateId": "task-123",
  "eventType": "TaskCreated",
  "timestamp": ISODate(...),
  "userId": "user-456",
  "payload": {
    "title": "Implementar login",
    "description": "OAuth2"
  }
}
```

Nunca se modifica ni se borra. Fuente de verdad para auditoría legal y trazabilidad.

### Refresh tokens con rotación single-use

Cada refresh genera un nuevo par (access + refresh). El refresh antiguo se invalida:

```
1. POST /auth/login → {accessToken, refreshToken}
2. POST /auth/refresh → {accessToken: NEW, refreshToken: NEW}
3. Intento reutilizar el refresh antiguo → 401 Unauthorized
   (señal de posible token theft)
```

Detalles en `AuthService.java`.

## Despliegue en AWS (Terraform)

Infraestructura IaC en `terraform/`:

```hcl
# EC2 para la app (t3.micro, free tier)
resource "aws_instance" "app" {
  ami = "ami-0c55b159cbfafe1f0" # Ubuntu 22.04 LTS
  instance_type = "t3.micro"
}

# RDS PostgreSQL (db.t3.micro, free tier)
resource "aws_db_instance" "postgres" {
  engine = "postgres"
  instance_class = "db.t3.micro"
  allocated_storage = 20
}
```

Deploy:

```bash
cd terraform
terraform init
terraform plan
terraform apply
```

## Roadmaps relacionados

- **[Cloud Roadmap](https://github.com/Toleflaco/cloud-roadmap)** — AWS, Kubernetes, OAuth2, Observabilidad
- **[AI Engineer Roadmap](https://github.com/Toleflaco/ai-engineer-roadmap-java)** — Spring AI, agentes, RAG

---

*Última actualización: 2026-09-20*
