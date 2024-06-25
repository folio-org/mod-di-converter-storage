package org.folio.rest.impl;

import com.google.common.collect.Lists;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.Field;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.MatchDetail;
import org.folio.rest.jaxrs.model.MatchExpression;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.MatchProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.Qualifier;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ReactToType;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.folio.rest.impl.ActionProfileTest.ACTION_PROFILES_PATH;
import static org.folio.rest.impl.ActionProfileTest.actionProfile_1;
import static org.folio.rest.impl.JobProfileTest.JOB_PROFILES_PATH;
import static org.folio.rest.impl.JobProfileTest.jobProfile_1;
import static org.folio.rest.impl.MappingProfileTest.MAPPING_PROFILES_PATH;
import static org.folio.rest.impl.MappingProfileTest.mappingProfile_1;
import static org.folio.rest.impl.MappingProfileTest.mappingProfile_2;
import static org.folio.rest.impl.MappingProfileTest.mappingProfile_3;
import static org.folio.rest.impl.association.CommonProfileAssociationTest.ACTION_PROFILES_TABLE;
import static org.folio.rest.impl.association.CommonProfileAssociationTest.ASSOCIATIONS_TABLE;
import static org.folio.rest.impl.association.CommonProfileAssociationTest.JOB_PROFILES_TABLE;
import static org.folio.rest.impl.association.CommonProfileAssociationTest.MAPPING_PROFILES_TABLE;
import static org.folio.rest.impl.association.CommonProfileAssociationTest.MATCH_PROFILES_TABLE;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.UPDATE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC;
import static org.folio.rest.jaxrs.model.MatchDetail.MatchCriterion.EXACTLY_MATCHES;
import static org.folio.rest.jaxrs.model.MatchExpression.DataValueType.VALUE_FROM_RECORD;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;
import static org.folio.rest.jaxrs.model.Qualifier.ComparisonPart.NUMERICS_ONLY;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@RunWith(VertxUnitRunner.class)
public class MatchProfileTest extends AbstractRestVerticleTest {
  static final String MATCH_PROFILES_PATH = "/data-import-profiles/matchProfiles";
  private static final String ASSOCIATED_PROFILES_PATH = "/data-import-profiles/profileAssociations";
  private static final String MATCH_PROFILE_UUID = "48a54656-8a2c-43c1-96b4-da96a70a0a62";

  private static final String PROFILE_WRAPPERS_TABLE = "profile_wrappers";

  private List<String> defaultMatchedProfileIds = Arrays.asList(
    "d27d71ce-8a1e-44c6-acea-96961b5592c6", //OCLC_MARC_MARC_MATCH_PROFILE_ID
    "31dbb554-0826-48ec-a0a4-3c55293d4dee", //OCLC_INSTANCE_UUID_MATCH_PROFILE_ID
    "4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6"  //DEFAULT_DELETE_MARC_AUTHORITY_MATCH_PROFILE_ID
  );

