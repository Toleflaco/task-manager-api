# ---- Etapa 1: Builder ----
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
COPY src/ src/
RUN ./mvnw package -DskipTests -B

# ---- Etapa 2: Runtime ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system --gid 1001 appuser \
 && useradd  --system --uid 1001 --gid appuser --home-dir /app --shell /usr/sbin/nologin appuser
USER appuser
COPY --from=builder /build/target/task-manager-api-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
