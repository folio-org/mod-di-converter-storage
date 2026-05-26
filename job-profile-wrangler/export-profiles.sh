#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH=""
if [ -d "${SCRIPT_DIR}/target" ]; then
  JAR_PATH="$(find "${SCRIPT_DIR}/target" -maxdepth 1 -name 'job-profile-wrangler-*.jar' ! -name 'original-*' | sort | tail -n 1)"
fi
REPO_PATH="${SCRIPT_DIR}/src/main/resources/repository"

if [ -z "$JAR_PATH" ]; then
  echo "No job-profile-wrangler jar found. Run: mvn -DskipTests package"
  exit 1
fi

# Use -i if an ID is provided, otherwise export all
if [ -n "$1" ]; then
  PROFILE_ARG="-i $1"
else
  PROFILE_ARG="--all"
fi

java -jar "$JAR_PATH" export \
  $PROFILE_ARG \
  -r "$REPO_PATH"
