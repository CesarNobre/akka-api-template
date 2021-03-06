#########
# Tasks #
#########

# Build application (fat jar)
build: dependencies/resources dependencies/swagger-ui
	$(_sbt-cmd) universal:packageZipTarball

# Build docker image
image: build
	- docker build \
	--build-arg version=$(version) \
	-t ${PROJECT_NAME}:$(version) .
	- docker tag ${PROJECT_NAME}:$(version) us.gcr.io/${GPROJECT_NAME}/${PROJECT_NAME}:$(version)

# Start services and third-party dependencies such as postgres, redis, etc
dependencies/services: dependencies/services/run db/migrate
dependencies/services/run:
	- docker-compose up -d

# Stop services and third-party dependencies
dependencies/clean/services:
	- docker-compose stop && docker-compose rm -vf

# Apply migration placed in `/src/main/resources/db/migrations:/flyway/sql` into specified database via
# args `MIGRATE_DB_USER`, `MIGRATE_DB_PASSWORD` and `MIGRATE_DB_URL`:
#
#   make db/migrate MIGRATE_DB_USER="chucknorris" \
#       MIGRATE_DB_PASSWORD="nowthatyouknowyoumustdie" \
#       MIGRATE_DB_URL="jdbc:postgresql://db.expendables.io:5432/jobs"
#
db/migrate:
	$(_flyway_cmd) migrate

# Compile download proto files from `PROTOS_PATH` and output generated classes into `RESOURCES_PATH`
#
dependencies/resources: dependencies/clean/resources fetch/resources
	- $(_protoc_cmd) scalapbc --proto_path=./$(PROTOS_PATH) \
	      --scala_out=flat_package:./$(RESOURCES_PATH) $(shell find "./$(PROTOS_PATH)" -name "*.proto")

# Clean downloaded proto files directory `PROTOS_PATH` and generated classes directory `RESOURCES_PATH`
#
dependencies/clean/resources:
	- rm -rf $(PROTOS_PATH) $(RESOURCES_PATH)

# Download latest version of `swagger-ui` in order to provided a built application with swagger interface
#
dependencies/swagger-ui:
	- rm -rf src/main/resources/public/swagger
	- mkdir -p src/main/resources/public/swagger
	- git clone \
	        --branch v2.2.8 \
	        --depth 1 https://github.com/swagger-api/swagger-ui.git \
	        tmp/swagger 2> /dev/null
	- mv tmp/swagger/dist/** src/main/resources/public/swagger
	- cp src/main/resources/api-docs/index.html src/main/resources/public/swagger/index.html

# Download proto resources from specified Github repository `PROTO_REPOSITORY` and tag `PROTO_VERSION`.
# The downloaded proto files will be placed into `PROTOS_PATH` and it also created the generated
# classes directory `RESOURCES_PATH`.
#
fetch/resources:
	- mkdir -p $(PROTOS_PATH) $(RESOURCES_PATH)
	- git clone \
	        --branch $(PROTO_VERSION) \
	        --depth 1 git@github.com:$(PROTO_REPOSITORY).git \
	        $(PROTOS_PATH) 2> /dev/null

# Setup, run tests and then tear down
#
#   make test
#
test: dependencies/resources dependencies/services test/run dependencies/clean/services

# Compile project with test folder included
#
#   make/compile
#
test/compile: dependencies/resources
	$(_sbt-cmd) test:compile

# Run tests
#
#   make test/run
#
test/run:
	$(_sbt-cmd-with-dependencies) test

# Configure gcloud tool to be used by circleci
#   make circleci/gcloud/setup
#
circleci/gcloud/setup:
	- sudo /opt/google-cloud-sdk/bin/gcloud --quiet components update
	- sudo /opt/google-cloud-sdk/bin/gcloud --quiet components update kubectl
	- echo ${GCLOUD_SERVICE_KEY} | base64 --decode -i > ${HOME}/gcloud-service-key.json
	- sudo /opt/google-cloud-sdk/bin/gcloud auth activate-service-account --key-file ${HOME}/gcloud-service-key.json
	- sudo /opt/google-cloud-sdk/bin/gcloud config set project ${GPROJECT_NAME}
	- sudo /opt/google-cloud-sdk/bin/gcloud --quiet config set container/cluster ${CLUSTER_NAME}
	- sudo /opt/google-cloud-sdk/bin/gcloud config set compute/zone ${CLOUDSDK_COMPUTE_ZONE}
	- sudo /opt/google-cloud-sdk/bin/gcloud --quiet container clusters get-credentials ${CLUSTER_NAME}
	- sudo /opt/google-cloud-sdk/bin/gcloud config set container/use_client_certificate True

# Push docker image to gcloud registry
#   make circleci/gcloud/image/publish
#
circleci/gcloud/image/publish: image
	- sudo /opt/google-cloud-sdk/bin/gcloud docker -- push us.gcr.io/${GPROJECT_NAME}/${PROJECT_NAME}:$(version)

# Deploy a new version to GKE cluster
#   make circleci/gcloud/deploy
#
circleci/gcloud/deploy: circleci/gcloud/setup circleci/gcloud/image/publish
	- sudo chown -R ubuntu:ubuntu /home/ubuntu/.kube
	- make db/migrate MIGRATE_DB_USER=${CI_MIGRATE_DB_USER} MIGRATE_DB_PASSWORD=${CI_MIGRATE_DB_PASSWORD} MIGRATE_DB_URL=${CI_MIGRATE_DB_URL}
	- m4 \
          -Dapp_env=qa \
          -Ddb_url=${DB_URL} \
          -Dversion=$(version) \
          deploy/deploy-macros.m4 deploy/deployment.template.yaml > deployment-qa-$(version).yaml
	- kubectl apply -f deployment-qa-$(version).yaml --record

###############
# Definitions #
###############

MIGRATE_DB_USER := postgres
MIGRATE_DB_PASSWORD := postgres
MIGRATE_DB_URL := jdbc:postgresql://postgres/$name__word$

PROTO_REPOSITORY = $company$/$name$-resources
PROTO_VERSION = v2.0.0
PROTOS_PATH = tmp/resources
RESOURCES_PATH = src/main/generated-proto

_flyway_cmd = docker run --rm --net host -v ${PWD}/src/main/resources/db/migrations:/flyway/sql \
      shouldbee/flyway \
      -user="$(MIGRATE_DB_USER)" \
      -password="$(MIGRATE_DB_PASSWORD)" \
      -url="$(MIGRATE_DB_URL)"

_protoc_cmd = \
      docker run \
      -v ${PWD}:/target \
      -w /target \
      --rm brennovich/protobuf-tools:latest

version = $(shell git rev-parse --short HEAD | tr -d "\n")

# Replace `options` with desired value
#
# More details: https://www.gnu.org/software/make/manual/make.html#Substitution-Refs
#
_sbt-cmd = $(_sbt-cmd-base:options=)
_sbt-cmd-with-dependencies = $(_sbt-cmd-base:options=--link postgres:postgres)
_sbt-cmd-base := \
	docker run --rm -it \
		-v $(PWD):/target \
		-v $(HOME)/.ivy2:/root/.ivy2 \
		-v $(HOME)/.m2:/root/.m2 \
		-w /target \
		-e VERSION=$(version) \
		options \
		hseeberger/scala-sbt:latest sbt

