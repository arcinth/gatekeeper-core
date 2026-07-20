FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
# No .git directory is available in this build context (docker-compose.yml scopes
# the build context to ./backend, and .git lives at the repo root) - git-commit-id-
# maven-plugin's failOnNoGitDirectory=false (see pom.xml) means this degrades
# gracefully rather than failing the build: an image built this way simply won't
# have real git commit/branch values in /actuator/info. A `mvn clean package` run
# directly (CI, local dev) does have real values, since Maven searches upward from
# the POM's own location for .git regardless of any Docker context boundary - see
# docs/Observability.md for this known limitation.
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# curl is the HEALTHCHECK probe below - not present in the jre-jammy base image by default.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
# Non-root container user (Milestone 10: Security Hardening) - the base image runs as
# root by default, which turns any future code-execution vulnerability in the app or
# its dependencies into a much worse container-escape scenario than it needs to be.
# A dedicated system user/group (no login shell, no home directory) owns only what it
# needs to: the jar it runs.
RUN groupadd --system gatekeeper && useradd --system --gid gatekeeper --no-create-home gatekeeper
COPY --from=build --chown=gatekeeper:gatekeeper /workspace/target/gatekeeper-core.jar app.jar
USER gatekeeper
EXPOSE 8080
# Actuator's own port (Milestone 9: Observability) - deliberately not published to
# the host by a real production deployment; docker-compose.yml publishes it here
# only for local development convenience. See docs/Observability.md.
EXPOSE 8081
# Hits the liveness probe, not the general health endpoint - liveness deliberately
# never depends on the database or GitHub (see GitHubHealthIndicator's Javadoc), so
# a downstream outage never causes the orchestrator to kill and restart a healthy
# process. start-period gives Flyway/JPA schema validation time to finish first.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
