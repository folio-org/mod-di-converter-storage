CREATE TABLE ${myuniversity}_${mymodule}.profiles_type (
  id uuid NOT NULL,
  "jsonb" jsonb NOT NULL,
  CONSTRAINT profiles_type_pk PRIMARY KEY (id));

INSERT INTO ${myuniversity}_${mymodule}.profiles_type (id, jsonb) values
  ('8d8ad2f4-5646-4f4c-ba85-4e4d57b695a7', '{
  "id": "8d8ad2f4-5646-4f4c-ba85-4e4d57b695a7",
  "name": "ACTION_PROFILE",
  "source": "folio"
}') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profiles_type (id, jsonb) values
  ('53fa6e4b-8f8d-4423-aabc-7a05e55b2399', '{
    "id": "53fa6e4b-8f8d-4423-aabc-7a05e55b2399",
    "name": "MAPPING_PROFILE",
    "source": "folio"
  }') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profiles_type (id, jsonb) values
  ('a3d38a9c-1e2a-4a6d-8fe2-6dc5a29a92d7', '{
    "id": "a3d38a9c-1e2a-4a6d-8fe2-6dc5a29a92d7",
    "name": "MATCH_PROFILE",
    "source": "folio"
  }') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profiles_type (id, jsonb) values
  ('f72c8278-73ed-4c4a-a5f6-8d6488c2f779', '{
    "id": "f72c8278-73ed-4c4a-a5f6-8d6488c2f779",
    "name": "JOB_PROFILE",
    "source": "folio"
  }') ON CONFLICT DO NOTHING;
