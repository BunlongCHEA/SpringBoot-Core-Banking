# CBS Backend — CI/CD Runbook (GitLab → Jenkins → K3s)

Simple SDLC for the Spring Boot backend:

```
feature/*  ──MR──▶  uat  ──MR──▶  main
   │                 │              │
   │ build + test    │ build+test   │ build+test
   │ secret scan     │ image + TRIVY GATE    │ image + TRIVY GATE
   │                 │ AUTO deploy → cbs-uat │ APPROVAL → pg_dump → deploy → cbs-prod
```

```
gitlab.com (repo + registry) ──push mirror──▶ github.com (read-only copy)
        │  (Jenkins polls every 2 min)
        ▼
pi5  (K3s server)  ── Jenkins controller (systemd, 0 executors)
        │ launches a Pod per build
        ▼
k8   (K3s agent, label cbs.ci/agent=true) ── build Pod: maven | docker(dind) | trivy
        │ kubectl (namespace-scoped kubeconfig)
        ▼
K3s namespaces  cbs-uat / cbs-prod:
   Cloudflare Tunnel → Traefik :80 → cbs-nginx → cbs-backend (Spring Boot)
```

| Piece | Where it lives |
|---|---|
| Pipeline | `Jenkinsfile`, `ci/jenkins-agent-pod.yaml`, `ci/jenkins-deploy-pod.yaml` |
| Image | `Dockerfile` (multi-arch amd64 + arm64, no `RUN` steps) |
| Kubernetes | `deploy/k8s/base` (backend, nginx, ingress), `deploy/k8s/overlays/{uat,prod}` |
| nginx config | `deploy/k8s/base/nginx.conf` |
| Non-secret env settings | `deploy/conf/{uat,prod}.conf` (documented in `deploy/deploy.conf.example`) |
| Secret template | `deploy/cbs.env.example` → real values only in Jenkins credentials |
| Deploy logic | `deploy/deploy.sh` (backup, apply, rollout, rollback, smoke test) |
| Jenkins install | Terraform-Plan `ansible-common/playbooks/install/jenkins.yml` |

---

## 0. Before anything else — rotate the leaked secrets

Earlier commits of `deploy/cbs.env.example` contained real values and were mirrored to public GitHub.
Treat them as compromised and change them at the source:

- [ ] MongoDB Atlas: Database Access → user → **Edit → new password**
- [ ] Redis Cloud: database → Security → **new default-user password**
- [ ] `JWT_SECRET`: new value (`openssl rand -base64 48`); all users must log in again
- [ ] `KYC_API_KEY`: revoke and issue a new integration key in Go-KYC
- [ ] `KYC_MQ_KEY_V1` + `CBS_HMAC_SECRET`: new values, update the KYC side to match
- [ ] Super-admin password: change it in the app (the initial-password variable only matters on first boot)

Deleting the file does not remove the values from Git history. Rotation is the fix.

---

## 1. Commit the base config (one time)

`src/main/resources/application.yaml` was never re-added after the "Removed cached" commit, so a clean
checkout (Jenkins) had no datasource URL and no `/api/v1` context path. This change adds a
**secret-free** version (every secret is `${ENV_VAR}` with no default).

Compare it with your local copy before committing, and move any real values into
`application-local.yaml` (git-ignored):

```bash
git diff --no-index ~/your-local/application.yaml src/main/resources/application.yaml
```

Also: `application-test.yaml` moved from `src/test/java/com/bank/resources/` to `src/test/resources/`.
Maven only puts files from `src/test/resources` on the test classpath, so before this move the test profile was never loaded.

---

## 2. GitLab (gitlab.com)

### 2.1 Create the `uat` branch from `main`
```bash
git clone git@gitlab.com:<namespace>/springboot-core-banking.git
cd springboot-core-banking
git checkout main && git pull
git checkout -b uat
git push -u origin uat
```
The push mirror copies `uat` to GitHub automatically.

### 2.2 Protect the branches
**Settings → Repository → Protected branches**

