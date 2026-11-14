// =====================================================================
// CBS Spring Boot backend — Jenkins pipeline (Multibranch, source: gitlab.com)
// ---------------------------------------------------------------------
//  feature/*, bugfix/* →  Secret scan → Build & Test
//  uat                 →  … → Image (amd64+arm64) → TRIVY GATE → deploy to K3s namespace cbs-uat
//  main                →  … → Image → TRIVY GATE → APPROVAL → pg_dump backup → deploy to cbs-prod
//
//  Build with Parameters → SECRETS_ONLY: skip the build, re-apply cbs-<env>-env
//  and restart the pods with the image that is already running.
//
//  Everything is in this file + deploy/k8s/ (Kubernetes manifests). Nothing else.
//
//  Jenkins credentials (Manage Jenkins → Credentials → System → Global):
//    gitlab-repo           Username+Password  GitLab user + PAT (read_repository)   — job source
//    gitlab-registry-push  Username+Password  deploy token (read+write_registry)
//    gitlab-registry-pull  Username+Password  deploy token (read_registry)          — cluster pulls
//    cbs-uat-env           Secret file        filled copy of deploy/cbs.env.example (uat values)
//    cbs-prod-env          Secret file        filled copy of deploy/cbs.env.example (prod values)
//    kubeconfig-cbs-uat    Secret file        Terraform-Plan raspberrypi/output/jenkins/kubeconfig-cbs-uat
//    kubeconfig-cbs-prod   Secret file        Terraform-Plan raspberrypi/output/jenkins/kubeconfig-cbs-prod
// =====================================================================

// ---------- Build Pod: runs on the k8 node (label set by Terraform-Plan jenkins.yml) ----------
//   maven  : JDK 25 → ./mvnw verify (Testcontainers uses the docker sidecar)
//   docker : Docker-in-Docker → Testcontainers + multi-arch image build/push
//   trivy  : secret scan + image vulnerability gate
BUILD_POD = '''
apiVersion: v1
kind: Pod
spec:
  nodeSelector:
    cbs.ci/agent: "true"
  automountServiceAccountToken: false
  containers:
    - name: maven
      image: eclipse-temurin:25-jdk
      command: ["cat"]
      tty: true
      env:
        - {name: DOCKER_HOST, value: "tcp://localhost:2375"}
        - {name: TESTCONTAINERS_HOST_OVERRIDE, value: "localhost"}
        - {name: MAVEN_OPTS, value: "-Xmx1g"}
      resources:
        requests: {cpu: "1", memory: 2Gi}
        limits: {memory: 3Gi}
      volumeMounts:
        - {name: m2-cache, mountPath: /root/.m2}
        - {name: tools-cache, mountPath: /cache/bin}
    - name: docker
      image: docker:29-dind
      securityContext: {privileged: true}
      env:
        - {name: DOCKER_TLS_CERTDIR, value: ""}
      args: ["--mtu=1450"]          # K3s flannel MTU; 1500 makes pulls hang
      resources:
        requests: {cpu: 500m, memory: 1Gi}
        limits: {memory: 4Gi}
      volumeMounts:
        - {name: docker-data, mountPath: /var/lib/docker}
    - name: trivy
      image: ghcr.io/aquasecurity/trivy:0.75.0   # pinned (v0.69.4 was compromised in Mar 2026)
      command: ["cat"]
      tty: true
      env:
        - {name: TRIVY_CACHE_DIR, value: /cache/trivy}
        - {name: TRIVY_NO_PROGRESS, value: "true"}
      resources:
        requests: {cpu: 250m, memory: 512Mi}
        limits: {memory: 2Gi}
      volumeMounts:
        - {name: trivy-cache, mountPath: /cache/trivy}
  volumes:
    - {name: docker-data, emptyDir: {}}
    - {name: m2-cache, hostPath: {path: /var/lib/jenkins-cache/m2, type: DirectoryOrCreate}}
    - {name: trivy-cache, hostPath: {path: /var/lib/jenkins-cache/trivy, type: DirectoryOrCreate}}
    - {name: tools-cache, hostPath: {path: /var/lib/jenkins-cache/bin, type: DirectoryOrCreate}}
'''

