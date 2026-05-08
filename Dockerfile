# =============================================================================
# IncidentIQ — multi-stage Docker build
# Stage 1: build the fat JAR with Maven (uses a cacheable dependency layer)
# Stage 2: minimal JRE-only runtime image (~200 MB)
# =============================================================================

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependencies first — pom.xml changes infrequently, source changes often
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -B \
 && cp target/*.jar app.jar

# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Run as non-root for safety
RUN groupadd -r app && useradd -r -g app app
USER app

COPY --from=build --chown=app:app /app/app.jar /app/app.jar

EXPOSE 8080

# JVM container-aware tuning + sensible defaults
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
