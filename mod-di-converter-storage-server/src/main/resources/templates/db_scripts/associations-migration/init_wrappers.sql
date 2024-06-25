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

DO
-- action_to_mapping_profiles: create unique wrappers for each action and mapping profile
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

        -- update wrapper references
        UPDATE action_to_mapping_profiles
        SET jsonb = jsonb_set(jsonb_set(jsonb, '{masterWrapperId}', to_jsonb(action_wrapper_id), true),
                              '{detailWrapperId}', to_jsonb(mapping_wrapper_id), true)
        WHERE id = r.id;
      END LOOP;
      RAISE NOTICE 'PROFILES_MIGRATION:: updated action_to_mapping_profiles';
  END
$$;

DO
-- job_to_match_profiles: create wrappers for match profiles in job_to_match_profiles profile_associations
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
      -- create wrapper for match profile
      -- job_profile_id is populated to help select the right wrapper further down this script
        insert into profile_wrappers (id, profile_type, match_profile_id, associated_job_profile_id)
        values (public.uuid_generate_v4(), 'MATCH_PROFILE', (r.jsonb ->> 'detailProfileId')::uuid,
                (r.jsonb ->> 'masterProfileId')::uuid)
        returning id into match_wrapper_id;

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

        -- update wrapper references
        UPDATE job_to_match_profiles
        SET jsonb = jsonb_set(jsonb_set(jsonb, '{masterWrapperId}', to_jsonb(job_wrapper_id), true),
                              '{detailWrapperId}', to_jsonb(match_wrapper_id), true)
        WHERE id = r.id;
      END LOOP;
    RAISE NOTICE 'PROFILES_MIGRATION:: updated job_to_match_profiles';
  END
$$;


DO
-- job_to_action_profiles: use existing wrappers for job and action profiles in job_to_actions_profiles profile_associations
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

        -- update wrapper references
        UPDATE job_to_action_profiles
        SET jsonb = jsonb_set(jsonb_set(jsonb, '{masterWrapperId}', to_jsonb(job_wrapper_id), true),
                              '{detailWrapperId}', to_jsonb(action_wrapper_id), true)
        WHERE id = r.id;
      END LOOP;
    RAISE NOTICE 'PROFILES_MIGRATION:: updated job_to_action_profiles';
  END
$$;

DO
-- match_to_match_profiles: create wrappers for match profiles in match_to_match_profiles profile_associations
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
            -- create wrapper for detail match profile
            insert into profile_wrappers (id, profile_type, match_profile_id, associated_job_profile_id)
            values (public.uuid_generate_v4(), 'MATCH_PROFILE',
                    (recursive_record.jsonb ->> 'detailProfileId')::uuid,
                    (recursive_record.jsonb ->> 'jobProfileId')::uuid)
            returning id into detail_match_wrapper_id;

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

            -- update wrapper references
            UPDATE match_to_match_profiles
            SET jsonb = jsonb_set(
              jsonb_set(jsonb, '{masterWrapperId}', to_jsonb(master_match_wrapper_id), true),
              '{detailWrapperId}', to_jsonb(detail_match_wrapper_id), true)
            WHERE id = recursive_record.id;
          end loop;

      end loop;
    raise notice 'PROFILES_MIGRATION:: updated match_to_match_profiles';
  end
$$;

DO
-- match_to_action_profiles: create wrappers for match profiles in match_to_action_profiles profile_associations
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

        -- update wrapper references
        UPDATE match_to_action_profiles
        SET jsonb = jsonb_set(jsonb_set(jsonb, '{masterWrapperId}', to_jsonb(match_wrapper_id), true),
                              '{detailWrapperId}', to_jsonb(action_wrapper_id), true)
        WHERE id = r.id;
      END LOOP;
    RAISE NOTICE 'PROFILES_MIGRATION:: updated match_to_action_profiles';
  END
$$;

/*
 System table for saving migration history.
 */
insert into metadata_internal(id, jsonb, creation_date)
  values (public.uuid_generate_v4(), '{"name": "Migration of profiles to the use of wrappers and general profile_associations"}', now()::timestamptz);
