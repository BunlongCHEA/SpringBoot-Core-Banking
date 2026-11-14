# =============================================================
# CBS backend — runtime image (multi-arch: linux/amd64 + linux/arm64)
#
# Jenkins already compiled and tested the JAR (./mvnw verify), so this
# image only RUNS it. There are deliberately NO `RUN` steps: that lets
# `docker buildx --platform linux/amd64,linux/arm64` build both
# architectures on the k8 agent without QEMU emulation, so the same tag
# runs on pi5 (arm64) and k8 (amd64).
#
# Health checks are Kubernetes probes (deploy/k8s/base/backend.yaml),
# so no curl / HEALTHCHECK is needed in the image.
# =============================================================
FROM eclipse-temurin:25-jre

WORKDIR /app

# Only the Spring Boot fat JAR (cbs-<version>.jar); *.jar.original is not matched.
# Owned by root and read-only for the app user.
COPY target/cbs-*.jar /app/app.jar

# Fixed non-root UID/GID 10001 (matches runAsUser in the Deployment).
# /app/logs and /tmp are writable emptyDir volumes in Kubernetes.
USER 10001:10001

EXPOSE 8080

# Container-aware memory sizing; crash fast on OOM so Kubernetes restarts it.
# The Deployment overrides this via env JAVA_OPTS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
