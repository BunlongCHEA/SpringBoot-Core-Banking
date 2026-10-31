#!/usr/bin/env bash
# =============================================================
# deploy/deploy.sh — deploy CBS to K3s. Called by Jenkins (Jenkinsfile).
#
#   deploy/deploy.sh <uat|prod> <image-ref>        normal deploy
#   deploy/deploy.sh <uat|prod> --secrets-only     re-apply secrets, restart pods
#
# Needs these environment variables (Jenkins withCredentials provides them):
#   KUBECONFIG      kubeconfig-cbs-<env>  (ServiceAccount limited to that namespace)
#   CBS_ENV_FILE    cbs-<env>-env         (application secrets, cbs.env format)
#   REG_PULL_USER / REG_PULL_PASS         gitlab-registry-pull (read_registry token)
#
# Steps:
#   1. sanity checks (right env file for this env, no CHANGE_ME left)
#   2. Secret "cbs-env" + pull secret "gitlab-registry" (create or update)
#   3. prod only: pg_dump backup Job (Flyway may migrate on startup)
#   4. kubectl apply -k  (image tag + secret checksum injected)
#   5. wait for rollout; if it fails → logs + `kubectl rollout undo`, exit 1
#   6. smoke test the health URL through nginx
# =============================================================
set -euo pipefail

ENV_NAME="${1:?usage: deploy.sh <uat|prod> <image-ref|--secrets-only>}"
TARGET="${2:?usage: deploy.sh <uat|prod> <image-ref|--secrets-only>}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

CONF="deploy/conf/${ENV_NAME}.conf"
[[ -f "$CONF" ]] || { echo "No config $CONF (expected uat or prod)" >&2; exit 2; }
# shellcheck source=/dev/null
source "$CONF"

: "${KUBECONFIG:?KUBECONFIG not set (Jenkins credential kubeconfig-cbs-${ENV_NAME})}"
: "${CBS_ENV_FILE:?CBS_ENV_FILE not set (Jenkins credential cbs-${ENV_NAME}-env)}"
: "${REG_PULL_USER:?REG_PULL_USER not set (Jenkins credential gitlab-registry-pull)}"
: "${REG_PULL_PASS:?REG_PULL_PASS not set}"

NS="$K8S_NAMESPACE"
k() { kubectl -n "$NS" "$@"; }
log() { echo "[$(date '+%F %T')] [$CBS_ENV] $*"; }
die() { log "ERROR: $*" >&2; exit 1; }

# ---------- 1. Sanity checks ----------
SECRETS_ONLY=false
if [[ "$TARGET" == "--secrets-only" ]]; then
  SECRETS_ONLY=true
elif [[ ! "$TARGET" =~ ^[a-z0-9.-]+(:[0-9]+)?(/[a-z0-9._-]+)+:[A-Za-z0-9_.-]+$ ]]; then
  die "Invalid image reference: $TARGET"
fi

env_val() { grep -E "^$1=" "$CBS_ENV_FILE" | tail -n1 | cut -d= -f2- || true; }

FILE_PROFILE="$(env_val SPRING_PROFILES_ACTIVE)"
[[ "$FILE_PROFILE" == "$CBS_ENV" ]] \
  || die "Env credential has SPRING_PROFILES_ACTIVE='$FILE_PROFILE' but this is '$CBS_ENV'. Wrong credential uploaded?"
if grep -qE '=.*CHANGE_ME' "$CBS_ENV_FILE"; then
  die "Env credential still contains CHANGE_ME placeholders: $(grep -E '=.*CHANGE_ME' "$CBS_ENV_FILE" | cut -d= -f1 | tr '\n' ' ')"
fi

kubectl auth can-i create deployments -n "$NS" >/dev/null \
  || die "kubeconfig cannot manage namespace $NS (run Terraform-Plan jenkins playbook?)"

# ---------- 2. Secrets ----------
log "Applying Secret cbs-env and pull secret gitlab-registry"
k create secret generic cbs-env --from-env-file="$CBS_ENV_FILE" \
  --dry-run=client -o yaml | k apply -f - >/dev/null
k create secret docker-registry gitlab-registry \
  --docker-server="$REGISTRY" --docker-username="$REG_PULL_USER" --docker-password="$REG_PULL_PASS" \
  --dry-run=client -o yaml | k apply -f - >/dev/null
ENV_CHECKSUM="$(sha256sum "$CBS_ENV_FILE" | cut -c1-16)"

# Which image?
if $SECRETS_ONLY; then
  IMAGE="$(k get deployment cbs-backend \
            -o jsonpath='{.spec.template.spec.containers[?(@.name=="cbs-backend")].image}' 2>/dev/null || true)"
  [[ -n "$IMAGE" ]] || die "--secrets-only needs an existing deployment; run a normal deploy first"
  log "Secrets-only: keeping image $IMAGE, restarting with new secrets"
else
  IMAGE="$TARGET"
fi
CURRENT="$(k get deployment cbs-backend \
            -o jsonpath='{.spec.template.spec.containers[?(@.name=="cbs-backend")].image}' 2>/dev/null || true)"
log "Current: ${CURRENT:-<none>}  ->  New: $IMAGE"

# ---------- 3. Production: back up the database first ----------
if [[ "$BACKUP_BEFORE_DEPLOY" == "true" && "$SECRETS_ONLY" == "false" && "$IMAGE" != "$CURRENT" ]]; then
  # The PVC must exist before the Job uses it (first deploy)
  k apply -f "$OVERLAY/backup-pvc.yaml" >/dev/null
  JOB="cbs-db-backup-$(date +%Y%m%d-%H%M%S)"
  TAG="${IMAGE##*:}"
  log "Backing up database (Job $JOB) before deploying $TAG"
  k apply -f - >/dev/null <<EOF
