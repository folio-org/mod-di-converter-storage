-- create indexes on profile_wrappers to speed up retrieval processes.
CREATE INDEX IF NOT EXISTS profile_wrappers_jobprofileid_idx ON ${myuniversity}_${mymodule}.profile_wrappers USING hash (job_profile_id);
CREATE INDEX IF NOT EXISTS profile_wrappers_actionprofileid_idx ON ${myuniversity}_${mymodule}.profile_wrappers USING hash (action_profile_id);
CREATE INDEX IF NOT EXISTS profile_wrappers_mappingprofileid_idx ON ${myuniversity}_${mymodule}.profile_wrappers USING hash (mapping_profile_id);
CREATE INDEX IF NOT EXISTS profile_wrappers_matchprofileid_idx ON ${myuniversity}_${mymodule}.profile_wrappers USING hash (match_profile_id);


