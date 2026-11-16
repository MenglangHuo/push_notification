# ==============================================================================
# Multi-Tenant Push Notification Engine - Backend Dockerfile
# Optimized for AWS Resource-Constrained Environments (Java 25 JRE Alpine)
# ==============================================================================
FROM eclipse-temurin:25-jre-alpine

# Non-root user and group for container security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Create uploads directory with appropriate permissions
RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app

# Build argument for target artifact
ARG JAR_FILE=target/push-platform-backend-1.0.0-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

RUN chown appuser:appgroup /app/app.jar

USER appuser:appgroup

# Production JVM tuning for small AWS instances (Serial GC + conservative heap/metaspace)
ENV JAVA_OPTS="-XX:+UseSerialGC -Xms128m -Xmx384m -XX:MaxMetaspaceSize=160m -XX:CompressedClassSpaceSize=48m -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

