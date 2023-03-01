UPDATE ${myuniversity}_${mymodule}.match_profiles
SET jsonb = jsonb_set(jsonb, '{childProfiles}', '[]')
WHERE created_by IN ('00000000-0000-0000-0000-000000000000', '6a010e5b-5421-5b1c-9b52-568b37038575');

UPDATE ${myuniversity}_${mymodule}.match_profiles
SET jsonb = jsonb_set(jsonb, '{parentProfiles}', '[]')
WHERE created_by IN ('00000000-0000-0000-0000-000000000000', '6a010e5b-5421-5b1c-9b52-568b37038575');

UPDATE ${myuniversity}_${mymodule}.action_profiles
SET jsonb = jsonb_set(jsonb, '{childProfiles}', '[]')
WHERE created_by IN ('00000000-0000-0000-0000-000000000000', '6a010e5b-5421-5b1c-9b52-568b37038575');

UPDATE ${myuniversity}_${mymodule}.action_profiles
SET jsonb = jsonb_set(jsonb, '{parentProfiles}', '[]')
WHERE created_by IN ('00000000-0000-0000-0000-000000000000', '6a010e5b-5421-5b1c-9b52-568b37038575');

UPDATE ${myuniversity}_${mymodule}.job_profiles
SET jsonb = jsonb_set(jsonb, '{childProfiles}', '[]')
WHERE created_by IN ('00000000-0000-0000-0000-000000000000', '6a010e5b-5421-5b1c-9b52-568b37038575');

UPDATE ${myuniversity}_${mymodule}.job_profiles
SET jsonb = jsonb_set(jsonb, '{parentProfiles}', '[]')
WHERE created_by IN ('00000000-0000-0000-0000-000000000000', '6a010e5b-5421-5b1c-9b52-568b37038575');

UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = jsonb_set(jsonb, '{childProfiles}', '[]')
WHERE created_by IN ('00000000-0000-0000-0000-000000000000', '6a010e5b-5421-5b1c-9b52-568b37038575');

UPDATE ${myuniversity}_${mymodule}.mapping_profiles
SET jsonb = jsonb_set(jsonb, '{parentProfiles}', '[]')
WHERE created_by IN ('00000000-0000-0000-0000-000000000000', '6a010e5b-5421-5b1c-9b52-568b37038575');
