-- action_profiles:
-- set default values: remove9Subfields to true for instances/bibs with no value set, false - for others (with no value set)
UPDATE ${myuniversity}_${mymodule}.action_profiles
SET jsonb = CASE
                WHEN jsonb ->> 'folioRecord' IN ('INSTANCE', 'MARC_BIBLIOGRAPHIC') THEN jsonb_set(jsonb, '{remove9Subfields}', 'true')
                WHEN jsonb ->> 'folioRecord' NOT IN ('INSTANCE', 'MARC_BIBLIOGRAPHIC') THEN jsonb_set(jsonb, '{remove9Subfields}', 'false')
                ELSE jsonb
            END
WHERE jsonb ->> 'remove9Subfields' IS NULL;
