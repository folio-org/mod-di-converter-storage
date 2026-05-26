# Job Profile Wrangler Testing Loop Document

**Final Document Location:** `/Users/okolawole/git/folio/mod-di-converter-storage/job-profile-wrangler/TESTING-LOOP.md`

## Overview

A step-by-step testing loop to verify that job profile wrangler works for every job profile in the repository. Claude should create tasks for each step

## Environment Configuration

| Component | Value |
|-----------|-------|
| FOLIO URL | `http://localhost:8000` |
| Tenant | `diku` |
| Credentials | `diku_admin` / `admin` |
| Database | `PGPASSWORD=supersecret psql -U folio_rw -d folio -h localhost -p 5432` |
| Default System Profile UUID | `e34d7b92-9b83-11eb-a8b3-0242ac130003` |
| Repository Path | `/Users/okolawole/git/folio/mod-di-converter-storage/job-profile-wrangler/src/main/resources/repository/` |
| Loki | `http://olamimacmini:3100` |
| Grafana | `http://olamimacmini:3000` |

---

## Pre-requisites

**Note:** `import-profiles.sh` currently points to bugfest environment (`kong-bugfest-sunflower.int.aws.folio.org`). For local testing:
1. Modify the script to use `http://localhost:8000`, tenant `diku`, credentials `diku_admin/admin`, OR
2. Use the direct JAR command shown below in Step 4

---

## Input: Job Profile to Test

**User provides the job profile to test.** Set these variables before running the test loop:

```bash
# SET THESE BEFORE RUNNING THE TEST LOOP
PROFILE_NUMBER=1           # The profile number (jp-001 = 1, jp-025 = 25, etc.)
PROFILE_NAME="jp-001"      # The profile name pattern to use
```

---

## Testing Loop Steps

### Step 1: Verify the Selected Job Profile

```bash
# List all profiles in repository
ls /Users/okolawole/git/folio/mod-di-converter-storage/job-profile-wrangler/src/main/resources/repository/*.dot

# View the selected profile structure
cat /Users/okolawole/git/folio/mod-di-converter-storage/job-profile-wrangler/src/main/resources/repository/jp-$(printf "%03d" $PROFILE_NUMBER).dot

# Optional: Generate visualization
cd /Users/okolawole/git/folio/mod-di-converter-storage/job-profile-wrangler
./visualize.sh $PROFILE_NUMBER
```

### Step 2: Authenticate with FOLIO

```bash
TOKEN=$(curl -s -X POST "http://localhost:8000/authn/login-with-expiry" \
  -H "Content-Type: application/json" \
  -H "x-okapi-tenant: diku" \
  -d '{"username":"diku_admin","password":"admin"}' \
  -c - | grep folioAccessToken | awk '{print $7}')

echo "Token: ${TOKEN:0:30}..."
```

### Step 3: Delete Existing Profiles

```bash
cd /Users/okolawole/git/folio/mod-di-converter-storage/job-profile-wrangler

# Dry run first
./delete-profiles.sh "$PROFILE_NAME"

# Confirm deletion
./delete-profiles.sh "$PROFILE_NAME" --confirm
```

**Script inputs:**
- `$1`: Name pattern (uses `$PROFILE_NAME` variable set above)
- Additional flags passed through (e.g., `--confirm` to execute deletion)

### Step 4: Export Profile from Repository to FOLIO

```bash
cd /Users/okolawole/git/folio/mod-di-converter-storage/job-profile-wrangler

# Direct JAR command (recommended for local testing)
java -jar target/job-profile-wrangler-2.4.0-SNAPSHOT.jar export \
  -u http://localhost:8000 \
  --tenant diku \
  --username diku_admin \
  --password admin \
  -i $PROFILE_NUMBER \
  -r src/main/resources/repository
```

### Step 5: Get the Job Profile UUID

```bash
# Uses PROFILE_NAME from the input section
JOB_PROFILE_UUID=$(curl -s "http://localhost:8000/data-import-profiles/jobProfiles?query=name==${PROFILE_NAME}*" \
  -H "x-okapi-token: $TOKEN" \
  -H "x-okapi-tenant: diku" | jq -r '.jobProfiles[0].id')

echo "Job Profile UUID: $JOB_PROFILE_UUID"
```

