-- Custom script to create associations_view view. Changes in this file will not result in an update of the view.
-- To change the associations_view view, update this script and copy it to the appropriate scripts.snippet field of the schema.json

CREATE OR REPLACE VIEW associations_view
  AS
    SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id
    FROM get_profile_association_snapshot('job_to_match_profiles', 'job_profiles', 'JOB_PROFILE', 'match_profiles', 'MATCH_PROFILE')
      UNION ALL
    SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id
    FROM get_profile_association_snapshot('job_to_action_profiles', 'job_profiles', 'JOB_PROFILE', 'action_profiles', 'ACTION_PROFILE')
      UNION ALL
    SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id
    FROM get_profile_association_snapshot('match_to_match_profiles', 'match_profiles', 'MATCH_PROFILE', 'match_profiles', 'MATCH_PROFILE')
     UNION ALL
    SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id
    FROM get_profile_association_snapshot('match_to_action_profiles', 'match_profiles','MATCH_PROFILE', 'action_profiles', 'ACTION_PROFILE')
     UNION ALL
    SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id
    FROM get_profile_association_snapshot('action_to_match_profiles', 'action_profiles', 'ACTION_PROFILE', 'match_profiles', 'MATCH_PROFILE')
      UNION ALL
    SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id
    FROM get_profile_association_snapshot('action_to_action_profiles', 'action_profiles', 'ACTION_PROFILE', 'action_profiles', 'ACTION_PROFILE')
      UNION ALL
    SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id
    FROM get_profile_association_snapshot('action_to_mapping_profiles', 'action_profiles', 'ACTION_PROFILE', 'mapping_profiles', 'MAPPING_PROFILE')
      UNION ALL
    SELECT ASSOCIATION.ID AS association_id, CAST(ASSOCIATION.JSONB ->> 'jobProfileId' AS UUID) AS master_id, ASSOCIATION.MASTERWRAPPERID, json_agg(JOB.jsonb) AS master, 'JOB_PROFILE' AS master_type, DETAIL.ID AS detail_id, ASSOCIATION.DETAILWRAPPERID, 'ACTION_PROFILE' AS detail_type, 0 AS detail_order, json_agg(DETAIL.JSONB) AS detail, null AS react_to, CAST(ASSOCIATION.JSONB ->> 'jobProfileId' AS UUID) AS job_profile_id
    FROM MATCH_TO_ACTION_PROFILES ASSOCIATION
    INNER JOIN PROFILE_WRAPPERS MASTERWRAPPER ON ASSOCIATION.MASTERWRAPPERID = MASTERWRAPPER.ID
    INNER JOIN PROFILE_WRAPPERS DETAILWRAPPER ON ASSOCIATION.DETAILWRAPPERID = DETAILWRAPPER.ID
    INNER JOIN MATCH_PROFILES MASTER ON MASTER.ID = MASTERWRAPPER.MATCH_PROFILE_ID
    INNER JOIN ACTION_PROFILES DETAIL ON DETAIL.ID = DETAILWRAPPER.ACTION_PROFILE_ID
    INNER JOIN JOB_PROFILES JOB ON JOB.ID = CAST(ASSOCIATION.JSONB ->> 'jobProfileId' AS UUID)
    WHERE ASSOCIATION.JSONB ->> 'detailProfileType' = 'ACTION_PROFILE'
    	AND ASSOCIATION.JSONB ->> 'masterProfileType' = 'MATCH_PROFILE'
    GROUP BY ASSOCIATION.ID, MASTER.ID, DETAIL.ID, DETAIL.JSONB;

-- Script to create rule which will triggered upon delete query to associations_view view.
CREATE OR REPLACE RULE delete_associations_with_details AS
  ON DELETE TO associations_view
  DO INSTEAD (
    DELETE FROM action_to_action_profiles WHERE OLD.master_id IN
      (SELECT action_profile_id FROM profile_wrappers WHERE id = action_to_action_profiles.masterwrapperid);
    DELETE FROM action_to_mapping_profiles WHERE OLD.master_id IN
      (SELECT action_profile_id FROM profile_wrappers WHERE id = action_to_mapping_profiles.masterwrapperid);
    DELETE FROM action_to_match_profiles WHERE OLD.master_id IN
      (SELECT action_profile_id FROM profile_wrappers WHERE id = action_to_match_profiles.masterwrapperid);
    DELETE FROM job_to_action_profiles WHERE OLD.master_id IN
      (SELECT job_profile_id FROM profile_wrappers WHERE id = job_to_action_profiles.masterwrapperid);
    DELETE FROM job_to_match_profiles WHERE OLD.master_id IN
      (SELECT job_profile_id FROM profile_wrappers WHERE id = job_to_match_profiles.masterwrapperid);
    DELETE FROM match_to_action_profiles WHERE OLD.master_id IN
      (SELECT match_profile_id FROM profile_wrappers WHERE id = match_to_action_profiles.masterwrapperid);
    DELETE FROM match_to_match_profiles WHERE OLD.master_id IN
      (SELECT match_profile_id FROM profile_wrappers WHERE id = match_to_match_profiles.masterwrapperid);
    );
