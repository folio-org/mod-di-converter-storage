UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = jsonb_insert(jsonb,'{mappingDetails, mappingFields, 44}',
  '{"name": "donorOrganizationIds", "enabled": "true", "required": false, "path": "order.poLine.donorOrganizationIds[]", "value": "", "subfields": []}')
WHERE jsonb -> 'mappingDetails' ->> 'recordType' = 'ORDER';
