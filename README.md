# devops-demo: Full CI/CD Pipeline (Git → Jenkins → Maven → Docker → Ansible → AWS)

A small Java web service plus everything needed to build, test, containerise and deploy it automatically
to AWS EC2 every time you push code.

## 1. Architecture

```
 Developer ──git push──▶ GitHub ──webhook / poll──▶ Jenkins (EC2 #1)
                                                      │
                          1. Checkout   (Git)         │
                          2. Build+Test (Maven)       │
                          3. Package    (Maven jar)   │
                          4. Docker build             │
                          5. Docker push ─────────────┼──▶ Docker Hub
                          6. ansible-playbook ────────┘
                                   │ SSH
                                   ▼
                          App server (EC2 #2)  ──pull image──▶ Docker Hub
                          runs container on port 80 ──▶ http://<APP_IP>/health
```

| Tool    | Role in this project                                              |
|---------|-------------------------------------------------------------------|
| Git     | Source control; GitHub triggers the pipeline                      |
| Maven   | Compiles, runs JUnit tests, builds `target/devops-demo.jar`       |
| Docker  | Packages the jar into an image, published to Docker Hub           |
| Jenkins | Orchestrates every stage (`Jenkinsfile`)                          |
| Ansible | Installs Docker on the app server and deploys the new container   |
| AWS EC2 | Hosts Jenkins (server 1) and the running application (server 2)  |

## 2. Project layout

```
devops-demo/
├── pom.xml                         Maven build (Java 17, JUnit 5)
├── src/main/java/com/devops/
│   ├── App.java                    HTTP service (JDK built-in server, no framework)
│   └── Calculator.java             Business logic
├── src/test/java/com/devops/
│   ├── CalculatorTest.java         Unit tests
│   └── AppTest.java                HTTP endpoint tests
├── Dockerfile                      Runtime image (jre-alpine, non-root, healthcheck)
├── Jenkinsfile                     The pipeline
├── ansible/
│   ├── ansible.cfg
│   ├── inventory.ini.example       For manual runs
│   ├── setup-server.yml            One-time: install Docker on the app server
│   └── deploy.yml                  Every build: pull image + run container + health check
├── .gitignore  /  .dockerignore
└── README.md
```

### Endpoints

| Path                | Example response                                          |
|---------------------|-----------------------------------------------------------|
| `GET /`             | `{"app":"devops-demo","version":"1.0.0","message":"..."}` |
| `GET /health`       | `{"status":"UP"}`                                         |
| `GET /add?a=2&b=3`  | `{"result":5}`                                            |
| `GET /multiply?a=3&b=4` | `{"result":12}`                                       |
| `GET /divide?a=10&b=4`  | `{"result":2.5}` (400 on divide by zero)              |

---

## 3. Step 0: Prerequisites

* A GitHub account and a Docker Hub account (create a Docker Hub **access token**: Account Settings → Security → New Access Token, Read & Write).
* An AWS account (Free Tier is fine for the app server; see cost note in Step 3).
* On your laptop: Git, Java 17+, Maven 3.9+, Docker (optional, for local tests).

## 4. Step 1: Run it locally (Maven)

```bash
cd devops-demo
mvn clean test          # compile + run all unit tests
mvn clean package       # builds target/devops-demo.jar
java -jar target/devops-demo.jar
# in another terminal:
curl localhost:8080/health
curl "localhost:8080/add?a=2&b=3"
```

## 5. Step 2: Git + GitHub

```bash
cd devops-demo
git init -b main
git add .
git commit -m "Initial commit: Java app + CI/CD pipeline"
```

Create an **empty** repository on GitHub (e.g. `devops-demo`), then:

```bash
git remote add origin https://github.com/<your-user>/devops-demo.git
git push -u origin main
```

Typical workflow afterwards: `git checkout -b feature/x` → commit → open PR → merge to `main` → pipeline deploys.

## 6. Step 3: AWS infrastructure (console)

**Region:** pick one (e.g. `ap-south-1` Mumbai) and use it for everything.

