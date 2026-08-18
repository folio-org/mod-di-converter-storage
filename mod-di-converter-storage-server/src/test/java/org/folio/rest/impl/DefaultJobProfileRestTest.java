package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.DELETE;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.UPDATE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.MARC_AUTHORITY;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC;
import static org.folio.rest.jaxrs.model.JobProfile.DataType.MARC;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;
import static org.folio.rest.jaxrs.model.ReactToType.MATCH;
import static org.folio.rest.jaxrs.model.ReactToType.NON_MATCH;
import static org.folio.support.TestUtil.JOB_PROFILES_PATH;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import io.vertx.junit5.VertxTestContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.http.HttpStatus;
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
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.support.AbstractRestTest;
import org.hamcrest.Matchers;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultJobProfileRestTest extends AbstractRestTest {

  private static final String DEFAULT_MARC_AUTHORITY_PROFILE_ID = "6eefa4c6-bbf7-4845-ad82-de7fc5abd0e3";
  private static final String DEFAULT_MARC_HOLDINGS_PROFILE_ID = "80898dee-449f-44dd-9c8e-37d5eb469b1d";
  private static final String DEFAULT_DELETE_MARC_AUTHORITY_ACTION_PROFILE_ID = "fabd9a3e-33c3-49b7-864d-c5af830d9990";

  private static Stream<String> defaultJobProfileIds() {
    return Stream.of(
      "d0ebb7b0-2f0f-11eb-adc1-0242ac120002", //OCLC_CREATE_INSTANCE_JOB_PROFILE_ID
      "91f9b8d6-d80e-4727-9783-73fb53e3c786", //OCLC_UPDATE_INSTANCE_JOB_PROFILE_ID
      "80898dee-449f-44dd-9c8e-37d5eb469b1d", //DEFAULT_CREATE_HOLDINGS_AND_SRS_MARC_HOLDINGS_JOB_PROFILE_ID
      "1a338fcd-3efc-4a03-b007-394eeb0d5fb9", //DEFAULT_DELETE_MARC_AUTHORITY_JOB_PROFILE_ID
      "6eefa4c6-bbf7-4845-ad82-de7fc5abd0e3"  //DEFAULT_CREATE_SRS_MARC_AUTHORITY_JOB_PROFILE_ID
    );
  }

  @Override
  protected void clearTables(VertxTestContext testContext) {
    testContext.completeNow();
  }

  @DisplayName("should return 201 Created when posting job profile with delete MARC-Authority action as first action under MARC-Authority match profile")
  @Test
  @SuppressWarnings("checkstyle:LineLength")
  void shouldReturnCreatedOnPostJobProfileWithDeleteMarcAuthorityActionAsFirstActionUnderMarcAuthorityMatchProfile() {
    final var jobProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();
    var actionProfileId = UUID.randomUUID().toString();
    var mappingProfileId = UUID.randomUUID().toString();

    postMappingProfile(mappingProfileId, "Delete MARC-Authority1",
      EntityType.MARC_AUTHORITY, EntityType.MARC_AUTHORITY);
    postActionProfileWithMapping(actionProfileId, "Delete MARC-Authority1",
      DELETE, MARC_AUTHORITY, mappingProfileId);
    postMatchProfile(matchProfileId, "Match MARC-Authority1",
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

    postRequest(JOB_PROFILES_PATH, new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(jobProfileId)
        .withName("Delete MARC-Authority1")
        .withDataType(MARC))
      .withAddedRelations(List.of(jobToMatchAssociation, matchToActionAssociation)))
      .statusCode(SC_CREATED);
  }

  @DisplayName("should return 201 Created when posting job profile with default delete MARC-Authority action as first action under MARC-Authority match profile")
  @Test
  @SuppressWarnings("checkstyle:LineLength")
  void shouldReturnCreatedOnPostJobProfileWithDefaultDeleteMarcAuthorityActionAsFirstActionUnderMarcAuthorityMatchProfile() {
    var jobProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();

    postMatchProfile(matchProfileId, "Match MARC-Authority2",
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

    postRequest(JOB_PROFILES_PATH, new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(jobProfileId)
        .withName("Delete MARC-Authority2")
        .withDataType(MARC))
      .withAddedRelations(List.of(jobToMatchAssociation, matchToActionAssociation)))
      .statusCode(SC_CREATED);
  }

  @DisplayName("should return 422 Unprocessable Entity when posting job profile with delete MARC-Authority under non-match branch")
  @Test
  void shouldReturnUnprocessableEntityOnPostJobProfileWithDeleteMarcAuthorityUnderNonMatchBranch() {
    var jobProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();
    var actionProfileId = UUID.randomUUID().toString();

    postMatchProfile(matchProfileId, "Match Marc-Authority3",
      EntityType.MARC_AUTHORITY, EntityType.MARC_AUTHORITY);
    postActionProfile(actionProfileId, "Delete MARC-Authority3", DELETE, MARC_AUTHORITY);

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
    assertThat(matchToActionAssociation.getReactTo()).isEqualTo(NON_MATCH);

    postRequest(JOB_PROFILES_PATH, new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(jobProfileId)
        .withName("Delete MARC-Authority3")
        .withDataType(MARC))
      .withAddedRelations(List.of(jobToMatchAssociation, matchToActionAssociation)))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Delete MARC-AUTHORITY action profile must be placed in the for-matches branch "
             + "of a match profile for MARC-AUTHORITY to MARC-AUTHORITY matching"))
      ));
  }

  @DisplayName("should return 422 when posting job profile if delete MARC-Authority is not first in match branch")
  @Test
  void shouldReturnUnprocessableEntityOnPostJobProfileIfDeleteMarcAuthorityNotFirstInMatchBranch() {
    final var jobProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();
    var updateActionProfileId = UUID.randomUUID().toString();
    var deleteActionProfileId = UUID.randomUUID().toString();

    postMatchProfile(matchProfileId, "Match Marc-Authority4",
      EntityType.MARC_AUTHORITY, EntityType.MARC_AUTHORITY);
    postActionProfile(updateActionProfileId, "Update Marc-Authority4", UPDATE, MARC_AUTHORITY);
    postActionProfile(deleteActionProfileId, "Delete MARC-Authority4", DELETE, MARC_AUTHORITY);

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

    postRequest(JOB_PROFILES_PATH, new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(jobProfileId)
        .withName("Delete MARC-Authority4")
        .withDataType(MARC))
      .withAddedRelations(List.of(
        jobToMatchAssociation, matchToUpdateActionAssociation, matchToDeleteActionAssociation)))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Delete MARC-AUTHORITY action profile cannot be placed next to other "
             + "action profiles in the for-matches branch"))));
  }

  @DisplayName("should return 422 Unprocessable Entity when posting job profile with delete MARC-Authority under non-MARC-Authority match profile")
  @Test
  @SuppressWarnings("checkstyle:LineLength")
  void shouldReturnUnprocessableEntityOnPostJobProfileWithDeleteMarcAuthorityUnderNonMarcAuthorityMatchProfile() {
    var jobProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();
    var actionProfileId = UUID.randomUUID().toString();

    postMatchProfile(matchProfileId, "Match Marc-Bib5",
      EntityType.MARC_BIBLIOGRAPHIC, EntityType.MARC_BIBLIOGRAPHIC);
    postActionProfile(actionProfileId, "Delete MARC-Authority5", DELETE, MARC_AUTHORITY);

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

    postRequest(JOB_PROFILES_PATH, new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(jobProfileId)
        .withName("Delete MARC-Authority5")
        .withDataType(MARC))
      .withAddedRelations(List.of(jobToMatchAssociation, matchToActionAssociation)))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Delete MARC-AUTHORITY action profile must be placed in the for-matches branch "
             + "of a match profile for MARC-AUTHORITY to MARC-AUTHORITY matching"))
      ));
  }

  @DisplayName("should return 200 OK when putting job profile with delete MARC-Authority as first action under MARC-Authority match profile")
  @Test
  void shouldReturnOkOnPutJobProfileWithDeleteMarcAuthorityAsFirstActionUnderMarcAuthorityMatchProfile() {
    var jobProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();
    var updateActionProfileId = UUID.randomUUID().toString();
    var updateMappingProfileId = UUID.randomUUID().toString();
    var deleteActionProfileId = UUID.randomUUID().toString();
    var deleteMappingProfileId = UUID.randomUUID().toString();

    var jobProfileToUpdate = postBaseJobProfileWithMatchAndUpdateAction(jobProfileId, matchProfileId,
      updateActionProfileId, MARC_AUTHORITY,
      updateMappingProfileId, EntityType.MARC_AUTHORITY, EntityType.MARC_AUTHORITY);
    postMappingProfile(deleteMappingProfileId, "Delete MARC-Authority6",
      EntityType.MARC_AUTHORITY, EntityType.MARC_AUTHORITY);
    postActionProfileWithMapping(deleteActionProfileId, "Delete MARC-Authority6",
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

    putRequest(JOB_PROFILES_PATH + "/" + jobProfileId, new JobProfileUpdateDto()
      .withProfile(jobProfileToUpdate.getProfile())
      .withAddedRelations(List.of(matchToDeleteActionAssociation))
      .withDeletedRelations(List.of(matchToUpdateActionAssociation)))
      .statusCode(SC_OK);
  }

  @DisplayName("should return 422 when putting job profile with delete MARC-Authority under non-match branch")
  @Test
  void shouldReturnUnprocessableEntityOnPutJobProfileWithDeleteMarcAuthorityUnderNonMatchBranch() {
    var jobProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();
    var actionProfileId = UUID.randomUUID().toString();
    var updateMappingProfileId = UUID.randomUUID().toString();
    var deleteActionProfileId = UUID.randomUUID().toString();
    var deleteMappingProfileId = UUID.randomUUID().toString();

    var jobProfileToUpdate = postBaseJobProfileWithMatchAndUpdateAction(jobProfileId, matchProfileId,
      actionProfileId, MARC_AUTHORITY, updateMappingProfileId, EntityType.MARC_AUTHORITY, EntityType.MARC_AUTHORITY);
    postMappingProfile(deleteMappingProfileId, "Delete MARC-Authority mapping7",
      EntityType.MARC_AUTHORITY, EntityType.MARC_AUTHORITY);
    postActionProfileWithMapping(deleteActionProfileId, "Delete MARC-Authority7",
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

    putRequest(JOB_PROFILES_PATH + "/" + jobProfileId, new JobProfileUpdateDto()
      .withProfile(jobProfileToUpdate.getProfile())
      .withAddedRelations(List.of(matchToDeleteActionAssociation))
      .withDeletedRelations(List.of(matchToUpdateActionAssociation)))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Delete MARC-AUTHORITY action profile must be placed in the for-matches branch "
             + "of a match profile for MARC-AUTHORITY to MARC-AUTHORITY matching"))
      ));
  }

  @DisplayName("should return 422 when putting job profile if delete MARC-Authority is not first in match branch")
  @Test
  void shouldReturnUnprocessableEntityOnPutJobProfileIfDeleteMarcAuthorityNotFirstInMatchBranch() {
    var jobProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();
    var updateActionProfileId = UUID.randomUUID().toString();
    var updateMappingProfileId = UUID.randomUUID().toString();
    var deleteActionProfileId = UUID.randomUUID().toString();

    var jobProfileToUpdate = postBaseJobProfileWithMatchAndUpdateAction(jobProfileId, matchProfileId,
      updateActionProfileId, MARC_AUTHORITY, updateMappingProfileId,
      EntityType.MARC_AUTHORITY, EntityType.MARC_AUTHORITY);
    postActionProfile(deleteActionProfileId, "Delete MARC-Authority8", DELETE, MARC_AUTHORITY);

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

    putRequest(JOB_PROFILES_PATH + "/" + jobProfileId, jobProfileToUpdate
      .withAddedRelations(List.of(matchToDeleteActionAssociation))
      .withDeletedRelations(List.of(matchToUpdateActionAssociation)))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Delete MARC-AUTHORITY action profile cannot be placed next "
             + "to other action profiles in the for-matches branch"))));
  }

  @DisplayName("should return 422 when putting job profile with delete MARC-Authority under non-MARC-Authority match")
  @Test
  void shouldReturnUnprocessableEntityOnPutJobProfileWithDeleteMarcAuthorityUnderNonMarcAuthorityMatchProfile() {
    var jobProfileId = UUID.randomUUID().toString();
    var marcBibMatchProfileId = UUID.randomUUID().toString();
    var actionProfileId = UUID.randomUUID().toString();
    var updateMappingProfileId = UUID.randomUUID().toString();
    var deleteActionProfileId = UUID.randomUUID().toString();

    var jobProfileToUpdate = postBaseJobProfileWithMatchAndUpdateAction(jobProfileId, marcBibMatchProfileId,
      actionProfileId, MARC_BIBLIOGRAPHIC, updateMappingProfileId,
      EntityType.MARC_BIBLIOGRAPHIC, EntityType.MARC_BIBLIOGRAPHIC);
    postActionProfile(deleteActionProfileId, "Delete MARC-Authority9", DELETE, MARC_AUTHORITY);

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

    putRequest(JOB_PROFILES_PATH + "/" + jobProfileId, jobProfileToUpdate
      .withAddedRelations(List.of(marcBibMatchToDeleteActionAssociation))
      .withDeletedRelations(List.of(matchToUpdateActionAssociation))
    )
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Delete MARC-AUTHORITY action profile must be placed in the for-matches branch "
             + "of a match profile for MARC-AUTHORITY to MARC-AUTHORITY matching"))
      ));
  }

  @DisplayName("should return default profiles list on GET")
  @Test
  void shouldReturnDefaultProfilesListOnGet() {
    getRequest(JOB_PROFILES_PATH)
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", greaterThanOrEqualTo(6));
  }

  @DisplayName("should return 400 Bad Request when deleting a default job profile")
  @ParameterizedTest
  @MethodSource("defaultJobProfileIds")
  void shouldReturnBadRequestOnDeleteDefaultProfiles(String id) {
    deleteRequest(JOB_PROFILES_PATH + "/" + id)
      .statusCode(SC_BAD_REQUEST);
  }

  @DisplayName("should return 400 Bad Request when putting a default job profile")
  @ParameterizedTest
  @MethodSource("defaultJobProfileIds")
  void shouldReturnBadRequestOnPutDefaultProfiles(String id) {
    putRequest(JOB_PROFILES_PATH + "/" + id, new JobProfileUpdateDto()
      .withProfile(new JobProfile().withName("Bla")
        .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum", "dolor")))
        .withDataType(MARC)))
      .statusCode(SC_BAD_REQUEST);
  }

  @DisplayName("should return hidden profiles when GET includes showHidden=true")
  @Test
  void shouldReturnWithHiddenProfilesOnGet() {
    getRequest(JOB_PROFILES_PATH + "?showHidden=true")
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", greaterThanOrEqualTo(7));
  }

  @DisplayName("should return MARC Authority profile on GET by id")
  @Test
  void shouldReturnMarcAuthorityProfileOnGetById() {
    var profile = getJobProfile(DEFAULT_MARC_AUTHORITY_PROFILE_ID);
    assertThat(profile.getName()).isEqualTo("Default - Create SRS MARC Authority");
    assertThat(profile.getDataType()).isEqualTo(JobProfile.DataType.MARC);
  }

  @DisplayName("should return MARC Holdings profile on GET by id")
  @Test
  void shouldReturnMarcHoldingsProfileOnGetById() {
    var profile = getJobProfile(DEFAULT_MARC_HOLDINGS_PROFILE_ID);
    assertThat(profile.getName()).isEqualTo("Default - Create Holdings and SRS MARC Holdings");
    assertThat(profile.getDataType()).isEqualTo(JobProfile.DataType.MARC);
  }

  @DisplayName("should add and remove tags on a default profile")
  @Test
  void shouldAddAndRemoveTagsDefaultProfile() {
    var tags = new Tags().withTagList(Arrays.asList("Lorem", "ipsum"));
    var profile = getJobProfile(DEFAULT_MARC_AUTHORITY_PROFILE_ID);
    // Add tags to default profile
    putRequest(JOB_PROFILES_PATH + "/" + DEFAULT_MARC_AUTHORITY_PROFILE_ID,
      new JobProfileUpdateDto().withProfile(profile.withTags(tags)))
      .statusCode(HttpStatus.SC_OK)
      .body("tags.tagList", Is.is(tags.getTagList()));

    profile = getJobProfile(DEFAULT_MARC_AUTHORITY_PROFILE_ID);
    // Delete tags from default profile
    putRequest(JOB_PROFILES_PATH + "/" + DEFAULT_MARC_AUTHORITY_PROFILE_ID,
      new JobProfileUpdateDto().withProfile(profile.withTags(new Tags().withTagList(Collections.emptyList()))))
      .statusCode(HttpStatus.SC_OK)
      .body("tags.tagList", Is.is(Matchers.empty()));
  }

  private void postMappingProfile(String id, String name, EntityType incomingRecordType,
                                  EntityType existingRecordType) {
    postMappingProfile(new MappingProfileUpdateDto()
      .withProfile(new MappingProfile()
        .withId(id)
        .withName(name)
        .withIncomingRecordType(incomingRecordType)
        .withExistingRecordType(existingRecordType)));
  }

  private void postActionProfile(String id, String name, ActionProfile.Action action,
                                 ActionProfile.FolioRecord recordType) {
    postActionProfile(new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withId(id)
        .withName(name)
        .withAction(action)
        .withFolioRecord(recordType)));
  }

  private void postActionProfileWithMapping(String actionProfileId, String name, ActionProfile.Action action,
                                            ActionProfile.FolioRecord recordType, String mappingProfileId) {
    postActionProfile(new ActionProfileUpdateDto()
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
        .withOrder(0))));
  }

  private void postMatchProfile(String id, String name, EntityType incomingRecordType, EntityType existingRecordType) {
    postMatchProfile(new MatchProfileUpdateDto()
      .withProfile(new MatchProfile()
        .withId(id)
        .withName(name)
        .withIncomingRecordType(incomingRecordType)
        .withExistingRecordType(existingRecordType)));
  }

  private JobProfileUpdateDto postBaseJobProfileWithMatchAndUpdateAction(
    String jobProfileId, String matchProfileId, String actionId, ActionProfile.FolioRecord folioRecordType,
    String mappingId,
    EntityType incomingRecordType, EntityType existingRecordType) {

    postMappingProfile(mappingId, "Update " + incomingRecordType.value() + jobProfileId, incomingRecordType,
      existingRecordType);
    postActionProfileWithMapping(actionId, "Update " + incomingRecordType.value() + jobProfileId, UPDATE,
      folioRecordType, mappingId);
    postMatchProfile(matchProfileId, "Match " + incomingRecordType.value() + jobProfileId, incomingRecordType,
      existingRecordType);

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

    return postJobProfile(new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(jobProfileId)
        .withName("Test job profile" + jobProfileId)
        .withDataType(MARC))
      .withAddedRelations(List.of(jobToMatchAssociation, matchToActionAssociation)));
  }
}
