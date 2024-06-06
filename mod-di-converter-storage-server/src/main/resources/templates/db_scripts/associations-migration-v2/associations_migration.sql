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
    action_wrapper_id  UUID;
    mapping_wrapper_id UUID;
  BEGIN
    FOR r IN
      select atm.id, atm.jsonb
      from action_to_mapping_profiles atm
      LOOP
        -- get existing wrapper for action profile
        select id
        into action_wrapper_id
        from profile_wrappers
        where action_profile_id = (r.jsonb ->> 'masterProfileId')::uuid;

        -- get existing wrapper for mapping profile
        select id
        into mapping_wrapper_id
        from profile_wrappers
        where mapping_profile_id = (r.jsonb ->> 'detailProfileId')::uuid;

        if mapping_wrapper_id is null or action_wrapper_id is null then
          raise debug 'Incorrect data, action_to_mapping_profiles id: %, action_wrapper_id: %, mapping_wrapper_id: %',
            r.id, action_wrapper_id, mapping_wrapper_id;
          continue;
        end if;

        -- insert into new association table
        INSERT INTO profile_associations (id, job_profile_id, master_wrapper_id,
            detail_wrapper_id, master_profile_id, detail_profile_id,
            master_profile_type, detail_profile_type, detail_order, react_to) values
            (r.id,
            null,
            action_wrapper_id,
            mapping_wrapper_id,
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
    match_wrapper_id UUID;
    job_wrapper_id   UUID;
  BEGIN
    FOR r IN
      select jtm.id, jtm.jsonb
      from job_to_match_profiles jtm
      LOOP

        -- get existing wrapper for job profile
        select id
        into job_wrapper_id
        from profile_wrappers
        where job_profile_id = (r.jsonb ->> 'masterProfileId')::uuid;

        if job_wrapper_id is null or match_wrapper_id is null then
          raise debug 'Incorrect data: job_to_match_profiles id: %, match_wrapper_id: %, job_wrapper_id: %',
            r.id, match_wrapper_id, job_wrapper_id;
          continue;
        end if;

        -- insert into new association table
        INSERT INTO profile_associations (id, job_profile_id, master_wrapper_id,
            detail_wrapper_id, master_profile_id, detail_profile_id,
            master_profile_type, detail_profile_type, detail_order, react_to) values
            (r.id,
            null,
            job_wrapper_id,
            match_wrapper_id,
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
    action_wrapper_id UUID;
    job_wrapper_id    UUID;
  BEGIN
    FOR r IN
      select jta.id, jta.jsonb
      from job_to_action_profiles jta
      LOOP
        -- get existing wrapper for action profile
        select id
        into action_wrapper_id
        from profile_wrappers
        where action_profile_id = (r.jsonb ->> 'detailProfileId')::uuid;

        -- get existing wrapper for job profile
        select id
        into job_wrapper_id
        from profile_wrappers
        where job_profile_id = (r.jsonb ->> 'masterProfileId')::uuid;

        if job_wrapper_id is null or action_wrapper_id is null then
          raise debug 'Incorrect data: job_to_action_profiles id: %, action_wrapper_id: %, job_wrapper_id: %',
            r.id, action_wrapper_id, job_wrapper_id;
          continue;
        end if;

        -- insert into new association table
        INSERT INTO profile_associations (id, job_profile_id, master_wrapper_id,
            detail_wrapper_id, master_profile_id, detail_profile_id,
            master_profile_type, detail_profile_type, detail_order, react_to) values
            (r.id,
            null,
            job_wrapper_id,
            action_wrapper_id,
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
    not_recursive_record    record;
    recursive_record        record;
    detail_match_wrapper_id UUID;
    master_match_wrapper_id UUID;
  BEGIN
    FOR not_recursive_record IN
      -- get match profiles that are associated with job profile. Each item in the result will be used to build a match hierarchy
      select jtm.id, jtm.jsonb
      from job_to_match_profiles jtm
      LOOP
        FOR recursive_record IN
          WITH RECURSIVE match_hierarchy AS (
            -- Base Case
            SELECT mtm.id, mtm.jsonb
            FROM match_to_match_profiles mtm
            where mtm.jsonb ->> 'masterProfileId' = not_recursive_record.jsonb ->> 'detailProfileId'
              and mtm.jsonb ->> 'jobProfileId' = not_recursive_record.jsonb ->> 'masterProfileId'
            UNION ALL
            -- Recursive Step
            SELECT m.id, m.jsonb
            FROM match_to_match_profiles m
                   INNER JOIN match_hierarchy mh
                              ON mh.jsonb ->> 'detailProfileId' = m.jsonb ->> 'masterProfileId'
                                and mh.jsonb ->> 'jobProfileId' = m.jsonb ->> 'jobProfileId')

          select *
          from match_hierarchy
          LOOP

            -- get existing wrapper for master match profile
            select id
            into master_match_wrapper_id
            from profile_wrappers
            where match_profile_id = (recursive_record.jsonb ->> 'masterProfileId')::uuid
              and associated_job_profile_id = (recursive_record.jsonb ->> 'jobProfileId')::uuid;

            -- insert into new association table
            INSERT INTO profile_associations (id, job_profile_id, master_wrapper_id,
                detail_wrapper_id, master_profile_id, detail_profile_id,
                master_profile_type, detail_profile_type, detail_order, react_to) values
                (recursive_record.id,
                (recursive_record.jsonb ->> 'jobProfileId')::uuid,
                master_match_wrapper_id,
                detail_match_wrapper_id,
                (recursive_record.jsonb ->> 'masterProfileId')::uuid,
                (recursive_record.jsonb ->> 'detailProfileId')::uuid,
               'MATCH_PROFILE',
               'MATCH_PROFILE',
                (recursive_record.jsonb ->> 'order')::int,
                (recursive_record.jsonb ->> 'reactTo')::text
               ) ON CONFLICT DO NOTHING;

          end loop;
      end loop;
    raise notice 'PROFILES_MIGRATION:: migrated from match_to_match_profiles';
  end
$$;

DO
-- match_to_action_profiles: migration
$$
  DECLARE
    r                 record;
    match_wrapper_id  UUID;
    action_wrapper_id UUID;
  BEGIN
    FOR r IN
      select mta.id, mta.jsonb
      from match_to_action_profiles mta
      LOOP
        -- get existing wrapper for action profile
        select id
        into action_wrapper_id
        from profile_wrappers
        where action_profile_id = (r.jsonb ->> 'detailProfileId')::uuid;

        -- get existing wrapper for match profile
        select id
        into match_wrapper_id
        from profile_wrappers
        where match_profile_id = (r.jsonb ->> 'masterProfileId')::uuid
            and associated_job_profile_id = (r.jsonb ->> 'jobProfileId')::uuid;

        if match_wrapper_id is null or action_wrapper_id is null then
          raise debug 'Incorrect data: match_to_action_profiles id: %, jobProfileId: %, action_wrapper_id: %, match_wrapper_id: %',
            r.id, (r.jsonb ->> 'jobProfileId')::uuid, action_wrapper_id, match_wrapper_id;
          continue;
        end if;

        -- insert into new association table
        INSERT INTO profile_associations (id, job_profile_id, master_wrapper_id,
            detail_wrapper_id, master_profile_id, detail_profile_id,
            master_profile_type, detail_profile_type, detail_order, react_to) values
            (r.id,
            (r.jsonb ->> 'jobProfileId')::uuid,
            match_wrapper_id,
            action_wrapper_id,
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

DO
-- action_to_action_profiles: migration
$$
  DECLARE
    r                        record;
    master_action_wrapper_id UUID;
    detail_action_wrapper_id UUID;
  BEGIN
    FOR r IN
      select ata.id, ata.jsonb
      from action_to_action_profiles ata
      LOOP
        -- get existing wrapper for detail action profile
        select id
        into detail_action_wrapper_id
        from profile_wrappers
        where action_profile_id = (r.jsonb ->> 'detailProfileId')::uuid;

        -- get existing wrapper for master action profile
        select id
        into master_action_wrapper_id
        from profile_wrappers
        where action_profile_id = (r.jsonb ->> 'masterProfileId')::uuid;

        if master_action_wrapper_id is null or detail_action_wrapper_id is null then
          raise warning 'BAD DATA, action_to_action_profiles id: %', r.id;
          continue;
        end if;

        -- insert into new association table
        INSERT INTO profile_associations (id, job_profile_id, master_wrapper_id,
            detail_wrapper_id, master_profile_id, detail_profile_id,
            master_profile_type, detail_profile_type, detail_order, react_to) values
            (r.id,
            null,
            master_action_wrapper_id,
            detail_action_wrapper_id,
            (r.jsonb ->> 'masterProfileId')::uuid,
            (r.jsonb ->> 'detailProfileId')::uuid,
           'ACTION_PROFILE',
           'ACTION_PROFILE',
            (r.jsonb ->> 'order')::int,
            null
           ) ON CONFLICT DO NOTHING;
      END LOOP;
    RAISE NOTICE 'PROFILES_MIGRATION:: migrated from action_to_action_profiles';
  END
$$;
