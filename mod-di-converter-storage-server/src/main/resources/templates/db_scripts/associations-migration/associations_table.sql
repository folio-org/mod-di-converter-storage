DROP TRIGGER set_id_in_jsonb
ON ${myuniversity}_${mymodule}.associations CASCADE;

ALTER TABLE ${myuniversity}_${mymodule}.associations
  ADD IF NOT EXISTS id text NULL,
  ADD IF NOT EXISTS job_profile_id uuid NULL,
  ADD IF NOT EXISTS master_wrapper_id uuid NULL,
  ADD IF NOT EXISTS detail_wrapper_id uuid NULL,
  ADD IF NOT EXISTS master_profile_id uuid NULL,
  ADD IF NOT EXISTS detail_profile_id uuid NULL,
  ADD IF NOT EXISTS master_profile_type text NULL,
  ADD IF NOT EXISTS detail_profile_type text NULL,
  ADD IF NOT EXISTS order integer NULL,

  ADD CONSTRAINT associations_master_wrapper_fk FOREIGN KEY (master_wrapper_id) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id),
  ADD CONSTRAINT associations_detail_wrapper_fk FOREIGN KEY (detail_wrapper_id) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id),
