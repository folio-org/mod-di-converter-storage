package org.folio.rest.impl;

import static java.util.Collections.emptyList;
import static org.folio.rest.impl.ActionProfileTest.ACTION_PROFILES_PATH;
import static org.folio.rest.impl.ActionProfileTest.ACTION_PROFILES_TABLE_NAME;
import static org.folio.rest.impl.MatchProfileTest.MATCH_PROFILES_PATH;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.CREATE;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.DELETE;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.MODIFY;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.UPDATE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.INSTANCE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.MARC_AUTHORITY;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC;
import static org.folio.rest.jaxrs.model.JobProfile.DataType.DELIMITED;
import static org.folio.rest.jaxrs.model.JobProfile.DataType.EDIFACT;
import static org.folio.rest.jaxrs.model.JobProfile.DataType.MARC;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MAPPING_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;
import static org.folio.rest.jaxrs.model.ReactToType.MATCH;
import static org.folio.rest.jaxrs.model.ReactToType.NON_MATCH;
import static org.folio.services.JobProfileServiceImpl.DELETE_MARC_AUTHORITY_CANNOT_BE_NEXT_TO_OTHER_ACTIONS;
import static org.folio.services.JobProfileServiceImpl.INVALID_DELETE_MARC_AUTHORITY_ACTION_PROFILE_PLACEMENT;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.folio.rest.impl.association.wrapper.MatchProfileWrapper;
import org.folio.rest.impl.association.wrapper.ProfileWrapper;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfile.FolioRecord;
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
import org.folio.rest.jaxrs.model.Tags;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class JobProfileTest extends AbstractRestVerticleTest {

  static final String JOB_PROFILES_PATH = "/data-import-profiles/jobProfiles";
  static final String MAPPING_PROFILES_TABLE_NAME = "mapping_profiles";
  static final String MATCH_PROFILES_TABLE_NAME = "match_profiles";
  static final String MAPPING_PROFILES_PATH = "/data-import-profiles/mappingProfiles";

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
  static JobProfileUpdateDto jobProfileNotEmptyChildAndParent = new JobProfileUpdateDto()
    .withProfile(new JobProfile()
      .withName("Job profile with child and parent")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum")))
      .withDataType(MARC)
      .withChildProfiles(List.of(new ProfileSnapshotWrapper().withId(UUID.randomUUID().toString())))
      .withParentProfiles(List.of(new ProfileSnapshotWrapper().withId(UUID.randomUUID().toString()))));

  private static final String JOB_PROFILES_TABLE_NAME = "job_profiles";
  private static final String ASSOCIATED_PROFILES_PATH = "/data-import-profiles/profileAssociations";
  private static final String PROFILE_WRAPPERS_TABLE_NAME = "profile_wrappers";
  private static final String ASSOCIATIONS_TABLE = "profile_associations";
  private static final String SNAPSHOTS_TABLE_NAME = "profile_snapshots";
  private static final String JOB_PROFILE_UUID = "b81c283c-131d-4470-ab91-e92bb415c000";
  private static final String DEFAULT_CREATE_SRS_MARC_AUTHORITY_JOB_PROFILE_ID = "6eefa4c6-bbf7-4845-ad82-de7fc5abd0e3";
  private static final String DEFAULT_DELETE_MARC_AUTHORITY_ACTION_PROFILE_ID = "fabd9a3e-33c3-49b7-864d-c5af830d9990";

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

  private final List<String> defaultJobProfileIds = Arrays.asList(
    "d0ebb7b0-2f0f-11eb-adc1-0242ac120002", //OCLC_CREATE_INSTANCE_JOB_PROFILE_ID
    "91f9b8d6-d80e-4727-9783-73fb53e3c786", //OCLC_UPDATE_INSTANCE_JOB_PROFILE_ID
    "80898dee-449f-44dd-9c8e-37d5eb469b1d", //DEFAULT_CREATE_HOLDINGS_AND_SRS_MARC_HOLDINGS_JOB_PROFILE_ID
    "1a338fcd-3efc-4a03-b007-394eeb0d5fb9"  //DEFAULT_DELETE_MARC_AUTHORITY_JOB_PROFILE_ID
  );

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
      .body("jobProfiles*.hidden", everyItem(is(false)))
      .body("totalRecords", is(3));
  }

  @Test
  public void shouldReturnSortedProfilesOnGetWhenSortByIsSpecified() {
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

    Assert.assertTrue(
      jobProfileList.get(0).getMetadata().getCreatedDate().after(jobProfileList.get(1).getMetadata().getCreatedDate()));
    Assert.assertTrue(
      jobProfileList.get(1).getMetadata().getCreatedDate().after(jobProfileList.get(2).getMetadata().getCreatedDate()));
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

  @Test
  public void shouldCreateProfileOnPost() {
    var jobProfile = createJobProfile(jobProfile_1, "testActionCreate", "testMappingCreate");

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
      .body("errors[0].message", is("Job profile 'Bla' already exists"));
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
          is(String.format("ActionProfile with id '%s' and action UPDATE requires linked MatchProfile",
            actionProfileId)))
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
  @SuppressWarnings("checkstyle:MethodLength")
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
    RestAssured.given()
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
  @SuppressWarnings("checkstyle:MethodLength")
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
    RestAssured.given()
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
    RestAssured.given()
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
  @SuppressWarnings("checkstyle:MethodLength")
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
    RestAssured.given()
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
    RestAssured.given()
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
  @SuppressWarnings("checkstyle:MethodLength")
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
      .put(JOB_PROFILES_PATH + "/" + UUID.randomUUID())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldReturnNotFoundOnPut() {
    RestAssured.given()
      .spec(spec)
      .body(jobProfile_2)
      .when()
      .put(JOB_PROFILES_PATH + "/" + UUID.randomUUID())
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
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    JobProfileUpdateDto jobProfile = createResponse.body().as(JobProfileUpdateDto.class);

    jobProfile.getProfile().setDescription("test");
    jobProfile.getProfile().setDataType(DELIMITED);
    jobProfile.getAddedRelations().getFirst().setId(UUID.randomUUID().toString());

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
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    JobProfileUpdateDto jobProfile = createResponse.body().as(JobProfileUpdateDto.class);

    jobProfile.getProfile().setName("updated name");
    jobProfile.getProfile().setDescription("updated description");
    jobProfile.getProfile().setDataType(EDIFACT);
    jobProfile.getAddedRelations().getFirst().setId(UUID.randomUUID().toString());

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
  @SuppressWarnings("checkstyle:MethodLength")
  public void shouldUpdateProfileAssociationsOnPut() {
    var jobUpdateDto = new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(UUID.randomUUID().toString())
        .withName("Bla")
        .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
        .withDataType(MARC));

    var matchUpdateDto = new MatchProfileUpdateDto()
      .withProfile(new MatchProfile()
        .withId(UUID.randomUUID().toString())
        .withName("testMatch")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE));

    var actionProfileUpdateDto = new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withId(UUID.randomUUID().toString())
        .withName("testAction")
        .withAction(UPDATE)
        .withFolioRecord(MARC_BIBLIOGRAPHIC));

    var mappingProfileUpdateDto = new MappingProfileUpdateDto()
      .withProfile(new MappingProfile().withName("testMapping")
        .withId(UUID.randomUUID().toString())
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE));

    JobProfileUpdateDto jobProfile = createJobProfileWithMatch(jobUpdateDto,
      matchUpdateDto,
      actionProfileUpdateDto,
      mappingProfileUpdateDto);

    JobProfileUpdateDto jobProfileToUpdate = RestAssured.given()
      .spec(spec)
      .body(jobProfile)
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .body(
        jobProfileToUpdate
          .withAddedRelations(null)
          .withDeletedRelations(List.of(jobProfile.getAddedRelations().getFirst())))
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
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
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
      .get(JOB_PROFILES_PATH + "/" + UUID.randomUUID())
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
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
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
      .body(jobProfile_3.withAddedRelations(emptyList()))
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  public void shouldUnlinkOneActionProfileFromTwoIdenticalOnes() {

    //create action profile
    var actionProfileId = UUID.randomUUID().toString();
    var actionProfile = RestAssured.given()
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
    var mappingProfileId = UUID.randomUUID().toString();
    RestAssured.given()
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
      .statusCode(HttpStatus.SC_CREATED);

    //create job profile
    var jobProfileId = UUID.randomUUID().toString();
    var jobProfile = RestAssured.given()
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

    var profileAssociationCollection = RestAssured.given()
      .spec(spec)
      .queryParam("master", JOB_PROFILE.value())
      .queryParam("detail", ACTION_PROFILE.value())
      .when()
      .get(ASSOCIATED_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract().body().as(ProfileAssociationCollection.class);
    assertEquals(2, profileAssociationCollection.getTotalRecords().intValue());

    RestAssured.given()
      .spec(spec)
      .body(jobProfile
        .withDeletedRelations(List.of(profileAssociationCollection.getProfileAssociations().getFirst()))
        .withAddedRelations(emptyList())
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
    assertEquals(1, profileAssociationCollection.getTotalRecords().intValue());
  }

  @Test
  @SuppressWarnings("checkstyle:MethodLength")
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
              .withOrder(0),
            new ProfileAssociation()
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
    RestAssured.given()
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
    assertEquals(4, profileAssociationCollection.getTotalRecords().intValue());

    RestAssured.given()
      .spec(spec)
      .body(jobProfile
        .withAddedRelations(null)
        .withDeletedRelations(List.of(profileAssociationCollection.getProfileAssociations().getFirst()))
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
    assertEquals(3, profileAssociationCollection.getTotalRecords().intValue());

    ProfileAssociation profileAssociation2deleteWithNullOrder =
      profileAssociationCollection.getProfileAssociations().getFirst().withOrder(null);

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
    assertEquals(2, profileAssociationCollection.getTotalRecords().intValue());
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
      .withMasterProfileId(jobProfileToUpdate.getProfile().getId());

    RestAssured.given()
      .spec(spec)
      .body(jobProfileToUpdate.withAddedRelations(List.of(invalidAssociation)))
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobProfileToUpdate.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is(String.format("ActionProfile with id '%s' and action UPDATE requires linked MatchProfile",
            actionProfileId)))
      ));
  }

  @Test
  public void shouldReturnBadRequestOnPutIfNoAssociations() {
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
      .body(jobProfileToUpdate
        .withDeletedRelations(jobProfileToUpdate.getAddedRelations())
        .withAddedRelations(emptyList())
      )
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobProfileToUpdate.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Job profile does not contain any associations"))
      ));
  }

  @Test
  public void shouldReturnBadRequestOnPutIfNoActionProfileAfterMatch() {
    var matchUpdateDto = new MatchProfileUpdateDto()
      .withProfile(new MatchProfile()
        .withId(UUID.randomUUID().toString())
        .withName("testMatch")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE));

    var actionProfileUpdateDto = new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withId(UUID.randomUUID().toString())
        .withName("testAction")
        .withAction(UPDATE)
        .withFolioRecord(MARC_BIBLIOGRAPHIC));

    var mappingProfileUpdateDto = new MappingProfileUpdateDto()
      .withProfile(new MappingProfile().withName("testMapping")
        .withId(UUID.randomUUID().toString())
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE));

    JobProfileUpdateDto jobProfile = createJobProfileWithMatch(jobProfile_1,
      matchUpdateDto,
      actionProfileUpdateDto,
      mappingProfileUpdateDto);

    JobProfileUpdateDto jobProfileToUpdate = RestAssured.given()
      .spec(spec)
      .body(jobProfile)
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .body(jobProfileToUpdate
        .withDeletedRelations(List.of(jobProfileToUpdate.getAddedRelations().get(1)))
        .withAddedRelations(emptyList())
      )
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobProfileToUpdate.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Linked ActionProfile was not found after MatchProfile"))
      ));
  }

  @Test
  public void shouldReturnBadRequestOnPostIfNoActionProfileAfterMatch() {
    var matchUpdateDto = new MatchProfileUpdateDto()
      .withProfile(new MatchProfile()
        .withId(UUID.randomUUID().toString())
        .withName("testMatch")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE));

    JobProfileUpdateDto jobProfile =
      createJobProfile(jobProfile_1, new MatchProfileWrapper(matchUpdateDto), MATCH_PROFILES_PATH, MATCH_PROFILE);

    RestAssured.given()
      .spec(spec)
      .body(jobProfile)
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Linked ActionProfile was not found after MatchProfile"))
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

    var mappingProfileId = UUID.randomUUID().toString();
    var mappingProfileUpdateDto = new MappingProfileUpdateDto()
      .withProfile(new MappingProfile().withName("testMapping")
        .withId(mappingProfileId)
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE));

    var jobProfile = createJobProfileWithAction(jobProfile_1, actionProfileUpdateDto, mappingProfileUpdateDto);

    var jobProfileToUpdate = RestAssured.given()
      .spec(spec)
      .body(jobProfile)
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    var invalidAssociation = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileId)
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobProfile.getId());

    RestAssured.given()
      .spec(spec)
      .body(jobProfileToUpdate.withAddedRelations(List.of(invalidAssociation)))
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobProfileToUpdate.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Modify action cannot be used as a standalone action"))
      ));
  }

  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  public void shouldReturnBadRequestOnPutWithStandaloneModifyActionAfterMatch() {
    var actionProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();

    var matchProfile = new MatchProfileUpdateDto()
      .withProfile(new MatchProfile()
        .withId(matchProfileId)
        .withName("testMatch")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE));

    var mappingProfileUpdateDto = new MappingProfileUpdateDto()
      .withProfile(new MappingProfile()
        .withId(UUID.randomUUID().toString())
        .withName("testMapping")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE));

    var actionProfileUpdateDto = new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withName("testAction")
        .withId(actionProfileId)
        .withAction(MODIFY)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)
        .withId(actionProfileId));

    var jobProfile = createJobProfileWithMatch(jobProfile_1, matchProfile,
      actionProfileUpdateDto, mappingProfileUpdateDto);

    var jobProfileToUpdate = RestAssured.given()
      .spec(spec)
      .body(jobProfile)
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    var invalidAssociationActionToMatch = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileId)
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId);

    var invalidAssociationMatchToJobProfile = new ProfileAssociation()
      .withDetailProfileType(MATCH_PROFILE)
      .withDetailProfileId(matchProfileId)
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobProfileToUpdate.getId());

    RestAssured.given()
      .spec(spec)
      .body(jobProfileToUpdate.withAddedRelations(
        List.of(invalidAssociationMatchToJobProfile, invalidAssociationActionToMatch)))
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobProfileToUpdate.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Modify action cannot be used right after a Match"))
      ));
  }

  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  public void shouldDeleteAssociationsWithDetailProfilesOnDelete() {
    // creation detail-profiles
    String actionProfileId = UUID.randomUUID().toString();
    var actionProfile = RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile()
          .withId(actionProfileId)
          .withName("testAction")
          .withAction(UPDATE)
          .withFolioRecord(MARC_BIBLIOGRAPHIC)))
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    String mappingProfileIdCreate = UUID.randomUUID().toString();
    var mappingProfile = RestAssured.given()
      .spec(spec)
      .body(new MappingProfileUpdateDto()
        .withProfile(new MappingProfile().withName("testMapping")
          .withId(mappingProfileIdCreate)
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withExistingRecordType(EntityType.INSTANCE)))
      .when()
      .post(MAPPING_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(MappingProfileUpdateDto.class);

    String matchProfileId = UUID.randomUUID().toString();
    var matchProfile = RestAssured.given()
      .spec(spec)
      .body(new MatchProfileUpdateDto()
        .withProfile(new MatchProfile()
          .withId(matchProfileId)
          .withName("testMatch")
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withExistingRecordType(EntityType.INSTANCE)))
      .when()
      .post(MATCH_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(MatchProfileUpdateDto.class);

    String jobProfileId = UUID.randomUUID().toString();

    var jobToMatchAssociation = new ProfileAssociation()
      .withId(UUID.randomUUID().toString())
      .withDetailProfileId(matchProfile.getProfile().getId())
      .withMasterProfileId(jobProfileId)
      .withMasterProfileType(JOB_PROFILE)
      .withDetailProfileType(MATCH_PROFILE)
      .withOrder(1);

    var matchToActionAssociation = new ProfileAssociation()
      .withId(UUID.randomUUID().toString())
      .withDetailProfileId(actionProfile.getProfile().getId())
      .withMasterProfileId(matchProfile.getProfile().getId())
      .withMasterProfileType(MATCH_PROFILE)
      .withDetailProfileType(ACTION_PROFILE)
      .withOrder(1);

    var actionToMappingAssociation = new ProfileAssociation()
      .withMasterProfileId(actionProfile.getProfile().getId())
      .withDetailProfileId(mappingProfile.getProfile().getId())
      .withMasterProfileType(ACTION_PROFILE)
      .withDetailProfileType(MAPPING_PROFILE)
      .withOrder(0);

    var jobProfile = new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(jobProfileId)
        .withName("Bla")
        .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
        .withDataType(MARC))
      .withAddedRelations(List.of(jobToMatchAssociation, matchToActionAssociation, actionToMappingAssociation));

    var profileToDelete = RestAssured.given()
      .spec(spec)
      .body(jobProfile)
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    ProfileAssociation jobToActionAssociation =
      postProfileAssociation(new ProfileAssociation()
          .withId(UUID.randomUUID().toString())
          .withDetailProfileId(actionProfile.getProfile().getId())
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
      .when()
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
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
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
  public void shouldHardDeleteProfileOnDeletion() {
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
      .get(JOB_PROFILES_PATH + "/" + jobProfileToDelete.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
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
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    JobProfileUpdateDto jobProfile = createResponse.body().as(JobProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .body(jobProfileNotEmptyChildAndParent.withAddedRelations(null))
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Job profile read-only 'child' field should be empty"))
      .body("errors[1].message", is("Job profile read-only 'parent' field should be empty"));
  }

  @Test
  @SuppressWarnings("checkstyle:LineLength")
  public void shouldReturnCreatedOnPostJobProfileWithDeleteMarcAuthorityActionAsFirstActionUnderMarcAuthorityMatchProfile() {
    final var jobProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();
    var actionProfileId = UUID.randomUUID().toString();
    var mappingProfileId = UUID.randomUUID().toString();

    postMappingProfile(mappingProfileId, "Delete MARC-Authority",
      EntityType.MARC_AUTHORITY, EntityType.MARC_AUTHORITY);
    postActionProfileWithMapping(actionProfileId, "Delete MARC-Authority",
      DELETE, MARC_AUTHORITY, mappingProfileId);
    postMatchProfile(matchProfileId, "Match MARC-Authority",
      EntityType.MARC_AUTHORITY, EntityType.MARC_AUTHORITY);

    var jobToMatchAssociation = new ProfileAssociation()
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobProfileId)
      .withDetailProfileType(MATCH_PROFILE)
      .withDetailProfileId(matchProfileId)
      .withOrder(0);

    var matchToActionAssociation = new ProfileAssociation()
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId)
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileId)
      .withReactTo(MATCH)
      .withOrder(0);

    RestAssured.given()
      .spec(spec)
      .body(new JobProfileUpdateDto()
        .withProfile(new JobProfile()
          .withId(jobProfileId)
          .withName("Delete MARC-Authority")
          .withDataType(MARC))
        .withAddedRelations(List.of(jobToMatchAssociation, matchToActionAssociation)))
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
  }

  @Test
  @SuppressWarnings("checkstyle:LineLength")
  public void shouldReturnCreatedOnPostJobProfileWithDefaultDeleteMarcAuthorityActionAsFirstActionUnderMarcAuthorityMatchProfile() {
    var jobProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();

    postMatchProfile(matchProfileId, "Match MARC-Authority",
      EntityType.MARC_AUTHORITY, EntityType.MARC_AUTHORITY);

    var jobToMatchAssociation = new ProfileAssociation()
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobProfileId)
      .withDetailProfileType(MATCH_PROFILE)
      .withDetailProfileId(matchProfileId)
      .withOrder(0);

    var matchToActionAssociation = new ProfileAssociation()
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId)
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(DEFAULT_DELETE_MARC_AUTHORITY_ACTION_PROFILE_ID)
      .withReactTo(MATCH)
      .withOrder(0);

    RestAssured.given()
      .spec(spec)
      .body(new JobProfileUpdateDto()
        .withProfile(new JobProfile()
          .withId(jobProfileId)
          .withName("Delete MARC-Authority")
          .withDataType(MARC))
        .withAddedRelations(List.of(jobToMatchAssociation, matchToActionAssociation)))
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
  }

  @Test
  public void shouldReturnUnprocessableEntityOnPostJobProfileWithDeleteMarcAuthorityUnderNonMatchBranch() {
    var jobProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();
    var actionProfileId = UUID.randomUUID().toString();

    postMatchProfile(matchProfileId, "Match Marc-Authority",
      EntityType.MARC_AUTHORITY, EntityType.MARC_AUTHORITY);
    postActionProfile(actionProfileId, "Delete MARC-Authority", DELETE, MARC_AUTHORITY);

    var jobToMatchAssociation = new ProfileAssociation()
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobProfileId)
      .withDetailProfileType(MATCH_PROFILE)
      .withDetailProfileId(matchProfileId)
      .withOrder(0);
    var matchToActionAssociation = new ProfileAssociation()
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId)
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileId)
      .withReactTo(NON_MATCH)
      .withOrder(0);
    assertEquals(NON_MATCH, matchToActionAssociation.getReactTo());

    RestAssured.given()
      .spec(spec)
      .body(new JobProfileUpdateDto()
        .withProfile(new JobProfile()
          .withId(jobProfileId)
          .withName("Delete MARC-Authority")
          .withDataType(MARC))
        .withAddedRelations(List.of(jobToMatchAssociation, matchToActionAssociation)))
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is(INVALID_DELETE_MARC_AUTHORITY_ACTION_PROFILE_PLACEMENT))
      ));
  }

  @Test
  public void shouldReturnUnprocessableEntityOnPostJobProfileIfDeleteMarcAuthorityNotFirstInMatchBranch() {
    final var jobProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();
    var updateActionProfileId = UUID.randomUUID().toString();
    var deleteActionProfileId = UUID.randomUUID().toString();

    postMatchProfile(matchProfileId, "Match Marc-Authority",
      EntityType.MARC_AUTHORITY, EntityType.MARC_AUTHORITY);
    postActionProfile(updateActionProfileId, "Update Marc-Authority", UPDATE, MARC_AUTHORITY);
    postActionProfile(deleteActionProfileId, "Delete MARC-Authority", DELETE, MARC_AUTHORITY);

    var jobToMatchAssociation = new ProfileAssociation()
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobProfileId)
      .withDetailProfileType(MATCH_PROFILE)
      .withDetailProfileId(matchProfileId)
      .withOrder(0);
    var matchToUpdateActionAssociation = new ProfileAssociation()
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId)
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(updateActionProfileId)
      .withReactTo(MATCH)
      .withOrder(0);
    var matchToDeleteActionAssociation = new ProfileAssociation()
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId)
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(deleteActionProfileId)
      .withReactTo(MATCH)
      .withOrder(1);

    RestAssured.given()
      .spec(spec)
      .body(new JobProfileUpdateDto()
        .withProfile(new JobProfile()
          .withId(jobProfileId)
          .withName("Delete MARC-Authority")
          .withDataType(MARC))
        .withAddedRelations(List.of(
          jobToMatchAssociation, matchToUpdateActionAssociation, matchToDeleteActionAssociation)))
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"), is(DELETE_MARC_AUTHORITY_CANNOT_BE_NEXT_TO_OTHER_ACTIONS))));
  }

  @Test
  @SuppressWarnings("checkstyle:LineLength")
  public void shouldReturnUnprocessableEntityOnPostJobProfileWithDeleteMarcAuthorityUnderNonMarcAuthorityMatchProfile() {
    var jobProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();
    var actionProfileId = UUID.randomUUID().toString();

    postMatchProfile(matchProfileId, "Match Marc-Bib",
      EntityType.MARC_BIBLIOGRAPHIC, EntityType.MARC_BIBLIOGRAPHIC);
    postActionProfile(actionProfileId, "Delete MARC-Authority", DELETE, MARC_AUTHORITY);

    var jobToMatchAssociation = new ProfileAssociation()
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobProfileId)
      .withDetailProfileType(MATCH_PROFILE)
      .withDetailProfileId(matchProfileId)
      .withOrder(0);
    var matchToActionAssociation = new ProfileAssociation()
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId)
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileId)
      .withReactTo(MATCH)
      .withOrder(0);

    RestAssured.given()
      .spec(spec)
      .body(new JobProfileUpdateDto()
        .withProfile(new JobProfile()
          .withId(jobProfileId)
          .withName("Delete MARC-Authority")
          .withDataType(MARC))
        .withAddedRelations(List.of(jobToMatchAssociation, matchToActionAssociation)))
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is(INVALID_DELETE_MARC_AUTHORITY_ACTION_PROFILE_PLACEMENT))
      ));
  }

  @Test
  public void shouldReturnOkOnPutJobProfileWithDeleteMarcAuthorityAsFirstActionUnderMarcAuthorityMatchProfile() {
    var jobProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();
    var updateActionProfileId = UUID.randomUUID().toString();
    var updateMappingProfileId = UUID.randomUUID().toString();
    var deleteActionProfileId = UUID.randomUUID().toString();
    var deleteMappingProfileId = UUID.randomUUID().toString();

    var jobProfileToUpdate = postBaseJobProfileWithMatchAndUpdateAction(jobProfileId, matchProfileId,
      updateActionProfileId, MARC_AUTHORITY,
      updateMappingProfileId, EntityType.MARC_AUTHORITY, EntityType.MARC_AUTHORITY);
    postMappingProfile(deleteMappingProfileId, "Delete MARC-Authority",
      EntityType.MARC_AUTHORITY, EntityType.MARC_AUTHORITY);
    postActionProfileWithMapping(deleteActionProfileId, "Delete MARC-Authority",
      DELETE, MARC_AUTHORITY, deleteMappingProfileId);

    var matchToUpdateActionAssociation = new ProfileAssociation()
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId)
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(updateActionProfileId)
      .withReactTo(MATCH)
      .withOrder(0);
    var matchToDeleteActionAssociation = new ProfileAssociation()
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId)
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(deleteActionProfileId)
      .withReactTo(MATCH)
      .withOrder(0);

    RestAssured.given()
      .spec(spec)
      .body(jobProfileToUpdate
        .withAddedRelations(List.of(matchToDeleteActionAssociation))
        .withDeletedRelations(List.of(matchToUpdateActionAssociation)))
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobProfileId)
      .then()
      .statusCode(HttpStatus.SC_OK);
  }

  @Test
  public void shouldReturnUnprocessableEntityOnPutJobProfileWithDeleteMarcAuthorityUnderNonMatchBranch() {
    var jobProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();
    var actionProfileId = UUID.randomUUID().toString();
    var updateMappingProfileId = UUID.randomUUID().toString();
    var deleteActionProfileId = UUID.randomUUID().toString();
    var deleteMappingProfileId = UUID.randomUUID().toString();

    var jobProfileToUpdate = postBaseJobProfileWithMatchAndUpdateAction(jobProfileId, matchProfileId,
      actionProfileId, MARC_AUTHORITY, updateMappingProfileId, EntityType.MARC_AUTHORITY, EntityType.MARC_AUTHORITY);
    postMappingProfile(deleteMappingProfileId, "Delete MARC-Authority mapping",
      EntityType.MARC_AUTHORITY, EntityType.MARC_AUTHORITY);
    postActionProfileWithMapping(deleteActionProfileId, "Delete MARC-Authority",
      DELETE, MARC_AUTHORITY, deleteMappingProfileId);

    var matchToUpdateActionAssociation = new ProfileAssociation()
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId)
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileId)
      .withReactTo(MATCH)
      .withOrder(0);
    var matchToDeleteActionAssociation = new ProfileAssociation()
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId)
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(deleteActionProfileId)
      .withReactTo(NON_MATCH)
      .withOrder(0);

    RestAssured.given()
      .spec(spec)
      .body(jobProfileToUpdate
        .withAddedRelations(List.of(matchToDeleteActionAssociation))
        .withDeletedRelations(List.of(matchToUpdateActionAssociation)))
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobProfileId)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is(INVALID_DELETE_MARC_AUTHORITY_ACTION_PROFILE_PLACEMENT))
      ));
  }

  @Test
  public void shouldReturnUnprocessableEntityOnPutJobProfileIfDeleteMarcAuthorityNotFirstInMatchBranch() {
    var jobProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();
    var updateActionProfileId = UUID.randomUUID().toString();
    var updateMappingProfileId = UUID.randomUUID().toString();
    var deleteActionProfileId = UUID.randomUUID().toString();

    var jobProfileToUpdate = postBaseJobProfileWithMatchAndUpdateAction(jobProfileId, matchProfileId,
      updateActionProfileId, MARC_AUTHORITY, updateMappingProfileId,
      EntityType.MARC_AUTHORITY, EntityType.MARC_AUTHORITY);
    postActionProfile(deleteActionProfileId, "Delete MARC-Authority", DELETE, MARC_AUTHORITY);

    var matchToUpdateActionAssociation = new ProfileAssociation()
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId)
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(updateActionProfileId)
      .withReactTo(MATCH)
      .withOrder(0);
    var matchToDeleteActionAssociation = new ProfileAssociation()
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId)
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(deleteActionProfileId)
      .withReactTo(MATCH)
      .withOrder(1);

    RestAssured.given()
      .spec(spec)
      .body(jobProfileToUpdate
        .withAddedRelations(List.of(matchToDeleteActionAssociation))
        .withDeletedRelations(List.of(matchToUpdateActionAssociation)))
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobProfileId)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"), is(DELETE_MARC_AUTHORITY_CANNOT_BE_NEXT_TO_OTHER_ACTIONS))));
  }

  @Test
  public void shouldReturnUnprocessableEntityOnPutJobProfileWithDeleteMarcAuthorityUnderNonMarcAuthorityMatchProfile() {
    var jobProfileId = UUID.randomUUID().toString();
    var marcBibMatchProfileId = UUID.randomUUID().toString();
    var actionProfileId = UUID.randomUUID().toString();
    var updateMappingProfileId = UUID.randomUUID().toString();
    var deleteActionProfileId = UUID.randomUUID().toString();

    var jobProfileToUpdate = postBaseJobProfileWithMatchAndUpdateAction(jobProfileId, marcBibMatchProfileId,
      actionProfileId, MARC_BIBLIOGRAPHIC, updateMappingProfileId,
      EntityType.MARC_BIBLIOGRAPHIC, EntityType.MARC_BIBLIOGRAPHIC);
    postActionProfile(deleteActionProfileId, "Delete MARC-Authority", DELETE, MARC_AUTHORITY);

    var matchToUpdateActionAssociation = new ProfileAssociation()
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(marcBibMatchProfileId)
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileId)
      .withReactTo(MATCH)
      .withOrder(0);
    var marcBibMatchToDeleteActionAssociation = new ProfileAssociation()
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(marcBibMatchProfileId)
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(deleteActionProfileId)
      .withReactTo(MATCH)
      .withOrder(0);

    RestAssured.given()
      .spec(spec)
      .body(jobProfileToUpdate
        .withAddedRelations(List.of(marcBibMatchToDeleteActionAssociation))
        .withDeletedRelations(List.of(matchToUpdateActionAssociation))
      )
      .when()
      .put(JOB_PROFILES_PATH + "/" + jobProfileId)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is(INVALID_DELETE_MARC_AUTHORITY_ACTION_PROFILE_PLACEMENT))
      ));
  }

  /**
   * Cleans up tables data except default action profile for MARC-AUTHORITY deletion and its wrapper because
   * the profile is used in the tests.
   */
  @Override
  protected void clearTables(TestContext context) {
    Async async = context.async();
    PostgresClient pgClient = PostgresClient.getInstance(vertx, TENANT_ID);

    Future.succeededFuture()
      .compose(v -> pgClient.delete(ASSOCIATIONS_TABLE, new Criterion()))
      .compose(v -> pgClient.delete(SNAPSHOTS_TABLE_NAME, new Criterion()))
      .compose(v -> pgClient.delete(PROFILE_WRAPPERS_TABLE_NAME, getProfileWrappersDeletionCriterion()))
      .compose(v -> pgClient.delete(JOB_PROFILES_TABLE_NAME, new Criterion()))
      .compose(v -> pgClient.delete(MATCH_PROFILES_TABLE_NAME, new Criterion()))
      .compose(v -> pgClient.delete(ACTION_PROFILES_TABLE_NAME, getActionProfilesDeletionCriterion()))
      .compose(v -> pgClient.delete(MAPPING_PROFILES_TABLE_NAME, new Criterion()))
      .onComplete(ar -> {
        context.assertTrue(ar.succeeded());
        async.complete();
      });
    async.awaitSuccess(30000);
  }

  private static Criterion getProfileWrappersDeletionCriterion() {
    return new Criterion().addCriterion(new Criteria()
      .setJSONB(false)
      .addField("action_profile_id")
      .setOperation("!=")
      .setVal(DEFAULT_DELETE_MARC_AUTHORITY_ACTION_PROFILE_ID));
  }

  private static Criterion getActionProfilesDeletionCriterion() {
    return new Criterion().addCriterion(new Criteria()
      .setJSONB(false)
      .addField("id")
      .setOperation("!=")
      .setVal(DEFAULT_DELETE_MARC_AUTHORITY_ACTION_PROFILE_ID));
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

  private <T> JobProfileUpdateDto createJobProfile(JobProfileUpdateDto jobProfileUpdateDto,
                                                   ProfileWrapper<T> profileWrapper, String url,
                                                   ProfileType detailProfileType) {
    T profile = RestAssured.given()
      .spec(spec)
      .body(profileWrapper.getProfile())
      .when()
      .post(url)
      .then().log().all()
      .statusCode(HttpStatus.SC_CREATED)
      .and()
      .extract().body().as(profileWrapper.getProfileType());
    profileWrapper.setProfile(profile);

    var association = new ProfileAssociation()
      .withMasterProfileId(jobProfileUpdateDto.getProfile().getId())
      .withDetailProfileId(profileWrapper.getId())
      .withMasterProfileType(JOB_PROFILE)
      .withDetailProfileType(detailProfileType)
      .withOrder(0);

    return jobProfileUpdateDto.withAddedRelations(List.of(association));
  }

  private JobProfileUpdateDto createJobProfileWithAction(JobProfileUpdateDto jobProfileUpdateDto,
                                                         ActionProfileUpdateDto actionProfileUpdateDto,
                                                         MappingProfileUpdateDto mappingProfileUpdateDto) {
    var mappingProfile = RestAssured.given()
      .spec(spec)
      .body(mappingProfileUpdateDto)
      .when()
      .post(MAPPING_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(MappingProfileUpdateDto.class);

    var actionProfile = RestAssured.given()
      .spec(spec)
      .body(actionProfileUpdateDto)
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    var actionToMappingAssociation = new ProfileAssociation()
      .withMasterProfileId(actionProfile.getProfile().getId())
      .withDetailProfileId(mappingProfile.getProfile().getId())
      .withMasterProfileType(ACTION_PROFILE)
      .withDetailProfileType(MAPPING_PROFILE)
      .withOrder(0);

    var jobToActionAssociation = new ProfileAssociation()
      .withMasterProfileId(jobProfileUpdateDto.getId())
      .withDetailProfileId(actionProfile.getProfile().getId())
      .withMasterProfileType(JOB_PROFILE)
      .withDetailProfileType(ACTION_PROFILE)
      .withOrder(1);

    return jobProfileUpdateDto.withAddedRelations(List.of(jobToActionAssociation, actionToMappingAssociation));
  }

  private JobProfileUpdateDto createJobProfileWithMatch(JobProfileUpdateDto jobProfileUpdateDto,
                                                        MatchProfileUpdateDto matchProfileUpdateDto,
                                                        ActionProfileUpdateDto actionProfileUpdateDto,
                                                        MappingProfileUpdateDto mappingProfileUpdateDto) {
    var mappingProfile = RestAssured.given()
      .spec(spec)
      .body(mappingProfileUpdateDto)
      .when()
      .post(MAPPING_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(MappingProfileUpdateDto.class);

    var matchProfile = RestAssured.given()
      .spec(spec)
      .body(matchProfileUpdateDto)
      .when()
      .post(MATCH_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(MatchProfileUpdateDto.class);

    var actionProfile = RestAssured.given()
      .spec(spec)
      .body(actionProfileUpdateDto)
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    var jobToMatchAssociation = new ProfileAssociation()
      .withMasterProfileId(jobProfileUpdateDto.getId())
      .withDetailProfileId(matchProfile.getId())
      .withMasterProfileType(JOB_PROFILE)
      .withDetailProfileType(MATCH_PROFILE)
      .withOrder(1);

    var jobToActionAssociation = new ProfileAssociation()
      .withMasterProfileId(matchProfile.getId())
      .withDetailProfileId(actionProfile.getId())
      .withMasterProfileType(MATCH_PROFILE)
      .withDetailProfileType(ACTION_PROFILE)
      .withOrder(1);

    var actionToMappingAssociation = new ProfileAssociation()
      .withMasterProfileId(actionProfile.getId())
      .withDetailProfileId(mappingProfile.getId())
      .withMasterProfileType(ACTION_PROFILE)
      .withDetailProfileType(MAPPING_PROFILE)
      .withOrder(1);

    return jobProfileUpdateDto
      .withAddedRelations(List.of(jobToMatchAssociation, jobToActionAssociation, actionToMappingAssociation));
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

  private ProfileAssociation postProfileAssociation(ProfileAssociation profileAssociation, ProfileType masterType,
                                                    ProfileType detailType) {
    Response createResponse = RestAssured.given()
      .spec(spec)
      .queryParam("master", masterType.value())
      .queryParam("detail", detailType.value())
      .body(profileAssociation)
      .when()
      .post(ASSOCIATED_PROFILES_PATH);
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    return createResponse.body().as(ProfileAssociation.class);
  }

  private void postMappingProfile(String id, String name, EntityType incomingRecordType,
                                  EntityType existingRecordType) {
    RestAssured.given()
      .spec(spec)
      .body(new MappingProfileUpdateDto()
        .withProfile(new MappingProfile()
          .withId(id)
          .withName(name)
          .withIncomingRecordType(incomingRecordType)
          .withExistingRecordType(existingRecordType)))
      .when()
      .post(MAPPING_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
  }

  private void postActionProfile(String id, String name, ActionProfile.Action action, FolioRecord recordType) {
    RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile()
          .withId(id)
          .withName(name)
          .withAction(action)
          .withFolioRecord(recordType)))
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
  }

  private void postActionProfileWithMapping(String actionProfileId, String name, ActionProfile.Action action,
                                            FolioRecord recordType, String mappingProfileId) {
    RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile()
          .withId(actionProfileId)
          .withName(name)
          .withAction(action)
          .withFolioRecord(recordType))
        .withAddedRelations(List.of(new ProfileAssociation()
          .withMasterProfileType(ACTION_PROFILE)
          .withDetailProfileType(ProfileType.MAPPING_PROFILE)
          .withMasterProfileId(actionProfileId)
          .withDetailProfileId(mappingProfileId)
          .withOrder(0))))
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
  }

  private void postMatchProfile(String id, String name, EntityType incomingRecordType, EntityType existingRecordType) {
    RestAssured.given()
      .spec(spec)
      .body(new MatchProfileUpdateDto()
        .withProfile(new MatchProfile()
          .withId(id)
          .withName(name)
          .withIncomingRecordType(incomingRecordType)
          .withExistingRecordType(existingRecordType)))
      .when()
      .post(MATCH_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
  }

  private JobProfileUpdateDto postBaseJobProfileWithMatchAndUpdateAction(
    String jobProfileId, String matchProfileId, String actionId, FolioRecord folioRecordType, String mappingId,
    EntityType incomingRecordType, EntityType existingRecordType) {

    postMappingProfile(mappingId, "Update " + incomingRecordType.value(), incomingRecordType, existingRecordType);
    postActionProfileWithMapping(actionId, "Update " + incomingRecordType.value(), UPDATE, folioRecordType, mappingId);
    postMatchProfile(matchProfileId, "Match " + incomingRecordType.value(), incomingRecordType, existingRecordType);

    var jobToMatchAssociation = new ProfileAssociation()
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobProfileId)
      .withDetailProfileType(MATCH_PROFILE)
      .withDetailProfileId(matchProfileId)
      .withOrder(0);

    var matchToActionAssociation = new ProfileAssociation()
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId)
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionId)
      .withReactTo(MATCH)
      .withOrder(0);

    return RestAssured.given()
      .spec(spec)
      .body(new JobProfileUpdateDto()
        .withProfile(new JobProfile()
          .withId(jobProfileId)
          .withName("Test job profile")
          .withDataType(MARC))
        .withAddedRelations(List.of(jobToMatchAssociation, matchToActionAssociation)))
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);
  }
}
