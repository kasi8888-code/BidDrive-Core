
# =====================================================================
# BidDrive Core — Dockerfile
# Phase 8C: Production Container
#
# Two-stage build:
#   Stage 1 (builder): Downloads dependencies and compiles the JAR
#   Stage 2 (runtime): Copies only the JAR into a minimal JRE image
#
# WHY two stages?
#   The builder stage needs Maven, JDK 21, and all source files (~600MB).
#   The runtime stage only needs the compiled JAR + JRE (~180MB).
#   Final image is ~3× smaller, loads faster, and has smaller attack surface.
# =====================================================================

# ─── Stage 1: Build ──────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first (Docker layer cache optimization)
# If only source code changes, Maven deps are NOT re-downloaded
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download all dependencies (this layer is cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -q

# Copy source code
COPY src/ ./src/

# Build the fat JAR, skip tests (tests run in CI separately)
RUN ./mvnw package -DskipTests -q

# ─── Stage 2: Runtime ────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security — never run app as root in production
RUN addgroup -S biddrive && adduser -S biddrive -G biddrive

WORKDIR /app

# Copy only the compiled JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Own the JAR as the non-root user
RUN chown biddrive:biddrive app.jar

USER biddrive

# Spring Boot embedded Tomcat listens on port 8080
EXPOSE 8080

# JVM tuning for containers:
#   -XX:+UseContainerSupport   — reads CPU/memory limits from Docker cgroups
#   -XX:MaxRAMPercentage=75.0  — use 75% of container memory for heap
#   -Djava.security.egd=...    — faster SecureRandom (important for OTP generation)
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", "app.jar"]
