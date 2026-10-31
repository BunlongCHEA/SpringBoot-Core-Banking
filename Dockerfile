# =============================================================
# CBS backend — runtime image
# Jenkins already compiled and tested the JAR (./mvnw verify),
# so this image only RUNS it. Small, non-root, health-checked.
# =============================================================
FROM eclipse-temurin:25-jre

# curl is used by the HEALTHCHECK below
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Fixed UID/GID 10001 so the host's ./logs folder can be chowned to match
RUN groupadd --system --gid 10001 cbs \
 && useradd --system --uid 10001 --gid cbs --home-dir /app --shell /usr/sbin/nologin cbs

WORKDIR /app

# Only the Spring Boot fat JAR (cbs-<version>.jar); *.jar.original is not matched
COPY target/cbs-*.jar app.jar

RUN mkdir -p /app/logs && chown -R cbs:cbs /app
USER cbs

EXPOSE 8080

# Container-aware memory sizing; crash fast on OOM so Docker restarts it
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# Flyway migrations run on startup, so give it a generous start period
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
  CMD curl -fsS http://localhost:8080/api/v1/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]