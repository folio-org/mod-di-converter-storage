#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="${SCRIPT_DIR}/target/job-profile-wrangler-2.4.0-SNAPSHOT.jar"
REPO_PATH="${SCRIPT_DIR}/src/main/resources/repository"

# Repo ID to visualize
REPO_ID="${1:?Usage: $0 <repo-id>}"

java -jar "$JAR_PATH" visualize \
  "$REPO_ID" \
  -r "$REPO_PATH"
