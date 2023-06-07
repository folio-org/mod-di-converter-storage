-- Recreate action_to_action_profiles foreign key constraint
ALTER TABLE ${myuniversity}_${mymodule}.action_to_action_profiles
  ADD CONSTRAINT masterwrapperid_action_profiles_fkey
    FOREIGN KEY (masterwrapperid) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id);

ALTER TABLE ${myuniversity}_${mymodule}.action_to_action_profiles
  ADD CONSTRAINT detailwrapperid_action_profiles_fkey
    FOREIGN KEY (detailwrapperid) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id);

-- Recreate action_to_mapping_profiles foreign key constraint
ALTER TABLE ${myuniversity}_${mymodule}.action_to_mapping_profiles
  ADD CONSTRAINT masterwrapperid_action_profiles_fkey
    FOREIGN KEY (masterwrapperid) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id);

ALTER TABLE ${myuniversity}_${mymodule}.action_to_mapping_profiles
  ADD CONSTRAINT detailwrapperid_mapping_profiles_fkey
    FOREIGN KEY (detailwrapperid) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id);

-- Recreate action_to_match_profiles foreign key constraint
ALTER TABLE ${myuniversity}_${mymodule}.action_to_match_profiles
  ADD CONSTRAINT masterwrapperid_action_profiles_fkey
    FOREIGN KEY (masterwrapperid) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id);

ALTER TABLE ${myuniversity}_${mymodule}.action_to_match_profiles
  ADD CONSTRAINT detailwrapperid_match_profiles_fkey
    FOREIGN KEY (detailwrapperid) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id);

-- Recreate job_to_action_profiles foreign key constraint
ALTER TABLE ${myuniversity}_${mymodule}.job_to_action_profiles
  ADD CONSTRAINT masterwrapperid_job_profiles_fkey
    FOREIGN KEY (masterwrapperid) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id);

ALTER TABLE ${myuniversity}_${mymodule}.job_to_action_profiles
  ADD CONSTRAINT detailwrapperid_action_profiles_fkey
    FOREIGN KEY (detailwrapperid) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id);

-- Recreate job_to_match_profiles foreign key constraint
ALTER TABLE ${myuniversity}_${mymodule}.job_to_match_profiles
  ADD CONSTRAINT masterwrapperid_job_profiles_fkey
    FOREIGN KEY (masterwrapperid) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id);

ALTER TABLE ${myuniversity}_${mymodule}.job_to_match_profiles
  ADD CONSTRAINT detailwrapperid_match_profiles_fkey
    FOREIGN KEY (detailwrapperid) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id);

-- Recreate match_to_action_profiles foreign key constraint
ALTER TABLE ${myuniversity}_${mymodule}.match_to_action_profiles
  ADD CONSTRAINT masterwrapperid_match_profiles_fkey
    FOREIGN KEY (masterwrapperid) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id);

ALTER TABLE ${myuniversity}_${mymodule}.match_to_action_profiles
  ADD CONSTRAINT detailwrapperid_action_profiles_fkey
    FOREIGN KEY (detailwrapperid) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id);

-- Recreate match_to_match_profiles foreign key constraint
ALTER TABLE ${myuniversity}_${mymodule}.match_to_match_profiles
  ADD CONSTRAINT masterwrapperid_match_profiles_fkey
    FOREIGN KEY (masterwrapperid) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id);

ALTER TABLE ${myuniversity}_${mymodule}.match_to_match_profiles
  ADD CONSTRAINT detailwrapperid_match_profiles_fkey
    FOREIGN KEY (detailwrapperid) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id);
