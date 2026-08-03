UPDATE ${myuniversity}_${mymodule}.job_profiles
SET jsonb = jsonb_set(
  jsonb_set(
    jsonb_set(jsonb, '{hidden}', 'false'::jsonb),
    '{name}', '"Default - Delete MARC Authority records"'::jsonb
  ),
  '{description}', '"Default job profile to delete MARC authority records. This job profile cannot be edited or deleted."'::jsonb
)
WHERE id = '1a338fcd-3efc-4a03-b007-394eeb0d5fb9';

UPDATE ${myuniversity}_${mymodule}.action_profiles
SET jsonb = jsonb_set(
  jsonb_set(
    jsonb_set(jsonb, '{hidden}', 'false'::jsonb),
    '{name}', '"Default - Delete MARC Authority records"'::jsonb
  ),
  '{description}', '"This action profile is used to delete MARC authority records. This action profile cannot be duplicated, edited, or deleted."'::jsonb
)
WHERE id = 'fabd9a3e-33c3-49b7-864d-c5af830d9990';

UPDATE ${myuniversity}_${mymodule}.match_profiles
SET jsonb = jsonb_set(
  jsonb_set(
    jsonb_set(jsonb, '{hidden}', 'false'::jsonb),
    '{name}', '"Default - Delete MARC Authority records"'::jsonb
  ),
  '{description}', '"This match profile is used to delete MARC authority records. The default field is set to 999 ff $s. This match profile cannot be deleted, but it can edited or duplicated."'::jsonb
)
WHERE id = '4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6';


-- Corrects detail_wrapper_id in the job-to-match association for default delete MARC authority job profile
-- defined in the default_delete_marc_authority_job_profile.sql
-- to have job-to-match relationship instead of job-to-action relationship.
UPDATE ${myuniversity}_${mymodule}.profile_associations
SET detail_wrapper_id = '69de98ea-68dd-46be-a187-a115f9afcc05'
WHERE id = '644e53c2-7be2-4ae5-bc17-131334222d39';
