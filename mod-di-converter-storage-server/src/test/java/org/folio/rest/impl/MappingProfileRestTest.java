package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.CREATE;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.MODIFY;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.UPDATE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.INSTANCE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MAPPING_PROFILE;
import static org.folio.support.ProfileFixtures.MAPPING_PROFILE_1;
import static org.folio.support.ProfileFixtures.MAPPING_PROFILE_2;
import static org.folio.support.ProfileFixtures.MAPPING_PROFILE_3;
import static org.folio.support.ProfileFixtures.MAPPING_PROFILE_4;
import static org.folio.support.ProfileFixtures.MAPPING_PROFILE_5;
import static org.folio.support.ProfileFixtures.MAPPING_PROFILE_NOT_EMPTY_CHILD_AND_PARENT;
import static org.folio.support.ProfileFixtures.MAPPING_PROFILE_UUID;
import static org.folio.support.ProfileFixtures.MAPPING_PROFILE_WITH_EMPTY_SUBFIELDS_AND_DELETE_EXISTING_ACTION;
import static org.folio.support.ProfileFixtures.MAPPING_PROFILE_WITH_EMPTY_SUBFIELDS_AND_EMPTY_ACTION;
import static org.folio.support.ProfileFixtures.MAPPING_PROFILE_WITH_EMPTY_SUBFIELDS_AND_NOT_DELETE_EXISTING_ACTION;
import static org.folio.support.TestUtil.ACTION_PROFILES_PATH;
import static org.folio.support.TestUtil.ASSOCIATED_PROFILES_PATH;
import static org.folio.support.TestUtil.MAPPING_PROFILES_PATH;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.vertx.core.json.JsonObject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.MappingDetail;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ReactToType;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.support.AbstractRestTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MappingProfileRestTest extends AbstractRestTest {

  @DisplayName("should return empty list when no profiles exist")
  @Test
  void shouldReturnEmptyListOnGet() {
    getRequest(MAPPING_PROFILES_PATH)
      .statusCode(SC_OK)
      .body("totalRecords", is(0))
      .body("mappingProfiles", empty());
  }

  @DisplayName("should return all profiles on GET")
  @Test
  void shouldReturnAllProfilesOnGet() {
    createProfiles();
    getRequest(MAPPING_PROFILES_PATH + "?withRelations=true")
      .statusCode(SC_OK)
      .body("totalRecords", is(3))
      .body("mappingProfiles*.hidden", everyItem(is(false)));
  }

  @DisplayName("should return profiles matching lastName query")
  @Test
  void shouldReturnCommittedProfilesOnGetWithQueryByLastName() {
    createProfiles();
    getRequest(MAPPING_PROFILES_PATH + "?query=userInfo.lastName=Doe")
      .statusCode(SC_OK)
      .body("totalRecords", is(3))
      .body("mappingProfiles*.userInfo.lastName", everyItem(is("Doe")));
  }

  @DisplayName("should return profiles with 'ipsum' tag when queried by tag")
  @Test
  void shouldReturnIpsumTaggedProfilesOnGetWithQueryByTag() {
    createProfiles();
    getRequest(MAPPING_PROFILES_PATH + "?query=tags.tagList=/respectCase/respectAccents \\\"ipsum\\\"")
      .statusCode(SC_OK)
      .body("totalRecords", is(2))
      .body("mappingProfiles.get(0).tags.tagList", hasItem("ipsum"))
      .body("mappingProfiles.get(1).tags.tagList", hasItem("ipsum"));
  }

  @DisplayName("should return limited collection when limit parameter is provided")
  @Test
  void shouldReturnLimitedCollectionOnGetWithLimit() {
    createProfiles();
    getRequest(MAPPING_PROFILES_PATH + "?limit=2")
      .statusCode(SC_OK)
      .body("mappingProfiles.size()", is(2))
      .body("totalRecords", is(3));
  }

  @DisplayName("should return 422 when POST body is invalid")
  @Test
  void shouldReturnBadRequestOnPost() {
    createProfiles();
    postRequest(MAPPING_PROFILES_PATH, new JsonObject().toString())
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should create profile on POST")
  @Test
  void shouldCreateProfileOnPost() {
    postRequest(MAPPING_PROFILES_PATH, MAPPING_PROFILE_1)
      .statusCode(SC_CREATED)
      .body("profile.name", is(MAPPING_PROFILE_1.getProfile().getName()))
      .body("profile.tags.tagList", is(MAPPING_PROFILE_1.getProfile().getTags().getTagList()))
      .body("profile.userInfo.lastName", is("Doe"))
      .body("profile.userInfo.firstName", is("Jane"))
      .body("profile.userInfo.userName", is("@janedoe"));

    postRequest(MAPPING_PROFILES_PATH, MAPPING_PROFILE_1)
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should create profile with given ID on POST")
  @Test
  void shouldCreateProfileWithGivenIdOnPost() {
    postRequest(MAPPING_PROFILES_PATH, MAPPING_PROFILE_4)
      .statusCode(SC_CREATED)
      .body("profile.name", is(MAPPING_PROFILE_4.getProfile().getName()))
      .body("profile.tags.tagList", is(MAPPING_PROFILE_4.getProfile().getTags().getTagList()))
      .body("profile.userInfo.lastName", is("Doe"))
      .body("profile.userInfo.firstName", is("Jane"))
      .body("profile.userInfo.userName", is("@janedoe"));

    postRequest(MAPPING_PROFILES_PATH, new MappingProfileUpdateDto()
      .withProfile(new MappingProfile().withId(MAPPING_PROFILE_UUID).withName("OLA")
        .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum")))
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE)))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("The field mapping profile with id 'OLA' already exists"));
  }

  @DisplayName("should create profile on POST without repeatable subfields and DELETE_EXISTING action")
  @Test
  void shouldCreateProfileOnPostWithoutRepeatableSubfieldsAndDeleteExistingAction() {
    postRequest(MAPPING_PROFILES_PATH, MAPPING_PROFILE_WITH_EMPTY_SUBFIELDS_AND_DELETE_EXISTING_ACTION)
      .statusCode(SC_CREATED)
      .body("profile.name", is(MAPPING_PROFILE_WITH_EMPTY_SUBFIELDS_AND_DELETE_EXISTING_ACTION.getProfile().getName()))
      .body("profile.tags.tagList",
        is(MAPPING_PROFILE_WITH_EMPTY_SUBFIELDS_AND_DELETE_EXISTING_ACTION.getProfile().getTags().getTagList()));
  }

  @DisplayName("should create profile on POST without repeatable subfields and empty action")
  @Test
  void shouldCreateProfileOnPostWithoutRepeatableSubfieldsAndEmptyAction() {
    postRequest(MAPPING_PROFILES_PATH, MAPPING_PROFILE_WITH_EMPTY_SUBFIELDS_AND_EMPTY_ACTION)
      .statusCode(SC_CREATED)
      .body("profile.name", is(MAPPING_PROFILE_WITH_EMPTY_SUBFIELDS_AND_EMPTY_ACTION.getProfile().getName()))
      .body("profile.tags.tagList",
        is(MAPPING_PROFILE_WITH_EMPTY_SUBFIELDS_AND_EMPTY_ACTION.getProfile().getTags().getTagList()));
  }

  @DisplayName("should return 422 when creating profile without repeatable subfields and non-DELETE_EXISTING action")
  @Test
  void shouldNotCreateProfileOnPostWithoutRepeatableSubfieldsAndWithoutDeleteExistingAction() {
    postRequest(MAPPING_PROFILES_PATH, MAPPING_PROFILE_WITH_EMPTY_SUBFIELDS_AND_NOT_DELETE_EXISTING_ACTION)
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should create profile with apostrophe in name on POST")
  @Test
  void shouldCreateProfileOnPostWithApostropheInName() {
    postRequest(MAPPING_PROFILES_PATH, MAPPING_PROFILE_5)
      .statusCode(SC_CREATED)
      .body("profile.name", is(MAPPING_PROFILE_5.getProfile().getName()))
      .body("profile.tags.tagList", is(MAPPING_PROFILE_5.getProfile().getTags().getTagList()))
      .body("profile.userInfo.lastName", is("Doe"))
      .body("profile.userInfo.firstName", is("Jane"))
      .body("profile.userInfo.userName", is("@janedoe"));

    postRequest(MAPPING_PROFILES_PATH, MAPPING_PROFILE_5)
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should return 422 when PUT body is invalid")
  @Test
  void shouldReturnBadRequestOnPut() {
    putRequest(MAPPING_PROFILES_PATH + "/" + UUID.randomUUID(), new JsonObject().toString())
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should return 404 when PUT targets a non-existent profile")
  @Test
  void shouldReturnNotFoundOnPut() {
    putRequest(MAPPING_PROFILES_PATH + "/" + UUID.randomUUID(), MAPPING_PROFILE_2)
      .statusCode(SC_NOT_FOUND);
  }

  @DisplayName("should return 422 when PUT uses an already-taken profile name")
  @Test
  void shouldReturnUnprocessableEntityOnPutProfileWithExistingName() {
    createProfiles();

    // arrange
    MappingProfileUpdateDto createdProfile = postRequest(MAPPING_PROFILES_PATH,
      new MappingProfileUpdateDto().withProfile(new MappingProfile()
        .withName("newProfile")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE)))
      .statusCode(SC_CREATED)
      .extract().body().as(MappingProfileUpdateDto.class);

    // act + assert
    createdProfile.getProfile().setName(MAPPING_PROFILE_1.getProfile().getName());
    putRequest(MAPPING_PROFILES_PATH + "/" + createdProfile.getProfile().getId(), createdProfile)
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should update profile on PUT")
  @Test
  void shouldUpdateProfileOnPut() {
    // arrange
    MappingProfileUpdateDto mappingProfile = postRequest(MAPPING_PROFILES_PATH, MAPPING_PROFILE_2)
      .statusCode(SC_CREATED)
      .extract().body().as(MappingProfileUpdateDto.class);

    // act
    mappingProfile.getProfile().setDescription("test");
    // assert
    putRequest(MAPPING_PROFILES_PATH + "/" + mappingProfile.getProfile().getId(), mappingProfile)
      .statusCode(SC_OK)
      .body("id", is(mappingProfile.getProfile().getId()))
      .body("name", is(mappingProfile.getProfile().getName()))
      .body("description", is("test"))
      .body("tags.tagList", is(mappingProfile.getProfile().getTags().getTagList()))
      .body("userInfo.lastName", is("Doe"))
      .body("userInfo.firstName", is("Jane"))
      .body("userInfo.userName", is("@janedoe"));
  }

  @DisplayName("should return 404 when GET by non-existent ID")
  @Test
  void shouldReturnNotFoundOnGetById() {
    getRequest(MAPPING_PROFILES_PATH + "/" + UUID.randomUUID())
      .statusCode(SC_NOT_FOUND);
  }

  @DisplayName("should return profile on GET by ID")
  @Test
  void shouldReturnProfileOnGetById() {
    // arrange
    MappingProfileUpdateDto mappingProfile = postRequest(MAPPING_PROFILES_PATH, MAPPING_PROFILE_3)
      .statusCode(SC_CREATED)
      .extract().body().as(MappingProfileUpdateDto.class);

    // assert
    getRequest(MAPPING_PROFILES_PATH + "/" + mappingProfile.getProfile().getId())
      .statusCode(SC_OK)
      .body("id", is(mappingProfile.getProfile().getId()))
      .body("name", is(mappingProfile.getProfile().getName()))
      .body("tags.tagList", is(mappingProfile.getProfile().getTags().getTagList()))
      .body("userInfo.lastName", is("Doe"))
      .body("userInfo.firstName", is("Jane"))
      .body("userInfo.userName", is("@janedoe"));
  }

  @DisplayName("should return 404 when DELETE targets a non-existent profile")
  @Test
  void shouldReturnNotFoundOnDelete() {
    deleteRequest(MAPPING_PROFILES_PATH + "/" + UUID.randomUUID())
      .statusCode(SC_NOT_FOUND);
  }

  @DisplayName("should return 409 when deleting a mapping profile associated with other profiles")
  @Test
  void shouldReturnBadRequestOnDeleteProfileAssociatedWithOtherProfiles() {
    // arrange
    MappingProfileUpdateDto profileToDelete = postRequest(MAPPING_PROFILES_PATH, MAPPING_PROFILE_1)
      .statusCode(SC_CREATED)
      .extract().body().as(MappingProfileUpdateDto.class);

    ActionProfileUpdateDto actionProfile = postRequest(ACTION_PROFILES_PATH,
      new ActionProfileUpdateDto().withProfile(new ActionProfile()
        .withName("testActionProfile")
        .withAction(UPDATE)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)))
      .statusCode(SC_CREATED)
      .extract().body().as(ActionProfileUpdateDto.class);

    // act
    postRequest(ASSOCIATED_PROFILES_PATH, new ProfileAssociation()
      .withMasterProfileId(actionProfile.getProfile().getId())
      .withDetailProfileId(profileToDelete.getProfile().getId())
      .withMasterProfileType(ProfileType.ACTION_PROFILE)
      .withDetailProfileType(ProfileType.MAPPING_PROFILE)
      .withOrder(1), Map.of("master", ACTION_PROFILE.value(), "detail", MAPPING_PROFILE.value()))
      .statusCode(is(SC_CREATED));

    // assert
    deleteRequest(MAPPING_PROFILES_PATH + "/" + profileToDelete.getProfile().getId())
      .log().all()
      .statusCode(SC_CONFLICT);
  }

  @DisplayName("should hard-delete profile on DELETE")
  @Test
  void shouldHardDeleteProfileOnDeletion() {
    // arrange
    MappingProfileUpdateDto profile = postRequest(MAPPING_PROFILES_PATH, MAPPING_PROFILE_2)
      .statusCode(SC_CREATED)
      .extract().body().as(MappingProfileUpdateDto.class);

    // act
    deleteRequest(MAPPING_PROFILES_PATH + "/" + profile.getProfile().getId())
      .statusCode(SC_NO_CONTENT);

    // assert
    getRequest(MAPPING_PROFILES_PATH + "/" + profile.getProfile().getId())
      .statusCode(SC_NOT_FOUND);
  }

  @DisplayName("should return only non-deleted profiles on GET when deleted parameter is not passed")
  @Test
  void shouldReturnOnlyUnmarkedAsDeletedProfilesOnGetWhenParameterDeletedIsNotPassed() {
    createProfiles();
    MappingProfileUpdateDto mappingProfileToDelete = postRequest(MAPPING_PROFILES_PATH,
      new MappingProfileUpdateDto().withProfile(new MappingProfile()
        .withName("ProfileToDelete")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE)))
      .statusCode(SC_CREATED)
      .extract().body().as(MappingProfileUpdateDto.class);

    deleteRequest(MAPPING_PROFILES_PATH + "/" + mappingProfileToDelete.getProfile().getId())
      .statusCode(SC_NO_CONTENT);

    getRequest(MAPPING_PROFILES_PATH)
      .statusCode(SC_OK)
      .body("totalRecords", is(3))
      .body("mappingProfiles*.hidden", everyItem(is(false)));
  }

  @DisplayName("should create profile on POST and replace existing association with action profile")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldCreateProfileOnPostAndReplaceExistingAssociationWithActionProfile() {
    MappingProfileUpdateDto mappingProfile1 = postRequest(MAPPING_PROFILES_PATH, MAPPING_PROFILE_1)
      .statusCode(SC_CREATED)
      .extract().body().as(MappingProfileUpdateDto.class);

    ActionProfileUpdateDto actionProfile = postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("testActionProfile")
        .withAction(CREATE)
        .withFolioRecord(INSTANCE))
      .withAddedRelations(List.of(new ProfileAssociation()
        .withMasterProfileType(ProfileType.ACTION_PROFILE)
        .withDetailProfileType(ProfileType.MAPPING_PROFILE)
        .withDetailProfileId(mappingProfile1.getProfile().getId()))))
      .statusCode(SC_CREATED)
      .extract().body().as(ActionProfileUpdateDto.class);

    getRequest(ASSOCIATED_PROFILES_PATH,
      Map.of("master", ACTION_PROFILE.value(), "detail", MAPPING_PROFILE.value()))
      .statusCode(SC_OK)
      .body("profileAssociations", hasSize(1))
      .body("profileAssociations[0].masterProfileId", is(actionProfile.getProfile().getId()))
      .body("profileAssociations[0].detailProfileId", is(mappingProfile1.getProfile().getId()));

    MappingProfileUpdateDto mappingProfile2 = postRequest(MAPPING_PROFILES_PATH, new MappingProfileUpdateDto()
      .withProfile(new MappingProfile().withName("mapping profile 2")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE))
      .withAddedRelations(
        List.of(
          new ProfileAssociation()
            .withMasterProfileType(ProfileType.ACTION_PROFILE)
            .withDetailProfileType(ProfileType.MAPPING_PROFILE)
            .withMasterWrapperId(actionProfile.getAddedRelations().getFirst().getMasterWrapperId())
            .withMasterProfileId(actionProfile.getProfile().getId())
        )
      ))
      .statusCode(SC_CREATED)
      .extract().body().as(MappingProfileUpdateDto.class);

    getRequest(ASSOCIATED_PROFILES_PATH,
      Map.of("master", ACTION_PROFILE.value(), "detail", MAPPING_PROFILE.value()))
      .statusCode(SC_OK)
      .body("totalRecords", is(1))
      .body("profileAssociations", hasSize(1))
      .body("profileAssociations[0].masterProfileId", is(actionProfile.getProfile().getId()))
      .body("profileAssociations[0].detailProfileId", is(mappingProfile2.getProfile().getId()));
  }

  @DisplayName("should update profile on PUT and replace existing association with action profile")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldUpdateProfileOnPutAndReplaceExistingAssociationWithActionProfile() {
    MappingProfileUpdateDto mappingProfile1 = postRequest(MAPPING_PROFILES_PATH, MAPPING_PROFILE_1)
      .statusCode(SC_CREATED)
      .extract().body().as(MappingProfileUpdateDto.class);

    MappingProfileUpdateDto mappingProfile2 = postRequest(MAPPING_PROFILES_PATH, MAPPING_PROFILE_2)
      .statusCode(SC_CREATED)
      .extract().body().as(MappingProfileUpdateDto.class);

    ActionProfileUpdateDto actionProfile = postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("testActionProfile")
        .withAction(CREATE)
        .withFolioRecord(INSTANCE))
      .withAddedRelations(List.of(new ProfileAssociation()
        .withMasterProfileType(ProfileType.ACTION_PROFILE)
        .withDetailProfileType(ProfileType.MAPPING_PROFILE)
        .withDetailProfileId(mappingProfile1.getProfile().getId()))))
      .statusCode(SC_CREATED)
      .extract().body().as(ActionProfileUpdateDto.class);

    String masterWrapperId = getRequest(ASSOCIATED_PROFILES_PATH,
      Map.of("master", ACTION_PROFILE.value(), "detail", MAPPING_PROFILE.value()))
      .statusCode(SC_OK)
      .body("profileAssociations", hasSize(1))
      .body("profileAssociations[0].masterProfileId", is(actionProfile.getProfile().getId()))
      .body("profileAssociations[0].detailProfileId", is(mappingProfile1.getProfile().getId()))
      .body("profileAssociations[0].masterWrapperId", notNullValue())
      .extract()
      .path("profileAssociations[0].masterWrapperId");

    mappingProfile2.getProfile().setName("mapping profile 2");
    putRequest(MAPPING_PROFILES_PATH + "/" + mappingProfile2.getProfile().getId(),
      new MappingProfileUpdateDto()
        .withProfile(new MappingProfile().withName("mapping profile 2")
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withExistingRecordType(EntityType.INSTANCE))
        .withAddedRelations(List.of(new ProfileAssociation()
          .withMasterProfileType(ProfileType.ACTION_PROFILE)
          .withDetailProfileType(ProfileType.MAPPING_PROFILE)
          .withMasterProfileId(actionProfile.getProfile().getId())
          .withDetailProfileId(mappingProfile2.getProfile().getId())
          .withMasterWrapperId(masterWrapperId))))
      .statusCode(SC_OK)
      .body("name", is("mapping profile 2"));

    getRequest(ASSOCIATED_PROFILES_PATH,
      Map.of("master", ACTION_PROFILE.value(), "detail", MAPPING_PROFILE.value()))
      .statusCode(SC_OK)
      .body("profileAssociations", hasSize(1))
      .body("profileAssociations[0].masterProfileId", is(actionProfile.getProfile().getId()))
      .body("profileAssociations[0].detailProfileId", is(mappingProfile2.getProfile().getId()));
  }

  @DisplayName("should update profile associations on PUT")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldUpdateProfileAssociationsOnPut() {
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
        .withReactTo(ReactToType.MATCH)
        .withDetailProfileType(ProfileType.MAPPING_PROFILE))));

    putRequest(MAPPING_PROFILES_PATH + "/" + mappingProfileDto.getProfile().getId(),
      new MappingProfileUpdateDto()
        .withId(mappingProfileDto.getId())
        .withProfile(new MappingProfile()
          .withId(mappingProfileDto.getProfile().getId())
          .withName("Test Mapping Profile with relations")
          .withExistingRecordType(EntityType.INSTANCE)
          .withIncomingRecordType(EntityType.INSTANCE))
        .withDeletedRelations(List.of(new ProfileAssociation()
          .withMasterProfileType(ProfileType.ACTION_PROFILE)
          .withMasterProfileId(actionProfileDto.getProfile().getId())
          .withDetailProfileType(ProfileType.MAPPING_PROFILE)
          .withReactTo(ReactToType.MATCH)
          .withDetailProfileId(mappingProfileDto.getAddedRelations().getFirst().getDetailProfileId()))))
      .statusCode(SC_OK)
      .body("id", is(mappingProfileDto.getProfile().getId()))
      .body("name", is(mappingProfileDto.getProfile().getName()));

    getRequest(MAPPING_PROFILES_PATH + "/" + mappingProfileDto.getProfile().getId() + "?withRelations=true")
      .statusCode(SC_OK)
      .body("id", is(mappingProfileDto.getProfile().getId()))
      .body("name", is(mappingProfileDto.getProfile().getName()))
      .body("userInfo.lastName", is("Doe"))
      .body("userInfo.firstName", is("Jane"))
      .body("userInfo.userName", is("@janedoe"))
      .body("parentProfiles", is(empty()))
      .body("childProfiles", is(empty()));
  }

  @DisplayName("should reuse existing action wrapper ID on subsequent PUT")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldReuseExistingActionWrapperId() {
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
        .withDetailProfileType(ProfileType.MAPPING_PROFILE)
        .withReactTo(ReactToType.MATCH))));

    String actionProfileId = actionProfileDto.getId();
    String mappingProfileId = mappingProfileDto.getId();

    putRequest(MAPPING_PROFILES_PATH + "/" + mappingProfileDto.getProfile().getId(),
      new MappingProfileUpdateDto()
        .withId(mappingProfileDto.getId())
        .withProfile(new MappingProfile()
          .withId(mappingProfileId)
          .withName("Test Mapping Profile with relations")
          .withExistingRecordType(EntityType.INSTANCE)
          .withIncomingRecordType(EntityType.INSTANCE))
        .withDeletedRelations(List.of(new ProfileAssociation()
          .withMasterProfileType(ProfileType.ACTION_PROFILE)
          .withMasterProfileId(actionProfileId)
          .withDetailProfileType(ProfileType.MAPPING_PROFILE)
          .withReactTo(ReactToType.MATCH)
          .withDetailProfileId(mappingProfileId))))
      .statusCode(SC_OK)
      .body("id", is(mappingProfileDto.getProfile().getId()))
      .body("name", is(mappingProfileDto.getProfile().getName()));

    putRequest(MAPPING_PROFILES_PATH + "/" + mappingProfileDto.getProfile().getId(),
      new MappingProfileUpdateDto()
        .withId(mappingProfileId)
        .withProfile(new MappingProfile()
          .withId(mappingProfileId)
          .withName("Test Mapping Profile with relations")
          .withExistingRecordType(EntityType.INSTANCE)
          .withIncomingRecordType(EntityType.INSTANCE))
        .withAddedRelations(List.of(new ProfileAssociation()
          .withMasterProfileType(ProfileType.ACTION_PROFILE)
          .withMasterProfileId(actionProfileId)
          .withDetailProfileType(ProfileType.MAPPING_PROFILE)
          .withDetailProfileId(mappingProfileId))))
      .statusCode(SC_OK)
      .body("id", is(mappingProfileId))
      .body("name", is(mappingProfileDto.getProfile().getName()));
  }

  @DisplayName("should return 422 when mapping profile has different FolioRecord from action profile")
  @Test
  void shouldNotCreateMappingProfilesWhenDifferentFolioRecord() {
    var actionProfileUpdateDto = postActionProfile(new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("Test Action Profile")
        .withAction(UPDATE)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)));

    postRequest(MAPPING_PROFILES_PATH, new MappingProfileUpdateDto()
      .withProfile(new MappingProfile()
        .withName("Test Action Profile")
        .withExistingRecordType(EntityType.INSTANCE)
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC))
      .withAddedRelations(List.of(
        new ProfileAssociation()
          .withMasterProfileType(ProfileType.ACTION_PROFILE)
          .withMasterProfileId(actionProfileUpdateDto.getProfile().getId())
          .withDetailProfileType(ProfileType.MAPPING_PROFILE))))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Action profile 'Test Action Profile' can not be linked to this Mapping profile. "
             + "FolioRecord and ExistingRecordType types are different")
        )));
  }

  @DisplayName("should return 422 when mapping profile has different action type from action profile")
  @Test
  void shouldNotCreateMappingProfilesWhenDifferentActionType() {
    var actionProfileUpdateDto = postActionProfile(new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("Test Action Profile")
        .withAction(UPDATE)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)));

    postRequest(MAPPING_PROFILES_PATH, new MappingProfileUpdateDto()
      .withProfile(new MappingProfile()
        .withName("Test Action Profile")
        .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withMappingDetails(new MappingDetail().withMarcMappingOption(MappingDetail.MarcMappingOption.MODIFY)))
      .withAddedRelations(List.of(
        new ProfileAssociation()
          .withMasterProfileType(ProfileType.ACTION_PROFILE)
          .withMasterProfileId(actionProfileUpdateDto.getProfile().getId())
          .withDetailProfileType(ProfileType.MAPPING_PROFILE))))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Unable to complete requested change. "
             + "MARC Update Action profiles can only be linked with MARC Update "
             + "Mapping profiles and MARC Modify Action profiles can only be linked with MARC Modify Mapping profiles. "
             + "Please ensure your Action and Mapping profiles are of like types and try again.")
        )));
  }

  @DisplayName("should return 422 when updating mapping profile with different FolioRecord or record type")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldNotUpdateMappingProfilesWhenDifferentFolioRecordAndUpdateRecordTypeWhenRecordTypesAreDifferent() {
    var actionProfileUpdateDto = postActionProfile(new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("Test Action Profile")
        .withAction(CREATE)
        .withFolioRecord(INSTANCE)));
    var actionProfileUpdateDto1 = postActionProfile(new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("Test Action Profile1")
        .withAction(UPDATE)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)));

    var mappingProfileUpdateDto = postMappingProfile(new MappingProfileUpdateDto()
      .withProfile(new MappingProfile()
        .withName("Test Mapping Profile")
        .withExistingRecordType(EntityType.INSTANCE)
        .withIncomingRecordType(EntityType.INSTANCE))
      .withAddedRelations(List.of(new ProfileAssociation()
        .withMasterProfileType(ProfileType.ACTION_PROFILE)
        .withMasterProfileId(actionProfileUpdateDto.getId())
        .withDetailProfileType(ProfileType.MAPPING_PROFILE))));

    putRequest(MAPPING_PROFILES_PATH + "/" + mappingProfileUpdateDto.getProfile().getId(),
      new MappingProfileUpdateDto()
        .withProfile(new MappingProfile()
          .withName("Test Mapping Profile")
          .withExistingRecordType(EntityType.INSTANCE)
          .withIncomingRecordType(EntityType.INSTANCE))
        .withAddedRelations(List.of(new ProfileAssociation()
          .withMasterProfileType(ProfileType.ACTION_PROFILE)
          .withMasterProfileId(actionProfileUpdateDto1.getProfile().getId())
          .withDetailProfileType(ProfileType.MAPPING_PROFILE))
        )
        .withDeletedRelations(List.of(new ProfileAssociation()
          .withMasterProfileType(ProfileType.ACTION_PROFILE)
          .withMasterProfileId(actionProfileUpdateDto.getProfile().getId())
          .withDetailProfileType(ProfileType.MAPPING_PROFILE))))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Action profile 'Test Action Profile1' can not be linked to this Mapping profile. "
             + "FolioRecord and ExistingRecordType types are different")
        )));

    putRequest(MAPPING_PROFILES_PATH + "/" + mappingProfileUpdateDto.getProfile().getId(),
      new MappingProfileUpdateDto()
        .withProfile(new MappingProfile()
          .withName("Test Mapping Profile")
          .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withIncomingRecordType(EntityType.INSTANCE)
          .withParentProfiles(List.of(new ProfileSnapshotWrapper()
            .withId(actionProfileUpdateDto.getProfile().getId())
            .withContent(actionProfileUpdateDto)
            .withContentType(ACTION_PROFILE)))))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Can not update MappingProfile recordType and linked ActionProfile recordType are different")
        )));
  }

  @DisplayName("should return 422 when updating mapping profile with different action type")
  @Test
  void shouldNotUpdateMappingProfilesWhenDifferentActionType() {
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

    putRequest(MAPPING_PROFILES_PATH + "/" + mappingProfileUpdateDto.getProfile().getId(),
      new MappingProfileUpdateDto()
        .withProfile(new MappingProfile()
          .withName("Test Mapping Profile")
          .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
          .withMappingDetails(new MappingDetail().withMarcMappingOption(MappingDetail.MarcMappingOption.UPDATE))
          .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC))
        .withAddedRelations(List.of(
          new ProfileAssociation()
            .withMasterProfileType(ProfileType.ACTION_PROFILE)
            .withMasterProfileId(actionProfileUpdateDto.getProfile().getId())
            .withDetailProfileType(ProfileType.MAPPING_PROFILE))))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Unable to complete requested change. "
             + "MARC Update Action profiles can only be linked with MARC Update "
             + "Mapping profiles and MARC Modify Action profiles can only be linked with MARC Modify Mapping profiles. "
             + "Please ensure your Action and Mapping profiles are of like types and try again.")
        )));
  }

  @DisplayName("should return 422 when POST has non-empty child or parent profile")
  @Test
  void shouldReturnBadRequestWhenChildOrParentProfileNotEmptyOnPost() {
    createProfiles();
    postRequest(MAPPING_PROFILES_PATH, MAPPING_PROFILE_NOT_EMPTY_CHILD_AND_PARENT)
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("The field mapping profile read-only 'child' field should be empty"))
      .body("errors[1].message", is("The field mapping profile read-only 'parent' field should be empty"));
  }

  @DisplayName("should return 422 when PUT has non-empty child or parent profile")
  @Test
  void shouldReturnBadRequestWhenChildOrParentProfileNotEmptyOnPut() {
    // arrange
    MappingProfileUpdateDto createdProfile = postRequest(MAPPING_PROFILES_PATH,
      new MappingProfileUpdateDto().withProfile(new MappingProfile()
        .withName("newProfile")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE)))
      .statusCode(SC_CREATED)
      .extract().body().as(MappingProfileUpdateDto.class);

    createProfiles();
    // assert
    putRequest(MAPPING_PROFILES_PATH + "/" + createdProfile.getProfile().getId(),
      MAPPING_PROFILE_NOT_EMPTY_CHILD_AND_PARENT)
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("The field mapping profile read-only 'child' field should be empty"))
      .body("errors[1].message", is("The field mapping profile read-only 'parent' field should be empty"));
  }

  private void createProfiles() {
    var mappingProfilesToPost = Arrays.asList(MAPPING_PROFILE_1, MAPPING_PROFILE_2, MAPPING_PROFILE_3);
    for (MappingProfileUpdateDto profile : mappingProfilesToPost) {
      postRequest(MAPPING_PROFILES_PATH, profile)
        .statusCode(SC_CREATED);
    }
  }
}
