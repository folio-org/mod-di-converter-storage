package org.folio.rest.impl;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.MappingDetail;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.util.EntityTypes;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.folio.rest.impl.MappingProfileTest.MAPPING_PROFILES_PATH;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.CREATE;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.MODIFY;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.UPDATE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.INSTANCE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MAPPING_PROFILE;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

@RunWith(VertxUnitRunner.class)
public class ActionProfileTest extends AbstractRestVerticleTest {

  static final String ACTION_PROFILES_TABLE_NAME = "action_profiles";
  static final String ACTION_PROFILES_PATH = "/data-import-profiles/actionProfiles";
  private static final String ENTITY_TYPES_PATH = " /data-import-profiles/entityTypes";
  private static final String JOB_PROFILES_TABLE_NAME = "job_profiles";
  static final String MAPPING_PROFILES_TABLE_NAME = "mapping_profiles";
  static final String MATCH_PROFILES_TABLE_NAME = "match_profiles";
  private static final String SNAPSHOTS_TABLE_NAME = "profile_snapshots";
  private static final String PROFILE_WRAPPERS_TABLE_NAME = "profile_wrappers";
  private static final String ASSOCIATIONS_TABLE_NAME = "profile_associations";
  private static final String ACTION_PROFILE_UUID = "16449d21-ad7c-4f69-b31e-a521fe4ae893";
  private static final String ASSOCIATED_PROFILES_PATH = "/data-import-profiles/profileAssociations";
  private final List<String> defaultActionProfileIds = Arrays.asList(
    "d0ebba8a-2f0f-11eb-adc1-0242ac120002", //OCLC_CREATE_INSTANCE_ACTION_PROFILE_ID
    "cddff0e1-233c-47ba-8be5-553c632709d9", //OCLC_UPDATE_INSTANCE_ACTION_PROFILE_ID
    "6aa8e98b-0d9f-41dd-b26f-15658d07eb52", //OCLC_UPDATE_MARC_BIB_ACTION_PROFILE_ID
    "f8e58651-f651-485d-aead-d2fa8700e2d1", //DEFAULT_CREATE_DERIVE_INSTANCE_ACTION_PROFILE_ID
    "f5feddba-f892-4fad-b702-e4e77f04f9a3", //DEFAULT_CREATE_DERIVE_HOLDINGS_ACTION_PROFILE_ID
    "8aa0b850-9182-4005-8435-340b704b2a19", //DEFAULT_CREATE_HOLDINGS_ACTION_PROFILE_ID
    "7915c72e-c6af-4962-969d-403c7238b051", //DEFAULT_CREATE_AUTHORITIES_ACTION_PROFILE_ID
    "fabd9a3e-33c3-49b7-864d-c5af830d9990"  //DEFAULT_DELETE_MARC_AUTHORITY_ACTION_PROFILE_ID
  );

  static ActionProfileUpdateDto actionProfile_1 = new ActionProfileUpdateDto()
    .withProfile(new ActionProfile().withName("Bla")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
      .withAction(CREATE)
      .withFolioRecord(INSTANCE));
  static ActionProfileUpdateDto actionProfile_2 = new ActionProfileUpdateDto()
    .withProfile(new ActionProfile().withName("Boo")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum")))
      .withAction(CREATE)
      .withFolioRecord(INSTANCE));
  static ActionProfileUpdateDto actionProfile_3 = new ActionProfileUpdateDto()
    .withProfile(new ActionProfile().withName("Foo")
      .withTags(new Tags().withTagList(singletonList("lorem")))
      .withAction(CREATE)
      .withFolioRecord(INSTANCE));
  static ActionProfileUpdateDto actionProfile_4 = new ActionProfileUpdateDto()
    .withProfile(new ActionProfile().withId(ACTION_PROFILE_UUID).withName("OLA")
      .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
      .withAction(UPDATE)
      .withFolioRecord(MARC_BIBLIOGRAPHIC));
  static ActionProfileUpdateDto actionProfileNotEmptyChildAndParent = new ActionProfileUpdateDto()
    .withProfile(new ActionProfile()
      .withName("Action profile with child and parent")
      .withAction(UPDATE)
      .withParentProfiles(List.of(new ProfileSnapshotWrapper().withId(UUID.randomUUID().toString())))
      .withChildProfiles(List.of(new ProfileSnapshotWrapper().withId(UUID.randomUUID().toString())))
      .withFolioRecord(MARC_BIBLIOGRAPHIC));

  @Test
  public void shouldReturnEmptyListOnGet() {
    RestAssured.given()
      .spec(spec)
      .when()
      .get(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(0))
      .body("actionProfiles", empty());
  }

