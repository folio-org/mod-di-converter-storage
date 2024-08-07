INSERT INTO ${myuniversity}_${mymodule}.job_profiles (id, jsonb) values
('6eefa4c6-bbf7-4845-ad82-de7fc5abd0e3', '{
  "id": "6eefa4c6-bbf7-4845-ad82-de7fc5abd0e3",
  "name": "Default - Create SRS MARC Authority",
  "description": "Load MARC Authority to create SRS MARC Authority",
  "dataType": "MARC",
  "tags": {
    "tagList": []
  },
  "childProfiles": [],
  "parentProfiles": [],
  "userInfo": {
    "firstName": "System",
    "lastName": "System",
    "userName": "System"
  },
  "metadata": {
    "createdDate": "2021-03-16T15:00:00.000",
    "updatedDate": "2021-03-16T15:00:00.000",
    "createdByUserId": "00000000-0000-0000-0000-000000000000",
    "updatedByUserId": "00000000-0000-0000-0000-000000000000"
  }
}') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.action_profiles (id, jsonb) values
  ('7915c72e-c6af-4962-969d-403c7238b051', '{
  "id": "7915c72e-c6af-4962-969d-403c7238b051",
  "name": "Default - Create Authorities",
  "action": "CREATE",
  "remove9Subfields": false,
  "description": "This action profile is used with FOLIO''s default job profile for creating Inventory Authorities and SRS MARC Authorities records. It can be edited, duplicated.",
  "folioRecord": "AUTHORITY",
  "childProfiles": [],
  "parentProfiles": [],
    "metadata": {
      "createdDate": "2021-10-08T14:00:00.000",
      "updatedDate": "2021-10-08T15:00:00.462+0000",
      "createdByUserId": "00000000-0000-0000-0000-000000000000",
      "updatedByUserId": "00000000-0000-0000-0000-000000000000"
    },
    "userInfo": {
      "lastName": "System",
      "userName": "System",
      "firstName": "System"
    }
}')
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.mapping_profiles (id, jsonb) values
  ('6a0ec1de-68eb-4833-bdbf-0741db25c314', '{
  "id": "6a0ec1de-68eb-4833-bdbf-0741db25c314",
  "name": "Default - Create authorities",
  "metadata": {
    "createdDate": "2021-10-08T14:00:00.000",
    "updatedDate": "2021-10-08T15:00:00.462+0000",
    "createdByUserId": "00000000-0000-0000-0000-000000000000",
    "updatedByUserId": "00000000-0000-0000-0000-000000000000"
  },
  "userInfo": {
    "lastName": "System",
    "userName": "System",
    "firstName": "System"
  },
  "description": "This field mapping profile is used with FOLIO''s default job profile for creating Inventory Authorities and SRS MARC Authorities records. It can be edited, duplicated, deleted, or linked to additional action profiles.",
  "childProfiles": [],
  "mappingDetails": {
    "name": "authority",
    "recordType": "AUTHORITY",
    "mappingFields": [
      {
        "name": "personalName",
        "path": "authority.personalName",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "sftPersonalName",
        "path": "authority.sftPersonalName[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "saftPersonalName",
        "path": "authority.saftPersonalName[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "corporateName",
        "path": "authority.corporateName",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "sftCorporateName",
        "path": "authority.sftCorporateName[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "saftCorporateName",
        "path": "authority.saftCorporateName[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "meetingName",
        "path": "authority.meetingName[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      },  {
        "name": "sftMeetingName",
        "path": "authority.sftMeetingName[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "saftMeetingName",
        "path": "authority.saftMeetingName[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "uniformTitle",
        "path": "authority.uniformTitle",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "sftUniformTitle",
        "path": "authority.sftUniformTitle[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "saftUniformTitle",
        "path": "authority.saftUniformTitle[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "topicalTerm",
        "path": "authority.topicalTerm",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "sftTopicalTerm",
        "path": "authority.sftTopicalTerm[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "saftTopicalTerm",
        "path": "authority.saftTopicalTerm[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "subjectHeadings",
        "path": "authority.subjectHeadings",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "geographicName",
        "path": "authority.geographicName",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "sftGeographicTerm",
        "path": "authority.sftGeographicTerm[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "saftGeographicTerm",
        "path": "authority.saftGeographicTerm[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "genre",
        "path": "authority.genre",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "identifiers",
        "path": "authority.identifiers[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "notes",
        "path": "authority.notes[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }
    ],
    "marcMappingDetails": []
  },
  "parentProfiles": [],
  "existingRecordType": "AUTHORITY",
  "incomingRecordType": "MARC_AUTHORITY",
  "marcFieldProtectionSettings": []
}')
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.job_to_action_profiles (id, jsonb) values
  ('06914a99-9b58-493d-828b-4ff104ba7e49', '{
  "id": "06914a99-9b58-493d-828b-4ff104ba7e49",
  "order": 0,
  "triggered": false,
  "detailProfileId": "7915c72e-c6af-4962-969d-403c7238b051",
  "masterProfileId": "6eefa4c6-bbf7-4845-ad82-de7fc5abd0e3",
  "detailProfileType": "ACTION_PROFILE",
  "masterProfileType": "JOB_PROFILE"
}')
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.action_to_mapping_profiles (id, jsonb) values
  ('329911fb-2835-476e-b85e-a7fcdc900f87', '{
  "id": "329911fb-2835-476e-b85e-a7fcdc900f87",
  "order": 0,
  "triggered": false,
  "detailProfileId": "6a0ec1de-68eb-4833-bdbf-0741db25c314",
  "masterProfileId": "7915c72e-c6af-4962-969d-403c7238b051",
  "detailProfileType": "MAPPING_PROFILE",
  "masterProfileType": "ACTION_PROFILE"
}')
ON CONFLICT DO NOTHING;

DO
$$
DECLARE
    job_wrapper_id UUID;
    action_wrapper_id UUID;
    mapping_wrapper_id UUID;
BEGIN
    SELECT id INTO job_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE job_profile_id = '6eefa4c6-bbf7-4845-ad82-de7fc5abd0e3';
    IF job_wrapper_id IS NULL THEN
        job_wrapper_id = '790f2fce-b2b7-4033-ac92-7b683d30bede';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id)
        VALUES (job_wrapper_id, 'JOB_PROFILE', '6eefa4c6-bbf7-4845-ad82-de7fc5abd0e3') ON CONFLICT DO NOTHING;
    END IF;

    SELECT id INTO action_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE action_profile_id = '7915c72e-c6af-4962-969d-403c7238b051';
    IF action_wrapper_id IS NULL THEN
        action_wrapper_id = '570476c2-2309-4a66-be93-eef158882f66';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id)
        VALUES (action_wrapper_id, 'ACTION_PROFILE', '7915c72e-c6af-4962-969d-403c7238b051') ON CONFLICT DO NOTHING;
    END IF;

    SELECT id INTO mapping_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE mapping_profile_id = '6a0ec1de-68eb-4833-bdbf-0741db25c314';
    IF mapping_wrapper_id IS NULL THEN
        mapping_wrapper_id = 'b5befbad-caac-4f05-8e94-6a292a1fbe17';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id)
        VALUES (mapping_wrapper_id, 'MAPPING_PROFILE', '6a0ec1de-68eb-4833-bdbf-0741db25c314') ON CONFLICT DO NOTHING;
    END IF;

    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id,
        detail_wrapper_id, master_profile_id, detail_profile_id,
        master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
        ('06914a99-9b58-493d-828b-4ff104ba7e49', NULL, job_wrapper_id, action_wrapper_id,
         '6eefa4c6-bbf7-4845-ad82-de7fc5abd0e3', '7915c72e-c6af-4962-969d-403c7238b051', 'JOB_PROFILE', 'ACTION_PROFILE', 0, NULL) ON CONFLICT DO NOTHING;
    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id,
            detail_wrapper_id, master_profile_id, detail_profile_id,
            master_profile_type, detail_profile_type, detail_order, react_to)
        VALUES
        ('329911fb-2835-476e-b85e-a7fcdc900f87', NULL, action_wrapper_id, mapping_wrapper_id,
         '7915c72e-c6af-4962-969d-403c7238b051', '6a0ec1de-68eb-4833-bdbf-0741db25c314', 'ACTION_PROFILE', 'MAPPING_PROFILE', 0, NULL) ON CONFLICT DO NOTHING;
END
$$;
