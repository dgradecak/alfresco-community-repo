#!/usr/bin/env bash

set -x

export DOCKER_COMPOSE_PATH=$1
export CLEAN_UP="$2"

if [ -z "$DOCKER_COMPOSE_PATH" ]
then
  echo "Please provide path to docker-compose.yml: \"${0##*/} /path/to/docker-compose.yml\""
  exit 1
fi

# Cleans up any generated images. These are created if the docker-compose file has "build:" clauses. They are not
# recreated if an image with the same name already exist. Also cleans up existing containers. Generally only needed on
# dev systems, however...
# The second parameter can be used to avoid doing a clean up if we are doing a restart test.
if [ "$CLEAN_UP" != "no-clean-up" ]
then
  docker-compose --file "${DOCKER_COMPOSE_PATH}" kill
  docker-compose --file "${DOCKER_COMPOSE_PATH}" rm -f

  export GENERATED_IMAGES=$(docker images | grep '^environment_' | awk '{ print $3 }')
  if [ -n "$GENERATED_IMAGES" ]
  then
    docker image rm -f $GENERATED_IMAGES
  fi
fi

echo "Starting ACS stack in ${DOCKER_COMPOSE_PATH}"

TRANSFORMERS_TAG=$(mvn help:evaluate -Dexpression=dependency.alfresco-transform-core.version -q -DforceStdout)
TRANSFORM_ROUTER_TAG=$(mvn help:evaluate -Dexpression=dependency.alfresco-transform-service.version -q -DforceStdout)

# .env files are picked up from project directory correctly on docker-compose 1.23.0+
"TRANSFORMERS_TAG=${TRANSFORMERS_TAG}" "TRANSFORM_ROUTER_TAG=${TRANSFORM_ROUTER_TAG}" docker-compose --file "${DOCKER_COMPOSE_PATH}" --project-directory $(dirname "${DOCKER_COMPOSE_PATH}") up -d

if [ $? -eq 0 ]
then
  echo "Docker Compose started ok"
else
  echo "Docker Compose failed to start" >&2
  exit 1
fi