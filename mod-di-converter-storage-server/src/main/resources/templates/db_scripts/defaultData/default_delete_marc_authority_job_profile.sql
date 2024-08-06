INSERT INTO ${myuniversity}_${mymodule}.job_profiles (id, jsonb) values
('1a338fcd-3efc-4a03-b007-394eeb0d5fb9', '{
  "id": "1a338fcd-3efc-4a03-b007-394eeb0d5fb9",
  "name": "Default - Delete MARC Authority",
  "description": "This job profile is used for deleting an MARC authority record via the MARC authority app. This profile deletes the authority record stored in source-record-storage and mod-inventory-storage. This job profile cannot be edited, duplicated, or deleted.",
  "dataType": "MARC",
  "hidden": true,
  "userInfo": {
    "firstName": "System",
    "lastName": "System",
    "userName": "System"
  },
  "parentProfiles": [],
  "childProfiles": [],
  "metadata": {
    "createdDate": "2022-02-16T15:00:00.000",
    "updatedDate": "2022-02-16T15:00:00.000",
    "createdByUserId": "00000000-0000-0000-0000-000000000000",
    "updatedByUserId": "00000000-0000-0000-0000-000000000000"
  }
}') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.action_profiles (id, jsonb) values
  ('fabd9a3e-33c3-49b7-864d-c5af830d9990', '{
  "id": "fabd9a3e-33c3-49b7-864d-c5af830d9990",
  "name": "Default - Delete MARC Authority via MARC Authority app",
  "description": "This action profile is used with FOLIO''s default job profile for deleting an MARC authority record via the MARC authority app. This profile deletes the authority record stored in source-record-storage and mod-inventory-storage. This action profile cannot be edited, duplicated, or deleted.",
  "action": "DELETE",
  "folioRecord": "MARC_AUTHORITY",
  "hidden": true,
  "remove9Subfields": false,
  "userInfo": {
    "firstName": "System",
    "lastName": "System",
    "userName": "System"
  },
  "parentProfiles": [],
  "childProfiles": [],
  "metadata": {
    "createdDate": "2022-02-16T14:00:00.000",
    "updatedDate": "2022-02-16T15:00:00.462+0000",
    "createdByUserId": "00000000-0000-0000-0000-000000000000",
    "updatedByUserId": "00000000-0000-0000-0000-000000000000"
  }
}')
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.match_profiles (id, jsonb) values
('4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6',
'{
  "id": "4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6",
  "name": "Default - Delete MARC Authority",
  "description": "This match profile is used with FOLIO''s default job profile for deleting an MARC authority record via the MARC authority app. This profile deletes the authority record stored in source-record-storage and mod-inventory-storage. This match profile cannot be edited, duplicated, or deleted.",
  "incomingRecordType": "MARC_AUTHORITY",
  "existingRecordType": "MARC_AUTHORITY",
  "matchDetails": [
    {
      "incomingRecordType": "MARC_AUTHORITY",
      "existingRecordType": "MARC_AUTHORITY",
      "incomingMatchExpression": {
        "dataValueType": "VALUE_FROM_RECORD",
        "fields": [
          {
            "label": "field",
            "value": "999"
          },
          {
            "label": "indicator1",
            "value": "f"
          },
          {
            "label": "indicator2",
            "value": "f"
          },
          {
            "label": "recordSubfield",
            "value": "s"
          }
        ]
      },
      "matchCriterion": "EXACTLY_MATCHES",
      "existingMatchExpression": {
        "dataValueType": "VALUE_FROM_RECORD",
        "fields": [
          {
            "label": "field",
            "value": "999"
          },
          {
            "label": "indicator1",
            "value": "f"
          },
          {
            "label": "indicator2",
            "value": "f"
          },
          {
            "label": "recordSubfield",
            "value": "s"
          }
        ]
      }
    }
  ],
  "hidden": true,
  "userInfo": {
    "firstName": "System",
    "lastName": "System",
    "userName": "System"
  },
  "parentProfiles": [],
  "childProfiles": [],
  "metadata": {
    "createdDate": "2022-02-16T15:00:00.000",
    "updatedDate": "2022-02-16T15:00:00.000",
    "createdByUserId": "00000000-0000-0000-0000-000000000000",
    "updatedByUserId": "00000000-0000-0000-0000-000000000000"
  }
}')
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.job_to_match_profiles (id, jsonb) values
('644e53c2-7be2-4ae5-bc17-131334222d39',
'{
	"id": "644e53c2-7be2-4ae5-bc17-131334222d39",
	"order": 0,
	"triggered": false,
	"detailProfileId": "4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6",
	"masterProfileId": "1a338fcd-3efc-4a03-b007-394eeb0d5fb9",
	"detailProfileType": "MATCH_PROFILE",
	"masterProfileType": "JOB_PROFILE"
}
') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.match_to_action_profiles (id, jsonb) values
('e0fd6684-fa34-4493-9048-a9e01c58f782',
'{
    "id": "e0fd6684-fa34-4493-9048-a9e01c58f782",
    "masterProfileId": "4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6",
    "detailProfileId": "fabd9a3e-33c3-49b7-864d-c5af830d9990",
    "order": 0,
    "reactTo": "MATCH",
    "triggered": false,
    "masterProfileType": "MATCH_PROFILE",
    "detailProfileType": "ACTION_PROFILE",
    "jobProfileId": "1a338fcd-3efc-4a03-b007-394eeb0d5fb9"
}
') ON CONFLICT DO NOTHING;

