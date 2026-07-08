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

# Stage 3: minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
