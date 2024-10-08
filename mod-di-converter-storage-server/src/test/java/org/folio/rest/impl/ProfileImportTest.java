package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.folio.TestUtil;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MAPPING_PROFILE;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

@RunWith(VertxUnitRunner.class)
public class ProfileImportTest extends AbstractRestVerticleTest {
  private static final String PROFILE_SNAPSHOT_FILE_PATH = "src/test/resources/snapshots/";
  private static final String JOB_PROFILES_PATH = "/data-import-profiles/jobProfiles";
  private static final String ACTION_PROFILES_PATH = "/data-import-profiles/actionProfiles";
  private static final String MATCH_PROFILES_PATH = "/data-import-profiles/matchProfiles";
  private static final String MAPPING_PROFILES_PATH = "/data-import-profiles/mappingProfiles";
  public static final String PROFILE_SNAPSHOT_PATH = "/data-import-profiles/profileSnapshots";
  public static final String PROFILE_TYPE_PARAM = "profileType";
  private static final String JOB_PROFILE_ID_PARAM = "jobProfileId";

  @Test
  public void shouldImportProfileSnapshot(TestContext testContext) throws IOException {
    String mappingProfileId = UUID.randomUUID().toString();
    String jobProfileId = UUID.randomUUID().toString();
    String matchProfileId = UUID.randomUUID().toString();
    String actionProfileId = UUID.randomUUID().toString();

    JsonObject importWrapper = constructProfileWrapper(PROFILE_SNAPSHOT_FILE_PATH + "profileSnapshot.json",
      jobProfileId, matchProfileId, actionProfileId, mappingProfileId);

    Async async = testContext.async();
    JsonObject postProfileSnapshotWrapper = new JsonObject(RestAssured.given()
      .spec(spec)
      .when()
      .body(importWrapper.encode())
      .post(PROFILE_SNAPSHOT_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().body().asPrettyString());

    removeWrapperId(importWrapper);
    removeWrapperId(postProfileSnapshotWrapper);

    Assert.assertNotEquals(postProfileSnapshotWrapper, importWrapper);

    JsonObject resultSnapshotWrapper = new JsonObject(RestAssured.given()
      .spec(spec)
      .when()
      .queryParam(PROFILE_TYPE_PARAM, JOB_PROFILE.value())
      .queryParam(JOB_PROFILE_ID_PARAM, jobProfileId)
      .get(PROFILE_SNAPSHOT_PATH + "/" + jobProfileId)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract().body().asPrettyString());

    removeWrapperId(resultSnapshotWrapper);

    Assert.assertEquals(postProfileSnapshotWrapper, resultSnapshotWrapper);

    prepareProfileSnapshotToCompare(importWrapper);
    prepareProfileSnapshotToCompare(resultSnapshotWrapper);

    Assert.assertEquals(importWrapper, resultSnapshotWrapper);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(MAPPING_PROFILES_PATH + "/" + mappingProfileId)
      .then()
      .statusCode(HttpStatus.SC_OK);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(ACTION_PROFILES_PATH + "/" + actionProfileId)
      .then()
      .statusCode(HttpStatus.SC_OK);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(MATCH_PROFILES_PATH + "/" + matchProfileId)
      .then()
      .statusCode(HttpStatus.SC_OK);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(JOB_PROFILES_PATH + "/" + jobProfileId)
      .then()
      .statusCode(HttpStatus.SC_OK);

    async.complete();
  }

  @Test
  public void shouldImportProfileAndUpdateActionProfileIfAlreadyExist(TestContext testContext) throws IOException {
    String mappingProfileId = UUID.randomUUID().toString();
    String jobProfileId = UUID.randomUUID().toString();
    String matchProfileId = UUID.randomUUID().toString();
    String actionProfileId = UUID.randomUUID().toString();

    MappingProfileUpdateDto existingMappingProfile = new MappingProfileUpdateDto()
      .withProfile(new MappingProfile().withId(UUID.randomUUID().toString())
        .withName("testMappingProfile2").withDescription("test-description")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE));

    existingMappingProfile = postProfile(testContext, existingMappingProfile, MAPPING_PROFILES_PATH).body().as(MappingProfileUpdateDto.class);

    ActionProfileUpdateDto existingActionProfile = new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withId(actionProfileId)
        .withName("testActionProfile2").withDescription("test-description")
        .withAction(ActionProfile.Action.CREATE)
        .withFolioRecord(ActionProfile.FolioRecord.INSTANCE))
      .withAddedRelations(List.of(new ProfileAssociation()
        .withMasterProfileId(actionProfileId)
        .withMasterProfileType(ACTION_PROFILE)
        .withDetailProfileId(existingMappingProfile.getId())
        .withDetailProfileType(MAPPING_PROFILE)));

