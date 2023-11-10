-- Update wrappers ids at jsonb for action_to_match_profiles table
UPDATE ${myuniversity}_${mymodule}.action_to_match_profiles
SET jsonb = jsonb_set(jsonb_set(jsonb, '{masterWrapperId}', to_jsonb(masterwrapperid), true),'{detailWrapperId}', to_jsonb(detailWrapperId), true)
WHERE masterwrapperid IS NOT NULL AND detailwrapperid IS NOT NULL AND jsonb->>'masterWrapperId' IS NULL AND jsonb->>'detailWrapperId' IS NULL;

-- Update wrappers ids at jsonb for action_to_action_profiles table
UPDATE ${myuniversity}_${mymodule}.action_to_action_profiles
SET jsonb = jsonb_set(jsonb_set(jsonb, '{masterWrapperId}', to_jsonb(masterwrapperid), true),'{detailWrapperId}', to_jsonb(detailWrapperId), true)
WHERE masterwrapperid IS NOT NULL AND detailwrapperid IS NOT NULL AND jsonb->>'masterWrapperId' IS NULL AND jsonb->>'detailWrapperId' IS NULL;

-- Update wrappers ids at jsonb for action_to_mapping_profiles table
UPDATE ${myuniversity}_${mymodule}.action_to_mapping_profiles
SET jsonb = jsonb_set(jsonb_set(jsonb, '{masterWrapperId}', to_jsonb(masterwrapperid), true),'{detailWrapperId}', to_jsonb(detailWrapperId), true)
WHERE masterwrapperid IS NOT NULL AND detailwrapperid IS NOT NULL AND jsonb->>'masterWrapperId' IS NULL AND jsonb->>'detailWrapperId' IS NULL;

-- Update wrappers ids at jsonb for match_to_action_profiles table
UPDATE ${myuniversity}_${mymodule}.match_to_action_profiles
SET jsonb = jsonb_set(jsonb_set(jsonb, '{masterWrapperId}', to_jsonb(masterwrapperid), true),'{detailWrapperId}', to_jsonb(detailWrapperId), true)
WHERE masterwrapperid IS NOT NULL AND detailwrapperid IS NOT NULL AND jsonb->>'masterWrapperId' IS NULL AND jsonb->>'detailWrapperId' IS NULL;

-- Update wrappers ids at jsonb for match_to_match_profiles table
UPDATE ${myuniversity}_${mymodule}.match_to_match_profiles
SET jsonb = jsonb_set(jsonb_set(jsonb, '{masterWrapperId}', to_jsonb(masterwrapperid), true),'{detailWrapperId}', to_jsonb(detailWrapperId), true)
WHERE masterwrapperid IS NOT NULL AND detailwrapperid IS NOT NULL AND jsonb->>'masterWrapperId' IS NULL AND jsonb->>'detailWrapperId' IS NULL;

-- Update wrappers ids at jsonb for job_to_action_profiles table
UPDATE ${myuniversity}_${mymodule}.job_to_action_profiles
SET jsonb = jsonb_set(jsonb_set(jsonb, '{masterWrapperId}', to_jsonb(masterwrapperid), true),'{detailWrapperId}', to_jsonb(detailWrapperId), true)
WHERE masterwrapperid IS NOT NULL AND detailwrapperid IS NOT NULL AND jsonb->>'masterWrapperId' IS NULL AND jsonb->>'detailWrapperId' IS NULL;

-- Update wrappers ids at jsonb for job_to_match_profiles table
UPDATE ${myuniversity}_${mymodule}.job_to_match_profiles
SET jsonb = jsonb_set(jsonb_set(jsonb, '{masterWrapperId}', to_jsonb(masterwrapperid), true),'{detailWrapperId}', to_jsonb(detailWrapperId), true)
WHERE masterwrapperid IS NOT NULL AND detailwrapperid IS NOT NULL AND jsonb->>'masterWrapperId' IS NULL AND jsonb->>'detailWrapperId' IS NULL;
