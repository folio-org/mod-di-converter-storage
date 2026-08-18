package org.folio.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.CREATE;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.DELETE;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.MODIFY;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.UPDATE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.INSTANCE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.MARC_AUTHORITY;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MAPPING_PROFILE;
import static org.folio.services.ActionProfileServiceImpl.INVALID_ACTION_PROFILE_DELETE_ACTION_TYPE;
import static org.folio.support.ProfileFixtures.ACTION_PROFILE_1;
import static org.folio.support.ProfileFixtures.ACTION_PROFILE_2;
import static org.folio.support.ProfileFixtures.ACTION_PROFILE_3;
import static org.folio.support.ProfileFixtures.ACTION_PROFILE_4;
import static org.folio.support.ProfileFixtures.ACTION_PROFILE_NOT_EMPTY_CHILD_AND_PARENT;
import static org.folio.support.ProfileFixtures.ACTION_PROFILE_UUID;
import static org.folio.support.TestUtil.ACTION_PROFILES_PATH;
import static org.folio.support.TestUtil.ASSOCIATED_PROFILES_PATH;
import static org.folio.support.TestUtil.ENTITY_TYPES_PATH;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import io.vertx.core.json.JsonObject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.MappingDetail;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.services.util.EntityTypes;
import org.folio.support.AbstractRestTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ActionProfileRestTest extends AbstractRestTest {

  @DisplayName("should create profile with DELETE action for MARC authority record")
  @Test
  void shouldCreateProfileOnPostWithDeleteActionAndMarcAuthorityRecord() {
    postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("Valid Delete Action Profile")
        .withAction(DELETE)
        .withFolioRecord(MARC_AUTHORITY)))
      .statusCode(HttpStatus.SC_CREATED);
  }

  @DisplayName("should return 422 when DELETE action is used with non-MARC-authority record")
  @Test
  void shouldReturnUnprocessableEntityOnPostWithDeleteActionAndNonMarcAuthorityRecord() {
    postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("Invalid Delete Action Profile")
        .withAction(DELETE)
        .withFolioRecord(INSTANCE)))
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is(INVALID_ACTION_PROFILE_DELETE_ACTION_TYPE));
  }

  @DisplayName("should return empty list when no profiles exist")
  @Test
  void shouldReturnEmptyListOnGet() {
    getRequest(ACTION_PROFILES_PATH)
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(0))
      .body("actionProfiles", empty());
  }

  @DisplayName("should return all profiles on GET")
  @Test
  void shouldReturnAllProfilesOnGet() {
    createProfiles();
    getRequest(ACTION_PROFILES_PATH, Map.of("withRelations", "true"))
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(3))
      .body("actionProfiles*.hidden", everyItem(is(false)));
  }

  @DisplayName("should return profiles matching query by last name")
  @Test
  void shouldReturnCommittedProfilesOnGetWithQueryByLastName() {
    createProfiles();
    getRequest(ACTION_PROFILES_PATH, Map.of("query", "userInfo.lastName=Doe"))
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(3))
      .body("actionProfiles*.hidden", everyItem(is(false)))
      .body("actionProfiles*.userInfo.lastName", everyItem(is("Doe")));
  }

  @DisplayName("should return profiles tagged with 'ipsum' when querying by tag")
  @Test
  void shouldReturnIpsumTaggedProfilesOnGetWithQueryByTag() {
    createProfiles();
    getRequest(ACTION_PROFILES_PATH, Map.of("query", "tags.tagList=/respectCase/respectAccents \\\"ipsum\\\""))
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(2))
      .body("actionProfiles*.hidden", everyItem(is(false)))
      .body("actionProfiles.get(0).tags.tagList", hasItem("ipsum"))
      .body("actionProfiles.get(1).tags.tagList", hasItem("ipsum"));
  }

  @DisplayName("should return limited collection when limit parameter is provided")
  @Test
  void shouldReturnLimitedCollectionOnGetWithLimit() {
    createProfiles();
    getRequest(ACTION_PROFILES_PATH, Map.of("limit", "2"))
      .statusCode(HttpStatus.SC_OK)
      .body("actionProfiles.size()", is(2))
      .body("totalRecords", is(3));
  }

  @DisplayName("should return 422 when MARC Bib record type is used with CREATE action")
  @Test
  void shouldReturnBadRequestOnPostWithMarcbibRecordAndCreateAction() {
    postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("Invalid Action Profile")
        .withAction(CREATE)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)))
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Can't create ActionProfile for MARC Bib record type with Create action"));
  }

  @DisplayName("should create profile and return 201 on POST")
  @Test
  void shouldCreateProfileOnPost() {
    postRequest(ACTION_PROFILES_PATH, ACTION_PROFILE_1)
      .statusCode(HttpStatus.SC_CREATED)
      .body("profile.name", is(ACTION_PROFILE_1.getProfile().getName()))
      .body("profile.tags.tagList", is(ACTION_PROFILE_1.getProfile().getTags().getTagList()))
      .body("profile.userInfo.lastName", is("Doe"))
      .body("profile.userInfo.firstName", is("Jane"))
      .body("profile.userInfo.userName", is("@janedoe"));

    postRequest(ACTION_PROFILES_PATH, ACTION_PROFILE_1)
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should not override default action/folio-record on update for each FolioRecord type")
  @ParameterizedTest
  @EnumSource(ActionProfile.FolioRecord.class)
  void shouldNotOverrideDefaultsOnCreateOrUpdate_whenFolioRecordType(ActionProfile.FolioRecord folioRecord) {
    boolean expectsOverriding = !folioRecord.equals(INSTANCE) && !folioRecord.equals(MARC_BIBLIOGRAPHIC);
    testCreateUpdateActionProfileNotOverridingDefaults(expectsOverriding, folioRecord, !expectsOverriding);
  }

  @DisplayName("should create profile with a given id on POST")
  @Test
  void shouldCreateProfileWithGivenIdOnPost() {
    postRequest(ACTION_PROFILES_PATH, ACTION_PROFILE_4)
      .statusCode(HttpStatus.SC_CREATED)
      .body("profile.name", is(ACTION_PROFILE_4.getProfile().getName()))
      .body("profile.tags.tagList", is(ACTION_PROFILE_4.getProfile().getTags().getTagList()))
      .body("profile.userInfo.lastName", is("Doe"))
      .body("profile.userInfo.firstName", is("Jane"))
      .body("profile.userInfo.userName", is("@janedoe"));

    postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withId(ACTION_PROFILE_UUID).withName("GOA")
        .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
        .withAction(CREATE)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)))
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Action profile with id 'GOA' already exists"));
  }

  @DisplayName("should return 422 on PUT with invalid body")
  @Test
  void shouldReturnBadRequestOnPut() {
    putRequest(ACTION_PROFILES_PATH + "/" + UUID.randomUUID(), new JsonObject().toString())
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should return 422 when MARC Bib record type and CREATE action are used on PUT")
  @Test
  void shouldReturnBadRequestOnPutWithMarcbibRecordAndCreateAction() {
    postRequest(ACTION_PROFILES_PATH, ACTION_PROFILE_4)
      .statusCode(HttpStatus.SC_CREATED);

    putRequest(ACTION_PROFILES_PATH + "/" + ACTION_PROFILE_4.getProfile().getId(),
      ACTION_PROFILE_4.withProfile(ACTION_PROFILE_4.getProfile().withAction(CREATE)))
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Can't create ActionProfile for MARC Bib record type with Create action"));
  }

  @DisplayName("should return 404 on PUT when profile does not exist")
  @Test
  void shouldReturnNotFoundOnPut() {
    putRequest(ACTION_PROFILES_PATH + "/" + UUID.randomUUID(), ACTION_PROFILE_2)
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @DisplayName("should return 422 on PUT when profile name already exists")
  @Test
  void shouldReturnUnprocessableEntityOnPutProfileWithExistingName() {
    createProfiles();

    var createdProfile = postActionProfile(new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("newProfile")
        .withAction(CREATE)
        .withFolioRecord(INSTANCE)));

    createdProfile.getProfile().setName(ACTION_PROFILE_1.getProfile().getName());
    putRequest(ACTION_PROFILES_PATH + "/" + createdProfile.getProfile().getId(), createdProfile)
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should update profile description on PUT")
  @Test
  void shouldUpdateProfileOnPut() {
    var actionProfile = postActionProfile(ACTION_PROFILE_2);

    actionProfile.getProfile().setDescription("test");
    putRequest(ACTION_PROFILES_PATH + "/" + actionProfile.getProfile().getId(), actionProfile)
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(actionProfile.getProfile().getId()))
      .body("description", is("test"))
      .body("name", is(actionProfile.getProfile().getName()))
      .body("tags.tagList", is(actionProfile.getProfile().getTags().getTagList()))
      .body("userInfo.lastName", is("Doe"))
      .body("userInfo.firstName", is("Jane"))
      .body("userInfo.userName", is("@janedoe"));
  }

  @DisplayName("should update profile associations on PUT")
  @Test
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
        .withDetailProfileType(ProfileType.MAPPING_PROFILE))));

    putRequest(ACTION_PROFILES_PATH + "/" + actionProfileDto.getProfile().getId(), actionProfileDto
      .withDeletedRelations(List.of(new ProfileAssociation()
        .withMasterProfileType(ProfileType.ACTION_PROFILE)
        .withMasterProfileId(actionProfileDto.getProfile().getId())
        .withDetailProfileType(ProfileType.MAPPING_PROFILE)
        .withDetailProfileId(mappingProfileDto.getAddedRelations().getFirst().getDetailProfileId()))))
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(actionProfileDto.getProfile().getId()))
      .body("name", is(actionProfileDto.getProfile().getName()));

    getRequest(ACTION_PROFILES_PATH + "/" + actionProfileDto.getProfile().getId() + "?withRelations=true")
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
  @SuppressWarnings("checkstyle:MethodLength")
  @DisplayName("should reuse existing action wrapper id when two action profiles share one mapping profile")
  void shouldReuseExistingActionWrapperIdForCaseWhenTwoActionProfilesUseOneMappingProfile() {
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
    var profileAssociation1 = new ProfileAssociation()
      .withMasterProfileId(actionProfileDto1.getProfile().getId())
      .withOrder(1);

    //creation associations 2
    var profileAssociation2 = new ProfileAssociation()
      .withMasterProfileId(actionProfileDto2.getProfile().getId())
      .withOrder(1);

    //creation mapping profile
    var associatedMappingProfile = postMappingProfile(new MappingProfileUpdateDto()
      .withProfile(new MappingProfile().withName("Test Mapping Profile")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE)));

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
    putRequest(ACTION_PROFILES_PATH + "/" + actionProfileDto1.getProfile().getId(), actionProfileDto1
      .withDeletedRelations(List.of(new ProfileAssociation()
        .withMasterProfileType(ProfileType.ACTION_PROFILE)
        .withMasterProfileId(actionProfileDto1.getId())
        .withDetailProfileType(ProfileType.MAPPING_PROFILE)
        .withDetailProfileId(associatedMappingProfile.getId()))))
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(actionProfileDto1.getId()))
      .body("name", is(actionProfileDto1.getProfile().getName()));

    //linking actionProfile1 - mappingProfile
    putRequest(ACTION_PROFILES_PATH + "/" + actionProfileDto1.getProfile().getId(), actionProfileDto1
      .withDeletedRelations(List.of())
      .withAddedRelations(List.of(new ProfileAssociation()
        .withMasterProfileType(ProfileType.ACTION_PROFILE)
        .withMasterProfileId(actionProfileDto1.getId())
        .withDetailProfileType(ProfileType.MAPPING_PROFILE)
        .withDetailProfileId(associatedMappingProfile.getId()))))
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(actionProfileDto1.getId()))
      .body("name", is(actionProfileDto1.getProfile().getName()));
  }

  @DisplayName("should reuse existing action wrapper id on re-link")
  @Test
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
        .withDetailProfileType(ProfileType.MAPPING_PROFILE))));

    String actionProfileId = actionProfileDto.getId();
    String mappingProfileId = mappingProfileDto.getId();

    putRequest(ACTION_PROFILES_PATH + "/" + actionProfileDto.getProfile().getId(), actionProfileDto
      .withDeletedRelations(List.of(new ProfileAssociation()
        .withMasterProfileType(ProfileType.ACTION_PROFILE)
        .withMasterProfileId(actionProfileId)
        .withDetailProfileType(ProfileType.MAPPING_PROFILE)
        .withDetailProfileId(mappingProfileId))))
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(actionProfileId))
      .body("name", is(actionProfileDto.getProfile().getName()));

    putRequest(ACTION_PROFILES_PATH + "/" + actionProfileDto.getProfile().getId(), actionProfileDto
      .withDeletedRelations(List.of())
      .withAddedRelations(List.of(new ProfileAssociation()
        .withMasterProfileType(ProfileType.ACTION_PROFILE)
        .withMasterProfileId(actionProfileId)
        .withDetailProfileType(ProfileType.MAPPING_PROFILE)
        .withDetailProfileId(mappingProfileId))))
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(actionProfileDto.getId()))
      .body("name", is(actionProfileDto.getProfile().getName()));
  }

  @DisplayName("should return 404 on GET by id when profile does not exist")
  @Test
  void shouldReturnNotFoundOnGetById() {
    getRequest(ACTION_PROFILES_PATH + "/" + UUID.randomUUID())
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @DisplayName("should return profile on GET by id")
  @Test
  void shouldReturnProfileOnGetById() {
    var actionProfile = postActionProfile(ACTION_PROFILE_3);

    getRequest(ACTION_PROFILES_PATH + "/" + actionProfile.getProfile().getId())
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(actionProfile.getProfile().getId()))
      .body("name", is(actionProfile.getProfile().getName()))
      .body("tags.tagList", is(actionProfile.getProfile().getTags().getTagList()))
      .body("userInfo.lastName", is("Doe"))
      .body("userInfo.firstName", is("Jane"))
      .body("userInfo.userName", is("@janedoe"));
  }

  @DisplayName("should return 404 on DELETE when profile does not exist")
  @Test
  void shouldReturnNotFoundOnDelete() {
    deleteRequest(ACTION_PROFILES_PATH + "/" + UUID.randomUUID())
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @DisplayName("should return 409 when deleting a profile associated with other profiles")
  @Test
  void shouldReturnBadRequestOnDeleteProfileAssociatedWithOtherProfiles() {
    var profileToDelete = postActionProfile(ACTION_PROFILE_1);
    var associatedActionProfile = postActionProfile(ACTION_PROFILE_2);

    postRequest(ASSOCIATED_PROFILES_PATH, new ProfileAssociation()
      .withMasterProfileId(associatedActionProfile.getProfile().getId())
      .withDetailProfileId(profileToDelete.getProfile().getId())
      .withMasterProfileType(ProfileType.ACTION_PROFILE)
      .withDetailProfileType(ProfileType.ACTION_PROFILE)
      .withOrder(1), Map.of("master", ACTION_PROFILE.value(), "detail", ACTION_PROFILE.value()))
      .statusCode(is(HttpStatus.SC_CREATED));

    deleteRequest(ACTION_PROFILES_PATH + "/" + profileToDelete.getProfile().getId())
      .log().all()
      .statusCode(HttpStatus.SC_CONFLICT);
  }

  @DisplayName("should hard delete profile on deletion")
  @Test
  void shouldHardDeleteProfileOnDeletion() {
    var profile = postActionProfile(ACTION_PROFILE_2);

    deleteRequest(ACTION_PROFILES_PATH + "/" + profile.getProfile().getId())
      .statusCode(HttpStatus.SC_NO_CONTENT);

    getRequest(ACTION_PROFILES_PATH + "/" + profile.getProfile().getId())
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  @DisplayName("should delete associations with detail profiles on delete")
  void shouldDeleteAssociationsWithDetailProfilesOnDelete() {
    var profileToDelete = postActionProfile(ACTION_PROFILE_1);
    // creation detail-profiles
    var associatedActionProfile = postActionProfile(ACTION_PROFILE_2);
    var associatedMappingProfile = postMappingProfile(new MappingProfileUpdateDto()
      .withProfile(new MappingProfile().withName("testMapping")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE)));

    // creation associations
    var profileAssociation = new ProfileAssociation()
      .withMasterProfileId(profileToDelete.getProfile().getId())
      .withOrder(1);

    var actionToActionAssociation = postProfileAssociation(
      profileAssociation.withDetailProfileId(associatedActionProfile.getProfile().getId())
        .withMasterProfileId(profileToDelete.getProfile().getId())
        .withMasterProfileType(ProfileType.ACTION_PROFILE)
        .withDetailProfileType(ProfileType.ACTION_PROFILE),
      ACTION_PROFILE, ACTION_PROFILE);

    var actionToMappingAssociation = postProfileAssociation(
      profileAssociation.withDetailProfileId(associatedMappingProfile.getProfile().getId())
        .withMasterProfileId(profileToDelete.getProfile().getId())
        .withMasterProfileType(ProfileType.ACTION_PROFILE)
        .withDetailProfileType(ProfileType.MAPPING_PROFILE),
      ACTION_PROFILE, MAPPING_PROFILE);

    // deleting action profile
    deleteRequest(ACTION_PROFILES_PATH + "/" + profileToDelete.getProfile().getId())
      .statusCode(HttpStatus.SC_NO_CONTENT);

    // receiving deleted associations
    getRequest(ASSOCIATED_PROFILES_PATH + "/" + actionToActionAssociation.getId(),
      Map.of("master", ACTION_PROFILE.value(), "detail", ACTION_PROFILE.value()))
      .statusCode(HttpStatus.SC_NOT_FOUND);

    getRequest(ASSOCIATED_PROFILES_PATH + "/" + actionToMappingAssociation.getId(),
      Map.of("master", ACTION_PROFILE.value(), "detail", MAPPING_PROFILE.value()))
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @DisplayName("should return only non-deleted profiles on GET when deleted parameter is not passed")
  @Test
  void shouldReturnOnlyUnmarkedAsDeletedProfilesOnGetWhenParameterDeletedIsNotPassed() {
    createProfiles();
    var profileToDelete = postActionProfile(new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("ProfileToDelete")
        .withAction(CREATE)
        .withFolioRecord(INSTANCE)));

    deleteRequest(ACTION_PROFILES_PATH + "/" + profileToDelete.getProfile().getId())
      .statusCode(HttpStatus.SC_NO_CONTENT);

    getRequest(ACTION_PROFILES_PATH)
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(3))
      .body("actionProfiles*.hidden", everyItem(is(false)));
  }

  @DisplayName("should return all entity types on GET")
  @Test
  void shouldReturnAllEntityTypesOnGet() {
    List<String> entityTypesList = Arrays.stream(EntityTypes.values())
      .map(EntityTypes::getName)
      .toList();

    getRequest(ENTITY_TYPES_PATH)
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(entityTypesList.size()))
      .body("entityTypes", containsInAnyOrder(entityTypesList.toArray()));
  }

  @DisplayName("should return 422 when action profile has different FolioRecord than linked mapping profile")
  @Test
  void shouldNotCreateActionProfilesWhenDifferentFolioRecord() {
    var mappingProfileUpdateDto = postMappingProfile(new MappingProfileUpdateDto()
      .withProfile(new MappingProfile()
        .withName("Test Mapping Profile")
        .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
        .withExistingRecordType(EntityType.ITEM)
        .withIncomingRecordType(EntityType.HOLDINGS)));

    postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("Test Action Profile")
        .withAction(CREATE)
        .withFolioRecord(MARC_BIBLIOGRAPHIC))
      .withAddedRelations(List.of(
        new ProfileAssociation()
          .withDetailProfileType(ProfileType.MAPPING_PROFILE)
          .withDetailProfileId(mappingProfileUpdateDto.getId())
          .withMasterProfileType(ProfileType.ACTION_PROFILE))))
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Mapping profile 'Test Mapping Profile' can not be linked to this Action profile. "
             + "ExistingRecordType and FolioRecord types are different")
        )));
  }

  @DisplayName("should return 422 when action profile has different action type than linked mapping profile")
  @Test
  void shouldNotCreateActionProfilesWhenDifferentActionType() {
    var mappingProfileUpdateDto = postMappingProfile(new MappingProfileUpdateDto()
      .withProfile(new MappingProfile()
        .withName("Test Mapping Profile")
        .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
        .withMappingDetails(new MappingDetail().withMarcMappingOption(MappingDetail.MarcMappingOption.UPDATE))
        .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)));

    postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("Test Action Profile")
        .withAction(MODIFY)
        .withFolioRecord(MARC_BIBLIOGRAPHIC))
      .withAddedRelations(List.of(
        new ProfileAssociation()
          .withDetailProfileType(ProfileType.MAPPING_PROFILE)
          .withDetailProfileId(mappingProfileUpdateDto.getId())
          .withMasterProfileType(ProfileType.ACTION_PROFILE))))
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Unable to complete requested change. "
             + "MARC Update Action profiles can only be linked with MARC Update "
             + "Mapping profiles and MARC Modify Action profiles can only be linked with MARC Modify Mapping profiles. "
             + "Please ensure your Action and Mapping profiles are of like types and try again.")
        )));
  }

  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  @DisplayName("should return 422 on update when action profile FolioRecord differs from linked mapping profile")
  void shouldNotUpdateActionProfilesWhenDifferentFolioRecord() {
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

    putRequest(ACTION_PROFILES_PATH + "/" + actionProfileUpdateDto.getProfile().getId(),
      new ActionProfileUpdateDto()
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
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Mapping profile 'Test Mapping Profile1' can not be linked to this Action profile. "
             + "ExistingRecordType and FolioRecord types are different")
        )));

    putRequest(ACTION_PROFILES_PATH + "/" + actionProfileUpdateDto.getProfile().getId(),
      new ActionProfileUpdateDto()
        .withProfile(new ActionProfile()
          .withName("Test Action Profile")
          .withAction(CREATE)
          .withFolioRecord(INSTANCE)
          .withChildProfiles(List.of(new ProfileSnapshotWrapper()
            .withId(mappingProfileUpdateDto.getId())
            .withContent(mappingProfileUpdateDto)
            .withContentType(MAPPING_PROFILE)))))
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Can not update ActionProfile recordType and linked MappingProfile recordType are different")
        )));
  }

  @DisplayName("should return 422 on update when action profile action type differs from linked mapping profile")
  @Test
  void shouldNotUpdateActionProfilesWhenDifferentActionType() {
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

    putRequest(ACTION_PROFILES_PATH + "/" + actionProfileUpdateDto.getProfile().getId(),
      new ActionProfileUpdateDto()
        .withProfile(new ActionProfile()
          .withName("Test Action Profile")
          .withAction(MODIFY)
          .withFolioRecord(MARC_BIBLIOGRAPHIC))
        .withAddedRelations(List.of(
          new ProfileAssociation()
            .withDetailProfileType(ProfileType.MAPPING_PROFILE)
            .withDetailProfileId(mappingProfileUpdateDto.getProfile().getId())
            .withMasterProfileType(ProfileType.ACTION_PROFILE))))
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Unable to complete requested change. "
             + "MARC Update Action profiles can only be linked with MARC Update "
             + "Mapping profiles and MARC Modify Action profiles can only be linked with MARC Modify Mapping profiles. "
             + "Please ensure your Action and Mapping profiles are of like types and try again.")
        )));
  }

  @DisplayName("should return 422 when child or parent profile is not empty on POST")
  @Test
  void shouldReturnBadRequestIfChildOrParentProfileIsNotEmptyOnPost() {
    postRequest(ACTION_PROFILES_PATH, ACTION_PROFILE_NOT_EMPTY_CHILD_AND_PARENT)
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Action profile read-only 'child' field should be empty"))
      .body("errors[1].message", is("Action profile read-only 'parent' field should be empty"));
  }

  @DisplayName("should return 422 when child or parent profile is not empty on PUT")
  @Test
  void shouldReturnBadRequestIfChildOrParentProfileIsNotEmptyOnPut() {
    var actionProfileUpdateDto = postActionProfile(new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withName("Test Action Profile")
        .withAction(UPDATE)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)));

    putRequest(ACTION_PROFILES_PATH + "/" + actionProfileUpdateDto.getProfile().getId(),
      ACTION_PROFILE_NOT_EMPTY_CHILD_AND_PARENT)
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

    var actionProfileUpdate = postActionProfile(actionProfile);
    assertThat(actionProfileUpdate.getProfile().getRemove9Subfields()).isEqualTo(expectedRemove9SubfieldFlag);

    actionProfileUpdate.getProfile().setRemove9Subfields(incomingRemove9SubfieldFlag);
    putRequest(ACTION_PROFILES_PATH + "/" + actionProfileUpdate.getProfile().getId(), actionProfileUpdate)
      .statusCode(HttpStatus.SC_OK)
      .body("remove9Subfields", is(expectedRemove9SubfieldFlag));
  }

  private void createProfiles() {
    List<ActionProfileUpdateDto> actionProfilesToPost =
      Arrays.asList(ACTION_PROFILE_1, ACTION_PROFILE_2, ACTION_PROFILE_3);
    for (ActionProfileUpdateDto profile : actionProfilesToPost) {
      postRequest(ACTION_PROFILES_PATH, profile)
        .statusCode(HttpStatus.SC_CREATED);
    }
  }
}
