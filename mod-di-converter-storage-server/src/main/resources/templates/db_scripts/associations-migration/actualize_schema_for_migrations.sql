DROP FUNCTION IF EXISTS get_profile_association_snapshot CASCADE;

CREATE OR REPLACE FUNCTION get_profile_association_snapshot(_association_table text, _master_table text, _master_type text, _detail_table text, _detail_type text) RETURNS TABLE(association_id uuid, master_id uuid, masterwrapperid uuid, master json, master_type text, detail_id uuid, detailwrapperid uuid, detail_type text, detail_order integer, detail json, react_to text, job_profile_id uuid) AS $$ BEGIN RETURN query EXECUTE format('SELECT association.id,  masterwrapper.%s_id AS master_id, association.masterwrapperid AS masterwrapperid, json_agg(master.jsonb) AS master, ''%s'' AS master_type, detailwrapper.%s_id AS detail_id, association.detailwrapperid AS detailwrapperid, ''%s'' AS detail_type, CAST(association.jsonb->>''order'' AS integer) AS detail_order, json_agg(detail.jsonb) AS detail, CAST(association.jsonb->>''reactTo'' AS text) as react_to, CAST(association.jsonb->>''jobProfileId'' AS uuid) as job_profile_id FROM %s association INNER JOIN profile_wrappers as masterwrapper ON association.masterwrapperid = masterwrapper.id INNER JOIN profile_wrappers as detailwrapper ON association.detailwrapperid = detailwrapper.id INNER JOIN %s master ON master.id = masterwrapper.%s_id INNER JOIN %s detail ON detail.id = detailwrapper.%s_id GROUP BY association.id, masterwrapper.%s_id, detailwrapper.%s_id, association.masterwrapperid, association.detailwrapperid', _master_type, _master_type, _detail_type, _detail_type, _association_table, _master_table, _master_type, _detail_table, _detail_type, _master_type, _detail_type); END $$ language plpgsql;

CREATE OR REPLACE VIEW associations_view AS SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id FROM get_profile_association_snapshot('job_to_match_profiles', 'job_profiles', 'JOB_PROFILE', 'match_profiles', 'MATCH_PROFILE') UNION ALL SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id FROM get_profile_association_snapshot('job_to_action_profiles', 'job_profiles', 'JOB_PROFILE', 'action_profiles', 'ACTION_PROFILE') UNION ALL SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id FROM get_profile_association_snapshot('match_to_match_profiles', 'match_profiles', 'MATCH_PROFILE', 'match_profiles', 'MATCH_PROFILE') UNION ALL SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id FROM get_profile_association_snapshot('match_to_action_profiles', 'match_profiles','MATCH_PROFILE', 'action_profiles', 'ACTION_PROFILE') UNION ALL SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id FROM get_profile_association_snapshot('action_to_match_profiles', 'action_profiles', 'ACTION_PROFILE', 'match_profiles', 'MATCH_PROFILE') UNION ALL SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id FROM get_profile_association_snapshot('action_to_action_profiles', 'action_profiles', 'ACTION_PROFILE', 'action_profiles', 'ACTION_PROFILE') UNION ALL SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id FROM get_profile_association_snapshot('action_to_mapping_profiles', 'action_profiles', 'ACTION_PROFILE', 'mapping_profiles', 'MAPPING_PROFILE') UNION ALL SELECT ASSOCIATION.ID AS association_id, CAST(ASSOCIATION.JSONB ->> 'jobProfileId' AS UUID) AS master_id, ASSOCIATION.MASTERWRAPPERID, json_agg(JOB.jsonb) AS master, 'JOB_PROFILE' AS master_type, DETAIL.ID AS detail_id, ASSOCIATION.DETAILWRAPPERID, 'ACTION_PROFILE' AS detail_type, 0 AS detail_order, json_agg(DETAIL.jsonb) AS detail, null AS react_to, CAST(ASSOCIATION.JSONB ->> 'jobProfileId' AS UUID) AS job_profile_id FROM MATCH_TO_ACTION_PROFILES ASSOCIATION INNER JOIN PROFILE_WRAPPERS MASTERWRAPPER ON ASSOCIATION.MASTERWRAPPERID = MASTERWRAPPER.ID INNER JOIN PROFILE_WRAPPERS DETAILWRAPPER ON ASSOCIATION.DETAILWRAPPERID = DETAILWRAPPER.ID INNER JOIN MATCH_PROFILES MASTER ON MASTER.ID = MASTERWRAPPER.MATCH_PROFILE_ID INNER JOIN ACTION_PROFILES DETAIL ON DETAIL.ID = DETAILWRAPPER.ACTION_PROFILE_ID INNER JOIN JOB_PROFILES JOB ON JOB.ID = CAST(ASSOCIATION.JSONB ->> 'jobProfileId' AS UUID) WHERE ASSOCIATION.JSONB ->> 'detailProfileType' = 'ACTION_PROFILE' AND ASSOCIATION.JSONB ->> 'masterProfileType' = 'MATCH_PROFILE' GROUP BY ASSOCIATION.ID, MASTER.ID, DETAIL.ID, DETAIL.JSONB;

