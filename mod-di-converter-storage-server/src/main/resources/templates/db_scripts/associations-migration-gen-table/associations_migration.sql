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
      select atm.id, atm.jsonb, atm.masterwrapperid, atm.detailwrapperid
      from action_to_mapping_profiles atm
      LOOP

        if r.masterwrapperid is null or r.detailwrapperid is null then
            raise debug 'Incorrect data action_wrapper_id and mapping_wrapper_id are null: action_to_mapping_profiles id: %, jobProfileId: %, action_wrapper_id: %, mapping_wrapper_id: %',
                r.id, (r.jsonb ->> 'jobProfileId')::uuid, r.masterwrapperid, r.detailwrapperid;
            continue;
        end if;

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
      select jtm.id, jtm.jsonb, jtm.masterwrapperid, jtm.detailwrapperid
      from job_to_match_profiles jtm
      LOOP

        if r.masterwrapperid is null or r.detailwrapperid is null then
          raise debug 'Incorrect data job_wrapper_id and match_wrapper_id are null: job_to_match_profiles id: %, jobProfileId: %, job_wrapper_id: %, match_wrapper_id: %',
              r.id, (r.jsonb ->> 'jobProfileId')::uuid, r.masterwrapperid, r.detailwrapperid;
          continue;
        end if;

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
      select jta.id, jta.jsonb, jta.masterwrapperid, jta.detailwrapperid
      from job_to_action_profiles jta
      LOOP

        if r.masterwrapperid is null or r.detailwrapperid is null then
            raise debug 'Incorrect data job_wrapper_id and action_wrapper_id are null: job_to_action_profiles id: %, jobProfileId: %, job_wrapper_id: %, action_wrapper_id: %',
                r.id, (r.jsonb ->> 'jobProfileId')::uuid, r.masterwrapperid, r.detailwrapperid;
            continue;
        end if;

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
      select mtm.id, mtm.jsonb, mtm.masterwrapperid, mtm.detailwrapperid
      from match_to_match_profiles mtm
      LOOP

        if r.masterwrapperid is null or r.detailwrapperid is null then
            raise debug 'Incorrect data match_wrapper_id and match_wrapper_id are null: match_to_match_profiles id: %, jobProfileId: %, match_wrapper_id: %, match_wrapper_id: %',
                r.id, (r.jsonb ->> 'jobProfileId')::uuid, r.masterwrapperid, r.detailwrapperid;
            continue;
        end if;

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
      select mta.id, mta.jsonb, mta.masterwrapperid, mta.detailwrapperid
      from match_to_action_profiles mta
      LOOP

        if r.masterwrapperid is null or r.detailwrapperid is null then
            raise debug 'Incorrect data action_wrapper_id and match_wrapper_id are null: match_to_action_profiles id: %, jobProfileId: %, action_wrapper_id: %, match_wrapper_id: %',
                r.id, (r.jsonb ->> 'jobProfileId')::uuid, r.masterwrapperid, r.detailwrapperid;
            continue;
        end if;

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
