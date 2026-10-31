#!/usr/bin/env bash
# =============================================================
# /opt/cbs/deploy.sh <image-tag>
# Called by Jenkins over SSH. Same script on UAT and PROD;
# behaviour differs only by CBS_ENV in /opt/cbs/deploy.conf.
#
#   1. (prod only) pg_dump backup of the database
#   2. pull the new image, restart the container
#   3. wait for /actuator/health = UP
#   4. if unhealthy -> roll back to the previous image tag, exit 1
# =============================================================
set -euo pipefail

APP_DIR=/opt/cbs
cd "$APP_DIR"

NEW_TAG="${1:?usage: deploy.sh <image-tag>}"
if [[ ! "$NEW_TAG" =~ ^[A-Za-z0-9_.-]+$ ]]; then
  echo "Invalid image tag: $NEW_TAG" >&2; exit 2
fi

# shellcheck source=/dev/null
source ./deploy.conf          # CBS_ENV, IMAGE_NAME, HEALTH_URL, HEALTH_TIMEOUT_SECONDS

log() { echo "[$(date '+%F %T')] [$CBS_ENV] $*"; }

# Previous tag (for rollback) is whatever .env currently points to
PREV_TAG=""
if [[ -f .env ]]; then
  PREV_TAG=$(grep -E '^IMAGE_TAG=' .env | cut -d= -f2- || true)
fi
log "Current: ${PREV_TAG:-<none>}  ->  New: $NEW_TAG"

write_compose_env() {
  printf 'IMAGE_NAME=%s\nIMAGE_TAG=%s\n' "$IMAGE_NAME" "$1" > .env
}

# ---------- 1. Production: back up the database first ----------
if [[ "$CBS_ENV" == "prod" ]]; then
  log "Backing up database before deploy (Flyway may migrate on startup)..."
  mkdir -p backups
  # read only the DB values (cbs.env is docker env-file format, not bash — don't `source` it)
  env_val() { grep -E "^$1=" cbs.env | tail -n1 | cut -d= -f2-; }
  DB_HOST=$(env_val DB_HOST); DB_PORT=$(env_val DB_PORT); DB_NAME=$(env_val DB_NAME)
  DB_USERNAME=$(env_val DB_USERNAME); DB_PASSWORD=$(env_val DB_PASSWORD)
  BACKUP_FILE="${DB_NAME}_$(date +%Y%m%d_%H%M%S)_before_${NEW_TAG}.dump"
  docker run --rm --network host --user "$(id -u):$(id -g)" \
    -e PGPASSWORD="$DB_PASSWORD" \
    -v "$APP_DIR/backups:/backups" \
    postgres:15-alpine \
    pg_dump -h "$DB_HOST" -p "${DB_PORT:-5432}" -U "$DB_USERNAME" -d "$DB_NAME" \
            -Fc -f "/backups/$BACKUP_FILE"
  log "Backup written: backups/$BACKUP_FILE"
  find backups -name '*.dump' -mtime +14 -delete   # keep 14 days
fi

# ---------- 2. Pull and start the new version ----------
write_compose_env "$NEW_TAG"
log "Pulling $IMAGE_NAME:$NEW_TAG"
docker compose pull
log "Starting container"
docker compose up -d

# ---------- 3. Wait until healthy ----------
TIMEOUT="${HEALTH_TIMEOUT_SECONDS:-180}"
log "Waiting up to ${TIMEOUT}s for $HEALTH_URL"
for (( waited=0; waited<TIMEOUT; waited+=5 )); do
  if curl -fsS "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
    log "Healthy. Deployed $NEW_TAG successfully."
    docker image prune -f >/dev/null || true
    exit 0
  fi
  sleep 5
done

# ---------- 4. Unhealthy -> roll back ----------
log "Health check FAILED. Last 100 log lines:"
docker compose logs --tail=100 cbs-backend || true

if [[ -n "$PREV_TAG" && "$PREV_TAG" != "$NEW_TAG" ]]; then
  log "Rolling back to $PREV_TAG"
  write_compose_env "$PREV_TAG"
  docker compose up -d
  log "Rolled back. NOTE: database migrations from $NEW_TAG (if any) were NOT undone."
else
  log "No previous tag to roll back to."
fi
exit 1
