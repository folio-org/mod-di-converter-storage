
/*
 Migration will start automatically when profile_wrappers table is empty.
 This function cleans this table: removes FKeys, disables triggers,
 truncates data, enables triggers and restores FKeys.
 */
DO $$
DECLARE
r record;
BEGIN
  RAISE NOTICE '===';
  RAISE NOTICE 'Preparing mod_di_converter_storage for migration.';
  DROP TABLE IF EXISTS foreign_keys;

  RAISE NOTICE 'Find FKeys for deletion.';
  CREATE TEMP TABLE foreign_keys AS
    SELECT conrelid :: regclass AS table_name, conname AS foreign_key
    FROM pg_constraint
    WHERE connamespace = (SELECT current_setting('SEARCH_PATH')) :: regnamespace
        AND contype = 'f' AND conrelid :: regclass :: text like '%_to_%'
    ORDER BY conrelid :: regclass :: text, contype desc;

  RAISE NOTICE 'Removing FKeys:';
  FOR r in
  SELECT table_name, foreign_key
  FROM foreign_keys
  LOOP
    RAISE NOTICE ' Delete FKey:: % : %', r.table_name, r.foreign_key;
    EXECUTE 'ALTER TABLE ' || r.table_name || ' DROP CONSTRAINT ' || r.foreign_key || ';';
  END LOOP;
  RAISE NOTICE 'FKeys were removed.';

  RAISE NOTICE 'Disable triggers.';
  SET session_replication_role = replica;

  RAISE NOTICE 'Removing old associations.';
  FOR r in
  SELECT distinct table_name
  FROM foreign_keys
  LOOP
    EXECUTE 'UPDATE ' || r.table_name || ' SET masterwrapperid=null, detailwrapperid=null;';
  END LOOP;

  RAISE NOTICE 'Removing new associations.';
  TRUNCATE associations CASCADE;

  RAISE NOTICE 'Removing old wrappers.';
  TRUNCATE profile_wrappers;

  RAISE NOTICE 'Enabling triggers back.';
  SET session_replication_role = DEFAULT;

  RAISE NOTICE 'Creating FKeys back:';
  FOR r in
  SELECT table_name, foreign_key
  FROM foreign_keys
  LOOP
    RAISE NOTICE ' Create FKey:: % : %', r.table_name, r.foreign_key;
    EXECUTE 'ALTER TABLE ' || r.table_name || ' ADD CONSTRAINT ' || r.foreign_key || ' FOREIGN KEY (' ||
      left(r.foreign_key, strpos(r.foreign_key, '_') -1)|| ') REFERENCES profile_wrappers(id) ON DELETE CASCADE;';
  END LOOP;

  DROP TABLE IF EXISTS foreign_keys;
  RAISE NOTICE 'DB ready for migration.';
END $$;
