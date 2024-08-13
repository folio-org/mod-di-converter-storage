package org.folio.rest.impl.snapshot;

import com.google.common.collect.Lists;
import io.restassured.RestAssured;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.apache.http.HttpStatus;
import org.folio.TestUtil;
import org.folio.rest.impl.AbstractRestVerticleTest;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.MatchProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import java.util.UUID;

import static org.folio.rest.jaxrs.model.ActionProfile.Action.CREATE;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.UPDATE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.INSTANCE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC;
import static org.folio.rest.jaxrs.model.JobProfile.DataType.MARC;
import static org.folio.rest.jaxrs.model.ReactToType.MATCH;
import static org.folio.rest.jaxrs.model.ReactToType.NON_MATCH;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;
import static org.hamcrest.Matchers.is;

@RunWith(VertxUnitRunner.class)
public class JobProfileSnapshotTest extends AbstractRestVerticleTest {
  private static final String PROFILE_SNAPSHOT_FILE_PATH = "src/test/resources/snapshots/";
  private static final String JOB_PROFILE_SNAPSHOT_PATH = "/data-import-profiles/jobProfileSnapshots";
  public static final String PROFILE_SNAPSHOT_PATH = "/data-import-profiles/profileSnapshots";
  private static final String JOB_PROFILES_PATH = "/data-import-profiles/jobProfiles";
  private static final String ACTION_PROFILES_PATH = "/data-import-profiles/actionProfiles";
  private static final String MATCH_PROFILES_PATH = "/data-import-profiles/matchProfiles";
  private static final String MAPPING_PROFILES_PATH = "/data-import-profiles/mappingProfiles";
  public static final String PROFILE_TYPE_PARAM = "profileType";
  private static final String JOB_PROFILE_ID_PARAM = "jobProfileId";

  private static final String JOB_PROFILES_TABLE_NAME = "job_profiles";
  private static final String ACTION_PROFILES_TABLE_NAME = "action_profiles";
  private static final String MAPPING_PROFILES_TABLE_NAME = "mapping_profiles";
  private static final String MATCH_PROFILES_TABLE_NAME = "match_profiles";
  private static final String SNAPSHOTS_TABLE_NAME = "profile_snapshots";
  private static final String ASSOCIATIONS_TABLE = "profile_associations";

  private static final String PROFILE_WRAPPERS_TABLE = "profile_wrappers";

  private JobProfileUpdateDto jobProfile = new JobProfileUpdateDto()
    .withProfile(new JobProfile().withName("testJobProfile1").withDataType(MARC).withDescription("test-description"));

  private MatchProfileUpdateDto matchProfile = new MatchProfileUpdateDto()
    .withProfile(new MatchProfile().withName("testMatchProfile1")
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withDescription("test-description"));

  private ActionProfileUpdateDto actionProfile = new ActionProfileUpdateDto()
    .withProfile(new ActionProfile().withName("testActionProfile1").withDescription("test-description")
      .withAction(UPDATE).withFolioRecord(MARC_BIBLIOGRAPHIC));

