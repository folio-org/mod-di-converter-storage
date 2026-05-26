#!/bin/bash
# Exit-code handling pattern:
# 0=generated, 2=needs-enrichment, 3=blocked-unsupported-workflow,
# 4=generator-gap, 5=invalid-profile-shape, anything else=setup/usage failure.

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

# Job Profile UUID to generate records for
PROFILE_UUID="${1:?Usage: $0 <job-profile-uuid>}"

java -jar "$JAR_PATH" generate \
  "$PROFILE_UUID" \
  -o test-records \
  --verbose \
  -r "$REPO_PATH"

EXIT=$?

case "$EXIT" in
  0)
    echo "generated: MARC records and report were written."
    ;;
  2)
    echo "needs-enrichment: run jp-wrangler enrich before importing the generated import file."
    ;;
  3)
    echo "blocked-unsupported-workflow: generation refused a known unsupported FOLIO workflow."
    ;;
  4)
    echo "generator-gap: generation could not translate the profile; inspect test-records-report.json."
    ;;
  5)
    echo "invalid-profile-shape: no importable execution path was found; inspect test-records-report.json."
    ;;
  *)
    echo "setup-or-usage-failure: generation failed before outcome classification."
    ;;
esac

exit "$EXIT"

#   -u https://folio-snapshot-okapi.dev.folio.org
