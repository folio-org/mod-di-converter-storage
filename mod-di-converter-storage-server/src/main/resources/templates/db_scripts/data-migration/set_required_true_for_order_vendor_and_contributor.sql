UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = jsonb_set(
  jsonb_set(
    jsonb,
    '{mappingDetails, mappingFields, 45, subfields, 0, fields, 0, required}',
    'true'::jsonb
  ),
  '{mappingDetails, mappingFields, 45, subfields, 0, fields, 1, required}',
  'true'::jsonb
            )
WHERE jsonb -> 'mappingDetails' ->> 'recordType' = 'ORDER';

UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = jsonb_set(
  jsonb_set(
    jsonb,
    '{mappingDetails, mappingFields, 25, subfields, 0, fields, 0, required}',
    'true'::jsonb
  ),
  '{mappingDetails, mappingFields, 25, subfields, 0, fields, 1, required}',
  'true'::jsonb
            )
WHERE jsonb -> 'mappingDetails' ->> 'recordType' = 'ORDER';
