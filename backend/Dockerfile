# Multi-stage Dockerfile for Spring Boot backend (Java 17).
#
# Stage 1: Build the executable JAR using Maven.
# Dependencies are cached in a separate layer before copying source code to speed up rebuilds.
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy application source and build the JAR (skipping tests for container packaging)
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime image using Eclipse Temurin JRE 17 (Ubuntu Jammy base, multi-arch support)
FROM eclipse-temurin:17-jre-jammy

# curl: actuator health check
# docker.io: allows compiling LaTeX PDFs in the sandbox container if /var/run/docker.sock is mounted
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl docker.io \
 && rm -rf /var/lib/apt/lists/*

# Run as a non-root user for security
RUN groupadd -g 1000 appgroup && \
    useradd -u 1000 -g appgroup -m -s /bin/bash appuser

WORKDIR /app

# Ensure runtime directories exist with proper ownership
RUN mkdir -p /app/artifacts /app/data && chown -R appuser:appgroup /app

# Copy the repackaged executable Spring Boot JAR
COPY --from=builder --chown=appuser:appgroup /build/target/resume-tailor-backend-*.jar /app/app.jar

USER appuser

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
