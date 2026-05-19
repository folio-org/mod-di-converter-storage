#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="${SCRIPT_DIR}/target/job-profile-wrangler-2.4.0-SNAPSHOT.jar"
REPO_PATH="${SCRIPT_DIR}/src/main/resources/repository"

# java -jar "$JAR_PATH" import \
#   -u https://folio-snapshot-okapi.dev.folio.org \
#   --tenant diku \
#   --username diku_admin \
#   --password \
#   -r "$REPO_PATH"


java -jar "$JAR_PATH" import \
  -u https://kong-bugfest-sunflower.int.aws.folio.org \
  --tenant fs09000000 \
  --username folio \
  --password folio\
  -r "$REPO_PATH"