| Branch | Allowed to merge | Allowed to push and merge | Allowed to force push |
|---|---|---|---|
| `main` | Maintainers | No one | off |
| `uat`  | Developers + Maintainers | No one | off |

**Settings → Merge requests →** tick **Pipelines must succeed** only if you also connect Jenkins status. For now, leave it unticked and review MRs by hand.

### 2.3 Tokens for Jenkins
| What | Where in GitLab | Scopes | Jenkins credential ID |
|---|---|---|---|
| Read the repo | **User Settings → Access tokens** (or Project → Settings → Access tokens, role Reporter) | `read_repository` | `gitlab-repo` |
| Push images | **Project → Settings → Repository → Deploy tokens** | `read_registry`, `write_registry` | `gitlab-registry-push` |
| Cluster pulls images | **Project → Settings → Repository → Deploy tokens** | `read_registry` | `gitlab-registry-pull` |

Give every token an expiry date and put a reminder in your calendar.

Image path = **Deploy → Container registry** page, e.g. `registry.gitlab.com/<namespace>/springboot-core-banking`.
Put it in `Jenkinsfile` → `IMAGE_NAME` (**all lowercase**).

---

## 3. Cluster + Jenkins (Terraform-Plan repo)

### 3.1 Inventory: pi5 FIRST (master + Jenkins), k8 SECOND (agent)
`raspberrypi/terraform.tfvars`
```hcl
pi_hosts = [
  { name = "pi5-1", ansible_host = "pi5-1", ansible_user = "pi-admin" },   # K3s server + Jenkins controller
  { name = "k8-2",  ansible_host = "k8-2",  ansible_user = "pi-admin" },   # K3s agent + build Pods
]
```

### 3.2 Values
`raspberrypi/group_vars/all.yml` (add to what you already have)
```yaml
jenkins_admin_password : "<openssl rand -base64 18>"   # no ' or \
# jenkins_domain       : "jenkins.bunlong.uk"         # optional, Cloudflare Tunnel
```

### 3.3 Run
```bash
./terraform_main.sh raspberrypi apply      # inventory
./terraform_main.sh raspberrypi test
./terraform_main.sh raspberrypi ansible
./terraform_main.sh raspberrypi rancher    # choose 2) Multi-node → pi5 server, k8 agent
./terraform_main.sh raspberrypi jenkins    # Jenkins controller + RBAC + namespaces + node label
```
ArgoCD/Prometheus/Grafana are optional and not used by this pipeline.

Check:
```bash
ssh pi5-1 'sudo kubectl get nodes --show-labels | grep cbs.ci/agent; sudo kubectl get ns | grep -E "jenkins|cbs-"'
ls -l raspberrypi/output/jenkins/        # kubeconfig-cbs-uat, kubeconfig-cbs-prod (chmod 600)
```
Open `http://<pi5-ip>:8080` and log in as `admin` with `jenkins_admin_password`.

---

## 4. Jenkins UI (one time)

### 4.1 Credentials
**Manage Jenkins → Credentials → System → Global credentials (unrestricted) → Add Credentials**

| ID | Kind | Value |
|---|---|---|
| `gitlab-repo` | Username with password | GitLab username + PAT (`read_repository`) |
| `gitlab-registry-push` | Username with password | deploy-token username + token (read+write registry) |
| `gitlab-registry-pull` | Username with password | deploy-token username + token (read registry) |
| `kubeconfig-cbs-uat` | Secret file | `raspberrypi/output/jenkins/kubeconfig-cbs-uat` |
| `kubeconfig-cbs-prod` | Secret file | `raspberrypi/output/jenkins/kubeconfig-cbs-prod` |
| `cbs-uat-env` | Secret file | your filled-in copy of `deploy/cbs.env.example` with `SPRING_PROFILES_ACTIVE=uat` |
| `cbs-prod-env` | Secret file | same, with `SPRING_PROFILES_ACTIVE=prod` and **different** values |

