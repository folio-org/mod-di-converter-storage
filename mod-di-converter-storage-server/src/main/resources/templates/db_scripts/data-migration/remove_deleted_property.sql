UPDATE ${myuniversity}_${mymodule}.job_profiles
SET jsonb = jsonb - 'deleted'
WHERE jsonb ? 'deleted';

UPDATE ${myuniversity}_${mymodule}.match_profiles
SET jsonb = jsonb - 'deleted'
WHERE jsonb ? 'deleted';

UPDATE ${myuniversity}_${mymodule}.action_profiles
SET jsonb = jsonb - 'deleted'
WHERE jsonb ? 'deleted';

UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = jsonb - 'deleted'
WHERE jsonb ? 'deleted';