// ---------- Small Pod for prod deploy / SECRETS_ONLY (no Docker) ----------
DEPLOY_POD = '''
apiVersion: v1
kind: Pod
spec:
  nodeSelector:
    cbs.ci/agent: "true"
  automountServiceAccountToken: false
  containers:
    - name: maven
      image: eclipse-temurin:25-jdk
      command: ["cat"]
      tty: true
      resources:
        requests: {cpu: 100m, memory: 128Mi}
        limits: {memory: 512Mi}
      volumeMounts:
        - {name: tools-cache, mountPath: /cache/bin}
  volumes:
    - {name: tools-cache, hostPath: {path: /var/lib/jenkins-cache/bin, type: DirectoryOrCreate}}
'''

// ---------- Deploy script (bash). Inputs come from deployCbs() below ----------
//  1. checks the env file (right profile, no CHANGE_ME)
//  2. Secret cbs-env + pull secret gitlab-registry
//  3. prod: pg_dump backup Job into PVC cbs-db-backups (only when the image changes)
//  4. kubectl apply -k deploy/k8s/overlays/<env> with the new image tag
//  5. waits until the new pods are Ready (fails the build if not)
DEPLOY_SCRIPT = '''#!/bin/bash
set -euo pipefail

# --- kubectl (checksum-verified, cached on the agent node) ---
ARCH=$(uname -m | sed 's/x86_64/amd64/; s/aarch64/arm64/')
BIN=/cache/bin/kubectl-$KUBECTL_VERSION-$ARCH
if [ ! -x "$BIN" ]; then
  curl -fsSL -o /tmp/kubectl "https://dl.k8s.io/release/$KUBECTL_VERSION/bin/linux/$ARCH/kubectl"
  echo "$(curl -fsSL "https://dl.k8s.io/release/$KUBECTL_VERSION/bin/linux/$ARCH/kubectl.sha256")  /tmp/kubectl" | sha256sum -c -
  install -m 0755 /tmp/kubectl "$BIN"
fi
k() { "$BIN" -n "$NS" "$@"; }

# --- 1. env file sanity ---
grep -qx "SPRING_PROFILES_ACTIVE=$ENV_NAME" "$CBS_ENV_FILE" \\
  || { echo "cbs-$ENV_NAME-env must contain SPRING_PROFILES_ACTIVE=$ENV_NAME (wrong file uploaded?)"; exit 1; }
if grep -q '=.*CHANGE_ME' "$CBS_ENV_FILE"; then
  echo "cbs-$ENV_NAME-env still has CHANGE_ME in: $(grep '=.*CHANGE_ME' "$CBS_ENV_FILE" | cut -d= -f1 | xargs)"; exit 1
fi

# --- 2. secrets ---
k create secret generic cbs-env --from-env-file="$CBS_ENV_FILE" --dry-run=client -o yaml | k apply -f -
k create secret docker-registry gitlab-registry --docker-server=registry.gitlab.com \\
  --docker-username="$REG_PULL_USER" --docker-password="$REG_PULL_PASS" --dry-run=client -o yaml | k apply -f -

CURRENT=$(k get deploy cbs-backend -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null || true)
if [ "$TARGET" = "--secrets-only" ]; then
  [ -n "$CURRENT" ] || { echo "SECRETS_ONLY needs a running deployment - run a normal build first"; exit 1; }
  IMAGE=$CURRENT
else
  IMAGE=$TARGET
fi
echo "Current: ${CURRENT:-<none>}   New: $IMAGE"

# --- 3. prod backup before a new image (Flyway migrates on startup) ---
if [ "$BACKUP" = "true" ] && [ "$IMAGE" != "$CURRENT" ]; then
  k apply -f deploy/k8s/overlays/prod/backup-pvc.yaml
  JOB=cbs-db-backup-$(date +%Y%m%d-%H%M%S)
  k apply -f - <<EOF
apiVersion: batch/v1
kind: Job
metadata: {name: $JOB}
spec:
  backoffLimit: 0
  ttlSecondsAfterFinished: 604800
  template:
    spec:
      restartPolicy: Never
      automountServiceAccountToken: false
      containers:
        - name: pg-dump
          image: postgres:17-alpine      # same major version as cbs-data PostgreSQL
          envFrom: [{secretRef: {name: cbs-env}}]
          command: ["/bin/sh", "-ec"]
          args:
            - |
              export PGPASSWORD="\\$DB_PASSWORD"
              F="/backups/\\${DB_NAME}_before_${IMAGE##*:}_\\$(date +%Y%m%d_%H%M%S).dump"
              pg_dump -h "\\$DB_HOST" -p "\\$DB_PORT" -U "\\$DB_USERNAME" -d "\\$DB_NAME" -Fc -f "\\$F"
              ls -lh "\\$F"
              find /backups -name '*.dump' -mtime +14 -print -delete
          volumeMounts: [{name: b, mountPath: /backups}]
      volumes: [{name: b, persistentVolumeClaim: {claimName: cbs-db-backups}}]
EOF
  for i in $(seq 1 120); do            # up to 20 min
    [ "$(k get job "$JOB" -o jsonpath='{.status.succeeded}')" = "1" ] && break
    if [ -n "$(k get job "$JOB" -o jsonpath='{.status.failed}')" ] || [ "$i" = 120 ]; then
      k logs "job/$JOB" || true
      echo "Backup FAILED - nothing deployed"; exit 1
    fi
    sleep 10
  done
  k logs "job/$JOB" | tail -n 3
fi

# --- 4. apply manifests with the image tag + secret checksum ---
R=deploy/k8s/.render/$ENV_NAME
rm -rf "$R" && mkdir -p "$R"
cat > "$R/kustomization.yaml" <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources: [../../overlays/$ENV_NAME]
images:
  - {name: cbs-backend, newName: ${IMAGE%:*}, newTag: ${IMAGE##*:}}
patches:
  - target: {kind: Deployment, name: cbs-backend}
    patch: |-
      - op: replace
        path: /spec/template/metadata/annotations/cbs~1env-checksum
        value: "$(sha256sum "$CBS_ENV_FILE" | cut -c1-16)"
EOF
"$BIN" apply -k "$R"

# --- 5. wait for Ready pods ---
if ! k rollout status deploy/cbs-backend --timeout=420s; then
  k get pods -o wide
  k logs -l app.kubernetes.io/name=cbs-backend --tail=80 --prefix || true
  echo "Rollout FAILED. Fix and rebuild, or roll back:  kubectl -n $NS rollout undo deploy/cbs-backend"
  exit 1
fi
k rollout status deploy/cbs-nginx --timeout=120s
echo "Deployed $IMAGE to $NS"
'''

