DELETE FROM ${myuniversity}_${mymodule}.job_profiles
WHERE jsonb->>'deleted' = 'true';

DELETE FROM ${myuniversity}_${mymodule}.match_profiles
WHERE jsonb->>'deleted' = 'true';

DELETE FROM ${myuniversity}_${mymodule}.action_profiles
WHERE jsonb->>'deleted' = 'true';

DELETE FROM ${myuniversity}_${mymodule}.mapping_profiles
WHERE jsonb->>'deleted' = 'true';
