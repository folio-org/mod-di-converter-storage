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
    FROM get_profile_association_snapshot('action_profiles', 'ACTION_PROFILE', 'mapping_profiles', 'MAPPING_PROFILE')
      UNION ALL
    SELECT profile_associations.id AS association_id, profile_associations.job_profile_id AS master_id, profile_associations.master_wrapper_id AS masterwrapperid, json_agg(job.jsonb) AS master, 'JOB_PROFILE' AS master_type, detail.id AS detail_id, profile_associations.detail_wrapper_id AS detailwrapperid, 'ACTION_PROFILE' AS detail_type, 0 AS detail_order, json_agg(detail.jsonb) AS detail, null AS react_to, profile_associations.job_profile_id AS job_profile_id
    FROM profile_associations
    INNER JOIN profile_wrappers masterwrapper ON profile_associations.master_wrapper_id = masterwrapper.id
    INNER JOIN profile_wrappers detailwrapper ON profile_associations.detail_wrapper_id = detailwrapper.id
    INNER JOIN match_profiles master ON master.id = masterwrapper.match_profile_id
    INNER JOIN action_profiles detail ON detail.id = detailwrapper.action_profile_id
    INNER JOIN job_profiles job ON job.id = profile_associations.job_profile_id
    WHERE profile_associations.detail_profile_type = 'ACTION_PROFILE'
    	AND profile_associations.master_profile_type = 'MATCH_PROFILE'
    GROUP BY profile_associations.id, master.id, detail.id, detail.jsonb;

-- Script to create rule which will triggered upon delete query to associations_view view.
CREATE OR REPLACE RULE delete_associations_with_details AS
  ON DELETE TO associations_view
  DO INSTEAD (
    DELETE FROM profile_associations WHERE OLD.master_id IN
      (SELECT action_profile_id FROM profile_wrappers WHERE id = profile_associations.master_wrapper_id);
    DELETE FROM profile_associations WHERE OLD.master_id IN
      (SELECT job_profile_id FROM profile_wrappers WHERE id = profile_associations.master_wrapper_id);
    DELETE FROM profile_associations WHERE OLD.master_id IN
      (SELECT match_profile_id FROM profile_wrappers WHERE id = profile_associations.master_wrapper_id);
    );
