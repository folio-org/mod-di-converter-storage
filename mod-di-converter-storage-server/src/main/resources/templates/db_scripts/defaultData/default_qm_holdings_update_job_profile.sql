INSERT INTO ${myuniversity}_${mymodule}.job_profiles (id, jsonb) values
('6cb347c6-c0b0-4363-89fc-32cedede87ba', '{
	"id": "6cb347c6-c0b0-4363-89fc-32cedede87ba",
	"name": "quickMARC - Default Update holdings",
	"description": "This job profile is used by the quickMARC to allow a user to update an SRS MARC holdings record and corresponding Inventory holdings. Profile cannot be edited or deleted",
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
('7e24a466-349b-451d-a18e-38fb21d71b38',
'{
	"id": "7e24a466-349b-451d-a18e-38fb21d71b38",
	"name": "quickMARC - Default Update MARC holdings",
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
	"description": "Updates existing SRS MARC holdings record",
	"folioRecord": "MARC_HOLDINGS",
	"childProfiles": [],
	"parentProfiles": []
}
') ON CONFLICT DO NOTHING;


INSERT INTO ${myuniversity}_${mymodule}.match_profiles (id, jsonb) values
('2a599369-817f-4fe8-bae2-f3e3987990fe',
'{
	"id": "2a599369-817f-4fe8-bae2-f3e3987990fe",
	"name": "quickMARC - Default match for existing SRS holdings",
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
	"description": "Matches the UUID from the 999 ff $s in the incoming MARC record to the same field in any SRS MARC holdings",
	"matchDetails": [{
			"matchCriterion": "EXACTLY_MATCHES",
			"existingRecordType": "MARC_HOLDINGS",
			"incomingRecordType": "MARC_HOLDINGS",
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
	"existingRecordType": "MARC_HOLDINGS",
	"incomingRecordType": "MARC_HOLDINGS"
}
') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.mapping_profiles (id, jsonb) values
('b8a9ca7d-4a33-44d3-86e1-f7c6cb7b265f',
'{
	"id": "b8a9ca7d-4a33-44d3-86e1-f7c6cb7b265f",
	"name": "quickMARC - Default Update MARC holdings",
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
	"description": "Updates existing SRS MARC holdings record",
	"childProfiles": [],
	"mappingDetails": {
		"name": "marcHoldings",
		"recordType": "MARC_HOLDINGS",
		"mappingFields": [],
		"marcMappingOption": "UPDATE",
		"marcMappingDetails": []
	},
	"parentProfiles": [],
	"existingRecordType": "MARC_HOLDINGS",
	"incomingRecordType": "MARC_HOLDINGS",
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
    SELECT id INTO job_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE job_profile_id = '6cb347c6-c0b0-4363-89fc-32cedede87ba';
    IF job_wrapper_id IS NULL THEN
        job_wrapper_id = 'e3c6c549-049f-4448-be35-49e7bbfb40a6';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id)
        VALUES (job_wrapper_id, 'JOB_PROFILE', '6cb347c6-c0b0-4363-89fc-32cedede87ba') ON CONFLICT DO NOTHING;
    END IF;

    -- MATCH_PROFILE
    SELECT id INTO match_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE match_profile_id = '2a599369-817f-4fe8-bae2-f3e3987990fe';
    IF match_wrapper_id IS NULL THEN
        match_wrapper_id = 'f31b5e40-0187-4fd9-8558-d728363be43b';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, match_profile_id)
        VALUES (match_wrapper_id, 'MATCH_PROFILE', '2a599369-817f-4fe8-bae2-f3e3987990fe') ON CONFLICT DO NOTHING;
    END IF;

    -- ACTION_PROFILE
    SELECT id INTO action_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE action_profile_id = '7e24a466-349b-451d-a18e-38fb21d71b38';
    IF action_wrapper_id IS NULL THEN
        action_wrapper_id = '0dd70479-bc19-4dc1-9e64-114e6b1ade45';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id)
        VALUES (action_wrapper_id, 'ACTION_PROFILE', '7e24a466-349b-451d-a18e-38fb21d71b38') ON CONFLICT DO NOTHING;
    END IF;

    -- MAPPING_PROFILE
    SELECT id INTO mapping_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE mapping_profile_id = 'b8a9ca7d-4a33-44d3-86e1-f7c6cb7b265f';
    IF mapping_wrapper_id IS NULL THEN
        mapping_wrapper_id = '63a2fb7e-d9fb-46c4-b204-25c327c4a2ed';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id)
        VALUES (mapping_wrapper_id, 'MAPPING_PROFILE', 'b8a9ca7d-4a33-44d3-86e1-f7c6cb7b265f') ON CONFLICT DO NOTHING;
    END IF;

    -- Create profile_associations
    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id,
        detail_wrapper_id, master_profile_id, detail_profile_id,
        master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
        ('364ab86a-e11c-4dd2-9ad5-efadbe79347b', NULL, job_wrapper_id, match_wrapper_id,
         '6cb347c6-c0b0-4363-89fc-32cedede87ba', '2a599369-817f-4fe8-bae2-f3e3987990fe',
         'JOB_PROFILE', 'MATCH_PROFILE', 0, NULL) ON CONFLICT DO NOTHING;
    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id,
            detail_wrapper_id, master_profile_id, detail_profile_id,
            master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
        ('31a69e89-4872-435c-b593-15664146cc2b', '6cb347c6-c0b0-4363-89fc-32cedede87ba',
         match_wrapper_id, action_wrapper_id, '2a599369-817f-4fe8-bae2-f3e3987990fe',
         '7e24a466-349b-451d-a18e-38fb21d71b38', 'MATCH_PROFILE', 'ACTION_PROFILE', 0, 'MATCH') ON CONFLICT DO NOTHING;
    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id,
            detail_wrapper_id, master_profile_id, detail_profile_id,
            master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
        ('96876af2-2a2b-40c0-9ffd-9538a5b39dd6', null,
         action_wrapper_id, mapping_wrapper_id, '7e24a466-349b-451d-a18e-38fb21d71b38',
         'b8a9ca7d-4a33-44d3-86e1-f7c6cb7b265f', 'ACTION_PROFILE', 'MAPPING_PROFILE', 0, null) ON CONFLICT DO NOTHING;
END
$$;