### Step 6: Generate Test MARC Records

```bash
cd /Users/okolawole/git/folio/mod-di-converter-storage/job-profile-wrangler

./gen-records.sh "$JOB_PROFILE_UUID"
```

Wrangler v1 is fail-fast. Always inspect `test-records-report.json` before continuing:

```bash
jq '.overallOutcome.type' test-records-report.json
```

Only `generated` and `needs-enrichment` produce MARC files. `blocked-unsupported-workflow`,
`generator-gap`, and `invalid-profile-shape` intentionally write the JSON report only;
fix the reported issue before continuing. If the outcome is `needs-enrichment`, run the
`enrich` command shown in the report after importing foundation records and before final
import.

**Outputs:**
- `test-records-foundation.mrc` - Foundation records to seed the database before testing
- `test-records-import.mrc` - Test records for exercising both CREATE and UPDATE paths

### Step 7: Import Foundation Records (If Generated)

**Only run this step if `test-records-foundation.mrc` was generated and is non-empty.**

**Profile Selection:** Choose the foundation import profile by the prerequisite entities
the test profile must match, not only by the MARC fields present in the generated
foundation file. Some generated foundation records contain `852` so the later test import
can create Holdings, but the foundation seed still only needs an Instance when the test
profile is `MATCH INSTANCE -> CREATE HOLDINGS`.

| Test Profile Needs To Match | Typical Shape | Use Profile | Profile UUID |
|----------------------------|---------------|-------------|--------------|
| Instance only | `MATCH INSTANCE -> CREATE HOLDINGS` | Default System Profile | `e34d7b92-9b83-11eb-a8b3-0242ac130003` |
| Holdings context already present | `MATCH INSTANCE -> MATCH/UPDATE HOLDINGS` or `MATCH HOLDINGS -> ...` | jp-001 | Query via API |
| Item context already present | `MATCH ITEM -> ...` | jp-001 | Query via API |

