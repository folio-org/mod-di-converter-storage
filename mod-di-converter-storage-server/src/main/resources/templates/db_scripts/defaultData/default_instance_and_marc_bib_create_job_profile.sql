INSERT INTO ${myuniversity}_${mymodule}.job_profiles (id, jsonb) values
    ('e34d7b92-9b83-11eb-a8b3-0242ac130003', '{
	"id": "e34d7b92-9b83-11eb-a8b3-0242ac130003",
	"name": "Default - Create instance and SRS MARC Bib",
	"dataType": "MARC",
	"metadata": {
		"createdDate": "2021-04-13T14:00:00.000",
		"updatedDate": "2021-04-13T15:00:00.462+0000",
		"createdByUserId": "00000000-0000-0000-0000-000000000000",
		"updatedByUserId": "00000000-0000-0000-0000-000000000000"
	},
	"userInfo": {
		"lastName": "System",
		"userName": "System",
		"firstName": "System"
	},
	"description": "This job profile creates SRS MARC Bib records and corresponding Inventory Instances using the library''s default MARC-to-Instance mapping. It can be edited, duplicated, or deleted.",
	"childProfiles": [],
	"parentProfiles": []
}
') ON CONFLICT DO NOTHING;


INSERT INTO ${myuniversity}_${mymodule}.action_profiles (id, jsonb) values
    ('fa45f3ec-9b83-11eb-a8b3-0242ac130003',
'{
	"id": "fa45f3ec-9b83-11eb-a8b3-0242ac130003",
	"name": "Default - Create instance",
	"action": "CREATE",
	"remove9Subfields": true,
	"metadata": {
		"createdDate": "2021-04-13T14:00:00.000",
		"updatedDate": "2021-04-13T15:00:00.462+0000",
		"createdByUserId": "00000000-0000-0000-0000-000000000000",
		"updatedByUserId": "00000000-0000-0000-0000-000000000000"
	},
	"userInfo": {
		"lastName": "System",
		"userName": "System",
		"firstName": "System"
	},
	"description": "This action profile is used with FOLIO''s default job profile for creating Inventory Instances and SRS MARC Bibliographic records. It can be edited, duplicated, or deleted.",
	"folioRecord": "INSTANCE",
	"childProfiles": [],
	"parentProfiles": []
}') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.mapping_profiles (id, jsonb) values
    ('bf7b3b86-9b84-11eb-a8b3-0242ac130003',
    '{
	"id": "bf7b3b86-9b84-11eb-a8b3-0242ac130003",
	"name": "Default - Create instance",
	"metadata": {
		"createdDate": "2021-04-13T14:00:00.000",
		"updatedDate": "2021-04-13T15:00:00.462+0000",
		"createdByUserId": "00000000-0000-0000-0000-000000000000",
		"updatedByUserId": "00000000-0000-0000-0000-000000000000"
	},
	"userInfo": {
		"lastName": "System",
		"userName": "System",
		"firstName": "System"
	},
	"description": "This field mapping profile is used with FOLIO''s default job profile for creating Inventory Instances and SRS MARC Bibliographic records. It can be edited, duplicated, deleted, or linked to additional action profiles.",
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
				"subfields": []
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

INSERT INTO ${myuniversity}_${mymodule}.job_to_action_profiles (id, jsonb) values
    ('d63003ac-9b84-11eb-a8b3-0242ac130003',
'{
	"id": "d63003ac-9b84-11eb-a8b3-0242ac130003",
	"order": 0,
	"triggered": false,
	"detailProfileId": "fa45f3ec-9b83-11eb-a8b3-0242ac130003",
	"masterProfileId": "e34d7b92-9b83-11eb-a8b3-0242ac130003",
	"detailProfileType": "ACTION_PROFILE",
	"masterProfileType": "JOB_PROFILE"
}') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.action_to_mapping_profiles (id, jsonb) values
    ('e1151d70-9b84-11eb-a8b3-0242ac130003',
'{
	"id": "e1151d70-9b84-11eb-a8b3-0242ac130003",
	"order": 0,
	"triggered": false,
	"detailProfileId": "bf7b3b86-9b84-11eb-a8b3-0242ac130003",
	"masterProfileId": "fa45f3ec-9b83-11eb-a8b3-0242ac130003",
	"detailProfileType": "MAPPING_PROFILE",
	"masterProfileType": "ACTION_PROFILE"
}') ON CONFLICT DO NOTHING;

DO
$$
DECLARE
    job_wrapper_id UUID;
    action_wrapper_id UUID;
    mapping_wrapper_id UUID;
BEGIN
    -- JOB_PROFILE
    SELECT id INTO job_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE job_profile_id = 'e34d7b92-9b83-11eb-a8b3-0242ac130003';
    IF job_wrapper_id IS NULL THEN
        job_wrapper_id = 'e1f5063c-9f0c-481d-afb7-48beca30cf9a';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id)
        VALUES (job_wrapper_id, 'JOB_PROFILE', 'e34d7b92-9b83-11eb-a8b3-0242ac130003') ON CONFLICT DO NOTHING;
    END IF;

    -- ACTION_PROFILE
    SELECT id INTO action_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE action_profile_id = 'fa45f3ec-9b83-11eb-a8b3-0242ac130003';
    IF action_wrapper_id IS NULL THEN
        action_wrapper_id = 'de887df7-dc88-4c4b-9f38-3ca736395c59';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id)
        VALUES (action_wrapper_id, 'ACTION_PROFILE', 'fa45f3ec-9b83-11eb-a8b3-0242ac130003') ON CONFLICT DO NOTHING;
    END IF;

    -- MAPPING_PROFILE
    SELECT id INTO mapping_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE mapping_profile_id = 'bf7b3b86-9b84-11eb-a8b3-0242ac130003';
    IF mapping_wrapper_id IS NULL THEN
        mapping_wrapper_id = '4d0014e0-5b55-4d55-bdf3-1d60786df115';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id)
        VALUES (mapping_wrapper_id, 'MAPPING_PROFILE', 'bf7b3b86-9b84-11eb-a8b3-0242ac130003') ON CONFLICT DO NOTHING;
    END IF;

    -- Create profile_associations
    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id,
        detail_wrapper_id, master_profile_id, detail_profile_id,
        master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
        ('d63003ac-9b84-11eb-a8b3-0242ac130003', NULL, job_wrapper_id, action_wrapper_id,
         'e34d7b92-9b83-11eb-a8b3-0242ac130003', 'fa45f3ec-9b83-11eb-a8b3-0242ac130003', 'JOB_PROFILE', 'ACTION_PROFILE', 0, NULL) ON CONFLICT DO NOTHING;
    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id,
            detail_wrapper_id, master_profile_id, detail_profile_id,
            master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
        ('e1151d70-9b84-11eb-a8b3-0242ac130003', NULL, action_wrapper_id, mapping_wrapper_id,
         'fa45f3ec-9b83-11eb-a8b3-0242ac130003', 'bf7b3b86-9b84-11eb-a8b3-0242ac130003', 'ACTION_PROFILE', 'MAPPING_PROFILE', 0, NULL) ON CONFLICT DO NOTHING;
END
$$;
