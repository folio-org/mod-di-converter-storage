## 2025-03-13 2.4.0
* [MODDATAIMP-1102](https://folio-org.atlassian.net/browse/MODDATAIMP-1102) Empty contributor and vendor reference properties created in order when no values in incoming record
* [MODDICONV-409](https://folio-org.atlassian.net/browse/MODDICONV-409) Update to Java 21 mod-di-converter-storage Sunflower R1 2025

## 2024-10-29 2.3.0
* [MODDICONV-312](https://folio-org.atlassian.net/browse/MODDICONV-312) Create general associations table
* [MODDICONV-373](https://folio-org.atlassian.net/browse/MODDICONV-373) Disallow create/update of a job profile without actions
* [MODDICONV-391](https://issues.folio.org/browse/MODDICONV-391) Create migration script for Order mapping profiles
* [MODDICONV-399](https://folio-org.atlassian.net/browse/MODDICONV-399) Upgrade Spring from 5.3.23 to 6.1.13
* [MODDICONV-393](https://folio-org.atlassian.net/browse/MODDICONV-393) Fix inconsistencies in permission namings
* [MODDICONV-396](https://folio-org.atlassian.net/browse/MODDICONV-396) Remove accepted values from Instance, Holdings, Items and Orders mapping profiles
* [MODDICONV-403](https://folio-org.atlassian.net/browse/MODDICONV-403) mod-di-converter-storage Ramsons 2024 R2 - RMB v35.3.x update
* [MODDICONV-374](https://folio-org.atlassian.net/browse/MODDICONV-374) Validate Job Profiles with Modify action at create
* [MODDICONV-380](https://folio-org.atlassian.net/browse/MODDICONV-380) Validate Job Profile with Modify action at Update
* [MODDICONV-388](https://folio-org.atlassian.net/browse/MODDICONV-388) Allow hard delete of Data Import Profiles
* [MODDICONV-389](https://folio-org.atlassian.net/browse/MODDICONV-389) Remove deprecated default job profile 'Create MARC Bibs'
* [MODDICONV-390](https://folio-org.atlassian.net/browse/MODDICONV-390) Move data import profile validation to service layer
* [MODDICONV-286](https://folio-org.atlassian.net/browse/MODDICONV-286) Allow Job Profiles To Be Imported
* [MODDICONV-392](https://folio-org.atlassian.net/browse/MODDICONV-392) Overlay existing profile during job profile import

## 2024-03-20 2.2.0
* [MODDICORE-398](https://folio-org.atlassian.net/browse/MODDICORE-398) Upgrade mod-di-converter-storage to RMB 35.2.0, Vert.x 4.5.4
* [MODDICONV-334](https://folio-org.atlassian.net/browse/MODDICONV-334) Disallow linking MARC Update action to a MARC Modify
* [MODDICONV-367](https://folio-org.atlassian.net/browse/MODDICONV-367) Disallow action profile creation for MARCbib record with 'Create' action type
* [MODDICONV-294](https://folio-org.atlassian.net/browse/MODDICONV-294) Disallow creation of JobProfile containing Update without Match
* [MODDICONV-305](https://issues.folio.org/browse/MODDICONV-305) Remove "permissions" interface dependency
* [MODDICONV-364](https://folio-org.atlassian.net/browse/MODDICONV-364) Adjust default QM Authority Create profile to new format
* [MODDICONV-363](https://folio-org.atlassian.net/browse/MODDICONV-363) Add migration script to provide mapping data for "accountNo" field
* [MODDICONV-353](https://folio-org.atlassian.net/browse/MODDICONV-353) Create new default profile for create Authority record

## 2023-10-12 2.1.0
* [MODDICONV-345](https://issues.folio.org/browse/MODDICONV-345) Set wrapper ids at jsonb for migrated relations
* [MODDICONV-313](https://issues.folio.org/browse/MODDICONV-313) Upgrade mod-di-converter-storage to Java 17
* [MODDICONV-300](https://issues.folio.org/browse/MODDICONV-300) Match and action profiles cannot be re-used in an import job profile - short term fix
* [MODDICONV-310](https://issues.folio.org/browse/MODDICONV-310) Add wrappers around profiles to build associations.
* [MODDICONV-297](https://issues.folio.org/browse/MODDICONV-297) Add migration script for profiles with mapping for required subfields.
* [MODDICONV-307](https://issues.folio.org/browse/MODDICONV-307) Populate "value" with empty string if this field is absent in match profile incoming records.

## 2022-02-14 v2.0.0
* [MODDICONV-259](https://issues.folio.org/browse/MODDICONV-259) Rename mod-data-import-converter-storage module to mod-di-converter-storage
* [MODDICONV-271](https://issues.folio.org/browse/MODDICONV-271) Logging improvement - Configuration
* [MODDATAIMP-736](https://issues.folio.org/browse/MODDATAIMP-736) Adjust logging configuration in all DI modules to display datetime in a proper format
* [MODDICONV-292](https://issues.folio.org/browse/MODDICONV-292) Add validation of Folio Record Type upon linking of Action and Mapping profile
* [MODDICONV-281](https://issues.folio.org/browse/MODDICONV-281) Enable ability to edit Job profile: Default - Create SRS MARC Authority
* [MODDICONV-282](https://issues.folio.org/browse/MODDICONV-282) Change Job profile: Default - Create SRS MARC Authority description
* [MODDICONV-233](https://issues.folio.org/browse/MODDICONV-233) Logging improvement
* [MODDATAIMP-758](https://issues.folio.org/browse/MODDATAIMP-758) Improve logging (hide SQL requests)
* [MODDICONV-291](https://issues.folio.org/browse/MODDICONV-291) Fixed unauthorized error after upgrade from Nolana to Orchid snapshot version
* [MODDATAIMP-750](https://issues.folio.org/browse/MODDATAIMP-750) Update dependencies

## 2022-10-19 v1.15.0
* [MODDICONV-260](https://issues.folio.org/browse/MODDICONV-260) spring-beans 5.3.20, Vert.x 4.3.3 fixing vulns
* [MODDICONV-258](https://issues.folio.org/browse/MODDICONV-258) Apostrophe not escaped in input when creating field mapping profile (possible SQL injection)
* [MODDICONV-254](https://issues.folio.org/browse/MODDICONV-254) Cache Job Profile Snapshot of Data Import Job
* [MODDICONV-267](https://issues.folio.org/browse/MODDICONV-267) Upgrade to RMB v35.0.1
* [MODDICONV-269](https://issues.folio.org/browse/MODDICONV-269) Change registration module in tests

## 2022-07-14 v1.14.1
* [MODDICONV-252](https://issues.folio.org/browse/MODDICONV-252) Correct date in a script for creation of default job profile

## 2022-06-28 v1.14.0
* [MODDICONV-210](https://issues.folio.org/browse/MODDICONV-210) Updated associations_view to fetch and display indirect relationships between job and action profiles
* [MODDICONV-235](https://issues.folio.org/browse/MODDICONV-235) Adding a tag to some profiles displays an error message and a success message
* [MODDICONV-244](https://issues.folio.org/browse/MODDICONV-244) Created default update profiles for quickMarc
* [MODDICONV-228](https://issues.folio.org/browse/MODDICONV-228) Delete Authority: Create default job profile

## 2022-02-24 v1.13.0
* [MODDICONV-211](https://issues.folio.org/browse/MODDICONV-211) Settings : Updates to default job profiles
* [MODDICONV-215](https://issues.folio.org/browse/MODDICONV-215) Upgrade to RMB 33.2.2 (includes fix of log4j vulnerability)

## 2021-10-01 v1.12.0
* [MODDICONV-196](https://issues.folio.org/browse/MODDICONV-196) Upgrade to RAML Module Builder 33.x
* [MODDICONV-193](https://issues.folio.org/browse/MODDICONV-193) Create default profile for importing new MARC Holdings
* [MODDICONV-199](https://issues.folio.org/browse/MODDICONV-199) Add default mapping profile to default Holdings profile
* [MODDICONV-179](https://issues.folio.org/browse/MODDICONV-179) Update endpoints for profiles creation to respect UUID from the request body
* [MODDICONV-207](https://issues.folio.org/browse/MODDICONV-207) Add default job profile for Derive Holdings and SRS MARC Holdings
* [MODDICONV-209](https://issues.folio.org/browse/MODDICONV-209) Create default Action profile and Mapping profile for Authority

## 2021-09-13 v1.11.3
* [MODDICONV-204](https://issues.folio.org/browse/MODDICONV-204) When removing an action profile from a job profile, it sometimes removes that action from ALL job profiles

## 2021-07-30 v1.11.2
* [MODDICONV-192](https://issues.folio.org/browse/MODDICONV-192) Disallow linking multiple mapping profiles to one action profile

## 2021-06-25 v1.11.1
* [MODDICONV-191](https://issues.folio.org/browse/MODDICONV-191) Change dataType to have common type for MARC related subtypes

## 2021-06-17 v1.11.0
* [MODDICONV-187](https://issues.folio.org/browse/MODDICONV-187) Store MARC Authority record

## 2021-04-22 v1.10.2
* [MODDICONV-185](https://issues.folio.org/browse/MODDICONV-185) Enable updated job profile.

## 2021-04-16 v1.10.1
* [MODDICONV-180](https://issues.folio.org/browse/MODDICONV-180) Add a new default job profile for creating instances and SRS MARC Bibs.
* [MODDICONV-181](https://issues.folio.org/browse/MODDICONV-181) Update the existing default job profile for creating MARC Bibs and Instances to be quickMARC-related.

## 2021-03-17 v1.10.0
* [MODDICONV-162](https://issues.folio.org/browse/MODDICONV-162) Update to the RMB 32.
* [MODDICONV-148](https://issues.folio.org/browse/MODDICONV-148) Create default OCLC Create Instance profile
* [MODDICONV-149](https://issues.folio.org/browse/MODDICONV-149) Create default OCLC Update Instance profile
* [MODDICONV-158](https://issues.folio.org/browse/MODDICONV-158) Update the names and descriptions of the default OCLC profiles.
* [MODDICONV-160](https://issues.folio.org/browse/MODDICONV-160) Update the names and descriptions of the default OCLC match, action, field mapping profiles.
* [MODDICONV-163](https://issues.folio.org/browse/MODDICONV-163) Add default job profile for creating instances and SRS MARC Bibs.
* [MODDICONV-173](https://issues.folio.org/browse/MODDICONV-173) Make tenant API asynchronous.
* [MODDICONV-161](https://issues.folio.org/browse/MODDICONV-161) Add personal data disclosure form.
* [MODDICONV-176](https://issues.folio.org/browse/MODDICONV-176) Update the names and descriptions of the default OCLC Single record profiles AGAIN.

## 2020-11-06 v1.9.2
* [MODDICONV-152](https://issues.folio.org/browse/MODDICONV-150) Upgrade to RMB v31.1.5
* [MODDICONV-156](https://issues.folio.org/browse/MODDICONV-156) Disable CQL2PgJSON & CQLWrapper extra logging in mod-di-converter-storage

## 2020-10-27 v1.9.1
* [MODDICONV-150](https://issues.folio.org/browse/MODDICONV-150) Fix error on creating data-import profile by a user with limited permissions

## 2020-10-09 v1.9.0
* [MODDICONV-138](https://issues.folio.org/browse/MODDICONV-138) Store MARC field protection settings
* [MODDICONV-139](https://issues.folio.org/browse/MODDICONV-139) Implement API for managing MARC field protection settings
* [MODDICONV-141](https://issues.folio.org/browse/MODDICONV-141) Update the available actions for Action profiles
* [MODDATAIMP-324](https://issues.folio.org/browse/MODDATAIMP-324) Update all Data-Import modules to the new RMB version

## 2020-07-08 v1.8.3
* [MODDICONV-135](https://issues.folio.org/browse/MODDICONV-135) Field mappings: Repeatable fields dropdown action without subfields support - validation for mod-data-import converter storage

## 2020-06-22 v1.8.2
* [MODDICONV-132](https://issues.folio.org/browse/MODDICONV-132) Fixed script to delete sample data

## 2020-06-16 v1.8.1
* [MODDICONV-129](https://issues.folio.org/browse/MODDICONV-129) Fixed creation of the rule to delete profiles

## 2020-06-11 v1.8.0
* [MODDICONV-117](https://issues.folio.org/browse/MODDICONV-117) Added migration script to remove outdated indexes
* [MODDICONV-123](https://issues.folio.org/browse/MODDICONV-123) Added migration script for profiles sample data
* [MODDICONV-118](https://issues.folio.org/browse/MODDICONV-118) Defined MARC mapping details schema
* [MODDICONV-125](https://issues.folio.org/browse/MODDICONV-125) Updated RMB version to 30.0.2
* [MODDICONV-115](https://issues.folio.org/browse/MODDICONV-115) Added pass-through JobProfiles identifiers in profile associations
* [MODDICONV-124](https://issues.folio.org/browse/MODDICONV-124) Remove sample profile data from Settings/Data import

## 2020-04-23 v1.7.2
* [MODDICONV-112](https://issues.folio.org/browse/MODDICONV-112) Added migration script to support RMB version update
* [MODDICONV-120](https://issues.folio.org/browse/MODDICONV-120) Added default job profile as reference data to enable "secret button"

## 2020-03-27 v1.7.1
* Adjusted staticValueDetail schema
* Allowed for matchDetails static values contain decimal numbers
* Provided API for getting Profile Snapshot structure for any profile type

  | METHOD |             URL                                                | DESCRIPTION                          |
  |--------|----------------------------------------------------------------|--------------------------------------|
  | GET    | /data-import-profiles/profileSnapshots/{id}?profileType={type} | Get Profile Snapshot structure by profile id and type (JOB_PROFILE, MATCH_PROFILE, ACTION_PROFILE, MAPPING_PROFILE) |

## 2020-03-13 v1.7.0
* Incremented RMB version
* Created API for getting Profile Snapshot structure
* Renamed mappingProfile field "folioRecord" to "existingRecordType"
* Added support of saving\updating profiles with changes in their relations in one transaction
* Implemented CRUD endpoints for FormConfig entity

  | METHOD |             URL                                              | DESCRIPTION                          |
  |--------|--------------------------------------------------------------|--------------------------------------|
  | GET    | /data-import-profiles/profileSnapshots/{id}                  | Get Profile Snapshot structure by id |
  | GET    | /converter-storage/forms/configs                             | Get all forms configs                |
  | POST   | /converter-storage/forms/configs                             | Create form config                   |
  | GET    | /converter-storage/forms/configs/{formName}                  | Get form config by formName          |
  | PUT    | /converter-storage/forms/configs/{formName}                  | Update form config by formName       |
  | DELETE | /converter-storage/forms/configs/{formName}                  | Delete form config by formName       |

## 2019-12-04 v1.6.0
* Fixed security vulnerabilities
* Applied new JVM features to manage container memory
* Updated schema for ProfileSnapshot

## 2019-09-10 v1.5.1
* Fixed filtering of profile associations
* Fixed match profiles sample data
* Added loading sample data by "loadSample" tenant parameter.
* Added 'profileId' field to profileSnapshotWrapper schema

## 2019-09-09 v1.5.0
* Updated Match Profile schema and sample data.
* Filled in "fromModuleVersion" value for each "tables" and "scripts" section in schema.json

## 2019-07-22 v1.4.0
* Added removing all profile associations when it's marked as deleted.
* Changed implementation of delete functionality for profile associations using DB rule.
* Updated Mapping Profile schema and sample data.

## 2019-06-12 v1.3.0
* Updated association profiles samples.
* Updated API endpoint for getting details by master id. Added query param to specify return type. Added searching by cql query for details.
* Updated API endpoint for getting masters by detail id. Added query param to specify return type. Added searching by cql query for details.
* Updated API endpoints for Profile Association entity. Added query parameters to specify master and detail types of profile association.
* Updated Action Profile sample data.
* Added check for existence of associated profiles before delete.
* Added validation on post and put for action, mapping and match profiles.
* Fixed 'name' field validation.

  | METHOD |             URL                                              | DESCRIPTION                         |
  |--------|--------------------------------------------------------------|-------------------------------------|
  | GET    | /data-import-profiles/jobProfiles                            | Get collection of Job Profiles      |
  | POST   | /data-import-profiles/jobProfiles                            | Create Job Profile                  |
  | GET    | /data-import-profiles/jobProfiles/{id}                       | Get Job Profile by id               |
  | PUT    | /data-import-profiles/jobProfiles/{id}                       | Update Job Profile entity           |
  | DELETE | /data-import-profiles/jobProfiles/{id}                       | Delete Job Profile entity           |
  | GET    | /data-import-profiles/matchProfiles                          | Get collection of Match Profiles    |
  | POST   | /data-import-profiles/matchProfiles                          | Create Match Profile                |
  | GET    | /data-import-profiles/matchProfiles/{id}                     | Get Match Profile by id             |
  | PUT    | /data-import-profiles/matchProfiles/{id}                     | Update Match Profile entity         |
  | DELETE | /data-import-profiles/matchProfiles/{id}                     | Delete Match Profile entity         |
  | GET    | /data-import-profiles/actionProfiles                         | Get collection of Action Profiles   |
  | POST   | /data-import-profiles/actionProfiles                         | Create Action Profile               |
  | GET    | /data-import-profiles/actionProfiles/{id}                    | Get Action Profile by id            |
  | PUT    | /data-import-profiles/actionProfiles/{id}                    | Update Action Profile entity        |
  | DELETE | /data-import-profiles/actionProfiles/{id}                    | Delete Action Profile entity        |
  | GET    | /data-import-profiles/mappingProfiles                        | Get collection of Mapping Profiles  |
  | POST   | /data-import-profiles/mappingProfiles                        | Create Mapping Profile              |
  | GET    | /data-import-profiles/mappingProfiles/{id}                   | Get Mapping Profile by id           |
  | PUT    | /data-import-profiles/mappingProfiles/{id}                   | Update Mapping Profile entity       |
  | DELETE | /data-import-profiles/mappingProfiles/{id}                   | Delete Mapping Profile entity       |
  | POST   | /data-import-profiles/profileAssociations?master={masterType}&detail={detailType}                    | Create Profile Association with appropriate masterType and detailType               |
  | GET    | /data-import-profiles/profileAssociations?master={masterType}&detail={detailType}                    | Get all Profile Association , masterType and detailType                             |
  | GET    | /data-import-profiles/profileAssociations/{id}?master={masterType}&detail={detailType}               | Get Profile Association with appropriate masterType and detailType                  |
  | PUT    | /data-import-profiles/profileAssociations/{id}?master={masterType}&detail={detailType}               | Update Profile Association entity by id with appropriate masterType and detailType  |
  | DELETE | /data-import-profiles/profileAssociations/{id}?master={masterType}&detail={detailType}               | Delete Profile Association entity by id , masterType and detailType                 |
  | GET    | /data-import-profiles/jobProfileSnapshots/{id}               | Get Job Profile Snapshot entity     |
  | POST   | /data-import-profiles/jobProfileSnapshots/{id}               | Create Job Profile Snapshot entity  |
  | GET    | /data-import-profiles/profileAssociations/{masterId}/details?masterType={masterType}| Get details by master profile masterType: JOB_PROFILE, ACTION_PROFILE, MATCH_PROFILE, MAPPING_PROFILE |
  | GET    | /data-import-profiles/profileAssociations/{detailId}/masters?detailType={detailType}| Get masters by detail profile detailType: JOB_PROFILE, ACTION_PROFILE, MATCH_PROFILE, MAPPING_PROFILE |
  | GET    | /data-import-profiles/entityTypes                            | Get collection of entity types      |

## 2019-05-16 v1.2.0
* Changed delete methods logic for action, job, match and mapping profiles. Profiles are being marked as deleted and not removed permanently from database.
* Fixed get API endpoints for jobProfiles and matchProfiles when sortedBy is specified.

## 2019-05-10 v1.1.2
* Added default Job Profile for MARC Bibs records import
* Added Match Profile samples

## 2019-05-03 v1.1.0
* Added endpoint to receive entity types list.
* Updated Job Profile schema to contain single dataType.
* Updated existing sample data.
* Added methods to work with associated profiles.
* Designed and created Job Profile Snapshot entity.
* Designed and created Job Profile Snapshot database view.
* Added unique constraint for Job Profile name.
* Updated Tenant API version.
* Populated sample Match Profile data.

  | METHOD |             URL                                              | DESCRIPTION                         |
  |--------|--------------------------------------------------------------|-------------------------------------|
  | GET    | /data-import-profiles/jobProfiles                            | Get collection of Job Profiles      |
  | POST   | /data-import-profiles/jobProfiles                            | Create Job Profile                  |
  | GET    | /data-import-profiles/jobProfiles/{id}                       | Get Job Profile by id               |
  | PUT    | /data-import-profiles/jobProfiles/{id}                       | Update Job Profile entity           |
  | DELETE | /data-import-profiles/jobProfiles/{id}                       | Delete Job Profile entity           |
  | GET    | /data-import-profiles/matchProfiles                          | Get collection of Match Profiles    |
  | POST   | /data-import-profiles/matchProfiles                          | Create Match Profile                |
  | GET    | /data-import-profiles/matchProfiles/{id}                     | Get Match Profile by id             |
  | PUT    | /data-import-profiles/matchProfiles/{id}                     | Update Match Profile entity         |
  | DELETE | /data-import-profiles/matchProfiles/{id}                     | Delete Match Profile entity         |
  | GET    | /data-import-profiles/actionProfiles                         | Get collection of Action Profiles   |
  | POST   | /data-import-profiles/actionProfiles                         | Create Action Profile               |
  | GET    | /data-import-profiles/actionProfiles/{id}                    | Get Action Profile by id            |
  | PUT    | /data-import-profiles/actionProfiles/{id}                    | Update Action Profile entity        |
  | DELETE | /data-import-profiles/actionProfiles/{id}                    | Delete Action Profile entity        |
  | GET    | /data-import-profiles/mappingProfiles                        | Get collection of Mapping Profiles  |
  | POST   | /data-import-profiles/mappingProfiles                        | Create Mapping Profile              |
  | GET    | /data-import-profiles/mappingProfiles/{id}                   | Get Mapping Profile by id           |
  | PUT    | /data-import-profiles/mappingProfiles/{id}                   | Update Mapping Profile entity       |
  | DELETE | /data-import-profiles/mappingProfiles/{id}                   | Delete Mapping Profile entity       |
  | POST   | /data-import-profiles/profileAssociations                    | Create Profile Association          |
  | GET    | /data-import-profiles/profileAssociations/{id}               | Get Profile Association by id       |
  | PUT    | /data-import-profiles/profileAssociations/{id}               | Update Profile Association entity   |
  | DELETE | /data-import-profiles/profileAssociations/{id}               | Delete Profile Association entity   |
  | GET    | /data-import-profiles/jobProfileSnapshots/{id}               | Get Job Profile Snapshot entity     |
  | POST   | /data-import-profiles/jobProfileSnapshots/{id}               | Create Job Profile Snapshot entity  |
  | GET    | /data-import-profiles/profileAssociations/{masterId}/details?masterType={masterType}| Get details by master profile masterType: JOB_PROFILE, ACTION_PROFILE, MATCH_PROFILE, MAPPING_PROFILE |
  | GET    | /data-import-profiles/profileAssociations/{detailId}/masters?detailType={detailType}| Get masters by detail profile detailType: JOB_PROFILE, ACTION_PROFILE, MATCH_PROFILE, MAPPING_PROFILE |
  | GET    | /data-import-profiles/entityTypes                            | Get collection of entity types      |


## 2019-03-20 v1.0.0
* Initial module setup
* Implemented CRUD endpoints for Job Profile entity
* Populated sample Job Profile data
* Implemented CRUD endpoints for Match Profile entity
* Implemented CRUD endpoints for Mapping Profile entity
* Implemented CRUD endpoints for Action Profile entity
* Implemented CRUD endpoint for Profile Associations
* Implemented GET and POST methods for Job Profile Snapshot
* Implemented GET method for retrieving detail profiles by master profile id
* Implemented GET method for retrieving master profiles by detail profile id

  | METHOD |             URL                                              | DESCRIPTION                         |
  |--------|--------------------------------------------------------------|-------------------------------------|
  | GET    | /data-import-profiles/jobProfiles                            | Get collection of Job Profiles      |
  | POST   | /data-import-profiles/jobProfiles                            | Create Job Profile                  |
  | GET    | /data-import-profiles/jobProfiles/{id}                       | Get Job Profile by id               |
  | PUT    | /data-import-profiles/jobProfiles/{id}                       | Update Job Profile entity           |
  | DELETE | /data-import-profiles/jobProfiles/{id}                       | Delete Job Profile entity           |
  | GET    | /data-import-profiles/matchProfiles                          | Get collection of Match Profiles    |
  | POST   | /data-import-profiles/matchProfiles                          | Create Match Profile                |
  | GET    | /data-import-profiles/matchProfiles/{id}                     | Get Match Profile by id             |
  | PUT    | /data-import-profiles/matchProfiles/{id}                     | Update Match Profile entity         |
  | DELETE | /data-import-profiles/matchProfiles/{id}                     | Delete Match Profile entity         |
  | GET    | /data-import-profiles/actionProfiles                         | Get collection of Action Profiles   |
  | POST   | /data-import-profiles/actionProfiles                         | Create Action Profile               |
  | GET    | /data-import-profiles/actionProfiles/{id}                    | Get Action Profile by id            |
  | PUT    | /data-import-profiles/actionProfiles/{id}                    | Update Action Profile entity        |
  | DELETE | /data-import-profiles/actionProfiles/{id}                    | Delete Action Profile entity        |
  | GET    | /data-import-profiles/mappingProfiles                        | Get collection of Mapping Profiles  |
  | POST   | /data-import-profiles/mappingProfiles                        | Create Mapping Profile              |
  | GET    | /data-import-profiles/mappingProfiles/{id}                   | Get Mapping Profile by id           |
  | PUT    | /data-import-profiles/mappingProfiles/{id}                   | Update Mapping Profile entity       |
  | DELETE | /data-import-profiles/mappingProfiles/{id}                   | Delete Mapping Profile entity       |
  | POST   | /data-import-profiles/profileAssociations                    | Create Profile Association          |
  | GET    | /data-import-profiles/profileAssociations/{id}               | Get Profile Association by id       |
  | PUT    | /data-import-profiles/profileAssociations/{id}               | Update Profile Association entity   |
  | DELETE | /data-import-profiles/profileAssociations/{id}               | Delete Profile Association entity   |
  | GET    | /data-import-profiles/jobProfileSnapshots/{id}               | Get Job Profile Snapshot entity     |
  | POST   | /data-import-profiles/jobProfileSnapshots/{id}               | Create Job Profile Snapshot entity  |
  | GET    | /data-import-profiles/profileAssociations/{masterId}/details?masterType={masterType}| Get details by master profile masterType: JOB_PROFILE, ACTION_PROFILE, MATCH_PROFILE, MAPPING_PROFILE |
  | GET    | /data-import-profiles/profileAssociations/{detailId}/masters?detailType={detailType}| Get masters by detail profile detailType: JOB_PROFILE, ACTION_PROFILE, MATCH_PROFILE, MAPPING_PROFILE |

