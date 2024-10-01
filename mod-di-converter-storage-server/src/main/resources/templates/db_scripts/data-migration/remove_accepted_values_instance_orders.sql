-- Create function that removes jsonb property recursive
CREATE OR REPLACE FUNCTION remove_key_recursive(data jsonb, target_key text)
RETURNS jsonb LANGUAGE plpgsql AS $$
BEGIN
    IF jsonb_typeof(data) = 'object' THEN
        RETURN (
            SELECT jsonb_object_agg(k, remove_key_recursive(v, target_key))
            FROM jsonb_each(data) AS t(k, v)
            WHERE k <> target_key
        );
    ELSIF jsonb_typeof(data) = 'array' THEN
        RETURN (
            SELECT jsonb_agg(remove_key_recursive(elem, target_key))
            FROM jsonb_array_elements(data) AS elem
        );
    ELSE
        RETURN data;
    END IF;
END;
$$;

-- Remove 'acceptedValues' property from 'INSTANCE', 'HOLDINGS', 'ITEM' and 'ORDER' mapping profile
UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = remove_key_recursive(jsonb, 'acceptedValues')
WHERE jsonb -> 'mappingDetails' ->> 'recordType' IN ('INSTANCE', 'HOLDINGS', 'ITEM', 'ORDER');

-- Delete function
DROP FUNCTION IF EXISTS remove_key_recursive CASCADE;