    existingActionProfile = postProfile(testContext, existingActionProfile, ACTION_PROFILES_PATH).body().as(ActionProfileUpdateDto.class);

    JsonObject importWrapper = constructProfileWrapper(PROFILE_SNAPSHOT_FILE_PATH + "profileSnapshot.json",
      jobProfileId, matchProfileId, actionProfileId, mappingProfileId);

    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .when()
      .body(importWrapper.encode())
      .post(PROFILE_SNAPSHOT_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    JsonObject resultSnapshotWrapper = new JsonObject(RestAssured.given()
      .spec(spec)
      .when()
      .queryParam(PROFILE_TYPE_PARAM, JOB_PROFILE.value())
      .queryParam(JOB_PROFILE_ID_PARAM, jobProfileId)
      .get(PROFILE_SNAPSHOT_PATH + "/" + jobProfileId)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract().body().asPrettyString());

    prepareProfileSnapshotToCompare(importWrapper);
    prepareProfileSnapshotToCompare(resultSnapshotWrapper);

    Assert.assertEquals(importWrapper, resultSnapshotWrapper);

    ActionProfile overlayActionProfile = RestAssured.given()
      .spec(spec)
      .when()
      .get(ACTION_PROFILES_PATH + "/" + actionProfileId)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract().body().as(ActionProfile.class);

    Assert.assertNotEquals(overlayActionProfile.getMetadata().getUpdatedDate(), existingActionProfile.getProfile().getMetadata().getUpdatedDate());

    RestAssured.given()
      .spec(spec)
      .when()
      .get(MAPPING_PROFILES_PATH + "/" + mappingProfileId)
      .then()
      .statusCode(HttpStatus.SC_OK);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(ACTION_PROFILES_PATH + "/" + actionProfileId)
      .then()
      .statusCode(HttpStatus.SC_OK);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(MATCH_PROFILES_PATH + "/" + matchProfileId)
      .then()
      .statusCode(HttpStatus.SC_OK);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(JOB_PROFILES_PATH + "/" + jobProfileId)
      .then()
      .statusCode(HttpStatus.SC_OK);

    async.complete();
  }

  @Test
  public void shouldImportProfileAndUpdateJobProfileIfAlreadyExist(TestContext testContext) throws IOException {
    String mappingProfileId = UUID.randomUUID().toString();
    String jobProfileId = UUID.randomUUID().toString();
    String matchProfileId = UUID.randomUUID().toString();
    String actionProfileId = UUID.randomUUID().toString();

    MappingProfileUpdateDto existingMappingProfile = new MappingProfileUpdateDto()
      .withProfile(new MappingProfile().withId(mappingProfileId)
        .withName("testMappingProfile3").withDescription("test-description")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE));

    existingMappingProfile = postProfile(testContext, existingMappingProfile, MAPPING_PROFILES_PATH).body().as(MappingProfileUpdateDto.class);

