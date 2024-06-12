-- create general association table
CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.profile_associations
(
    id uuid NOT NULL,
    job_profile_id uuid,
    master_wrapper_id uuid,
    detail_wrapper_id uuid,
    master_profile_id uuid,
    detail_profile_id uuid,
    master_profile_type text,
    detail_profile_type text,
    detail_order integer,
    react_to text,
    CONSTRAINT profile_associations_pkey PRIMARY KEY (id),
    CONSTRAINT detail_wrapper_id_profile_wrappers_fkey FOREIGN KEY (detail_wrapper_id)
        REFERENCES ${myuniversity}_${mymodule}.profile_wrappers (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE,
    CONSTRAINT master_wrapper_id_profile_wrappers_fkey FOREIGN KEY (master_wrapper_id)
        REFERENCES ${myuniversity}_${mymodule}.profile_wrappers (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);

-- create indexes
CREATE INDEX IF NOT EXISTS profile_associations_detail_wrapper_id ON ${myuniversity}_${mymodule}.profile_associations USING hash (detail_wrapper_id);

CREATE INDEX IF NOT EXISTS profile_associations_master_wrapper_id ON ${myuniversity}_${mymodule}.profile_associations USING hash (master_wrapper_id);

CREATE INDEX IF NOT EXISTS profile_associations_job_profile_id ON ${myuniversity}_${mymodule}.profile_associations USING hash (job_profile_id)

-- create trigger for removing record from profile_wrappers
CREATE OR REPLACE FUNCTION remove_related_wrappers_profile_associations()
RETURNS trigger AS
$$
BEGIN
    IF OLD.master_profile_type = 'ACTION_PROFILE' AND OLD.detail_profile_type IN ('ACTION_PROFILE', 'MAPPING_PROFILE', 'MATCH_PROFILE') OR
           OLD.master_profile_type IN ('JOB_PROFILE', 'MATCH_PROFILE') AND OLD.detail_profile_type = 'MATCH_PROFILE' THEN
            DELETE FROM ${myuniversity}_${mymodule}.profile_wrappers
            WHERE id = OLD.detail_wrapper_id;
        END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS remove_related_wrappers_profile_associations
ON ${myuniversity}_${mymodule}.profile_associations;

CREATE TRIGGER remove_related_wrappers_profile_associations
    AFTER DELETE
    ON ${myuniversity}_${mymodule}.profile_associations
    FOR EACH ROW
    EXECUTE FUNCTION ${myuniversity}_${mymodule}.remove_related_wrappers_profile_associations();

