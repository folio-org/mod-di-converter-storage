-- action_profiles:
-- change values: REPLACE and COMBINE to UPDATE
UPDATE ${myuniversity}_${mymodule}.action_profiles
SET jsonb = jsonb_set(jsonb, '{action}', '"UPDATE"')
WHERE jsonb ->> 'action' IN ('REPLACE', 'COMBINE');
