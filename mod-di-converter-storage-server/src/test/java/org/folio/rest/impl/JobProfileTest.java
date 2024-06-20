package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.sqlclient.Tuple;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileCollection;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.MatchProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileAssociationCollection;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ReactToType;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.*;

import static org.folio.rest.impl.ActionProfileTest.ACTION_PROFILES_PATH;
import static org.folio.rest.impl.ActionProfileTest.ACTION_PROFILES_TABLE_NAME;
import static org.folio.rest.impl.MatchProfileTest.MATCH_PROFILES_PATH;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.CREATE;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.MODIFY;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.UPDATE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.INSTANCE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC;
import static org.folio.rest.jaxrs.model.JobProfile.DataType.*;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MAPPING_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;
import static org.folio.rest.jaxrs.model.ReactToType.MATCH;
import static org.folio.rest.jaxrs.model.ReactToType.NON_MATCH;
import static org.hamcrest.Matchers.*;

@RunWith(VertxUnitRunner.class)
public class JobProfileTest extends AbstractRestVerticleTest {

  private static final String JOB_PROFILES_TABLE_NAME = "job_profiles";
  public static final String JOB_TO_ACTION_PROFILES_TABLE = "job_to_action_profiles";
  public static final String JOB_TO_MATCH_PROFILES_TABLE = "job_to_match_profiles";
  static final String JOB_PROFILES_PATH = "/data-import-profiles/jobProfiles";
  private static final String ASSOCIATED_PROFILES_PATH = "/data-import-profiles/profileAssociations";
  private static final String PROFILE_WRAPPERS_TABLE_NAME = "profile_wrappers";
  static final String MAPPING_PROFILES_TABLE_NAME = "mapping_profiles";
  private static final String ACTION_TO_MAPPING_PROFILES_TABLE = "action_to_mapping_profiles";
  static final String MATCH_PROFILES_TABLE_NAME = "match_profiles";
  private static final String SNAPSHOTS_TABLE_NAME = "profile_snapshots";
  private static final String MATCH_TO_ACTION_PROFILES_TABLE_NAME = "match_to_action_profiles";
  private static final String ACTION_TO_ACTION_PROFILES_TABLE_NAME = "action_to_action_profiles";
  private static final String MATCH_TO_MATCH_PROFILES_TABLE_NAME = "match_to_match_profiles";
  private static final String PROFILE_WRAPPERS_TABLE = "profile_wrappers";
  static final String MAPPING_PROFILES_PATH = "/data-import-profiles/mappingProfiles";

  private static final String JOB_PROFILE_UUID = "b81c283c-131d-4470-ab91-e92bb415c000";
  private static final String DEFAULT_CREATE_SRS_MARC_AUTHORITY_JOB_PROFILE_ID = "6eefa4c6-bbf7-4845-ad82-de7fc5abd0e3";
  private final List<String> defaultJobProfileIds = Arrays.asList(
    "d0ebb7b0-2f0f-11eb-adc1-0242ac120002", //OCLC_CREATE_INSTANCE_JOB_PROFILE_ID
    "91f9b8d6-d80e-4727-9783-73fb53e3c786", //OCLC_UPDATE_INSTANCE_JOB_PROFILE_ID
    "fa0262c7-5816-48d0-b9b3-7b7a862a5bc7", //DEFAULT_CREATE_DERIVE_HOLDINGS_JOB_PROFILE_ID
    "6409dcff-71fa-433a-bc6a-e70ad38a9604", //DEFAULT_CREATE_DERIVE_INSTANCE_JOB_PROFILE_ID
    "80898dee-449f-44dd-9c8e-37d5eb469b1d", //DEFAULT_CREATE_HOLDINGS_AND_SRS_MARC_HOLDINGS_JOB_PROFILE_ID
    "1a338fcd-3efc-4a03-b007-394eeb0d5fb9", //DEFAULT_DELETE_MARC_AUTHORITY_JOB_PROFILE_ID
    "cf6f2718-5aa5-482a-bba5-5bc9b75614da", //DEFAULT_QM_MARC_BIB_UPDATE_JOB_PROFILE_ID
    "6cb347c6-c0b0-4363-89fc-32cedede87ba", //DEFAULT_QM_HOLDINGS_UPDATE_JOB_PROFILE_ID
    "c7fcbc40-c4c0-411d-b569-1fc6bc142a92",
    "6eefa4c6-bbf7-4845-ad82-de7fc4abd0e3"  //DEFAULT_QM_AUTHORITY_CREATE_JOB_PROFILE_ID
  );

  static JobProfileUpdateDto jobProfile_1 = new JobProfileUpdateDto()
    .withProfile(new JobProfile().withName("Bla")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
      .withDataType(MARC));
  static JobProfileUpdateDto jobProfile_2 = new JobProfileUpdateDto()
    .withProfile(new JobProfile().withName("Boo")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum")))
      .withDataType(MARC));
  static JobProfileUpdateDto jobProfile_3 = new JobProfileUpdateDto()
    .withProfile(new JobProfile().withName("Foo")
      .withTags(new Tags().withTagList(Collections.singletonList("lorem")))
      .withDataType(MARC));
  static JobProfileUpdateDto jobProfile_4 = new JobProfileUpdateDto()
    .withProfile(new JobProfile().withId(JOB_PROFILE_UUID)
      .withName("OLA")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
      .withDataType(MARC));
  static JobProfileUpdateDto jobProfile_5 = new JobProfileUpdateDto()
    .withProfile(new JobProfile().withId(DEFAULT_CREATE_SRS_MARC_AUTHORITY_JOB_PROFILE_ID)
      .withName("Default - Create SRS MARC Authority")
      .withDescription("Default job profile for creating MARC authority records.")
      .withDataType(MARC));
  static JobProfileUpdateDto jobProfileNotEmptyChildAndParent = new JobProfileUpdateDto()
    .withProfile(new JobProfile()
      .withName("Job profile with child and parent")
      .withDataType(MARC)
      .withChildProfiles(List.of(new ProfileSnapshotWrapper().withId(UUID.randomUUID().toString())))
      .withParentProfiles(List.of(new ProfileSnapshotWrapper().withId(UUID.randomUUID().toString()))));

  @Test
  public void shouldReturnEmptyListOnGet() {
    RestAssured.given()
      .spec(spec)
      .when()
      .get(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(0))
      .body("jobProfiles", empty());
  }

