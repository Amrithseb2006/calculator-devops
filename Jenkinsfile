pipeline {
    agent any

    // Names must match what you configure in Manage Jenkins -> Tools
    tools {
        maven 'Maven3'
        jdk   'JDK17'
    }

    environment {
        // CHANGE ME: your Docker Hub username
        DOCKERHUB_USER = 'your-dockerhub-username'
        IMAGE_NAME     = "${DOCKERHUB_USER}/devops-demo"
        IMAGE_TAG      = "${env.BUILD_NUMBER}"
        // Jenkins credentials IDs (create them in Manage Jenkins -> Credentials)
        DOCKERHUB_CREDS = 'dockerhub-creds'   // Username with password (Docker Hub access token)
        EC2_SSH_CREDS   = 'ec2-ssh-key'       // SSH Username with private key (user: ubuntu)
        APP_IP_CREDS    = 'app-server-ip'     // Secret text: public/Elastic IP of the app EC2
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    triggers {
        // Fallback if you don't set up the GitHub webhook: poll every 5 minutes
        pollSCM('H/5 * * * *')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Unit Test') {
            steps {
                sh 'mvn -B clean verify'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
                sh 'mvn -B -DskipTests package'
                archiveArtifacts artifacts: 'target/devops-demo.jar', fingerprint: true
            }
        }

        stage('Docker Build') {
            steps {
                sh """
                  docker build --build-arg APP_VERSION=${IMAGE_TAG} \
                    -t ${IMAGE_NAME}:${IMAGE_TAG} \
                    -t ${IMAGE_NAME}:latest .
                """
            }
        }

        stage('Docker Push') {
            steps {
                withCredentials([usernamePassword(credentialsId: "${DOCKERHUB_CREDS}",
                                                  usernameVariable: 'DH_USER',
                                                  passwordVariable: 'DH_PASS')]) {
                    sh '''
                      echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin
                      docker push ${IMAGE_NAME}:${IMAGE_TAG}
                      docker push ${IMAGE_NAME}:latest
                      docker logout
                    '''
                }
            }
        }

        stage('Deploy to AWS (Ansible)') {
            // Only deploy builds of the main branch (works for plain and multibranch jobs)
            when {
                expression { env.BRANCH_NAME == 'main' || env.GIT_BRANCH in ['origin/main', 'main'] }
            }
            steps {
                withCredentials([
                    sshUserPrivateKey(credentialsId: "${EC2_SSH_CREDS}", keyFileVariable: 'SSH_KEY'),
                    string(credentialsId: "${APP_IP_CREDS}", variable: 'APP_IP')
                ]) {
                    sh '''
                      # Build the inventory at runtime so no IPs/keys live in Git
                      printf "[app_servers]\\napp1 ansible_host=%s\\n\\n[app_servers:vars]\\nansible_user=ubuntu\\n" "$APP_IP" > ansible/inventory.ini

                      export ANSIBLE_HOST_KEY_CHECKING=False
                      ansible-playbook -i ansible/inventory.ini ansible/deploy.yml \
                        --private-key "$SSH_KEY" \
                        -e "image_name=${IMAGE_NAME}" \
                        -e "image_tag=${IMAGE_TAG}"
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline OK: ${IMAGE_NAME}:${IMAGE_TAG} deployed."
        }
        failure {
            echo 'Pipeline FAILED - check the stage logs above.'
        }
        always {
            // keep the Jenkins disk from filling up with old images
            sh 'docker image prune -f || true'
            cleanWs()
        }
    }
}
