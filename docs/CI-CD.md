# CBS Backend — CI/CD (GitLab → Jenkins → K3s)

```
feature/* ──MR──▶ uat ──MR──▶ main
  build+test        build+test, image, Trivy     build+test, image, Trivy
                    → deploy cbs-uat             → APPROVAL → pg_dump → deploy cbs-prod
```

```
gitlab.com (repo + registry) ──push mirror──▶ github.com (copy only, never push there)
      │ Jenkins polls every 2 min
pi5:  Jenkins controller ──▶ build Pod on k8 node (maven | docker | trivy)
                                   │ kubectl
K3s:  cbs-uat / cbs-prod :  Cloudflare Tunnel → Traefik → cbs-nginx → cbs-backend
      cbs-data           :  PostgreSQL · MongoDB · Redis · RabbitMQ (shared, per-env DB/user)
```

## Files

| File | What it does |
|---|---|
| `Jenkinsfile` | The whole pipeline: build Pod YAML, tests, image, Trivy, deploy script, approval |
| `Dockerfile` | Runtime image, multi-arch (amd64 + arm64), no `RUN` steps |
| `.trivyignore` | Reviewed CVE exceptions for the Trivy gate (reason + review date per line) |
| `deploy/k8s/base/` | `backend.yaml` (Spring Boot), `nginx.yaml` + `nginx.conf` (proxy, rate limits), Ingress |
| `deploy/k8s/overlays/uat` | namespace `cbs-uat`, host `cbs-api-uat.bunlong.uk`, 1 replica |
| `deploy/k8s/overlays/prod` | namespace `cbs-prod`, host `cbs-api.bunlong.uk`, 2 replicas, PDB, backup PVC |
| `deploy/cbs.env.example` | Every field the app reads (UAT values, `uat → prod` notes); copy twice, replace `CHANGE_ME`, upload as `cbs-uat-env` / `cbs-prod-env` |

Infrastructure lives in **Terraform-Plan**:
- `jenkins.yml`: Jenkins, the `cbs-uat`/`cbs-prod` namespaces, and their kubeconfigs.
- `postgres.yml`, `mongodb.yml`, `redis.yml`, `rabbitmq.yml`: the databases (namespace `cbs-data`).
- Each database playbook writes `access-info/cbs-data/<db>-access-info.txt` with logins and the exact lines for `cbs-uat.env` / `cbs-prod.env`.

## One-time setup

1. **Terraform-Plan**
   ```bash
   ./terraform_main.sh raspberrypi rancher     # 2) multi-node
   ./terraform_main.sh raspberrypi jenkins
   ./terraform_main.sh raspberrypi postgres
   ./terraform_main.sh raspberrypi mongodb
   ./terraform_main.sh raspberrypi redis
   ./terraform_main.sh raspberrypi rabbitmq
   ```
   Outputs on your machine:
   - `raspberrypi/output/jenkins/kubeconfig-cbs-{uat,prod}`
   - `ansible-common/access-info/cbs-data/<db>-access-info.txt`: logins + the lines to paste into the env files.
   Then make `cbs-uat.env` and `cbs-prod.env` from `deploy/cbs.env.example`:
   copy it twice outside the repo, change the `uat → prod` lines in the prod copy, and replace every `CHANGE_ME` (database passwords from the access-info files, the rest with `openssl rand`).
2. **GitLab**
   - **Protected branches:** `main` push = No one; `uat` push = Maintainers.
   - **Deploy tokens:** `jenkins-push` (read+write registry) and `k3s-pull` (read registry).
   - **Access token:** `jenkins-repo` (read_repository).
   - **Mirroring:** push to GitHub.
3. **Jenkins → Credentials (Global)**:
   - `gitlab-repo`, `gitlab-registry-push`, `gitlab-registry-pull`: Username+Password.
   - `kubeconfig-cbs-uat`, `kubeconfig-cbs-prod`, `cbs-uat-env`, `cbs-prod-env`: Secret file.
4. **Jenkins job**: New Item `cbs-backend`, Multibranch Pipeline.
   - Branch source: Git `https://gitlab.com/BunlongCHEA/SpringBoot-Core-Banking.git`, credential `gitlab-repo`.
   - Behaviours: **Discover branches** + **Filter by name** `main uat feature/* bugfix/*`.
   - Scan periodically: every 2 min.
5. **Cloudflare Tunnel**:
   - `cbs-api-uat.bunlong.uk` → `http://localhost:80`, Host Header `cbs-api-uat.bunlong.uk`.
   - `cbs-api.bunlong.uk` → `http://localhost:80`, Host Header `cbs-api.bunlong.uk`.

## Daily use

- Push `feature/x` → MR to `uat` → deployed to UAT automatically.
- MR `uat` → `main` → open the build → **Approve & Deploy** → PROD (DB backed up first).
- **Change a password:** delete its file in `access-info/cbs-data/secrets/`, re-run that database's playbook, put the new value in your env file, then **Update** the Jenkins credential → **Build with Parameters → SECRETS_ONLY**.
- **Roll back:** `sudo kubectl -n cbs-prod rollout undo deploy/cbs-backend` (code only; DB migrations stay).
- **Backups (prod):** PVC `cbs-db-backups`, one `pg_dump` per prod deploy, kept 14 days.
- **Trivy blocks:** upgrade the dependency in `pom.xml`, or add the CVE to `.trivyignore` with a reason.
