-- create general association table
CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.profile_associations
(
    id uuid NOT NULL,
    job_profile_id uuid,
    master_wrapper_id uuid,
    detail_wrapper_id uuid,
    master_profile_id uuid,
    detail_profile_id uuid,
    master_profile_type text COLLATE pg_catalog."default",
    detail_profile_type text COLLATE pg_catalog."default",
    detail_order integer,
    react_to text COLLATE pg_catalog."default",
    CONSTRAINT profile_associations_pkey PRIMARY KEY (id),
    CONSTRAINT detail_wrapper_id_profile_wrappers_fkey FOREIGN KEY (detail_wrapper_id)
        REFERENCES ${myuniversity}_${mymodule}.profile_wrappers (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE,
    CONSTRAINT master_wrapper_id_profile_wrappers_fkey FOREIGN KEY (master_wrapper_id)
        REFERENCES ${myuniversity}_${mymodule}.profile_wrappers (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
)

-- create indexes
CREATE INDEX IF NOT EXISTS profile_associations_detailwrapperid_idx
    ON ${myuniversity}_${mymodule}.profile_associations USING btree
    (detail_wrapper_id ASC NULLS LAST)
    TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS profile_associations_masterwrapperid_idx
    ON ${myuniversity}_${mymodule}.profile_associations USING btree
    (master_wrapper_id ASC NULLS LAST)
    TABLESPACE pg_default;

-- create trigger for removing record from profile_wrappers
CREATE OR REPLACE FUNCTION remove_related_wrappers_profile_associations()
RETURNS trigger AS
$$
BEGIN
   DELETE FROM ${myuniversity}_${mymodule}.profile_wrappers
   USING  ${myuniversity}_${mymodule}.profile_associations
   WHERE ${myuniversity}_${mymodule}.profile_wrappers.id = OLD.detail_wrapper_id;
   RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS remove_related_wrappers_profile_associations
ON ${myuniversity}_${mymodule}.profile_associations;

CREATE TRIGGER remove_related_wrappers_profile_associations
    AFTER DELETE
    ON ${myuniversity}_${mymodule}.profile_associations
    FOR EACH ROW
    EXECUTE PROCEDURE ${myuniversity}_${mymodule}.remove_related_wrappers_profile_associations();