apiVersion: batch/v1
kind: Job
metadata:
  name: $JOB
  labels: {app.kubernetes.io/name: cbs-db-backup, app.kubernetes.io/part-of: cbs}
spec:
  backoffLimit: 0
  ttlSecondsAfterFinished: 604800
  activeDeadlineSeconds: 1800
  template:
    metadata:
      labels: {app.kubernetes.io/name: cbs-db-backup}
    spec:
      restartPolicy: Never
      automountServiceAccountToken: false
      containers:
        - name: pg-dump
          image: postgres:15-alpine
          env:
            - {name: DB_HOST,     valueFrom: {secretKeyRef: {name: cbs-env, key: DB_HOST}}}
            - {name: DB_PORT,     valueFrom: {secretKeyRef: {name: cbs-env, key: DB_PORT}}}
            - {name: DB_NAME,     valueFrom: {secretKeyRef: {name: cbs-env, key: DB_NAME}}}
            - {name: DB_USERNAME, valueFrom: {secretKeyRef: {name: cbs-env, key: DB_USERNAME}}}
            - {name: PGPASSWORD,  valueFrom: {secretKeyRef: {name: cbs-env, key: DB_PASSWORD}}}
            - {name: BACKUP_FILE, value: "before_${TAG}_$(date +%Y%m%d_%H%M%S).dump"}
            - {name: RETENTION_DAYS, value: "${BACKUP_RETENTION_DAYS}"}
          command: ["/bin/sh", "-ec"]
          args:
            - |
              pg_dump -h "\$DB_HOST" -p "\$DB_PORT" -U "\$DB_USERNAME" -d "\$DB_NAME" -Fc -f "/backups/\${DB_NAME}_\$BACKUP_FILE"
              ls -lh "/backups/\${DB_NAME}_\$BACKUP_FILE"
              find /backups -name '*.dump' -mtime +"\$RETENTION_DAYS" -print -delete
          volumeMounts:
            - {name: backups, mountPath: /backups}
      volumes:
        - name: backups
          persistentVolumeClaim: {claimName: cbs-db-backups}
EOF
  for _ in $(seq 1 180); do   # up to 30 min
    OK="$(k get job "$JOB" -o jsonpath='{.status.succeeded}')"
    BAD="$(k get job "$JOB" -o jsonpath='{.status.failed}')"
    [[ "$OK" == "1" ]] && break
    if [[ -n "$BAD" && "$BAD" != "0" ]]; then
      k logs "job/$JOB" --tail=50 || true
      die "Database backup FAILED — nothing was deployed."
    fi
    sleep 10
  done
  [[ "$OK" == "1" ]] || die "Database backup timed out — nothing was deployed."
  k logs "job/$JOB" --tail=5 || true
  log "Backup done (PVC cbs-db-backups)."
fi

# ---------- 4. Apply manifests ----------
RENDER="deploy/k8s/.render/${ENV_NAME}"
rm -rf "$RENDER" && mkdir -p "$RENDER"
cat > "$RENDER/kustomization.yaml" <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../../overlays/${ENV_NAME}
images:
  - name: cbs-backend
    newName: ${IMAGE%:*}
    newTag: ${IMAGE##*:}
patches:
  - target: {kind: Deployment, name: cbs-backend}
    patch: |-
      - op: replace
        path: /spec/template/metadata/annotations/cbs~1env-checksum
        value: "${ENV_CHECKSUM}"
EOF

log "Changes to apply (kubectl diff):"
kubectl diff -k "$RENDER" || true      # exit code 1 just means "there are differences"
log "Applying manifests"
kubectl apply -k "$RENDER"

# ---------- 5. Wait for rollout, roll back on failure ----------
log "Waiting up to $ROLLOUT_TIMEOUT for rollout"
if ! k rollout status deployment/cbs-backend --timeout="$ROLLOUT_TIMEOUT"; then
  log "Rollout FAILED. Pods, events and logs:"
  k get pods -l app.kubernetes.io/name=cbs-backend -o wide || true
  k get events --sort-by=.lastTimestamp | tail -n 20 || true
  k logs -l app.kubernetes.io/name=cbs-backend --tail=100 --all-containers --prefix || true
  if [[ -n "$CURRENT" ]]; then
    log "Rolling back to previous revision ($CURRENT)"
    k rollout undo deployment/cbs-backend
    k rollout status deployment/cbs-backend --timeout="$ROLLOUT_TIMEOUT" || true
    log "Rolled back. NOTE: Flyway migrations from the new version (if any) were NOT undone,"
    log "and Secret cbs-env keeps the NEW values."
  else
    log "First deploy — nothing to roll back to."
  fi
  exit 1
fi
k rollout status deployment/cbs-nginx --timeout=120s

# ---------- 6. Smoke test through nginx ----------
fetch() { if command -v curl >/dev/null; then curl -fsS --max-time 5 "$1"; else wget -qO- -T 5 "$1"; fi; }
for _ in $(seq 1 12); do
  if fetch "$SMOKE_URL" 2>/dev/null | grep -q '"status":"UP"'; then
    log "Healthy via nginx. Deployed $IMAGE to $NS."
    exit 0
  fi
  sleep 5
done
die "Pods are Ready but $SMOKE_URL is not UP (check nginx / dependencies). Not rolled back automatically."
