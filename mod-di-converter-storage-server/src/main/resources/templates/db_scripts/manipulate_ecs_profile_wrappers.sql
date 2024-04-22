UPDATE ${myuniversity}_${mymodule}.action_to_mapping_profiles
SET "jsonb"  = "jsonb"
WHERE id = '654f9356-8a7f-49fc-b6d2-91b08df15433';

UPDATE ${myuniversity}_${mymodule}.job_to_action_profiles
SET "jsonb" = "jsonb"
WHERE id = '7e1b00ad-eb12-4c27-aae7-3c4b39e97e3d';

DELETE FROM ${myuniversity}_${mymodule}.profile_wrappers dpf
WHERE dpf.id IN (
SELECT pf.id
FROM ${myuniversity}_${mymodule}.profile_wrappers pf
    WHERE pf.profile_type IN
    ('JOB_PROFILE','ACTION_PROFILE','MAPPING_PROFILE') AND
    (pf.job_profile_id = '90fd4389-e5a9-4cc5-88cf-1568c0ff7e8b'
    OR pf.action_profile_id = '671a848a-eb4e-49d2-9e01-41c179e789f5' OR pf.mapping_profile_id = 'f8d7e135-3c35-4c60-bb33-0a3cf01e7b94')
    AND
    NOT EXISTS (SELECT id, jsonb, creation_date, created_by, masterwrapperid, detailwrapperid
	FROM ${myuniversity}_${mymodule}.job_to_action_profiles nested_jtap
	WHERE pf.id = nested_jtap.masterwrapperid
	OR
	pf.id = nested_jtap.detailwrapperid)
	AND NOT EXISTS (SELECT id, jsonb, creation_date, created_by, masterwrapperid, detailwrapperid
	FROM ${myuniversity}_${mymodule}.action_to_mapping_profiles nested_atmp
    WHERE pf.id = nested_atmp.masterwrapperid
    OR
    pf.id = nested_atmp.detailwrapperid)
);
