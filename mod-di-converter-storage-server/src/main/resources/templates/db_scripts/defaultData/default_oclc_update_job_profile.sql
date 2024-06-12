INSERT INTO ${myuniversity}_${mymodule}.job_profiles (id, jsonb) values
('91f9b8d6-d80e-4727-9783-73fb53e3c786', '{
	"id": "91f9b8d6-d80e-4727-9783-73fb53e3c786",
	"name": "Inventory Single Record - Default Update Instance",
	"description": "Triggered by an action in Inventory, this job profile imports a single record from an external system, to update an existing Instance, and either create a new MARC record or update an existing MARC record",
	"deleted": false,
	"dataType": "MARC",
	"metadata": {
		"createdDate": "2020-11-30T09:07:47.667",
		"updatedDate": "2020-11-30T09:09:10.382+0000",
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
('cddff0e1-233c-47ba-8be5-553c632709d9',
'{
	"id": "cddff0e1-233c-47ba-8be5-553c632709d9",
	"name": "Inventory Single Record - Default Update Instance",
	"action": "UPDATE",
	"deleted": false,
	"remove9Subfields": true,
	"metadata": {
		"createdDate": "2020-11-30T09:03:05.334",
		"updatedDate": "2020-11-30T11:57:14.464+0000",
		"createdByUserId": "6a010e5b-5421-5b1c-9b52-568b37038575",
		"updatedByUserId": "6a010e5b-5421-5b1c-9b52-568b37038575"
	},
	"userInfo": {
		"lastName": "System",
		"userName": "System",
		"firstName": "System"
	},
	"description": "Updates existing Inventory Instances based on Inventory single record imports",
	"folioRecord": "INSTANCE",
	"childProfiles": [],
	"parentProfiles": []
}
') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.action_profiles (id, jsonb) values
('6aa8e98b-0d9f-41dd-b26f-15658d07eb52',
'{
	"id": "6aa8e98b-0d9f-41dd-b26f-15658d07eb52",
	"name": "Inventory Single Record - Default Update MARC Bib",
	"action": "UPDATE",
	"deleted": false,
	"remove9Subfields": true,
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
	"description": "Updates existing SRS MARC Bib records based on Inventory single record imports",
	"folioRecord": "MARC_BIBLIOGRAPHIC",
	"childProfiles": [],
	"parentProfiles": []
}
') ON CONFLICT DO NOTHING;


INSERT INTO ${myuniversity}_${mymodule}.match_profiles (id, jsonb) values
('d27d71ce-8a1e-44c6-acea-96961b5592c6',
'{
	"id": "d27d71ce-8a1e-44c6-acea-96961b5592c6",
	"name": "Inventory Single Record - Default match for existing SRS record",
	"deleted": false,
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
	"description": "Matches the Instance UUID from the 999 ff $i in the incoming MARC record to the same field in any SRS MARC Bib",
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
						"value": "i"
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
						"value": "i"
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

INSERT INTO ${myuniversity}_${mymodule}.match_profiles (id, jsonb) values
('31dbb554-0826-48ec-a0a4-3c55293d4dee',
'{
	"id": "31dbb554-0826-48ec-a0a4-3c55293d4dee",
	"name": "Inventory Single Record - Default match for no SRS record",
	"deleted": false,
	"metadata": {
		"createdDate": "2020-11-30T09:06:57.367",
		"updatedDate": "2020-11-30T10:00:10.359+0000",
		"createdByUserId": "6a010e5b-5421-5b1c-9b52-568b37038575",
		"updatedByUserId": "6a010e5b-5421-5b1c-9b52-568b37038575"
	},
	"userInfo": {
		"lastName": "System",
		"userName": "System",
		"firstName": "System"
	},
	"description": "Matches the Instance UUID from the 999 ff $i in the incoming MARC record to the UUID of the existing Instance record",
	"matchDetails": [{
			"matchCriterion": "EXACTLY_MATCHES",
			"existingRecordType": "INSTANCE",
			"incomingRecordType": "MARC_BIBLIOGRAPHIC",
			"existingMatchExpression": {
				"fields": [{
						"label": "field",
						"value": "instance.id"
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
						"value": "i"
					}
				],
				"dataValueType": "VALUE_FROM_RECORD"
			}
		}
	],
	"childProfiles": [],
	"parentProfiles": [],
	"existingRecordType": "INSTANCE",
	"incomingRecordType": "MARC_BIBLIOGRAPHIC"
}
') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.mapping_profiles (id, jsonb) values
('862000b9-84ea-4cae-a223-5fc0552f2b42',
'{
	"id": "862000b9-84ea-4cae-a223-5fc0552f2b42",
	"name": "Inventory Single Record - Default Update Instance",
	"deleted": false,
	"metadata": {
		"createdDate": "2020-11-30T09:01:29.039",
		"updatedDate": "2020-11-30T11:57:35.927+0000",
		"createdByUserId": "6a010e5b-5421-5b1c-9b52-568b37038575",
		"updatedByUserId": "6a010e5b-5421-5b1c-9b52-568b37038575"
	},
	"userInfo": {
		"lastName": "System",
		"userName": "System",
		"firstName": "System"
	},
	"description": "Updates existing Inventory Instances based on Inventory single record imports",
	"childProfiles": [],
	"mappingDetails": {
		"name": "instance",
		"recordType": "INSTANCE",
		"mappingFields": [{
				"name": "discoverySuppress",
				"path": "instance.discoverySuppress",
				"value": "",
				"enabled": "true",
				"subfields": []
			}, {
				"name": "staffSuppress",
				"path": "instance.staffSuppress",
				"value": "",
				"enabled": "true",
				"subfields": []
			}, {
				"name": "previouslyHeld",
				"path": "instance.previouslyHeld",
				"value": "",
				"enabled": "true",
				"subfields": []
			}, {
				"name": "hrid",
				"path": "instance.hrid",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "source",
				"path": "instance.source",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "catalogedDate",
				"path": "instance.catalogedDate",
				"value": "",
				"enabled": "true",
				"subfields": []
			}, {
				"name": "statusId",
				"path": "instance.statusId",
				"value": "",
				"enabled": "true",
				"subfields": [],
				"acceptedValues": {
					"26f5208e-110a-4394-be29-1569a8c84a65": "Uncataloged",
					"2a340d34-6b70-443a-bb1b-1b8d1c65d862": "Other",
					"52a2ff34-2a12-420d-8539-21aa8d3cf5d8": "Batch Loaded",
					"9634a5ab-9228-4703-baf2-4d12ebc77d56": "Cataloged",
					"daf2681c-25af-4202-a3fa-e58fdf806183": "Temporary",
					"f5cc2ab6-bb92-4cab-b83f-5a3d09261a41": "Not yet assigned"
				}
			}, {
				"name": "modeOfIssuanceId",
				"path": "instance.modeOfIssuanceId",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "statisticalCodeIds",
				"path": "instance.statisticalCodeIds[]",
				"value": "",
				"enabled": "true",
				"subfields": []
			}, {
				"name": "title",
				"path": "instance.title",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "alternativeTitles",
				"path": "instance.alternativeTitles[]",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "indexTitle",
				"path": "instance.indexTitle",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "series",
				"path": "instance.series[]",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "precedingTitles",
				"path": "instance.precedingTitles[]",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "succeedingTitles",
				"path": "instance.succeedingTitles[]",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "identifiers",
				"path": "instance.identifiers[]",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "contributors",
				"path": "instance.contributors[]",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "publication",
				"path": "instance.publication[]",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "editions",
				"path": "instance.editions[]",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "physicalDescriptions",
				"path": "instance.physicalDescriptions[]",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "instanceTypeId",
				"path": "instance.instanceTypeId",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "natureOfContentTermIds",
				"path": "instance.natureOfContentTermIds[]",
				"value": "",
				"enabled": "true",
				"subfields": []
			}, {
				"name": "instanceFormatIds",
				"path": "instance.instanceFormatIds[]",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "languages",
				"path": "instance.languages[]",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "publicationFrequency",
				"path": "instance.publicationFrequency[]",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "publicationRange",
				"path": "instance.publicationRange[]",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "notes",
				"path": "instance.notes[]",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "electronicAccess",
				"path": "instance.electronicAccess[]",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "subjects",
				"path": "instance.subjects[]",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "classifications",
				"path": "instance.classifications[]",
				"value": "",
				"enabled": "false",
				"subfields": []
			}, {
				"name": "parentInstances",
				"path": "instance.parentInstances[]",
				"value": "",
				"enabled": "true",
				"subfields": []
			}, {
				"name": "childInstances",
				"path": "instance.childInstances[]",
				"value": "",
				"enabled": "true",
				"subfields": []
			}
		],
		"marcMappingDetails": []
	},
	"parentProfiles": [],
	"existingRecordType": "INSTANCE",
	"incomingRecordType": "MARC_BIBLIOGRAPHIC",
	"marcFieldProtectionSettings": []
}
') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.mapping_profiles (id, jsonb) values
('f90864ef-8030-480f-a43f-8cdd21233252',
'{
	"id": "f90864ef-8030-480f-a43f-8cdd21233252",
	"name": "Inventory Single Record - Default Update MARC Bib",
	"deleted": false,
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
	"description": "Updates existing SRS MARC Bib records based on Inventory single record imports",
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

INSERT INTO ${myuniversity}_${mymodule}.job_to_match_profiles (id, jsonb) values
('624c99a2-49ba-45b7-b5bf-1f385182a62c',
'{
	"id": "624c99a2-49ba-45b7-b5bf-1f385182a62c",
	"order": 0,
	"triggered": false,
	"detailProfileId": "d27d71ce-8a1e-44c6-acea-96961b5592c6",
	"masterProfileId": "91f9b8d6-d80e-4727-9783-73fb53e3c786",
	"detailProfileType": "MATCH_PROFILE",
	"masterProfileType": "JOB_PROFILE"
}
') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.match_to_action_profiles (id, jsonb) values
('516301b2-d511-4134-9943-377744af007f',
'{
	"id": "516301b2-d511-4134-9943-377744af007f",
	"order": 0,
	"reactTo": "MATCH",
	"triggered": false,
	"jobProfileId": "91f9b8d6-d80e-4727-9783-73fb53e3c786",
	"detailProfileId": "6aa8e98b-0d9f-41dd-b26f-15658d07eb52",
	"masterProfileId": "d27d71ce-8a1e-44c6-acea-96961b5592c6",
	"detailProfileType": "ACTION_PROFILE",
	"masterProfileType": "MATCH_PROFILE"
}
') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.action_to_mapping_profiles (id, jsonb) values
('34a26c5d-d9a6-4a9f-a1f6-b1b5249fb1f7',
'{
	"id": "34a26c5d-d9a6-4a9f-a1f6-b1b5249fb1f7",
	"order": 0,
	"triggered": false,
	"detailProfileId": "f90864ef-8030-480f-a43f-8cdd21233252",
	"masterProfileId": "6aa8e98b-0d9f-41dd-b26f-15658d07eb52",
	"detailProfileType": "MAPPING_PROFILE",
	"masterProfileType": "ACTION_PROFILE"
}') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.match_to_match_profiles (id, jsonb) values
('8d1c9e5e-032b-49ff-986e-e84adffc9955',
'{
	"id": "8d1c9e5e-032b-49ff-986e-e84adffc9955",
	"order": 0,
	"reactTo": "NON_MATCH",
	"triggered": false,
	"jobProfileId": "91f9b8d6-d80e-4727-9783-73fb53e3c786",
	"detailProfileId": "31dbb554-0826-48ec-a0a4-3c55293d4dee",
	"masterProfileId": "d27d71ce-8a1e-44c6-acea-96961b5592c6",
	"detailProfileType": "MATCH_PROFILE",
	"masterProfileType": "MATCH_PROFILE"
}
') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.match_to_action_profiles (id, jsonb) values
('3e569bda-c996-45df-a3c0-ad124058b982',
'{
	"id": "3e569bda-c996-45df-a3c0-ad124058b982",
	"order": 0,
	"reactTo": "MATCH",
	"triggered": false,
	"jobProfileId": "91f9b8d6-d80e-4727-9783-73fb53e3c786",
	"detailProfileId": "cddff0e1-233c-47ba-8be5-553c632709d9",
	"masterProfileId": "31dbb554-0826-48ec-a0a4-3c55293d4dee",
	"detailProfileType": "ACTION_PROFILE",
	"masterProfileType": "MATCH_PROFILE"
}') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.action_to_mapping_profiles (id, jsonb) values
('185b86b2-eca1-4a64-885d-f2dab21c0fc0',
'{
	"id": "185b86b2-eca1-4a64-885d-f2dab21c0fc0",
	"order": 0,
	"triggered": false,
	"detailProfileId": "862000b9-84ea-4cae-a223-5fc0552f2b42",
	"masterProfileId": "cddff0e1-233c-47ba-8be5-553c632709d9",
	"detailProfileType": "MAPPING_PROFILE",
	"masterProfileType": "ACTION_PROFILE"
}

') ON CONFLICT DO NOTHING;

DO
$$
DECLARE
    job_wrapper_id UUID;
    action_wrapper_id UUID;
    mapping_wrapper_id UUID;
    match_wrapper_id UUID;
    match_wrapper_id2 UUID;
    action_wrapper_id2 UUID;
    mapping_wrapper_id2 UUID;
BEGIN
    -- JOB_PROFILE
    SELECT id INTO job_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE job_profile_id = '91f9b8d6-d80e-4727-9783-73fb53e3c786';
    IF job_wrapper_id IS NULL THEN
        job_wrapper_id = '94cba6e2-6441-4ae1-8ced-40674883b97f';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id)
        VALUES (job_wrapper_id, 'JOB_PROFILE', '91f9b8d6-d80e-4727-9783-73fb53e3c786') ON CONFLICT DO NOTHING;
    END IF;

    -- MATCH_PROFILE 1
    SELECT id INTO match_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE match_profile_id = 'd27d71ce-8a1e-44c6-acea-96961b5592c6';
    IF match_wrapper_id IS NULL THEN
        match_wrapper_id = '5c690f8c-824f-4a1a-869c-3eb4e7ce9a87';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, match_profile_id)
        VALUES (match_wrapper_id, 'MATCH_PROFILE', 'd27d71ce-8a1e-44c6-acea-96961b5592c6') ON CONFLICT DO NOTHING;
    END IF;

    -- MATCH_PROFILE 2
    SELECT id INTO match_wrapper_id2 FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE match_profile_id = '31dbb554-0826-48ec-a0a4-3c55293d4dee';
    IF match_wrapper_id2 IS NULL THEN
        match_wrapper_id2 = '79bd243d-05ad-490a-bb9e-aba7f5a51b2e';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, match_profile_id)
        VALUES (match_wrapper_id2, 'MATCH_PROFILE', '31dbb554-0826-48ec-a0a4-3c55293d4dee') ON CONFLICT DO NOTHING;
    END IF;

    -- ACTION_PROFILE 1
    SELECT id INTO action_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE action_profile_id = '6aa8e98b-0d9f-41dd-b26f-15658d07eb52';
    IF action_wrapper_id IS NULL THEN
        action_wrapper_id = '8fc60cdb-d3a1-426e-b7c6-aab593418797';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id)
        VALUES (action_wrapper_id, 'ACTION_PROFILE', '6aa8e98b-0d9f-41dd-b26f-15658d07eb52') ON CONFLICT DO NOTHING;
    END IF;

    -- ACTION_PROFILE 2
    SELECT id INTO action_wrapper_id2 FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE action_profile_id = 'cddff0e1-233c-47ba-8be5-553c632709d9';
    IF action_wrapper_id2 IS NULL THEN
        action_wrapper_id2 = '33b6e902-a79a-4afb-b531-faa3ffb0cd07';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id)
        VALUES (action_wrapper_id2, 'ACTION_PROFILE', 'cddff0e1-233c-47ba-8be5-553c632709d9') ON CONFLICT DO NOTHING;
    END IF;

    -- MAPPING_PROFILE 1
    SELECT id INTO mapping_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE mapping_profile_id = 'f90864ef-8030-480f-a43f-8cdd21233252';
    IF mapping_wrapper_id IS NULL THEN
        mapping_wrapper_id = '841d32f5-b66f-4cf7-b2da-ef7abe5fd00c';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id)
        VALUES (mapping_wrapper_id, 'MAPPING_PROFILE', 'f90864ef-8030-480f-a43f-8cdd21233252') ON CONFLICT DO NOTHING;
    END IF;

    -- MAPPING_PROFILE 2
    SELECT id INTO mapping_wrapper_id2 FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE mapping_profile_id = '862000b9-84ea-4cae-a223-5fc0552f2b42';
    IF mapping_wrapper_id2 IS NULL THEN
        mapping_wrapper_id2 = 'd5bf86d7-433e-4e13-9ec9-173777120045';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id)
        VALUES (mapping_wrapper_id2, 'MAPPING_PROFILE', '862000b9-84ea-4cae-a223-5fc0552f2b42') ON CONFLICT DO NOTHING;
    END IF;

    -- Create profile_associations
    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id, detail_wrapper_id, master_profile_id, detail_profile_id, master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
    ('624c99a2-49ba-45b7-b5bf-1f385182a62c', NULL, job_wrapper_id, match_wrapper_id, '91f9b8d6-d80e-4727-9783-73fb53e3c786', 'd27d71ce-8a1e-44c6-acea-96961b5592c6', 'JOB_PROFILE', 'MATCH_PROFILE', 0, NULL) ON CONFLICT DO NOTHING;

    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id, detail_wrapper_id, master_profile_id, detail_profile_id, master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
    ('516301b2-d511-4134-9943-377744af007f', '91f9b8d6-d80e-4727-9783-73fb53e3c786', match_wrapper_id, action_wrapper_id, 'd27d71ce-8a1e-44c6-acea-96961b5592c6', '6aa8e98b-0d9f-41dd-b26f-15658d07eb52', 'MATCH_PROFILE', 'ACTION_PROFILE', 0, 'MATCH') ON CONFLICT DO NOTHING;

    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id, detail_wrapper_id, master_profile_id, detail_profile_id, master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
    ('34a26c5d-d9a6-4a9f-a1f6-b1b5249fb1f7', NULL, action_wrapper_id, mapping_wrapper_id, '6aa8e98b-0d9f-41dd-b26f-15658d07eb52', 'f90864ef-8030-480f-a43f-8cdd21233252', 'ACTION_PROFILE', 'MAPPING_PROFILE', 0, NULL) ON CONFLICT DO NOTHING;

    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id, detail_wrapper_id, master_profile_id, detail_profile_id, master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
    ('8d1c9e5e-032b-49ff-986e-e84adffc9955', '91f9b8d6-d80e-4727-9783-73fb53e3c786', match_wrapper_id, match_wrapper_id2, 'd27d71ce-8a1e-44c6-acea-96961b5592c6', '31dbb554-0826-48ec-a0a4-3c55293d4dee', 'MATCH_PROFILE', 'MATCH_PROFILE', 0, 'NON_MATCH') ON CONFLICT DO NOTHING;

    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id, detail_wrapper_id, master_profile_id, detail_profile_id, master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
    ('3e569bda-c996-45df-a3c0-ad124058b982', '91f9b8d6-d80e-4727-9783-73fb53e3c786', match_wrapper_id2, action_wrapper_id2, '31dbb554-0826-48ec-a0a4-3c55293d4dee', 'cddff0e1-233c-47ba-8be5-553c632709d9', 'MATCH_PROFILE', 'ACTION_PROFILE', 0, 'MATCH') ON CONFLICT DO NOTHING;

    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id, detail_wrapper_id, master_profile_id, detail_profile_id, master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
    ('185b86b2-eca1-4a64-885d-f2dab21c0fc0', NULL, action_wrapper_id2, match_wrapper_id2, 'cddff0e1-233c-47ba-8be5-553c632709d9', '862000b9-84ea-4cae-a223-5fc0552f2b42', 'ACTION_PROFILE', 'MAPPING_PROFILE', 0, NULL) ON CONFLICT DO NOTHING;
END
$$;