  private static MatchProfileUpdateDto matchProfile_1 = new MatchProfileUpdateDto()
    .withProfile(new MatchProfile().withName("Bla")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC));
  private static MatchProfileUpdateDto matchProfile_2 = new MatchProfileUpdateDto()
    .withProfile(new MatchProfile().withName("Boo")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum")))
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC));
  private static MatchProfileUpdateDto matchProfile_3 = new MatchProfileUpdateDto()
    .withProfile(new MatchProfile().withName("Foo")
      .withTags(new Tags().withTagList(Collections.singletonList("lorem")))
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC));
  private static MatchProfileUpdateDto matchProfile_4 = new MatchProfileUpdateDto()
    .withProfile(new MatchProfile().withId(MATCH_PROFILE_UUID).withName("OLA")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum")))
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC));

  private static MatchProfileUpdateDto matchProfileNotEmptyChildAndParent = new MatchProfileUpdateDto()
    .withProfile(new MatchProfile()
      .withName("Match profile with child and parent")
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withChildProfiles(List.of(new ProfileSnapshotWrapper().withId(UUID.randomUUID().toString())))
      .withParentProfiles(List.of(new ProfileSnapshotWrapper().withId(UUID.randomUUID().toString()))));

  @Test
  public void shouldReturnEmptyListOnGet() {
    RestAssured.given()
      .spec(spec)
      .when()
      .get(MATCH_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(0))
      .body("matchProfiles", empty());
  }

  @Test
  public void shouldReturnAllProfilesOnGet() {
    createProfiles();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(MATCH_PROFILES_PATH + "?withRelations=true")
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(3))
      .body("matchProfiles*.deleted", everyItem(is(false)))
      .body("matchProfiles*.hidden", everyItem(is(false)));
  }

  @Test
  public void shouldReturnAllProfilesOnGetTree(TestContext context) {
    clearTables(context);
    List<String> ids = createProfiles();
    createProfilesTree(ids);
    RestAssured.given()
      .spec(spec)
      .when()
      .get(MATCH_PROFILES_PATH + "?withRelations=true&query=id=" + ids.get(0))
      .then()
      .statusCode(HttpStatus.SC_OK).log().all()
      .body("totalRecords", is(1))
      .body("matchProfiles*.childProfiles*.id", everyItem(is(notNullValue())))
      .body("matchProfiles*.parentProfiles*.id", everyItem(is(notNullValue())))
      .body("matchProfiles*.deleted", everyItem(is(false)))
      .body("matchProfiles*.hidden", everyItem(is(false)));
  }

  @Test
  public void shouldReturnAllProfilesOnGetByIdTree() {
    List<String> ids = createProfiles();
    createProfilesTree(ids);
    RestAssured.given()
      .spec(spec)
      .when()
      .get(MATCH_PROFILES_PATH + "/" + ids.get(0) + "?withRelations=true")
      .then().log().all()
      .statusCode(HttpStatus.SC_OK).log().all()
      .body("childProfiles*.id", everyItem(is(notNullValue())))
      .body("parentProfiles*.id", everyItem(is(notNullValue())))
      .body("deleted", is(false));
  }

  @Test
  public void shouldReturnCommittedProfilesOnGetWithQueryByLastName(TestContext context) {
    clearTables(context);
    createProfiles();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(MATCH_PROFILES_PATH + "?query=userInfo.lastName=Doe")
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(3))
      .body("matchProfiles*.deleted", everyItem(is(false)))
      .body("matchProfiles*.hidden", everyItem(is(false)))
      .body("matchProfiles*.userInfo.lastName", everyItem(is("Doe")));
  }

  @Test
  public void shouldReturnIpsumTaggedProfilesOnGetWithQueryByTag() {
    createProfiles();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(MATCH_PROFILES_PATH + "?query=tags.tagList=/respectCase/respectAccents \\\"ipsum\\\"")
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(2))
      .body("matchProfiles.get(0).tags.tagList", hasItem("ipsum"))
      .body("matchProfiles.get(1).tags.tagList", hasItem("ipsum"));
  }

  @Test
  public void shouldReturnLimitedCollectionOnGetWithLimit(TestContext context) {
    clearTables(context);
    createProfiles();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(MATCH_PROFILES_PATH + "?limit=2")
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("matchProfiles.size()", is(2))
      .body("totalRecords", is(3));
  }

  @Test
  public void shouldReturnBadRequestOnPost(TestContext context) {
    clearTables(context);
    createProfiles();
    RestAssured.given()
      .spec(spec)
      .body(new JsonObject().toString())
      .when()
      .post(MATCH_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldReturnBadRequestOnPutWithDefaultProfiles() {
    createProfiles();
    for (String id : defaultMatchedProfileIds) {
      RestAssured.given()
        .spec(spec)
        .body(matchProfile_1)
        .when()
        .put(MATCH_PROFILES_PATH + "/" + id)
        .then()
        .statusCode(HttpStatus.SC_BAD_REQUEST);
    }
  }

  @Test
  public void shouldReturnBadRequestOnDeleteWithDefaultProfiles() {
    createProfiles();
    for (String id : defaultMatchedProfileIds) {
      RestAssured.given()
        .spec(spec)
        .when()
        .delete(MATCH_PROFILES_PATH + "/" + id)
        .then()
        .statusCode(HttpStatus.SC_BAD_REQUEST);
    }
  }

  @Test
  public void shouldCreateProfileOnPost() {
    RestAssured.given()
      .spec(spec)
      .body(matchProfile_1)
      .when()
      .post(MATCH_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .body("profile.name", is(matchProfile_1.getProfile().getName()))
      .body("profile.tags.tagList", is(matchProfile_1.getProfile().getTags().getTagList()))
      .body("profile.userInfo.lastName", is("Doe"))
      .body("profile.userInfo.firstName", is("Jane"))
      .body("profile.userInfo.userName", is("@janedoe"));

    RestAssured.given()
      .spec(spec)
      .body(matchProfile_1)
      .when()
      .post(MATCH_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldCreateProfileWithGivenIdOnPost() {
    RestAssured.given()
      .spec(spec)
      .body(matchProfile_4)
      .when()
      .post(MATCH_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .body("profile.name", is(matchProfile_4.getProfile().getName()))
      .body("profile.tags.tagList", is(matchProfile_4.getProfile().getTags().getTagList()))
      .body("profile.userInfo.lastName", is("Doe"))
      .body("profile.userInfo.firstName", is("Jane"))
      .body("profile.userInfo.userName", is("@janedoe"));

    RestAssured.given()
      .spec(spec)
      .body(new MatchProfileUpdateDto()
        .withProfile(new MatchProfile().withId(MATCH_PROFILE_UUID).withName("GOA")
          .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum")))
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)))
      .when()
      .post(MATCH_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Match profile with id 'GOA' already exists"));
  }

  @Test
  public void shouldReturnBadRequestOnPut() {
    RestAssured.given()
      .spec(spec)
      .body(new JsonObject().toString())
      .when()
      .put(MATCH_PROFILES_PATH + "/" + UUID.randomUUID())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldReturnNotFoundOnPut() {
    RestAssured.given()
      .spec(spec)
      .body(matchProfile_2)
      .when()
      .put(MATCH_PROFILES_PATH + "/" + UUID.randomUUID())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnUnprocessableEntityOnPutProfileWithExistingName(TestContext context) {
    clearTables(context);
    createProfiles();

    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(new MatchProfileUpdateDto().withProfile(new MatchProfile()
        .withName("newProfile")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)))
      .when()
      .post(MATCH_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    MatchProfileUpdateDto createdProfile = createResponse.body().as(MatchProfileUpdateDto.class);

    createdProfile.getProfile().setName(matchProfile_1.getProfile().getName());
    RestAssured.given()
      .spec(spec)
      .body(createdProfile)
      .when()
      .put(MATCH_PROFILES_PATH + "/" + createdProfile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldUpdateProfileOnPut(TestContext context) {
    clearTables(context);
    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(matchProfile_2)
      .when()
      .post(MATCH_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    MatchProfileUpdateDto matchProfile = createResponse.body().as(MatchProfileUpdateDto.class);

    matchProfile.getProfile().setDescription("test");
    RestAssured.given()
      .spec(spec)
      .body(matchProfile)
      .when()
      .put(MATCH_PROFILES_PATH + "/" + matchProfile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(matchProfile.getProfile().getId()))
      .body("name", is(matchProfile.getProfile().getName()))
      .body("description", is("test"))
      .body("tags.tagList", is(matchProfile.getProfile().getTags().getTagList()))
      .body("userInfo.lastName", is("Doe"))
      .body("userInfo.firstName", is("Jane"))
      .body("userInfo.userName", is("@janedoe"));
  }

  @Test
  public void shouldReturnNotFoundOnGetById() {
    RestAssured.given()
      .spec(spec)
      .when()
      .get(MATCH_PROFILES_PATH + "/" + UUID.randomUUID())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnProfileOnGetById() {
    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(matchProfile_3)
      .when()
      .post(MATCH_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    MatchProfileUpdateDto matchProfile = createResponse.body().as(MatchProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(MATCH_PROFILES_PATH + "/" + matchProfile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(matchProfile.getProfile().getId()))
      .body("name", is(matchProfile.getProfile().getName()))
      .body("tags.tagList", is(matchProfile.getProfile().getTags().getTagList()))
      .body("userInfo.lastName", is(matchProfile.getProfile().getUserInfo().getLastName()))
      .body("userInfo.firstName", is(matchProfile.getProfile().getUserInfo().getFirstName()))
      .body("userInfo.userName", is(matchProfile.getProfile().getUserInfo().getUserName()));
  }

  @Test
  public void shouldReturnNotFoundOnDelete() {
    RestAssured.given()
      .spec(spec)
      .when()
      .delete(MATCH_PROFILES_PATH + "/" + UUID.randomUUID())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnBadRequestOnDeleteProfileAssociatedWithOtherProfiles() {
    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(matchProfile_1)
      .when()
      .post(MATCH_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    MatchProfileUpdateDto profileToDelete = createResponse.body().as(MatchProfileUpdateDto.class);

    createResponse = RestAssured.given()
      .spec(spec)
      .body(matchProfile_2)
      .when()
      .post(MATCH_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    MatchProfileUpdateDto matchProfile = createResponse.body().as(MatchProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .queryParam("master", MATCH_PROFILE.value())
      .queryParam("detail", MATCH_PROFILE.value())
      .body(new ProfileAssociation()
        .withMasterProfileId(matchProfile.getProfile().getId())
        .withDetailProfileId(profileToDelete.getProfile().getId())
        .withMasterProfileType(ProfileType.MATCH_PROFILE)
        .withDetailProfileType(ProfileType.MATCH_PROFILE)
        .withOrder(1))
      .when()
      .post(ASSOCIATED_PROFILES_PATH)
      .then()
      .statusCode(is(HttpStatus.SC_CREATED));

    RestAssured.given()
      .spec(spec)
      .when()
      .delete(MATCH_PROFILES_PATH + "/" + profileToDelete.getProfile().getId())
      .then()
      .log().all()
      .statusCode(HttpStatus.SC_CONFLICT);
  }

  @Test
  public void shouldMarkAsDeletedProfileOnDelete() {
    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(matchProfile_2)
      .when()
      .post(MATCH_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    MatchProfileUpdateDto profile = createResponse.body().as(MatchProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .when()
      .delete(MATCH_PROFILES_PATH + "/" + profile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(MATCH_PROFILES_PATH + "/" + profile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(MATCH_PROFILES_PATH + "?showDeleted=true")
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(1))
      .body("matchProfiles.get(0).deleted", is(true));
  }

  @Test
  public void shouldDeleteAssociationsWithDetailProfilesOnDelete() {
    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(matchProfile_1)
      .when()
      .post(MATCH_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    MatchProfileUpdateDto profileToDelete = createResponse.body().as(MatchProfileUpdateDto.class);

    // creation detail-profiles
    createResponse = RestAssured.given()
      .spec(spec)
      .body(matchProfile_2)
      .when()
      .post(MATCH_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    MatchProfileUpdateDto associatedMatchProfile = createResponse.body().as(MatchProfileUpdateDto.class);

    createResponse = RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto().withProfile(new ActionProfile()
        .withName("testAction")
        .withAction(UPDATE)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)))
      .when()
      .post(ACTION_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    ActionProfileUpdateDto associatedActionProfile = createResponse.body().as(ActionProfileUpdateDto.class);

    // creation associations
    ProfileAssociation profileAssociation = new ProfileAssociation()
      .withMasterProfileId(profileToDelete.getProfile().getId())
      .withOrder(1);

    ProfileAssociation matchToMatchAssociation =
      postProfileAssociation(
        profileAssociation.withDetailProfileId(associatedMatchProfile.getProfile().getId())
          .withMasterProfileId(profileToDelete.getProfile().getId())
          .withMasterProfileType(ProfileType.MATCH_PROFILE)
          .withDetailProfileType(ProfileType.MATCH_PROFILE),
      MATCH_PROFILE, MATCH_PROFILE);

    ProfileAssociation matchToActionAssociation =
      postProfileAssociation(
        profileAssociation.withDetailProfileId(associatedActionProfile.getProfile().getId())
          .withMasterProfileId(profileToDelete.getProfile().getId())
          .withMasterProfileType(ProfileType.MATCH_PROFILE)
          .withDetailProfileType(ProfileType.ACTION_PROFILE),
      MATCH_PROFILE, ACTION_PROFILE);

    // deleting match profile
    RestAssured.given()
      .spec(spec)
      .when()
      .delete(MATCH_PROFILES_PATH + "/" + profileToDelete.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    // receiving deleted associations
    RestAssured.given()
      .spec(spec)
      .queryParam("master", MATCH_PROFILE.value())
      .queryParam("detail", MATCH_PROFILE.value())
      .when()
      .get(ASSOCIATED_PROFILES_PATH + "/" + matchToMatchAssociation.getId())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);

    RestAssured.given()
      .spec(spec)
      .queryParam("master", MATCH_PROFILE.value())
      .queryParam("detail", ACTION_PROFILE.value())
      .when()
      .get(ASSOCIATED_PROFILES_PATH + "/" + matchToActionAssociation.getId())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnMarkedAndUnmarkedAsDeletedProfilesOnGetWhenParameterDeletedIsTrue(TestContext context) {
    clearTables(context);
    createProfiles();
    MatchProfileUpdateDto matchProfileToDelete = RestAssured.given()
      .spec(spec)
      .body(new MatchProfileUpdateDto().withProfile(new MatchProfile()
        .withName("ProfileToDelete")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)))
      .when()
      .post(MATCH_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().body().as(MatchProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .when()
      .delete(MATCH_PROFILES_PATH + "/" + matchProfileToDelete.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    RestAssured.given()
      .spec(spec)
      .when()
      .param("showDeleted", true)
      .get(MATCH_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(4));
  }

  @Test
  public void shouldReturnOnlyUnmarkedAsDeletedProfilesOnGetWhenParameterDeletedIsNotPassed(TestContext context) {
    clearTables(context);
    createProfiles();
    MatchProfileUpdateDto matchProfileToDelete = RestAssured.given()
      .spec(spec)
      .body(new MatchProfileUpdateDto().withProfile(new MatchProfile()
        .withName("ProfileToDelete")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)))
      .when()
      .post(MATCH_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().body().as(MatchProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .when()
      .delete(MATCH_PROFILES_PATH + "/" + matchProfileToDelete.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(MATCH_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(3))
      .body("matchProfiles*.deleted", everyItem(is(false)))
      .body("matchProfiles*.hidden", everyItem(is(false)));
  }

  @Test
  public void shouldCreateProfileWithMatchDetailsOnPost() {
    MatchDetail matchDetail = new MatchDetail()
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withIncomingMatchExpression(new MatchExpression()
        .withDataValueType(VALUE_FROM_RECORD)
        .withFields(Arrays.asList(
          new Field().withLabel("field").withValue("001"),
          new Field().withLabel("indicator1").withValue(StringUtils.EMPTY),
          new Field().withLabel("indicator2").withValue(StringUtils.EMPTY),
          new Field().withLabel("recordSubfield").withValue(StringUtils.EMPTY)))
        .withQualifier(new Qualifier().withComparisonPart(NUMERICS_ONLY)))
      .withMatchCriterion(EXACTLY_MATCHES)
      .withExistingMatchExpression(new MatchExpression()
        .withDataValueType(VALUE_FROM_RECORD)
        .withFields(Collections.singletonList(
          new Field().withLabel("field").withValue("INSTANCE_HRID")))
        .withQualifier(new Qualifier().withComparisonPart(NUMERICS_ONLY)));

    MatchProfile matchProfile = new MatchProfile()
      .withName("Bla")
      .withTags(new Tags().withTagList(Collections.singletonList("hrid")))
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withMatchDetails(Collections.singletonList(matchDetail));

    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(new MatchProfileUpdateDto().withProfile(matchProfile))
      .when()
      .post(MATCH_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    MatchProfileUpdateDto createdMatchProfile = createResponse.body().as(MatchProfileUpdateDto.class);

    Response getResponse = RestAssured.given()
      .spec(spec)
      .when()
      .get(MATCH_PROFILES_PATH + "/" + createdMatchProfile.getProfile().getId());
    Assert.assertEquals(HttpStatus.SC_OK, getResponse.statusCode());
    MatchProfile receivedMatchProfile = getResponse.body().as(MatchProfile.class);

    // assert id and name
    Assert.assertEquals(receivedMatchProfile.getId(), createdMatchProfile.getProfile().getId());
    Assert.assertEquals(receivedMatchProfile.getName(), createdMatchProfile.getProfile().getName());

    // assert matchDetail
    Assert.assertEquals(1, receivedMatchProfile.getMatchDetails().size());
MatchDetail receivedMatchDetail1 = receivedMatchProfile.getMatchDetails().get(0);
    Assert.assertEquals(receivedMatchDetail1.getIncomingRecordType(), matchDetail.getIncomingRecordType());
    Assert.assertEquals(receivedMatchDetail1.getExistingRecordType(), matchDetail.getExistingRecordType());

// assert incomingMatchExpression
    Assert.assertEquals(receivedMatchDetail1.getIncomingMatchExpression().getDataValueType(),
      matchDetail.getIncomingMatchExpression().getDataValueType());

    Assert.assertEquals(receivedMatchDetail1.getIncomingMatchExpression().getQualifier().getComparisonPart(),
      matchDetail.getIncomingMatchExpression().getQualifier().getComparisonPart());

    // assert incoming fields
    Assert.assertEquals(4, receivedMatchDetail1.getIncomingMatchExpression().getFields().size());
    List<Field> createdIncomingFields = receivedMatchDetail1.getIncomingMatchExpression().getFields();
    for (int i = 0; i < createdIncomingFields.size(); i++) {
      Assert.assertEquals(createdIncomingFields.get(i).getLabel(),
        matchDetail.getIncomingMatchExpression().getFields().get(i).getLabel());
      Assert.assertEquals(createdIncomingFields.get(i).getValue(),
        matchDetail.getIncomingMatchExpression().getFields().get(i).getValue());
    }

    // assert matchCriterion
    Assert.assertEquals(receivedMatchDetail1.getMatchCriterion(), matchDetail.getMatchCriterion());

// assert existingMatchExpression
    Assert.assertEquals(receivedMatchDetail1.getExistingMatchExpression().getDataValueType(),
      matchDetail.getExistingMatchExpression().getDataValueType());
    Assert.assertEquals(1, receivedMatchDetail1.getExistingMatchExpression().getFields().size());
    Assert.assertEquals(receivedMatchDetail1.getExistingMatchExpression().getFields().get(0).getLabel(),
      matchDetail.getExistingMatchExpression().getFields().get(0).getLabel());
    Assert.assertEquals(receivedMatchDetail1.getExistingMatchExpression().getFields().get(0).getValue(),
      matchDetail.getExistingMatchExpression().getFields().get(0).getValue());
    Assert.assertEquals(receivedMatchDetail1.getExistingMatchExpression().getQualifier().getComparisonPart(),
      matchDetail.getExistingMatchExpression().getQualifier().getComparisonPart());
  }

  @Test
  public void shouldReturnBadRequestWhenChildOrParentIsNotEmptyOnPost(TestContext context) {
    RestAssured.given()
      .spec(spec)
      .body(matchProfileNotEmptyChildAndParent)
      .when()
      .post(MATCH_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Match profile read-only 'child' field should be empty"))
      .body("errors[1].message", is("Match profile read-only 'parent' field should be empty"));
  }

  @Test
  public void shouldReturnBadRequestWhenChildOrParentIsNotEmptyOnPut(TestContext context) {
    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(matchProfile_2)
      .when()
      .post(MATCH_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    MatchProfileUpdateDto matchProfile = createResponse.body().as(MatchProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .body(matchProfileNotEmptyChildAndParent)
      .when()
      .put(MATCH_PROFILES_PATH + "/" + matchProfile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Match profile read-only 'child' field should be empty"))
      .body("errors[1].message", is("Match profile read-only 'parent' field should be empty"));
  }

  private List<String> createProfiles() {
    List<MatchProfileUpdateDto> matchProfilesToPost = Arrays.asList(matchProfile_1, matchProfile_2, matchProfile_3);
    List<String> ids = new ArrayList<>();
    for (MatchProfileUpdateDto profile : matchProfilesToPost) {
      ids.add(RestAssured.given()
        .spec(spec)
        .body(profile)
        .when()
        .post(MATCH_PROFILES_PATH)
        .then()
        .statusCode(HttpStatus.SC_CREATED).extract().body().as(MatchProfileUpdateDto.class).getProfile().getId());
    }
    return ids;
  }

  private void createProfilesTree(List<String> profilesIds) {
    String nameForProfiles = "tree";
    List<JobProfileUpdateDto> jobProfiles = Arrays.asList(jobProfile_1, jobProfile_1, jobProfile_1);
    List<ActionProfileUpdateDto> actionProfiles = Arrays.asList(actionProfile_1, actionProfile_1, actionProfile_1);
    List<MappingProfileUpdateDto> mappingProfiles = Arrays.asList(mappingProfile_1, mappingProfile_2, mappingProfile_3);
    List<JobProfileUpdateDto> created = new ArrayList<>();
    List<MappingProfileUpdateDto> createdMappings = new ArrayList<>();
    List<ActionProfileUpdateDto> createdActions = new ArrayList<>();
    int i;
    i = 0;
    for (MappingProfileUpdateDto profile : mappingProfiles) {
      createdMappings.add(RestAssured.given()
        .spec(spec)
        .body(new MappingProfileUpdateDto()
          .withProfile(profile.getProfile().withName(nameForProfiles + i)))
        .when()
        .post(MAPPING_PROFILES_PATH)
        .then()
        .statusCode(HttpStatus.SC_CREATED).extract().body().as(MappingProfileUpdateDto.class));
      i++;
    }

    i = 0;
    for (JobProfileUpdateDto profile : jobProfiles) {
      created.add(RestAssured.given()
        .spec(spec)
        .body(new JobProfileUpdateDto()
          .withProfile(profile.getProfile().withName(nameForProfiles + i))
          .withAddedRelations(Collections.singletonList(new ProfileAssociation()
            .withDetailProfileId(profilesIds.get(i))
            .withDetailProfileType(ProfileType.MATCH_PROFILE)
            .withMasterProfileType(ProfileType.JOB_PROFILE)
            .withOrder(0)
            .withTriggered(false).withReactTo(ReactToType.MATCH)
          )))
        .when()
        .post(JOB_PROFILES_PATH)
        .then()
        .statusCode(HttpStatus.SC_CREATED).extract().body().as(JobProfileUpdateDto.class));
      i++;
    }
    i = 0;
    for (JobProfileUpdateDto profile : created) {
      profile.setDeletedRelations(Collections.singletonList(new ProfileAssociation()
        .withDetailProfileId(profilesIds.get(i))
        .withMasterProfileId(profile.getProfile().getId())
        .withDetailProfileType(ProfileType.MATCH_PROFILE)
        .withMasterProfileType(ProfileType.JOB_PROFILE)
        .withOrder(0)
        .withTriggered(false).withReactTo(ReactToType.MATCH)));
      profile.getAddedRelations().clear();
      RestAssured.given()
        .spec(spec)
        .body(profile)
        .when().log().all()
        .put(JOB_PROFILES_PATH + "/" + profile.getProfile().getId())
        .then()
        .statusCode(HttpStatus.SC_OK);
      i++;
    }
    i = 0;
    for (JobProfileUpdateDto profile : created) {
      profile.setAddedRelations(Collections.singletonList(new ProfileAssociation()
        .withDetailProfileId(profilesIds.get(i))
        .withMasterProfileId(profile.getProfile().getId())
        .withDetailProfileType(ProfileType.MATCH_PROFILE)
        .withMasterProfileType(ProfileType.JOB_PROFILE)
        .withOrder(0)
        .withTriggered(false).withReactTo(ReactToType.MATCH)));
      profile.setDeletedRelations(Collections.emptyList());
      RestAssured.given()
        .spec(spec)
        .body(profile)
        .when().log().all()
        .put(JOB_PROFILES_PATH + "/" + profile.getProfile().getId())
        .then()
        .statusCode(HttpStatus.SC_OK);
      i++;
    }
    i = 0;
    for (ActionProfileUpdateDto action : actionProfiles) {
      createdActions.add(RestAssured.given()
        .spec(spec)
        .body(new ActionProfileUpdateDto()
          .withProfile(action.getProfile()
            .withName(nameForProfiles + i))
          .withAddedRelations(Lists.newArrayList(new ProfileAssociation()
              .withMasterProfileId(profilesIds.get(i))
              .withDetailProfileType(ProfileType.ACTION_PROFILE)
              .withMasterProfileType(ProfileType.MATCH_PROFILE)
              .withOrder(0)
              .withTriggered(false)
              .withReactTo(ReactToType.MATCH),
            new ProfileAssociation()
              .withMasterProfileId(action.getProfile().getId())
              .withDetailProfileId(createdMappings.get(i).getId())
              .withDetailProfileType(ProfileType.MAPPING_PROFILE)
              .withMasterProfileType(ProfileType.ACTION_PROFILE)
              .withOrder(0)
              .withTriggered(false))))
        .when()
        .post(ACTION_PROFILES_PATH)
        .then()
        .statusCode(HttpStatus.SC_CREATED).extract().body().as(ActionProfileUpdateDto.class));
      i++;
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
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    return createResponse.body().as(ProfileAssociation.class);
  }

  @Override
  public void clearTables(TestContext context) {
    Async async = context.async();
    deleteTable(ASSOCIATIONS_TABLE)
      .compose(e -> deleteTable(PROFILE_WRAPPERS_TABLE))
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
