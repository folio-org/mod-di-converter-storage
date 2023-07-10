--Modify job_to_action_profiles table
ALTER TABLE ${myuniversity}_${mymodule}.job_to_action_profiles
DROP CONSTRAINT masterwrapperid_profile_wrappers_fkey;

ALTER TABLE ${myuniversity}_${mymodule}.job_to_action_profiles
ADD CONSTRAINT masterwrapperid_profile_wrappers_fkey FOREIGN KEY (masterWrapperId) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id) ON DELETE CASCADE;

ALTER TABLE ${myuniversity}_${mymodule}.job_to_action_profiles
DROP CONSTRAINT detailwrapperid_profile_wrappers_fkey;

ALTER TABLE ${myuniversity}_${mymodule}.job_to_action_profiles
ADD CONSTRAINT detailwrapperid_profile_wrappers_fkey FOREIGN KEY (detailWrapperId) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id) ON DELETE CASCADE;


--Modify job_to_match_profiles table
ALTER TABLE ${myuniversity}_${mymodule}.job_to_match_profiles
DROP CONSTRAINT masterwrapperid_profile_wrappers_fkey;

ALTER TABLE ${myuniversity}_${mymodule}.job_to_match_profiles
ADD CONSTRAINT masterwrapperid_profile_wrappers_fkey FOREIGN KEY (masterWrapperId) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id) ON DELETE CASCADE;

ALTER TABLE ${myuniversity}_${mymodule}.job_to_match_profiles
DROP CONSTRAINT detailwrapperid_profile_wrappers_fkey;

ALTER TABLE ${myuniversity}_${mymodule}.job_to_match_profiles
ADD CONSTRAINT detailwrapperid_profile_wrappers_fkey FOREIGN KEY (detailWrapperId) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id) ON DELETE CASCADE;


--Modify match_to_match_profiles table
ALTER TABLE ${myuniversity}_${mymodule}.match_to_match_profiles
DROP CONSTRAINT masterwrapperid_profile_wrappers_fkey;

ALTER TABLE ${myuniversity}_${mymodule}.match_to_match_profiles
ADD CONSTRAINT masterwrapperid_profile_wrappers_fkey FOREIGN KEY (masterWrapperId) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id) ON DELETE CASCADE;

ALTER TABLE ${myuniversity}_${mymodule}.match_to_match_profiles
DROP CONSTRAINT detailwrapperid_profile_wrappers_fkey;

ALTER TABLE ${myuniversity}_${mymodule}.match_to_match_profiles
ADD CONSTRAINT detailwrapperid_profile_wrappers_fkey FOREIGN KEY (detailWrapperId) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id) ON DELETE CASCADE;


--Modify match_to_action_profiles table
ALTER TABLE ${myuniversity}_${mymodule}.match_to_action_profiles
DROP CONSTRAINT masterwrapperid_profile_wrappers_fkey;

ALTER TABLE ${myuniversity}_${mymodule}.match_to_action_profiles
ADD CONSTRAINT masterwrapperid_profile_wrappers_fkey FOREIGN KEY (masterWrapperId) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id) ON DELETE CASCADE;

ALTER TABLE ${myuniversity}_${mymodule}.match_to_action_profiles
DROP CONSTRAINT detailwrapperid_profile_wrappers_fkey;

ALTER TABLE ${myuniversity}_${mymodule}.match_to_action_profiles
ADD CONSTRAINT detailwrapperid_profile_wrappers_fkey FOREIGN KEY (detailWrapperId) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id) ON DELETE CASCADE;


--Modify action_to_action_profiles table
ALTER TABLE ${myuniversity}_${mymodule}.action_to_action_profiles
DROP CONSTRAINT masterwrapperid_profile_wrappers_fkey;

ALTER TABLE ${myuniversity}_${mymodule}.action_to_action_profiles
ADD CONSTRAINT masterwrapperid_profile_wrappers_fkey FOREIGN KEY (masterWrapperId) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id) ON DELETE CASCADE;

ALTER TABLE ${myuniversity}_${mymodule}.action_to_action_profiles
DROP CONSTRAINT detailwrapperid_profile_wrappers_fkey;

ALTER TABLE ${myuniversity}_${mymodule}.action_to_action_profiles
ADD CONSTRAINT detailwrapperid_profile_wrappers_fkey FOREIGN KEY (detailWrapperId) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id) ON DELETE CASCADE;


--Modify action_to_match_profiles table
ALTER TABLE ${myuniversity}_${mymodule}.action_to_match_profiles
DROP CONSTRAINT masterwrapperid_profile_wrappers_fkey;

ALTER TABLE ${myuniversity}_${mymodule}.action_to_match_profiles
ADD CONSTRAINT masterwrapperid_profile_wrappers_fkey FOREIGN KEY (masterWrapperId) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id) ON DELETE CASCADE;

ALTER TABLE ${myuniversity}_${mymodule}.action_to_match_profiles
DROP CONSTRAINT detailwrapperid_profile_wrappers_fkey;

ALTER TABLE ${myuniversity}_${mymodule}.action_to_match_profiles
ADD CONSTRAINT detailwrapperid_profile_wrappers_fkey FOREIGN KEY (detailWrapperId) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id) ON DELETE CASCADE;


--Modify action_to_mapping_profiles table
ALTER TABLE ${myuniversity}_${mymodule}.action_to_mapping_profiles
DROP CONSTRAINT masterwrapperid_profile_wrappers_fkey;

ALTER TABLE ${myuniversity}_${mymodule}.action_to_mapping_profiles
ADD CONSTRAINT masterwrapperid_profile_wrappers_fkey FOREIGN KEY (masterWrapperId) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id) ON DELETE CASCADE;

ALTER TABLE ${myuniversity}_${mymodule}.action_to_mapping_profiles
DROP CONSTRAINT detailwrapperid_profile_wrappers_fkey;

ALTER TABLE ${myuniversity}_${mymodule}.action_to_mapping_profiles
ADD CONSTRAINT detailwrapperid_profile_wrappers_fkey FOREIGN KEY (detailWrapperId) REFERENCES ${myuniversity}_${mymodule}.profile_wrappers(id) ON DELETE CASCADE;