`deploy.sh` refuses to deploy if the env file's `SPRING_PROFILES_ACTIVE` doesn't match the target, or if any `CHANGE_ME` is left.
After uploading, delete the local env files (`shred -u`).

### 4.2 Pipeline job
**New Item → name `cbs-backend` → Multibranch Pipeline → OK**

- **Branch Sources → Add source → Git**
  - Project Repository: `https://gitlab.com/<namespace>/springboot-core-banking.git`
  - Credentials: `gitlab-repo`
  - Behaviours: *Discover branches* (default) → **Add → Filter by name (with wildcards)** → Include: `main uat feature/* bugfix/*`
- **Build Configuration**: Mode *by Jenkinsfile*, Script Path `Jenkinsfile`
- **Scan Multibranch Pipeline Triggers**: tick *Periodically if not otherwise run* → Interval **2 minutes**
- **Orphaned Item Strategy**: Discard old items, Max # of old items to keep: `10`
- **Save**. Jenkins scans and starts a build for `main` and `uat`.

> Polling needs no inbound access. If you publish Jenkins through Cloudflare Tunnel later, you can add a GitLab webhook and lower the polling interval. Protect the Jenkins hostname with Cloudflare Access.

### 4.3 First run of each branch
The first build of a branch registers the `SECRETS_ONLY` parameter. After that, the branch shows **Build with Parameters**.

---

## 5. Cloudflare Tunnel (public hostnames)

Same pattern as Rancher: the tunnel runs on pi5 and sends traffic to Traefik on port 80.

| Public hostname | Service | Additional settings → HTTP Settings → HTTP Host Header |
|---|---|---|
| `cbs-api-uat.bunlong.uk` | `http://localhost:80` | `cbs-api-uat.bunlong.uk` |
| `cbs-api.bunlong.uk` | `http://localhost:80` | `cbs-api.bunlong.uk` |
| `jenkins.bunlong.uk` *(optional)* | `http://localhost:8080` | — (add a Cloudflare Access policy!) |

The hostnames are set in `deploy/k8s/overlays/<env>/kustomization.yaml` (marked `CHANGE`).
Put the frontend's origin in `CBS_CORS_ORIGINS` inside the env credential.

Test: `curl https://cbs-api-uat.bunlong.uk/api/v1/actuator/health` returns `{"status":"UP",...}`.

---

## 6. Daily workflow

1. `git checkout uat && git pull && git checkout -b feature/loan-fees`
2. Commit and push. Jenkins runs **Secret Scan + Build & Test** (Testcontainers: Postgres, Redis, Mongo, RabbitMQ).
3. Open an MR **feature/loan-fees → uat** and merge it when green. Jenkins then:
   build → multi-arch image → **Trivy gate** → deploy to **cbs-uat** automatically.
4. Testers verify on `https://cbs-api-uat.bunlong.uk` (Swagger is enabled in UAT).
5. Open an MR **uat → main** and merge it. Jenkins builds, runs the Trivy gate, then **waits for approval**.
6. An approver opens the build → **Approve & Deploy**. `deploy.sh` runs `pg_dump` of PROD first, then rolls out.
   If the rollout fails, Jenkins rolls back to the previous version automatically.

Rejecting the approval, or letting it time out after 24 h, deploys nothing.

---

## 7. Change a password / key later (no code change)

1. **Manage Jenkins → Credentials → `cbs-prod-env` → Update → upload the new file**
   (keep a copy of the filled template in your password manager, not on disk)
2. **cbs-backend → main → Build with Parameters → tick `SECRETS_ONLY` → Build**
3. Approve. Jenkins re-creates Secret `cbs-env` and restarts the pods with the **same image**.

UAT works the same way on branch `uat`, without the approval step.
Non-secret settings (replicas, memory, hostnames, JAVA_OPTS) are changed in Git under `deploy/`.

---

## 8. When Trivy blocks a deploy

The **Trivy Image Gate** fails on HIGH/CRITICAL vulnerabilities **that already have a fix**. The image is pushed but not deployed. The log shows a table per platform:

