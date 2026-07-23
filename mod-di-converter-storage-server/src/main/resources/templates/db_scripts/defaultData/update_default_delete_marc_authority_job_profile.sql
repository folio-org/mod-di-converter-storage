UPDATE ${myuniversity}_${mymodule}.job_profiles
SET jsonb = jsonb_set(
  jsonb_set(
    jsonb_set(jsonb, '{hidden}', 'false'::jsonb),
    '{name}', 'Default - Delete MARC Authority records'::jsonb
  ),
  '{description}', 'Default job profile to delete MARC authority records. This job profile cannot be edited or deleted.'::jsonb
)
WHERE id = '1a338fcd-3efc-4a03-b007-394eeb0d5fb9';

UPDATE ${myuniversity}_${mymodule}.action_profiles
SET jsonb = jsonb_set(
  jsonb_set(
    jsonb_set(jsonb, '{hidden}', 'false'::jsonb),
    '{name}', 'Default - Delete MARC Authority records'::jsonb
  ),
  '{description}', 'This action profile is used to delete MARC authority records. This action profile cannot be duplicated, edited, or deleted.'::jsonb
)
WHERE id = 'fabd9a3e-33c3-49b7-864d-c5af830d9990';

UPDATE ${myuniversity}_${mymodule}.match_profiles
SET jsonb = jsonb_set(
  jsonb_set(
    jsonb_set(jsonb, '{hidden}', 'false'::jsonb),
    '{name}', 'Default - Delete MARC Authority records'::jsonb
  ),
  '{description}', 'This match profile is used to delete MARC authority records. The default field is set to 999 ff $s. This match profile cannot be deleted, but it can edited or duplicated.'::jsonb
)
WHERE id = '4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6';
