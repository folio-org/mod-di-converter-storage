DROP TRIGGER IF EXISTS remove_related_wrappers_job_action
ON ${myuniversity}_${mymodule}.job_to_action_profiles;

DROP FUNCTION IF EXISTS remove_related_wrappers_job_action();

CREATE OR REPLACE FUNCTION remove_related_wrappers_job_match()
RETURNS trigger AS
$$BEGIN
   DELETE FROM ${myuniversity}_${mymodule}.profile_wrappers
   USING  ${myuniversity}_${mymodule}.job_to_match_profiles
   WHERE ${myuniversity}_${mymodule}.profile_wrappers.id = OLD.detailwrapperid;
   RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER OR REPLACE remove_related_wrappers_job_match
   AFTER DELETE ON ${myuniversity}_${mymodule}.job_to_match_profiles
   FOR EACH ROW
   EXECUTE PROCEDURE remove_related_wrappers_job_match();



CREATE OR REPLACE FUNCTION remove_related_wrappers_match_match()
RETURNS trigger AS
$$BEGIN
   DELETE FROM ${myuniversity}_${mymodule}.profile_wrappers
   USING  ${myuniversity}_${mymodule}.match_to_match_profiles
   WHERE ${myuniversity}_${mymodule}.profile_wrappers.id = OLD.detailwrapperid;
   RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER OR REPLACE remove_related_wrappers_match_match
   AFTER DELETE ON ${myuniversity}_${mymodule}.match_to_match_profiles
   FOR EACH ROW
   EXECUTE PROCEDURE remove_related_wrappers_match_match();


DROP TRIGGER IF EXISTS remove_related_wrappers_match_action
ON ${myuniversity}_${mymodule}.match_to_action_profiles;

DROP FUNCTION IF EXISTS remove_related_wrappers_match_action();

DROP TRIGGER IF EXISTS remove_related_wrappers_action_action
ON ${myuniversity}_${mymodule}.action_to_action_profiles;

DROP FUNCTION IF EXISTS remove_related_wrappers_action_action();

CREATE OR REPLACE FUNCTION remove_related_wrappers_action_match()
RETURNS trigger AS
$$BEGIN
   DELETE FROM ${myuniversity}_${mymodule}.profile_wrappers
   USING  ${myuniversity}_${mymodule}.action_to_match_profiles
   WHERE ${myuniversity}_${mymodule}.profile_wrappers.id = OLD.detailwrapperid;
   RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER OR REPLACE remove_related_wrappers_action_match
   AFTER DELETE ON ${myuniversity}_${mymodule}.action_to_match_profiles
   FOR EACH ROW
   EXECUTE PROCEDURE remove_related_wrappers_action_match();



CREATE OR REPLACE FUNCTION remove_related_wrappers_action_mapping()
RETURNS trigger AS
$$BEGIN
   DELETE FROM ${myuniversity}_${mymodule}.profile_wrappers
   USING  ${myuniversity}_${mymodule}.action_to_mapping_profiles
   WHERE ${myuniversity}_${mymodule}.profile_wrappers.id = OLD.detailwrapperid;
   RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER OR REPLACE remove_related_wrappers_action_mapping
   AFTER DELETE ON ${myuniversity}_${mymodule}.action_to_mapping_profiles
   FOR EACH ROW
   EXECUTE PROCEDURE remove_related_wrappers_action_mapping();
