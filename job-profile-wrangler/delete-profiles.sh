#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="${SCRIPT_DIR}/target/job-profile-wrangler-2.4.0-SNAPSHOT.jar"

# Use provided name pattern, otherwise default to "jp-"
NAME_PATTERN="${1:-jp-}"
shift 2>/dev/null || true

java -jar "$JAR_PATH" delete \
  -u http://localhost:8000 \
  --tenant diku \
  --username diku_admin \
  --password admin \
  -n "$NAME_PATTERN" \
  "$@"