```bash
# Check if foundation records file exists and has content
if [ -s test-records-foundation.mrc ]; then
  echo "Foundation records file found, proceeding with import..."
else
  echo "No foundation records file, skipping to Step 8"
  # Skip to Step 8
fi

# 7.0: Determine appropriate import profile based on required prerequisites.
# Default to seeding only the Instance. Use jp-001 only when the test profile must match
# or update existing Holdings/Items, not merely because 852 exists in the MARC.
NEEDS_EXISTING_HOLDINGS_OR_ITEMS=false

if [ "$NEEDS_EXISTING_HOLDINGS_OR_ITEMS" = "true" ]; then
  echo "Test profile needs existing Holdings/Items - using jp-001 for foundation import"
  # Get jp-001 UUID
  FOUNDATION_PROFILE_UUID=$(curl -s "http://localhost:8000/data-import-profiles/jobProfiles?query=name==jp-001*" \
    -H "x-okapi-token: $TOKEN" \
    -H "x-okapi-tenant: diku" | jq -r '.jobProfiles[0].id')
  FOUNDATION_PROFILE_NAME="jp-001 (Instance + Holdings + Item)"

  if [ "$FOUNDATION_PROFILE_UUID" = "null" ] || [ -z "$FOUNDATION_PROFILE_UUID" ]; then
    echo "WARNING: jp-001 not found in FOLIO. The test profile needs existing Holdings/Items but no appropriate profile exists."
    echo "Please export jp-001 first: java -jar target/job-profile-wrangler-2.4.0-SNAPSHOT.jar export -u http://localhost:8000 --tenant diku --username diku_admin --password admin -i 1 -r src/main/resources/repository"
    exit 1
  fi
else
  echo "Test profile only needs an existing Instance - using default system profile"
  FOUNDATION_PROFILE_UUID="e34d7b92-9b83-11eb-a8b3-0242ac130003"
  FOUNDATION_PROFILE_NAME="Default - Create instance and SRS MARC Bib"
fi

echo "Using profile: $FOUNDATION_PROFILE_NAME ($FOUNDATION_PROFILE_UUID)"

# 7.1: Create upload definition
UPLOAD_DEF=$(curl -s -X POST "http://localhost:8000/data-import/uploadDefinitions" \
  -H "x-okapi-token: $TOKEN" \
  -H "x-okapi-tenant: diku" \
  -H "Content-Type: application/json" \
  -d '{"fileDefinitions":[{"name":"test-records-foundation.mrc"}]}')

UPLOAD_DEF_ID=$(echo "$UPLOAD_DEF" | jq -r '.id')
FILE_DEF_ID=$(echo "$UPLOAD_DEF" | jq -r '.fileDefinitions[0].id')

# 7.2: Get S3 upload URL (note: param is 'filename' lowercase)
UPLOAD_URL_RESP=$(curl -s "http://localhost:8000/data-import/uploadUrl?filename=test-records-foundation.mrc" \
  -H "x-okapi-token: $TOKEN" \
  -H "x-okapi-tenant: diku")

UPLOAD_URL=$(echo "$UPLOAD_URL_RESP" | jq -r '.url')
KEY=$(echo "$UPLOAD_URL_RESP" | jq -r '.key')
UPLOAD_ID=$(echo "$UPLOAD_URL_RESP" | jq -r '.uploadId')

# 7.3: Upload file to S3
ETAG=$(curl -s -X PUT "$UPLOAD_URL" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @test-records-foundation.mrc \
  -D - | grep -i etag | cut -d'"' -f2)

# 7.4: Assemble storage file (uploadId from step 7.2 is required)
curl -s -X POST "http://localhost:8000/data-import/uploadDefinitions/$UPLOAD_DEF_ID/files/$FILE_DEF_ID/assembleStorageFile" \
  -H "x-okapi-token: $TOKEN" \
  -H "x-okapi-tenant: diku" \
  -H "Content-Type: application/json" \
  -d "{\"key\":\"$KEY\",\"tags\":[\"$ETAG\"],\"uploadId\":\"$UPLOAD_ID\"}"

# 7.5: Wait for upload status = UPLOADED
sleep 2
STATUS=$(curl -s "http://localhost:8000/data-import/uploadDefinitions/$UPLOAD_DEF_ID" \
  -H "x-okapi-token: $TOKEN" \
  -H "x-okapi-tenant: diku" | jq -r '.fileDefinitions[0].status')
echo "Upload Status: $STATUS"

# 7.6: Process with selected foundation profile
curl -s -X POST "http://localhost:8000/data-import/uploadDefinitions/$UPLOAD_DEF_ID/processFiles" \
  -H "x-okapi-token: $TOKEN" \
  -H "x-okapi-tenant: diku" \
  -H "Content-Type: application/json" \
  -d "{
    \"uploadDefinition\": $(curl -s "http://localhost:8000/data-import/uploadDefinitions/$UPLOAD_DEF_ID" \
      -H "x-okapi-token: $TOKEN" -H "x-okapi-tenant: diku"),
    \"jobProfileInfo\": {
      \"id\": \"$FOUNDATION_PROFILE_UUID\",
      \"name\": \"$FOUNDATION_PROFILE_NAME\",
      \"dataType\": \"MARC\"
    }
  }"

# 7.7: Wait for job completion
sleep 5
FOUNDATION_JOB_ID=$(curl -s "http://localhost:8000/metadata-provider/jobExecutions?sortBy=started_date,desc&limit=1" \
  -H "x-okapi-token: $TOKEN" \
  -H "x-okapi-tenant: diku" | jq -r '.jobExecutions[0].id')

echo "Foundation Job ID: $FOUNDATION_JOB_ID"

# Poll until complete
for i in {1..30}; do
  STATUS=$(curl -s "http://localhost:8000/change-manager/jobExecutions/$FOUNDATION_JOB_ID" \
    -H "x-okapi-token: $TOKEN" \
    -H "x-okapi-tenant: diku" | jq -r '.status')
  echo "Foundation job status: $STATUS"
  [ "$STATUS" = "COMMITTED" ] || [ "$STATUS" = "ERROR" ] && break
  sleep 5
done
```

### Step 8: Import Test Records with Test Profile