    ActionProfileUpdateDto existingActionProfile = new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withId(actionProfileId)
        .withName("testActionProfile3").withDescription("test-description")
        .withAction(ActionProfile.Action.CREATE)
        .withFolioRecord(ActionProfile.FolioRecord.INSTANCE))
      .withAddedRelations(List.of(new ProfileAssociation()
        .withMasterProfileId(actionProfileId)
        .withMasterProfileType(ACTION_PROFILE)
        .withDetailProfileId(mappingProfileId)
        .withDetailProfileType(MAPPING_PROFILE)));

    existingActionProfile = postProfile(testContext, existingActionProfile, ACTION_PROFILES_PATH).body().as(ActionProfileUpdateDto.class);

    JobProfileUpdateDto existingJobProfile = new JobProfileUpdateDto()
      .withProfile(new JobProfile().withId(jobProfileId)
        .withName("testJobProfile3").withDescription("test-description")
        .withDataType(JobProfile.DataType.MARC))
      .withAddedRelations(List.of(new ProfileAssociation()
        .withMasterProfileId(jobProfileId)
        .withMasterProfileType(JOB_PROFILE)
        .withDetailProfileId(actionProfileId)
        .withDetailProfileType(ACTION_PROFILE)));

    existingJobProfile = postProfile(testContext, existingJobProfile, JOB_PROFILES_PATH).body().as(JobProfileUpdateDto.class);

    JsonObject importWrapper = constructProfileWrapper(PROFILE_SNAPSHOT_FILE_PATH + "profileSnapshot.json",
      jobProfileId, matchProfileId, actionProfileId, mappingProfileId);

    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .when()
      .body(importWrapper.encode())
      .post(PROFILE_SNAPSHOT_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    JsonObject resultSnapshotWrapper = new JsonObject(RestAssured.given()
      .spec(spec)
      .when()
      .queryParam(PROFILE_TYPE_PARAM, JOB_PROFILE.value())
      .queryParam(JOB_PROFILE_ID_PARAM, jobProfileId)
      .get(PROFILE_SNAPSHOT_PATH + "/" + jobProfileId)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract().body().asPrettyString());

    prepareProfileSnapshotToCompare(importWrapper);
    prepareProfileSnapshotToCompare(resultSnapshotWrapper);

    Assert.assertEquals(importWrapper, resultSnapshotWrapper);

    MappingProfile overlayMappingProfile = RestAssured.given()
      .spec(spec)
      .when()
      .get(MAPPING_PROFILES_PATH + "/" + mappingProfileId)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract().body().as(MappingProfile.class);

    Assert.assertNotEquals(overlayMappingProfile.getMetadata().getUpdatedDate(), existingMappingProfile.getProfile().getMetadata().getUpdatedDate());

    ActionProfile overlayActionProfile = RestAssured.given()
      .spec(spec)
      .when()
      .get(ACTION_PROFILES_PATH + "/" + actionProfileId)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract().body().as(ActionProfile.class);

    Assert.assertNotEquals(overlayActionProfile.getMetadata().getUpdatedDate(), existingActionProfile.getProfile().getMetadata().getUpdatedDate());

    JobProfile overlayJobProfile = RestAssured.given()
      .spec(spec)
      .when()
      .get(JOB_PROFILES_PATH + "/" + jobProfileId)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract().body().as(JobProfile.class);

    Assert.assertNotEquals(overlayJobProfile.getMetadata().getUpdatedDate(), existingJobProfile.getProfile().getMetadata().getUpdatedDate());

    RestAssured.given()
      .spec(spec)
      .when()
      .get(MATCH_PROFILES_PATH + "/" + matchProfileId)
      .then()
      .statusCode(HttpStatus.SC_OK);

    async.complete();
  }

  @Test
  public void shouldNotImportProfileSnapshotIfNotJobProfileType(TestContext testContext) {
    ProfileSnapshotWrapper profileSnapshotWrapper = new ProfileSnapshotWrapper().withContentType(ACTION_PROFILE).withContent(new ActionProfile());
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .when()
      .body(profileSnapshotWrapper)
      .post(PROFILE_SNAPSHOT_PATH)
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .body(is(String.format("Cannot import profile snapshot of %s required type is %s", ACTION_PROFILE, JOB_PROFILE)));

    async.complete();
  }

  @Test
  public void shouldValidateProfileSnapshotDuringImport(TestContext testContext) throws IOException {
    String mappingProfileId = UUID.randomUUID().toString();
    String jobProfileId = UUID.randomUUID().toString();
    String matchProfileId = UUID.randomUUID().toString();
    String actionProfileId = UUID.randomUUID().toString();

    JsonObject importWrapper = constructProfileWrapper(PROFILE_SNAPSHOT_FILE_PATH + "invalidProfileSnapshotAssociation.json",
      jobProfileId, matchProfileId, actionProfileId, mappingProfileId);

    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .when()
      .body(importWrapper.encode())
      .post(PROFILE_SNAPSHOT_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Modify action cannot be used as a standalone action"));

    async.complete();
  }

  @Test
  public void shouldReturnErrorMessageIfInvalidProfileContent(TestContext testContext) throws IOException {
    String mappingProfileId = UUID.randomUUID().toString();
    String jobProfileId = UUID.randomUUID().toString();
    String matchProfileId = UUID.randomUUID().toString();
    String actionProfileId = UUID.randomUUID().toString();

    JsonObject importWrapper = constructProfileWrapper(PROFILE_SNAPSHOT_FILE_PATH + "invalidProfileSnapshotContent.json",
      jobProfileId, matchProfileId, actionProfileId, mappingProfileId);

    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .when()
      .body(importWrapper.encode())
      .post(PROFILE_SNAPSHOT_PATH)
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .body(startsWith("Cannot map profile content, error: "));

    async.complete();
  }

  private JsonObject constructProfileWrapper(String profilePath, String jobProfileId, String matchProfileId, String actionProfileId, String mappingProfileId)
    throws IOException {
    return new JsonObject(TestUtil.readFileFromPath(profilePath)
      .replace("#(jobProfileId)", jobProfileId)
      .replace("#(matchProfileId)", matchProfileId)
      .replace("#(actionProfileId)", actionProfileId)
      .replace("#(mappingProfileId)", mappingProfileId)
      .replace("#(uuid)", UUID.randomUUID().toString()));
  }

  private void removeWrapperId(JsonObject importWrapper) {
    importWrapper.remove("id");

    JsonArray childSnapshotWrapper = importWrapper.getJsonArray("childSnapshotWrappers");
    if (!childSnapshotWrapper.isEmpty()) {
      for (Object object : childSnapshotWrapper) {
        removeWrapperId((JsonObject) object);
      }
    }
  }

  private void prepareProfileSnapshotToCompare(JsonObject importWrapper) {
    removeRedundantFieldsFormSnapshotWrapper(importWrapper);

    if (importWrapper.containsKey("content")) {
      removeRedundantFieldsFormSnapshotWrapper(importWrapper.getJsonObject("content"));
    }

    JsonArray childSnapshotWrapper = importWrapper.getJsonArray("childSnapshotWrappers");
    if (!childSnapshotWrapper.isEmpty()) {
      for (Object object : childSnapshotWrapper) {
        prepareProfileSnapshotToCompare((JsonObject) object);
      }
    }
  }

  private static void removeRedundantFieldsFormSnapshotWrapper(JsonObject importWrapper) {
    importWrapper.remove("id");
    importWrapper.remove("profileWrapperId");
    importWrapper.remove("userInfo");
    importWrapper.remove("metadata");
  }

  private <T> ExtractableResponse<Response> postProfile(TestContext testContext, T profileDto, String profileUrl) {
    Async async = testContext.async();
    ExtractableResponse<Response> createdProfile = RestAssured.given()
      .spec(spec)
      .body(profileDto)
      .when()
      .post(profileUrl)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract();
    async.complete();
    return createdProfile;
  }
}
