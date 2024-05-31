-- Custom script to create get_profile_association_snapshot function. Changes in this file will not result in an update of the function.
-- To change the get_profile_association_snapshot function, update this script and copy it to the appropriate scripts.snippet field of the schema.json
CREATE OR REPLACE FUNCTION
  get_profile_association_snapshot(_master_table text, _master_type text, _detail_table text, _detail_type text)
  RETURNS TABLE(association_id uuid, master_id uuid, masterwrapperid uuid, master json, master_type text, detail_id uuid, detailwrapperid uuid, detail_type text, detail_order integer, detail json, react_to text, job_profile_id uuid)
    AS $$
      BEGIN
        RETURN query
          EXECUTE format('
            SELECT
              a.id,
              masterwrapper.%s_id AS master_id,
			  a.master_wrapper_id AS masterwrapperid,
              json_agg(master.jsonb) AS master,
              ''%s'' AS master_type,
              detailwrapper.%s_id AS detail_id,
			  a.detail_wrapper_id AS detailwrapperid,
              ''%s'' AS detail_type,
              a.detail_order AS detail_order,
              json_agg(detail.jsonb) AS detail,
              a.react_to as react_to,
              a.job_profile_id as job_profile_id
            FROM profile_associations a
              INNER JOIN profile_wrappers as masterwrapper ON a.master_wrapper_id = masterwrapper.id
			  INNER JOIN profile_wrappers as detailwrapper ON a.detail_wrapper_id = detailwrapper.id
              INNER JOIN %s master ON master.id = masterwrapper.%s_id
              INNER JOIN %s detail ON detail.id = detailwrapper.%s_id
            GROUP BY a.id, masterwrapper.%s_id, detailwrapper.%s_id, a.master_wrapper_id, a.detail_wrapper_id',
            _master_type, _master_type, _detail_type, _detail_type, _master_table, _master_type, _detail_table, _detail_type, _master_type, _detail_type);
      END $$
language plpgsql;
