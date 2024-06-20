CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

/*
This script will migrate old profile_associations to new general association table. The order of DML is important to ensure consistent
state before and after migration.
*/

DO
-- action_to_mapping_profiles: migration
$$
  DECLARE
    r                  record;
  BEGIN
    FOR r IN
      select atm.id, atm.jsonb
      from action_to_mapping_profiles atm
      LOOP
        -- insert into new association table
        INSERT INTO profile_associations (id, job_profile_id, master_wrapper_id,
            detail_wrapper_id, master_profile_id, detail_profile_id,
            master_profile_type, detail_profile_type, detail_order, react_to) values
            (r.id,
            null,
            (r.jsonb ->> 'masterWrapperId')::uuid,
            (r.jsonb ->> 'detailWrapperId')::uuid,
            (r.jsonb ->> 'masterProfileId')::uuid,
            (r.jsonb ->> 'detailProfileId')::uuid,
           'ACTION_PROFILE',
           'MAPPING_PROFILE',
            (r.jsonb ->> 'order')::int,
            null
           ) ON CONFLICT DO NOTHING;

      END LOOP;
      RAISE NOTICE 'PROFILES_MIGRATION:: migrated from action_to_mapping_profiles';
  END
$$;

DO
-- job_to_match_profiles: migration
$$
  DECLARE
    r                record;
  BEGIN
    FOR r IN
      select jtm.id, jtm.jsonb
      from job_to_match_profiles jtm
      LOOP
        -- insert into new association table
        INSERT INTO profile_associations (id, job_profile_id, master_wrapper_id,
            detail_wrapper_id, master_profile_id, detail_profile_id,
            master_profile_type, detail_profile_type, detail_order, react_to) values
            (r.id,
            null,
            (r.jsonb ->> 'masterWrapperId')::uuid,
            (r.jsonb ->> 'detailWrapperId')::uuid,
            (r.jsonb ->> 'masterProfileId')::uuid,
            (r.jsonb ->> 'detailProfileId')::uuid,
           'JOB_PROFILE',
           'MATCH_PROFILE',
            (r.jsonb ->> 'order')::int,
            null
           ) ON CONFLICT DO NOTHING;

      END LOOP;
    RAISE NOTICE 'PROFILES_MIGRATION:: migrated from job_to_match_profiles';
  END
$$;

DO
-- job_to_action_profiles: migration
$$
  DECLARE
    r                 record;
  BEGIN
    FOR r IN
      select jta.id, jta.jsonb
      from job_to_action_profiles jta
      LOOP
        -- insert into new association table
        INSERT INTO profile_associations (id, job_profile_id, master_wrapper_id,
            detail_wrapper_id, master_profile_id, detail_profile_id,
            master_profile_type, detail_profile_type, detail_order, react_to) values
            (r.id,
            null,
            (r.jsonb ->> 'masterWrapperId')::uuid,
            (r.jsonb ->> 'detailWrapperId')::uuid,
            (r.jsonb ->> 'masterProfileId')::uuid,
            (r.jsonb ->> 'detailProfileId')::uuid,
           'JOB_PROFILE',
           'ACTION_PROFILE',
            (r.jsonb ->> 'order')::int,
            null
           ) ON CONFLICT DO NOTHING;

      END LOOP;
    RAISE NOTICE 'PROFILES_MIGRATION:: migrated from job_to_action_profiles';
  END
$$;

DO
-- match_to_match_profiles: migration
$$
  DECLARE
    r                 record;
  BEGIN
    FOR r IN
      select mtm.id, mtm.jsonb
      from match_to_match_profiles mtm
      LOOP
        -- insert into new association table
        INSERT INTO profile_associations (id, job_profile_id, master_wrapper_id,
            detail_wrapper_id, master_profile_id, detail_profile_id,
            master_profile_type, detail_profile_type, detail_order, react_to) values
            (r.id,
            (r.jsonb ->> 'jobProfileId')::uuid,
            (r.jsonb ->> 'masterWrapperId')::uuid,
            (r.jsonb ->> 'detailWrapperId')::uuid,
            (r.jsonb ->> 'masterProfileId')::uuid,
            (r.jsonb ->> 'detailProfileId')::uuid,
           'MATCH_PROFILE',
           'MATCH_PROFILE',
            (r.jsonb ->> 'order')::int,
            (r.jsonb ->> 'reactTo')::text
           ) ON CONFLICT DO NOTHING;

      END LOOP;
    RAISE NOTICE 'PROFILES_MIGRATION:: migrated from match_to_match_profiles';
  END
$$;

DO
-- match_to_action_profiles: migration
$$
  DECLARE
    r                 record;
  BEGIN
    FOR r IN
      select mta.id, mta.jsonb
      from match_to_action_profiles mta
      LOOP
        -- insert into new association table
        INSERT INTO profile_associations (id, job_profile_id, master_wrapper_id,
            detail_wrapper_id, master_profile_id, detail_profile_id,
            master_profile_type, detail_profile_type, detail_order, react_to) values
            (r.id,
            (r.jsonb ->> 'jobProfileId')::uuid,
            (r.jsonb ->> 'masterWrapperId')::uuid,
            (r.jsonb ->> 'detailWrapperId')::uuid,
            (r.jsonb ->> 'masterProfileId')::uuid,
            (r.jsonb ->> 'detailProfileId')::uuid,
           'MATCH_PROFILE',
           'ACTION_PROFILE',
            (r.jsonb ->> 'order')::int,
            (r.jsonb ->> 'reactTo')::text
           ) ON CONFLICT DO NOTHING;
      END LOOP;
    RAISE NOTICE 'PROFILES_MIGRATION:: migrated from match_to_action_profiles';
  END
$$;
/*
 System table for saving migration history.
 */
insert into metadata_internal(id, jsonb, creation_date)
  values (public.uuid_generate_v4(), '{"name": "Migration of profiles to the use of new general associations"}', now()::timestamptz);
