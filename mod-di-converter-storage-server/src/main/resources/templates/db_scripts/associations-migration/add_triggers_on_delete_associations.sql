CREATE OR REPLACE FUNCTION remove_related_wrappers_job_match()
RETURNS trigger AS
$$BEGIN
   DELETE FROM ${myuniversity}_${mymodule}.profile_wrappers
   USING  ${myuniversity}_${mymodule}.job_to_match_profiles
   WHERE ${myuniversity}_${mymodule}.profile_wrappers.id = OLD.detailwrapperid;
   RETURN OLD;
END;
$$ LANGUAGE plpgsql;


DROP TRIGGER IF EXISTS remove_related_wrappers_job_match
ON ${myuniversity}_${mymodule}.job_to_match_profiles;

CREATE TRIGGER  remove_related_wrappers_job_match
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

DROP TRIGGER IF EXISTS remove_related_wrappers_match_match
ON ${myuniversity}_${mymodule}.match_to_match_profiles;

CREATE TRIGGER  remove_related_wrappers_match_match
   AFTER DELETE ON ${myuniversity}_${mymodule}.match_to_match_profiles
   FOR EACH ROW
   EXECUTE PROCEDURE remove_related_wrappers_match_match();

CREATE OR REPLACE FUNCTION remove_related_wrappers_action_match()
RETURNS trigger AS
$$BEGIN
   DELETE FROM ${myuniversity}_${mymodule}.profile_wrappers
   USING  ${myuniversity}_${mymodule}.action_to_match_profiles
   WHERE ${myuniversity}_${mymodule}.profile_wrappers.id = OLD.detailwrapperid;
   RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS remove_related_wrappers_action_match
ON ${myuniversity}_${mymodule}.action_to_match_profiles;

CREATE TRIGGER remove_related_wrappers_action_match
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

DROP TRIGGER IF EXISTS remove_related_wrappers_action_mapping
ON ${myuniversity}_${mymodule}.action_to_mapping_profiles;

CREATE TRIGGER  remove_related_wrappers_action_mapping
   AFTER DELETE ON ${myuniversity}_${mymodule}.action_to_mapping_profiles
   FOR EACH ROW
   EXECUTE PROCEDURE remove_related_wrappers_action_mapping();