```bash
# 8.1: Create upload definition for import records
UPLOAD_DEF_TEST=$(curl -s -X POST "http://localhost:8000/data-import/uploadDefinitions" \
  -H "x-okapi-token: $TOKEN" \
  -H "x-okapi-tenant: diku" \
  -H "Content-Type: application/json" \
  -d '{"fileDefinitions":[{"name":"test-records-import.mrc"}]}')

UPLOAD_DEF_TEST_ID=$(echo "$UPLOAD_DEF_TEST" | jq -r '.id')
FILE_DEF_TEST_ID=$(echo "$UPLOAD_DEF_TEST" | jq -r '.fileDefinitions[0].id')

# 8.2: Get S3 upload URL (note: param is 'filename' lowercase)
UPLOAD_URL_RESP=$(curl -s "http://localhost:8000/data-import/uploadUrl?filename=test-records-import.mrc" \
  -H "x-okapi-token: $TOKEN" -H "x-okapi-tenant: diku")
UPLOAD_URL=$(echo "$UPLOAD_URL_RESP" | jq -r '.url')
KEY=$(echo "$UPLOAD_URL_RESP" | jq -r '.key')
UPLOAD_ID=$(echo "$UPLOAD_URL_RESP" | jq -r '.uploadId')

# 8.3: Upload file to S3
ETAG=$(curl -s -X PUT "$UPLOAD_URL" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @test-records-import.mrc \
  -D - | grep -i etag | cut -d'"' -f2)

# 8.4: Assemble storage file (uploadId from step 8.2 is required)
curl -s -X POST "http://localhost:8000/data-import/uploadDefinitions/$UPLOAD_DEF_TEST_ID/files/$FILE_DEF_TEST_ID/assembleStorageFile" \
  -H "x-okapi-token: $TOKEN" \
  -H "x-okapi-tenant: diku" \
  -H "Content-Type: application/json" \
  -d "{\"key\":\"$KEY\",\"tags\":[\"$ETAG\"],\"uploadId\":\"$UPLOAD_ID\"}"

sleep 2

# 8.5: Process with the test job profile
curl -s -X POST "http://localhost:8000/data-import/uploadDefinitions/$UPLOAD_DEF_TEST_ID/processFiles" \
  -H "x-okapi-token: $TOKEN" \
  -H "x-okapi-tenant: diku" \
  -H "Content-Type: application/json" \
  -d "{
    \"uploadDefinition\": $(curl -s "http://localhost:8000/data-import/uploadDefinitions/$UPLOAD_DEF_TEST_ID" \
      -H "x-okapi-token: $TOKEN" -H "x-okapi-tenant: diku"),
    \"jobProfileInfo\": {
      \"id\": \"$JOB_PROFILE_UUID\",
      \"name\": \"$PROFILE_NAME\",
      \"dataType\": \"MARC\"
    }
  }"

# 8.6: Get job execution ID and wait
sleep 3
TEST_JOB_ID=$(curl -s "http://localhost:8000/metadata-provider/jobExecutions?sortBy=started_date,desc&limit=1" \
  -H "x-okapi-token: $TOKEN" \
  -H "x-okapi-tenant: diku" | jq -r '.jobExecutions[0].id')

echo "Test Job ID: $TEST_JOB_ID"

for i in {1..30}; do
  RESULT=$(curl -s "http://localhost:8000/change-manager/jobExecutions/$TEST_JOB_ID" \
    -H "x-okapi-token: $TOKEN" \
    -H "x-okapi-tenant: diku")
  STATUS=$(echo "$RESULT" | jq -r '.status')
  UI_STATUS=$(echo "$RESULT" | jq -r '.uiStatus')
  echo "Test job: status=$STATUS, uiStatus=$UI_STATUS"
  [ "$STATUS" = "COMMITTED" ] || [ "$STATUS" = "ERROR" ] && break
  sleep 5
done
```

### Step 9: Review Job Summary

