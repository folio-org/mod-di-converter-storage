package org.folio.rest.impl.association;

import com.google.common.collect.Lists;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.List;
import org.apache.http.HttpStatus;
import org.folio.rest.impl.AbstractRestVerticleTest;
import org.folio.rest.impl.association.wrapper.ActionProfileWrapper;
import org.folio.rest.impl.association.wrapper.JobProfileWrapper;
import org.folio.rest.impl.association.wrapper.MappingProfileWrapper;
import org.folio.rest.impl.association.wrapper.MatchProfileWrapper;
import org.folio.rest.impl.association.wrapper.ProfileWrapper;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.MappingDetail;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.MatchProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.UUID;

import static org.folio.rest.jaxrs.model.ActionProfile.Action.CREATE;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.UPDATE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.INSTANCE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC;
import static org.folio.rest.jaxrs.model.JobProfile.DataType.MARC;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MAPPING_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;
import static org.hamcrest.Matchers.is;

@RunWith(VertxUnitRunner.class)
public class CommonProfileAssociationTest extends AbstractRestVerticleTest {

  public static final String PROFILE_WRAPPERS = "profile_wrappers";
  public static final String ASSOCIATIONS_TABLE = "profile_associations";

  public static final String JOB_PROFILES_TABLE = "job_profiles";
  public static final String ACTION_PROFILES_TABLE = "action_profiles";
  public static final String MAPPING_PROFILES_TABLE = "mapping_profiles";
  public static final String MATCH_PROFILES_TABLE = "match_profiles";

  private static final String JOB_PROFILES_URL = "/data-import-profiles/jobProfiles";
  private static final String ACTION_PROFILES_URL = "/data-import-profiles/actionProfiles";
  private static final String MAPPING_PROFILES_URL = "/data-import-profiles/mappingProfiles";
  private static final String MATCH_PROFILES_URL = "/data-import-profiles/matchProfiles";

  public static final String ASSOCIATED_PROFILES_URL = "/data-import-profiles/profileAssociations";
  private static final String DETAILS_BY_MASTER_URL = "/data-import-profiles/profileAssociations/{masterId}/details";
  private static final String MASTERS_BY_DETAIL_URL = "/data-import-profiles/profileAssociations/{detailId}/masters";
  private static final String ASSOCIATION_UUID = "7e5da9cf-e9e8-4734-b23e-a4a1129ebcdb";

  JobProfileUpdateDto jobProfile1 = new JobProfileUpdateDto()
    .withProfile(new JobProfile().withName("testJobProfile1").withDataType(MARC).withDescription("test-description"));
  JobProfileUpdateDto jobProfile2 = new JobProfileUpdateDto()
    .withProfile(new JobProfile().withName("testJobProfile2").withDataType(MARC).withDescription("test-description"));

  ActionProfileUpdateDto actionProfile1 = new ActionProfileUpdateDto().withId("f12db804-70d1-49dd-a58c-49edf9031371")
    .withProfile(new ActionProfile().withName("testActionProfile1").withDescription("test-description")
      .withAction(CREATE).withFolioRecord(INSTANCE));

  ActionProfileUpdateDto actionProfile2 = new ActionProfileUpdateDto().withId("f12db804-70d1-49dd-a58c-49edf9031372")
    .withProfile(new ActionProfile().withName("testActionProfile2").withDescription("test-description")
      .withAction(UPDATE).withFolioRecord(MARC_BIBLIOGRAPHIC));

  ActionProfileUpdateDto actionProfile3 = new ActionProfileUpdateDto().withId("f12db804-70d1-49dd-a58c-49edf9031373")
    .withProfile(new ActionProfile().withName("testActionProfile3").withDescription("test-description")
      .withAction(UPDATE).withFolioRecord(MARC_BIBLIOGRAPHIC));

  ActionProfileUpdateDto actionProfile4 = new ActionProfileUpdateDto().withId("f12db804-70d1-49dd-a58c-49edf9031374")
    .withProfile(new ActionProfile().withName("testActionProfile4")
      .withAction(UPDATE).withFolioRecord(MARC_BIBLIOGRAPHIC));

