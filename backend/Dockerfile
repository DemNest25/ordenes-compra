# ---------- Etapa 1: compilar ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Primero solo el pom: si no cambian las dependencias, Docker reutiliza esta capa
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Los tests necesitan PostgreSQL: se corren en local con docker compose, no aquí
RUN mvn -B -q -DskipTests package

# ---------- Etapa 2: ejecutar (solo JRE, sin Maven ni código fuente) ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /app/target/*.jar app.jar
USER app
ENV TZ=America/Guatemala
EXPOSE 8080
# Plan gratuito de Render = 512 MB: heap acotado y Serial GC (menos memoria fija que G1)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-XX:+UseSerialGC", "-Xss512k", \
            "-Duser.timezone=America/Guatemala", "-jar", "app.jar"]
