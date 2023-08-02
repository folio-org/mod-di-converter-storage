-- Update masterwrapperid/detailwrapperid via wrappers ids inside jsonb column
ALTER TABLE  ${myuniversity}_${mymodule}.job_to_action_profiles
DISABLE TRIGGER update_job_to_action_profiles_references;

UPDATE ${myuniversity}_${mymodule}.job_to_action_profiles JOB
SET masterwrapperid = WR.id
FROM ${myuniversity}_${mymodule}.profile_wrappers WR
WHERE JOB.jsonb ->> 'masterProfileId'::text= WR.job_profile_id::text
AND WR.job_profile_id IS NOT NULL;

UPDATE ${myuniversity}_${mymodule}.job_to_action_profiles JOB
SET detailwrapperid = WR.id
FROM ${myuniversity}_${mymodule}.profile_wrappers WR
WHERE JOB.jsonb ->> 'detailProfileId'::text= WR.action_profile_id::text
AND WR.action_profile_id IS NOT NULL;

ALTER TABLE  ${myuniversity}_${mymodule}.job_to_action_profiles
ENABLE TRIGGER update_job_to_action_profiles_references;


ALTER TABLE  ${myuniversity}_${mymodule}.job_to_match_profiles
DISABLE TRIGGER update_job_to_match_profiles_references;

UPDATE ${myuniversity}_${mymodule}.job_to_match_profiles JOB
SET masterwrapperid = WR.id
FROM ${myuniversity}_${mymodule}.profile_wrappers WR
WHERE JOB.jsonb ->> 'masterProfileId'::text= WR.job_profile_id::text
AND WR.job_profile_id IS NOT NULL;

UPDATE ${myuniversity}_${mymodule}.job_to_match_profiles JOB
SET detailwrapperid = WR.id
FROM ${myuniversity}_${mymodule}.profile_wrappers WR
WHERE JOB.jsonb ->> 'detailProfileId'::text= WR.match_profile_id::text
AND WR.match_profile_id IS NOT NULL;

ALTER TABLE  ${myuniversity}_${mymodule}.job_to_match_profiles
ENABLE TRIGGER update_job_to_match_profiles_references;


ALTER TABLE  ${myuniversity}_${mymodule}.action_to_action_profiles
DISABLE TRIGGER update_action_to_action_profiles_references;

UPDATE ${myuniversity}_${mymodule}.action_to_action_profiles JOB
SET masterwrapperid = WR.id
FROM ${myuniversity}_${mymodule}.profile_wrappers WR
WHERE JOB.jsonb ->> 'masterProfileId'::text= WR.action_profile_id::text
AND WR.action_profile_id IS NOT NULL;

UPDATE ${myuniversity}_${mymodule}.action_to_action_profiles JOB
SET detailwrapperid = WR.id
FROM ${myuniversity}_${mymodule}.profile_wrappers WR
WHERE JOB.jsonb ->> 'detailProfileId'::text= WR.action_profile_id::text
AND WR.action_profile_id IS NOT NULL;

ALTER TABLE  ${myuniversity}_${mymodule}.action_to_action_profiles
ENABLE TRIGGER update_action_to_action_profiles_references;


ALTER TABLE  ${myuniversity}_${mymodule}.action_to_mapping_profiles
DISABLE TRIGGER update_action_to_mapping_profiles_references;

UPDATE ${myuniversity}_${mymodule}.action_to_mapping_profiles JOB
SET masterwrapperid = WR.id
FROM ${myuniversity}_${mymodule}.profile_wrappers WR
WHERE JOB.jsonb ->> 'masterProfileId'::text= WR.action_profile_id::text
AND WR.action_profile_id IS NOT NULL;

UPDATE ${myuniversity}_${mymodule}.action_to_mapping_profiles JOB
SET detailwrapperid = WR.id
FROM ${myuniversity}_${mymodule}.profile_wrappers WR
WHERE JOB.jsonb ->> 'detailProfileId'::text= WR.mapping_profile_id::text
AND WR.mapping_profile_id IS NOT NULL;

ALTER TABLE  ${myuniversity}_${mymodule}.action_to_mapping_profiles
ENABLE TRIGGER update_action_to_mapping_profiles_references;


ALTER TABLE  ${myuniversity}_${mymodule}.match_to_action_profiles
DISABLE TRIGGER update_match_to_action_profiles_references;

UPDATE ${myuniversity}_${mymodule}.match_to_action_profiles JOB
SET masterwrapperid = WR.id
FROM ${myuniversity}_${mymodule}.profile_wrappers WR
WHERE JOB.jsonb ->> 'masterProfileId'::text= WR.match_profile_id::text
AND WR.match_profile_id IS NOT NULL;

UPDATE ${myuniversity}_${mymodule}.match_to_action_profiles JOB
SET detailwrapperid = WR.id
FROM ${myuniversity}_${mymodule}.profile_wrappers WR
WHERE JOB.jsonb ->> 'detailProfileId'::text= WR.action_profile_id::text
AND WR.action_profile_id IS NOT NULL;

ALTER TABLE  ${myuniversity}_${mymodule}.match_to_action_profiles
ENABLE TRIGGER update_match_to_action_profiles_references;


ALTER TABLE  ${myuniversity}_${mymodule}.match_to_match_profiles
DISABLE TRIGGER update_match_to_match_profiles_references;

UPDATE ${myuniversity}_${mymodule}.match_to_match_profiles JOB
SET masterwrapperid = WR.id
FROM ${myuniversity}_${mymodule}.profile_wrappers WR
WHERE JOB.jsonb ->> 'masterProfileId'::text= WR.match_profile_id::text
AND WR.match_profile_id IS NOT NULL;

UPDATE ${myuniversity}_${mymodule}.match_to_match_profiles JOB
SET detailwrapperid = WR.id
FROM ${myuniversity}_${mymodule}.profile_wrappers WR
WHERE JOB.jsonb ->> 'detailProfileId'::text= WR.match_profile_id::text
AND WR.match_profile_id IS NOT NULL;

ALTER TABLE  ${myuniversity}_${mymodule}.match_to_match_profiles
ENABLE TRIGGER update_match_to_match_profiles_references;
