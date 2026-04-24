def call(Map configMap) {
    pipeline {
        agent {
            node {
                label 'AGENT-1'
            }
        }
        environment {
            APPVERSION = ""
            REGION = "us-east-1"
            ACCOUNT_ID = "515138251473"
            COMPONENT = "${configMap.COMPONENT}"
            PROJECT = "roboshop"
            BRANCH = "${configMap.BRANCH}"
            GIT_URL = "${configMap.GIT_URL}"
        }
        options {
            timeout(time:15, unit: 'MINUTES')
            disableConcurrentBuilds()
        }
        stages {
            stage('clean workspace') {
                steps {
                    cleanWs()
                }
            }
            stage('get code') {
                steps {
                    git url: "${GIT_URL}", branch: "${BRANCH}"
                }
            }
            stage('read version') {
                steps {
                    dir("${COMPONENT}") {
                        script {
                            def env.APPVERSION = readFile('version.txt').trim()
                            echo "APPVERSION IS: ${env.APPVERSION}"
                        }
                    }
                }
            }
            stage('build code') {
                steps {
                    dir("${COMPONENT}") {
                        sh "pip3 install -r requirements.txt"
                    }
                }
            }
            stage('sonarqube') {
                steps {
                    dir("${COMPONENT}") {
                        script {
                            def scannerHome = tool 'sonar-8.0'
                            withSonarQubeEnv('sonar-server') {
                                sh "${scannerHome}/bin/sonar-scanner"
                            }
                        }
                    }
                }
            }
            // stage('quality stage') {
            //     steps {
            //         script {
            //             timeout(time:15, unit: 'MINUTES') {
            //                 waitForQualityGate abortPipeline: true
            //             }
            //         }
            //     }
            // }
            stage('image-build') {
                steps {
                    sh """
                    docker build -t ${PROJECT}/${COMPONENT}:${env.APPVERSION}-${BUILD_NUMBER} ./${COMPONENT}
                    docker images
                    """
                }
            }
            // stage('image-scan') {
            //     steps {
            //         sh "trivy image ${PROJECT}/${COMPONENT}:${env.APPVERSION}-${BUILD_NUMBER} > ${COMPONENT}-image-scan-report-txt"
            //     }
            // }
            stage('image push') {
                steps {
                    script {
                        withAWS(region:"${REGION}",credentials:'aws-creds') {
                            sh """
                            aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com
                            docker tag ${PROJECT}/${COMPONENT}:${env.APPVERSION}-${BUILD_NUMBER} ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${PROJECT}/${COMPONENT}:${env.APPVERSION}-${BUILD_NUMBER}
                            docker push ${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${PROJECT}/${COMPONENT}:${env.APPVERSION}-${BUILD_NUMBER}
                            """
                        }
                    }
                }
            }
            stage('trigger deployment') {
                steps {
                    build job: "${COMPONENT}-cd-pipeline",
                    propagate: false,
                    wait: false
                }
            }
        }
    }
}