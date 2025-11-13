# Multi-stage build for Spring Boot Agent

# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Gradle files for dependency caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Grant execute permission
RUN chmod +x gradlew

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src src

# Build application (skip tests for faster build)
RUN ./gradlew bootJar -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install Docker CLI for Agent to communicate with Docker daemon
RUN apk add --no-cache docker-cli

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring && \
    addgroup spring docker
USER spring:spring

# Copy built JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Environment variables
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
ENV SERVER_PORT=8082

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8082/actuator/health || exit 1

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} app.jar"]