  @Test
  public void shouldReturnAllProfilesOnGet() {
    createProfiles();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(ACTION_PROFILES_PATH + "?withRelations=true")
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(3))
      .body("actionProfiles*.deleted", everyItem(is(nullValue())))
      .body("actionProfiles*.hidden", everyItem(is(false)));
  }

  @Test
  public void shouldReturnCommittedProfilesOnGetWithQueryByLastName() {
    createProfiles();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(ACTION_PROFILES_PATH + "?query=userInfo.lastName=Doe")
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(3))
      .body("actionProfiles*.deleted", everyItem(is(nullValue())))
      .body("actionProfiles*.hidden", everyItem(is(false)))
      .body("actionProfiles*.userInfo.lastName", everyItem(is("Doe")));
  }

  @Test
  public void shouldReturnIpsumTaggedProfilesOnGetWithQueryByTag() {
    createProfiles();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(ACTION_PROFILES_PATH + "?query=tags.tagList=/respectCase/respectAccents \\\"ipsum\\\"")
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(2))
      .body("actionProfiles*.deleted", everyItem(is(nullValue())))
      .body("actionProfiles*.hidden", everyItem(is(false)))
      .body("actionProfiles.get(0).tags.tagList", hasItem("ipsum"))
      .body("actionProfiles.get(1).tags.tagList", hasItem("ipsum"));
  }

  @Test
  public void shouldReturnLimitedCollectionOnGetWithLimit() {
    createProfiles();
    RestAssured.given()
      .spec(spec)
      .when()
      .get(ACTION_PROFILES_PATH + "?limit=2")
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("actionProfiles.size()", is(2))
      .body("totalRecords", is(3));
  }

  @Test
  public void shouldReturnBadRequestOnPostWithMarcbibRecordAndCreateAction() {
    RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile()
          .withName("Invalid Action Profile")
          .withAction(CREATE)
          .withFolioRecord(MARC_BIBLIOGRAPHIC)))
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Can't create ActionProfile for MARC Bib record type with Create action"));
  }

  @Test
  public void shouldCreateProfileOnPost() {
    RestAssured.given()
      .spec(spec)
      .body(actionProfile_1)
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .body("profile.name", is(actionProfile_1.getProfile().getName()))
      .body("profile.tags.tagList", is(actionProfile_1.getProfile().getTags().getTagList()))
      .body("profile.userInfo.lastName", is("Doe"))
      .body("profile.userInfo.firstName", is("Jane"))
      .body("profile.userInfo.userName", is("@janedoe"));

    RestAssured.given()
      .spec(spec)
      .body(actionProfile_1)
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldCreateAndUpdateProfileNotOverridingDefaultsOnPost() {
    var testData = Arrays.stream(ActionProfile.FolioRecord.values())
      .collect(Collectors.toMap(folioRecord -> folioRecord,
        folioRecord -> !folioRecord.equals(INSTANCE) && !folioRecord.equals(MARC_BIBLIOGRAPHIC)));
    var errors = new LinkedList<String>();

    for (var testDataEntry : testData.entrySet()) {
      try {
        testCreateUpdateActionProfileNotOverridingDefaults(testDataEntry.getValue(), testDataEntry.getKey(),
          !testDataEntry.getValue());
      } catch (Throwable thr) {
        errors.add("Failed for record: " + testDataEntry.getKey() + ", flag value: "
          + testDataEntry.getValue() + ". Cause: " + thr.getMessage());
      }
    }

    if (!errors.isEmpty()) {
      throw new AssertionError("There were " + errors.size() + " test failures out of " + testData.size()
        + ": " + errors);
    }
  }

  @Test
  public void shouldCreateProfileWithGivenIdOnPost() {
    RestAssured.given()
      .spec(spec)
      .body(actionProfile_4)
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .body("profile.name", is(actionProfile_4.getProfile().getName()))
      .body("profile.tags.tagList", is(actionProfile_4.getProfile().getTags().getTagList()))
      .body("profile.userInfo.lastName", is("Doe"))
      .body("profile.userInfo.firstName", is("Jane"))
      .body("profile.userInfo.userName", is("@janedoe"));

    RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile().withId(ACTION_PROFILE_UUID).withName("GOA")
          .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
          .withAction(CREATE)
          .withFolioRecord(MARC_BIBLIOGRAPHIC)))
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Action profile with id 'GOA' already exists"));
  }

  @Test
  public void shouldReturnBadRequestOnPut() {
    RestAssured.given()
      .spec(spec)
      .body(new JsonObject().toString())
      .when()
      .put(ACTION_PROFILES_PATH + "/" + UUID.randomUUID())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldReturnBadRequestOnPutWithDefaultProfiles() {
    createProfiles();
    for (String id : defaultActionProfileIds) {
      RestAssured.given()
        .spec(spec)
        .body(actionProfile_1)
        .when()
        .put(ACTION_PROFILES_PATH + "/" + id)
        .then()
        .statusCode(HttpStatus.SC_BAD_REQUEST);
    }
  }

  @Test
  public void shouldReturnBadRequestOnPutWithMarcbibRecordAndCreateAction() {
    RestAssured.given()
      .spec(spec)
      .body(actionProfile_4)
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    RestAssured.given()
      .spec(spec)
      .body(actionProfile_4.withProfile(actionProfile_4.getProfile().withAction(CREATE)))
      .when()
      .put(ACTION_PROFILES_PATH + "/" + actionProfile_4.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Can't create ActionProfile for MARC Bib record type with Create action"));
  }

  @Test
  public void shouldReturnBadRequestOnDeleteWithDefaultProfiles() {
    createProfiles();
    for (String id : defaultActionProfileIds) {
      RestAssured.given()
        .spec(spec)
        .when()
        .delete(ACTION_PROFILES_PATH + "/" + id)
        .then()
        .statusCode(HttpStatus.SC_BAD_REQUEST);
    }
  }

  @Test
  public void shouldReturnNotFoundOnPut() {
    RestAssured.given()
      .spec(spec)
      .body(actionProfile_2)
      .when()
      .put(ACTION_PROFILES_PATH + "/" + UUID.randomUUID())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnUnprocessableEntityOnPutProfileWithExistingName() {
    createProfiles();

    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto().withProfile(new ActionProfile()
        .withName("newProfile")
        .withAction(CREATE)
        .withFolioRecord(INSTANCE)))
      .when()
      .post(ACTION_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    ActionProfileUpdateDto createdProfile = createResponse.body().as(ActionProfileUpdateDto.class);

    createdProfile.getProfile().setName(actionProfile_1.getProfile().getName());
    RestAssured.given()
      .spec(spec)
      .body(createdProfile)
      .when()
      .put(ACTION_PROFILES_PATH + "/" + createdProfile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @Test
  public void shouldUpdateProfileOnPut() {
    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(actionProfile_2)
      .when()
      .post(ACTION_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    ActionProfileUpdateDto actionProfile = createResponse.body().as(ActionProfileUpdateDto.class);

    actionProfile.getProfile().setDescription("test");
    RestAssured.given()
      .spec(spec)
      .body(actionProfile)
      .when()
      .put(ACTION_PROFILES_PATH + "/" + actionProfile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(actionProfile.getProfile().getId()))
      .body("description", is("test"))
      .body("name", is(actionProfile.getProfile().getName()))
      .body("tags.tagList", is(actionProfile.getProfile().getTags().getTagList()))
      .body("userInfo.lastName", is("Doe"))
      .body("userInfo.firstName", is("Jane"))
      .body("userInfo.userName", is("@janedoe"));
  }

  @Test
  public void shouldUpdateProfileAssociationsOnPut() {
    var actionProfileDto = postActionProfile(new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("Test Action Profile")
        .withAction(CREATE)
        .withFolioRecord(INSTANCE)));

    var mappingProfileDto = postMappingProfile(new MappingProfileUpdateDto()
      .withProfile(new MappingProfile()
        .withName("Test Mapping Profile with relations")
        .withExistingRecordType(EntityType.INSTANCE)
        .withIncomingRecordType(EntityType.INSTANCE))
      .withAddedRelations(List.of(new ProfileAssociation()
        .withMasterProfileType(ProfileType.ACTION_PROFILE)
        .withMasterProfileId(actionProfileDto.getId())
        .withDetailProfileType(ProfileType.MAPPING_PROFILE))));

    RestAssured.given()
      .spec(spec)
      .body(actionProfileDto
        .withDeletedRelations(List.of(new ProfileAssociation()
          .withMasterProfileType(ProfileType.ACTION_PROFILE)
          .withMasterProfileId(actionProfileDto.getProfile().getId())
          .withDetailProfileType(ProfileType.MAPPING_PROFILE)
          .withDetailProfileId(mappingProfileDto.getAddedRelations().get(0).getDetailProfileId()))))
      .when()
      .put(ACTION_PROFILES_PATH + "/" + actionProfileDto.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(actionProfileDto.getProfile().getId()))
      .body("name", is(actionProfileDto.getProfile().getName()));

    RestAssured.given()
      .spec(spec)
      .when()
      .get(ACTION_PROFILES_PATH + "/" + actionProfileDto.getProfile().getId() + "?withRelations=true")
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(actionProfileDto.getProfile().getId()))
      .body("name", is(actionProfileDto.getProfile().getName()))
      .body("userInfo.lastName", is("Doe"))
      .body("userInfo.firstName", is("Jane"))
      .body("userInfo.userName", is("@janedoe"))
      .body("parentProfiles", is(empty()))
      .body("childProfiles", is(empty()));
  }

  @Test
  public void shouldReuseExistingActionWrapperIdForCaseWhenTwoActionProfilesUseOneMappingProfile() {

    //action profile 1
    var actionProfileDto1 = postActionProfile(new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("Test Action Profile 1")
        .withAction(CREATE)
        .withFolioRecord(INSTANCE)));

    //action profile 2
    var actionProfileDto2 = postActionProfile(new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("Test Action Profile 2")
        .withAction(CREATE)
        .withFolioRecord(INSTANCE)));

    //creation associations 1
    ProfileAssociation profileAssociation1 = new ProfileAssociation()
      .withMasterProfileId(actionProfileDto1.getProfile().getId())
      .withOrder(1);

    //creation associations 2
    ProfileAssociation profileAssociation2 = new ProfileAssociation()
      .withMasterProfileId(actionProfileDto2.getProfile().getId())
      .withOrder(1);

    //creation mapping profile
    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(new MappingProfileUpdateDto()
        .withProfile(new MappingProfile().withName("Test Mapping Profile")
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withExistingRecordType(EntityType.INSTANCE)))
      .when()
      .post(MAPPING_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    MappingProfileUpdateDto associatedMappingProfile = createResponse.body().as(MappingProfileUpdateDto.class);

    //creation association1 actionProfile1 - mappingProfile
    postProfileAssociation(
        profileAssociation1
          .withMasterProfileId(actionProfileDto1.getProfile().getId())
          .withMasterProfileType(ProfileType.ACTION_PROFILE)
          .withDetailProfileId(associatedMappingProfile.getProfile().getId())
          .withDetailProfileType(ProfileType.MAPPING_PROFILE),
        ACTION_PROFILE, MAPPING_PROFILE);

    //creation association2 actionProfile2 - mappingProfile
    postProfileAssociation(
        profileAssociation2
          .withMasterProfileId(actionProfileDto2.getProfile().getId())
          .withMasterProfileType(ProfileType.ACTION_PROFILE)
          .withDetailProfileId(associatedMappingProfile.getProfile().getId())
          .withDetailProfileType(ProfileType.MAPPING_PROFILE),
        //when we want to reuse MAPPING_PROFILE_WRAPPER
        //.withDetailWrapperId(actionToMappingAssociation1.getDetailWrapperId()),
        ACTION_PROFILE, MAPPING_PROFILE);

    //unlinking actionProfile1 - mappingProfile
    RestAssured.given()
      .spec(spec)
      .body(actionProfileDto1
        .withDeletedRelations(List.of(new ProfileAssociation()
          .withMasterProfileType(ProfileType.ACTION_PROFILE)
          .withMasterProfileId(actionProfileDto1.getId())
          .withDetailProfileType(ProfileType.MAPPING_PROFILE)
          .withDetailProfileId(associatedMappingProfile.getId()))))
      .when()
      .put(ACTION_PROFILES_PATH + "/" + actionProfileDto1.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(actionProfileDto1.getId()))
      .body("name", is(actionProfileDto1.getProfile().getName()));

    //linking actionProfile1 - mappingProfile
    RestAssured.given()
      .spec(spec)
      .body(actionProfileDto1
        .withDeletedRelations(List.of())
        .withAddedRelations(List.of(new ProfileAssociation()
          .withMasterProfileType(ProfileType.ACTION_PROFILE)
          .withMasterProfileId(actionProfileDto1.getId())
          .withDetailProfileType(ProfileType.MAPPING_PROFILE)
          .withDetailProfileId(associatedMappingProfile.getId()))))
      .when()
      .put(ACTION_PROFILES_PATH + "/" + actionProfileDto1.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(actionProfileDto1.getId()))
      .body("name", is(actionProfileDto1.getProfile().getName()));
  }

  @Test
  public void shouldReuseExistingActionWrapperId() {
    var actionProfileDto = postActionProfile(new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("Test Action Profile")
        .withAction(CREATE)
        .withFolioRecord(INSTANCE)));

    var mappingProfileDto = postMappingProfile(new MappingProfileUpdateDto()
      .withProfile(new MappingProfile()
        .withName("Test Mapping Profile with relations")
        .withExistingRecordType(EntityType.INSTANCE)
        .withIncomingRecordType(EntityType.INSTANCE))
      .withAddedRelations(List.of(new ProfileAssociation()
        .withMasterProfileType(ProfileType.ACTION_PROFILE)
        .withMasterProfileId(actionProfileDto.getId())
        .withDetailProfileType(ProfileType.MAPPING_PROFILE))));

    String actionProfileId = actionProfileDto.getId();
    String mappingProfileId = mappingProfileDto.getId();

    RestAssured.given()
      .spec(spec)
      .body(actionProfileDto
        .withDeletedRelations(List.of(new ProfileAssociation()
          .withMasterProfileType(ProfileType.ACTION_PROFILE)
          .withMasterProfileId(actionProfileId)
          .withDetailProfileType(ProfileType.MAPPING_PROFILE)
          .withDetailProfileId(mappingProfileId))))
      .when()
      .put(ACTION_PROFILES_PATH + "/" + actionProfileDto.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(actionProfileId))
      .body("name", is(actionProfileDto.getProfile().getName()));

     RestAssured.given()
      .spec(spec)
      .body(actionProfileDto
        .withDeletedRelations(List.of())
        .withAddedRelations(List.of(new ProfileAssociation()
          .withMasterProfileType(ProfileType.ACTION_PROFILE)
          .withMasterProfileId(actionProfileId)
          .withDetailProfileType(ProfileType.MAPPING_PROFILE)
          .withDetailProfileId(mappingProfileId))))
      .when()
      .put(ACTION_PROFILES_PATH + "/" + actionProfileDto.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(actionProfileDto.getId()))
      .body("name", is(actionProfileDto.getProfile().getName()));

    // TODO need to check that existing actionProfileWrapperId is used after second linking
  }

  @Test
  public void shouldReturnNotFoundOnGetById() {
    RestAssured.given()
      .spec(spec)
      .when()
      .get(ACTION_PROFILES_PATH + "/" + UUID.randomUUID())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnProfileOnGetById() {
    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(actionProfile_3)
      .when()
      .post(ACTION_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    ActionProfileUpdateDto actionProfile = createResponse.body().as(ActionProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(ACTION_PROFILES_PATH + "/" + actionProfile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(actionProfile.getProfile().getId()))
      .body("name", is(actionProfile.getProfile().getName()))
      .body("tags.tagList", is(actionProfile.getProfile().getTags().getTagList()))
      .body("userInfo.lastName", is("Doe"))
      .body("userInfo.firstName", is("Jane"))
      .body("userInfo.userName", is("@janedoe"));
  }

  @Test
  public void shouldReturnNotFoundOnDelete() {
    RestAssured.given()
      .spec(spec)
      .when()
      .delete(ACTION_PROFILES_PATH + "/" + UUID.randomUUID())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnBadRequestOnDeleteProfileAssociatedWithOtherProfiles() {
    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(actionProfile_1)
      .when()
      .post(ACTION_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    ActionProfileUpdateDto profileToDelete = createResponse.body().as(ActionProfileUpdateDto.class);

    createResponse = RestAssured.given()
      .spec(spec)
      .body(actionProfile_2)
      .when()
      .post(ACTION_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    ActionProfileUpdateDto associatedActionProfile = createResponse.body().as(ActionProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .queryParam("master", ACTION_PROFILE.value())
      .queryParam("detail", ACTION_PROFILE.value())
      .body(new ProfileAssociation()
        .withMasterProfileId(associatedActionProfile.getProfile().getId())
        .withDetailProfileId(profileToDelete.getProfile().getId())
        .withMasterProfileType(ProfileType.ACTION_PROFILE)
        .withDetailProfileType(ProfileType.ACTION_PROFILE)
        .withOrder(1))
      .when()
      .post(ASSOCIATED_PROFILES_PATH)
      .then()
      .statusCode(is(HttpStatus.SC_CREATED));

    RestAssured.given()
      .spec(spec)
      .when()
      .delete(ACTION_PROFILES_PATH + "/" + profileToDelete.getProfile().getId())
      .then()
      .log().all()
      .statusCode(HttpStatus.SC_CONFLICT);
  }

  @Test
  public void shouldHardDeleteProfileOnDeletion() {
    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(actionProfile_2)
      .when()
      .post(ACTION_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    ActionProfileUpdateDto profile = createResponse.body().as(ActionProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .when()
      .delete(ACTION_PROFILES_PATH + "/" + profile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(ACTION_PROFILES_PATH + "/" + profile.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldDeleteAssociationsWithDetailProfilesOnDelete() {
    Response createResponse = RestAssured.given()
      .spec(spec)
      .body(actionProfile_1)
      .when()
      .post(ACTION_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    ActionProfileUpdateDto profileToDelete = createResponse.body().as(ActionProfileUpdateDto.class);

    // creation detail-profiles
    createResponse = RestAssured.given()
      .spec(spec)
      .body(actionProfile_2)
      .when()
      .post(ACTION_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    ActionProfileUpdateDto associatedActionProfile = createResponse.body().as(ActionProfileUpdateDto.class);

    createResponse = RestAssured.given()
      .spec(spec)
      .body(new MappingProfileUpdateDto().withProfile(new MappingProfile().withName("testMapping")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE)))
      .when()
      .post(MAPPING_PROFILES_PATH);
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    MappingProfileUpdateDto associatedMappingProfile = createResponse.body().as(MappingProfileUpdateDto.class);

    // creation associations
    ProfileAssociation profileAssociation = new ProfileAssociation()
      .withMasterProfileId(profileToDelete.getProfile().getId())
      .withOrder(1);

    ProfileAssociation actionToActionAssociation =
      postProfileAssociation(
        profileAssociation.withDetailProfileId(associatedActionProfile.getProfile().getId())
          .withMasterProfileId(profileToDelete.getProfile().getId())
          .withMasterProfileType(ProfileType.ACTION_PROFILE)
          .withDetailProfileType(ProfileType.ACTION_PROFILE),
        ACTION_PROFILE, ACTION_PROFILE);

    ProfileAssociation actionToMappingAssociation =
      postProfileAssociation(
        profileAssociation.withDetailProfileId(associatedMappingProfile.getProfile().getId())
          .withMasterProfileId(profileToDelete.getProfile().getId())
          .withMasterProfileType(ProfileType.ACTION_PROFILE)
          .withDetailProfileType(ProfileType.MAPPING_PROFILE),
        ACTION_PROFILE, MAPPING_PROFILE);

    // deleting action profile
    RestAssured.given()
      .spec(spec)
      .when()
      .delete(ACTION_PROFILES_PATH + "/" + profileToDelete.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    // receiving deleted associations
    RestAssured.given()
      .spec(spec)
      .queryParam("master", ACTION_PROFILE.value())
      .queryParam("detail", ACTION_PROFILE.value())
      .when()
      .get(ASSOCIATED_PROFILES_PATH + "/" + actionToActionAssociation.getId())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);

    RestAssured.given()
      .spec(spec)
      .queryParam("master", ACTION_PROFILE.value())
      .queryParam("detail", MAPPING_PROFILE.value())
      .when()
      .get(ASSOCIATED_PROFILES_PATH + "/" + actionToMappingAssociation.getId())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void shouldReturnOnlyUnmarkedAsDeletedProfilesOnGetWhenParameterDeletedIsNotPassed() {
    createProfiles();
    ActionProfileUpdateDto profileToDelete = RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto().withProfile(new ActionProfile()
        .withName("ProfileToDelete")
        .withAction(CREATE)
        .withFolioRecord(INSTANCE)))
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract().body().as(ActionProfileUpdateDto.class);

    RestAssured.given()
      .spec(spec)
      .when()
      .delete(ACTION_PROFILES_PATH + "/" + profileToDelete.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    RestAssured.given()
      .spec(spec)
      .when()
      .get(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(3))
      .body("actionProfiles*.deleted", everyItem(is(nullValue())))
      .body("actionProfiles*.hidden", everyItem(is(false)));
  }

  @Test
  public void shouldReturnAllEntityTypesOnGet() {
    List<String> entityTypesList = Arrays.stream(EntityTypes.values())
      .map(EntityTypes::getName)
      .collect(Collectors.toList());

    Response getResponse = RestAssured.given()
      .spec(spec)
      .when()
      .get(ENTITY_TYPES_PATH);

    getResponse
      .then()
      .log().all()
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(entityTypesList.size()))
      .body("entityTypes", containsInAnyOrder(entityTypesList.toArray()));
  }

  @Test
  public void shouldNotCreateActionProfilesWhenDifferentFolioRecord() {
    var mappingProfileUpdateDto = postMappingProfile(new MappingProfileUpdateDto()
      .withProfile(new MappingProfile()
        .withName("Test Mapping Profile")
        .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
        .withExistingRecordType(EntityType.ITEM)
        .withIncomingRecordType(EntityType.HOLDINGS)));

    RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile()
          .withName("Test Action Profile")
          .withAction(CREATE)
          .withFolioRecord(MARC_BIBLIOGRAPHIC))
        .withAddedRelations(List.of(
          new ProfileAssociation()
            .withDetailProfileType(ProfileType.MAPPING_PROFILE)
            .withDetailProfileId(mappingProfileUpdateDto.getId())
            .withMasterProfileType(ProfileType.ACTION_PROFILE))))
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Mapping profile 'Test Mapping Profile' can not be linked to this Action profile. ExistingRecordType and FolioRecord types are different")
        )));
  }

  @Test
  public void shouldNotCreateActionProfilesWhenDifferentActionType() {
    var mappingProfileUpdateDto = postMappingProfile(new MappingProfileUpdateDto()
      .withProfile(new MappingProfile()
        .withName("Test Mapping Profile")
        .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
        .withMappingDetails(new MappingDetail().withMarcMappingOption(MappingDetail.MarcMappingOption.UPDATE))
        .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)));

    RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile()
          .withName("Test Action Profile")
          .withAction(MODIFY)
          .withFolioRecord(MARC_BIBLIOGRAPHIC))
        .withAddedRelations(List.of(
          new ProfileAssociation()
            .withDetailProfileType(ProfileType.MAPPING_PROFILE)
            .withDetailProfileId(mappingProfileUpdateDto.getId())
            .withMasterProfileType(ProfileType.ACTION_PROFILE))))
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Unable to complete requested change. MARC Update Action profiles can only be linked with MARC Update " +
            "Mapping profiles and MARC Modify Action profiles can only be linked with MARC Modify Mapping profiles. Please ensure your Action and Mapping profiles are of like types and try again.")
        )));
  }

  @Test
  public void shouldNotUpdateActionProfilesWhenDifferentFolioRecord() {
    var mappingProfileUpdateDto = postMappingProfile(new MappingProfileUpdateDto()
      .withProfile(new MappingProfile()
        .withName("Test Mapping Profile")
        .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
        .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withIncomingRecordType(EntityType.HOLDINGS)));
    var mappingProfileUpdateDto1 = postMappingProfile(new MappingProfileUpdateDto()
      .withProfile(new MappingProfile()
        .withName("Test Mapping Profile1")
        .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
        .withExistingRecordType(EntityType.HOLDINGS)
        .withIncomingRecordType(EntityType.HOLDINGS)));

    var actionProfileUpdateDto = postActionProfile(new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("Test Action Profile")
        .withAction(UPDATE)
        .withFolioRecord(MARC_BIBLIOGRAPHIC))
      .withAddedRelations(List.of(
        new ProfileAssociation()
          .withDetailProfileType(ProfileType.MAPPING_PROFILE)
          .withDetailProfileId(mappingProfileUpdateDto.getProfile().getId())
          .withMasterProfileType(ProfileType.ACTION_PROFILE))));

    RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile()
          .withName("Test Action Profile")
          .withAction(UPDATE)
          .withFolioRecord(MARC_BIBLIOGRAPHIC))
        .withAddedRelations(List.of(
          new ProfileAssociation()
            .withDetailProfileType(ProfileType.MAPPING_PROFILE)
            .withDetailProfileId(mappingProfileUpdateDto1.getProfile().getId())
            .withMasterProfileType(ProfileType.ACTION_PROFILE)))
        .withDeletedRelations(List.of(
          new ProfileAssociation()
            .withDetailProfileType(ProfileType.MAPPING_PROFILE)
            .withDetailProfileId(mappingProfileUpdateDto.getProfile().getId())
            .withMasterProfileType(ProfileType.ACTION_PROFILE))))
      .when()
      .put(ACTION_PROFILES_PATH + "/" + actionProfileUpdateDto.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Mapping profile 'Test Mapping Profile1' can not be linked to this Action profile. ExistingRecordType and FolioRecord types are different")
        )));

    RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile()
          .withName("Test Action Profile")
          .withAction(CREATE)
          .withFolioRecord(INSTANCE)
          .withChildProfiles(List.of(new ProfileSnapshotWrapper()
            .withId(mappingProfileUpdateDto.getId())
            .withContent(mappingProfileUpdateDto)
            .withContentType(MAPPING_PROFILE)))))
      .when()
      .put(ACTION_PROFILES_PATH + "/" + actionProfileUpdateDto.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Can not update ActionProfile recordType and linked MappingProfile recordType are different")
        )));
  }

  @Test
  public void shouldNotUpdateActionProfilesWhenDifferentActionType() {
    var mappingProfileUpdateDto = postMappingProfile(new MappingProfileUpdateDto()
      .withProfile(new MappingProfile()
        .withName("Test Mapping Profile")
        .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
        .withMappingDetails(new MappingDetail().withMarcMappingOption(MappingDetail.MarcMappingOption.UPDATE))
        .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)));

    var actionProfileUpdateDto = postActionProfile(new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("Test Action Profile")
        .withAction(MODIFY)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)));

    RestAssured.given()
      .spec(spec)
      .body(new ActionProfileUpdateDto()
        .withProfile(new ActionProfile()
          .withName("Test Action Profile")
          .withAction(MODIFY)
          .withFolioRecord(MARC_BIBLIOGRAPHIC))
        .withAddedRelations(List.of(
          new ProfileAssociation()
            .withDetailProfileType(ProfileType.MAPPING_PROFILE)
            .withDetailProfileId(mappingProfileUpdateDto.getProfile().getId())
            .withMasterProfileType(ProfileType.ACTION_PROFILE))))
      .when()
      .put(ACTION_PROFILES_PATH + "/" + actionProfileUpdateDto.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Unable to complete requested change. MARC Update Action profiles can only be linked with MARC Update " +
            "Mapping profiles and MARC Modify Action profiles can only be linked with MARC Modify Mapping profiles. Please ensure your Action and Mapping profiles are of like types and try again.")
        )));
  }

  @Test
  public void shouldReturnBadRequestIfChildOrParentProfileIsNotEmptyOnPost() {
    RestAssured.given()
      .spec(spec)
      .body(actionProfileNotEmptyChildAndParent)
      .when()
      .post(ACTION_PROFILES_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Action profile read-only 'child' field should be empty"))
      .body("errors[1].message", is("Action profile read-only 'parent' field should be empty"));
  }

  @Test
  public void shouldReturnBadRequestIfChildOrParentProfileIsNotEmptyOnPut() {
    var actionProfileUpdateDto = postActionProfile(new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("Test Action Profile")
        .withAction(UPDATE)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)));


    RestAssured.given()
      .spec(spec)
      .body(actionProfileNotEmptyChildAndParent)
      .when()
      .put(ACTION_PROFILES_PATH + "/" + actionProfileUpdateDto.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Action profile read-only 'child' field should be empty"))
      .body("errors[1].message", is("Action profile read-only 'parent' field should be empty"));
  }

  private void testCreateUpdateActionProfileNotOverridingDefaults(Boolean incomingRemove9SubfieldFlag,
                                                                  ActionProfile.FolioRecord folioRecord,
                                                                  Boolean expectedRemove9SubfieldFlag) {
    var actionProfile = new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withName("test:" + folioRecord)
        .withAction(UPDATE)
        .withFolioRecord(folioRecord)
        .withRemove9Subfields(incomingRemove9SubfieldFlag));

    var createResponse = RestAssured.given()
      .spec(spec)
      .body(actionProfile)
      .when()
      .post(ACTION_PROFILES_PATH);
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    var actionProfileUpdate = createResponse.body().as(ActionProfileUpdateDto.class);
    assertEquals(expectedRemove9SubfieldFlag, actionProfileUpdate.getProfile().getRemove9Subfields());

    actionProfileUpdate.getProfile().setRemove9Subfields(incomingRemove9SubfieldFlag);
    RestAssured.given()
      .spec(spec)
      .body(actionProfileUpdate)
      .when()
      .put(ACTION_PROFILES_PATH + "/" + actionProfileUpdate.getProfile().getId())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body("remove9Subfields", is(expectedRemove9SubfieldFlag));
  }

  private void createProfiles() {
    List<ActionProfileUpdateDto> actionProfilesToPost = Arrays.asList(actionProfile_1, actionProfile_2, actionProfile_3);
    for (ActionProfileUpdateDto profile : actionProfilesToPost) {
      RestAssured.given()
        .spec(spec)
        .body(profile)
        .when()
        .post(ACTION_PROFILES_PATH)
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
    Assert.assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    return createResponse.body().as(ProfileAssociation.class);
  }

  @Override
  public void clearTables(TestContext context) {
    Async async = context.async();
    PostgresClient pgClient = PostgresClient.getInstance(vertx, TENANT_ID);
    pgClient.delete(ASSOCIATIONS_TABLE_NAME, new Criterion(), event1 ->
      pgClient.delete(SNAPSHOTS_TABLE_NAME, new Criterion(), event2 ->
        pgClient.delete(PROFILE_WRAPPERS_TABLE_NAME, new Criterion(), event3 ->
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
}
