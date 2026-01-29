INSERT INTO ${myuniversity}_${mymodule}.job_profiles (id, jsonb) values
('80898dee-449f-44dd-9c8e-37d5eb469b1d', '{
  "id": "80898dee-449f-44dd-9c8e-37d5eb469b1d",
  "name": "Default - Create Holdings and SRS MARC Holdings",
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
('8aa0b850-9182-4005-8435-340b704b2a19', '{
  "id": "8aa0b850-9182-4005-8435-340b704b2a19",
  "name": "Default - Create Holdings",
  "action": "CREATE",
  "remove9Subfields": false,
  "description": "This action profile is used with FOLIO''s default job profile for creating Inventory Holdings and SRS MARC Holdings records. It can be edited, duplicated.",
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
('13cf7adf-c7a7-4c2e-838f-14d0ac36ec0a', '{
  "id": "13cf7adf-c7a7-4c2e-838f-14d0ac36ec0a",
  "name": "Default - Create holdings",
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
  "description": "This field mapping profile is used with FOLIO''s default job profile for creating Inventory Holdings and SRS MARC Holdings records. It can be edited, duplicated, deleted, or linked to additional action profiles.",
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
        "enabled": "false",
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
        "enabled": "false",
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
        "enabled": "false",
        "subfields": []
      }, {
        "name": "effectiveLocationId",
        "path": "holdings.effectiveLocationId",
        "value": "",
        "enabled": "false",
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
        "enabled": "false",
        "subfields": []
      }, {
        "name": "acquisitionMethod",
        "path": "holdings.acquisitionMethod",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "receiptStatus",
        "path": "holdings.receiptStatus",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "illPolicyId",
        "path": "holdings.illPolicyId",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "retentionPolicy",
        "path": "holdings.retentionPolicy",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "digitizationPolicy",
        "path": "holdings.digitizationPolicy",
        "value": "",
        "enabled": "false",
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
        "enabled": "false",
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
        "enabled": "false",
        "subfields": []
      }, {
        "name": "statisticalCodeIds",
        "path": "holdings.statisticalCodeIds[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "holdingsItems",
        "path": "holdings.holdingsItems[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "bareHoldingsItems",
        "path": "holdings.bareHoldingsItems[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "holdingsInstance",
        "path": "holdings.holdingsInstance",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "sourceId",
        "path": "holdings.sourceId",
        "value": "",
        "enabled": "false",
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

DO
$$
DECLARE
    job_wrapper_id UUID;
    action_wrapper_id UUID;
    mapping_wrapper_id UUID;
BEGIN
    SELECT id INTO job_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE job_profile_id = '80898dee-449f-44dd-9c8e-37d5eb469b1d';
    IF job_wrapper_id IS NULL THEN
        job_wrapper_id = 'd7ea2be8-7576-401e-9644-2cb941b090f2';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id)
        VALUES (job_wrapper_id, 'JOB_PROFILE', '80898dee-449f-44dd-9c8e-37d5eb469b1d') ON CONFLICT DO NOTHING;
    END IF;

    SELECT id INTO action_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE action_profile_id = '8aa0b850-9182-4005-8435-340b704b2a19';
    IF action_wrapper_id IS NULL THEN
        action_wrapper_id = '68c54cdb-575b-4e06-b196-85f0b9c1d483';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id)
        VALUES (action_wrapper_id, 'ACTION_PROFILE', '8aa0b850-9182-4005-8435-340b704b2a19') ON CONFLICT DO NOTHING;
    END IF;

    SELECT id INTO mapping_wrapper_id FROM ${myuniversity}_${mymodule}.profile_wrappers WHERE mapping_profile_id = '13cf7adf-c7a7-4c2e-838f-14d0ac36ec0a';
    IF mapping_wrapper_id IS NULL THEN
        mapping_wrapper_id = 'e2bda8d0-1c47-4a6f-a440-ae97b2aaf468';
        INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id)
        VALUES (mapping_wrapper_id, 'MAPPING_PROFILE', '13cf7adf-c7a7-4c2e-838f-14d0ac36ec0a') ON CONFLICT DO NOTHING;
    END IF;

    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id,
        detail_wrapper_id, master_profile_id, detail_profile_id,
        master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
        ('866a90ce-53b2-4b7b-afb5-d3a564e5087e', NULL, job_wrapper_id, action_wrapper_id,
         '80898dee-449f-44dd-9c8e-37d5eb469b1d', '8aa0b850-9182-4005-8435-340b704b2a19', 'JOB_PROFILE', 'ACTION_PROFILE', 0, NULL) ON CONFLICT DO NOTHING;
    INSERT INTO ${myuniversity}_${mymodule}.profile_associations (id, job_profile_id, master_wrapper_id,
            detail_wrapper_id, master_profile_id, detail_profile_id,
            master_profile_type, detail_profile_type, detail_order, react_to)
    VALUES
        ('42f66e86-cacb-479d-aa80-50a200d0b6b6', NULL, action_wrapper_id, mapping_wrapper_id,
         '8aa0b850-9182-4005-8435-340b704b2a19', '13cf7adf-c7a7-4c2e-838f-14d0ac36ec0a', 'ACTION_PROFILE', 'MAPPING_PROFILE', 0, NULL) ON CONFLICT DO NOTHING;
END
$$;
