#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="${SCRIPT_DIR}/target/job-profile-wrangler-2.4.0-SNAPSHOT.jar"
REPO_PATH="${SCRIPT_DIR}/src/main/resources/repository"

# Use -i if an ID is provided, otherwise export all
if [ -n "$1" ]; then
  PROFILE_ARG="-i $1"
else
  PROFILE_ARG="--all"
fi

java -jar "$JAR_PATH" export \
  -u http://localhost:8000 \
  --tenant diku \
  --username diku_admin \
  --password admin \
  $PROFILE_ARG \
  -r "$REPO_PATH"