CREATE OR REPLACE RULE delete_associations_with_details AS ON DELETE TO associations_view DO INSTEAD (DELETE FROM action_to_action_profiles WHERE OLD.master_id IN (SELECT action_profile_id FROM profile_wrappers WHERE id = action_to_action_profiles.masterwrapperid); DELETE FROM action_to_mapping_profiles WHERE OLD.master_id IN (SELECT action_profile_id FROM profile_wrappers WHERE id = action_to_mapping_profiles.masterwrapperid); DELETE FROM action_to_match_profiles WHERE OLD.master_id IN (SELECT action_profile_id FROM profile_wrappers WHERE id = action_to_match_profiles.masterwrapperid); DELETE FROM job_to_action_profiles WHERE OLD.master_id IN (SELECT job_profile_id FROM profile_wrappers WHERE id = job_to_action_profiles.masterwrapperid); DELETE FROM job_to_match_profiles WHERE OLD.master_id IN (SELECT job_profile_id FROM profile_wrappers WHERE id = job_to_match_profiles.masterwrapperid); DELETE FROM match_to_action_profiles WHERE OLD.master_id IN (SELECT match_profile_id FROM profile_wrappers WHERE id = match_to_action_profiles.masterwrapperid); DELETE FROM match_to_match_profiles WHERE OLD.master_id IN (SELECT match_profile_id FROM profile_wrappers WHERE id = match_to_match_profiles.masterwrapperid););

CREATE OR REPLACE FUNCTION get_profile_snapshot(profileId uuid, profile_type text, profile_table text, jobProfileId text) RETURNS TABLE(snapshot json) AS $$ BEGIN  RETURN query EXECUTE format('WITH RECURSIVE recursive_snapshot AS (SELECT job_profile.id AS association_id, CAST(NULL AS uuid) AS master_id, job_profile.id AS detail_id, CAST(NULL AS uuid) AS masterwrapperid, pw.id AS detailwrapperid, ''%s'' detail_type, null AS master_type, 0 AS detail_order, json_agg(job_profile.jsonb) detail, null AS react_to FROM %s AS job_profile LEFT JOIN profile_wrappers pw ON pw.job_profile_id = job_profile.id WHERE job_profile.id = ''%s'' GROUP BY job_profile.id, pw.id UNION ALL SELECT associations_view.association_id, associations_view.master_id AS master_id, associations_view.detail_id AS detail_id, associations_view.masterwrapperid AS masterwrapperid, associations_view.detailwrapperid AS detailwrapperid, associations_view.detail_type AS detail_type, associations_view.master_type AS master_type, associations_view.detail_order AS detail_order, associations_view.detail AS detail, associations_view.react_to AS react_to FROM associations_view INNER JOIN recursive_snapshot ON associations_view.masterwrapperid = recursive_snapshot.detailwrapperid AND CASE WHEN associations_view.master_type = ''MATCH_PROFILE'' AND ''%s'' != ''null'' THEN associations_view.job_profile_id = NULLIF(''%s'',''null'')::uuid ELSE associations_view.job_profile_id IS NULL END) SELECT row_to_json(row) FROM recursive_snapshot row ORDER BY row.detail_order ASC', profile_type, profile_table, profileId, jobProfileId, jobProfileId); END $$language plpgsql;

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA ${myuniversity}_${mymodule} TO ${myuniversity}_${mymodule};

/*
  To check the results of the migration. Could be remove after migration.
 */
drop table if exists snapshots_new;
create table snapshots_new as
select job_profile_id, s.get_profile_snapshot ->> 'association_id' as association_id, s.get_profile_snapshot as snapshot
  from (select jp.id job_profile_id, get_profile_snapshot(jp.id, 'JOB_PROFILE', 'job_profiles', jp.id::TEXT)  from job_profiles jp) s;
