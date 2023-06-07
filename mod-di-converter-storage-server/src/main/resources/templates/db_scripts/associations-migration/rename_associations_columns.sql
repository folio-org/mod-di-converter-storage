-- Rename action_to_action_profiles columns
ALTER TABLE ${myuniversity}_${mymodule}.action_to_action_profiles
  DROP COLUMN masterprofileid CASCADE,
  ADD COLUMN masterwrapperid uuid;

ALTER TABLE ${myuniversity}_${mymodule}.action_to_action_profiles
  DROP COLUMN detailprofileid CASCADE,
  ADD COLUMN detailwrapperid uuid;

-- Rename action_to_mapping_profiles columns
ALTER TABLE ${myuniversity}_${mymodule}.action_to_mapping_profiles
  DROP COLUMN masterprofileid CASCADE,
  ADD COLUMN masterwrapperid uuid;

ALTER TABLE ${myuniversity}_${mymodule}.action_to_mapping_profiles
  DROP COLUMN detailprofileid CASCADE,
  ADD COLUMN detailwrapperid uuid;

-- Rename action_to_match_profiles columns
ALTER TABLE ${myuniversity}_${mymodule}.action_to_match_profiles
  DROP COLUMN masterprofileid CASCADE,
  ADD COLUMN masterwrapperid uuid;

ALTER TABLE ${myuniversity}_${mymodule}.action_to_match_profiles
  DROP COLUMN detailprofileid CASCADE,
  ADD COLUMN detailwrapperid uuid;

-- Rename job_to_action_profiles columns
ALTER TABLE ${myuniversity}_${mymodule}.job_to_action_profiles
  DROP COLUMN masterprofileid CASCADE,
  ADD COLUMN masterwrapperid uuid;

ALTER TABLE ${myuniversity}_${mymodule}.job_to_action_profiles
  DROP COLUMN detailprofileid CASCADE,
  ADD COLUMN detailwrapperid uuid;

-- Rename job_to_match_profiles columns
ALTER TABLE ${myuniversity}_${mymodule}.job_to_match_profiles
  DROP COLUMN masterprofileid CASCADE,
  ADD COLUMN masterwrapperid uuid;

ALTER TABLE ${myuniversity}_${mymodule}.job_to_match_profiles
  DROP COLUMN detailprofileid CASCADE,
  ADD COLUMN detailwrapperid uuid;

-- Rename match_to_action_profiles columns
ALTER TABLE ${myuniversity}_${mymodule}.match_to_action_profiles
  DROP COLUMN masterprofileid CASCADE,
  ADD COLUMN masterwrapperid uuid;

ALTER TABLE ${myuniversity}_${mymodule}.match_to_action_profiles
  DROP COLUMN detailprofileid CASCADE,
  ADD COLUMN detailwrapperid uuid;

-- Rename match_to_match_profiles columns
ALTER TABLE ${myuniversity}_${mymodule}.match_to_match_profiles
  DROP COLUMN masterprofileid CASCADE,
  ADD COLUMN masterwrapperid uuid;

ALTER TABLE ${myuniversity}_${mymodule}.match_to_match_profiles
  DROP COLUMN detailprofileid CASCADE,
  ADD COLUMN detailwrapperid uuid;
