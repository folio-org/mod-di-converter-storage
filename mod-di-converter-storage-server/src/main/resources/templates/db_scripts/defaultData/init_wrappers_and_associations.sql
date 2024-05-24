-- default_delete_marc_authority_job_profile
INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id) values
  ('7248a1a1-8811-4771-b418-c1d16423e2bc', 'JOB_PROFILE', '1a338fcd-3efc-4a03-b007-394eeb0d5fb9') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id) values
  ('ed54fc13-aac0-40d4-b21e-dda1e6f9a03a', 'ACTION_PROFILE', 'fabd9a3e-33c3-49b7-864d-c5af830d9990') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, match_profile_id) values
  ('69de98ea-68dd-46be-a187-a115f9afcc05', 'MATCH_PROFILE', '4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('644e53c2-7be2-4ae5-bc17-131334222d39',
   null,
   '7248a1a1-8811-4771-b418-c1d16423e2bc',
   'ed54fc13-aac0-40d4-b21e-dda1e6f9a03a',
   '1a338fcd-3efc-4a03-b007-394eeb0d5fb9',
   '4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6',
   'JOB_PROFILE',
   'MATCH_PROFILE',
    0,
    null
   ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('e0fd6684-fa34-4493-9048-a9e01c58f782',
   '1a338fcd-3efc-4a03-b007-394eeb0d5fb9',
   '69de98ea-68dd-46be-a187-a115f9afcc05',
   'ed54fc13-aac0-40d4-b21e-dda1e6f9a03a',
   '4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6',
   'fabd9a3e-33c3-49b7-864d-c5af830d9990',
   'MATCH_PROFILE',
   'ACTION_PROFILE',
   0,
   'MATCH'
  ) ON CONFLICT DO NOTHING;

-- default_ecs_instance_and_marc_bib_create_job_profile
INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id) values
  ('b4b102e0-7fb4-4b77-97f1-782927839eb0', 'JOB_PROFILE', '90fd4389-e5a9-4cc5-88cf-1568c0ff7e8b') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id) values
  ('213aadd4-4d7b-42e2-af6b-2591e3db0c2c', 'ACTION_PROFILE', '671a848a-eb4e-49d2-9e01-41c179e789f5') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id) values
  ('e4f143c6-1b7a-4ed9-b0e6-d0b8d5f5ad63', 'MAPPING_PROFILE', 'f8d7e135-3c35-4c60-bb33-0a3cf01e7b94') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('7e1b00ad-eb12-4c27-aae7-3c4b39e97e3d',
   null,
   'b4b102e0-7fb4-4b77-97f1-782927839eb0',
   '213aadd4-4d7b-42e2-af6b-2591e3db0c2c',
    null,
    null,
   'JOB_PROFILE',
   'ACTION_PROFILE',
    0,
    null
   ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('654f9356-8a7f-49fc-b6d2-91b08df15433',
   null,
   '213aadd4-4d7b-42e2-af6b-2591e3db0c2c',
   'e4f143c6-1b7a-4ed9-b0e6-d0b8d5f5ad63',
   null,
   null,
   'ACTION_PROFILE',
   'MAPPING_PROFILE',
   0,
   null
  ) ON CONFLICT DO NOTHING;

-- default_instance_and_marc_bib_create_job_profile
INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id) values
  ('e1f5063c-9f0c-481d-afb7-48beca30cf9a', 'JOB_PROFILE', 'e34d7b92-9b83-11eb-a8b3-0242ac130003') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id) values
  ('de887df7-dc88-4c4b-9f38-3ca736395c59', 'ACTION_PROFILE', 'fa45f3ec-9b83-11eb-a8b3-0242ac130003') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id) values
  ('4d0014e0-5b55-4d55-bdf3-1d60786df115', 'MAPPING_PROFILE', 'bf7b3b86-9b84-11eb-a8b3-0242ac130003') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('d63003ac-9b84-11eb-a8b3-0242ac130003',
   null,
   'e1f5063c-9f0c-481d-afb7-48beca30cf9a',
   'de887df7-dc88-4c4b-9f38-3ca736395c59',
   'e34d7b92-9b83-11eb-a8b3-0242ac130003',
   'fa45f3ec-9b83-11eb-a8b3-0242ac130003',
   'JOB_PROFILE',
   'ACTION_PROFILE',
    0,
    null
   ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('e1151d70-9b84-11eb-a8b3-0242ac130003',
   null,
   'de887df7-dc88-4c4b-9f38-3ca736395c59',
   '4d0014e0-5b55-4d55-bdf3-1d60786df115',
   'fa45f3ec-9b83-11eb-a8b3-0242ac130003',
   'bf7b3b86-9b84-11eb-a8b3-0242ac130003',
   'ACTION_PROFILE',
   'MAPPING_PROFILE',
   0,
   null
  ) ON CONFLICT DO NOTHING;

-- default_marc_authority_job_profile
INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id) values
  ('790f2fce-b2b7-4033-ac92-7b683d30bede', 'JOB_PROFILE', '6eefa4c6-bbf7-4845-ad82-de7fc5abd0e3') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id) values
  ('570476c2-2309-4a66-be93-eef158882f66', 'ACTION_PROFILE', '7915c72e-c6af-4962-969d-403c7238b051') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id) values
  ('b5befbad-caac-4f05-8e94-6a292a1fbe17', 'MAPPING_PROFILE', '6a0ec1de-68eb-4833-bdbf-0741db25c314') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('06914a99-9b58-493d-828b-4ff104ba7e49',
   null,
   '790f2fce-b2b7-4033-ac92-7b683d30bede',
   '570476c2-2309-4a66-be93-eef158882f66',
   '6eefa4c6-bbf7-4845-ad82-de7fc5abd0e3',
   '7915c72e-c6af-4962-969d-403c7238b051',
   'JOB_PROFILE',
   'ACTION_PROFILE',
    0,
    null
   ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('329911fb-2835-476e-b85e-a7fcdc900f87',
   null,
   '570476c2-2309-4a66-be93-eef158882f66',
   'b5befbad-caac-4f05-8e94-6a292a1fbe17',
   '7915c72e-c6af-4962-969d-403c7238b051',
   '6a0ec1de-68eb-4833-bdbf-0741db25c314',
   'ACTION_PROFILE',
   'MAPPING_PROFILE',
   0,
   null
  ) ON CONFLICT DO NOTHING;

-- default_marc_holdings_job_profile
INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id) values
  ('d7ea2be8-7576-401e-9644-2cb941b090f2', 'JOB_PROFILE', '80898dee-449f-44dd-9c8e-37d5eb469b1d') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id) values
  ('68c54cdb-575b-4e06-b196-85f0b9c1d483', 'ACTION_PROFILE', '8aa0b850-9182-4005-8435-340b704b2a19') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id) values
  ('e2bda8d0-1c47-4a6f-a440-ae97b2aaf468', 'MAPPING_PROFILE', '13cf7adf-c7a7-4c2e-838f-14d0ac36ec0a') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('866a90ce-53b2-4b7b-afb5-d3a564e5087e',
   null,
   'd7ea2be8-7576-401e-9644-2cb941b090f2',
   '68c54cdb-575b-4e06-b196-85f0b9c1d483',
   '80898dee-449f-44dd-9c8e-37d5eb469b1d',
   '8aa0b850-9182-4005-8435-340b704b2a19',
   'JOB_PROFILE',
   'ACTION_PROFILE',
    0,
    null
   ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('42f66e86-cacb-479d-aa80-50a200d0b6b6',
   null,
   '68c54cdb-575b-4e06-b196-85f0b9c1d483',
   'e2bda8d0-1c47-4a6f-a440-ae97b2aaf468',
   '8aa0b850-9182-4005-8435-340b704b2a19',
   '13cf7adf-c7a7-4c2e-838f-14d0ac36ec0a',
   'ACTION_PROFILE',
   'MAPPING_PROFILE',
   0,
   null
  ) ON CONFLICT DO NOTHING;

-- default_oclc_job_profile

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id) values
  ('1e9cac23-8160-4e4e-9bca-f18d306bf9ce', 'JOB_PROFILE', 'd0ebb7b0-2f0f-11eb-adc1-0242ac120002') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id) values
  ('16e5da96-06ee-4f3b-9ec4-a50622c3f946', 'ACTION_PROFILE', 'd0ebba8a-2f0f-11eb-adc1-0242ac120002') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id) values
  ('75fe6550-a091-436d-b100-6305bfc63c49', 'MAPPING_PROFILE', 'd0ebbc2e-2f0f-11eb-adc1-0242ac120002') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('d0ebbdbe-2f0f-11eb-adc1-0242ac120002',
   null,
   '1e9cac23-8160-4e4e-9bca-f18d306bf9ce',
   '16e5da96-06ee-4f3b-9ec4-a50622c3f946',
   'd0ebb7b0-2f0f-11eb-adc1-0242ac120002',
   'd0ebba8a-2f0f-11eb-adc1-0242ac120002',
   'JOB_PROFILE',
   'ACTION_PROFILE',
    0,
    null
   ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('d0ebbec2-2f0f-11eb-adc1-0242ac120002',
   null,
   '16e5da96-06ee-4f3b-9ec4-a50622c3f946',
   '75fe6550-a091-436d-b100-6305bfc63c49',
   'd0ebba8a-2f0f-11eb-adc1-0242ac120002',
   'd0ebbc2e-2f0f-11eb-adc1-0242ac120002',
   'ACTION_PROFILE',
   'MAPPING_PROFILE',
   0,
   null
  ) ON CONFLICT DO NOTHING;

-- default_oclc_update_job_profile
INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id) values
  ('94cba6e2-6441-4ae1-8ced-40674883b97f', 'JOB_PROFILE', '91f9b8d6-d80e-4727-9783-73fb53e3c786') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, match_profile_id) values
  ('5c690f8c-824f-4a1a-869c-3eb4e7ce9a87', 'MATCH_PROFILE', 'd27d71ce-8a1e-44c6-acea-96961b5592c6') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, match_profile_id) values
  ('79bd243d-05ad-490a-bb9e-aba7f5a51b2e', 'MATCH_PROFILE', '31dbb554-0826-48ec-a0a4-3c55293d4dee') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id) values
  ('8fc60cdb-d3a1-426e-b7c6-aab593418797', 'ACTION_PROFILE', '6aa8e98b-0d9f-41dd-b26f-15658d07eb52') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id) values
  ('841d32f5-b66f-4cf7-b2da-ef7abe5fd00c', 'MAPPING_PROFILE', 'f90864ef-8030-480f-a43f-8cdd21233252') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id) values
  ('33b6e902-a79a-4afb-b531-faa3ffb0cd07', 'ACTION_PROFILE', 'cddff0e1-233c-47ba-8be5-553c632709d9') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id) values
  ('d5bf86d7-433e-4e13-9ec9-173777120045', 'MAPPING_PROFILE', '862000b9-84ea-4cae-a223-5fc0552f2b42') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('624c99a2-49ba-45b7-b5bf-1f385182a62c',
   null,
   '94cba6e2-6441-4ae1-8ced-40674883b97f',
   '5c690f8c-824f-4a1a-869c-3eb4e7ce9a87',
   '91f9b8d6-d80e-4727-9783-73fb53e3c786',
   'd27d71ce-8a1e-44c6-acea-96961b5592c6',
   'JOB_PROFILE',
   'MATCH_PROFILE',
    0,
    null
   ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('516301b2-d511-4134-9943-377744af007f',
   '91f9b8d6-d80e-4727-9783-73fb53e3c786',
   '5c690f8c-824f-4a1a-869c-3eb4e7ce9a87',
   '8fc60cdb-d3a1-426e-b7c6-aab593418797',
   'd27d71ce-8a1e-44c6-acea-96961b5592c6',
   '6aa8e98b-0d9f-41dd-b26f-15658d07eb52',
   'MATCH_PROFILE',
   'ACTION_PROFILE',
   0,
   'MATCH'
  ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('34a26c5d-d9a6-4a9f-a1f6-b1b5249fb1f7',
   null,
   '8fc60cdb-d3a1-426e-b7c6-aab593418797',
   '841d32f5-b66f-4cf7-b2da-ef7abe5fd00c',
   '6aa8e98b-0d9f-41dd-b26f-15658d07eb52',
   'f90864ef-8030-480f-a43f-8cdd21233252',
   'ACTION_PROFILE',
   'MAPPING_PROFILE',
    0,
    null
   ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('8d1c9e5e-032b-49ff-986e-e84adffc9955',
   '91f9b8d6-d80e-4727-9783-73fb53e3c786',
   '5c690f8c-824f-4a1a-869c-3eb4e7ce9a87',
   '79bd243d-05ad-490a-bb9e-aba7f5a51b2e',
   'd27d71ce-8a1e-44c6-acea-96961b5592c6',
   '31dbb554-0826-48ec-a0a4-3c55293d4dee',
   'MATCH_PROFILE',
   'MATCH_PROFILE',
   0,
   'NON_MATCH'
  ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('3e569bda-c996-45df-a3c0-ad124058b982',
   '91f9b8d6-d80e-4727-9783-73fb53e3c786',
   '79bd243d-05ad-490a-bb9e-aba7f5a51b2e',
   '33b6e902-a79a-4afb-b531-faa3ffb0cd07',
   '31dbb554-0826-48ec-a0a4-3c55293d4dee',
   'cddff0e1-233c-47ba-8be5-553c632709d9',
   'MATCH_PROFILE',
   'ACTION_PROFILE',
    0,
    'MATCH'
   ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('185b86b2-eca1-4a64-885d-f2dab21c0fc0',
   null,
   '33b6e902-a79a-4afb-b531-faa3ffb0cd07',
   'd5bf86d7-433e-4e13-9ec9-173777120045',
   'cddff0e1-233c-47ba-8be5-553c632709d9',
   '862000b9-84ea-4cae-a223-5fc0552f2b42',
   'ACTION_PROFILE',
   'MAPPING_PROFILE',
   0,
   null
  ) ON CONFLICT DO NOTHING;

-- default_qm_authority_create_job_profile
INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id) values
  ('0a416462-f13a-40bd-b6dd-09fdb92971e4', 'JOB_PROFILE', '6eefa4c6-bbf7-4845-ad82-de7fc4abd0e3') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id) values
  ('fea9b307-7121-488e-a09b-e1c9fa670601', 'ACTION_PROFILE', '7915c72e-c7af-4962-969d-403c7238b051') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id) values
  ('1fc9af82-6f1e-4ff2-ab15-97c50c3a8d49', 'MAPPING_PROFILE', '6a0ec1de-68eb-4833-bdbf-0741db85c314') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('06915a99-9b58-493d-828b-4ff104ba7e49',
   null,
   '0a416462-f13a-40bd-b6dd-09fdb92971e4',
   'fea9b307-7121-488e-a09b-e1c9fa670601',
    null,
    null,
   'JOB_PROFILE',
   'ACTION_PROFILE',
    0,
    null
   ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('cfde7532-28e0-4bcc-9d3c-54f8853bcba1',
   null,
   'fea9b307-7121-488e-a09b-e1c9fa670601',
   '1fc9af82-6f1e-4ff2-ab15-97c50c3a8d49',
   null,
   null,
   'ACTION_PROFILE',
   'MAPPING_PROFILE',
   0,
   null
  ) ON CONFLICT DO NOTHING;

-- default_qm_authority_update_job_profile

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id) values
  ('c1fd6476-8dfa-4276-886e-ae2bbd43c3d6', 'JOB_PROFILE', 'c7fcbc40-c4c0-411d-b569-1fc6bc142a92') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, match_profile_id) values
  ('08ba771c-1fee-4240-b532-dca6ee034c82', 'MATCH_PROFILE', 'aff72eae-847c-4a97-b7b9-c1ddb8cdcbbf') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id) values
  ('e0a64315-efa4-4190-b50e-65643b92d722', 'ACTION_PROFILE', 'f0f788c8-2e65-4e3a-9247-e9444eeb7d70') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id) values
  ('716960de-2015-41d2-8c65-0c72aa9bbc3f', 'MAPPING_PROFILE', '041f8ff9-9d17-4436-b305-1033e0879501') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('e78e0445-d2d1-48d5-b023-efbcc9b25103',
   null,
   'c1fd6476-8dfa-4276-886e-ae2bbd43c3d6',
   '08ba771c-1fee-4240-b532-dca6ee034c82',
   'c7fcbc40-c4c0-411d-b569-1fc6bc142a92',
   'aff72eae-847c-4a97-b7b9-c1ddb8cdcbbf',
   'JOB_PROFILE',
   'MATCH_PROFILE',
    0,
    null
   ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('0ae939cf-835a-43b9-83c5-7e0afd49cbca',
   'c7fcbc40-c4c0-411d-b569-1fc6bc142a92',
   '08ba771c-1fee-4240-b532-dca6ee034c82',
   'e0a64315-efa4-4190-b50e-65643b92d722',
   'aff72eae-847c-4a97-b7b9-c1ddb8cdcbbf',
   'f0f788c8-2e65-4e3a-9247-e9444eeb7d70',
   'MATCH_PROFILE',
   'ACTION_PROFILE',
   0,
   'MATCH'
  ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('cfde7532-29e0-4bcc-9d3c-54f8853bcba1',
   null,
   'e0a64315-efa4-4190-b50e-65643b92d722',
   '716960de-2015-41d2-8c65-0c72aa9bbc3f',

   'f0f788c8-2e65-4e3a-9247-e9444eeb7d70',
   '041f8ff9-9d17-4436-b305-1033e0879501',
   'ACTION_PROFILE',
   'MAPPING_PROFILE',
   0,
   null
  ) ON CONFLICT DO NOTHING;

-- default_qm_holdings_and_srs_marc_holdings_create_job_profile
INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id) values
  ('5d4e95e0-f296-431f-b11f-0b9869e077fa', 'JOB_PROFILE', 'fa0262c7-5816-48d0-b9b3-7b7a862a5bc7') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id) values
  ('4bf02d3a-2dcb-4a75-adb6-8f6538f3fd4d', 'ACTION_PROFILE', 'f5feddba-f892-4fad-b702-e4e77f04f9a3') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id) values
  ('4adf6d51-fde9-4b0d-97b2-09ff99576861', 'MAPPING_PROFILE', 'e0fbaad5-10c0-40d5-9228-498b351dbbaa') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('adbe1e5c-7796-4902-b18e-794b1d58caac',
   null,
   '5d4e95e0-f296-431f-b11f-0b9869e077fa',
   '4bf02d3a-2dcb-4a75-adb6-8f6538f3fd4d',
   'fa0262c7-5816-48d0-b9b3-7b7a862a5bc7',
   'f5feddba-f892-4fad-b702-e4e77f04f9a3',
   'JOB_PROFILE',
   'ACTION_PROFILE',
    0,
    null
   ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('3c73fa82-97bb-4960-aa6b-e4c8f230bcdc',
   null,
   '4bf02d3a-2dcb-4a75-adb6-8f6538f3fd4d',
   '4adf6d51-fde9-4b0d-97b2-09ff99576861',
   'f5feddba-f892-4fad-b702-e4e77f04f9a3',
   'e0fbaad5-10c0-40d5-9228-498b351dbbaa',
   'ACTION_PROFILE',
   'MAPPING_PROFILE',
   0,
   null
  ) ON CONFLICT DO NOTHING;

-- default_qm_holdings_update_job_profile

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id) values
  ('e3c6c549-049f-4448-be35-49e7bbfb40a6', 'JOB_PROFILE', '6cb347c6-c0b0-4363-89fc-32cedede87ba') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, match_profile_id) values
  ('f31b5e40-0187-4fd9-8558-d728363be43b', 'MATCH_PROFILE', '2a599369-817f-4fe8-bae2-f3e3987990fe') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id) values
  ('0dd70479-bc19-4dc1-9e64-114e6b1ade45', 'ACTION_PROFILE', '7e24a466-349b-451d-a18e-38fb21d71b38') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id) values
  ('63a2fb7e-d9fb-46c4-b204-25c327c4a2ed', 'MAPPING_PROFILE', 'b8a9ca7d-4a33-44d3-86e1-f7c6cb7b265f') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('364ab86a-e11c-4dd2-9ad5-efadbe79347b',
   null,
   'e3c6c549-049f-4448-be35-49e7bbfb40a6',
   'f31b5e40-0187-4fd9-8558-d728363be43b',
   '6cb347c6-c0b0-4363-89fc-32cedede87ba',
   '2a599369-817f-4fe8-bae2-f3e3987990fe',
   'JOB_PROFILE',
   'MATCH_PROFILE',
    0,
    null
   ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('31a69e89-4872-435c-b593-15664146cc2b',
   '6cb347c6-c0b0-4363-89fc-32cedede87ba',
   'f31b5e40-0187-4fd9-8558-d728363be43b',
   '0dd70479-bc19-4dc1-9e64-114e6b1ade45',
   '2a599369-817f-4fe8-bae2-f3e3987990fe',
   '7e24a466-349b-451d-a18e-38fb21d71b38',
   'MATCH_PROFILE',
   'ACTION_PROFILE',
   0,
   'MATCH'
  ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('96876af2-2a2b-40c0-9ffd-9538a5b39dd6',
   null,
   '0dd70479-bc19-4dc1-9e64-114e6b1ade45',
   '63a2fb7e-d9fb-46c4-b204-25c327c4a2ed',
   '7e24a466-349b-451d-a18e-38fb21d71b38',
   'b8a9ca7d-4a33-44d3-86e1-f7c6cb7b265f',
   'ACTION_PROFILE',
   'MAPPING_PROFILE',
   0,
   null
  ) ON CONFLICT DO NOTHING;

-- default_qm_instance_and_srs_marc_bib_create_job_profile

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id) values
  ('8d802fef-b077-4eff-8997-a22edbbdd901', 'JOB_PROFILE', '6409dcff-71fa-433a-bc6a-e70ad38a9604') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id) values
  ('1591ec40-579c-4dde-9692-1b0b779905c1', 'ACTION_PROFILE', 'f8e58651-f651-485d-aead-d2fa8700e2d1') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id) values
  ('48fde3e4-f3bf-45ab-bcc5-55cd0e3c4b00', 'MAPPING_PROFILE', '991c0300-44a6-47e3-8ea2-b01bb56a38cc') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('b168efb3-1443-400b-9bc6-bc7dc2d3050a',
   null,
   '8d802fef-b077-4eff-8997-a22edbbdd901',
   '1591ec40-579c-4dde-9692-1b0b779905c1',
   '6409dcff-71fa-433a-bc6a-e70ad38a9604',
   'f8e58651-f651-485d-aead-d2fa8700e2d1',
   'JOB_PROFILE',
   'ACTION_PROFILE',
    0,
    null
   ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('85375360-9430-4bb1-a64a-197aee7c9400',
   null,
   '1591ec40-579c-4dde-9692-1b0b779905c1',
   '48fde3e4-f3bf-45ab-bcc5-55cd0e3c4b00',
   'f8e58651-f651-485d-aead-d2fa8700e2d1',
   '991c0300-44a6-47e3-8ea2-b01bb56a38cc',
   'ACTION_PROFILE',
   'MAPPING_PROFILE',
   0,
   null
  ) ON CONFLICT DO NOTHING;

-- default_qm_marc_bib_update_job_profile
INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id) values
  ('56b866e8-3dff-4eb1-827e-b8f9bec8219c', 'JOB_PROFILE', 'cf6f2718-5aa5-482a-bba5-5bc9b75614da') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, match_profile_id) values
  ('a3c8c9fb-0dc2-4fff-a968-cffc14119f4a', 'MATCH_PROFILE', '91cec42a-260d-4a8c-a9fb-90d9435ca2f4') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id) values
  ('71dbaa0b-aeb0-4a06-9b65-2d7987087f46', 'ACTION_PROFILE', 'c2e2d482-9486-476e-a28c-8f1e303cbe1a') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id) values
  ('069e1890-408b-4b49-a121-db5e33ec375f', 'MAPPING_PROFILE', '39b265e1-c963-4e5f-859d-6e8c327a265c') ON CONFLICT DO NOTHING;


INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('83477fa3-1db1-4088-af0e-3c5fccb7e337',
   null,
   '56b866e8-3dff-4eb1-827e-b8f9bec8219c',
   'a3c8c9fb-0dc2-4fff-a968-cffc14119f4a',
   'cf6f2718-5aa5-482a-bba5-5bc9b75614da',
   '91cec42a-260d-4a8c-a9fb-90d9435ca2f4',
   'JOB_PROFILE',
   'MATCH_PROFILE',
    0,
    null
   ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('e5f754a0-1b44-487c-b037-bb68eebba383',
   'cf6f2718-5aa5-482a-bba5-5bc9b75614da',
   'a3c8c9fb-0dc2-4fff-a968-cffc14119f4a',
   '71dbaa0b-aeb0-4a06-9b65-2d7987087f46',
   '91cec42a-260d-4a8c-a9fb-90d9435ca2f4',
   'c2e2d482-9486-476e-a28c-8f1e303cbe1a',
   'MATCH_PROFILE',
   'ACTION_PROFILE',
   0,
   'MATCH'
  ) ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.associations (id, job_profile_id, master_wrapper_id,
    detail_wrapper_id, master_profile_id, detail_profile_id,
    master_profile_type, detail_profile_type, detail_order, react_to) values
  ('4e2bf7bf-dee1-4e7a-9074-e2139ef3f031',
   null,
   '71dbaa0b-aeb0-4a06-9b65-2d7987087f46',
   '069e1890-408b-4b49-a121-db5e33ec375f',
   'c2e2d482-9486-476e-a28c-8f1e303cbe1a',
   '39b265e1-c963-4e5f-859d-6e8c327a265c',
   'ACTION_PROFILE',
   'MAPPING_PROFILE',
   0,
   null
  ) ON CONFLICT DO NOTHING;



