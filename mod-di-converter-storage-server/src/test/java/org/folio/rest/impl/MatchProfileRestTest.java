package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.UPDATE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC;
import static org.folio.rest.jaxrs.model.MatchDetail.MatchCriterion.EXACTLY_MATCHES;
import static org.folio.rest.jaxrs.model.MatchExpression.DataValueType.VALUE_FROM_RECORD;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;
import static org.folio.rest.jaxrs.model.Qualifier.ComparisonPart.NUMERICS_ONLY;
import static org.folio.support.ProfileFixtures.ACTION_PROFILE_1;
import static org.folio.support.ProfileFixtures.JOB_PROFILE_1;
import static org.folio.support.ProfileFixtures.MAPPING_PROFILE_1;
import static org.folio.support.ProfileFixtures.MAPPING_PROFILE_2;
import static org.folio.support.ProfileFixtures.MAPPING_PROFILE_3;
import static org.folio.support.ProfileFixtures.MATCH_PROFILE_1;
import static org.folio.support.ProfileFixtures.MATCH_PROFILE_2;
import static org.folio.support.ProfileFixtures.MATCH_PROFILE_3;
import static org.folio.support.ProfileFixtures.MATCH_PROFILE_4;
import static org.folio.support.ProfileFixtures.MATCH_PROFILE_NOT_EMPTY_CHILD_AND_PARENT;
import static org.folio.support.ProfileFixtures.MATCH_PROFILE_UUID;
import static org.folio.support.TestUtil.ACTION_PROFILES_PATH;
import static org.folio.support.TestUtil.ASSOCIATED_PROFILES_PATH;
import static org.folio.support.TestUtil.JOB_PROFILES_PATH;
import static org.folio.support.TestUtil.MAPPING_PROFILES_PATH;
import static org.folio.support.TestUtil.MATCH_PROFILES_PATH;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.google.common.collect.Lists;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
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
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.Qualifier;
import org.folio.rest.jaxrs.model.ReactToType;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.support.AbstractRestTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchProfileRestTest extends AbstractRestTest {

  @DisplayName("should return empty list when no profiles exist")
  @Test
  void shouldReturnEmptyListOnGet() {
    getRequest(MATCH_PROFILES_PATH)
      .statusCode(SC_OK)
      .body("totalRecords", is(0))
      .body("matchProfiles", empty());
  }

  @DisplayName("should return all profiles on GET")
  @Test
  void shouldReturnAllProfilesOnGet() {
    createProfiles();
    getRequest(MATCH_PROFILES_PATH + "?withRelations=true")
      .statusCode(SC_OK)
      .body("totalRecords", is(3))
      .body("matchProfiles*.hidden", everyItem(is(false)));
  }

  @DisplayName("should return all profiles with tree relations on GET")
  @Test
  void shouldReturnAllProfilesOnGetTree() {
    List<String> ids = createProfiles();
    createProfilesTree(ids);
    getRequest(MATCH_PROFILES_PATH + "?withRelations=true&query=id=" + ids.getFirst())
      .statusCode(SC_OK)
      .body("totalRecords", is(1))
      .body("matchProfiles*.childProfiles*.id", everyItem(is(notNullValue())))
      .body("matchProfiles*.parentProfiles*.id", everyItem(is(notNullValue())))
      .body("matchProfiles*.hidden", everyItem(is(false)));
  }

  @DisplayName("should return all profiles with tree relations on GET by ID")
  @Test
  void shouldReturnAllProfilesOnGetByIdTree() {
    List<String> ids = createProfiles();
    createProfilesTree(ids);
    getRequest(MATCH_PROFILES_PATH + "/" + ids.getFirst() + "?withRelations=true")
      .statusCode(SC_OK)
      .body("childProfiles*.id", everyItem(is(notNullValue())))
      .body("parentProfiles*.id", everyItem(is(notNullValue())));
  }

  @DisplayName("should return profiles matching lastName query")
  @Test
  void shouldReturnCommittedProfilesOnGetWithQueryByLastName() {
    createProfiles();
    getRequest(MATCH_PROFILES_PATH + "?query=userInfo.lastName=Doe")
      .statusCode(SC_OK)
      .body("totalRecords", is(3))
      .body("matchProfiles*.hidden", everyItem(is(false)))
      .body("matchProfiles*.userInfo.lastName", everyItem(is("Doe")));
  }

  @DisplayName("should return profiles with 'ipsum' tag when queried by tag")
  @Test
  void shouldReturnIpsumTaggedProfilesOnGetWithQueryByTag() {
    createProfiles();
    getRequest(MATCH_PROFILES_PATH + "?query=tags.tagList=/respectCase/respectAccents \\\"ipsum\\\"")
      .statusCode(SC_OK)
      .body("totalRecords", is(2))
      .body("matchProfiles.get(0).tags.tagList", hasItem("ipsum"))
      .body("matchProfiles.get(1).tags.tagList", hasItem("ipsum"));
  }

  @DisplayName("should return limited collection when limit parameter is provided")
  @Test
  void shouldReturnLimitedCollectionOnGetWithLimit() {
    createProfiles();
    getRequest(MATCH_PROFILES_PATH + "?limit=2")
      .statusCode(SC_OK)
      .body("matchProfiles.size()", is(2))
      .body("totalRecords", is(3));
  }

  @DisplayName("should return 422 when POST body is invalid")
  @Test
  void shouldReturnBadRequestOnPost() {
    createProfiles();
    postRequest(MATCH_PROFILES_PATH, new JsonObject().toString())
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should create profile on POST")
  @Test
  void shouldCreateProfileOnPost() {
    postRequest(MATCH_PROFILES_PATH, MATCH_PROFILE_1)
      .statusCode(SC_CREATED)
      .body("profile.name", is(MATCH_PROFILE_1.getProfile().getName()))
      .body("profile.tags.tagList", is(MATCH_PROFILE_1.getProfile().getTags().getTagList()))
      .body("profile.userInfo.lastName", is("Doe"))
      .body("profile.userInfo.firstName", is("Jane"))
      .body("profile.userInfo.userName", is("@janedoe"));

    postRequest(MATCH_PROFILES_PATH, MATCH_PROFILE_1)
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should create profile with given ID on POST")
  @Test
  void shouldCreateProfileWithGivenIdOnPost() {
    postRequest(MATCH_PROFILES_PATH, MATCH_PROFILE_4)
      .statusCode(SC_CREATED)
      .body("profile.name", is(MATCH_PROFILE_4.getProfile().getName()))
      .body("profile.tags.tagList", is(MATCH_PROFILE_4.getProfile().getTags().getTagList()))
      .body("profile.userInfo.lastName", is("Doe"))
      .body("profile.userInfo.firstName", is("Jane"))
      .body("profile.userInfo.userName", is("@janedoe"));

    postRequest(MATCH_PROFILES_PATH, new MatchProfileUpdateDto()
      .withProfile(new MatchProfile().withId(MATCH_PROFILE_UUID).withName("GOA")
        .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum")))
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Match profile with id 'GOA' already exists"));
  }

  @DisplayName("should return 422 when PUT body is invalid")
  @Test
  void shouldReturnBadRequestOnPut() {
    putRequest(MATCH_PROFILES_PATH + "/" + UUID.randomUUID(), new JsonObject().toString())
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should return 404 when PUT targets a non-existent profile")
  @Test
  void shouldReturnNotFoundOnPut() {
    putRequest(MATCH_PROFILES_PATH + "/" + UUID.randomUUID(), MATCH_PROFILE_2)
      .statusCode(SC_NOT_FOUND);
  }

  @DisplayName("should return 422 when PUT uses an already-taken profile name")
  @Test
  void shouldReturnUnprocessableEntityOnPutProfileWithExistingName() {
    createProfiles();

    // arrange
    MatchProfileUpdateDto createdProfile = postRequest(MATCH_PROFILES_PATH,
      new MatchProfileUpdateDto().withProfile(new MatchProfile()
        .withName("newProfile")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)))
      .statusCode(SC_CREATED)
      .extract().body().as(MatchProfileUpdateDto.class);

    // act + assert
    createdProfile.getProfile().setName(MATCH_PROFILE_1.getProfile().getName());
    putRequest(MATCH_PROFILES_PATH + "/" + createdProfile.getProfile().getId(), createdProfile)
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should update profile on PUT")
  @Test
  void shouldUpdateProfileOnPut() {
    // arrange
    MatchProfileUpdateDto matchProfile = postRequest(MATCH_PROFILES_PATH, MATCH_PROFILE_2)
      .statusCode(SC_CREATED)
      .extract().body().as(MatchProfileUpdateDto.class);

    // act
    matchProfile.getProfile().setDescription("test");
    // assert
    putRequest(MATCH_PROFILES_PATH + "/" + matchProfile.getProfile().getId(), matchProfile)
      .statusCode(SC_OK)
      .body("id", is(matchProfile.getProfile().getId()))
      .body("name", is(matchProfile.getProfile().getName()))
      .body("description", is("test"))
      .body("tags.tagList", is(matchProfile.getProfile().getTags().getTagList()))
      .body("userInfo.lastName", is("Doe"))
      .body("userInfo.firstName", is("Jane"))
      .body("userInfo.userName", is("@janedoe"));
  }

  @DisplayName("should return 404 when GET by non-existent ID")
  @Test
  void shouldReturnNotFoundOnGetById() {
    getRequest(MATCH_PROFILES_PATH + "/" + UUID.randomUUID())
      .statusCode(SC_NOT_FOUND);
  }

  @DisplayName("should return profile on GET by ID")
  @Test
  void shouldReturnProfileOnGetById() {
    // arrange
    MatchProfileUpdateDto matchProfile = postRequest(MATCH_PROFILES_PATH, MATCH_PROFILE_3)
      .statusCode(SC_CREATED)
      .extract().body().as(MatchProfileUpdateDto.class);

    // assert
    getRequest(MATCH_PROFILES_PATH + "/" + matchProfile.getProfile().getId())
      .statusCode(SC_OK)
      .body("id", is(matchProfile.getProfile().getId()))
      .body("name", is(matchProfile.getProfile().getName()))
      .body("tags.tagList", is(matchProfile.getProfile().getTags().getTagList()))
      .body("userInfo.lastName", is(matchProfile.getProfile().getUserInfo().getLastName()))
      .body("userInfo.firstName", is(matchProfile.getProfile().getUserInfo().getFirstName()))
      .body("userInfo.userName", is(matchProfile.getProfile().getUserInfo().getUserName()));
  }

  @DisplayName("should return 404 when DELETE targets a non-existent profile")
  @Test
  void shouldReturnNotFoundOnDelete() {
    deleteRequest(MATCH_PROFILES_PATH + "/" + UUID.randomUUID())
      .statusCode(SC_NOT_FOUND);
  }

  @DisplayName("should return 409 when deleting a match profile associated with other profiles")
  @Test
  void shouldReturnBadRequestOnDeleteProfileAssociatedWithOtherProfiles() {
    // arrange
    MatchProfileUpdateDto profileToDelete = postRequest(MATCH_PROFILES_PATH, MATCH_PROFILE_1)
      .statusCode(SC_CREATED)
      .extract().body().as(MatchProfileUpdateDto.class);

    MatchProfileUpdateDto matchProfile = postRequest(MATCH_PROFILES_PATH, MATCH_PROFILE_2)
      .statusCode(SC_CREATED)
      .extract().body().as(MatchProfileUpdateDto.class);

    // act
    postRequest(ASSOCIATED_PROFILES_PATH, new ProfileAssociation()
      .withMasterProfileId(matchProfile.getProfile().getId())
      .withDetailProfileId(profileToDelete.getProfile().getId())
      .withMasterProfileType(ProfileType.MATCH_PROFILE)
      .withDetailProfileType(ProfileType.MATCH_PROFILE)
      .withOrder(1), Map.of("master", MATCH_PROFILE.value(), "detail", MATCH_PROFILE.value()))
      .statusCode(is(SC_CREATED));

    // assert
    deleteRequest(MATCH_PROFILES_PATH + "/" + profileToDelete.getProfile().getId())
      .statusCode(SC_CONFLICT);
  }

  @DisplayName("should hard-delete profile on DELETE")
  @Test
  void shouldHardDeleteProfileOnDeletion() {
    // arrange
    MatchProfileUpdateDto profile = postRequest(MATCH_PROFILES_PATH, MATCH_PROFILE_2)
      .statusCode(SC_CREATED)
      .extract().body().as(MatchProfileUpdateDto.class);

    // act
    deleteRequest(MATCH_PROFILES_PATH + "/" + profile.getProfile().getId())
      .statusCode(SC_NO_CONTENT);

    // assert
    getRequest(MATCH_PROFILES_PATH + "/" + profile.getProfile().getId())
      .statusCode(SC_NOT_FOUND);
  }

  @DisplayName("should delete associations with detail profiles on DELETE")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldDeleteAssociationsWithDetailProfilesOnDelete() {
    // arrange
    MatchProfileUpdateDto profileToDelete = postRequest(MATCH_PROFILES_PATH, MATCH_PROFILE_1)
      .statusCode(SC_CREATED)
      .extract().body().as(MatchProfileUpdateDto.class);

    // creation detail-profiles
    MatchProfileUpdateDto associatedMatchProfile = postRequest(MATCH_PROFILES_PATH, MATCH_PROFILE_2)
      .statusCode(SC_CREATED)
      .extract().body().as(MatchProfileUpdateDto.class);

    ActionProfileUpdateDto associatedActionProfile = postRequest(ACTION_PROFILES_PATH,
      new ActionProfileUpdateDto().withProfile(new ActionProfile()
        .withName("testAction")
        .withAction(UPDATE)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)))
      .statusCode(SC_CREATED)
      .extract().body().as(ActionProfileUpdateDto.class);

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

    // act: deleting match profile
    deleteRequest(MATCH_PROFILES_PATH + "/" + profileToDelete.getProfile().getId())
      .statusCode(SC_NO_CONTENT);

    // assert: receiving deleted associations
    getRequest(
      ASSOCIATED_PROFILES_PATH + "/" + matchToMatchAssociation.getId(),
      Map.of("master", MATCH_PROFILE.value(), "detail", MATCH_PROFILE.value())
    )
      .statusCode(SC_NOT_FOUND);

    getRequest(
      ASSOCIATED_PROFILES_PATH + "/" + matchToActionAssociation.getId(),
      Map.of("master", MATCH_PROFILE.value(), "detail", ACTION_PROFILE.value())
    )
      .statusCode(SC_NOT_FOUND);
  }

  @DisplayName("should return only non-deleted profiles on GET when deleted parameter is not passed")
  @Test
  void shouldReturnOnlyUnmarkedAsDeletedProfilesOnGetWhenParameterDeletedIsNotPassed() {
    createProfiles();
    MatchProfileUpdateDto matchProfileToDelete = postRequest(MATCH_PROFILES_PATH,
      new MatchProfileUpdateDto().withProfile(new MatchProfile()
        .withName("ProfileToDelete")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)))
      .statusCode(SC_CREATED)
      .extract().body().as(MatchProfileUpdateDto.class);

    deleteRequest(MATCH_PROFILES_PATH + "/" + matchProfileToDelete.getProfile().getId())
      .statusCode(SC_NO_CONTENT);

    getRequest(MATCH_PROFILES_PATH)
      .statusCode(SC_OK)
      .body("totalRecords", is(3))
      .body("matchProfiles*.hidden", everyItem(is(false)));
  }

  @DisplayName("should create profile with match details on POST")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldCreateProfileWithMatchDetailsOnPost() {
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

    // arrange
    MatchProfileUpdateDto createdMatchProfile = postRequest(MATCH_PROFILES_PATH,
      new MatchProfileUpdateDto().withProfile(matchProfile))
      .statusCode(SC_CREATED)
      .extract().body().as(MatchProfileUpdateDto.class);

    MatchProfile receivedMatchProfile = getRequest(MATCH_PROFILES_PATH + "/" + createdMatchProfile.getProfile().getId())
      .statusCode(SC_OK)
      .extract().body().as(MatchProfile.class);

    // assert id and name
    assertThat(receivedMatchProfile.getId()).isEqualTo(createdMatchProfile.getProfile().getId());
    assertThat(receivedMatchProfile.getName()).isEqualTo(createdMatchProfile.getProfile().getName());

    // assert matchDetail
    assertThat(receivedMatchProfile.getMatchDetails()).hasSize(1);
    MatchDetail receivedMatchDetail1 = receivedMatchProfile.getMatchDetails().getFirst();
    assertThat(receivedMatchDetail1.getIncomingRecordType()).isEqualTo(matchDetail.getIncomingRecordType());
    assertThat(receivedMatchDetail1.getExistingRecordType()).isEqualTo(matchDetail.getExistingRecordType());

    // assert incomingMatchExpression
    assertThat(receivedMatchDetail1.getIncomingMatchExpression().getDataValueType())
      .isEqualTo(matchDetail.getIncomingMatchExpression().getDataValueType());

    assertThat(receivedMatchDetail1.getIncomingMatchExpression().getQualifier().getComparisonPart())
      .isEqualTo(matchDetail.getIncomingMatchExpression().getQualifier().getComparisonPart());

    // assert incoming fields
    assertThat(receivedMatchDetail1.getIncomingMatchExpression().getFields()).hasSize(4);
    List<Field> createdIncomingFields = receivedMatchDetail1.getIncomingMatchExpression().getFields();
    for (int i = 0; i < createdIncomingFields.size(); i++) {
      assertThat(createdIncomingFields.get(i).getLabel())
        .isEqualTo(matchDetail.getIncomingMatchExpression().getFields().get(i).getLabel());
      assertThat(createdIncomingFields.get(i).getValue())
        .isEqualTo(matchDetail.getIncomingMatchExpression().getFields().get(i).getValue());
    }

    // assert matchCriterion
    assertThat(receivedMatchDetail1.getMatchCriterion()).isEqualTo(matchDetail.getMatchCriterion());

    // assert existingMatchExpression
    assertThat(receivedMatchDetail1.getExistingMatchExpression().getDataValueType())
      .isEqualTo(matchDetail.getExistingMatchExpression().getDataValueType());
    assertThat(receivedMatchDetail1.getExistingMatchExpression().getFields()).hasSize(1);
    assertThat(receivedMatchDetail1.getExistingMatchExpression().getFields().getFirst().getLabel())
      .isEqualTo(matchDetail.getExistingMatchExpression().getFields().getFirst().getLabel());
    assertThat(receivedMatchDetail1.getExistingMatchExpression().getFields().getFirst().getValue())
      .isEqualTo(matchDetail.getExistingMatchExpression().getFields().getFirst().getValue());
    assertThat(receivedMatchDetail1.getExistingMatchExpression().getQualifier().getComparisonPart())
      .isEqualTo(matchDetail.getExistingMatchExpression().getQualifier().getComparisonPart());
  }

  @DisplayName("should return 422 when POST has non-empty child or parent profile")
  @Test
  void shouldReturnBadRequestWhenChildOrParentIsNotEmptyOnPost() {
    postRequest(MATCH_PROFILES_PATH, MATCH_PROFILE_NOT_EMPTY_CHILD_AND_PARENT)
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Match profile read-only 'child' field should be empty"))
      .body("errors[1].message", is("Match profile read-only 'parent' field should be empty"));
  }

  @DisplayName("should return 422 when PUT has non-empty child or parent profile")
  @Test
  void shouldReturnBadRequestWhenChildOrParentIsNotEmptyOnPut() {
    // arrange
    MatchProfileUpdateDto matchProfile = postRequest(MATCH_PROFILES_PATH, MATCH_PROFILE_2)
      .statusCode(SC_CREATED)
      .extract().body().as(MatchProfileUpdateDto.class);

    // assert
    putRequest(MATCH_PROFILES_PATH + "/" + matchProfile.getProfile().getId(), MATCH_PROFILE_NOT_EMPTY_CHILD_AND_PARENT)
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Match profile read-only 'child' field should be empty"))
      .body("errors[1].message", is("Match profile read-only 'parent' field should be empty"));
  }

  private List<String> createProfiles() {
    List<MatchProfileUpdateDto> matchProfilesToPost = Arrays.asList(MATCH_PROFILE_1, MATCH_PROFILE_2, MATCH_PROFILE_3);
    List<String> ids = new ArrayList<>();
    for (MatchProfileUpdateDto profile : matchProfilesToPost) {
      ids.add(postRequest(MATCH_PROFILES_PATH, profile)
        .statusCode(SC_CREATED).extract().body().as(MatchProfileUpdateDto.class).getProfile().getId());
    }
    return ids;
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private void createProfilesTree(List<String> profilesIds) {
    String nameForProfiles = "tree";
    JobProfileUpdateDto clonedJobProfile1 = JsonObject.mapFrom(JOB_PROFILE_1).mapTo(JobProfileUpdateDto.class);
    ActionProfileUpdateDto clonedActionProfile1 =
      JsonObject.mapFrom(ACTION_PROFILE_1).mapTo(ActionProfileUpdateDto.class);
    MappingProfileUpdateDto clonedMappingProfile1 =
      JsonObject.mapFrom(MAPPING_PROFILE_1).mapTo(MappingProfileUpdateDto.class);
    MappingProfileUpdateDto clonedMappingProfile2 =
      JsonObject.mapFrom(MAPPING_PROFILE_2).mapTo(MappingProfileUpdateDto.class);
    MappingProfileUpdateDto clonedMappingProfile3 =
      JsonObject.mapFrom(MAPPING_PROFILE_3).mapTo(MappingProfileUpdateDto.class);

    final var jobProfiles = Arrays.asList(clonedJobProfile1, clonedJobProfile1, clonedJobProfile1);
    final var actionProfiles = Arrays.asList(clonedActionProfile1, clonedActionProfile1, clonedActionProfile1);
    final var mappingProfiles = Arrays.asList(clonedMappingProfile1, clonedMappingProfile2, clonedMappingProfile3);
    final List<JobProfileUpdateDto> created = new ArrayList<>();
    final List<MappingProfileUpdateDto> createdMappings = new ArrayList<>();
    final List<ActionProfileUpdateDto> createdActions = new ArrayList<>();
    int i;
    i = 0;
    for (MappingProfileUpdateDto profile : mappingProfiles) {
      createdMappings.add(postRequest(MAPPING_PROFILES_PATH, new MappingProfileUpdateDto()
        .withProfile(profile.getProfile().withName(nameForProfiles + i)))
        .statusCode(SC_CREATED).extract().body().as(MappingProfileUpdateDto.class));
      i++;
    }
    i = 0;
    for (ActionProfileUpdateDto action : actionProfiles) {
      createdActions.add(postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
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
        .statusCode(SC_CREATED).extract().body().as(ActionProfileUpdateDto.class));
      i++;
    }
    i = 0;
    for (JobProfileUpdateDto profile : jobProfiles) {
      created.add(postRequest(JOB_PROFILES_PATH, new JobProfileUpdateDto()
        .withProfile(profile.getProfile().withName(nameForProfiles + i))
        .withAddedRelations(List.of(
          new ProfileAssociation()
            .withDetailProfileId(profilesIds.get(i))
            .withDetailProfileType(ProfileType.MATCH_PROFILE)
            .withMasterProfileType(ProfileType.JOB_PROFILE)
            .withOrder(0)
            .withTriggered(false).withReactTo(ReactToType.MATCH),
          new ProfileAssociation()
            .withDetailProfileId(createdActions.get(i).getId())
            .withDetailProfileType(ACTION_PROFILE)
            .withMasterProfileType(MATCH_PROFILE)
            .withOrder(0)
            .withTriggered(false),
          new ProfileAssociation()
            .withDetailProfileId(createdActions.get(i).getId())
            .withDetailProfileType(ACTION_PROFILE)
            .withMasterProfileType(JOB_PROFILE)
            .withOrder(0)
            .withTriggered(false)
        )))
        .statusCode(SC_CREATED).extract().body().as(JobProfileUpdateDto.class));
      i++;
    }
    i = 0;
    for (JobProfileUpdateDto profile : created) {
      profile.setDeletedRelations(Collections.singletonList(new ProfileAssociation()
        .withDetailProfileId(createdActions.get(i).getId())
        .withMasterProfileId(profile.getProfile().getId())
        .withDetailProfileType(ACTION_PROFILE)
        .withMasterProfileType(JOB_PROFILE)
        .withOrder(0)
        .withTriggered(false).withReactTo(ReactToType.MATCH)
      ));
      profile.getAddedRelations().clear();
      putRequest(JOB_PROFILES_PATH + "/" + profile.getProfile().getId(), profile)
        .statusCode(SC_OK);
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
      putRequest(JOB_PROFILES_PATH + "/" + profile.getProfile().getId(), profile)
        .statusCode(SC_OK);
      i++;
    }
  }
}