1. **Key pair**: EC2 → Key Pairs → Create key pair → name `devops-key`, type RSA, format `.pem`. Download it (you can't download it again).
   ```bash
   chmod 400 devops-key.pem
   ```
2. **Security groups** (EC2 → Security Groups → Create):

   | Group        | Inbound rules                                                                                           |
   |--------------|---------------------------------------------------------------------------------------------------------|
   | `jenkins-sg` | SSH 22 from **My IP**; Custom TCP **8080** from **My IP** (see webhook note below)                      |
   | `app-sg`     | SSH 22 from **My IP**; SSH 22 from **`jenkins-sg`** (source = that security group); HTTP **80** from `0.0.0.0/0` |

3. **Launch two EC2 instances** (Ubuntu Server 24.04 LTS, key pair `devops-key`):

   | Name         | Type                               | Security group | Storage |
   |--------------|------------------------------------|----------------|---------|
   | `jenkins-server` | `t3.small` or `t3.medium` (Jenkins + Maven + Docker needs ≥2 GB RAM; a 1 GB t2.micro will struggle) | `jenkins-sg` | 20 GB |
   | `app-server`     | `t2.micro` / `t3.micro` (Free Tier eligible, depending on your account) | `app-sg` | 8–10 GB |

4. Note both **public IPs**. They change on stop/start; allocate an **Elastic IP** to each if you plan to stop and restart them (EC2 → Elastic IPs).

> **Cost note:** the Jenkins instance is not free-tier. Stop or terminate both instances when you're done (Step 12). Public IPv4 addresses are billed hourly by AWS.

> **Webhook note:** GitHub webhooks need Jenkins reachable from the internet on 8080. If you'd rather not open 8080 to the world, skip the webhook; the `Jenkinsfile` already polls Git every 5 minutes (`pollSCM`), which works with 8080 restricted to your IP.

## 7. Step 4: Set up the Jenkins server

```bash
ssh -i devops-key.pem ubuntu@<JENKINS_IP>
```

### 4.1 Base packages: Java, Git, Maven, Docker, Ansible

```bash
sudo apt update && sudo apt -y upgrade
sudo apt install -y openjdk-17-jdk git maven docker.io python3-docker software-properties-common fontconfig
sudo add-apt-repository -y --update ppa:ansible/ansible
sudo apt install -y ansible
ansible-galaxy collection install community.docker     # modules used by deploy.yml
java -version && mvn -v && docker --version && ansible --version
```

### 4.2 Install Jenkins (LTS)

Jenkins signs its apt repository with a key whose filename changes over time, so copy the exact **"Debian/Ubuntu → Long Term Support release"** commands from the official page: <https://www.jenkins.io/doc/book/installing/linux/>. They look like:

```bash
sudo wget -O /etc/apt/keyrings/jenkins-keyring.asc <KEY_URL_FROM_OFFICIAL_PAGE>
echo "deb [signed-by=/etc/apt/keyrings/jenkins-keyring.asc] https://pkg.jenkins.io/debian-stable binary/" | sudo tee /etc/apt/sources.list.d/jenkins.list > /dev/null
sudo apt update
sudo apt install -y jenkins
sudo systemctl enable --now jenkins
```

### 4.3 Let Jenkins use Docker

```bash
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
sudo -u jenkins docker ps          # must NOT say "permission denied"
```

### 4.4 First login

1. Open `http://<JENKINS_IP>:8080`.
2. Unlock with: `sudo cat /var/lib/jenkins/secrets/initialAdminPassword`
3. **Install suggested plugins**, create the admin user.
4. **Manage Jenkins → Plugins → Available plugins**, make sure these are installed: *Pipeline*, *Git*, *GitHub*, *Credentials Binding*, *SSH Agent* (optional), *JUnit*, *Timestamper*, *Workspace Cleanup*.

### 4.5 Configure tools (names must match the Jenkinsfile)

**Manage Jenkins → Tools**
* **JDK** → Add JDK: name `JDK17`, untick *Install automatically*, `JAVA_HOME` = `/usr/lib/jvm/java-17-openjdk-amd64`
* **Maven** → Add Maven: name `Maven3`, untick *Install automatically*, `MAVEN_HOME` = `/usr/share/maven`

### 4.6 Add credentials

**Manage Jenkins → Credentials → System → Global credentials → Add Credentials**

| Kind                        | ID                | Value                                                              |
|-----------------------------|-------------------|--------------------------------------------------------------------|
| Username with password      | `dockerhub-creds` | Docker Hub username + **access token** as password                |
| SSH Username with private key | `ec2-ssh-key`   | Username `ubuntu`; Enter directly → paste the full contents of `devops-key.pem` |
| Secret text                 | `app-server-ip`   | Public/Elastic IP of `app-server`                                  |

## 8. Step 5: Prepare the app server with Ansible (one-time)

From the **Jenkins server** (or your laptop with Ansible installed). Copy the key there first if needed (`scp -i devops-key.pem devops-key.pem ubuntu@<JENKINS_IP>:~/`; then `chmod 400 ~/devops-key.pem`).

```bash
git clone https://github.com/<your-user>/devops-demo.git
cd devops-demo/ansible
cp inventory.ini.example inventory.ini
nano inventory.ini                      # set EC2_PUBLIC_IP to the app-server IP

ansible app_servers -m ping --private-key ~/devops-key.pem            # expect "pong"
ansible-playbook setup-server.yml --private-key ~/devops-key.pem      # installs Docker
```

(Optional manual deploy test, before involving Jenkins:)
```bash
ansible-playbook deploy.yml --private-key ~/devops-key.pem \
  -e image_name=<dockerhub-user>/devops-demo -e image_tag=latest
```
(This only works once an image exists on Docker Hub, i.e. after the first successful pipeline run.)

## 9. Step 6: Edit the Jenkinsfile

In `Jenkinsfile`, change:

```groovy
DOCKERHUB_USER = 'your-dockerhub-username'
```

Commit and push:

```bash
git add Jenkinsfile && git commit -m "Set Docker Hub user" && git push
```

## 10. Step 7: Create the Jenkins pipeline job

1. **New Item** → name `devops-demo` → **Pipeline** → OK.
2. *Build Triggers*: tick **GitHub hook trigger for GITScm polling** (if you use the webhook).
3. *Pipeline* → **Definition: Pipeline script from SCM** → SCM: **Git**
   * Repository URL: `https://github.com/<your-user>/devops-demo.git` (public repo needs no credentials; for a private repo add a GitHub PAT credential)
   * Branch Specifier: `*/main`
   * Script Path: `Jenkinsfile`
4. Save → **Build Now**.

**GitHub webhook (optional, instant triggers):** GitHub repo → Settings → Webhooks → Add webhook → Payload URL `http://<JENKINS_IP>:8080/github-webhook/`, content type `application/json`, event "Just the push event". Requires port 8080 open to GitHub (see the webhook note in Step 3).

## 11. Step 8: Verify the deployment

After a green build:

```bash
curl http://<APP_SERVER_IP>/health      # {"status":"UP"}
curl http://<APP_SERVER_IP>/            # shows version = Jenkins build number
curl "http://<APP_SERVER_IP>/add?a=40&b=2"
```

Now prove the pipeline end-to-end: change the message in `App.java`, update a test if needed, then

```bash
git add -A && git commit -m "Change greeting" && git push
```

Jenkins builds, tests, builds and pushes the image, and Ansible replaces the container. Re-run the `curl` to see the new version.

### What each pipeline stage does

| Stage                 | Command / action                                              | Fails when…                         |
|-----------------------|---------------------------------------------------------------|-------------------------------------|
| Checkout              | `checkout scm`                                                | Repo/credentials wrong              |
| Build & Unit Test     | `mvn clean verify` + publishes JUnit report                   | Compile error or failing test       |
| Package               | `mvn package`, archives the jar                               | Build problem                       |
| Docker Build          | `docker build -t user/devops-demo:<build#> -t …:latest .`     | Jenkins user not in `docker` group  |
| Docker Push           | `docker login` + `docker push`                                | Bad Docker Hub token                |
| Deploy (Ansible)      | `ansible-playbook deploy.yml -e image_tag=<build#>`           | SSH/SG problem, Docker missing on app server |

The deploy stage only runs for the `main` branch.

## 12. Step 9: Clean up (avoid AWS charges)

* EC2 → Instances → select both → **Instance state → Terminate**
* EC2 → Elastic IPs → **Release** any you allocated
* Delete the key pair and security groups if you no longer need them

## 13. Troubleshooting

| Symptom | Fix |
|---------|-----|
| `docker: permission denied` in Jenkins | `sudo usermod -aG docker jenkins && sudo systemctl restart jenkins` |
| `Tool type "maven" does not have an install of "Maven3"` | Tool names in Manage Jenkins → Tools must be exactly `Maven3` and `JDK17` |
| Ansible `UNREACHABLE` / timeout | `app-sg` must allow SSH from `jenkins-sg`; check the IP in the `app-server-ip` credential |
| Ansible: `couldn't resolve module community.docker…` | On the Jenkins server run `ansible-galaxy collection install community.docker` **as the `jenkins` user too**: `sudo -u jenkins ansible-galaxy collection install community.docker` |
| Ansible: `Failed to import the required Python library (Docker SDK)` | Re-run `setup-server.yml` (installs `python3-docker`) |
| `pull access denied` on the app server | Image name/tag wrong, or the Docker Hub repo is private (make it public or add a `docker_login` task) |
| App not reachable on port 80 | `app-sg` needs HTTP 80 from `0.0.0.0/0`; on the server run `sudo docker ps` and `sudo docker logs devops-demo` |
| Jenkins runs out of memory / hangs | Use `t3.small` or larger, or add swap: `sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile` |
| `when` stage skipped | Deploy only runs when the branch is `main`; check the Branch Specifier |

## 14. Ideas to extend it

* Replace Docker Hub with **Amazon ECR** and an IAM role on the instances
* Put an **Application Load Balancer** in front and deploy to two instances
* Provision the AWS resources with **Terraform**
* Add code-quality gates (SonarQube) and image scanning (Trivy) as extra stages
* Add Slack/email notifications in the `post` block