```bash
# Job execution summary
curl -s "http://localhost:8000/change-manager/jobExecutions/$TEST_JOB_ID" \
  -H "x-okapi-token: $TOKEN" \
  -H "x-okapi-tenant: diku" | jq '{
    id, status, uiStatus, errorStatus,
    jobProfileName: .jobProfileInfo.name,
    progress
  }'

# Job log entries (check for errors)
curl -s "http://localhost:8000/metadata-provider/jobLogEntries/$TEST_JOB_ID" \
  -H "x-okapi-token: $TOKEN" \
  -H "x-okapi-tenant: diku" | jq '.entries[] | {
    sourceRecordOrder,
    sourceRecordTitle,
    actionStatus: .relatedInstanceInfo.actionStatus,
    error
  }'

# Database verification
PGPASSWORD=supersecret psql -U folio_rw -d folio -h localhost -p 5432 -c "
  SELECT entity_type, action_type, action_status,
         substring(error, 1, 80) as error_preview
  FROM diku_mod_source_record_manager.journal_records
  WHERE job_execution_id = '$TEST_JOB_ID'
  ORDER BY action_date;"
```

### Step 10: Check Logs (Troubleshooting)

```bash
# Query Loki for recent errors
START=$(date -v-30M +%s)000000000
END=$(date +%s)000000000

# Search for job execution errors
curl -G -s "http://olamimacmini:3100/loki/api/v1/query_range" \
  --data-urlencode "query={container_name=~\".*mod-source-record-manager.*\"} |= \"$TEST_JOB_ID\"" \
  --data-urlencode "start=$START" \
  --data-urlencode "end=$END" | jq '.data.result[].values[]'

# Check for HTTP errors
curl -G -s "http://olamimacmini:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={container_name=~".*mod-.*"} |= "ERROR"' \
  --data-urlencode "start=$START" \
  --data-urlencode "end=$END" | jq '.data.result[].values[]'
```

---

## Verification Checklist

- [ ] Profile deleted and re-imported successfully
- [ ] Job Profile UUID obtained
- [ ] Test MARC records generated (check which files exist)
- [ ] **Foundation records generated when required** (see Troubleshooting below if missing)
- [ ] Foundation records imported with system profile if `test-records-foundation.mrc` exists (status: COMMITTED)
- [ ] Import/Test records imported with test profile (status: COMMITTED)
- [ ] Job log entries show expected actions (CREATED/UPDATED)
- [ ] No unexpected errors in journal records
- [ ] Loki logs checked if issues found

---

## Troubleshooting: Job Profile Wrangler Inconsistencies

**STOP and investigate if you encounter any of these issues:**

### Missing Foundation Records

If a job profile contains MATCH profiles that require existing records (e.g., match on INSTANCE to CREATE ITEM), but `test-records-foundation.mrc` is not generated:

1. **Check the generator output** - Look for inconsistencies between:
   - Log messages saying what was generated
   - The "Output:" line in the report
   - Actual files created on disk

2. **Expected behavior**: Profiles with MATCH → CREATE paths should generate:
   - `test-records-foundation.mrc` - Records to create the entities being matched against
   - `test-records-import.mrc` - Records that will match and trigger the CREATE action

3. **Investigation**: Review `MarcTestDataGenerator.java` and related classes:
   - `src/main/java/org/folio/exports/MarcTestDataGenerator.java`
   - `src/main/java/org/folio/exports/JobProfileAnalyzer.java`
   - Check how CREATE paths with `reactTo: MATCH` are handled

### Report/Output Mismatch

If the generation report says one thing but files show another:
- This indicates a bug in the generator's reporting logic
- Document the exact discrepancy
- Review the code that produces the summary report

### Profiles That Cannot Be Tested

Some profile structures may not be fully supported by the generator yet:
- Match on INSTANCE → CREATE ITEM (requires existing instance + holdings)
- Complex multi-level matches
- Profiles with conditional logic

**Do not proceed with testing until foundation record generation issues are resolved.**

---

## Files Modified

- `/Users/okolawole/git/folio/mod-di-converter-storage/job-profile-wrangler/delete-profiles.sh` - Delete profiles script
- `/Users/okolawole/git/folio/mod-di-converter-storage/job-profile-wrangler/gen-records.sh` - Generate MARC records script
- `/Users/okolawole/git/folio/mod-di-converter-storage/job-profile-wrangler/import-profiles.sh` - Import profiles script (needs localhost config for local testing)