  MappingProfileUpdateDto mappingProfile1 = new MappingProfileUpdateDto()
    .withProfile(new MappingProfile().withName("testMappingProfile1").withDescription("test-description")
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.INSTANCE));
  MappingProfileUpdateDto mappingProfile2 = new MappingProfileUpdateDto()
    .withProfile(new MappingProfile().withName("testMappingProfile2").withDescription("test-description")
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.INSTANCE));
  MappingProfileUpdateDto mappingProfile3 = new MappingProfileUpdateDto()
    .withProfile(new MappingProfile().withName("testMappingProfile3")
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.INSTANCE));

  MatchProfileUpdateDto matchProfile1 = new MatchProfileUpdateDto()
    .withProfile(new MatchProfile().withName("testMatchProfile1")
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.INSTANCE)
      .withDescription("test-description"));
  MatchProfileUpdateDto matchProfile2 = new MatchProfileUpdateDto()
    .withProfile(new MatchProfile().withName("testMatchProfile2")
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withDescription("test-description"));
  MatchProfileUpdateDto matchProfile3 = new MatchProfileUpdateDto()
    .withProfile(new MatchProfile().withName("testMatchProfile3")
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withDescription("test-description"));
  MatchProfileUpdateDto matchProfile4 = new MatchProfileUpdateDto()
    .withProfile(new MatchProfile().withName("testMatchProfile4")
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC));

  @Test
  public void runTestShouldReturnEmptyOkResultOnGetAll(TestContext testContext) {
    shouldReturnEmptyOkResultOnGetAll(testContext, ACTION_PROFILE, ACTION_PROFILE);
    shouldReturnEmptyOkResultOnGetAll(testContext, ACTION_PROFILE, MAPPING_PROFILE);
    shouldReturnEmptyOkResultOnGetAll(testContext, ACTION_PROFILE, MATCH_PROFILE);
    shouldReturnEmptyOkResultOnGetAll(testContext, JOB_PROFILE, ACTION_PROFILE);
    shouldReturnEmptyOkResultOnGetAll(testContext, JOB_PROFILE, MATCH_PROFILE);
    shouldReturnEmptyOkResultOnGetAll(testContext, MATCH_PROFILE, ACTION_PROFILE);
    shouldReturnEmptyOkResultOnGetAll(testContext, MATCH_PROFILE, MATCH_PROFILE);
  }

  public void shouldReturnEmptyOkResultOnGetAll(TestContext testContext, ProfileType masterProfileType, ProfileType detailProfileType) {
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterProfileType.value())
      .queryParam("detail", detailProfileType.value())
      .when()
      .get(ASSOCIATED_PROFILES_URL)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(0));
    async.complete();
  }

  @Test
  public void runTestShouldReturnProfileAssociationListOnGet(TestContext testContext) {
    // action to mapping
    shouldReturnProfileAssociationListOnGet(testContext, new ActionProfileWrapper(actionProfile3), new MappingProfileWrapper(mappingProfile1),
      ACTION_PROFILES_URL, MAPPING_PROFILES_URL, ACTION_PROFILE, MAPPING_PROFILE);
    // job to action
    shouldReturnJobToActionAssociationListOnGet(testContext, new JobProfileWrapper(jobProfile1),
      new ActionProfileWrapper(actionProfile1), new MappingProfileWrapper(mappingProfile1));
    // job to match
    shouldReturnJobToMatchAssociationListOnGet(testContext, new JobProfileWrapper(jobProfile2), new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1), new MatchProfileWrapper(matchProfile1));
    // match to action
    shouldReturnProfileAssociationListOnGet(testContext, new MatchProfileWrapper(matchProfile1),
      new ActionProfileWrapper(actionProfile1),
      MATCH_PROFILES_URL, ACTION_PROFILES_URL, MATCH_PROFILE, ACTION_PROFILE);
    // match to match
    shouldReturnProfileAssociationListOnGet(testContext, new MatchProfileWrapper(matchProfile2), new MatchProfileWrapper(matchProfile3),
      MATCH_PROFILES_URL, MATCH_PROFILES_URL, MATCH_PROFILE, MATCH_PROFILE);
  }

  public <M, D> void shouldReturnJobToMatchAssociationListOnGet(TestContext testContext,
                                                                JobProfileWrapper jobProfileWrapper,
                                                                ActionProfileWrapper actionProfileWrapper,
                                                                MappingProfileWrapper mappingProfileWrapper,
                                                                MatchProfileWrapper matchProfileWrapper) {
    var jobProfile = createJobProfileWithMatch(testContext, jobProfileWrapper, actionProfileWrapper,
      mappingProfileWrapper, matchProfileWrapper);

    postProfile(testContext, new JobProfileWrapper(jobProfile), JOB_PROFILES_URL);

    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", JOB_PROFILE)
      .queryParam("detail", MATCH_PROFILE)
      .when()
      .get(ASSOCIATED_PROFILES_URL)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(1));
    async.complete();

    clearTables(testContext);
  }

  public <M, D> void shouldReturnJobToActionAssociationListOnGet(TestContext testContext,
                                                                            JobProfileWrapper jobProfileWrapper,
                                                                            ActionProfileWrapper actionProfileWrapper,
                                                                            MappingProfileWrapper mappingProfileWrapper) {
    var jobProfile = createJobProfileWithAction(testContext, jobProfileWrapper, actionProfileWrapper,
      mappingProfileWrapper);

    postProfile(testContext, new JobProfileWrapper(jobProfile), JOB_PROFILES_URL);

    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", JOB_PROFILE)
      .queryParam("detail", ACTION_PROFILE)
      .when()
      .get(ASSOCIATED_PROFILES_URL)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(1));
    async.complete();

    clearTables(testContext);
  }

  public <M, D> void shouldReturnProfileAssociationListOnGet(TestContext testContext, ProfileWrapper<M> masterWrapper1, ProfileWrapper<D> detailWrapper1,
                                                             String masterProfileUrl, String detailProfileUrl, ProfileType masterProfileType, ProfileType detailProfileType) {
    ProfileWrapper<M> masterProfileWrapper1 = postProfile(testContext, masterWrapper1, masterProfileUrl);
    ProfileWrapper<D> detailProfileWrapper1 = postProfile(testContext, detailWrapper1, detailProfileUrl);

    ProfileAssociation profileAssociation = new ProfileAssociation()
      .withMasterProfileId(masterProfileWrapper1.getId())
      .withDetailProfileId(detailProfileWrapper1.getId())
      .withMasterProfileType(ProfileType.valueOf(masterProfileType.value()))
      .withDetailProfileType(ProfileType.valueOf(detailProfileType.value()))
      .withOrder(5)
      .withTriggered(true);

    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterProfileType.value())
      .queryParam("detail", detailProfileType.value())
      .body(profileAssociation)
      .when()
      .post(ASSOCIATED_PROFILES_URL)
      .then()
      .statusCode(HttpStatus.SC_CREATED);
    async.complete();

    async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterProfileType.value())
      .queryParam("detail", detailProfileType.value())
      .when()
      .get(ASSOCIATED_PROFILES_URL)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(1));
    async.complete();

    clearTables(testContext);
  }

  @Test
  public void runTestShouldReturnNotFoundOnGetById(TestContext testContext) {
    shouldReturnNotFoundOnGetById(testContext, ACTION_PROFILE, ACTION_PROFILE);
    shouldReturnNotFoundOnGetById(testContext, ACTION_PROFILE, MAPPING_PROFILE);
    shouldReturnNotFoundOnGetById(testContext, ACTION_PROFILE, MATCH_PROFILE);
    shouldReturnNotFoundOnGetById(testContext, JOB_PROFILE, ACTION_PROFILE);
    shouldReturnNotFoundOnGetById(testContext, JOB_PROFILE, MATCH_PROFILE);
    shouldReturnNotFoundOnGetById(testContext, MATCH_PROFILE, ACTION_PROFILE);
    shouldReturnNotFoundOnGetById(testContext, MATCH_PROFILE, MATCH_PROFILE);
  }

  public void shouldReturnNotFoundOnGetById(TestContext testContext, ProfileType masterProfileType, ProfileType detailProfileType) {
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterProfileType.value())
      .queryParam("detail", detailProfileType.value())
      .when()
      .get(ASSOCIATED_PROFILES_URL + "/" + UUID.randomUUID())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
    async.complete();
  }

  @Test
  public void runTestShouldPostAndGetById(TestContext testContext) {
    // action to mapping
    shouldPostAndGetById(testContext, new ActionProfileWrapper(actionProfile1),
      new MappingProfileWrapper(mappingProfile1),
      ACTION_PROFILES_URL, MAPPING_PROFILES_URL, ACTION_PROFILE, MAPPING_PROFILE);
    // job to action
    shouldPostJobToActionAndGetById(testContext, new JobProfileWrapper(jobProfile1),
      new ActionProfileWrapper(actionProfile1),
      new MappingProfileWrapper(mappingProfile1));
    // job to match
    shouldPostJobToMatchAndGetById(testContext, new JobProfileWrapper(jobProfile2), new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1), new MatchProfileWrapper(matchProfile1));
    // match to action
    shouldPostAndGetById(testContext, new ActionProfileWrapper(actionProfile1), new MatchProfileWrapper(matchProfile2),
      ACTION_PROFILES_URL, MATCH_PROFILES_URL, ACTION_PROFILE, MATCH_PROFILE);
    // match to match
    shouldPostAndGetById(testContext, new MatchProfileWrapper(matchProfile1), new MatchProfileWrapper(matchProfile3),
      MATCH_PROFILES_URL, MATCH_PROFILES_URL, MATCH_PROFILE, MATCH_PROFILE);
  }

  private void shouldPostJobToActionAndGetById(TestContext testContext,
                                              JobProfileWrapper jobProfileWrapper,
                                              ActionProfileWrapper actionProfileWrapper,
                                              MappingProfileWrapper mappingProfileWrapper) {
    var jobProfile = createJobProfileWithAction(testContext, jobProfileWrapper, actionProfileWrapper,
      mappingProfileWrapper);

    ProfileWrapper<JobProfileUpdateDto> masterWrapper = postProfile(testContext, new JobProfileWrapper(jobProfile),
      JOB_PROFILES_URL);

    RestAssured.given()
      .spec(spec)
      .queryParam("master", JOB_PROFILE)
      .queryParam("detail", ACTION_PROFILE)
      .when()
      .get(ASSOCIATED_PROFILES_URL + "/" + ASSOCIATION_UUID)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(ASSOCIATION_UUID))
      .body("masterProfileId", is(masterWrapper.getId()))
      .body("detailProfileId", is(jobProfile.getAddedRelations().get(0).getDetailProfileId()))
      .body("order", is(jobProfile.getAddedRelations().get(0).getOrder()));

    clearTables(testContext);
  }

  private JobProfileUpdateDto createJobProfileWithAction(TestContext testContext,
                                                         JobProfileWrapper jobProfileWrapper,
                                                         ActionProfileWrapper actionProfileWrapper,
                                                         MappingProfileWrapper mappingProfileWrapper) {
    var mappingWrapper = postProfile(testContext, mappingProfileWrapper, MAPPING_PROFILES_URL);
    var actionWrapper = postProfile(testContext, actionProfileWrapper, ACTION_PROFILES_URL);

    var actionToMappingAssociation = new ProfileAssociation()
      .withMasterProfileId(actionWrapper.getId())
      .withDetailProfileId(mappingWrapper.getId())
      .withMasterProfileType(ACTION_PROFILE)
      .withDetailProfileType(MAPPING_PROFILE)
      .withOrder(0);

    var jobToActionAssociation = new ProfileAssociation()
      .withId(ASSOCIATION_UUID)
      .withMasterProfileId(jobProfileWrapper.getProfile().getId())
      .withDetailProfileId(actionWrapper.getId())
      .withMasterProfileType(JOB_PROFILE)
      .withDetailProfileType(ACTION_PROFILE)
      .withOrder(5)
      .withTriggered(true);

    return jobProfileWrapper.getProfile().withAddedRelations(List.of(jobToActionAssociation, actionToMappingAssociation));
  }

  private JobProfileUpdateDto createJobProfileWithMatch(TestContext testContext,
                                                        JobProfileWrapper jobProfileWrapper,
                                                        ActionProfileWrapper actionProfileWrapper,
                                                        MappingProfileWrapper mappingProfileWrapper,
                                                        MatchProfileWrapper matchProfileWrapper) {
    var mappingWrapper = postProfile(testContext, mappingProfileWrapper, MAPPING_PROFILES_URL);
    var actionWrapper = postProfile(testContext, actionProfileWrapper, ACTION_PROFILES_URL);
    var matchWrapper = postProfile(testContext, matchProfileWrapper, MATCH_PROFILES_URL);

    var actionToMappingAssociation = new ProfileAssociation()
      .withMasterProfileId(actionWrapper.getId())
      .withDetailProfileId(mappingWrapper.getId())
      .withMasterProfileType(ACTION_PROFILE)
      .withDetailProfileType(MAPPING_PROFILE)
      .withOrder(0);

    var matchToActionAssociation = new ProfileAssociation()
      .withMasterProfileId(matchWrapper.getId())
      .withDetailProfileId(actionWrapper.getId())
      .withMasterProfileType(MATCH_PROFILE)
      .withDetailProfileType(ACTION_PROFILE)
      .withOrder(5)
      .withTriggered(true);

    var jobToMatchAssociation = new ProfileAssociation()
      .withId(ASSOCIATION_UUID)
      .withMasterProfileId(jobProfileWrapper.getProfile().getId())
      .withDetailProfileId(matchProfileWrapper.getId())
      .withMasterProfileType(JOB_PROFILE)
      .withDetailProfileType(MATCH_PROFILE)
      .withOrder(5)
      .withTriggered(true);

    return jobProfileWrapper.getProfile()
      .withAddedRelations(List.of(jobToMatchAssociation, matchToActionAssociation, actionToMappingAssociation));
  }

  private void shouldPostJobToMatchAndGetById(TestContext testContext,
                                              JobProfileWrapper jobProfileWrapper,
                                              ActionProfileWrapper actionProfileWrapper,
                                              MappingProfileWrapper mappingProfileWrapper,
                                              MatchProfileWrapper matchProfileWrapper) {
    var jobProfile = createJobProfileWithMatch(testContext, jobProfileWrapper, actionProfileWrapper,
      mappingProfileWrapper, matchProfileWrapper);
    ProfileWrapper<JobProfileUpdateDto> masterWrapper = postProfile(testContext, new JobProfileWrapper(jobProfile),
      JOB_PROFILES_URL);

    RestAssured.given()
      .spec(spec)
      .queryParam("master", JOB_PROFILE)
      .queryParam("detail", MATCH_PROFILE)
      .when()
      .get(ASSOCIATED_PROFILES_URL + "/" + ASSOCIATION_UUID)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(ASSOCIATION_UUID))
      .body("masterProfileId", is(masterWrapper.getId()))
      .body("detailProfileId", is(jobProfile.getAddedRelations().get(0).getDetailProfileId()))
      .body("order", is(jobProfile.getAddedRelations().get(0).getOrder()));

    clearTables(testContext);
  }

  public <M, D> void shouldPostAndGetById(TestContext testContext, ProfileWrapper<M> masterProfileWrapper, ProfileWrapper<D> detailProfileWrapper,
                                          String masterProfileUrl, String detailProfileUrl, ProfileType masterContentType, ProfileType detailContentType) {
    ProfileWrapper<M> masterWrapper = postProfile(testContext, masterProfileWrapper, masterProfileUrl);
    ProfileWrapper<D> detailWrapper = postProfile(testContext, detailProfileWrapper, detailProfileUrl);

    ProfileAssociation profileAssociation = new ProfileAssociation()
      .withMasterProfileId(masterWrapper.getId())
      .withDetailProfileId(detailWrapper.getId())
      .withMasterProfileType(ProfileType.valueOf(masterContentType.value()))
      .withDetailProfileType(ProfileType.valueOf(detailContentType.value()))
      .withOrder(5)
      .withTriggered(true);

    Async async = testContext.async();
    Response createResponse = RestAssured.given()
      .spec(spec)
      .queryParam("master", masterContentType.value())
      .queryParam("detail", detailContentType.value())
      .body(profileAssociation)
      .when()
      .post(ASSOCIATED_PROFILES_URL);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    ProfileAssociation savedProfileAssociation = createResponse.body().as(ProfileAssociation.class);
    async.complete();

    async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterContentType.value())
      .queryParam("detail", detailContentType.value())
      .when()
      .get(ASSOCIATED_PROFILES_URL + "/" + savedProfileAssociation.getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(savedProfileAssociation.getId()))
      .body("masterProfileId", is(masterWrapper.getId()))
      .body("detailProfileId", is(detailWrapper.getId()))
      .body("order", is(savedProfileAssociation.getOrder()));
    async.complete();

    clearTables(testContext);
  }

  @Test
  public void shouldReturnBadRequestOnPost(TestContext testContext) {
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .body(new JobProfile())
      .when()
      .post(ASSOCIATED_PROFILES_URL)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
    async.complete();
  }

  @Test
  public void runTestShouldReturnNotFoundOnDelete(TestContext testContext) {
    shouldReturnNotFoundOnDelete(testContext, ACTION_PROFILE, ACTION_PROFILE);
    shouldReturnNotFoundOnDelete(testContext, ACTION_PROFILE, MAPPING_PROFILE);
    shouldReturnNotFoundOnDelete(testContext, ACTION_PROFILE, MATCH_PROFILE);
    shouldReturnNotFoundOnDelete(testContext, JOB_PROFILE, ACTION_PROFILE);
    shouldReturnNotFoundOnDelete(testContext, JOB_PROFILE, MATCH_PROFILE);
    shouldReturnNotFoundOnDelete(testContext, MATCH_PROFILE, ACTION_PROFILE);
    shouldReturnNotFoundOnDelete(testContext, MATCH_PROFILE, MATCH_PROFILE);
  }

  public void shouldReturnNotFoundOnDelete(TestContext testContext, ProfileType masterProfileType, ProfileType detailProfileType) {
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterProfileType.value())
      .queryParam("detail", detailProfileType.value())
      .when()
      .delete(ASSOCIATED_PROFILES_URL + "/" + UUID.randomUUID())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
    async.complete();
  }

  @Test
  public void runTestShouldDeleteProfileOnDelete(TestContext testContext) {
    // action to mapping
    shouldDeleteProfileOnDelete(testContext, new ActionProfileWrapper(actionProfile3), new MappingProfileWrapper(mappingProfile1),
      ACTION_PROFILES_URL, MAPPING_PROFILES_URL, ACTION_PROFILE, MAPPING_PROFILE);
    // job to action
    shouldDeleteJobToActionOnDelete(testContext, new JobProfileWrapper(jobProfile1),
      new ActionProfileWrapper(actionProfile1), new MappingProfileWrapper(mappingProfile1));
    // job to match
    shouldDeleteJobToMatchOnDelete(testContext, new JobProfileWrapper(jobProfile2), new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1), new MatchProfileWrapper(matchProfile1));
    // match to action
    shouldDeleteProfileOnDelete(testContext, new MatchProfileWrapper(matchProfile2),
      new ActionProfileWrapper(actionProfile1),
      MATCH_PROFILES_URL, ACTION_PROFILES_URL, MATCH_PROFILE, ACTION_PROFILE);
    // match to match
    shouldDeleteProfileOnDelete(testContext, new MatchProfileWrapper(matchProfile1), new MatchProfileWrapper(matchProfile2),
      MATCH_PROFILES_URL, MATCH_PROFILES_URL, MATCH_PROFILE, MATCH_PROFILE);
  }

  public <M, D> void shouldDeleteJobToActionOnDelete(TestContext testContext,
                                                     JobProfileWrapper jobProfileWrapper,
                                                     ActionProfileWrapper actionProfileWrapper,
                                                     MappingProfileWrapper mappingProfileWrapper) {
    var jobProfile = createJobProfileWithAction(testContext, jobProfileWrapper, actionProfileWrapper,
      mappingProfileWrapper);

    postProfile(testContext, new JobProfileWrapper(jobProfile), JOB_PROFILES_URL);

    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", JOB_PROFILE)
      .queryParam("detail", ACTION_PROFILE)
      .when()
      .delete(ASSOCIATED_PROFILES_URL + "/" + ASSOCIATION_UUID)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);
    async.complete();

    clearTables(testContext);
  }

  public <M, D> void shouldDeleteJobToMatchOnDelete(TestContext testContext,
                                                     JobProfileWrapper jobProfileWrapper,
                                                     ActionProfileWrapper actionProfileWrapper,
                                                     MappingProfileWrapper mappingProfileWrapper,
                                                     MatchProfileWrapper matchProfileWrapper) {
    var jobProfile = createJobProfileWithMatch(testContext, jobProfileWrapper, actionProfileWrapper,
      mappingProfileWrapper, matchProfileWrapper);

    postProfile(testContext, new JobProfileWrapper(jobProfile), JOB_PROFILES_URL);

    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", JOB_PROFILE)
      .queryParam("detail", ACTION_PROFILE)
      .when()
      .delete(ASSOCIATED_PROFILES_URL + "/" + ASSOCIATION_UUID)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);
    async.complete();

    clearTables(testContext);
  }

  public <M, D> void shouldDeleteProfileOnDelete(TestContext testContext, ProfileWrapper<M> masterWrapper, ProfileWrapper<D> detailWrapper,
                                                 String masterProfileUrl, String detailProfileUrl, ProfileType masterProfileType, ProfileType detailProfileType) {
    ProfileWrapper<D> detailProfileWrapper = postProfile(testContext, detailWrapper, detailProfileUrl);
    ProfileWrapper<M> masterProfileWrapper = postProfile(testContext, masterWrapper, masterProfileUrl);

    ProfileAssociation profileAssociation = new ProfileAssociation()
      .withMasterProfileId(masterProfileWrapper.getId())
      .withDetailProfileId(detailProfileWrapper.getId())
      .withMasterProfileType(ProfileType.valueOf(masterProfileType.value()))
      .withDetailProfileType(ProfileType.valueOf(detailProfileType.value()))
      .withOrder(10)
      .withTriggered(false);

    Async async = testContext.async();
    ProfileAssociation savedProfileAssociation = RestAssured.given()
      .spec(spec)
      .queryParam("master", masterProfileType.value())
      .queryParam("detail", detailProfileType.value())
      .body(profileAssociation)
      .when()
      .post(ASSOCIATED_PROFILES_URL)
      .then()
      .statusCode(is(HttpStatus.SC_CREATED))
      .and()
      .extract().body().as(ProfileAssociation.class);
    async.complete();

    async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterProfileType.value())
      .queryParam("detail", detailProfileType.value())
      .when()
      .delete(ASSOCIATED_PROFILES_URL + "/" + savedProfileAssociation.getId())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);
    async.complete();

    clearTables(testContext);
  }

  @Test
  public void runTestShouldReturnBadRequestOnPut(TestContext testContext) {
    shouldReturnBadRequestOnPut(testContext, ACTION_PROFILE, ACTION_PROFILE);
    shouldReturnBadRequestOnPut(testContext, ACTION_PROFILE, MAPPING_PROFILE);
    shouldReturnBadRequestOnPut(testContext, ACTION_PROFILE, MATCH_PROFILE);
    shouldReturnBadRequestOnPut(testContext, JOB_PROFILE, ACTION_PROFILE);
    shouldReturnBadRequestOnPut(testContext, JOB_PROFILE, MATCH_PROFILE);
    shouldReturnBadRequestOnPut(testContext, MATCH_PROFILE, ACTION_PROFILE);
    shouldReturnBadRequestOnPut(testContext, MATCH_PROFILE, MATCH_PROFILE);
  }

  public void shouldReturnBadRequestOnPut(TestContext testContext, ProfileType masterContentType, ProfileType detailContentType) {
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterContentType.value())
      .queryParam("detail", detailContentType.value())
      .body(new JobProfile())
      .when()
      .put(ASSOCIATED_PROFILES_URL + "/" + UUID.randomUUID())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
    async.complete();
  }

  @Test
  public void runTestShouldReturnNotFoundOnPut(TestContext testContext) {
    String jobProfileId = UUID.randomUUID().toString();
    String actionProfileId = UUID.randomUUID().toString();
    String firstMatchProfileId = UUID.randomUUID().toString();
    String secondMatchProfileId = UUID.randomUUID().toString();
    String mappingProfileId = UUID.randomUUID().toString();

    JobProfileWrapper existingJobProfileWrapper = new JobProfileWrapper(new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(jobProfileId)
        .withName("Existing JobProfile")
        .withDataType(MARC)
        .withDeleted(false)
        .withHidden(false)
        .withDescription("test-description")));

    ActionProfileWrapper existingActionProfileWrapper = new ActionProfileWrapper(new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withId(actionProfileId)
        .withName("Existing ActionProfile")
        .withFolioRecord(MARC_BIBLIOGRAPHIC)
        .withAction(UPDATE)
        .withDeleted(false)
        .withHidden(false)
        .withDescription("test-description")));

    MatchProfileWrapper existingMatchProfileWrapper = new MatchProfileWrapper(new MatchProfileUpdateDto()
      .withProfile(new MatchProfile()
        .withId(firstMatchProfileId)
        .withName("Existing MatchProfile")
        .withMatchDetails(Lists.newArrayList())
        .withDeleted(false)
        .withHidden(false)
        .withDescription("test-description")));

    MatchProfileWrapper secondExistingMatchProfileWrapper = new MatchProfileWrapper(new MatchProfileUpdateDto()
      .withProfile(new MatchProfile()
        .withId(secondMatchProfileId)
        .withName("Second Existing MatchProfile")
        .withMatchDetails(Lists.newArrayList())
        .withDeleted(false)
        .withHidden(false)
        .withDescription("test-description")));

    MappingProfileWrapper existingMappingProfileWrapper = new MappingProfileWrapper(new MappingProfileUpdateDto()
      .withProfile(new MappingProfile()
        .withId(mappingProfileId)
        .withName("Existing MappingProfile")
        .withExistingRecordType(EntityType.INSTANCE)
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withMappingDetails(new MappingDetail())
        .withDeleted(false)
        .withHidden(false)
        .withDescription("test-description")));

    RestAssured.given()
      .spec(spec)
      .body(existingActionProfileWrapper.getProfile())
      .when()
      .post(ACTION_PROFILES_URL)
      .then().log().all()
      .statusCode(HttpStatus.SC_CREATED)
      .body("profile.id", is(actionProfileId));

    RestAssured.given()
      .spec(spec)
      .body(existingMatchProfileWrapper.getProfile())
      .when()
      .post(MATCH_PROFILES_URL)
      .then().log().all()
      .statusCode(HttpStatus.SC_CREATED)
      .body("profile.id", is(firstMatchProfileId));

    RestAssured.given()
      .spec(spec)
      .body(secondExistingMatchProfileWrapper.getProfile())
      .when()
      .post(MATCH_PROFILES_URL)
      .then().log().all()
      .statusCode(HttpStatus.SC_CREATED)
      .body("profile.id", is(secondMatchProfileId));

    RestAssured.given()
      .spec(spec)
      .body(existingMappingProfileWrapper.getProfile())
      .when()
      .post(MAPPING_PROFILES_URL)
      .then().log().all()
      .statusCode(HttpStatus.SC_CREATED)
      .body("profile.id", is(mappingProfileId));

    shouldReturnNotFoundOnPut(testContext, ACTION_PROFILE, MAPPING_PROFILE, actionProfileId, mappingProfileId, ProfileType.ACTION_PROFILE, ProfileType.MAPPING_PROFILE);
    shouldReturnNotFoundOnPutJobToAction(testContext, existingJobProfileWrapper, actionProfileId, mappingProfileId, firstMatchProfileId);
    shouldReturnNotFoundOnPut(testContext, ACTION_PROFILE, MATCH_PROFILE, actionProfileId, firstMatchProfileId, ProfileType.ACTION_PROFILE, ProfileType.MATCH_PROFILE);
    shouldReturnNotFoundOnPut(testContext, MATCH_PROFILE, ACTION_PROFILE, firstMatchProfileId, actionProfileId, ProfileType.MATCH_PROFILE, ProfileType.ACTION_PROFILE);
    shouldReturnNotFoundOnPut(testContext, MATCH_PROFILE, MATCH_PROFILE, firstMatchProfileId, secondMatchProfileId, ProfileType.MATCH_PROFILE, ProfileType.MATCH_PROFILE);
  }

  public void shouldReturnNotFoundOnPutJobToAction(TestContext testContext,
                                                   JobProfileWrapper jobProfileWrapper,
                                                   String actionProfileId,
                                                   String mappingProfileId,
                                                   String matchProfileId) {
    Async async = testContext.async();

    var jobToMatchAssociation = new ProfileAssociation()
      .withMasterProfileId(jobProfileWrapper.getId())
      .withDetailProfileId(matchProfileId)
      .withMasterProfileType(JOB_PROFILE)
      .withDetailProfileType(MATCH_PROFILE);

    var matchToActionAssociation = new ProfileAssociation()
      .withMasterProfileId(matchProfileId)
      .withDetailProfileId(actionProfileId)
      .withMasterProfileType(MATCH_PROFILE)
      .withDetailProfileType(ACTION_PROFILE);

    var actionToMappingAssociation = new ProfileAssociation()
      .withMasterProfileId(actionProfileId)
      .withDetailProfileId(mappingProfileId)
      .withMasterProfileType(ACTION_PROFILE)
      .withDetailProfileType(MAPPING_PROFILE);

    RestAssured.given()
      .spec(spec)
      .body(jobProfileWrapper.getProfile()
        .withAddedRelations(List.of(jobToMatchAssociation, matchToActionAssociation, actionToMappingAssociation)))
      .when()
      .post(JOB_PROFILES_URL)
      .then().log().all()
      .statusCode(HttpStatus.SC_CREATED);

    RestAssured.given()
      .spec(spec)
      .queryParam("master", JOB_PROFILE)
      .queryParam("detail", ACTION_PROFILE)
      .body(jobToMatchAssociation)
      .when()
      .put(ASSOCIATED_PROFILES_URL + "/" + UUID.randomUUID())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
    async.complete();
  }

  public void shouldReturnNotFoundOnPut(TestContext testContext, ProfileType masterContentType, ProfileType detailContentType, String masterId, String detailId,
                                        ProfileType masterType, ProfileType detailType) {
    Async async = testContext.async();

    ProfileAssociation profileAssociation = new ProfileAssociation()
      .withId(UUID.randomUUID().toString())
      .withMasterProfileId(masterId)
      .withDetailProfileId(detailId)
      .withMasterProfileType(masterType)
      .withDetailProfileType(detailType);
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterContentType.value())
      .queryParam("detail", detailContentType.value())
      .body(profileAssociation)
      .when()
      .put(ASSOCIATED_PROFILES_URL + "/" + UUID.randomUUID())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
    async.complete();
  }

  @Test
  public void runTestShouldUpdateProfileAssociationOnPut(TestContext testContext) {
    // action to mapping
    shouldUpdateProfileAssociationOnPut(testContext, new ActionProfileWrapper(actionProfile2), new MappingProfileWrapper(mappingProfile1),
      new MappingProfileWrapper(mappingProfile2), ACTION_PROFILES_URL, MAPPING_PROFILES_URL, ACTION_PROFILE, MAPPING_PROFILE);
    // job to match to action
    shouldUpdateJobToMatchAssociationOnPut(testContext, new JobProfileWrapper(jobProfile1),
      new ActionProfileWrapper(actionProfile2), new MappingProfileWrapper(mappingProfile1),
      new MatchProfileWrapper(matchProfile1));
    // match to action
    shouldUpdateProfileAssociationOnPut(testContext, new MatchProfileWrapper(matchProfile1),
      new ActionProfileWrapper(actionProfile1),
      new ActionProfileWrapper(actionProfile2), MATCH_PROFILES_URL, ACTION_PROFILES_URL, MATCH_PROFILE, ACTION_PROFILE);
    // match to match
    shouldUpdateProfileAssociationOnPut(testContext, new MatchProfileWrapper(matchProfile1), new MatchProfileWrapper(matchProfile2),
      new MatchProfileWrapper(matchProfile3), MATCH_PROFILES_URL, MATCH_PROFILES_URL, MATCH_PROFILE, MATCH_PROFILE);
  }

  public <M, D> void shouldUpdateJobToMatchAssociationOnPut(TestContext testContext,
                                                            JobProfileWrapper jobProfileWrapper,
                                                            ActionProfileWrapper actionProfileWrapper,
                                                            MappingProfileWrapper mappingProfileWrapper,
                                                            MatchProfileWrapper matchProfileWrapper) {
    var jobProfile = createJobProfileWithMatch(testContext, jobProfileWrapper, actionProfileWrapper,
      mappingProfileWrapper, matchProfileWrapper);

    var jobWrapper = postProfile(testContext, new JobProfileWrapper(jobProfile), JOB_PROFILES_URL);

    var mappingProfile = postProfile(testContext, new MappingProfileWrapper(mappingProfile2), MAPPING_PROFILES_URL);

    var actionToMappingAssociation = new ProfileAssociation()
      .withId(jobWrapper.getProfile().getAddedRelations().get(2).getId())
      .withMasterProfileId(jobWrapper.getProfile().getAddedRelations().get(2).getMasterProfileId())
      .withDetailProfileId(mappingProfile.getProfile().getId())
      .withMasterProfileType(ACTION_PROFILE)
      .withDetailProfileType(MAPPING_PROFILE);

    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", ACTION_PROFILE)
      .queryParam("detail", MAPPING_PROFILE)
      .body(actionToMappingAssociation)
      .when()
      .put(ASSOCIATED_PROFILES_URL + "/" + actionToMappingAssociation.getId())
      .then()
      .statusCode(is(HttpStatus.SC_OK))
      .body("id", is(actionToMappingAssociation.getId()))
      .body("masterProfileId", is(jobWrapper.getProfile().getAddedRelations().get(2).getMasterProfileId()))
      .body("detailProfileId", is(mappingProfile.getId()))
      .body("order", is(actionToMappingAssociation.getOrder()));
    async.complete();

    clearTables(testContext);
  }

  public <M, D> void shouldUpdateProfileAssociationOnPut(TestContext testContext, ProfileWrapper<M> masterWrapper, ProfileWrapper<D> detailWrapper, ProfileWrapper<D> detailWrapper2,
                                                         String masterProfileUrl, String detailProfileUrl, ProfileType masterContentType, ProfileType detailContentType) {
    ProfileWrapper<M> masterProfileWrapper = postProfile(testContext, masterWrapper, masterProfileUrl);
    ProfileWrapper<D> detailProfileWrapper = postProfile(testContext, detailWrapper, detailProfileUrl);

    ProfileAssociation profileAssociation = new ProfileAssociation()
      .withMasterProfileId(masterProfileWrapper.getId())
      .withDetailProfileId(detailProfileWrapper.getId())
      .withMasterProfileType(ProfileType.valueOf(masterContentType.value()))
      .withDetailProfileType(ProfileType.valueOf(detailContentType.value()))
      .withOrder(7)
      .withTriggered(true);

    Async async = testContext.async();
    ProfileAssociation savedProfileAssociation = RestAssured.given()
      .spec(spec)
      .queryParam("master", masterContentType.value())
      .queryParam("detail", detailContentType.value())
      .body(profileAssociation)
      .when()
      .post(ASSOCIATED_PROFILES_URL)
      .then()
      .statusCode(is(HttpStatus.SC_CREATED))
      .and()
      .extract().body().as(ProfileAssociation.class);
    async.complete();

    ProfileWrapper<D> detailProfileWrapper2 = postProfile(testContext, detailWrapper2, detailProfileUrl);
    savedProfileAssociation.setDetailProfileId(detailProfileWrapper2.getId());

    async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterContentType.value())
      .queryParam("detail", detailContentType.value())
      .body(savedProfileAssociation)
      .when()
      .put(ASSOCIATED_PROFILES_URL + "/" + savedProfileAssociation.getId())
      .then()
      .statusCode(is(HttpStatus.SC_OK))
      .body("id", is(savedProfileAssociation.getId()))
      .body("masterProfileId", is(masterProfileWrapper.getId()))
      .body("detailProfileId", is(detailProfileWrapper2.getId()))
      .body("order", is(savedProfileAssociation.getOrder()));
    async.complete();

    clearTables(testContext);
  }

  @Test
  public void runTestGetDetailsByMasterProfile_OK(TestContext testContext) {
    // action to mapping
    getDetailProfilesByMasterProfile_OK(testContext, new ActionProfileWrapper(actionProfile1),
      new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1), new MappingProfileWrapper(mappingProfile2), ACTION_PROFILES_URL, MAPPING_PROFILES_URL, ACTION_PROFILE, MAPPING_PROFILE);
    // job to match to action
    getDetailProfilesByMasterProfile_OKJobToMatchToAction(testContext, new JobProfileWrapper(jobProfile1),
      new ActionProfileWrapper(actionProfile2), new MappingProfileWrapper(mappingProfile1),
      new MatchProfileWrapper(matchProfile1));
    // match to action
    getDetailProfilesByMasterProfile_OK(testContext, new MatchProfileWrapper(matchProfile1),
      new MatchProfileWrapper(matchProfile2), new ActionProfileWrapper(actionProfile1), new ActionProfileWrapper(actionProfile2), MATCH_PROFILES_URL,
      ACTION_PROFILES_URL, MATCH_PROFILE, ACTION_PROFILE);
    // match to match
    getDetailProfilesByMasterProfile_OK(testContext, new MatchProfileWrapper(matchProfile1), new MatchProfileWrapper(matchProfile2),
      new MatchProfileWrapper(matchProfile3), new MatchProfileWrapper(matchProfile4),
      MATCH_PROFILES_URL, MATCH_PROFILES_URL, MATCH_PROFILE, MATCH_PROFILE);
  }


  public <M, D> void getDetailProfilesByMasterProfile_OKJobToMatchToAction(TestContext testContext,
                                                                           JobProfileWrapper jobProfileWrapper,
                                                                           ActionProfileWrapper actionProfileWrapper,
                                                                           MappingProfileWrapper mappingProfileWrapper,
                                                                           MatchProfileWrapper matchProfileWrapper) {
    var jobProfile = createJobProfileWithMatch(testContext, jobProfileWrapper, actionProfileWrapper,
      mappingProfileWrapper, matchProfileWrapper);

    var jobWrapper = postProfile(testContext, new JobProfileWrapper(jobProfile), JOB_PROFILES_URL);

    RestAssured.given()
      .spec(spec)
      .queryParam("masterType", JOB_PROFILE)
      .queryParam("detailType", MATCH_PROFILE)
      .queryParam("query", "name=" + matchProfileWrapper.getName())
      .when()
      .get(DETAILS_BY_MASTER_URL, jobWrapper.getProfile().getId())
      .then().statusCode(is(HttpStatus.SC_OK))
      .body("contentType", is(JOB_PROFILE.value()))
      .body("id", is(jobWrapper.getProfile().getId()))
      .body("content.id", is(jobWrapper.getProfile().getId()))
      .body("content.userInfo.firstName", is(jobWrapper.getUserInfo().getFirstName()))
      .body("content.userInfo.lastName", is(jobWrapper.getUserInfo().getLastName()))
      .body("content.userInfo.userName", is(jobWrapper.getUserInfo().getUserName()))
      .body("content.metadata.createdByUserId", is(jobWrapper.getMetadata().getCreatedByUserId()))
      .body("content.metadata.updatedByUserId", is(jobWrapper.getMetadata().getUpdatedByUserId()))
      .body("childSnapshotWrappers.size()", is(1))
      .body("childSnapshotWrappers[0].id", is(matchProfileWrapper.getId()))
      .body("childSnapshotWrappers[0].contentType", is(MATCH_PROFILE.value()))
      .body("childSnapshotWrappers[0].content.id", is(matchProfileWrapper.getProfile().getId()))
      .body("childSnapshotWrappers[0].content.name", is(matchProfileWrapper.getName()))
      .body("childSnapshotWrappers[0].content.userInfo.firstName", is(matchProfileWrapper.getUserInfo().getFirstName()))
      .body("childSnapshotWrappers[0].content.userInfo.lastName", is(matchProfileWrapper.getUserInfo().getLastName()))
      .body("childSnapshotWrappers[0].content.userInfo.userName", is(matchProfileWrapper.getUserInfo().getUserName()));

    clearTables(testContext);
  }

  public <M, D> void getDetailProfilesByMasterProfile_OK(TestContext testContext, ProfileWrapper<M> masterWrapper, ProfileWrapper<M> masterWrapper2,
                                                         ProfileWrapper<D> detailWrapper, ProfileWrapper<D> detailWrapper2,
                                                         String masterProfileUrl, String detailProfileUrl, ProfileType masterProfileType, ProfileType detailProfileType) {
    ProfileWrapper<M> masterProfileWrapper1 = postProfile(testContext, masterWrapper, masterProfileUrl);
    ProfileWrapper<M> masterProfileWrapper2 = postProfile(testContext, masterWrapper2, masterProfileUrl);
    ProfileWrapper<D> detailProfileWrapper1 = postProfile(testContext, detailWrapper, detailProfileUrl);
    ProfileWrapper<D> detailProfileWrapper2 = postProfile(testContext, detailWrapper2, detailProfileUrl);

    ProfileAssociation profileAssociation1 = new ProfileAssociation()
      .withMasterProfileId(masterProfileWrapper1.getId())
      .withDetailProfileId(detailProfileWrapper1.getId())
      .withMasterProfileType(ProfileType.valueOf(masterProfileType.value()))
      .withDetailProfileType(ProfileType.valueOf(detailProfileType.value()))
      .withOrder(7)
      .withTriggered(true);

    ProfileAssociation profileAssociation2 = new ProfileAssociation()
      .withMasterProfileId(masterProfileWrapper2.getId())
      .withDetailProfileId(detailProfileWrapper2.getId())
      .withMasterProfileType(ProfileType.valueOf(masterProfileType.value()))
      .withDetailProfileType(ProfileType.valueOf(detailProfileType.value()))
      .withOrder(7)
      .withTriggered(true);

    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterProfileType.value())
      .queryParam("detail", detailProfileType.value())
      .body(profileAssociation1)
      .when()
      .post(ASSOCIATED_PROFILES_URL)
      .then()
      .statusCode(is(HttpStatus.SC_CREATED))
      .and()
      .extract().body().as(ProfileAssociation.class);
    async.complete();

    async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterProfileType.value())
      .queryParam("detail", detailProfileType.value())
      .body(profileAssociation2)
      .when()
      .post(ASSOCIATED_PROFILES_URL)
      .then()
      .statusCode(is(HttpStatus.SC_CREATED))
      .and()
      .extract().body().as(ProfileAssociation.class);
    async.complete();

    RestAssured.given()
      .spec(spec)
      .queryParam("masterType", masterProfileType.value())
      .queryParam("detailType", detailProfileType.value())
      .queryParam("query", "name=" + detailProfileWrapper1.getName())
      .when()
      .get(DETAILS_BY_MASTER_URL, masterProfileWrapper1.getId())
      .then().statusCode(is(HttpStatus.SC_OK))
      .body("contentType", is(masterProfileType.value()))
      .body("id", is(masterProfileWrapper1.getId()))
      .body("content.id", is(masterProfileWrapper1.getId()))
      .body("content.userInfo.firstName", is(masterProfileWrapper1.getUserInfo().getFirstName()))
      .body("content.userInfo.lastName", is(masterProfileWrapper1.getUserInfo().getLastName()))
      .body("content.userInfo.userName", is(masterProfileWrapper1.getUserInfo().getUserName()))
      .body("content.metadata.createdByUserId", is(masterProfileWrapper1.getMetadata().getCreatedByUserId()))
      .body("content.metadata.updatedByUserId", is(masterProfileWrapper1.getMetadata().getUpdatedByUserId()))
      .body("childSnapshotWrappers.size()", is(1))
      .body("childSnapshotWrappers[0].id", is(detailProfileWrapper1.getId()))
      .body("childSnapshotWrappers[0].contentType", is(detailProfileType.value()))
      .body("childSnapshotWrappers[0].content.id", is(detailProfileWrapper1.getId()))
      .body("childSnapshotWrappers[0].content.name", is(detailProfileWrapper1.getName()))
      .body("childSnapshotWrappers[0].content.userInfo.firstName", is(detailProfileWrapper1.getUserInfo().getFirstName()))
      .body("childSnapshotWrappers[0].content.userInfo.lastName", is(detailProfileWrapper1.getUserInfo().getLastName()))
      .body("childSnapshotWrappers[0].content.userInfo.userName", is(detailProfileWrapper1.getUserInfo().getUserName()));

    clearTables(testContext);
  }

  @Test
  public void runTestGetDetailsByMasterProfile_NotFound(TestContext testContext) {
    getDetailProfilesByMasterProfile_NotFound(testContext, ACTION_PROFILE);
    getDetailProfilesByMasterProfile_NotFound(testContext, JOB_PROFILE);
    getDetailProfilesByMasterProfile_NotFound(testContext, MATCH_PROFILE);
  }

  public void getDetailProfilesByMasterProfile_NotFound(TestContext testContext, ProfileType masterContentType) {
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("masterType", masterContentType.value())
      .when()
      .get(DETAILS_BY_MASTER_URL, UUID.randomUUID().toString())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
    async.complete();
  }

  @Test
  public void getDetailsByMasterProfile_WrongQueryParameter(TestContext testContext) {
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("masterType", "foo")
      .when()
      .get(DETAILS_BY_MASTER_URL, UUID.randomUUID().toString())
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .body(is("The specified type: foo is wrong. It should be " + Arrays.toString(ProfileType.values())));
    async.complete();
  }

  @Test
  public void runTestGetDetailsByMasterProfile_emptyDetailsListWithMasterProfile(TestContext testContext) {
    // job to match, match to action
    getDetailsJobToMatchToActionByMasterProfile_emptyDetailsListWithMasterProfile(testContext,
      new JobProfileWrapper(jobProfile1), new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1), new MatchProfileWrapper(matchProfile1));
    // match to action, match to match
    getDetailsByMasterProfile_emptyDetailsListWithMasterProfile(testContext, new MatchProfileWrapper(matchProfile1), MATCH_PROFILES_URL, MATCH_PROFILE);
  }

  public <M> void getDetailsJobToMatchToActionByMasterProfile_emptyDetailsListWithMasterProfile(TestContext testContext,
                                                                                                JobProfileWrapper jobProfileWrapper,
                                                                                                ActionProfileWrapper actionProfileWrapper,
                                                                                                MappingProfileWrapper mappingProfileWrapper,
                                                                                                MatchProfileWrapper matchProfileWrapper) {
    var jobProfile = createJobProfileWithMatch(testContext, jobProfileWrapper, actionProfileWrapper,
      mappingProfileWrapper, matchProfileWrapper);

    var jobWrapper = postProfile(testContext, new JobProfileWrapper(jobProfile), JOB_PROFILES_URL);
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("masterType", JOB_PROFILE)
      .when()
      .get(DETAILS_BY_MASTER_URL, jobWrapper.getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("contentType", is(JOB_PROFILE.value()))
      .body("id", is(jobWrapper.getId()))
      .body("content.id", is(jobWrapper.getId()))
      .body("content.userInfo.firstName", is(jobWrapper.getUserInfo().getFirstName()))
      .body("content.userInfo.lastName", is(jobWrapper.getUserInfo().getLastName()))
      .body("content.userInfo.userName", is(jobWrapper.getUserInfo().getUserName()))
      .body("content.metadata.createdByUserId", is(jobWrapper.getMetadata().getCreatedByUserId()))
      .body("content.metadata.updatedByUserId", is(jobWrapper.getMetadata().getUpdatedByUserId()))
      .body("childSnapshotWrappers.size()", is(2));
    async.complete();
    clearTables(testContext);
  }

  public <M> void getDetailsByMasterProfile_emptyDetailsListWithMasterProfile(TestContext testContext, ProfileWrapper<M> masterWrapper,
                                                                              String masterProfileUrl, ProfileType masterProfileType) {
    ProfileWrapper<M> masterProfileWrapper = postProfile(testContext, masterWrapper, masterProfileUrl);
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("masterType", masterProfileType.value())
      .when()
      .get(DETAILS_BY_MASTER_URL, masterProfileWrapper.getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("contentType", is(masterProfileType.value()))
      .body("id", is(masterProfileWrapper.getId()))
      .body("content.id", is(masterProfileWrapper.getId()))
      .body("content.userInfo.firstName", is(masterProfileWrapper.getUserInfo().getFirstName()))
      .body("content.userInfo.lastName", is(masterProfileWrapper.getUserInfo().getLastName()))
      .body("content.userInfo.userName", is(masterProfileWrapper.getUserInfo().getUserName()))
      .body("content.metadata.createdByUserId", is(masterProfileWrapper.getMetadata().getCreatedByUserId()))
      .body("content.metadata.updatedByUserId", is(masterProfileWrapper.getMetadata().getUpdatedByUserId()))
      .body("childSnapshotWrappers.size()", is(0));
    async.complete();
  }

  @Test
  public void runTestGetDetailProfilesByMasterProfile_sortByName_OK(TestContext testContext) {
    // action to mapping
    getDetailProfilesByMasterProfile_sortByName_OK(testContext, new ActionProfileWrapper(actionProfile2), new MappingProfileWrapper(mappingProfile1),
      new MappingProfileWrapper(mappingProfile2), new MappingProfileWrapper(mappingProfile3), ACTION_PROFILES_URL, MAPPING_PROFILES_URL, ACTION_PROFILE, MAPPING_PROFILE);
    // job to action
    getDetailProfilesJobToMatchByMasterProfile_sortByName_OK(testContext, new JobProfileWrapper(jobProfile1),
      new ActionProfileWrapper(actionProfile2), new MappingProfileWrapper(mappingProfile1), new MatchProfileWrapper(matchProfile2));
    // match to action
    getDetailProfilesByMasterProfile_sortByName_OK(testContext, new MatchProfileWrapper(matchProfile1),
      new ActionProfileWrapper(actionProfile1),
      new ActionProfileWrapper(actionProfile2), new ActionProfileWrapper(actionProfile4), MATCH_PROFILES_URL, ACTION_PROFILES_URL, MATCH_PROFILE, ACTION_PROFILE);
    // match to match
    getDetailProfilesByMasterProfile_sortByName_OK(testContext, new MatchProfileWrapper(matchProfile1), new MatchProfileWrapper(matchProfile2),
      new MatchProfileWrapper(matchProfile3), new MatchProfileWrapper(matchProfile4), MATCH_PROFILES_URL, MATCH_PROFILES_URL, MATCH_PROFILE, MATCH_PROFILE);
  }

  public <M, D> void getDetailProfilesJobToMatchByMasterProfile_sortByName_OK(TestContext testContext,
                                                                              JobProfileWrapper jobProfileWrapper,
                                                                              ActionProfileWrapper actionProfileWrapper,
                                                                              MappingProfileWrapper mappingProfileWrapper,
                                                                              MatchProfileWrapper matchProfileWrapper) {
    var jobProfile = createJobProfileWithMatch(testContext, jobProfileWrapper, actionProfileWrapper,
      mappingProfileWrapper, matchProfileWrapper);

    var jobWrapper = postProfile(testContext, new JobProfileWrapper(jobProfile), JOB_PROFILES_URL);

    //searching by description and sorting by name
    RestAssured.given()
      .spec(spec)
      .queryParam("masterType", JOB_PROFILE.value())
      .queryParam("detailType", MATCH_PROFILE.value())
      .queryParam("query", "description=test-description and (cql.allRecords=1) sortBy name")
      .when()
      .get(DETAILS_BY_MASTER_URL, jobWrapper.getId())
      .then().statusCode(is(HttpStatus.SC_OK))
      .log().all()
      .body("contentType", is(JOB_PROFILE.value()))
      .body("childSnapshotWrappers.size()", is(1))
      .body("childSnapshotWrappers[0].content.name", is(matchProfileWrapper.getName()));

    clearTables(testContext);
  }


  public <M, D> void getDetailProfilesByMasterProfile_sortByName_OK(TestContext testContext, ProfileWrapper<M> masterWrapper,
                                                                    ProfileWrapper<D> detailWrapper, ProfileWrapper<D> detailWrapper2, ProfileWrapper<D> detailWrapper3,
                                                                    String masterProfileUrl, String detailProfileUrl, ProfileType masterProfileType, ProfileType detailProfileType) {
    ProfileWrapper<M> masterProfileWrapper1 = postProfile(testContext, masterWrapper, masterProfileUrl);

    //creates association 2
    ProfileWrapper<D> detailProfileWrapper2 = postProfile(testContext, detailWrapper2, detailProfileUrl);

    ProfileAssociation profileAssociation2 = new ProfileAssociation()
      .withMasterProfileId(masterProfileWrapper1.getId())
      .withDetailProfileId(detailProfileWrapper2.getId())
      .withMasterProfileType(ProfileType.valueOf(masterProfileType.value()))
      .withDetailProfileType(ProfileType.valueOf(detailProfileType.value()))
      .withOrder(7)
      .withTriggered(true);
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterProfileType.value())
      .queryParam("detail", detailProfileType.value())
      .body(profileAssociation2)
      .when()
      .post(ASSOCIATED_PROFILES_URL)
      .then()
      .statusCode(is(HttpStatus.SC_CREATED))
      .and()
      .extract().body().as(ProfileAssociation.class);
    async.complete();
    //end

    //creates association 1
    ProfileWrapper<D> detailProfileWrapper1 = postProfile(testContext, detailWrapper, detailProfileUrl);

    ProfileAssociation profileAssociation1 = new ProfileAssociation()
      .withMasterProfileId(masterProfileWrapper1.getId())
      .withDetailProfileId(detailProfileWrapper1.getId())
      .withMasterProfileType(ProfileType.valueOf(masterProfileType.value()))
      .withDetailProfileType(ProfileType.valueOf(detailProfileType.value()))
      .withOrder(7)
      .withTriggered(true);
    async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterProfileType.value())
      .queryParam("detail", detailProfileType.value())
      .body(profileAssociation1)
      .when()
      .post(ASSOCIATED_PROFILES_URL)
      .then()
      .statusCode(is(HttpStatus.SC_CREATED))
      .and()
      .extract().body().as(ProfileAssociation.class);
    async.complete();
    //end

    //creates association 3
    ProfileWrapper<D> detailProfileWrapper3 = postProfile(testContext, detailWrapper3, detailProfileUrl);

    ProfileAssociation profileAssociation3 = new ProfileAssociation()
      .withMasterProfileId(masterProfileWrapper1.getId())
      .withDetailProfileId(detailProfileWrapper3.getId())
      .withMasterProfileType(ProfileType.valueOf(masterProfileType.value()))
      .withDetailProfileType(ProfileType.valueOf(detailProfileType.value()))
      .withOrder(7)
      .withTriggered(true);
    async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterProfileType.value())
      .queryParam("detail", detailProfileType.value())
      .body(profileAssociation3)
      .when()
      .post(ASSOCIATED_PROFILES_URL)
      .then()
      .statusCode(is(HttpStatus.SC_CREATED))
      .and()
      .extract().body().as(ProfileAssociation.class);
    async.complete();
    //end

    //searching by description and sorting by name
    RestAssured.given()
      .spec(spec)
      .queryParam("masterType", masterProfileType.value())
      .queryParam("detailType", detailProfileType.value())
      .queryParam("query", "description=test-description and (cql.allRecords=1) sortBy name")
      .when()
      .get(DETAILS_BY_MASTER_URL, masterProfileWrapper1.getId())
      .then().statusCode(is(HttpStatus.SC_OK))
      .log().all()
      .body("contentType", is(masterProfileType.value()))
      .body("childSnapshotWrappers.size()", is(2))
      .body("childSnapshotWrappers[0].content.name", is(detailProfileWrapper1.getName()))
      .body("childSnapshotWrappers[1].content.name", is(detailProfileWrapper2.getName()));

    clearTables(testContext);
  }

  @Test
  public void runTestGetMastersByDetailProfile_OK(TestContext testContext) {
    // action to mapping
    getMastersByDetailProfile_OK(testContext, new ActionProfileWrapper(actionProfile3), new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1), new MappingProfileWrapper(mappingProfile2),
      ACTION_PROFILES_URL, MAPPING_PROFILES_URL, ACTION_PROFILE, MAPPING_PROFILE);
    // job to match, match to action
    getMastersJobToMatchToActionByDetailProfile_OK(testContext, new JobProfileWrapper(jobProfile1), new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1), new MatchProfileWrapper(matchProfile1));
    // match to action
    getMastersByDetailProfile_OK(testContext, new MatchProfileWrapper(matchProfile1), new MatchProfileWrapper(matchProfile2),
      new ActionProfileWrapper(actionProfile1), new ActionProfileWrapper(actionProfile2),
      MATCH_PROFILES_URL, ACTION_PROFILES_URL, MATCH_PROFILE, ACTION_PROFILE);
    // match to match
    getMastersByDetailProfile_OK(testContext, new MatchProfileWrapper(matchProfile1), new MatchProfileWrapper(matchProfile2),
      new MatchProfileWrapper(matchProfile3), new MatchProfileWrapper(matchProfile4),
      MATCH_PROFILES_URL, MATCH_PROFILES_URL, MATCH_PROFILE, MATCH_PROFILE);
  }

  public <M, D> void getMastersJobToMatchToActionByDetailProfile_OK(TestContext testContext,
                                                                    JobProfileWrapper jobProfileWrapper,
                                                                    ActionProfileWrapper actionProfileWrapper,
                                                                    MappingProfileWrapper mappingProfileWrapper,
                                                                    MatchProfileWrapper matchProfileWrapper) {
    var jobProfile = createJobProfileWithMatch(testContext, jobProfileWrapper, actionProfileWrapper,
      mappingProfileWrapper, matchProfileWrapper);

    var jobWrapper = postProfile(testContext, new JobProfileWrapper(jobProfile), JOB_PROFILES_URL);

    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("detailType", MATCH_PROFILE.value())
      .queryParam("masterType", JOB_PROFILE.value())
      .queryParam("query", "name=" + jobWrapper.getName())
      .when()
      .get(MASTERS_BY_DETAIL_URL, matchProfileWrapper.getId())
      .then().statusCode(is(HttpStatus.SC_OK))
      .body("contentType", is(MATCH_PROFILE.value()))
      .body("id", is(matchProfileWrapper.getId()))
      .body("content.id", is(matchProfileWrapper.getId()))
      .body("content.userInfo.firstName", is(matchProfileWrapper.getUserInfo().getFirstName()))
      .body("content.userInfo.lastName", is(matchProfileWrapper.getUserInfo().getLastName()))
      .body("content.userInfo.userName", is(matchProfileWrapper.getUserInfo().getUserName()))
      .body("content.metadata.createdByUserId", is(matchProfileWrapper.getMetadata().getCreatedByUserId()))
      .body("content.metadata.updatedByUserId", is(matchProfileWrapper.getMetadata().getUpdatedByUserId()))
      .body("childSnapshotWrappers.size()", is(1))
      .body("childSnapshotWrappers[0].id", is(jobWrapper.getId()))
      .body("childSnapshotWrappers[0].contentType", is(JOB_PROFILE.value()))
      .body("childSnapshotWrappers[0].content.id", is(jobWrapper.getId()))
      .body("childSnapshotWrappers[0].content.name", is(jobWrapper.getName()))
      .body("childSnapshotWrappers[0].content.userInfo.firstName", is(jobWrapper.getUserInfo().getFirstName()))
      .body("childSnapshotWrappers[0].content.userInfo.lastName", is(jobWrapper.getUserInfo().getLastName()))
      .body("childSnapshotWrappers[0].content.userInfo.userName", is(jobWrapper.getUserInfo().getUserName()));
    async.complete();
    clearTables(testContext);
  }

  public <M, D> void getMastersByDetailProfile_OK(TestContext testContext, ProfileWrapper<M> masterWrapper, ProfileWrapper<M> masterWrapper2,
                                                  ProfileWrapper<D> detailWrapper, ProfileWrapper<D> detailWrapper2,
                                                  String masterProfileUrl, String detailProfileUrl, ProfileType masterProfileType, ProfileType detailProfileType) {
    ProfileWrapper<M> masterProfileWrapper1 = postProfile(testContext, masterWrapper, masterProfileUrl);
    ProfileWrapper<M> masterProfileWrapper2 = postProfile(testContext, masterWrapper2, masterProfileUrl);

    ProfileWrapper<D> detailProfileWrapper1 = postProfile(testContext, detailWrapper, detailProfileUrl);
    ProfileWrapper<D> detailProfileWrapper2 = postProfile(testContext, detailWrapper2, detailProfileUrl);

    ProfileAssociation profileAssociation1 = new ProfileAssociation()
      .withMasterProfileId(masterProfileWrapper1.getId())
      .withDetailProfileId(detailProfileWrapper1.getId())
      .withMasterProfileType(ProfileType.valueOf(masterProfileType.value()))
      .withDetailProfileType(ProfileType.valueOf(detailProfileType.value()))
      .withOrder(7)
      .withTriggered(true);

    ProfileAssociation profileAssociation2 = new ProfileAssociation()
      .withMasterProfileId(masterProfileWrapper2.getId())
      .withDetailProfileId(detailProfileWrapper2.getId())
      .withMasterProfileType(ProfileType.valueOf(masterProfileType.value()))
      .withDetailProfileType(ProfileType.valueOf(detailProfileType.value()))
      .withOrder(7)
      .withTriggered(true);

    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterProfileType.value())
      .queryParam("detail", detailProfileType.value())
      .body(profileAssociation1)
      .when()
      .post(ASSOCIATED_PROFILES_URL)
      .then()
      .statusCode(is(HttpStatus.SC_CREATED))
      .and()
      .extract().body().as(ProfileAssociation.class);
    async.complete();

    async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterProfileType.value())
      .queryParam("detail", detailProfileType.value())
      .body(profileAssociation2)
      .when()
      .post(ASSOCIATED_PROFILES_URL)
      .then()
      .statusCode(is(HttpStatus.SC_CREATED))
      .and()
      .extract().body().as(ProfileAssociation.class);
    async.complete();

    RestAssured.given()
      .spec(spec)
      .queryParam("detailType", detailProfileType.value())
      .queryParam("masterType", masterProfileType.value())
      .queryParam("query", "name=" + masterProfileWrapper1.getName())
      .when()
      .get(MASTERS_BY_DETAIL_URL, detailProfileWrapper1.getId())
      .then().statusCode(is(HttpStatus.SC_OK))
      .body("contentType", is(detailProfileType.value()))
      .body("id", is(detailProfileWrapper1.getId()))
      .body("content.id", is(detailProfileWrapper1.getId()))
      .body("content.userInfo.firstName", is(detailProfileWrapper1.getUserInfo().getFirstName()))
      .body("content.userInfo.lastName", is(detailProfileWrapper1.getUserInfo().getLastName()))
      .body("content.userInfo.userName", is(detailProfileWrapper1.getUserInfo().getUserName()))
      .body("content.metadata.createdByUserId", is(detailProfileWrapper1.getMetadata().getCreatedByUserId()))
      .body("content.metadata.updatedByUserId", is(detailProfileWrapper1.getMetadata().getUpdatedByUserId()))
      .body("childSnapshotWrappers.size()", is(1))
      .body("childSnapshotWrappers[0].id", is(masterProfileWrapper1.getId()))
      .body("childSnapshotWrappers[0].contentType", is(masterProfileType.value()))
      .body("childSnapshotWrappers[0].content.id", is(masterProfileWrapper1.getId()))
      .body("childSnapshotWrappers[0].content.name", is(masterProfileWrapper1.getName()))
      .body("childSnapshotWrappers[0].content.userInfo.firstName", is(masterProfileWrapper1.getUserInfo().getFirstName()))
      .body("childSnapshotWrappers[0].content.userInfo.lastName", is(masterProfileWrapper1.getUserInfo().getLastName()))
      .body("childSnapshotWrappers[0].content.userInfo.userName", is(masterProfileWrapper1.getUserInfo().getUserName()));

    clearTables(testContext);
  }

  @Test
  public void runTestGetMastersByDetailProfile_NotFound(TestContext testContext) {
    getMastersByDetailProfile_NotFound(testContext, ACTION_PROFILE);
    getMastersByDetailProfile_NotFound(testContext, MAPPING_PROFILE);
    getMastersByDetailProfile_NotFound(testContext, MATCH_PROFILE);
  }

  public void getMastersByDetailProfile_NotFound(TestContext testContext, ProfileType detailContentType) {
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("detailType", detailContentType.value())
      .when()
      .get(MASTERS_BY_DETAIL_URL, UUID.randomUUID().toString())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
    async.complete();
  }

  @Test
  public void getMastersByDetailProfile_WrongQueryParameter(TestContext testContext) {
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("detailType", "foo")
      .when()
      .get(MASTERS_BY_DETAIL_URL, UUID.randomUUID().toString())
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .body(is("The specified type: foo is wrong. It should be " + Arrays.toString(ProfileType.values())));
    async.complete();
  }


  @Test
  public void runTestGetMastersByDetailProfile_emptyMastersListWithDetailProfile(TestContext testContext) {
    //action to match, job to match, match to match
    getMastersByDetailProfile_emptyMastersListWithDetailProfile(testContext, new MatchProfileWrapper(matchProfile1), MATCH_PROFILES_URL, MATCH_PROFILE);
    // action to mapping
    getMastersByDetailProfile_emptyMastersListWithDetailProfile(testContext, new MappingProfileWrapper(mappingProfile1), MAPPING_PROFILES_URL, MAPPING_PROFILE);
  }

  public <D> void getMastersByDetailProfile_emptyMastersListWithDetailProfile(TestContext testContext, ProfileWrapper<D> detailWrapper,
                                                                              String detailProfileUrl, ProfileType detailProfileType) {
    ProfileWrapper<D> detailProfileWrapper = postProfile(testContext, detailWrapper, detailProfileUrl);

    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("detailType", detailProfileType.value())
      .when()
      .get(MASTERS_BY_DETAIL_URL, detailProfileWrapper.getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("contentType", is(detailProfileType.value()))
      .body("id", is(detailProfileWrapper.getId()))
      .body("content.id", is(detailProfileWrapper.getId()))
      .body("content.userInfo.firstName", is(detailProfileWrapper.getUserInfo().getFirstName()))
      .body("content.userInfo.lastName", is(detailProfileWrapper.getUserInfo().getLastName()))
      .body("content.userInfo.userName", is(detailProfileWrapper.getUserInfo().getUserName()))
      .body("content.metadata.createdByUserId", is(detailProfileWrapper.getMetadata().getCreatedByUserId()))
      .body("content.metadata.updatedByUserId", is(detailProfileWrapper.getMetadata().getUpdatedByUserId()))
      .body("childSnapshotWrappers.size()", is(0));
    async.complete();
  }

  @Test
  public void runTestGetMastersByDetailProfile_sortBy_OK(TestContext testContext) {
    // action to mapping
    getMastersByDetailProfile_sortBy_OK(testContext, new ActionProfileWrapper(actionProfile1), new ActionProfileWrapper(actionProfile2), new ActionProfileWrapper(actionProfile4),
      new MappingProfileWrapper(mappingProfile1), ACTION_PROFILES_URL, MAPPING_PROFILES_URL, ACTION_PROFILE, MAPPING_PROFILE);
    // job to match, match to action
    getMastersJobToMatchToActionByDetailProfile_sortBy_OK(testContext, new JobProfileWrapper(jobProfile1), new ActionProfileWrapper(actionProfile1),
      new MappingProfileWrapper(mappingProfile1), new MatchProfileWrapper(matchProfile1));
    // match to action
    getMastersByDetailProfile_sortBy_OK(testContext, new MatchProfileWrapper(matchProfile2), new MatchProfileWrapper(matchProfile3), new MatchProfileWrapper(matchProfile4),
      new ActionProfileWrapper(actionProfile1), MATCH_PROFILES_URL, ACTION_PROFILES_URL, MATCH_PROFILE, ACTION_PROFILE);
    // match to match
    getMastersByDetailProfile_sortBy_OK(testContext, new MatchProfileWrapper(matchProfile2), new MatchProfileWrapper(matchProfile3), new MatchProfileWrapper(matchProfile4),
      new MatchProfileWrapper(matchProfile1), MATCH_PROFILES_URL, MATCH_PROFILES_URL, MATCH_PROFILE, MATCH_PROFILE);
  }

  public <M, D> void getMastersJobToMatchToActionByDetailProfile_sortBy_OK(TestContext testContext,
                                                                           JobProfileWrapper jobProfileWrapper,
                                                                           ActionProfileWrapper actionProfileWrapper,
                                                                           MappingProfileWrapper mappingProfileWrapper,
                                                                           MatchProfileWrapper matchProfileWrapper) {
    var jobProfile = createJobProfileWithMatch(testContext, jobProfileWrapper, actionProfileWrapper,
      mappingProfileWrapper, matchProfileWrapper);

    var jobWrapper = postProfile(testContext, new JobProfileWrapper(jobProfile), JOB_PROFILES_URL);

    //creates association 3
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("detailType", MATCH_PROFILE.value())
      .queryParam("masterType", JOB_PROFILE.value())
      .queryParam("query", "description=\"test*\" or description==\"*description\" sortBy name")
      .when()
      .get(MASTERS_BY_DETAIL_URL, matchProfileWrapper.getId())
      .then().statusCode(is(HttpStatus.SC_OK))
      .body("contentType", is(MATCH_PROFILE.value()))
      .body("childSnapshotWrappers.size()", is(1))
      .body("childSnapshotWrappers[0].content.name", is(jobWrapper.getName()));

    clearTables(testContext);
    async.complete();
  }

  public <M, D> void getMastersByDetailProfile_sortBy_OK(TestContext testContext,
                                                         ProfileWrapper<M> masterProfileWrapper1,
                                                         ProfileWrapper<M> masterProfileWrapper2,
                                                         ProfileWrapper<M> masterProfileWrapper3,
                                                         ProfileWrapper<D> detailProfileWrapper,
                                                         String masterProfileUrl, String detailProfileUrl,
                                                         ProfileType masterProfileType, ProfileType detailProfileType) {
    ProfileWrapper<D> detailWrapper = postProfile(testContext, detailProfileWrapper, detailProfileUrl);
    ProfileWrapper<M> masterWrapper3 = postProfile(testContext, masterProfileWrapper3, masterProfileUrl);
    ProfileWrapper<M> masterWrapper2 = postProfile(testContext, masterProfileWrapper2, masterProfileUrl);
    ProfileWrapper<M> masterWrapper1 = postProfile(testContext, masterProfileWrapper1, masterProfileUrl);

    //creates association 3
    Async async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterProfileType.value())
      .queryParam("detail", detailProfileType.value())
      .body(new ProfileAssociation()
        .withMasterProfileId(masterWrapper3.getId())
        .withDetailProfileId(detailWrapper.getId())
        .withMasterProfileType(ProfileType.valueOf(masterProfileType.value()))
        .withDetailProfileType(ProfileType.valueOf(detailProfileType.value()))
        .withOrder(7)
        .withTriggered(true))
      .when()
      .post(ASSOCIATED_PROFILES_URL)
      .then()
      .statusCode(is(HttpStatus.SC_CREATED))
      .and()
      .extract().body().as(ProfileAssociation.class);
    async.complete();
    //end

    //creates association 2
    async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterProfileType.value())
      .queryParam("detail", detailProfileType.value())
      .body(new ProfileAssociation()
        .withMasterProfileId(masterWrapper2.getId())
        .withDetailProfileId(detailWrapper.getId())
        .withMasterProfileType(ProfileType.valueOf(masterProfileType.value()))
        .withDetailProfileType(ProfileType.valueOf(detailProfileType.value()))
        .withOrder(7)
        .withTriggered(true))
      .when()
      .post(ASSOCIATED_PROFILES_URL)
      .then()
      .statusCode(is(HttpStatus.SC_CREATED))
      .and()
      .extract().body().as(ProfileAssociation.class);
    async.complete();
    //end

    //creates association 1
    async = testContext.async();
    RestAssured.given()
      .spec(spec)
      .queryParam("master", masterProfileType.value())
      .queryParam("detail", detailProfileType.value())
      .body(new ProfileAssociation()
        .withMasterProfileId(masterWrapper1.getId())
        .withDetailProfileId(detailWrapper.getId())
        .withMasterProfileType(ProfileType.valueOf(masterProfileType.value()))
        .withDetailProfileType(ProfileType.valueOf(detailProfileType.value()))
        .withOrder(7)
        .withTriggered(true))
      .when()
      .post(ASSOCIATED_PROFILES_URL)
      .then()
      .statusCode(is(HttpStatus.SC_CREATED))
      .and()
      .extract().body().as(ProfileAssociation.class);
    async.complete();
    //end

    RestAssured.given()
      .spec(spec)
      .queryParam("detailType", detailProfileType.value())
      .queryParam("masterType", masterProfileType.value())
      .queryParam("query", "description=\"test*\" or description==\"*description\" sortBy name")
      .when()
      .get(MASTERS_BY_DETAIL_URL, detailWrapper.getId())
      .then().statusCode(is(HttpStatus.SC_OK))
      .body("contentType", is(detailProfileType.value()))
      .body("childSnapshotWrappers.size()", is(2))
      .body("childSnapshotWrappers[0].content.name", is(masterWrapper1.getName()))
      .body("childSnapshotWrappers[1].content.name", is(masterWrapper2.getName()));

    clearTables(testContext);
  }

  private <T> ProfileWrapper<T> postProfile(TestContext testContext, ProfileWrapper<T> profileWrapper, String profileUrl) {
    Async async = testContext.async();
    T profile = RestAssured.given()
      .spec(spec)
      .body(profileWrapper.getProfile())
      .when()
      .post(profileUrl)
      .then().log().all()
      .statusCode(HttpStatus.SC_CREATED)
      .and()
      .extract().body().as(profileWrapper.getProfileType());
    async.complete();
    profileWrapper.setProfile(profile);
    return profileWrapper;
  }

  @Override
  public void clearTables(TestContext context) {
    Async async = context.async();
    deleteTable(ASSOCIATIONS_TABLE)
      .compose(e -> deleteTable(PROFILE_WRAPPERS))
      .compose(e -> deleteTable(ACTION_PROFILES_TABLE))
      .compose(e -> deleteTable(JOB_PROFILES_TABLE))
      .compose(e -> deleteTable(MAPPING_PROFILES_TABLE))
      .compose(e -> deleteTable(MATCH_PROFILES_TABLE))
      .onComplete(clearAr -> {
        if (clearAr.failed()) {
          context.fail(clearAr.cause());
        }
        async.complete();
      });
    async.awaitSuccess();
  }

  private Future<Void> deleteTable(String tableName) {
    Promise<Void> promise = Promise.promise();
    PostgresClient pgClient = PostgresClient.getInstance(vertx, TENANT_ID);
    pgClient.delete(tableName, new Criterion(), ar -> {
      if (ar.failed()) {
        promise.fail(ar.cause());
      } else {
        promise.complete();
      }
    });
    return promise.future();
  }
}