  private MappingProfileUpdateDto mappingProfile = new MappingProfileUpdateDto()
    .withProfile(new MappingProfile().withName("testMappingProfile1").withDescription("test-description")
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC));

  @Before
  public void setUp(TestContext testContext) {
    super.setUp(testContext);

    mappingProfile = postProfile(testContext, mappingProfile, MAPPING_PROFILES_PATH).body().as(MappingProfileUpdateDto.class);
    actionProfile.withAddedRelations(Collections.singletonList(new ProfileAssociation()
      .withMasterProfileId(actionProfile.getId())
      .withDetailProfileId(mappingProfile.getId())
      .withMasterProfileType(ProfileType.ACTION_PROFILE)
      .withDetailProfileType(ProfileType.MAPPING_PROFILE)
      .withOrder(0)));


    actionProfile = postProfile(testContext, actionProfile, ACTION_PROFILES_PATH).body().as(ActionProfileUpdateDto.class);
    matchProfile.withAddedRelations(Collections.singletonList(new ProfileAssociation()
      .withMasterProfileId(matchProfile.getId())
      .withDetailProfileId(actionProfile.getId())
      .withMasterProfileType(ProfileType.MATCH_PROFILE)
      .withDetailProfileType(ProfileType.ACTION_PROFILE)
      .withReactTo(NON_MATCH)
      .withJobProfileId(jobProfile.getId())
      .withOrder(0)));

    matchProfile = postProfile(testContext, matchProfile, MATCH_PROFILES_PATH).body().as(MatchProfileUpdateDto.class);

    jobProfile.withAddedRelations(Lists.newArrayList(new ProfileAssociation()
        .withMasterProfileId(jobProfile.getId())
        .withDetailProfileId(matchProfile.getId())
        .withMasterProfileType(ProfileType.JOB_PROFILE)
        .withDetailProfileType(ProfileType.MATCH_PROFILE)
        .withOrder(0),
      new ProfileAssociation()
        .withMasterProfileId(matchProfile.getId())
        .withDetailProfileId(actionProfile.getId())
        .withMasterProfileType(ProfileType.MATCH_PROFILE)
        .withDetailProfileType(ProfileType.ACTION_PROFILE)
        .withOrder(0)
// TODO: check why shouldReturnSnapshotWrapperOnGetByProfileIdForJobProfileWithEmptyChildSnapshotWrappers
//       does not show action-mapping relation without removing the part below

//         ,new ProfileAssociation()
//        .withMasterProfileId(actionProfile.getId())
//        .withDetailProfileId(mappingProfile.getId())
//        .withMasterProfileType(ProfileType.ACTION_PROFILE)
//        .withDetailProfileType(ProfileType.MAPPING_PROFILE)
//        .withOrder(0)
    ));

    jobProfile = postProfile(testContext, jobProfile, JOB_PROFILES_PATH).body().as(JobProfileUpdateDto.class);
  }

  @Test
  public void shouldReturnNotFoundOnGetById(TestContext testContext) {
    Async async = testContext.async();
    String id = UUID.randomUUID().toString();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(JOB_PROFILE_SNAPSHOT_PATH + "/" + id)
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
    async.complete();
  }

  @Test
  public void shouldBuildAndReturn500OnGetById(TestContext testContext) {
    Async async = testContext.async();
    String id = UUID.randomUUID().toString();
    RestAssured.given()
      .spec(spec)
      .when()
      .post(JOB_PROFILE_SNAPSHOT_PATH + "/" + id)
      .then()
      .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    async.complete();
  }

  @Test
  public void shouldBuildAndReturn500OnGetSnapshotById(TestContext testContext) {
    Async async = testContext.async();
    String id = UUID.randomUUID().toString();
    RestAssured.given()
      .spec(spec)
      .when()
      .queryParam(PROFILE_TYPE_PARAM, JOB_PROFILE.value())
      .get(PROFILE_SNAPSHOT_PATH + "/" + id)
      .then()
      .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    async.complete();
  }

  @Test
  public void shouldReturnBadRequestWhenProfileTypeQueryParamIsMissed(TestContext testContext) {
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(PROFILE_SNAPSHOT_PATH + "/" + UUID.randomUUID())
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST);
    async.complete();
  }

  @Test
  public void shouldReturnBadRequestWhenProfileTypeQueryParamIsInvalid(TestContext testContext) {
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .when()
      .queryParam(PROFILE_TYPE_PARAM, "invalid param")
      .get(PROFILE_SNAPSHOT_PATH + "/" + UUID.randomUUID())
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST);
    async.complete();
  }

  @Test
  public void shouldReturnSnapshotWrapperOnGetByProfileIdForJobProfileWithEmptyChildSnapshotWrappers(TestContext testContext) {
    Async async = testContext.async();
    ProfileSnapshotWrapper jobProfileSnapshot = RestAssured.given()
      .spec(spec)
      .when()
      .queryParam(PROFILE_TYPE_PARAM, JOB_PROFILE.value())
      .queryParam(JOB_PROFILE_ID_PARAM, jobProfile.getId())
      .get(PROFILE_SNAPSHOT_PATH + "/" + jobProfile.getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract().body().as(ProfileSnapshotWrapper.class);

    JobProfile actualJobProfile = DatabindCodec.mapper().convertValue(jobProfileSnapshot.getContent(), JobProfile.class);
    Assert.assertEquals(jobProfile.getId(), actualJobProfile.getId());
    Assert.assertEquals(1, jobProfileSnapshot.getChildSnapshotWrappers().size());

    ProfileSnapshotWrapper matchProfileSnapshot = jobProfileSnapshot.getChildSnapshotWrappers().get(0);
    MatchProfile actualMatchProfile = DatabindCodec.mapper().convertValue(matchProfileSnapshot.getContent(), MatchProfile.class);
    Assert.assertEquals(matchProfile.getId(), actualMatchProfile.getId());
    Assert.assertEquals(1, matchProfileSnapshot.getChildSnapshotWrappers().size());

    ProfileSnapshotWrapper actionProfileSnapshot = matchProfileSnapshot.getChildSnapshotWrappers().get(0);
    ActionProfile actualActionProfile = DatabindCodec.mapper().convertValue(actionProfileSnapshot.getContent(), ActionProfile.class);
    Assert.assertEquals(actionProfile.getId(), actualActionProfile.getId());
    Assert.assertEquals(1, actionProfileSnapshot.getChildSnapshotWrappers().size());

    ProfileSnapshotWrapper mappingProfileSnapshot = actionProfileSnapshot.getChildSnapshotWrappers().get(0);
    MappingProfile mappingActionProfile = DatabindCodec.mapper().convertValue(mappingProfileSnapshot.getContent(), MappingProfile.class);
    Assert.assertEquals(mappingProfile.getId(), mappingActionProfile.getId());
    Assert.assertEquals(0, mappingProfileSnapshot.getChildSnapshotWrappers().size());
    async.complete();
  }

  @Test
  public void shouldReturnSnapshotWrapperOnGetByProfileIdForMatchProfileWithEmptyChildSnapshotWrappers(TestContext testContext) {
    Async async = testContext.async();
    ProfileSnapshotWrapper matchProfileSnapshot = RestAssured.given()
      .spec(spec)
      .when()
      .queryParam(PROFILE_TYPE_PARAM, MATCH_PROFILE.value())
      .queryParam(JOB_PROFILE_ID_PARAM, jobProfile.getId())
      .get(PROFILE_SNAPSHOT_PATH + "/" + matchProfile.getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract().body().as(ProfileSnapshotWrapper.class);

    MatchProfile actualMatchProfile = DatabindCodec.mapper().convertValue(matchProfileSnapshot.getContent(), MatchProfile.class);
    Assert.assertEquals(matchProfile.getId(), actualMatchProfile.getId());
    Assert.assertEquals(0, matchProfileSnapshot.getChildSnapshotWrappers().size());
    async.complete();
  }

  @Test
  public void shouldReturnSnapshotWrapperOnGetByProfileIdForActionProfile(TestContext testContext) {
    Async async = testContext.async();
    ProfileSnapshotWrapper actionProfileSnapshot = RestAssured.given()
      .spec(spec)
      .when()
      .queryParam(PROFILE_TYPE_PARAM, ACTION_PROFILE.value())
      .get(PROFILE_SNAPSHOT_PATH + "/" + actionProfile.getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract().body().as(ProfileSnapshotWrapper.class);

    Assert.assertEquals(0, actionProfileSnapshot.getChildSnapshotWrappers().size());
    async.complete();
  }

  @Test
  public void shouldReturnSnapshotWrapperOnGetByProfileIdForJobProfile(TestContext testContext) {
    Async async = testContext.async();

    ActionProfileUpdateDto actionProfile2 = RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(
          new ActionProfile()
            .withId(UUID.randomUUID().toString())
            .withName("actionProfile2")
            .withDescription("actionProfile2-description")
            .withAction(CREATE)
            .withFolioRecord(INSTANCE))
      )
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    MappingProfileUpdateDto mappingProfile2 = RestAssured.given()
      .spec(spec)
      .body(new MappingProfileUpdateDto()
        .withProfile(
          new MappingProfile()
            .withName("testMapping2")
            .withDescription("testMapping2-description")
            .withId(UUID.randomUUID().toString())
            .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
            .withExistingRecordType(EntityType.INSTANCE))
      )
      .when()
      .post(MAPPING_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(MappingProfileUpdateDto.class);

    JobProfileUpdateDto jobProfile2 = RestAssured.given()
      .spec(spec)
      .body(new JobProfileUpdateDto()
        .withProfile(new JobProfile()
            .withName("jobProfile2")
            .withDescription("jobProfile2-description")
            .withDataType(MARC)
          )
        .withAddedRelations(Arrays.asList(
          new ProfileAssociation()
            .withDetailProfileId(matchProfile.getId())
            .withMasterProfileType(ProfileType.JOB_PROFILE)
            .withDetailProfileType(ProfileType.MATCH_PROFILE)
            .withOrder(0),
          new ProfileAssociation()
            .withMasterProfileId(matchProfile.getId())
            .withDetailProfileId(actionProfile2.getId())
            .withMasterProfileType(ProfileType.MATCH_PROFILE)
            .withDetailProfileType(ProfileType.ACTION_PROFILE)
            .withReactTo(MATCH)
            .withOrder(0),
          new ProfileAssociation()
            .withMasterProfileId(actionProfile2.getId())
            .withDetailProfileId(mappingProfile2.getId())
            .withMasterProfileType(ProfileType.ACTION_PROFILE)
            .withDetailProfileType(ProfileType.MAPPING_PROFILE)
            .withOrder(0)))
      )
      .when()
      .post(JOB_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    ProfileSnapshotWrapper jobProfileSnapshot = RestAssured.given()
      .spec(spec)
      .when()
      .queryParam(PROFILE_TYPE_PARAM, JOB_PROFILE.value())
      .queryParam(JOB_PROFILE_ID_PARAM, jobProfile2.getId())
      .get(PROFILE_SNAPSHOT_PATH + "/" + jobProfile2.getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract().body().as(ProfileSnapshotWrapper.class);

    JobProfile actualJobProfile = DatabindCodec.mapper().convertValue(jobProfileSnapshot.getContent(), JobProfile.class);
    Assert.assertEquals(jobProfile2.getId(), actualJobProfile.getId());
    Assert.assertEquals(1, jobProfileSnapshot.getChildSnapshotWrappers().size());

    ProfileSnapshotWrapper matchProfileSnapshot = jobProfileSnapshot.getChildSnapshotWrappers().get(0);
    MatchProfile actualMatchProfile = DatabindCodec.mapper().convertValue(matchProfileSnapshot.getContent(), MatchProfile.class);
    Assert.assertEquals(matchProfile.getId(), actualMatchProfile.getId());
    Assert.assertEquals(1, matchProfileSnapshot.getChildSnapshotWrappers().size());

    ProfileSnapshotWrapper actionProfileSnapshot = matchProfileSnapshot.getChildSnapshotWrappers().get(0);
    ActionProfile actualActionProfile = DatabindCodec.mapper().convertValue(actionProfileSnapshot.getContent(), ActionProfile.class);
    Assert.assertEquals(actionProfile2.getId(), actualActionProfile.getId());
    Assert.assertEquals(1, actionProfileSnapshot.getChildSnapshotWrappers().size());
    async.complete();
  }

  @Test
  public void shouldImportProfileSnapshot(TestContext testContext) throws IOException {
    String mappingProfileId = UUID.randomUUID().toString();
    String jobProfileId = UUID.randomUUID().toString();
    String matchProfileId = UUID.randomUUID().toString();
    String actionProfileId = UUID.randomUUID().toString();

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
  public void shouldImportProfileAndUpdateProfileIfAlreadyExist(TestContext testContext) throws IOException {
    String mappingProfileId = UUID.randomUUID().toString();
    String jobProfileId = UUID.randomUUID().toString();
    String matchProfileId = UUID.randomUUID().toString();
    String actionProfileId = UUID.randomUUID().toString();

    MappingProfileUpdateDto existingMappingProfile = new MappingProfileUpdateDto()
      .withProfile(new MappingProfile().withId(mappingProfileId)
        .withName("testMappingProfile2").withDescription("test-description")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC));

    existingMappingProfile = postProfile(testContext, existingMappingProfile, MAPPING_PROFILES_PATH).body().as(MappingProfileUpdateDto.class);

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
  public void shouldNotImportProfileSnapshotOfNotJobProfile(TestContext testContext) {
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
  public void shouldReturnSnapshotWrapperForJobProfileWithoutMatchProfileChildWrappersWhenJobProfileIdParamIsMissed(TestContext testContext) {
    Async async = testContext.async();
    ProfileSnapshotWrapper jobProfileSnapshot = RestAssured.given()
      .spec(spec)
      .when()
      .queryParam(PROFILE_TYPE_PARAM, JOB_PROFILE.value())
      .get(PROFILE_SNAPSHOT_PATH + "/" + jobProfile.getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract().body().as(ProfileSnapshotWrapper.class);

    JobProfile actualJobProfile = DatabindCodec.mapper().convertValue(jobProfileSnapshot.getContent(), JobProfile.class);
    Assert.assertEquals(jobProfile.getId(), actualJobProfile.getId());
    Assert.assertEquals(1, jobProfileSnapshot.getChildSnapshotWrappers().size());

    ProfileSnapshotWrapper matchProfileSnapshot = jobProfileSnapshot.getChildSnapshotWrappers().get(0);
    MatchProfile actualMatchProfile = DatabindCodec.mapper().convertValue(matchProfileSnapshot.getContent(), MatchProfile.class);
    Assert.assertEquals(matchProfile.getId(), actualMatchProfile.getId());
    Assert.assertEquals(0, matchProfileSnapshot.getChildSnapshotWrappers().size());
    async.complete();
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

  private <T> ExtractableResponse<Response> updateProfile(TestContext testContext, T profileDto, String profileId, String profileUrl) {
    Async async = testContext.async();
    ExtractableResponse<Response> createdProfile = RestAssured.given()
      .spec(spec)
      .body(profileDto)
      .when()
      .put(profileUrl + "/" + profileId)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract();
    async.complete();
    return createdProfile;
  }

  @Override
  public void clearTables(TestContext context) {
    Async async = context.async();
    PostgresClient pgClient = PostgresClient.getInstance(vertx, TENANT_ID);
    pgClient.delete(SNAPSHOTS_TABLE_NAME, new Criterion(), event1 ->
      pgClient.delete(ASSOCIATIONS_TABLE, new Criterion(), event2 ->
        pgClient.delete(PROFILE_WRAPPERS_TABLE, new Criterion(), event3 ->
          pgClient.delete(JOB_PROFILES_TABLE_NAME, new Criterion(), event4 ->
            pgClient.delete(MATCH_PROFILES_TABLE_NAME, new Criterion(), event5 ->
              pgClient.delete(ACTION_PROFILES_TABLE_NAME, new Criterion(), event6 ->
                  pgClient.delete(MAPPING_PROFILES_TABLE_NAME, new Criterion(), event7 -> {
                      if (event7.failed()) {
                        context.fail(event7.cause());
                      }
                      async.complete();
                    })))))));
  }

  private JsonObject constructProfileWrapper(String profilePath, String jobProfileId, String matchProfileId, String actionProfileId, String mappingProfileId)
    throws IOException {
    return new JsonObject(TestUtil.readFileFromPath(profilePath)
      .replace("#(jobProfileId)", jobProfileId)
      .replace("#(matchProfileId)", matchProfileId)
      .replace("#(actionProfileId)", actionProfileId)
      .replace("#(mappingProfileId)", mappingProfileId));
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
}
