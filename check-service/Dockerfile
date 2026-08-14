# Check Service Dockerfile
# Multi-stage build for optimized image size

# Build stage
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace/app

# Copy maven wrapper and pom files
COPY ../mvnw .
COPY ../.mvn .mvn
COPY ../pom.xml .
COPY ../common/pom.xml common/
COPY pom.xml check-service/

# Make mvnw executable
RUN chmod +x ./mvnw

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -pl common,check-service -B

# Copy source code
COPY ../common/src common/src
COPY src check-service/src

# Build the application
RUN ./mvnw package -pl common,check-service -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Add non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Create directories for logs and temp files
RUN mkdir -p /app/logs /app/tmp && chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Copy the built jar
COPY --from=build --chown=spring:spring /workspace/app/check-service/target/*.jar app.jar

# Environment variables
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV SERVER_PORT=8083
ENV SPRING_PROFILES_ACTIVE=docker

# Expose port
EXPOSE 8083

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8083/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
