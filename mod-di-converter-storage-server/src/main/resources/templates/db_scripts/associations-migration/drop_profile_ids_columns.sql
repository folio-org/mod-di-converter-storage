ALTER TABLE ${myuniversity}_${mymodule}.job_to_match_profiles
  DROP COLUMN IF EXISTS masterprofileid CASCADE,
  DROP COLUMN IF EXISTS detailprofileid CASCADE;

ALTER TABLE ${myuniversity}_${mymodule}.job_to_action_profiles
  DROP COLUMN IF EXISTS masterprofileid CASCADE,
  DROP COLUMN IF EXISTS detailprofileid CASCADE;

ALTER TABLE ${myuniversity}_${mymodule}.match_to_action_profiles
  DROP COLUMN IF EXISTS masterprofileid CASCADE,
  DROP COLUMN IF EXISTS detailprofileid CASCADE;

ALTER TABLE ${myuniversity}_${mymodule}.match_to_match_profiles
  DROP COLUMN IF EXISTS masterprofileid CASCADE,
  DROP COLUMN IF EXISTS detailprofileid CASCADE;

ALTER TABLE ${myuniversity}_${mymodule}.action_to_match_profiles
  DROP COLUMN IF EXISTS masterprofileid CASCADE,
  DROP COLUMN IF EXISTS detailprofileid CASCADE;

ALTER TABLE ${myuniversity}_${mymodule}.action_to_action_profiles
  DROP COLUMN IF EXISTS masterprofileid CASCADE,
  DROP COLUMN IF EXISTS detailprofileid CASCADE;

ALTER TABLE ${myuniversity}_${mymodule}.action_to_mapping_profiles
  DROP COLUMN IF EXISTS masterprofileid CASCADE,
  DROP COLUMN IF EXISTS detailprofileid CASCADE;