def deployCbs(String envName, String target) {
    def ns = "cbs-${envName}"
    withCredentials([
        file(credentialsId: "kubeconfig-cbs-${envName}", variable: 'KUBECONFIG'),
        file(credentialsId: "cbs-${envName}-env",       variable: 'CBS_ENV_FILE'),
        usernamePassword(credentialsId: 'gitlab-registry-pull',
                         usernameVariable: 'REG_PULL_USER', passwordVariable: 'REG_PULL_PASS')
    ]) {
        withEnv(["ENV_NAME=${envName}", "NS=${ns}", "TARGET=${target}",
                 "BACKUP=${envName == 'prod'}"]) {
            sh DEPLOY_SCRIPT
        }
    }
}

pipeline {
    agent none      // the approval stage must not hold a Pod while it waits

    parameters {
        booleanParam(name: 'SECRETS_ONLY', defaultValue: false,
            description: 'Skip build. Re-apply cbs-<env>-env and restart pods with the current image.')
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    environment {
        REGISTRY        = 'registry.gitlab.com'
        IMAGE_NAME      = 'registry.gitlab.com/bunlongchea/springboot-core-banking'  // all lowercase
        PLATFORMS       = 'linux/amd64,linux/arm64'
        KUBECTL_VERSION = 'v1.36.4'          // = k3s_version in Terraform-Plan versions.yml
        PROD_APPROVERS  = 'admin'            // Jenkins user IDs allowed to approve prod
    }

    stages {
        stage('CI') {
            when { beforeAgent true; expression { !params.SECRETS_ONLY } }
            agent { kubernetes { yaml BUILD_POD; defaultContainer 'maven' } }
            stages {
                stage('Prepare') {
                    steps {
                        script {
                            // build containers have no git → use the commit from the checkout
                            def sha = env.GIT_COMMIT.take(8)
                            env.IMAGE_TAG = "${env.BRANCH_NAME.replaceAll('[^a-zA-Z0-9_.-]', '-')}-${env.BUILD_NUMBER}-${sha}"
                            currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.IMAGE_TAG}"
                        }
                    }
                }

                stage('Secret Scan') {
                    steps {
                        container('trivy') {
                            sh 'trivy fs --scanners secret --exit-code 1 --skip-dirs .git --skip-dirs target .'
                        }
                    }
                }

                stage('Build & Test') {
                    options { timeout(time: 30, unit: 'MINUTES') }
                    steps {
                        container('docker') {
                            sh 'for i in $(seq 1 30); do docker info >/dev/null 2>&1 && exit 0; sleep 2; done; exit 1'
                        }
                        sh 'chmod +x mvnw && ./mvnw -B -ntp clean verify'
                    }
                    post {
                        always { junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml' }
                    }
                }

                stage('Image Build & Push') {
                    when { anyOf { branch 'uat'; branch 'main' } }
                    steps {
                        container('docker') {
                            withCredentials([usernamePassword(credentialsId: 'gitlab-registry-push',
                                    usernameVariable: 'REG_USER', passwordVariable: 'REG_PASS')]) {
                                sh '''
                                    echo "$REG_PASS" | docker login "$REGISTRY" -u "$REG_USER" --password-stdin
                                    docker buildx inspect cbs >/dev/null 2>&1 || docker buildx create --name cbs --driver docker-container
                                    docker buildx build --builder cbs --platform "$PLATFORMS" \
                                      --label org.opencontainers.image.revision="$GIT_COMMIT" \
                                      -t "$IMAGE_NAME:$IMAGE_TAG" --push .
                                '''
                            }
                        }
                    }
                }

                stage('Trivy Image Gate') {
                    // image is pushed but NOT deployed unless this passes (exceptions: .trivyignore)
                    when { anyOf { branch 'uat'; branch 'main' } }
                    steps {
                        container('trivy') {
                            withCredentials([usernamePassword(credentialsId: 'gitlab-registry-push',
                                    usernameVariable: 'TRIVY_USERNAME', passwordVariable: 'TRIVY_PASSWORD')]) {
                                sh '''
                                    for p in $(echo "$PLATFORMS" | tr ',' ' '); do
                                      trivy image --image-src remote --platform "$p" --scanners vuln \
                                        --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 "$IMAGE_NAME:$IMAGE_TAG"
                                    done
                                '''
                            }
                        }
                    }
                }

                stage('Deploy to UAT') {
                    when { branch 'uat' }
                    steps { script { deployCbs('uat', "${env.IMAGE_NAME}:${env.IMAGE_TAG}") } }
                }
            }
        }

        stage('UAT: Secrets Only') {
            when { beforeAgent true; allOf { branch 'uat'; expression { params.SECRETS_ONLY } } }
            agent { kubernetes { yaml DEPLOY_POD; defaultContainer 'maven' } }
            steps { script { deployCbs('uat', '--secrets-only') } }
        }

        stage('Approval for Production') {
            when { branch 'main' }
            steps {
                timeout(time: 24, unit: 'HOURS') {
                    input message: params.SECRETS_ONLY ? 'PROD: restart with new cbs-prod-env?' :
                                   "Deploy ${env.IMAGE_NAME}:${env.IMAGE_TAG} to PRODUCTION?",
                          ok: 'Approve & Deploy', submitter: env.PROD_APPROVERS
                }
            }
        }

        stage('Deploy to Production') {
            when { beforeAgent true; branch 'main' }
            agent { kubernetes { yaml DEPLOY_POD; defaultContainer 'maven' } }
            options { timeout(time: 45, unit: 'MINUTES') }
            steps {
                script {
                    deployCbs('prod', params.SECRETS_ONLY ? '--secrets-only' : "${env.IMAGE_NAME}:${env.IMAGE_TAG}")
                }
            }
        }
    }
}
