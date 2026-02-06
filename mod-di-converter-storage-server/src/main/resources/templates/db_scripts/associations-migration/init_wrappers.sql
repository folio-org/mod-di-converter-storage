CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

/*
This script will migrate job profiles to utilize profile wrappers. The order of DML is important to ensure consistent
state before and after migration.
*/

-- create unique wrappers for each job profile
insert into profile_wrappers (id, profile_type, job_profile_id)
select public.uuid_generate_v4(), 'JOB_PROFILE', jp.id
from job_profiles jp;

-- create unique wrappers for each action profile
insert into profile_wrappers (id, profile_type, action_profile_id)
select public.uuid_generate_v4(), 'ACTION_PROFILE', ap.id
from action_profiles ap;

-- create unique wrappers for each mapping profile
insert into profile_wrappers (id, profile_type, mapping_profile_id)
select public.uuid_generate_v4(), 'MAPPING_PROFILE', mp.id
from mapping_profiles mp;

/*
 System table for saving migration history.
 */
insert into metadata_internal(id, jsonb, creation_date)
  values (public.uuid_generate_v4(), '{"name": "Migration of profiles to the use of wrappers and general profile_associations"}', now()::timestamptz);