| Library / package | Fix |
|---|---|
| Java dependency (`jackson`, `netty`, `tomcat`, …) | bump the Spring Boot parent or the dependency version in `pom.xml` |
| OS package in `eclipse-temurin:25-jre` | rebuild; the base image gets patched upstream often |
| Not exploitable in CBS | add the CVE to `.trivyignore` with a reason and review date (code review required) |

The **Secret Scan** stage fails when a key or password is committed. Remove it, rotate it, and push again.

---

## 9. Operations

```bash
# on pi5
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl -n cbs-prod get pods,svc,ingress
kubectl -n cbs-prod logs deploy/cbs-backend -f
kubectl -n cbs-prod rollout history deployment/cbs-backend
kubectl -n cbs-prod rollout undo deployment/cbs-backend        # manual rollback (code only, not DB)
```

**Backups (prod)**: one `pg_dump -Fc` per deploy, kept 14 days, stored in PVC `cbs-db-backups`
(K3s local-path, on ONE node: `/var/lib/rancher/k3s/storage/pvc-*_cbs-prod_cbs-db-backups`). Copy them off-node regularly.

Restore (example):
```bash
pg_restore -h <db-host> -U <owner> -d core_banking_restore --no-owner --clean --if-exists \
  core_banking_prod_before_main-17-9f8e7d6c_20261031_101500.dump
```

Restoring after a rollback is a deliberate human decision: Flyway migrations are not undone automatically.
Keep migrations backward-compatible (add columns, don't rename/drop in the same release).

---

## 10. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Build Pod stays *Pending* | k8 node not labelled → `kubectl get nodes -L cbs.ci/agent`; re-run `./terraform_main.sh raspberrypi jenkins` |
| Agent "offline", `Connection refused` to `http://<pi5>:8080` | Jenkins URL wrong or firewall; **Manage Jenkins → Clouds → k3s → Jenkins URL** |
| Testcontainers `Could not find a valid Docker environment` | dind not ready or privileged Pods blocked (Kyverno?) → check the `docker` container log |
| `docker pull` / buildx hangs in dind | MTU — keep `--mtu=1450` in `ci/jenkins-agent-pod.yaml` |
| `ImagePullBackOff` in cbs-uat | `gitlab-registry-pull` token wrong/expired, or `IMAGE_NAME` not lowercase |
| Rollout times out, pod `CrashLoopBackOff` | open the deploy log: it prints pod logs (missing env var, DB unreachable, Flyway checksum) |
| `Env credential has SPRING_PROFILES_ACTIVE='uat' but this is 'prod'` | wrong file uploaded to `cbs-prod-env` |
| 429 on login | nginx brute-force limit (5/min per IP): `deploy/k8s/base/nginx.conf` |
| 404 on `/api/v1/actuator/prometheus` from outside | intended; scrape `cbs-backend.<ns>.svc:8080` inside the cluster |

---

## 11. Design notes (why it is built this way)

- **nginx is a Deployment, not the ingress controller.** The community ingress-nginx project was retired in March 2026 and gets no more security fixes. Traefik (bundled with K3s) stays the ingress, and nginx sits behind it as the API proxy (rate limits, hidden actuator, headers).
- **Multi-arch image, no `RUN` in the Dockerfile.** pi5 is arm64, the k8 node is amd64. Without `RUN`, buildx builds both architectures without QEMU, so pods can run on either node.
- **Secrets only in Jenkins.** One Secret-file credential per environment, turned into a K8s Secret at deploy time. A checksum annotation restarts pods when the secret changes.
- **Namespace-scoped kubeconfigs.** The UAT credential cannot touch PROD, and neither can touch the cluster outside its namespace.
- **Trivy pinned to 0.75.0.** Trivy's release pipeline was compromised in March 2026 (v0.69.4). Releases are immutable now, but pin the image digest as well.
- **Known trade-offs (homelab):** UAT and PROD share one 2-node cluster. Docker-in-Docker build Pods are privileged, which is why they are confined to the agent node. Backups sit on a single node's disk.
