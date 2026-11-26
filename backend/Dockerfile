# Imagen de AutoReel (fase 8): el backend (Java 21) con FFmpeg, y el frontend compilado, que sirve el mismo backend
# en el mismo origen (ConfiguracionFrontend). Para una instancia EC2 x86_64 detrás del balanceador de AWS.
# El contexto es la raíz del repositorio (lo que entra lo define /.dockerignore):
#
#   docker build --platform linux/amd64 -f backend/Dockerfile -t autoreel-backend:latest .

# Etapa 1: el frontend. Las dependencias van en una capa aparte: un cambio en el código no vuelve a instalarlas.
FROM node:24-trixie-slim AS frontend
WORKDIR /fuente
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# Etapa 2: el jar del backend. El repositorio local de Maven queda en una caché de BuildKit entre construcciones.
FROM eclipse-temurin:21-jdk-noble AS backend
WORKDIR /fuente
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN --mount=type=cache,target=/root/.m2 chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package \
    && cp target/autoreel-*.jar /fuente/autoreel.jar

# Etapa 3: ejecución. Debian trixie trae FFmpeg 7.1 (drawtext con textfile, expansion=none y y_align, xfade,
# zoompan, libx264 y AAC) y OpenJDK 21 con AWT sin pantalla, que mide los textos con las fuentes del nicho.
FROM debian:trixie-slim AS ejecucion

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        openjdk-21-jre-headless \
        ffmpeg \
        fontconfig \
        fonts-dejavu-core \
        ca-certificates \
        curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system autoreel \
    && useradd --system --gid autoreel --home-dir /app --shell /usr/sbin/nologin autoreel \
    && mkdir -p /app \
    && chown autoreel:autoreel /app

WORKDIR /app
COPY --from=backend /fuente/autoreel.jar /app/autoreel.jar
COPY --from=frontend /fuente/dist /app/static

# El frontend se sirve desde /app/static (variable de entorno: los archivos yml no cambian).
ENV LANG=C.UTF-8 \
    TZ=UTC \
    SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/static/

USER autoreel
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=180s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# Memoria pensada para una t3.medium (4 GB): el 60 % para la JVM y el resto para FFmpeg y el sistema.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-XX:+ExitOnOutOfMemoryError", "-Djava.awt.headless=true", \
    "-jar", "/app/autoreel.jar"]