  @Test
  public void shouldReturnAllProfilesOnGet() {
    createProfiles();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(JOB_PROFILES_PATH + "?withRelations=true")
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(3))
      .body("jobProfiles*.deleted", everyItem(is(false)))
      .body("jobProfiles*.hidden", everyItem(is(false)));
  }

  @Test
  public void shouldReturnCommittedProfilesOnGetWithQueryByLastName() {
    createProfiles();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(JOB_PROFILES_PATH + "?query=userInfo.lastName=Doe")
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(3))
      .body("jobProfiles*.deleted", everyItem(is(false)))
      .body("jobProfiles*.hidden", everyItem(is(false)))
      .body("jobProfiles*.userInfo.lastName", everyItem(is("Doe")));
  }

  @Test
  public void shouldReturnIpsumTaggedProfilesOnGetWithQueryByTag() {
    createProfiles();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(JOB_PROFILES_PATH + "?query=tags.tagList=/respectCase/respectAccents \\\"ipsum\\\"")
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(2))
      .body("jobProfiles*.deleted", everyItem(is(false)))
      .body("jobProfiles*.hidden", everyItem(is(false)))
      .body("jobProfiles.get(0).tags.tagList", hasItem("ipsum"))
      .body("jobProfiles.get(1).tags.tagList", hasItem("ipsum"));
  }

  @Test
  public void shouldReturnLimitedCollectionOnGetWithLimit() {
    createProfiles();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(JOB_PROFILES_PATH + "?limit=2")
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("jobProfiles.size()", is(2))
      .body("jobProfiles*.deleted", everyItem(is(false)))
      .body("jobProfiles*.hidden", everyItem(is(false)))
      .body("totalRecords", is(3));
  }

  @Test
  public void shouldReturnSortedProfilesOnGetWhenSortByIsSpecified(TestContext testContext) {
    createProfiles();
    List<JobProfile> jobProfileList = RestAssured.given()
      .spec(spec)
      .when()
      .get(JOB_PROFILES_PATH + "?query=(cql.allRecords=1) sortBy metadata.createdDate/sort.descending")
      .then()
      .log().all()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(3))
      .extract().body().as(JobProfileCollection.class).getJobProfiles();

    Assert.assertTrue(jobProfileList.get(0).getMetadata().getCreatedDate().after(jobProfileList.get(1).getMetadata().getCreatedDate()));
    Assert.assertTrue(jobProfileList.get(1).getMetadata().getCreatedDate().after(jobProfileList.get(2).getMetadata().getCreatedDate()));
  }

  @Test
  public void shouldReturnBadRequestOnPost() {
    createProfiles();
    RestAssured.given()
      .spec(spec)
      .body(new JsonObject().toString())
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldReturnBadRequestOnDeleteDefaultProfiles() {
    createProfiles();
    List<String> allDefaultJobProfilesIds = new ArrayList<>(defaultJobProfileIds);
    allDefaultJobProfilesIds.add(DEFAULT_CREATE_SRS_MARC_AUTHORITY_JOB_PROFILE_ID);
    for (String id : allDefaultJobProfilesIds) {
      RestAssured.given()
        .spec(spec)
        .when()
        .delete(JOB_PROFILES_PATH + "/" + id)
        .then()
        .statusCode(HttpStatus.SC_BAD_REQUEST);
    }
  }

  @Test
  public void shouldReturnBadRequestOnPutDefaultProfiles() {
    createProfiles();
    for (String id : defaultJobProfileIds) {
      RestAssured.given()
        .spec(spec)
        .body(jobProfile_1)
        .when()
        .put(JOB_PROFILES_PATH + "/" + id)
        .then()
        .statusCode(HttpStatus.SC_BAD_REQUEST);
    }
  }

  private JobProfileUpdateDto createJobProfile(JobProfileUpdateDto jobProfileUpdateDto,
                                               String actionName,
                                               String mappingName) {
    var actionProfileIdCreate = UUID.randomUUID().toString();
    RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile().withName(actionName)
          .withId(actionProfileIdCreate)
          .withAction(CREATE)
          .withFolioRecord(INSTANCE)))
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    String mappingProfileIdCreate = UUID.randomUUID().toString();
    RestAssured.given()
      .spec(spec)
      .body(new MappingProfileUpdateDto()
        .withProfile(new MappingProfile().withName(mappingName)
          .withId(mappingProfileIdCreate)
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withExistingRecordType(EntityType.INSTANCE))
        .withAddedRelations(
          List.of(
            new ProfileAssociation()
              .withMasterProfileId(actionProfileIdCreate)
              .withDetailProfileId(mappingProfileIdCreate)
              .withMasterProfileType(ACTION_PROFILE)
              .withDetailProfileType(MAPPING_PROFILE)
              .withOrder(0))))
      .when()
      .post(MAPPING_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    var validAssociation = new ProfileAssociation()
      .withMasterProfileId(jobProfileUpdateDto.getId())
      .withDetailProfileId(actionProfileIdCreate)
      .withMasterProfileType(JOB_PROFILE)
      .withDetailProfileType(ACTION_PROFILE)
      .withOrder(0);

    return jobProfileUpdateDto.withAddedRelations(List.of(validAssociation));
  }

  private JobProfileUpdateDto createJobProfileWithAction(JobProfileUpdateDto jobProfileUpdateDto,
                                               ActionProfileUpdateDto actionProfileUpdateDto) {
    ActionProfileUpdateDto actionProfile = RestAssured.given()
      .spec(spec)
      .body(actionProfileUpdateDto)
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    var validAssociation = new ProfileAssociation()
      .withMasterProfileId(jobProfileUpdateDto.getId())
      .withDetailProfileId(actionProfile.getId())
      .withMasterProfileType(JOB_PROFILE)
      .withDetailProfileType(ACTION_PROFILE)
      .withOrder(1);

    return jobProfileUpdateDto.withAddedRelations(List.of(validAssociation));
  }

  private JobProfileUpdateDto createJobProfileWithMatch(JobProfileUpdateDto jobProfileUpdateDto,
                                               MatchProfileUpdateDto matchProfileUpdateDto) {
    MatchProfileUpdateDto matchProfile = RestAssured.given()
      .spec(spec)
      .body(matchProfileUpdateDto)
      .when()
      .post(MATCH_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(MatchProfileUpdateDto.class);

    var validAssociation = new ProfileAssociation()
      .withMasterProfileId(jobProfileUpdateDto.getId())
      .withDetailProfileId(matchProfile.getId())
      .withMasterProfileType(JOB_PROFILE)
      .withDetailProfileType(MATCH_PROFILE)
      .withOrder(1);

    return jobProfileUpdateDto.withAddedRelations(List.of(validAssociation));
  }

  @Test
  public void shouldCreateProfileOnPost() {
    String jobId = UUID.randomUUID().toString();
    JobProfileUpdateDto jobProfile = new JobProfileUpdateDto()
        .withProfile(new JobProfile()
          .withId(jobId)
          .withName("Bla")
          .withDataType(MARC)
          .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor"))));
    jobProfile = createJobProfile(jobProfile, "testActionCreate", "testMappingCreate");

    RestAssured.given()
      .spec(spec)
      .body(jobProfile)
      .when()
      .post(JOB_PROFILES_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_CREATED)
      .body("profile.name", is(jobProfile.getProfile().getName()))
      .body("profile.tags.tagList", is(jobProfile.getProfile().getTags().getTagList()))
      .body("profile.userInfo.lastName", is("Doe"))
      .body("profile.userInfo.firstName", is("Jane"))
      .body("profile.userInfo.userName", is("@janedoe"))
      .body("profile.dataType", is(jobProfile.getProfile().getDataType().value()));

    RestAssured.given().spec(spec)
      .body(jobProfile)
      .when()
      .post(JOB_PROFILES_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Job profile with id 'Bla' already exists"));
  }

  @Test
  public void shouldCreateProfileWithGivenIdOnPost() {
    JobProfileUpdateDto jobProfile = createJobProfile(jobProfile_4, "actionCreate", "mappingCreate");
    RestAssured.given()
      .spec(spec)
      .body(jobProfile)
      .when()
      .post(JOB_PROFILES_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_CREATED)
      .body("profile.name", is(jobProfile.getProfile().getName()))
      .body("profile.tags.tagList", is(jobProfile.getProfile().getTags().getTagList()))
      .body("profile.userInfo.lastName", is("Doe"))
      .body("profile.userInfo.firstName", is("Jane"))
      .body("profile.userInfo.userName", is("@janedoe"))
      .body("profile.dataType", is(jobProfile.getProfile().getDataType().value()));

    JobProfileUpdateDto jobProfile2 = createJobProfile(new JobProfileUpdateDto()
      .withProfile(new JobProfile().withId(JOB_PROFILE_UUID)
        .withName("GOA")
        .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum")))
        .withDataType(MARC)), "createAction2", "mappingCreate2");

    RestAssured.given()
      .spec(spec)
      .body(jobProfile2)
      .when()
      .post(JOB_PROFILES_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Job profile with id 'GOA' already exists"));
  }

  @Test
  public void shouldReturnBadRequestOnPostJobProfileWithoutDataType() {
    JsonObject jobProfileWithoutDataType = new JsonObject()
      .put("name", "Bla");

    RestAssured.given()
      .spec(spec)
      .body(jobProfileWithoutDataType.encode())
      .when()
      .post(JOB_PROFILES_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldReturnBadRequestOnPostJobProfileWithInvalidField() {
    JsonObject jobProfile = new JsonObject()
      .put("name", "Bla")
      .put("dataType", MARC)
      .put("invalidField", "value");

    RestAssured.given()
      .spec(spec)
      .body(jobProfile.encode())
      .when()
      .post(JOB_PROFILES_PATH)
      .then().log().all()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldReturnBadRequestOnPostJobProfileWithUpdateActionProfileWithoutMatchProfile() {
    var actionProfileId = UUID.randomUUID().toString();
    var jobId = UUID.randomUUID().toString();

    RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile().withName("testAction")
          .withAction(UPDATE)
          .withFolioRecord(INSTANCE)
          .withId(actionProfileId))
      )
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    var invalidAssociation = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileId)
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobId);

    RestAssured.given()
      .spec(spec)
      .body(new JobProfileUpdateDto()
        .withProfile(new JobProfile()
          .withId(jobId)
          .withName("testJob")
          .withDataType(MARC))
        .withAddedRelations(List.of(invalidAssociation))
      )
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is(String.format("ActionProfile with id '%s' and action UPDATE requires linked MatchProfile", actionProfileId)))
        ));
  }

  @Test
  public void shouldReturnBadRequestOnPostJobProfileWithStandaloneModifyAction() {
    var actionProfileId = UUID.randomUUID().toString();
    var jobId = UUID.randomUUID().toString();

    RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile().withName("testAction")
          .withAction(MODIFY)
          .withFolioRecord(MARC_BIBLIOGRAPHIC)
          .withId(actionProfileId)))
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    var invalidAssociation = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileId)
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobId);

    RestAssured.given()
      .spec(spec)
      .body(new JobProfileUpdateDto()
        .withProfile(new JobProfile()
          .withId(jobId)
          .withName("testJob")
          .withDataType(MARC))
        .withAddedRelations(List.of(invalidAssociation))
      )
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Modify action cannot be used as a standalone action"))
      ));
  }

  @Test
  public void shouldReturnBadRequestOnPostJobProfileWithTwoModifyActions() {
    var actionProfileId = UUID.randomUUID().toString();
    var jobId = UUID.randomUUID().toString();

    ActionProfileUpdateDto actionProfileModify = RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile().withName("testActionModify")
          .withAction(MODIFY)
          .withFolioRecord(MARC_BIBLIOGRAPHIC)
          .withId(actionProfileId)))
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    String mappingProfileIdModify = UUID.randomUUID().toString();
    MappingProfileUpdateDto mappingProfileModify = RestAssured.given()
      .spec(spec)
      .body(new MappingProfileUpdateDto()
        .withProfile(new MappingProfile().withName("testModify")
          .withId(mappingProfileIdModify)
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC))
        .withAddedRelations(
          List.of(
            new ProfileAssociation()
              .withMasterProfileId(actionProfileModify.getId())
              .withDetailProfileId(mappingProfileIdModify)
              .withMasterProfileType(ACTION_PROFILE)
              .withDetailProfileType(ProfileType.MAPPING_PROFILE)
              .withOrder(0))))
      .when()
      .post(MAPPING_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(MappingProfileUpdateDto.class);

    var invalidAssociation1 = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileModify.getId())
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobId)
      .withOrder(0);

    var invalidAssociation2 = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileModify.getId())
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobId)
      .withOrder(2);

    RestAssured.given()
      .spec(spec)
      .body(new JobProfileUpdateDto()
        .withProfile(new JobProfile()
          .withId(jobId)
          .withName("testJob")
          .withDataType(MARC))
        .withAddedRelations(List.of(invalidAssociation1, invalidAssociation2))
      )
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Modify action cannot be used as a standalone action"))
      ));
  }


  @Test
  public void shouldReturnBadRequestOnPutJobProfileWithStandaloneModifyAction() {
    var actionProfileIdCreate = UUID.randomUUID().toString();
    var actionProfileIdModify = UUID.randomUUID().toString();

    var jobId = UUID.randomUUID().toString();

    ActionProfileUpdateDto actionProfileModify = RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile().withName("testActionModify")
          .withAction(MODIFY)
          .withFolioRecord(MARC_BIBLIOGRAPHIC)
          .withId(actionProfileIdModify)))
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    String mappingProfileIdModify = UUID.randomUUID().toString();
    MappingProfileUpdateDto mappingProfileModify = RestAssured.given()
      .spec(spec)
      .body(new MappingProfileUpdateDto()
        .withProfile(new MappingProfile().withName("testModify")
          .withId(mappingProfileIdModify)
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC))
        .withAddedRelations(
          List.of(
            new ProfileAssociation()
              .withMasterProfileId(actionProfileModify.getId())
              .withDetailProfileId(mappingProfileIdModify)
              .withMasterProfileType(ACTION_PROFILE)
              .withDetailProfileType(ProfileType.MAPPING_PROFILE)
              .withOrder(0))))
      .when()
      .post(MAPPING_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(MappingProfileUpdateDto.class);

    ActionProfileUpdateDto actionProfileCreate = RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile().withName("testActionCreate")
          .withId(actionProfileIdCreate)
          .withAction(CREATE)
          .withFolioRecord(INSTANCE)))
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    String mappingProfileIdCreate = UUID.randomUUID().toString();
    MappingProfileUpdateDto mappingProfileCreate = RestAssured.given()
      .spec(spec)
      .body(new MappingProfileUpdateDto()
        .withProfile(new MappingProfile().withName("testCreateInstance")
          .withId(mappingProfileIdCreate)
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withExistingRecordType(EntityType.INSTANCE))
        .withAddedRelations(
          List.of(
            new ProfileAssociation()
              .withMasterProfileId(actionProfileCreate.getId())
              .withDetailProfileId(mappingProfileIdCreate)
              .withMasterProfileType(ACTION_PROFILE)
              .withDetailProfileType(ProfileType.MAPPING_PROFILE)
              .withOrder(0))))
      .when()
      .post(MAPPING_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(MappingProfileUpdateDto.class);

    var validAssociation1 = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileModify.getId())
      .withMasterProfileType(JOB_PROFILE)
      .withOrder(0);

    var validAssociation2 = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileCreate.getId())
      .withMasterProfileType(JOB_PROFILE)
      .withOrder(1);


    JobProfileUpdateDto jobProfileUpdateDto = RestAssured.given()
      .spec(spec)
      .body(new JobProfileUpdateDto()
        .withProfile(new JobProfile()
          .withId(jobId)
          .withName("testJob")
          .withDataType(MARC))
        .withAddedRelations(List.of(validAssociation1, validAssociation2)))
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);



    var deleteAssociation = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileCreate.getId())
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobId)
      .withMasterWrapperId(jobProfileUpdateDto.getAddedRelations().get(1).getMasterWrapperId())
      .withDetailWrapperId(jobProfileUpdateDto.getAddedRelations().get(1).getDetailWrapperId());

    RestAssured.given()
      .spec(spec)
      .body(new JobProfileUpdateDto()
        .withId(jobId)
        .withProfile(new JobProfile()
          .withId(jobId)
          .withName("testJob")
          .withDataType(MARC))
        .withDeletedRelations(List.of(deleteAssociation)))
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobId)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Modify action cannot be used as a standalone action"))
      ));
  }

  @Test
  public void shouldReturnBadRequestOnPutJobProfileWithStandaloneModifyActionAfterMatch() {
    var actionProfileIdCreate = UUID.randomUUID().toString();
    var actionProfileIdModify = UUID.randomUUID().toString();

    var jobId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();

    RestAssured.given()
      .spec(spec)
      .body(new MatchProfileUpdateDto()
        .withProfile(new MatchProfile()
          .withId(matchProfileId)
          .withName("testMatch")
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withExistingRecordType(EntityType.INSTANCE)))
      .when()
      .post(MATCH_PROFILES_PATH);

    ActionProfileUpdateDto actionProfileModify = RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile().withName("testActionModify")
          .withAction(MODIFY)
          .withFolioRecord(MARC_BIBLIOGRAPHIC)
          .withId(actionProfileIdModify)))
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    String mappingProfileIdModify = UUID.randomUUID().toString();
    MappingProfileUpdateDto mappingProfileModify = RestAssured.given()
      .spec(spec)
      .body(new MappingProfileUpdateDto()
        .withProfile(new MappingProfile().withName("testModify")
          .withId(mappingProfileIdModify)
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC))
        .withAddedRelations(
          List.of(
            new ProfileAssociation()
              .withMasterProfileId(actionProfileModify.getId())
              .withDetailProfileId(mappingProfileIdModify)
              .withMasterProfileType(ACTION_PROFILE)
              .withDetailProfileType(ProfileType.MAPPING_PROFILE)
              .withOrder(0))))
      .when()
      .post(MAPPING_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(MappingProfileUpdateDto.class);

    ActionProfileUpdateDto actionProfileCreate = RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile().withName("testActionCreate")
          .withId(actionProfileIdCreate)
          .withAction(UPDATE)
          .withFolioRecord(INSTANCE)))
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    String mappingProfileIdCreate = UUID.randomUUID().toString();
    MappingProfileUpdateDto mappingProfileCreate = RestAssured.given()
      .spec(spec)
      .body(new MappingProfileUpdateDto()
        .withProfile(new MappingProfile().withName("testCreateInstance")
          .withId(mappingProfileIdCreate)
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withExistingRecordType(EntityType.INSTANCE))
        .withAddedRelations(
          List.of(
            new ProfileAssociation()
              .withMasterProfileId(actionProfileCreate.getId())
              .withDetailProfileId(mappingProfileIdCreate)
              .withMasterProfileType(ACTION_PROFILE)
              .withDetailProfileType(ProfileType.MAPPING_PROFILE)
              .withOrder(0))))
      .when()
      .post(MAPPING_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(MappingProfileUpdateDto.class);

    var validAssociation1 = new ProfileAssociation()
      .withDetailProfileType(MATCH_PROFILE)
      .withDetailProfileId(matchProfileId)
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobId);

    var validAssociation2 = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileCreate.getId())
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId)
      .withOrder(0);

    var validAssociation3 = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileModify.getId())
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId)
      .withOrder(1);

    JobProfileUpdateDto jobProfileUpdateDto = RestAssured.given()
      .spec(spec)
      .body(new JobProfileUpdateDto()
        .withProfile(new JobProfile()
          .withId(jobId)
          .withName("testJob")
          .withDataType(MARC))
        .withAddedRelations(List.of(validAssociation1, validAssociation2, validAssociation3)))
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    var deleteAssociation = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileCreate.getId())
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId)
      .withMasterWrapperId(jobProfileUpdateDto.getAddedRelations().get(1).getMasterWrapperId())
      .withDetailWrapperId(jobProfileUpdateDto.getAddedRelations().get(1).getDetailWrapperId());

    RestAssured.given()
      .spec(spec)
      .body(new JobProfileUpdateDto()
        .withId(jobId)
        .withProfile(new JobProfile()
          .withId(jobId)
          .withName("testJob")
          .withDataType(MARC))
        .withDeletedRelations(List.of(deleteAssociation)))
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobId)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Modify action cannot be used right after a Match"))
      ));
  }

  @Test
  public void shouldReturnBadRequestOnPostJobProfileWithStandaloneModifyActionAfterMatch() {
    var actionProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();
    var jobId = UUID.randomUUID().toString();

    RestAssured.given()
      .spec(spec)
      .body(new MatchProfileUpdateDto()
        .withProfile(new MatchProfile()
          .withId(matchProfileId)
          .withName("testMatch")
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withExistingRecordType(EntityType.INSTANCE)))
      .when()
      .post(MATCH_PROFILES_PATH);

    RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile().withName("testAction")
          .withAction(MODIFY)
          .withFolioRecord(MARC_BIBLIOGRAPHIC)
          .withId(actionProfileId)))
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    var invalidAssociationActionToMatch = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileId)
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId);

    var invalidAssociationMatchToJobProfile = new ProfileAssociation()
      .withDetailProfileType(MATCH_PROFILE)
      .withDetailProfileId(matchProfileId)
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobId);

    RestAssured.given()
      .spec(spec)
      .body(new JobProfileUpdateDto()
        .withProfile(new JobProfile()
          .withId(jobId)
          .withName("testJob")
          .withDataType(MARC))
        .withAddedRelations(List.of(invalidAssociationMatchToJobProfile, invalidAssociationActionToMatch))
      )
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Modify action cannot be used right after a Match"))
      ));
  }

  @Test
  public void shouldReturnNotFoundOnPostJobProfileWithInvalidLinkedProfiles() {
    var actionProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();
    var jobIdWithAction = UUID.randomUUID().toString();
    var jobIdWithMatch = UUID.randomUUID().toString();

    var invalidActionAssociation = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileId)
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobIdWithAction);

    var invalidMatchAssociation = new ProfileAssociation()
      .withDetailProfileType(MATCH_PROFILE)
      .withDetailProfileId(matchProfileId)
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobIdWithMatch);

    RestAssured.given()
      .spec(spec)
      .body(new JobProfileUpdateDto()
        .withProfile(new JobProfile()
          .withId(jobIdWithAction)
          .withName("testJob")
          .withDataType(MARC))
        .withAddedRelations(List.of(invalidActionAssociation))
      )
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);

    RestAssured.given()
      .spec(spec)
      .body(new JobProfileUpdateDto()
        .withProfile(new JobProfile()
          .withId(jobIdWithMatch)
          .withName("testJob")
          .withDataType(MARC))
        .withAddedRelations(List.of(invalidMatchAssociation))
      )
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnBadRequestOnPut() {
    RestAssured.given()
      .spec(spec)
      .body(new JsonObject().toString())
      .when()
      .put(JOB_PROFILES_PATH + "/" + UUID.randomUUID().toString())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldReturnNotFoundOnPut() {
    RestAssured.given()
      .spec(spec)
      .body(jobProfile_2)
      .when()
      .put(JOB_PROFILES_PATH + "/" + UUID.randomUUID().toString())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldUpdateProfileOnPut() {
    JobProfileUpdateDto jobProfile2 = createJobProfile(jobProfile_2, "createAction", "mappingCreate");
    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(jobProfile2)
      .when()
      .post(JOB_PROFILES_PATH);
    Assert.assertThat(createResponse.statusCode(), is(HttpStatus.SC_CREATED));
    JobProfileUpdateDto jobProfile = createResponse.body().as(JobProfileUpdateDto.class);

    jobProfile.getProfile().setDescription("test");
    jobProfile.getProfile().setDataType(DELIMITED);
    RestAssured.given()
      .spec(spec)
      .body(jobProfile)
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(jobProfile.getProfile().getId()))
      .body("name", is(jobProfile.getProfile().getName()))
      .body("description", is("test"))
      .body("tags.tagList", is(jobProfile.getProfile().getTags().getTagList()))
      .body("userInfo.lastName", is("Doe"))
      .body("userInfo.firstName", is("Jane"))
      .body("userInfo.userName", is("@janedoe"))
      .body("dataType", is(jobProfile.getProfile().getDataType().value()));
  }

  @Test
  public void shouldUpdateDefaultAuthorityJobProfileOnPut() {
    JobProfileUpdateDto jobProfile5 = createJobProfile(jobProfile_5, "createAction", "createMapping");
    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(jobProfile5)
      .when()
      .post(JOB_PROFILES_PATH);
    Assert.assertThat(createResponse.statusCode(), is(HttpStatus.SC_CREATED));
    JobProfileUpdateDto jobProfile = createResponse.body().as(JobProfileUpdateDto.class);

    jobProfile.getProfile().setName("updated name");
    jobProfile.getProfile().setDescription("updated description");
    jobProfile.getProfile().setDataType(EDIFACT);

    RestAssured.given()
      .spec(spec)
      .body(jobProfile)
      .when()
      .put(JOB_PROFILES_PATH + "/" + DEFAULT_CREATE_SRS_MARC_AUTHORITY_JOB_PROFILE_ID)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(DEFAULT_CREATE_SRS_MARC_AUTHORITY_JOB_PROFILE_ID))
      .body("name", is(jobProfile.getProfile().getName()))
      .body("description", is(jobProfile.getProfile().getDescription()))
      .body("dataType", is(jobProfile.getProfile().getDataType().value()));
  }

  @Test
  public void shouldUpdateProfileAssociationsOnPut() {
    JobProfileUpdateDto jobProfile = createJobProfile(
      new JobProfileUpdateDto()
        .withProfile(new JobProfile()
        .withId(UUID.randomUUID().toString())
        .withName("Bla")
        .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
        .withDataType(MARC)), "createAction", "createMapping");

    JobProfileUpdateDto jobProfileToUpdate = RestAssured.given()
      .spec(spec)
      .body(jobProfile)
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    MatchProfileUpdateDto associatedMatchProfile = RestAssured.given()
      .spec(spec)
      .body(new MatchProfileUpdateDto()
        .withProfile(new MatchProfile()
          .withId(UUID.randomUUID().toString())
          .withName("testMatch")
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withExistingRecordType(EntityType.INSTANCE)))
      .when()
      .post(MATCH_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(MatchProfileUpdateDto.class);

    ProfileAssociation jobToMatchAssociation = postProfileAssociation(
      new ProfileAssociation()
        .withDetailProfileId(associatedMatchProfile.getProfile().getId())
        .withMasterProfileId(jobProfileToUpdate.getProfile().getId())
        .withMasterProfileType(JOB_PROFILE)
        .withDetailProfileType(MATCH_PROFILE)
        .withReactTo(ReactToType.MATCH)
        .withOrder(1),
      JOB_PROFILE, MATCH_PROFILE);

    RestAssured.given()
      .spec(spec)
      .body(jobProfileToUpdate.withDeletedRelations(List.of(jobToMatchAssociation)))
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(jobProfileToUpdate.getProfile().getId()))
      .body("name", is(jobProfileToUpdate.getProfile().getName()))
      .body("tags.tagList", is(jobProfile.getProfile().getTags().getTagList()))
      .body("userInfo.lastName", is("Doe"))
      .body("userInfo.firstName", is("Jane"))
      .body("userInfo.userName", is("@janedoe"))
      .body("dataType", is(jobProfile.getProfile().getDataType().value()))
      .extract().body().asPrettyString();

    RestAssured.given()
      .spec(spec)
      .when()
      .get(JOB_PROFILES_PATH + "/" + jobProfileToUpdate.getProfile().getId() + "?withRelations=true")
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(jobProfileToUpdate.getProfile().getId()))
      .body("name", is(jobProfileToUpdate.getProfile().getName()))
      .body("userInfo.lastName", is("Doe"))
      .body("userInfo.firstName", is("Jane"))
      .body("userInfo.userName", is("@janedoe"))
      .body("dataType", is(jobProfile.getProfile().getDataType().value()))
      .body("parentProfiles", is(empty()));
  }

  @Test
  public void shouldReturnBadRequestOnPutJobProfileWithInvalidField() {
    JobProfileUpdateDto jobProfile2 = createJobProfile(jobProfile_2, "createAction", "createMapping");
    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(jobProfile2)
      .when()
      .post(JOB_PROFILES_PATH);
    Assert.assertThat(createResponse.statusCode(), is(HttpStatus.SC_CREATED));
    JobProfileUpdateDto jobProfile = createResponse.body().as(JobProfileUpdateDto.class);

    JsonObject jobProfileJson = JsonObject.mapFrom(jobProfile)
      .put("invalidField", "value");

    RestAssured.given()
      .spec(spec)
      .body(jobProfileJson.encode())
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldReturnNotFoundOnGetById() {
    RestAssured.given()
      .spec(spec)
      .when()
      .get(JOB_PROFILES_PATH + "/" + UUID.randomUUID().toString())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnProfileOnGetById() {
    JobProfileUpdateDto jobProfile3 = createJobProfile(jobProfile_3, "createAction", "createMapping");
    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(jobProfile3)
      .when()
      .post(JOB_PROFILES_PATH);
    Assert.assertThat(createResponse.statusCode(), is(HttpStatus.SC_CREATED));
    JobProfileUpdateDto jobProfile = createResponse.body().as(JobProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(jobProfile.getProfile().getId()))
      .body("name", is(jobProfile.getProfile().getName()))
      .body("tags.tagList", is(jobProfile.getProfile().getTags().getTagList()))
      .body("userInfo.lastName", is("Doe"))
      .body("userInfo.firstName", is("Jane"))
      .body("userInfo.userName", is("@janedoe"))
      .body("dataType", is(jobProfile.getProfile().getDataType().value()));
  }

  @Test
  public void shouldReturnBadRequestOnPostJobProfileWithEmptyAssociations() {
    RestAssured.given()
      .spec(spec)
      .body(jobProfile_3)
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

    @Test
  public void shouldUnlinkOneActionProfileFromTwoIdenticalOnes() {

    //create action profile
    String actionProfileId = UUID.randomUUID().toString();
    ActionProfileUpdateDto actionProfile = RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile().withName("testAction")
          .withId(actionProfileId)
          .withAction(CREATE)
          .withFolioRecord(INSTANCE))
      )
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    //create mapping profile
    String mappingProfileId = UUID.randomUUID().toString();
    MappingProfileUpdateDto mappingProfile = RestAssured.given()
      .spec(spec)
      .body(new MappingProfileUpdateDto()
        .withProfile(new MappingProfile().withName("testMapping")
          .withId(mappingProfileId)
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withExistingRecordType(EntityType.INSTANCE))
        .withAddedRelations(
          List.of(
            new ProfileAssociation()
              .withMasterProfileId(actionProfile.getId())
              .withDetailProfileId(mappingProfileId)
              .withMasterProfileType(ACTION_PROFILE)
              .withDetailProfileType(ProfileType.MAPPING_PROFILE)
              .withOrder(0)
          )
        )
      )
      .when()
      .post(MAPPING_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(MappingProfileUpdateDto.class);

    //create job profile
    String jobProfileId = UUID.randomUUID().toString();
    JobProfileUpdateDto jobProfile = RestAssured.given()
      .spec(spec)
      .body(new JobProfileUpdateDto()
        .withProfile(new JobProfile()
          .withId(jobProfileId)
          .withName("testJob")
          .withDataType(MARC))
        .withAddedRelations(List.of(
          new ProfileAssociation()
            .withMasterProfileId(jobProfileId)
            .withDetailProfileId(actionProfile.getId())
            .withMasterProfileType(JOB_PROFILE)
            .withDetailProfileType(ACTION_PROFILE)
            .withOrder(0),
          new ProfileAssociation()
            .withMasterProfileId(jobProfileId)
            .withDetailProfileId(actionProfile.getId())
            .withMasterProfileType(JOB_PROFILE)
            .withDetailProfileType(ACTION_PROFILE)
            .withOrder(1)
          )
        )
      )
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    ProfileAssociationCollection profileAssociationCollection = RestAssured.given()
      .spec(spec)
      .queryParam("master", JOB_PROFILE.value())
      .queryParam("detail", ACTION_PROFILE.value())
      .when()
      .get(ASSOCIATED_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract().body().as(ProfileAssociationCollection.class);
    Assert.assertEquals(2, profileAssociationCollection.getTotalRecords().intValue());

    RestAssured.given()
      .spec(spec)
      .body(jobProfile
        .withAddedRelations(null)
        .withDeletedRelations(List.of(profileAssociationCollection.getProfileAssociations().get(0)))
      )
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(jobProfile.getProfile().getId()))
      .body("name", is(jobProfile.getProfile().getName()))
      .body("dataType", is(jobProfile.getProfile().getDataType().value()))
      .extract().body().asPrettyString();

    profileAssociationCollection = RestAssured.given()
      .spec(spec)
      .queryParam("master", JOB_PROFILE.value())
      .queryParam("detail", ACTION_PROFILE.value())
      .when()
      .get(ASSOCIATED_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract().body().as(ProfileAssociationCollection.class);
    Assert.assertEquals(1, profileAssociationCollection.getTotalRecords().intValue());

  }


  @Test
  public void shouldUnlinkActionsMirrorAssociationsWithEqualsMatchProfiles() {

    JobProfileUpdateDto jobProfileBody = createJobProfile(new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(UUID.randomUUID().toString())
        .withName("testJob")
        .withDataType(MARC)), "createAction", "createMapping");
    //create job profile
    JobProfileUpdateDto jobProfile = RestAssured.given()
      .spec(spec)
      .body(jobProfileBody)
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    //create matchProfile with associations to jobProfile
    String matchProfileId = UUID.randomUUID().toString();
    MatchProfileUpdateDto matchProfile = RestAssured.given()
      .spec(spec)
      .body(new MatchProfileUpdateDto()
        .withProfile(new MatchProfile()
          .withId(matchProfileId)
          .withName("testMatch")
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withExistingRecordType(EntityType.INSTANCE)
          ).withAddedRelations(
          List.of(
            new ProfileAssociation()
              .withMasterProfileId(jobProfile.getProfile().getId())
              .withDetailProfileId(matchProfileId)
              .withMasterProfileType(JOB_PROFILE)
              .withDetailProfileType(MATCH_PROFILE)
              .withOrder(0)
            ,new ProfileAssociation()
              .withMasterProfileId(jobProfile.getProfile().getId())
              .withDetailProfileId(matchProfileId)
              .withMasterProfileType(JOB_PROFILE)
              .withDetailProfileType(MATCH_PROFILE)
              .withOrder(1)
          )))
      .when()
      .post(MATCH_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(MatchProfileUpdateDto.class);

     //create action profile
     String actionProfileId = UUID.randomUUID().toString();
     ActionProfileUpdateDto actionProfile = RestAssured.given()
       .spec(spec)
       .body(new ActionProfileUpdateDto()
         .withProfile(new ActionProfile().withName("testAction")
           .withId(actionProfileId)
           .withAction(CREATE)
           .withFolioRecord(INSTANCE))
       )
       .when()
       .post(ACTION_PROFILES_PATH)
       .then()
       .statusCode(HttpStatus.SC_CREATED)
       .extract().as(ActionProfileUpdateDto.class);

     String mappingProfileId = UUID.randomUUID().toString();
     MappingProfileUpdateDto mappingProfile = RestAssured.given()
       .spec(spec)
       .body(new MappingProfileUpdateDto()
         .withProfile(new MappingProfile().withName("testMapping")
           .withId(mappingProfileId)
           .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
           .withExistingRecordType(EntityType.INSTANCE))
         .withAddedRelations(
           List.of(
             new ProfileAssociation()
               .withMasterProfileId(actionProfile.getId())
               .withDetailProfileId(mappingProfileId)
               .withMasterProfileType(ACTION_PROFILE)
               .withDetailProfileType(ProfileType.MAPPING_PROFILE)
               .withOrder(0)
             )
         )
       )
       .when()
       .post(MAPPING_PROFILES_PATH)
       .then()
       .statusCode(HttpStatus.SC_CREATED)
       .extract().as(MappingProfileUpdateDto.class);

     ProfileAssociation match1ToMatchAction =
       new ProfileAssociation()
         .withMasterProfileId(matchProfile.getAddedRelations().get(0).getDetailProfileId())
         .withDetailProfileId(actionProfileId)
         .withMasterProfileType(MATCH_PROFILE)
         .withDetailProfileType(ACTION_PROFILE)
         .withMasterWrapperId(matchProfile.getAddedRelations().get(0).getDetailWrapperId())
         .withOrder(0)
         .withReactTo(MATCH);

     ProfileAssociation match1ToNonMatchAction =
       new ProfileAssociation()
         .withMasterProfileId(matchProfile.getAddedRelations().get(0).getDetailProfileId())
         .withDetailProfileId(actionProfileId)
         .withMasterProfileType(MATCH_PROFILE)
         .withDetailProfileType(ACTION_PROFILE)
         .withMasterWrapperId(matchProfile.getAddedRelations().get(0).getDetailWrapperId())
         .withOrder(0)
         .withReactTo(NON_MATCH);

     ProfileAssociation match2ToMatchAction =
       new ProfileAssociation()
         .withMasterProfileId(matchProfile.getAddedRelations().get(1).getDetailProfileId())
         .withDetailProfileId(actionProfileId)
         .withMasterProfileType(MATCH_PROFILE)
         .withDetailProfileType(ACTION_PROFILE)
         .withMasterWrapperId(matchProfile.getAddedRelations().get(1).getDetailWrapperId())
         .withOrder(0)
         .withReactTo(MATCH);

     ProfileAssociation match2ToNonMatchAction =
       new ProfileAssociation()
         .withMasterProfileId(matchProfile.getAddedRelations().get(1).getDetailProfileId())
         .withDetailProfileId(actionProfileId)
         .withMasterProfileType(MATCH_PROFILE)
         .withDetailProfileType(ACTION_PROFILE)
         .withMasterWrapperId(matchProfile.getAddedRelations().get(1).getDetailWrapperId())
         .withOrder(0)
         .withReactTo(NON_MATCH);

     RestAssured.given()
       .spec(spec)
       .body(jobProfile.withAddedRelations(
         List.of(match1ToMatchAction, match1ToNonMatchAction, match2ToMatchAction, match2ToNonMatchAction)
       ))
       .when()
       .put(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId())
       .then()
       .statusCode(HttpStatus.SC_OK)
       .body("id", is(jobProfile.getProfile().getId()))
       .body("name", is(jobProfile.getProfile().getName()))
       .body("dataType", is(jobProfile.getProfile().getDataType().value()))
       .extract().body().asPrettyString();

    Object resp2 = RestAssured.given()
      .spec(spec)
      .when()
      .get(MATCH_PROFILES_PATH + "?withRelations=true")
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract().as(Object.class);
    Assert.assertNotNull(resp2);

    ProfileAssociationCollection profileAssociationCollection = RestAssured.given()
       .spec(spec)
       .queryParam("master", MATCH_PROFILE.value())
       .queryParam("detail", ACTION_PROFILE.value())
       .when()
       .get(ASSOCIATED_PROFILES_PATH)
       .then()
       .statusCode(HttpStatus.SC_OK)
       .extract().body().as(ProfileAssociationCollection.class);
    Assert.assertEquals(4,profileAssociationCollection.getTotalRecords().intValue());

     RestAssured.given()
       .spec(spec)
       .body(jobProfile
         .withAddedRelations(null)
         .withDeletedRelations(List.of(profileAssociationCollection.getProfileAssociations().get(0)))
       )
       .when()
       .put(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId())
       .then()
       .statusCode(HttpStatus.SC_OK)
       .body("id", is(jobProfile.getProfile().getId()))
       .body("name", is(jobProfile.getProfile().getName()))
       .body("dataType", is(jobProfile.getProfile().getDataType().value()))
       .extract().body().asPrettyString();

     profileAssociationCollection = RestAssured.given()
       .spec(spec)
       .queryParam("master", MATCH_PROFILE.value())
       .queryParam("detail", ACTION_PROFILE.value())
       .when()
       .get(ASSOCIATED_PROFILES_PATH)
       .then()
       .statusCode(HttpStatus.SC_OK)
       .extract().body().as(ProfileAssociationCollection.class);
     Assert.assertEquals(3, profileAssociationCollection.getTotalRecords().intValue());

     ProfileAssociation profileAssociation2deleteWithNullOrder =
       profileAssociationCollection.getProfileAssociations().get(0).withOrder(null);

     RestAssured.given()
      .spec(spec)
      .body(jobProfile
        .withAddedRelations(null)
        .withDeletedRelations(List.of(profileAssociation2deleteWithNullOrder))
      )
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(jobProfile.getProfile().getId()))
      .body("name", is(jobProfile.getProfile().getName()))
      .body("dataType", is(jobProfile.getProfile().getDataType().value()))
      .extract().body().asPrettyString();

    profileAssociationCollection = RestAssured.given()
      .spec(spec)
      .queryParam("master", MATCH_PROFILE.value())
      .queryParam("detail", ACTION_PROFILE.value())
      .when()
      .get(ASSOCIATED_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract().body().as(ProfileAssociationCollection.class);
    Assert.assertEquals(2, profileAssociationCollection.getTotalRecords().intValue());
  }

  @Test
  public void shouldReturnBadRequestOnPutWithUpdateActionProfileWithoutMatchProfile() {
    var actionProfileId = UUID.randomUUID().toString();

    JobProfileUpdateDto jobProfile = createJobProfile(jobProfile_1, "createAction", "createMapping");
    var jobProfileToUpdate = RestAssured.given()
      .spec(spec)
      .body(jobProfile)
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile().withName("testAction")
          .withAction(UPDATE)
          .withFolioRecord(INSTANCE)
          .withId(actionProfileId))
      )
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    var invalidAssociation = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileId)
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobProfile.getId());

    RestAssured.given()
      .spec(spec)
      .body(jobProfileToUpdate.withAddedRelations(List.of(invalidAssociation)))
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is(String.format("ActionProfile with id '%s' and action UPDATE requires linked MatchProfile", actionProfileId)))
      ));
  }

  @Test
  public void shouldReturnBadRequestOnPutWithStandaloneModifyAction() {
    var actionProfileId = UUID.randomUUID().toString();
    var actionProfileUpdateDto = new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withName("testAction")
        .withAction(MODIFY)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)
        .withId(actionProfileId));

    var jobProfile = createJobProfileWithAction(jobProfile_1, actionProfileUpdateDto);

    RestAssured.given()
      .spec(spec)
      .body(jobProfile)
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Modify action cannot be used as a standalone action"))
      ));
  }

  @Test
  public void shouldReturnBadRequestOnPutWithStandaloneModifyActionAfterMatch() {
    var actionProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();

    var matchProfile = new MatchProfileUpdateDto()
      .withProfile(new MatchProfile()
        .withId(matchProfileId)
        .withName("testMatch")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE));

    var jobProfile = createJobProfileWithMatch(jobProfile_1, matchProfile);

    RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile().withName("testAction")
          .withAction(MODIFY)
          .withFolioRecord(MARC_BIBLIOGRAPHIC)
          .withId(actionProfileId)))
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    var invalidAssociationActionToMatch = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileId)
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId);

    var invalidAssociationMatchToJobProfile = new ProfileAssociation()
      .withDetailProfileType(MATCH_PROFILE)
      .withDetailProfileId(matchProfileId)
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobProfile.getId());

    var jobProfileToUpdate = RestAssured.given()
      .spec(spec)
      .body(jobProfile)
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .body(jobProfileToUpdate.withAddedRelations(List.of(invalidAssociationMatchToJobProfile, invalidAssociationActionToMatch)))
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Modify action cannot be used right after a Match"))
      ));
  }

  @Test
  public void shouldDeleteAssociationsWithDetailProfilesOnDelete() {
    // creation detail-profiles
    String actionProfileId = UUID.randomUUID().toString();
    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile()
          .withId(actionProfileId)
          .withName("testAction")
          .withAction(UPDATE)
          .withFolioRecord(MARC_BIBLIOGRAPHIC)))
      .when()
      .post(ACTION_PROFILES_PATH);
    Assert.assertThat(createResponse.statusCode(), is(HttpStatus.SC_CREATED));
    ActionProfileUpdateDto associatedActionProfile = createResponse.body().as(ActionProfileUpdateDto.class);

    String matchProfileId = UUID.randomUUID().toString();
    createResponse = RestAssured.given()
      .spec(spec)
      .body(new MatchProfileUpdateDto()
        .withProfile(new MatchProfile()
          .withId(matchProfileId)
          .withName("testMatch")
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withExistingRecordType(EntityType.INSTANCE)))
      .when()
      .post(MATCH_PROFILES_PATH);
    Assert.assertThat(createResponse.statusCode(), is(HttpStatus.SC_CREATED));
    MatchProfileUpdateDto associatedMatchProfile = createResponse.body().as(MatchProfileUpdateDto.class);

    String jobProfileId = UUID.randomUUID().toString();

    ProfileAssociation jobToMatchAssociation = new ProfileAssociation()
      .withId(UUID.randomUUID().toString())
      .withDetailProfileId(associatedMatchProfile.getProfile().getId())
      .withMasterProfileId(jobProfileId)
      .withMasterProfileType(JOB_PROFILE)
      .withDetailProfileType(MATCH_PROFILE)
      .withOrder(1);

    JobProfileUpdateDto jobProfile = new JobProfileUpdateDto()
      .withProfile(new JobProfile().withId(jobProfileId).withName("Bla")
        .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
        .withDataType(MARC))
      .withAddedRelations(List.of(jobToMatchAssociation));

    createResponse = RestAssured.given()
      .spec(spec)
      .body(jobProfile)
      .when()
      .post(JOB_PROFILES_PATH);
    Assert.assertThat(createResponse.statusCode(), is(HttpStatus.SC_CREATED));
    JobProfileUpdateDto profileToDelete = createResponse.body().as(JobProfileUpdateDto.class);

    ProfileAssociation jobToActionAssociation =
      postProfileAssociation(new ProfileAssociation()
        .withId(UUID.randomUUID().toString())
        .withDetailProfileId(associatedActionProfile.getProfile().getId())
        .withMasterProfileId(jobProfileId)
        .withMasterProfileType(JOB_PROFILE)
        .withDetailProfileType(ACTION_PROFILE)
        .withOrder(1),
        JOB_PROFILE, ACTION_PROFILE);

    // deleting job profile
    RestAssured.given()
      .spec(spec)
      .when()
      .delete(JOB_PROFILES_PATH + "/" + profileToDelete.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    // receiving deleted associations
    RestAssured.given()
      .spec(spec)
      .queryParam("master", JOB_PROFILE.value())
      .queryParam("detail", ACTION_PROFILE.value())
      .when() // null
      .get(ASSOCIATED_PROFILES_PATH + "/" + jobToActionAssociation.getId())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);

    RestAssured.given()
      .spec(spec)
      .queryParam("master", JOB_PROFILE.value())
      .queryParam("detail", MATCH_PROFILE.value())
      .when()
      .get(ASSOCIATED_PROFILES_PATH + "/" + jobToMatchAssociation.getId())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnUnprocessableEntityOnPutJobProfileWithExistingName() {
    JobProfileUpdateDto jobProfile = createJobProfile(jobProfile_1, "createAction", "createMapping");

    RestAssured.given()
      .spec(spec)
      .body(jobProfile)
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(is(HttpStatus.SC_CREATED));

    JobProfileUpdateDto newJobProfile = createJobProfile(jobProfile_2, "createAction2", "createMapping2");
    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(newJobProfile)
      .when()
      .post(JOB_PROFILES_PATH);
    Assert.assertThat(createResponse.statusCode(), is(HttpStatus.SC_CREATED));
    JobProfileUpdateDto createdJobProfile = createResponse.body().as(JobProfileUpdateDto.class);

    createdJobProfile.getProfile().setName(jobProfile_1.getProfile().getName());
    RestAssured.given()
      .spec(spec)
      .body(createdJobProfile)
      .when()
      .put(JOB_PROFILES_PATH + "/" + createdJobProfile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldReturnMarkedAndUnmarkedAsDeletedProfilesOnGetWhenParameterDeletedIsTrue() {
    createProfiles();

    JobProfileUpdateDto jobProfile = new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withName("ProfileToDelete")
        .withDataType(MARC));

    JobProfileUpdateDto jobProfileToDelete = createJobProfile(jobProfile, "createAction", "createMapping");

    jobProfileToDelete = RestAssured.given()
      .spec(spec)
      .body(jobProfileToDelete)
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .log().all()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().body().as(JobProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .when()
      .delete(JOB_PROFILES_PATH + "/" + jobProfileToDelete.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    RestAssured.given()
      .spec(spec)
      .when()
      .param("showDeleted", true)
      .get(JOB_PROFILES_PATH)
      .then()
      .log().all()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(4));
  }

  @Test
  public void shouldReturnOnlyUnmarkedAsDeletedProfilesOnGetWhenParameterDeletedIsNotPassed() {
    createProfiles();

    JobProfileUpdateDto jobProfile = new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withName("ProfileToDelete")
        .withDataType(MARC));

    JobProfileUpdateDto jobProfileToDelete = createJobProfile(jobProfile, "createAction", "createMapping");

    jobProfileToDelete = RestAssured.given()
      .spec(spec)
      .body(jobProfileToDelete)
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .log().all()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().body().as(JobProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .when()
      .delete(JOB_PROFILES_PATH + "/" + jobProfileToDelete.getProfile().getId())
      .then()
      .log().all()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(3))
      .body("jobProfiles*.deleted", everyItem(is(false)))
      .body("jobProfiles*.hidden", everyItem(is(false)));
  }

  @Test
  public void shouldCreateProfileOnPostWhenWasDeletedProfileWithSameNameBefore() {
    JobProfileUpdateDto jobProfile = new JobProfileUpdateDto()
      .withProfile(new JobProfile().withName("profileName")
      .withDataType(MARC));

    JobProfileUpdateDto jobProfileToDelete = createJobProfile(jobProfile, "createAction", "createMapping");

    jobProfileToDelete = RestAssured.given()
      .spec(spec)
      .body(jobProfileToDelete)
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .log().all()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().body().as(JobProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .when()
      .delete(JOB_PROFILES_PATH + "/" + jobProfileToDelete.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    JobProfileUpdateDto jobProfile2 = createJobProfile(jobProfile, "createAction2", "createMapping2");

    RestAssured.given()
      .spec(spec)
      .body(jobProfile2)
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .log().all()
      .statusCode(HttpStatus.SC_CREATED);
  }

  @Test
  public void shouldReturnBadRequestWhenChildOrParentProfileIsNotEmptyOnPost() {
    JobProfileUpdateDto jobProfile = createJobProfile(jobProfileNotEmptyChildAndParent, "createAction",
      "createMapping");

    RestAssured.given()
      .spec(spec)
      .body(jobProfile)
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Job profile read-only 'child' field should be empty"))
      .body("errors[1].message", is("Job profile read-only 'parent' field should be empty"));
  }

  @Test
  public void shouldReturnBadRequestWhenChildOrParentProfileIsNotEmptyOnPut() {
    JobProfileUpdateDto jobProfileUpdateDto = createJobProfile(jobProfile_2, "createAction",
      "createMapping");

    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(jobProfileUpdateDto)
      .when()
      .post(JOB_PROFILES_PATH);
    Assert.assertThat(createResponse.statusCode(), is(HttpStatus.SC_CREATED));
    JobProfileUpdateDto jobProfile = createResponse.body().as(JobProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .body(jobProfileNotEmptyChildAndParent)
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Job profile read-only 'child' field should be empty"))
      .body("errors[1].message", is("Job profile read-only 'parent' field should be empty"));
  }


  private void createProfiles() {
    List<Tuple> jobProfilesToPost = Arrays.asList(
      Tuple.of(jobProfile_1, "actionCreate1", "mappingCreate1"),
      Tuple.of(jobProfile_2, "actionCreate2", "mappingCreate2"),
      Tuple.of(jobProfile_3, "actionCreate3", "mappingCreate3"));
    for (Tuple profile : jobProfilesToPost) {
      JobProfileUpdateDto jobProfile = createJobProfile(
        profile.get(JobProfileUpdateDto.class, 0),
        profile.getString(1),
        profile.getString(2));
      RestAssured.given()
        .spec(spec)
        .body(jobProfile)
        .when()
        .post(JOB_PROFILES_PATH)
        .then()
        .statusCode(HttpStatus.SC_CREATED);
    }
  }

  private ProfileAssociation postProfileAssociation(ProfileAssociation profileAssociation, ProfileType masterType, ProfileType detailType) {
    Response createResponse = RestAssured.given()
      .spec(spec)
      .queryParam("master", masterType.value())
      .queryParam("detail", detailType.value())
      .body(profileAssociation)
      .when()
      .post(ASSOCIATED_PROFILES_PATH);
    Assert.assertThat(createResponse.statusCode(), is(HttpStatus.SC_CREATED));
    return createResponse.body().as(ProfileAssociation.class);
  }

  @Override
  public void clearTables(TestContext context) {
    Async async = context.async();
    PostgresClient pgClient = PostgresClient.getInstance(vertx, TENANT_ID);
    pgClient.delete(PROFILE_WRAPPERS_TABLE_NAME, new Criterion(), event1 ->
      pgClient.delete(SNAPSHOTS_TABLE_NAME, new Criterion(), event2 ->
      pgClient.delete(JOB_TO_ACTION_PROFILES_TABLE, new Criterion(), event3 ->
        pgClient.delete(JOB_TO_MATCH_PROFILES_TABLE, new Criterion(), event4 ->
          pgClient.delete(ACTION_TO_MAPPING_PROFILES_TABLE, new Criterion(), event5 ->
            pgClient.delete(MATCH_TO_ACTION_PROFILES_TABLE_NAME, new Criterion(), event6 ->
              pgClient.delete(JOB_PROFILES_TABLE_NAME, new Criterion(), event7 ->
                pgClient.delete(MATCH_PROFILES_TABLE_NAME, new Criterion(), event8 ->
                  pgClient.delete(ACTION_PROFILES_TABLE_NAME, new Criterion(), event9 ->
                    pgClient.delete(ACTION_TO_ACTION_PROFILES_TABLE_NAME, new Criterion(), event10 ->
                      pgClient.delete(MAPPING_PROFILES_TABLE_NAME, new Criterion(), event11 ->
                        pgClient.delete(MATCH_TO_MATCH_PROFILES_TABLE_NAME, new Criterion(), event12 ->
                          pgClient.delete(PROFILE_WRAPPERS_TABLE, new Criterion(), event13 -> {
                            if (event12.failed()) {
                            context.fail(event12.cause());
                          }
                          async.complete();
                        })))))))))))));
  }
}