DO
$$
DECLARE
    job_wrapper_id UUID;
    action_wrapper_id UUID;
    match_wrapper_id UUID;
BEGIN
    -- JOB_PROFILE
    SELECT id INTO job_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE job_profile_id = '1a338fcd-3efc-4a03-b007-394eeb0d5fb9';
    IF job_wrapper_id IS NULL THEN
        job_wrapper_id = '7248a1a1-8811-4771-b418-c1d16423e2bc';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id)
        VALUES (job_wrapper_id, 'JOB_PROFILE', '1a338fcd-3efc-4a03-b007-394eeb0d5fb9') ON CONFLICT DO NOTHING;
    END IF;

    -- ACTION_PROFILE
    SELECT id INTO action_wrapper_id  FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE action_profile_id = 'fabd9a3e-33c3-49b7-864d-c5af830d9990';
    IF action_wrapper_id IS NULL THEN
        action_wrapper_id = 'ed54fc13-aac0-40d4-b21e-dda1e6f9a03a';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id)
        VALUES (action_wrapper_id, 'ACTION_PROFILE', 'fabd9a3e-33c3-49b7-864d-c5af830d9990') ON CONFLICT DO NOTHING;
    END IF;

    -- MATCH_PROFILE
    SELECT id INTO match_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE match_profile_id = '4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6';
    IF match_wrapper_id IS NULL THEN
        match_wrapper_id = '69de98ea-68dd-46be-a187-a115f9afcc05';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, match_profile_id)
        VALUES (match_wrapper_id, 'MATCH_PROFILE', '4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6') ON CONFLICT DO NOTHING;
    END IF;

    -- Create profile_associations
    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id,
        detail_wrapper_id, master_profile_id, detail_profile_id,
        master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
        ('644e53c2-7be2-4ae5-bc17-131334222d39', NULL, job_wrapper_id, action_wrapper_id,
         '1a338fcd-3efc-4a03-b007-394eeb0d5fb9', '4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6',
         'JOB_PROFILE', 'MATCH_PROFILE', 0, NULL) ON CONFLICT DO NOTHING;

    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id,
            detail_wrapper_id, master_profile_id, detail_profile_id,
            master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
        ('e0fd6684-fa34-4493-9048-a9e01c58f782', '1a338fcd-3efc-4a03-b007-394eeb0d5fb9',
         match_wrapper_id, action_wrapper_id, '4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6',
         'fabd9a3e-33c3-49b7-864d-c5af830d9990', 'MATCH_PROFILE', 'ACTION_PROFILE', 0, 'MATCH') ON CONFLICT DO NOTHING;
END
$$;
