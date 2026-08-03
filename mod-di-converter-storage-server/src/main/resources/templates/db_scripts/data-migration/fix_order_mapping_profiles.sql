-- removes redundant mapping for "donorOrganizationIds" field that might have been added
-- due to the repeated execution of the corresponding script for adding this mapping
WITH profiles_ids AS (
  SELECT id
  FROM ${myuniversity}_${mymodule}.mapping_profiles
  WHERE jsonb -> 'mappingDetails' ->> 'recordType' = 'ORDER'
    AND jsonb_array_length(jsonb_path_query_array(jsonb, '$.mappingDetails.mappingFields[*] ? (@.name == "donorOrganizationIds")')) > 1
)
UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = jsonb_set(jsonb, '{mappingDetails, mappingFields}', (jsonb -> 'mappingDetails' -> 'mappingFields') - 44)
WHERE id IN (SELECT id FROM profiles_ids);
