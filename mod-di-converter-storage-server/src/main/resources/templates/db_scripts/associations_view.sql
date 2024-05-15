-- Custom script to create associations_view view. Changes in this file will not result in an update of the view.
-- To change the associations_view view, update this script and copy it to the appropriate scripts.snippet field of the schema.json

CREATE OR REPLACE VIEW associations_view
  AS
    SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id
    FROM get_profile_association_snapshot('job_profiles', 'JOB_PROFILE', 'match_profiles', 'MATCH_PROFILE')
      UNION ALL
    SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id
    FROM get_profile_association_snapshot('job_profiles', 'JOB_PROFILE', 'action_profiles', 'ACTION_PROFILE')
      UNION ALL
    SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id
    FROM get_profile_association_snapshot('match_profiles', 'MATCH_PROFILE', 'match_profiles', 'MATCH_PROFILE')
     UNION ALL
    SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id
    FROM get_profile_association_snapshot('match_profiles','MATCH_PROFILE', 'action_profiles', 'ACTION_PROFILE')
     UNION ALL
    SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id
    FROM get_profile_association_snapshot('action_profiles', 'ACTION_PROFILE', 'match_profiles', 'MATCH_PROFILE')
      UNION ALL
    SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id
    FROM get_profile_association_snapshot('action_profiles', 'ACTION_PROFILE', 'action_profiles', 'ACTION_PROFILE')
      UNION ALL
    SELECT association_id, master_id, masterwrapperid, master, master_type, detail_id, detailwrapperid, detail_type, detail_order, detail, react_to, job_profile_id
    FROM get_profile_association_snapshot('action_profiles', 'ACTION_PROFILE', 'mapping_profiles', 'MAPPING_PROFILE')
      UNION ALL
    SELECT associations.id AS association_id, associations.job_profile_id AS master_id, associations.master_wrapper_id AS masterwrapperid, json_agg(JOB.jsonb) AS master, 'JOB_PROFILE' AS master_type, DETAIL.ID AS detail_id, associations.detail_wrapper_id AS detailwrapperid, 'ACTION_PROFILE' AS detail_type, 0 AS detail_order, json_agg(DETAIL.JSONB) AS detail, null AS react_to, associations.job_profile_id AS job_profile_id
    FROM associations
    INNER JOIN PROFILE_WRAPPERS MASTERWRAPPER ON associations.master_wrapper_id = MASTERWRAPPER.ID
    INNER JOIN PROFILE_WRAPPERS DETAILWRAPPER ON associations.detail_Wrapper_id = DETAILWRAPPER.ID
    INNER JOIN MATCH_PROFILES MASTER ON MASTER.ID = MASTERWRAPPER.MATCH_PROFILE_ID
    INNER JOIN ACTION_PROFILES DETAIL ON DETAIL.ID = DETAILWRAPPER.ACTION_PROFILE_ID
    INNER JOIN JOB_PROFILES JOB ON JOB.ID = associations.job_profile_id
    WHERE associations.detail_profile_type = 'ACTION_PROFILE'
    	AND associations.master_profile_type = 'MATCH_PROFILE'
    GROUP BY associations.id, MASTER.ID, DETAIL.ID, DETAIL.JSONB;

-- Script to create rule which will triggered upon delete query to associations_view view.
CREATE OR REPLACE RULE delete_associations_with_details AS
  ON DELETE TO associations_view
  DO INSTEAD (
    DELETE FROM associations WHERE OLD.master_id IN
      (SELECT action_profile_id FROM profile_wrappers WHERE id = associations.master_wrapper_id);
    DELETE FROM associations WHERE OLD.master_id IN
      (SELECT job_profile_id FROM profile_wrappers WHERE id = associations.master_wrapper_id);
    DELETE FROM associations WHERE OLD.master_id IN
      (SELECT match_profile_id FROM profile_wrappers WHERE id = associations.master_wrapper_id);
    );
