#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH=""
if [ -d "${SCRIPT_DIR}/target" ]; then
  JAR_PATH="$(find "${SCRIPT_DIR}/target" -maxdepth 1 -name 'job-profile-wrangler-*.jar' ! -name 'original-*' | sort | tail -n 1)"
fi

if [ -z "$JAR_PATH" ]; then
  echo "No job-profile-wrangler jar found. Run: mvn -DskipTests package"
  exit 1
fi

# Use provided name pattern, otherwise default to "jp-"
NAME_PATTERN="${1:-jp-}"
shift 2>/dev/null || true

java -jar "$JAR_PATH" delete \
  -n "$NAME_PATTERN" \
  "$@"
