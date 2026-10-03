# Stage 1: build the React frontend
FROM node:22-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# vite.config.ts outputs to ../src/main/resources/static (relative to frontend/)
RUN npm run build

# Stage 2: build the Spring Boot jar
FROM maven:3.9-eclipse-temurin-21-alpine AS backend
WORKDIR /app
COPY pom.xml ./
# Download dependencies as a separate layer so they are cached on source-only changes
RUN mvn dependency:resolve -q
COPY src/ ./src/
# Overwrite with the freshly built frontend assets
COPY --from=frontend /app/src/main/resources/static ./src/main/resources/static
RUN mvn package -DskipTests -q

# Stage 3: fetch Stockfish, the engine behind game review. Pinned to one
# release and checked against its SHA-256, so a rebuild can never pick up a
# different binary than the one that was tested.
FROM alpine:3 AS stockfish
ARG STOCKFISH_URL=https://github.com/official-stockfish/Stockfish/releases/download/sf_19/stockfish-linux-x86-64-universal.tar.gz
ARG STOCKFISH_SHA256=9defc0d4e55d49c65a6d042f3e571a39fcea499ade6dbe741b53b8c65e03611f
RUN wget -qO /tmp/stockfish.tar.gz "$STOCKFISH_URL" \
    && echo "$STOCKFISH_SHA256  /tmp/stockfish.tar.gz" | sha256sum -c - \
    && tar -xzf /tmp/stockfish.tar.gz -C /tmp \
    && install -m 0755 /tmp/stockfish/stockfish-linux-x86-64-universal /stockfish

# Stage 4: runtime image. Ubuntu-based rather than Alpine because the official
# Stockfish binary links against glibc, and Alpine's musl cannot load it.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar
COPY --from=stockfish /stockfish /usr/local/bin/stockfish
# Serving HTTP as root means a JVM-level compromise starts with root in the
# container. Nothing here needs it — the jar only reads its own directory.
# /logs is logback's default destination (see logback.xml) and has to exist
# up front: an unprivileged user cannot create a directory at the filesystem
# root, so without this the file appender silently fails to open.
RUN useradd --system --user-group --no-create-home chess \
    && mkdir -p /logs \
    && chown -R chess:chess /app /logs
USER chess
# Without a cap the JVM sizes its heap from the host's RAM (a quarter of it),
# which the VPS memory budget can't afford once Stockfish and the LLM share it.
ENV JAVA_TOOL_OPTIONS="-Xmx768m"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
