CREATE TABLE ${myuniversity}_${mymodule}.profile_wrappers (
  id uuid NOT NULL,
  profile_type uuid NULL,
  profile_id uuid NOT NULL,
  CONSTRAINT profile_wrappers_pk PRIMARY KEY (id),
  CONSTRAINT profile_wrappers_fk FOREIGN KEY (profile_type) REFERENCES ${myuniversity}_${mymodule}.profiles_type(id)
);
