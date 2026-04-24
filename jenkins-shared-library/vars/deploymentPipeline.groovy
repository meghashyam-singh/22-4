def call(configMap) {
    pipeline {
        agent {
            node {
                label 'AGENT-1'
            }
        }
        environment {
            REGION = "us-east-1"
            COMPONENT = "${configMap.COMPONENT}"
            CLUSTER_NAME = "roboshop-cluster"
            BRANCH = "${configMap.BRANCH}"
            GIT_URL = "${configMap.GIT_URL}"
        }
        stages {
            stage('get code') {
                steps {
                    git url: "${GIT_URL}", branch: "${BRANCH}"
                }
            }
            stage('deployment') {
                steps {
                    dir("${COMPONENT}") {
                        script {
                            withAWS(region:"${REGION}",credentials:'aws-creds') {
                                sh """
                                aws eks update-kubeconfig --region ${REGION} --name ${CLUSTER_NAME}
                                kubectl apply -f manifestfile.yaml
                                """
                            }
                        }
                    }
                }
            }
            stage('health check') {
                steps {
                    withAWS(region:"${REGION}",credentials:'aws-creds') {
                        sh " kubectl rollout status deployment ${COMPONENT} -n roboshop"
                    }
                }
            }
        }
    }
}