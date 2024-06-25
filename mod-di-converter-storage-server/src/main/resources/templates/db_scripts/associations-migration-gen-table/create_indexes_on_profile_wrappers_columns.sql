-- create indexes on profile_wrappers to speed up retrieval processes.
CREATE INDEX IF NOT EXISTS profile_wrappers_job_profile_id ON ${myuniversity}_${mymodule}.profile_wrappers USING hash (job_profile_id);
CREATE INDEX IF NOT EXISTS profile_wrappers_action_profile_id ON ${myuniversity}_${mymodule}.profile_wrappers USING hash (action_profile_id);
CREATE INDEX IF NOT EXISTS profile_wrappers_mapping_profile_id ON ${myuniversity}_${mymodule}.profile_wrappers USING hash (mapping_profile_id);
CREATE INDEX IF NOT EXISTS profile_wrappers_match_profile_id ON ${myuniversity}_${mymodule}.profile_wrappers USING hash (match_profile_id);


