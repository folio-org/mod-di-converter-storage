INSERT INTO ${myuniversity}_${mymodule}.job_profiles (id, jsonb) values
  ('90fd4389-e5a9-4cc5-88cf-1568c0ff7e8b', '{
	"id": "90fd4389-e5a9-4cc5-88cf-1568c0ff7e8b",
	"name": "ECS - Create instance and SRS MARC Bib",
	"deleted": false,
	"dataType": "MARC",
	"metadata": {
		"createdDate": "2023-10-18T14:00:00.000",
		"updatedDate": "2023-10-18T15:00:00.000",
		"createdByUserId": "00000000-0000-0000-0000-000000000000",
		"updatedByUserId": "00000000-0000-0000-0000-000000000000"
	},
	"userInfo": {
		"lastName": "System",
		"userName": "System",
		"firstName": "System"
	},
	"description": "This job profile is used to create ECS instance during sharing process. It is hidden.",
	"childProfiles": [],
	"parentProfiles": [],
  "hidden": true
}
') ON CONFLICT DO NOTHING;


INSERT INTO ${myuniversity}_${mymodule}.action_profiles (id, jsonb) values
  ('671a848a-eb4e-49d2-9e01-41c179e789f5',
   '{
     "id": "671a848a-eb4e-49d2-9e01-41c179e789f5",
     "name": "ECS - Create instance",
     "action": "CREATE",
     "deleted": false,
     "remove9Subfields": false,
     "metadata": {
       "createdDate": "2023-10-18T14:00:00.000",
       "updatedDate": "2023-10-18T15:00:00.000",
       "createdByUserId": "00000000-0000-0000-0000-000000000000",
       "updatedByUserId": "00000000-0000-0000-0000-000000000000"
     },
     "userInfo": {
       "lastName": "System",
       "userName": "System",
       "firstName": "System"
     },
     "description": "This action profile is used to create ECS instance during sharing process. It is hidden.",
     "folioRecord": "INSTANCE",
     "childProfiles": [],
     "parentProfiles": [],
     "hidden": true
   }') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.mapping_profiles (id, jsonb) values
  ('f8d7e135-3c35-4c60-bb33-0a3cf01e7b94',
   '{
 "id": "f8d7e135-3c35-4c60-bb33-0a3cf01e7b94",
 "name": "ECS - Create instance",
 "deleted": false,
 "hidden": true,
 "metadata": {
   "createdDate": "2023-10-18T14:00:00.000",
   "updatedDate": "2023-10-18T15:00:00.000",
   "createdByUserId": "00000000-0000-0000-0000-000000000000",
   "updatedByUserId": "00000000-0000-0000-0000-000000000000"
 },
 "userInfo": {
   "lastName": "System",
   "userName": "System",
   "firstName": "System"
 },
 "description": "This field mapping profile is used to create ECS instance during sharing process. It is hidden.",
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

DO
$$
DECLARE
    job_wrapper_id UUID;
    action_wrapper_id UUID;
    mapping_wrapper_id UUID;
BEGIN
    SELECT id INTO job_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE job_profile_id = '90fd4389-e5a9-4cc5-88cf-1568c0ff7e8b';
        IF job_wrapper_id IS NULL THEN
            job_wrapper_id = 'b4b102e0-7fb4-4b77-97f1-782927839eb0';
            INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id)
            VALUES (job_wrapper_id, 'JOB_PROFILE', '90fd4389-e5a9-4cc5-88cf-1568c0ff7e8b') ON CONFLICT DO NOTHING;
        END IF;

    SELECT id INTO action_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE action_profile_id = '671a848a-eb4e-49d2-9e01-41c179e789f5';
        IF action_wrapper_id IS NULL THEN
            action_wrapper_id = '213aadd4-4d7b-42e2-af6b-2591e3db0c2c';
            INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id)
            VALUES (action_wrapper_id, 'ACTION_PROFILE', '671a848a-eb4e-49d2-9e01-41c179e789f5') ON CONFLICT DO NOTHING;
        END IF;

    SELECT id INTO mapping_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE mapping_profile_id = 'f8d7e135-3c35-4c60-bb33-0a3cf01e7b94';
        IF mapping_wrapper_id IS NULL THEN
            mapping_wrapper_id = 'e4f143c6-1b7a-4ed9-b0e6-d0b8d5f5ad63';
            INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id)
            VALUES (mapping_wrapper_id, 'MAPPING_PROFILE', 'f8d7e135-3c35-4c60-bb33-0a3cf01e7b94') ON CONFLICT DO NOTHING;
        END IF;

    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id,
        detail_wrapper_id, master_profile_id, detail_profile_id,
        master_profile_type, detail_profile_type, detail_order, react_to) values
      ('7e1b00ad-eb12-4c27-aae7-3c4b39e97e3d', null, job_wrapper_id, action_wrapper_id,
       '90fd4389-e5a9-4cc5-88cf-1568c0ff7e8b', '671a848a-eb4e-49d2-9e01-41c179e789f5',
       'JOB_PROFILE', 'ACTION_PROFILE', 0, null
       ) ON CONFLICT DO NOTHING;

    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id,
        detail_wrapper_id, master_profile_id, detail_profile_id,
        master_profile_type, detail_profile_type, detail_order, react_to) values
      ('654f9356-8a7f-49fc-b6d2-91b08df15433', null, action_wrapper_id, mapping_wrapper_id,
       '671a848a-eb4e-49d2-9e01-41c179e789f5', 'f8d7e135-3c35-4c60-bb33-0a3cf01e7b94',
       'ACTION_PROFILE', 'MAPPING_PROFILE', 0, null
      ) ON CONFLICT DO NOTHING;
END
$$;

INSERT INTO ${myuniversity}_${mymodule}.job_to_action_profiles (id, jsonb) values
  ('7e1b00ad-eb12-4c27-aae7-3c4b39e97e3d',
   '{
     "id": "7e1b00ad-eb12-4c27-aae7-3c4b39e97e3d",
     "order": 0,
     "triggered": false,
     "detailWrapperId": "213aadd4-4d7b-42e2-af6b-2591e3db0c2c",
     "masterWrapperId": "b4b102e0-7fb4-4b77-97f1-782927839eb0",
     "detailProfileType": "ACTION_PROFILE",
     "masterProfileType": "JOB_PROFILE"
   }') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.action_to_mapping_profiles (id, jsonb) values
  ('654f9356-8a7f-49fc-b6d2-91b08df15433',
   '{
     "id": "654f9356-8a7f-49fc-b6d2-91b08df15433",
     "order": 0,
     "triggered": false,
     "detailWrapperId": "e4f143c6-1b7a-4ed9-b0e6-d0b8d5f5ad63",
     "masterWrapperId": "213aadd4-4d7b-42e2-af6b-2591e3db0c2c",
     "detailProfileType": "MAPPING_PROFILE",
     "masterProfileType": "ACTION_PROFILE"
   }') ON CONFLICT DO NOTHING;
