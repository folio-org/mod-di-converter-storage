CREATE TABLE ${myuniversity}_${mymodule}.profile_wrappers (
  id uuid NOT NULL,
  profile_type text NULL,
  action_profile_id uuid NULL,
  match_profile_id uuid NULL,
  mapping_profile_id uuid NULL,
  job_profile_id uuid NULL,
  CONSTRAINT profile_wrappers_pk PRIMARY KEY (id),
  CONSTRAINT profile_wrappers_action_profile_fk FOREIGN KEY (action_profile_id) REFERENCES ${myuniversity}_${mymodule}.action_profiles(id),
  CONSTRAINT profile_wrappers_match_profile_fk FOREIGN KEY (match_profile_id) REFERENCES ${myuniversity}_${mymodule}.match_profiles(id),
  CONSTRAINT profile_wrappers_mapping_profile_fk FOREIGN KEY (mapping_profile_id) REFERENCES ${myuniversity}_${mymodule}.mapping_profiles(id),
  CONSTRAINT profile_wrappers_job_profile_fk FOREIGN KEY (job_profile_id) REFERENCES ${myuniversity}_${mymodule}.job_profiles(id)
);
