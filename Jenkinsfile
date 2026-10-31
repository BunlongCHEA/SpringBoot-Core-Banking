// =====================================================================
// CBS Spring Boot backend — Jenkins pipeline (Multibranch, GitLab)
// ---------------------------------------------------------------------
//  feature/*, bugfix/*, MRs  →  Build & Test only (fast feedback)
//  uat                       →  Build & Test → Image → AUTO deploy to UAT
//  main                      →  Build & Test → Image → APPROVAL → deploy to PROD
//
//  CHANGE the five values marked CHANGE in `environment` before first use.
//  Credentials referenced here (create them in Jenkins first):
//    gitlab-registry  Username+Password  GitLab deploy token (read/write_registry)
//    uat-ssh          SSH private key    'deploy' user on the UAT server
//    prod-ssh         SSH private key    'deploy' user on the PROD server
//  Plugins: GitLab Branch Source, SSH Agent, Pipeline (suggested set)
// =====================================================================

pipeline {
    // No global agent: the approval stage must NOT hold a build executor
    // while it waits (possibly hours) for a human.
    agent none

    options {
        timestamps()
        disableConcurrentBuilds()                     // one build per branch at a time
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    environment {
        REGISTRY   = 'registry.gitlab.example.com'               // CHANGE: your GitLab registry host
        IMAGE_NAME = "${REGISTRY}/bank/cbs-backend"              // CHANGE: <group>/<project>
        UAT_HOST   = 'deploy@10.0.10.21'                         // CHANGE: UAT server
        PROD_HOST  = 'deploy@10.0.20.21'                         // CHANGE: Production server
        // Who may click "Approve & Deploy": comma-separated Jenkins user IDs
        // (or LDAP/AD group names if Jenkins uses LDAP/AD login)
        PROD_APPROVERS = 'admin'                                 // CHANGE
    }

    stages {

        // -------------------------------------------------------------
        // CI — runs for EVERY branch and merge request
        // -------------------------------------------------------------
        stage('CI') {
            agent any
            stages {

                stage('Prepare') {
                    steps {
                        script {
                            def shortSha  = sh(script: 'git rev-parse --short=8 HEAD', returnStdout: true).trim()
                            def safeBranch = env.BRANCH_NAME.replaceAll('[^a-zA-Z0-9_.-]', '-')
                            // e.g. uat-42-1a2b3c4d  /  main-17-9f8e7d6c
                            env.IMAGE_TAG = "${safeBranch}-${env.BUILD_NUMBER}-${shortSha}"
                            currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.IMAGE_TAG}"
                        }
                        sh 'java -version && docker version --format "Docker {{.Server.Version}}"'
                    }
                }

                stage('Build & Test') {
                    options { timeout(time: 30, unit: 'MINUTES') }
                    steps {
                        sh 'chmod +x mvnw'
                        // compile + unit tests + Testcontainers context test + package JAR
                        sh './mvnw -B -ntp clean verify'
                    }
                    post {
                        always {
                            junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                        }
                    }
                }

                stage('Docker Build & Push') {
                    when { anyOf { branch 'uat'; branch 'main' } }
                    options { timeout(time: 15, unit: 'MINUTES') }
                    steps {
                        withCredentials([usernamePassword(credentialsId: 'gitlab-registry',
                                                          usernameVariable: 'REG_USER',
                                                          passwordVariable: 'REG_PASS')]) {
                            sh '''
                                set -e
                                echo "$REG_PASS" | docker login "$REGISTRY" -u "$REG_USER" --password-stdin
                                docker build \
                                  --label "org.opencontainers.image.revision=$GIT_COMMIT" \
                                  --label "org.opencontainers.image.source=$GIT_URL" \
                                  -t "$IMAGE_NAME:$IMAGE_TAG" .
                                docker push "$IMAGE_NAME:$IMAGE_TAG"
                                docker logout "$REGISTRY"
                                docker image rm "$IMAGE_NAME:$IMAGE_TAG" || true
                            '''
                        }
                    }
                }

                stage('Deploy to UAT') {
                    when { branch 'uat' }
                    options { timeout(time: 10, unit: 'MINUTES') }
                    steps {
                        sshagent(credentials: ['uat-ssh']) {
                            sh 'ssh -o StrictHostKeyChecking=accept-new "$UAT_HOST" "/opt/cbs/deploy.sh $IMAGE_TAG"'
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // CD to PRODUCTION — main branch only, human approval required
        // -------------------------------------------------------------
        stage('Approval for Production') {
            when { branch 'main' }
            // no agent here on purpose: waiting costs no executor
            steps {
                timeout(time: 24, unit: 'HOURS') {
                    script {
                        env.APPROVED_BY = input(
                            message: "Deploy ${env.IMAGE_TAG} to PRODUCTION?",
                            ok: 'Approve & Deploy',
                            submitter: env.PROD_APPROVERS,        // only these users can approve
                            submitterParameter: 'APPROVER'
                        )
                    }
                    echo "Approved by: ${env.APPROVED_BY}"
                }
            }
        }

        stage('Deploy to Production') {
            when { branch 'main' }
            agent any
            options {
                skipDefaultCheckout()                             // nothing from Git is needed here
                timeout(time: 20, unit: 'MINUTES')
            }
            steps {
                sshagent(credentials: ['prod-ssh']) {
                    // deploy.sh on the prod server backs up the DB first, then deploys
                    sh 'ssh -o StrictHostKeyChecking=accept-new "$PROD_HOST" "/opt/cbs/deploy.sh $IMAGE_TAG"'
                }
            }
        }
    }

    post {
        success {
            echo "SUCCESS: ${env.BRANCH_NAME} ${env.IMAGE_TAG ?: ''}"
        }
        failure {
            echo "FAILED: ${env.BRANCH_NAME} — open the stage log above to see why."
        }
        aborted {
            echo "ABORTED: approval rejected or timed out — nothing was deployed to Production."
        }
    }
}