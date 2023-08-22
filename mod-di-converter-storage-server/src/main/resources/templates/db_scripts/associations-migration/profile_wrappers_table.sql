DROP TRIGGER set_id_in_jsonb
ON ${myuniversity}_${mymodule}.profile_wrappers CASCADE;

ALTER TABLE ${myuniversity}_${mymodule}.profile_wrappers
  DROP COLUMN IF EXISTS jsonb,
  ADD IF NOT EXISTS profile_type text NULL,
  ADD IF NOT EXISTS action_profile_id uuid NULL,
  ADD IF NOT EXISTS match_profile_id uuid NULL,
  ADD IF NOT EXISTS mapping_profile_id uuid NULL,
  ADD IF NOT EXISTS job_profile_id uuid NULL,
  ADD CONSTRAINT profile_wrappers_action_profile_fk FOREIGN KEY (action_profile_id) REFERENCES ${myuniversity}_${mymodule}.action_profiles(id),
  ADD CONSTRAINT profile_wrappers_match_profile_fk FOREIGN KEY (match_profile_id) REFERENCES ${myuniversity}_${mymodule}.match_profiles(id),
  ADD CONSTRAINT profile_wrappers_mapping_profile_fk FOREIGN KEY (mapping_profile_id) REFERENCES ${myuniversity}_${mymodule}.mapping_profiles(id),
  ADD CONSTRAINT profile_wrappers_job_profile_fk FOREIGN KEY (job_profile_id) REFERENCES ${myuniversity}_${mymodule}.job_profiles(id);
