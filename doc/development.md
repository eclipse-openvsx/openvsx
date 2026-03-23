## Quick Start

### Using Gitpod

To get started quickly, it is recommended to use Gitpod as default with the development environment already configured.

[![Open in Gitpod](https://gitpod.io/button/open-in-gitpod.svg)](https://gitpod.io/#https://github.com/eclipse-openvsx/openvsx)

### Using Red Hat OpenShift Dev Spaces

Open a development environment in Red Hat OpenShift Dev Spaces. 

[![Open in Dev Spaces](https://www.eclipse.org/che/contribute.svg)](https://workspaces.openshift.com#https://github.com/eclipse-openvsx/openvsx)

In the workspace, you'll find a set of predefined commands from the devfile.yaml that will assist you in building, running, and testing the application:
* 1.1. Build OVSX CLI
* 1.2. Build UI Component
* 1.3. Build Server Component
* 1.4. Run OpenVSX Server
* 1.5. Run OpenVSX WebUI
* 1.6. Publish extensions by OVSX CLI

To execute any of these commands within your workspace, navigate to Terminal -> Run Task -> devfile.

### Using Docker Compose

To run the Open VSX registry in a development environment, you can use `docker compose` by following these steps:

 * Verify Docker Compose is installed by running `docker compose version`. If an error occurs, you may need to [install docker compose](https://docs.docker.com/compose/install/) on your machine.
 * Decide which profile(s) to run based on your needs. The [docker-compose.yml] file defines profiles for specific components:
   * `db`: Starts the PostgreSQL container.
   * `es`: Starts the Elasticsearch container.
   * `debug`: Starts the PostgreSQL and Elasticsearch containers, which suits running the OpenVSX server and web UI locally for easier debugging.
   * `backend`: Starts the OpenVSX server container (java).
   * `frontend`: Starts the web UI container.
   * `commandline`: Starts a container with the OpenVSX CLI tools.
   * `openvsx`: Combines `backend`, `frontend`, and `commandline` profiles to start all related services.
   * `kibana`: Starts a kibana instance for easier access to the Elasticsearch service.
 * In the project root, initiate Docker Compose:
   * Without profiles: `docker compose up`.
   * With profiles: `docker compose --profile <profile_name> up`. Use multiple `--profile` flags for multiple profiles, e.g., `docker compose --profile openvsx --profile kibana up`.

 * Depending on which profile(s) you selected, after some seconds, the respective services become available:
   * registry backend is available at [http://localhost:8080/](http://localhost:8080/) if the `backend` or `openvsx` profile was selected.
   * web ui is available at [http://localhost:3000/](http://localhost:3000/) if the `frontend` or `openvsx` profile was selected.
   * kibana is exposed at [http://localhost:5601/](http://localhost:5601/) if the `kibana` profile was selected.
 * Open VSX CLI commands can be run via `docker compose exec cli lib/ovsx` if the `commandline` or `openvsx` profile was selected.
 * To load some extensions from the main registry (openvsx.org), run `docker compose exec cli yarn load-extensions <N>`, where N is the number of extensions you would like to publish in your local registry.
 * For troubleshooting or manual intervention, access a service's interactive shell with `docker compose run --rm <service> /bin/bash`. Service names are listed in the [docker-compose.yml](docker-compose.yml) file.

### Setup locally on WSL

- Install WSL

  - [https://learn.microsoft.com/en-us/windows/wsl/setup/environment?source=recommendations](https://learn.microsoft.com/en-us/windows/wsl/setup/environment?source=recommendations)

- Make sure the firewall is not blocking internet access on WSL2 - need to check every time before running the application or installing new tools.

  - Try to ping a random website, for example, "ping -c 3 [www.google.ca](http://www.google.ca)", if fails, proceed below
  - sudo vi /etc/resolv.conf
  - Change nameserver to 8.8.8.8
  - Ping google again to see if it works this time
  - Related issue: [https://github.com/microsoft/WSL/issues/3669](https://github.com/microsoft/WSL/issues/3669)
  - Solution that I'm following: [https://stackoverflow.com/questions/60269422/windows10-wsl2-ubuntu-debian-no-network](https://stackoverflow.com/questions/60269422/windows10-wsl2-ubuntu-debian-no-network)

- clone the repository from github
- set up vscode on WSL

  - [https://learn.microsoft.com/en-us/windows/wsl/tutorials/wsl-vscode](https://learn.microsoft.com/en-us/windows/wsl/tutorials/wsl-vscode)

- Install postgres

  - sudo apt update
  - sudo apt install postgresql postgresql-contrib
  - sudo service postgresql start
  - sudo service postgresql status (this is to check if the service is really on)
  - sudo -u postgres psql (connect to the postgres service)
    - CREATE ROLE gitpod with LOGIN PASSWORD 'gitpod';
  - [https://learn.microsoft.com/en-us/windows/wsl/tutorials/wsl-database](https://learn.microsoft.com/en-us/windows/wsl/tutorials/wsl-database)

- Install node.js using nvm

  - [https://docs.microsoft.com/en-us/windows/dev-environment/javascript/nodejs-on-wsl](https://docs.microsoft.com/en-us/windows/dev-environment/javascript/nodejs-on-wsl)

- Set up JDK 25
  - sudo apt install -y wget apt-transport-https
  - mkdir -p /etc/apt/keyrings
  - wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | tee /etc/apt/keyrings/adoptium.asc
  - echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list
  - sudo apt update
  - sudo apt install temurin-25-jdk

- Install docker

  - [https://learn.microsoft.com/en-us/windows/wsl/tutorials/wsl-containers](https://learn.microsoft.com/en-us/windows/wsl/tutorials/wsl-containers)
  - [https://www.youtube.com/watch?v=idW-an99TAM](https://www.youtube.com/watch?v=idW-an99TAM)

- Install Elasticsearch with Docker

  - sudo docker pull elasticsearch:8.18.8
  - sudo docker run -d --name elasticsearch  -p 9200:9200 -p 9300:9300 -e "discovery.type=single-node" -e "xpack.ml.enabled=false" -e "xpack.security.enabled=false" elasticsearch:8.7.1

### Setup locally on macOS

- Set up postgreSQL using Homebrew

  - brew install postgresql@16

  - Use `postgres –V` to check if it is the correct version

  - brew services start postgresql@16

  - From the terminal, run the command `createdb postgres` (NOT inside `psql`)

  - `psql –d postgres` to enter the database

  - CREATE ROLE gitpod with LOGIN PASSWORD 'gitpod';

  - Using \d to display all tables and relations, now empty

- Make sure the correct Java version is being used

  - Download Java 25 if you don't have it already: https://adoptium.net/temurin/releases/

  - Run the command `/usr/libexec/java_home –V` to see your matching Java virtual machines

  - Pick Java 25 accordingly

  - export JAVA_HOME='/usr/libexec/java_home –v 25.0.1+8'

  - Run `java –version` to check if Java 25 is indeed being used

  - https://stackoverflow.com/questions/21964709/how-to-set-or-change-the-default-java-jdk-version-on-macos

- Set up Elasticsearch using Docker

- Download Docker Desktop from https://www.docker.com/

- Download the Elasticsearch image from Docker Hub and enable it in the Docker Desktop

### Run the application locally

- cd cli

  - yarn install
  - yarn build

- cd server

  - ./scripts/generate-properties.sh
  - ./gradlew build
  - ./gradlew runServer

- cd webui
  - yarn install
  - yarn build
  - yarn build:default
  - yarn start:default
- Go to localhost:3000 on browser and it should be up and running

### Optional: Deploy example extensions to your local registry

Run:

- in project root (the server application must be running):
  `./server/scripts/upload-test-extensions.sh`

or

- in `server/`:  
  `gradlew downloadTestExtensions` to download vsix files from the official store and from Github.
- in project root (the server application must be running):
  ```bash
  export OVSX_REGISTRY_URL=http://localhost:8080
  export OVSX_PAT=super_token
  export PUBLISHERS="DotJoshJohnson eamodio felixfbecker formulahendry HookyQR ms-azuretools ms-mssql ms-python ms-vscode octref redhat ritwickdey sburg vscode vscodevim Wscats"
  for pub in $PUBLISHERS; do cli/lib/ovsx create-namespace $pub; done
  find server/build/test-extensions-builtin -name '*.vsix' -exec cli/lib/ovsx publish '{}' \;
  find server/build/test-extensions -name '*.vsix' -exec cli/lib/ovsx publish '{}' \;
  ```
