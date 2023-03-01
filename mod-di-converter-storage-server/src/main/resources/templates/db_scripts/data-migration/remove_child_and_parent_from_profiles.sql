UPDATE ${myuniversity}_${mymodule}.match_profiles
SET jsonb = jsonb_set(jsonb, '{childProfiles}', '[]');

UPDATE ${myuniversity}_${mymodule}.match_profiles
SET jsonb = jsonb_set(jsonb, '{parentProfiles}', '[]');

UPDATE ${myuniversity}_${mymodule}.action_profiles
SET jsonb = jsonb_set(jsonb, '{childProfiles}', '[]');

UPDATE ${myuniversity}_${mymodule}.action_profiles
SET jsonb = jsonb_set(jsonb, '{parentProfiles}', '[]');

UPDATE ${myuniversity}_${mymodule}.job_profiles
SET jsonb = jsonb_set(jsonb, '{childProfiles}', '[]');

UPDATE ${myuniversity}_${mymodule}.job_profiles
SET jsonb = jsonb_set(jsonb, '{parentProfiles}', '[]');

UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = jsonb_set(jsonb, '{childProfiles}', '[]');

UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = jsonb_set(jsonb, '{parentProfiles}', '[]');
