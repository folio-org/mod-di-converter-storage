INSERT INTO ${myuniversity}_${mymodule}.job_profiles (id, jsonb) values
('fa0262c7-5816-48d0-b9b3-7b7a862a5bc7', '{
  "id": "fa0262c7-5816-48d0-b9b3-7b7a862a5bc7",
  "name": "quickMARC Derive - Create Holdings and SRS MARC Holdings",
  "description": "Load MARC Holdings to create SRS MARC Holdings and Inventory Holdings",
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
}')
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.action_profiles (id, jsonb) values
('f5feddba-f892-4fad-b702-e4e77f04f9a3', '{
  "id": "f5feddba-f892-4fad-b702-e4e77f04f9a3",
  "name": "quickMARC Derive - Create Inventory Holdings",
  "action": "CREATE",
  "remove9Subfields": false,
  "description": "This action profile is used with FOLIO''s default job profile for creating Inventory Holdings and SRS MARC Holdings records. It cannot be edited or deleted.",
  "folioRecord": "HOLDINGS",
  "childProfiles": [],
  "parentProfiles": [],
    "metadata": {
      "createdDate": "2021-08-05T14:00:00.000",
      "updatedDate": "2021-08-05T15:00:00.462+0000",
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
('e0fbaad5-10c0-40d5-9228-498b351dbbaa', '{
  "id": "e0fbaad5-10c0-40d5-9228-498b351dbbaa",
  "name": "quickMARC Derive - Create Inventory Holdings",
  "metadata": {
    "createdDate": "2021-08-05T14:00:00.000",
    "updatedDate": "2021-08-05T15:00:00.462+0000",
    "createdByUserId": "00000000-0000-0000-0000-000000000000",
    "updatedByUserId": "00000000-0000-0000-0000-000000000000"
  },
  "userInfo": {
    "lastName": "System",
    "userName": "System",
    "firstName": "System"
  },
  "description": "This field mapping profile is used with FOLIO''s default job profile for creating Inventory Holdings and SRS MARC Holdings records. It cannot be edited or deleted.",
  "childProfiles": [],
  "mappingDetails": {
    "name": "holdings",
    "recordType": "HOLDINGS",
    "mappingFields": [
      {
        "name": "hrid",
        "path": "holdings.hrid",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "holdingsTypeId",
        "path": "holdings.holdingsTypeId",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "discoverySuppress",
        "path": "holdings.discoverySuppress",
        "value": "",
        "enabled": "true",
        "subfields": []
      }, {
        "name": "formerIds",
        "path": "holdings.formerIds",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "instanceId",
        "path": "holdings.instanceId",
        "value": "",
        "enabled": "true",
        "subfields": []
      }, {
        "name": "permanentLocationId",
        "path": "holdings.permanentLocationId",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "temporaryLocationId",
        "path": "holdings.temporaryLocationId",
        "value": "",
        "enabled": "true",
        "subfields": []
      }, {
        "name": "effectiveLocationId",
        "path": "holdings.effectiveLocationId",
        "value": "",
        "enabled": "true",
        "subfields": []
      }, {
        "name": "callNumberTypeId",
        "path": "holdings.callNumberTypeId",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "callNumberPrefix",
        "path": "holdings.callNumberPrefix",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "callNumber",
        "path": "holdings.callNumber",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "callNumberSuffix",
        "path": "holdings.callNumberSuffix",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "shelvingTitle",
        "path": "holdings.shelvingTitle",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "acquisitionFormat",
        "path": "holdings.acquisitionFormat",
        "value": "",
        "enabled": "true",
        "subfields": []
      }, {
        "name": "acquisitionMethod",
        "path": "holdings.acquisitionMethod",
        "value": "",
        "enabled": "true",
        "subfields": []
      }, {
        "name": "receiptStatus",
        "path": "holdings.receiptStatus",
        "value": "",
        "enabled": "true",
        "subfields": []
      }, {
        "name": "illPolicyId",
        "path": "holdings.illPolicyId",
        "value": "",
        "enabled": "true",
        "subfields": []
      }, {
        "name": "retentionPolicy",
        "path": "holdings.retentionPolicy",
        "value": "",
        "enabled": "true",
        "subfields": []
      }, {
        "name": "digitizationPolicy",
        "path": "holdings.digitizationPolicy",
        "value": "",
        "enabled": "true",
        "subfields": []
      }, {
        "name": "copyNumber",
        "path": "holdings.copyNumber",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "numberOfItems",
        "path": "holdings.numberOfItems",
        "value": "",
        "enabled": "true",
        "subfields": []
      }, {
        "name": "electronicAccess",
        "path": "holdings.electronicAccess[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "notes",
        "path": "holdings.notes[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "holdingsStatements",
        "path": "holdings.holdingsStatements[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "holdingsStatementsForIndexes",
        "path": "holdings.holdingsStatementsForIndexes[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "holdingsStatementsForSupplements",
        "path": "holdings.holdingsStatementsForSupplements[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "receivingHistory",
        "path": "holdings.receivingHistory",
        "value": "",
        "enabled": "true",
        "subfields": []
      }, {
        "name": "statisticalCodeIds",
        "path": "holdings.statisticalCodeIds[]",
        "value": "",
        "enabled": "true",
        "subfields": []
      }, {
        "name": "holdingsItems",
        "path": "holdings.holdingsItems[]",
        "value": "",
        "enabled": "true",
        "subfields": []
      }, {
        "name": "bareHoldingsItems",
        "path": "holdings.bareHoldingsItems[]",
        "value": "",
        "enabled": "true",
        "subfields": []
      }, {
        "name": "holdingsInstance",
        "path": "holdings.holdingsInstance",
        "value": "",
        "enabled": "true",
        "subfields": []
      }, {
        "name": "sourceId",
        "path": "holdings.sourceId",
        "value": "",
        "enabled": "true",
        "subfields": []
      }
    ],
    "marcMappingDetails": []
  },
  "parentProfiles": [],
  "existingRecordType": "HOLDINGS",
  "incomingRecordType": "MARC_HOLDINGS",
  "marcFieldProtectionSettings": []
}')
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.job_to_action_profiles (id, jsonb) values
('adbe1e5c-7796-4902-b18e-794b1d58caac', '{
  "id": "adbe1e5c-7796-4902-b18e-794b1d58caac",
  "order": 0,
  "triggered": false,
  "detailProfileId": "f5feddba-f892-4fad-b702-e4e77f04f9a3",
  "masterProfileId": "fa0262c7-5816-48d0-b9b3-7b7a862a5bc7",
  "detailProfileType": "ACTION_PROFILE",
  "masterProfileType": "JOB_PROFILE"
}')
ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.action_to_mapping_profiles (id, jsonb) values
('3c73fa82-97bb-4960-aa6b-e4c8f230bcdc', '{
  "id": "3c73fa82-97bb-4960-aa6b-e4c8f230bcdc",
  "order": 0,
  "triggered": false,
  "detailProfileId": "e0fbaad5-10c0-40d5-9228-498b351dbbaa",
  "masterProfileId": "f5feddba-f892-4fad-b702-e4e77f04f9a3",
  "detailProfileType": "MAPPING_PROFILE",
  "masterProfileType": "ACTION_PROFILE"
}')
ON CONFLICT DO NOTHING;

DO $$
DECLARE
    job_wrapper_id UUID;
    action_wrapper_id UUID;
    mapping_wrapper_id UUID := '4adf6d51-fde9-4b0d-97b2-09ff99576861';
BEGIN
  SELECT id INTO job_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE job_profile_id = 'fa0262c7-5816-48d0-b9b3-7b7a862a5bc7';
  IF job_wrapper_id IS NULL THEN
    job_wrapper_id = '5d4e95e0-f296-431f-b11f-0b9869e077fa';
    INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id) values
      (job_wrapper_id, 'JOB_PROFILE', 'fa0262c7-5816-48d0-b9b3-7b7a862a5bc7');
  END IF;

  SELECT id INTO action_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE action_profile_id = 'f5feddba-f892-4fad-b702-e4e77f04f9a3';
  IF action_wrapper_id IS NULL THEN
    action_wrapper_id = '4bf02d3a-2dcb-4a75-adb6-8f6538f3fd4d';
    INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id) values
      (action_wrapper_id, 'ACTION_PROFILE', 'f5feddba-f892-4fad-b702-e4e77f04f9a3');
  END IF;

  SELECT id INTO mapping_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE mapping_profile_id = 'e0fbaad5-10c0-40d5-9228-498b351dbbaa';
  IF mapping_wrapper_id IS NULL THEN
    mapping_wrapper_id = '4adf6d51-fde9-4b0d-97b2-09ff99576861';
    INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id) values
      (mapping_wrapper_id, 'MAPPING_PROFILE', 'e0fbaad5-10c0-40d5-9228-498b351dbbaa');
  END IF;

  INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id,
        detail_wrapper_id, master_profile_id, detail_profile_id,
        master_profile_type, detail_profile_type, detail_order, react_to)
  VALUES
      ('adbe1e5c-7796-4902-b18e-794b1d58caac', null, job_wrapper_id, action_wrapper_id,
       'fa0262c7-5816-48d0-b9b3-7b7a862a5bc7', 'f5feddba-f892-4fad-b702-e4e77f04f9a3',
       'JOB_PROFILE', 'ACTION_PROFILE', 0, null) ON CONFLICT DO NOTHING;
  INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id,
          detail_wrapper_id, master_profile_id, detail_profile_id,
          master_profile_type, detail_profile_type, detail_order, react_to)
  VALUES
      ('3c73fa82-97bb-4960-aa6b-e4c8f230bcdc', null, action_wrapper_id, mapping_wrapper_id,
       'f5feddba-f892-4fad-b702-e4e77f04f9a3', 'e0fbaad5-10c0-40d5-9228-498b351dbbaa',
       'ACTION_PROFILE', 'MAPPING_PROFILE', 0, null) ON CONFLICT DO NOTHING;
END $$;
