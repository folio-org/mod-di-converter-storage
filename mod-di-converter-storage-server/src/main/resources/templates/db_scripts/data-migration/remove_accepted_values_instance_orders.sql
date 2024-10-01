UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = jsonb_set(
    jsonb,
    '{mappingDetails, mappingFields}',
    (
        SELECT jsonb_agg(
            CASE
                WHEN mappingField ? 'acceptedValues' THEN mappingField - 'acceptedValues'
                ELSE mappingField
            END
        )
        FROM jsonb_array_elements(jsonb->'mappingDetails'->'mappingFields') AS mappingField
    )
)
WHERE jsonb -> 'mappingDetails' -> 'mappingFields' @> '[{"acceptedValues": {}}]' AND jsonb -> 'mappingDetails' ->> 'recordType' IN ('INSTANCE', 'HOLDINGS', 'ITEM', 'ORDER');
