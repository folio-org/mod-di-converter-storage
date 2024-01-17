INSERT INTO ${myuniversity}_${mymodule}.job_profiles (id, jsonb) values
  ('6eefa4c6-bbf7-4845-ad82-de7fc4abd0e3', '{
	"id": "6eefa4c6-bbf7-4845-ad82-de7fc4abd0e3",
	"name": "quickMARC - Default Create authority",
	"description": "This job profile is used by the quickMARC to allow a user to create an SRS MARC authority record and corresponding Inventory authority. Profile cannot be edited or deleted",
	"deleted": false,
	"hidden": true,
	"dataType": "MARC",
	"metadata": {
		"createdDate": "2022-04-19T09:07:47.667",
		"updatedDate": "2022-04-19T09:09:10.382+0000",
		"createdByUserId": "6a010e5b-5421-5b1c-9b52-568b37038575",
		"updatedByUserId": "6a010e5b-5421-5b1c-9b52-568b37038575"
	},
	"userInfo": {
		"lastName": "System",
		"userName": "System",
		"firstName": "System"
	},
	"childProfiles": [],
	"parentProfiles": []
}
') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.action_profiles (id, jsonb) values
  ('7915c72e-c7af-4962-969d-403c7238b051',
   '{
     "id": "7915c72e-c7af-4962-969d-403c7238b051",
     "name": "quickMARC - Default Create authority",
     "action": "CREATE",
     "deleted": false,
     "hidden": true,
     "remove9Subfields": false,
     "metadata": {
       "createdDate": "2020-11-30T09:02:39.96",
       "updatedDate": "2020-11-30T11:57:24.083+0000",
       "createdByUserId": "6a010e5b-5421-5b1c-9b52-568b37038575",
       "updatedByUserId": "6a010e5b-5421-5b1c-9b52-568b37038575"
     },
     "userInfo": {
       "lastName": "System",
       "userName": "System",
       "firstName": "System"
     },
     "description": "This action profile is used for creating Inventory Authorities and SRS MARC Authorities records. It can be edited, duplicated.",
     "folioRecord": "AUTHORITY",
     "childProfiles": [],
     "parentProfiles": []
   }
   ') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.mapping_profiles (id, jsonb) values
  ('6a0ec1de-68eb-4833-bdbf-0741db85c314', '{
  "id": "6a0ec1de-68eb-4833-bdbf-0741db85c314",
  "name": "Default - Create authorities",
  "deleted": false,
  "metadata": {
    "createdDate": "2021-10-08T14:00:00.000",
    "updatedDate": "2021-10-08T15:00:00.462+0000",
    "createdByUserId": "00000000-0000-0000-0000-000000000000",
    "updatedByUserId": "00000000-0000-0000-0000-000000000000"
  },
  "userInfo": {
    "lastName": "System",
    "userName": "System",
    "firstName": "System"
  },
  "description": "This field mapping profile is used with FOLIO''s default job profile for creating Inventory Authorities and SRS MARC Authorities records. It can be edited, duplicated, deleted, or linked to additional action profiles.",
  "childProfiles": [],
  "mappingDetails": {
    "name": "authority",
    "recordType": "AUTHORITY",
    "mappingFields": [
      {
        "name": "personalName",
        "path": "authority.personalName",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "sftPersonalName",
        "path": "authority.sftPersonalName[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "saftPersonalName",
        "path": "authority.saftPersonalName[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "corporateName",
        "path": "authority.corporateName",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "sftCorporateName",
        "path": "authority.sftCorporateName[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "saftCorporateName",
        "path": "authority.saftCorporateName[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "meetingName",
        "path": "authority.meetingName[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      },  {
        "name": "sftMeetingName",
        "path": "authority.sftMeetingName[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "saftMeetingName",
        "path": "authority.saftMeetingName[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "uniformTitle",
        "path": "authority.uniformTitle",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "sftUniformTitle",
        "path": "authority.sftUniformTitle[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "saftUniformTitle",
        "path": "authority.saftUniformTitle[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "topicalTerm",
        "path": "authority.topicalTerm",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "sftTopicalTerm",
        "path": "authority.sftTopicalTerm[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "saftTopicalTerm",
        "path": "authority.saftTopicalTerm[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "subjectHeadings",
        "path": "authority.subjectHeadings",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "geographicName",
        "path": "authority.geographicName",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "sftGeographicTerm",
        "path": "authority.sftGeographicTerm[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "saftGeographicTerm",
        "path": "authority.saftGeographicTerm[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "genre",
        "path": "authority.genre",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "identifiers",
        "path": "authority.identifiers[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }, {
        "name": "notes",
        "path": "authority.notes[]",
        "value": "",
        "enabled": "false",
        "subfields": []
      }
    ],
    "marcMappingDetails": []
  },
  "parentProfiles": [],
  "existingRecordType": "AUTHORITY",
  "incomingRecordType": "MARC_AUTHORITY",
  "marcFieldProtectionSettings": []
}')
  ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, job_profile_id) values
  ('0a416462-f13a-40bd-b6dd-09fdb92971e4', 'JOB_PROFILE', '6eefa4c6-bbf7-4845-ad82-de7fc4abd0e3') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, action_profile_id) values
  ('fea9b307-7121-488e-a09b-e1c9fa670601', 'ACTION_PROFILE', '7915c72e-c7af-4962-969d-403c7238b051') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.profile_wrappers (id, profile_type, mapping_profile_id) values
  ('1fc9af82-6f1e-4ff2-ab15-97c50c3a8d49', 'MAPPING_PROFILE', '6a0ec1de-68eb-4833-bdbf-0741db85c314') ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.job_to_action_profiles (id, jsonb) values
  ('06915a99-9b58-493d-828b-4ff104ba7e49', '{
  "id": "06915a99-9b58-493d-828b-4ff104ba7e49",
  "order": 0,
  "triggered": false,
  "detailWrapperId": "fea9b307-7121-488e-a09b-e1c9fa670601",
  "masterWrapperId": "0a416462-f13a-40bd-b6dd-09fdb92971e4",
  "detailProfileType": "ACTION_PROFILE",
  "masterProfileType": "JOB_PROFILE"
}')
  ON CONFLICT DO NOTHING;

INSERT INTO ${myuniversity}_${mymodule}.action_to_mapping_profiles (id, jsonb) values
  ('cfde7532-28e0-4bcc-9d3c-54f8853bcba1',
   '{
     "id": "cfde7532-28e0-4bcc-9d3c-54f8853bcba1",
     "order": 0,
     "triggered": false,
     "detailWrapperId": "1fc9af82-6f1e-4ff2-ab15-97c50c3a8d49",
     "masterWrapperId": "fea9b307-7121-488e-a09b-e1c9fa670601",
     "detailProfileType": "MAPPING_PROFILE",
     "masterProfileType": "ACTION_PROFILE"
   }') ON CONFLICT DO NOTHING;
