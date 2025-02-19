UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = jsonb_insert(jsonb, '{mappingDetails, mappingFields, 44}', '{"name": "deleted", "enabled": "true", "path": "instance.deleted", "booleanFieldAction": "ALL_FALSE", "subfields": []}')
WHERE jsonb -> 'mappingDetails' ->> 'recordType' = 'INSTANCE';
