ALTER TABLE ${myuniversity}_${mymodule}.profile_wrappers
  DROP CONSTRAINT IF EXISTS profile_wrappers_action_profile_fk,
  DROP CONSTRAINT IF EXISTS profile_wrappers_match_profile_fk,
  DROP CONSTRAINT IF EXISTS profile_wrappers_mapping_profile_fk,
  DROP CONSTRAINT IF EXISTS profile_wrappers_job_profile_fk,
  ADD CONSTRAINT profile_wrappers_action_profile_fk FOREIGN KEY (action_profile_id) REFERENCES ${myuniversity}_${mymodule}.action_profiles(id) ON DELETE CASCADE,
  ADD CONSTRAINT profile_wrappers_match_profile_fk FOREIGN KEY (match_profile_id) REFERENCES ${myuniversity}_${mymodule}.match_profiles(id) ON DELETE CASCADE,
  ADD CONSTRAINT profile_wrappers_mapping_profile_fk FOREIGN KEY (mapping_profile_id) REFERENCES ${myuniversity}_${mymodule}.mapping_profiles(id) ON DELETE CASCADE,
  ADD CONSTRAINT profile_wrappers_job_profile_fk FOREIGN KEY (job_profile_id) REFERENCES ${myuniversity}_${mymodule}.job_profiles(id) ON DELETE CASCADE;
