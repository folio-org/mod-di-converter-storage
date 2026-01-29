INSERT INTO ${myuniversity}_${mymodule}.job_profiles (id, jsonb) values
('cf6f2718-5aa5-482a-bba5-5bc9b75614da', '{
	"id": "cf6f2718-5aa5-482a-bba5-5bc9b75614da",
	"name": "quickMARC - Default Update instance",
	"description": "This job profile is used by the quickMARC to allow a user to update an SRS MARC bib record and corresponding Inventory instance. Profile cannot be edited or deleted",
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
('c2e2d482-9486-476e-a28c-8f1e303cbe1a',
'{
	"id": "c2e2d482-9486-476e-a28c-8f1e303cbe1a",
	"name": "quickMARC - Default Update MARC bib",
	"action": "UPDATE",
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
	"description": "Updates existing SRS MARC bib record",
	"folioRecord": "MARC_BIBLIOGRAPHIC",
	"childProfiles": [],
	"parentProfiles": []
}
') ON CONFLICT DO NOTHING;


INSERT INTO ${myuniversity}_${mymodule}.match_profiles (id, jsonb) values
('91cec42a-260d-4a8c-a9fb-90d9435ca2f4',
'{
	"id": "91cec42a-260d-4a8c-a9fb-90d9435ca2f4",
	"name": "quickMARC - Default match for existing SRS bib",
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
	"description": "Matches the UUID from the 999 ff $s in the incoming MARC record to the same field in any SRS MARC bib",
	"matchDetails": [{
			"matchCriterion": "EXACTLY_MATCHES",
			"existingRecordType": "MARC_BIBLIOGRAPHIC",
			"incomingRecordType": "MARC_BIBLIOGRAPHIC",
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
	"existingRecordType": "MARC_BIBLIOGRAPHIC",
	"incomingRecordType": "MARC_BIBLIOGRAPHIC"
}
') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.mapping_profiles (id, jsonb) values
('39b265e1-c963-4e5f-859d-6e8c327a265c',
'{
	"id": "39b265e1-c963-4e5f-859d-6e8c327a265c",
	"name": "quickMARC - Default Update MARC bib",
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
	"description": "Updates existing SRS MARC bib record",
	"childProfiles": [],
	"mappingDetails": {
		"name": "marcBib",
		"recordType": "MARC_BIBLIOGRAPHIC",
		"mappingFields": [],
		"marcMappingOption": "UPDATE",
		"marcMappingDetails": []
	},
	"parentProfiles": [],
	"existingRecordType": "MARC_BIBLIOGRAPHIC",
	"incomingRecordType": "MARC_BIBLIOGRAPHIC",
	"marcFieldProtectionSettings": []
}
') ON CONFLICT DO NOTHING;

DO
$$
DECLARE
    job_wrapper_id UUID;
    match_wrapper_id UUID;
    action_wrapper_id UUID;
    mapping_wrapper_id UUID;
BEGIN
    -- JOB_PROFILE
    SELECT id INTO job_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE job_profile_id = 'cf6f2718-5aa5-482a-bba5-5bc9b75614da';
    IF job_wrapper_id IS NULL THEN
        job_wrapper_id = '56b866e8-3dff-4eb1-827e-b8f9bec8219c';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id)
        VALUES (job_wrapper_id, 'JOB_PROFILE', 'cf6f2718-5aa5-482a-bba5-5bc9b75614da') ON CONFLICT DO NOTHING;
    END IF;

    -- MATCH_PROFILE
    SELECT id INTO match_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE match_profile_id = '91cec42a-260d-4a8c-a9fb-90d9435ca2f4';
    IF match_wrapper_id IS NULL THEN
        match_wrapper_id = 'a3c8c9fb-0dc2-4fff-a968-cffc14119f4a';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, match_profile_id)
        VALUES (match_wrapper_id, 'MATCH_PROFILE', '91cec42a-260d-4a8c-a9fb-90d9435ca2f4') ON CONFLICT DO NOTHING;
    END IF;

    -- ACTION_PROFILE
    SELECT id INTO action_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE action_profile_id = 'c2e2d482-9486-476e-a28c-8f1e303cbe1a';
    IF action_wrapper_id IS NULL THEN
        action_wrapper_id = '71dbaa0b-aeb0-4a06-9b65-2d7987087f46';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id)
        VALUES (action_wrapper_id, 'ACTION_PROFILE', 'c2e2d482-9486-476e-a28c-8f1e303cbe1a') ON CONFLICT DO NOTHING;
    END IF;

    -- MAPPING_PROFILE
    SELECT id INTO mapping_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE mapping_profile_id = '39b265e1-c963-4e5f-859d-6e8c327a265c';
    IF mapping_wrapper_id IS NULL THEN
        mapping_wrapper_id = '069e1890-408b-4b49-a121-db5e33ec375f';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id)
        VALUES (mapping_wrapper_id, 'MAPPING_PROFILE', '39b265e1-c963-4e5f-859d-6e8c327a265c') ON CONFLICT DO NOTHING;
    END IF;

    -- Create profile_associations
    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id,
        detail_wrapper_id, master_profile_id, detail_profile_id,
        master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
        ('83477fa3-1db1-4088-af0e-3c5fccb7e337', NULL, job_wrapper_id, match_wrapper_id,
         'cf6f2718-5aa5-482a-bba5-5bc9b75614da', '91cec42a-260d-4a8c-a9fb-90d9435ca2f4',
         'JOB_PROFILE', 'MATCH_PROFILE', 0, NULL) ON CONFLICT DO NOTHING;
    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id,
            detail_wrapper_id, master_profile_id, detail_profile_id,
            master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
        ('e5f754a0-1b44-487c-b037-bb68eebba383', 'cf6f2718-5aa5-482a-bba5-5bc9b75614da',
         match_wrapper_id, action_wrapper_id, '91cec42a-260d-4a8c-a9fb-90d9435ca2f4',
         'c2e2d482-9486-476e-a28c-8f1e303cbe1a', 'MATCH_PROFILE', 'ACTION_PROFILE', 0, 'MATCH') ON CONFLICT DO NOTHING;
    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id,
            detail_wrapper_id, master_profile_id, detail_profile_id,
            master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
        ('4e2bf7bf-dee1-4e7a-9074-e2139ef3f031', null,
         action_wrapper_id, mapping_wrapper_id, 'c2e2d482-9486-476e-a28c-8f1e303cbe1a',
         '39b265e1-c963-4e5f-859d-6e8c327a265c', 'ACTION_PROFILE', 'MAPPING_PROFILE', 0, null) ON CONFLICT DO NOTHING;
END
$$;
