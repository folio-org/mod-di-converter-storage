-- Custom script to create get_profile_snapshot function. Changes in this file will not result in an update of the function.
-- To change the get_profile_snapshot function, update this script and copy it to the appropriate scripts.snippet field of the schema.json

CREATE OR REPLACE FUNCTION get_profile_snapshot(profileId uuid, profile_type text, profile_table text, jobProfileId text)
RETURNS TABLE(snapshot json)
  AS $$
    BEGIN
      RETURN query
        EXECUTE format('
          WITH RECURSIVE recursive_snapshot AS (
              SELECT
                profile.id AS association_id,
                CAST(NULL AS uuid) AS master_id,
                profile.id AS detail_id,
				        CAST(NULL AS uuid) AS masterwrapperid,
                pw.id AS detailwrapperid,
                ''%s'' detail_type,
                null AS master_type,
                0 AS detail_order,
                json_agg(profile.jsonb) detail,
                null AS react_to
              FROM %s AS profile LEFT JOIN profile_wrappers pw ON pw.%s_id = profile.id AND ''%s'' != ''MATCH_PROFILE''
              WHERE profile.id = ''%s''
              GROUP BY profile.id, pw.id
                UNION ALL
              SELECT
                associations_view.association_id,
                associations_view.master_id AS master_id,
                associations_view.detail_id AS detail_id,
                associations_view.masterwrapperid AS masterwrapperid,
                associations_view.detailwrapperid AS detailwrapperid,
                associations_view.detail_type AS detail_type,
                associations_view.master_type AS master_type,
                associations_view.detail_order AS detail_order,
                associations_view.detail AS detail,
                associations_view.react_to AS react_to
              FROM associations_view INNER JOIN recursive_snapshot ON associations_view.masterwrapperid = recursive_snapshot.detailwrapperid
                AND CASE WHEN associations_view.master_type = ''MATCH_PROFILE'' AND ''%s'' != ''null'' THEN associations_view.job_profile_id = NULLIF(''%s'',''null'')::uuid
                         ELSE associations_view.job_profile_id IS NULL END)
          SELECT row_to_json(row) FROM recursive_snapshot row ORDER BY row.detail_order ASC',
          profile_type, profile_table, profile_type, profile_type, profileId, jobProfileId, jobProfileId);
    END $$
language plpgsql;
