INSERT INTO ${myuniversity}_${mymodule}.job_profiles (id, jsonb) values
('c7fcbc40-c4c0-411d-b569-1fc6bc142a92', '{
	"id": "c7fcbc40-c4c0-411d-b569-1fc6bc142a92",
	"name": "quickMARC - Default Update authority",
	"description": "This job profile is used by the quickMARC to allow a user to update an SRS MARC authority record and corresponding Inventory authority. Profile cannot be edited or deleted",
	"deleted": false,
	"hidden": true,
	"dataType": "MARC",
	"metadata": {
		"createdDate": "2022-04-19T09:07:47.667",
		"updatedDate": "2022-04-19T09:09:10.382+0000",
		"createdByUserId": "6a010e5b-5421-5b1c-9b52-568b37038575",
		"updatedByUserId": "6a010e5b-5421-5b1c-9b52-568b37038575"
	},
	"userInfo": {
		"lastName": "System",
		"userName": "System",
		"firstName": "System"
	},
	"childProfiles": [],
	"parentProfiles": []
}
') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.action_profiles (id, jsonb) values
('f0f788c8-2e65-4e3a-9247-e9444eeb7d70',
'{
	"id": "f0f788c8-2e65-4e3a-9247-e9444eeb7d70",
	"name": "quickMARC - Default Update MARC authority",
	"action": "UPDATE",
	"deleted": false,
	"hidden": true,
    "remove9Subfields": false,
	"metadata": {
		"createdDate": "2020-11-30T09:02:39.96",
		"updatedDate": "2020-11-30T11:57:24.083+0000",
		"createdByUserId": "6a010e5b-5421-5b1c-9b52-568b37038575",
		"updatedByUserId": "6a010e5b-5421-5b1c-9b52-568b37038575"
	},
	"userInfo": {
		"lastName": "System",
		"userName": "System",
		"firstName": "System"
	},
	"description": "Updates existing SRS MARC authority record",
	"folioRecord": "MARC_AUTHORITY",
	"childProfiles": [],
	"parentProfiles": []
}
') ON CONFLICT DO NOTHING;


INSERT INTO ${myuniversity}_${mymodule}.match_profiles (id, jsonb) values
('aff72eae-847c-4a97-b7b9-c1ddb8cdcbbf',
'{
	"id": "aff72eae-847c-4a97-b7b9-c1ddb8cdcbbf",
	"name": "quickMARC - Default match for existing SRS authority",
	"deleted": false,
	"hidden": true,
	"metadata": {
		"createdDate": "2020-11-30T09:06:01.52",
		"updatedDate": "2020-11-30T09:59:01.248+0000",
		"createdByUserId": "6a010e5b-5421-5b1c-9b52-568b37038575",
		"updatedByUserId": "6a010e5b-5421-5b1c-9b52-568b37038575"
	},
	"userInfo": {
		"lastName": "System",
		"userName": "System",
		"firstName": "System"
	},
	"description": "Matches the UUID from the 999 ff $s in the incoming MARC record to the same field in any SRS MARC authority",
	"matchDetails": [{
			"matchCriterion": "EXACTLY_MATCHES",
			"existingRecordType": "MARC_AUTHORITY",
			"incomingRecordType": "MARC_AUTHORITY",
			"existingMatchExpression": {
				"fields": [{
						"label": "field",
						"value": "999"
					}, {
						"label": "indicator1",
						"value": "f"
					}, {
						"label": "indicator2",
						"value": "f"
					}, {
						"label": "recordSubfield",
						"value": "s"
					}
				],
				"dataValueType": "VALUE_FROM_RECORD"
			},
			"incomingMatchExpression": {
				"fields": [{
						"label": "field",
						"value": "999"
					}, {
						"label": "indicator1",
						"value": "f"
					}, {
						"label": "indicator2",
						"value": "f"
					}, {
						"label": "recordSubfield",
						"value": "s"
					}
				],
				"dataValueType": "VALUE_FROM_RECORD"
			}
		}
	],
	"childProfiles": [],
	"parentProfiles": [],
	"existingRecordType": "MARC_AUTHORITY",
	"incomingRecordType": "MARC_AUTHORITY"
}
') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.mapping_profiles (id, jsonb) values
('041f8ff9-9d17-4436-b305-1033e0879501',
'{
	"id": "041f8ff9-9d17-4436-b305-1033e0879501",
	"name": "quickMARC - Default Update MARC authority",
	"deleted": false,
	"hidden": true,
	"metadata": {
		"createdDate": "2020-11-30T09:02:06.555",
		"updatedDate": "2020-11-30T11:57:46.948+0000",
		"createdByUserId": "6a010e5b-5421-5b1c-9b52-568b37038575",
		"updatedByUserId": "6a010e5b-5421-5b1c-9b52-568b37038575"
	},
	"userInfo": {
		"lastName": "System",
		"userName": "System",
		"firstName": "System"
	},
	"description": "Updates existing SRS MARC authority record",
	"childProfiles": [],
	"mappingDetails": {
		"name": "marcAuthority",
		"recordType": "MARC_AUTHORITY",
		"mappingFields": [],
		"marcMappingOption": "UPDATE",
		"marcMappingDetails": []
	},
	"parentProfiles": [],
	"existingRecordType": "MARC_AUTHORITY",
	"incomingRecordType": "MARC_AUTHORITY",
	"marcFieldProtectionSettings": []
}
') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.job_to_match_profiles (id, jsonb) values
('e78e0445-d2d1-48d5-b023-efbcc9b25103',
'{
	"id": "e78e0445-d2d1-48d5-b023-efbcc9b25103",
	"order": 0,
	"triggered": false,
	"detailProfileId": "aff72eae-847c-4a97-b7b9-c1ddb8cdcbbf",
	"masterProfileId": "c7fcbc40-c4c0-411d-b569-1fc6bc142a92",
	"detailProfileType": "MATCH_PROFILE",
	"masterProfileType": "JOB_PROFILE"
}
') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.match_to_action_profiles (id, jsonb) values
('0ae939cf-835a-43b9-83c5-7e0afd49cbca',
'{
	"id": "0ae939cf-835a-43b9-83c5-7e0afd49cbca",
	"order": 0,
	"reactTo": "MATCH",
	"triggered": false,
	"jobProfileId": "c7fcbc40-c4c0-411d-b569-1fc6bc142a92",
	"detailProfileId": "f0f788c8-2e65-4e3a-9247-e9444eeb7d70",
	"masterProfileId": "aff72eae-847c-4a97-b7b9-c1ddb8cdcbbf",
	"detailProfileType": "ACTION_PROFILE",
	"masterProfileType": "MATCH_PROFILE"
}
') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.action_to_mapping_profiles (id, jsonb) values
('cfde7532-29e0-4bcc-9d3c-54f8853bcba1',
'{
	"id": "cfde7532-29e0-4bcc-9d3c-54f8853bcba1",
	"order": 0,
	"triggered": false,
	"detailProfileId": "041f8ff9-9d17-4436-b305-1033e0879501",
	"masterProfileId": "f0f788c8-2e65-4e3a-9247-e9444eeb7d70",
	"detailProfileType": "MAPPING_PROFILE",
	"masterProfileType": "ACTION_PROFILE"
}') ON CONFLICT DO NOTHING;



INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id) values
  ('c1fd6476-8dfa-4276-886e-ae2bbd43c3d6', 'JOB_PROFILE', 'c7fcbc40-c4c0-411d-b569-1fc6bc142a92') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id) values
  ('08ba771c-1fee-4240-b532-dca6ee034c82', 'MATCH_PROFILE', 'aff72eae-847c-4a97-b7b9-c1ddb8cdcbbf') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id) values
  ('e0a64315-efa4-4190-b50e-65643b92d722', 'ACTION_PROFILE', 'f0f788c8-2e65-4e3a-9247-e9444eeb7d70') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id) values
  ('716960de-2015-41d2-8c65-0c72aa9bbc3f', 'MAPPING_PROFILE', '041f8ff9-9d17-4436-b305-1033e0879501') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('e78e0445-d2d1-48d5-b023-efbcc9b25103',
   null,
   'c1fd6476-8dfa-4276-886e-ae2bbd43c3d6',
   '08ba771c-1fee-4240-b532-dca6ee034c82',
   'c7fcbc40-c4c0-411d-b569-1fc6bc142a92', -- master profile
   'aff72eae-847c-4a97-b7b9-c1ddb8cdcbbf',
   'JOB_PROFILE',
   'MATCH_PROFILE',
    0,
    null
   ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('0ae939cf-835a-43b9-83c5-7e0afd49cbca',
   'c7fcbc40-c4c0-411d-b569-1fc6bc142a92',
   '08ba771c-1fee-4240-b532-dca6ee034c82',
   'e0a64315-efa4-4190-b50e-65643b92d722',
   'aff72eae-847c-4a97-b7b9-c1ddb8cdcbbf',
   'f0f788c8-2e65-4e3a-9247-e9444eeb7d70',
   'MATCH_PROFILE',
   'ACTION_PROFILE',
   0,
   'MATCH'
  ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('cfde7532-29e0-4bcc-9d3c-54f8853bcba1',
   null,
   'e0a64315-efa4-4190-b50e-65643b92d722',
   '716960de-2015-41d2-8c65-0c72aa9bbc3f',

   'f0f788c8-2e65-4e3a-9247-e9444eeb7d70',
   '041f8ff9-9d17-4436-b305-1033e0879501',
   'ACTION_PROFILE',
   'MAPPING_PROFILE',
   0,
   null
  ) ON CONFLICT DO NOTHING;