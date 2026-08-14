package org.folio.rest.impl;

import static java.util.Collections.emptyList;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.CREATE;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.MODIFY;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.UPDATE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.INSTANCE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC;
import static org.folio.rest.jaxrs.model.JobProfile.DataType.DELIMITED;
import static org.folio.rest.jaxrs.model.JobProfile.DataType.MARC;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MAPPING_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;
import static org.folio.rest.jaxrs.model.ReactToType.MATCH;
import static org.folio.rest.jaxrs.model.ReactToType.NON_MATCH;
import static org.folio.support.ProfileFixtures.JOB_PROFILE_1;
import static org.folio.support.ProfileFixtures.JOB_PROFILE_2;
import static org.folio.support.ProfileFixtures.JOB_PROFILE_3;
import static org.folio.support.ProfileFixtures.JOB_PROFILE_4;
import static org.folio.support.ProfileFixtures.JOB_PROFILE_NOT_EMPTY_CHILD_AND_PARENT;
import static org.folio.support.ProfileFixtures.JOB_PROFILE_UUID;
import static org.folio.support.TestUtil.ACTION_PROFILES_PATH;
import static org.folio.support.TestUtil.ASSOCIATED_PROFILES_PATH;
import static org.folio.support.TestUtil.JOB_PROFILES_PATH;
import static org.folio.support.TestUtil.MAPPING_PROFILES_PATH;
import static org.folio.support.TestUtil.MATCH_PROFILES_PATH;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.rest.impl.association.wrapper.MatchProfileWrapper;
import org.folio.rest.impl.association.wrapper.ProfileWrapper;
import org.folio.rest.jaxrs.model.ActionProfile;
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
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.Tags;
import org.folio.support.AbstractRestTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobProfileRestTest extends AbstractRestTest {

  @DisplayName("should return empty list on GET when no profiles exist")
  @Test
  void shouldReturnEmptyListOnGet() {
    getRequest(JOB_PROFILES_PATH)
      .statusCode(SC_OK)
      .body("totalRecords", is(0))
      .body("jobProfiles", empty());
  }

  @DisplayName("should return all profiles on GET")
  @Test
  void shouldReturnAllProfilesOnGet() {
    createProfiles();
    getRequest(JOB_PROFILES_PATH, Map.of("withRelations", "true"))
      .statusCode(SC_OK)
      .body("totalRecords", is(3))
      .body("jobProfiles*.hidden", everyItem(is(false)));
  }

  @DisplayName("should return committed profiles on GET when queried by last name")
  @Test
  void shouldReturnCommittedProfilesOnGetWithQueryByLastName() {
    createProfiles();
    getRequest(JOB_PROFILES_PATH, Map.of("query", "userInfo.lastName=Doe"))
      .statusCode(SC_OK)
      .body("totalRecords", is(3))
      .body("jobProfiles*.hidden", everyItem(is(false)))
      .body("jobProfiles*.userInfo.lastName", everyItem(is("Doe")));
  }

  @DisplayName("should return ipsum-tagged profiles on GET when queried by tag")
  @Test
  void shouldReturnIpsumTaggedProfilesOnGetWithQueryByTag() {
    createProfiles();
    getRequest(JOB_PROFILES_PATH, Map.of("query", "tags.tagList=/respectCase/respectAccents \\\"ipsum\\\""))
      .statusCode(SC_OK)
      .body("totalRecords", is(2))
      .body("jobProfiles*.hidden", everyItem(is(false)))
      .body("jobProfiles.get(0).tags.tagList", hasItem("ipsum"))
      .body("jobProfiles.get(1).tags.tagList", hasItem("ipsum"));
  }

  @DisplayName("should return limited collection on GET when limit is specified")
  @Test
  void shouldReturnLimitedCollectionOnGetWithLimit() {
    createProfiles();
    getRequest(JOB_PROFILES_PATH, Map.of("limit", 2))
      .statusCode(SC_OK)
      .body("jobProfiles.size()", is(2))
      .body("jobProfiles*.hidden", everyItem(is(false)))
      .body("totalRecords", is(3));
  }

  @DisplayName("should return profiles sorted by creation date descending when sortBy is specified")
  @Test
  void shouldReturnSortedProfilesOnGetWhenSortByIsSpecified() {
    createProfiles();
    var jobProfileList = getRequest(JOB_PROFILES_PATH,
      Map.of("query", "(cql.allRecords=1) sortBy metadata.createdDate/sort.descending"))
      .statusCode(SC_OK)
      .body("totalRecords", is(3))
      .extract().body().as(JobProfileCollection.class).getJobProfiles();

    assertThat(jobProfileList.get(0).getMetadata().getCreatedDate())
      .isAfter(jobProfileList.get(1).getMetadata().getCreatedDate());
    assertThat(jobProfileList.get(1).getMetadata().getCreatedDate())
      .isAfter(jobProfileList.get(2).getMetadata().getCreatedDate());
  }

  @DisplayName("should return bad request on POST when body is invalid")
  @Test
  void shouldReturnBadRequestOnPost() {
    createProfiles();
    postRequest(JOB_PROFILES_PATH, new JsonObject().toString())
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should create profile on POST")
  @Test
  void shouldCreateProfileOnPost() {
    var jobProfile = createJobProfile(JOB_PROFILE_1, "testActionCreate", "testMappingCreate");

    postRequest(JOB_PROFILES_PATH, jobProfile)
      .statusCode(SC_CREATED)
      .body("profile.name", is(jobProfile.getProfile().getName()))
      .body("profile.tags.tagList", is(jobProfile.getProfile().getTags().getTagList()))
      .body("profile.userInfo.lastName", is("Doe"))
      .body("profile.userInfo.firstName", is("Jane"))
      .body("profile.userInfo.userName", is("@janedoe"))
      .body("profile.dataType", is(jobProfile.getProfile().getDataType().value()));

    postRequest(JOB_PROFILES_PATH, jobProfile)
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Job profile 'Bla' already exists"));
  }

  @DisplayName("should create profile with given ID on POST")
  @Test
  void shouldCreateProfileWithGivenIdOnPost() {
    var jobProfile = createJobProfile(JOB_PROFILE_4, "actionCreate", "mappingCreate");
    postRequest(JOB_PROFILES_PATH, jobProfile)
      .statusCode(SC_CREATED)
      .body("profile.name", is(jobProfile.getProfile().getName()))
      .body("profile.tags.tagList", is(jobProfile.getProfile().getTags().getTagList()))
      .body("profile.userInfo.lastName", is("Doe"))
      .body("profile.userInfo.firstName", is("Jane"))
      .body("profile.userInfo.userName", is("@janedoe"))
      .body("profile.dataType", is(jobProfile.getProfile().getDataType().value()));

    var jobProfile2 = createJobProfile(new JobProfileUpdateDto()
      .withProfile(new JobProfile().withId(JOB_PROFILE_UUID)
        .withName("GOA")
        .withTags(new Tags().withTagList(Arrays.asList("lorem", "ipsum")))
        .withDataType(MARC)), "createAction2", "mappingCreate2");

    postRequest(JOB_PROFILES_PATH, jobProfile2)
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Job profile with id 'GOA' already exists"));
  }

  @DisplayName("should return bad request on POST when job profile has no data type")
  @Test
  void shouldReturnBadRequestOnPostJobProfileWithoutDataType() {
    var jobProfileWithoutDataType = new JsonObject()
      .put("name", "Bla");

    postRequest(JOB_PROFILES_PATH, jobProfileWithoutDataType.encode())
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should return bad request on POST when job profile has an invalid field")
  @Test
  void shouldReturnBadRequestOnPostJobProfileWithInvalidField() {
    var jobProfile = new JsonObject()
      .put("name", "Bla")
      .put("dataType", MARC)
      .put("invalidField", "value");

    postRequest(JOB_PROFILES_PATH, jobProfile.encode())
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should return bad request on POST when job profile links UPDATE action without match profile")
  @Test
  void shouldReturnBadRequestOnPostJobProfileWithUpdateActionProfileWithoutMatchProfile() {
    var actionProfileId = UUID.randomUUID().toString();
    var jobId = UUID.randomUUID().toString();

    postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withName("testAction")
        .withAction(UPDATE)
        .withFolioRecord(INSTANCE)
        .withId(actionProfileId)))
      .statusCode(SC_CREATED);

    var invalidAssociation = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileId)
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobId);

    postRequest(JOB_PROFILES_PATH, new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(jobId)
        .withName("testJob")
        .withDataType(MARC))
      .withAddedRelations(List.of(invalidAssociation)))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is(String.format("ActionProfile with id '%s' and action UPDATE requires linked MatchProfile",
            actionProfileId)))
      ));
  }

  @DisplayName("should return bad request on POST when job profile has a standalone MODIFY action")
  @Test
  void shouldReturnBadRequestOnPostJobProfileWithStandaloneModifyAction() {
    var actionProfileId = UUID.randomUUID().toString();
    var jobId = UUID.randomUUID().toString();

    postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withName("testAction")
        .withAction(MODIFY)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)
        .withId(actionProfileId)))
      .statusCode(SC_CREATED);

    var invalidAssociation = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileId)
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobId);

    postRequest(JOB_PROFILES_PATH, new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(jobId)
        .withName("testJob")
        .withDataType(MARC))
      .withAddedRelations(List.of(invalidAssociation)))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Modify action cannot be used as a standalone action"))
      ));
  }

  @DisplayName("should return bad request on POST when job profile has two standalone MODIFY actions")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldReturnBadRequestOnPostJobProfileWithTwoModifyActions() {
    var actionProfileId = UUID.randomUUID().toString();
    var jobId = UUID.randomUUID().toString();

    var actionProfileModify = postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withName("testActionModify")
        .withAction(MODIFY)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)
        .withId(actionProfileId)))
      .statusCode(SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    var mappingProfileIdModify = UUID.randomUUID().toString();
    postRequest(MAPPING_PROFILES_PATH, new MappingProfileUpdateDto()
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
      .statusCode(SC_CREATED)
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

    postRequest(JOB_PROFILES_PATH, new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(jobId)
        .withName("testJob")
        .withDataType(MARC))
      .withAddedRelations(List.of(invalidAssociation1, invalidAssociation2)))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Modify action cannot be used as a standalone action"))
      ));
  }

  @DisplayName("should return bad request on PUT when job profile has a standalone MODIFY action")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldReturnBadRequestOnPutJobProfileWithStandaloneModifyAction() {
    var actionProfileIdCreate = UUID.randomUUID().toString();
    var actionProfileIdModify = UUID.randomUUID().toString();

    var jobId = UUID.randomUUID().toString();

    var actionProfileModify = postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withName("testActionModify")
        .withAction(MODIFY)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)
        .withId(actionProfileIdModify)))
      .statusCode(SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    var mappingProfileIdModify = UUID.randomUUID().toString();
    postRequest(MAPPING_PROFILES_PATH, new MappingProfileUpdateDto()
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
      .statusCode(SC_CREATED)
      .extract().as(MappingProfileUpdateDto.class);

    var actionProfileCreate = postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withName("testActionCreate")
        .withId(actionProfileIdCreate)
        .withAction(CREATE)
        .withFolioRecord(INSTANCE)))
      .statusCode(SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    var mappingProfileIdCreate = UUID.randomUUID().toString();
    postRequest(MAPPING_PROFILES_PATH, new MappingProfileUpdateDto()
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
      .statusCode(SC_CREATED)
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

    var jobProfileUpdateDto = postRequest(JOB_PROFILES_PATH, new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(jobId)
        .withName("testJob")
        .withDataType(MARC))
      .withAddedRelations(List.of(validAssociation1, validAssociation2)))
      .statusCode(SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    var deleteAssociation = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileCreate.getId())
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobId)
      .withMasterWrapperId(jobProfileUpdateDto.getAddedRelations().get(1).getMasterWrapperId())
      .withDetailWrapperId(jobProfileUpdateDto.getAddedRelations().get(1).getDetailWrapperId());

    putRequest(JOB_PROFILES_PATH + "/" + jobId, new JobProfileUpdateDto()
      .withId(jobId)
      .withProfile(new JobProfile()
        .withId(jobId)
        .withName("testJob")
        .withDataType(MARC))
      .withDeletedRelations(List.of(deleteAssociation)))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Modify action cannot be used as a standalone action"))
      ));
  }

  @DisplayName("should return bad request on PUT when job profile has standalone MODIFY action after match")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldReturnBadRequestOnPutJobProfileWithStandaloneModifyActionAfterMatch() {
    var actionProfileIdCreate = UUID.randomUUID().toString();
    var actionProfileIdModify = UUID.randomUUID().toString();

    var jobId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();

    postRequest(MATCH_PROFILES_PATH, new MatchProfileUpdateDto()
      .withProfile(new MatchProfile()
        .withId(matchProfileId)
        .withName("testMatch")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE)))
      .statusCode(SC_CREATED);

    ActionProfileUpdateDto actionProfileModify = postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withName("testActionModify")
        .withAction(MODIFY)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)
        .withId(actionProfileIdModify)))
      .statusCode(SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    String mappingProfileIdModify = UUID.randomUUID().toString();
    postRequest(MAPPING_PROFILES_PATH, new MappingProfileUpdateDto()
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
      .statusCode(SC_CREATED)
      .extract().as(MappingProfileUpdateDto.class);

    ActionProfileUpdateDto actionProfileCreate = postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withName("testActionCreate")
        .withId(actionProfileIdCreate)
        .withAction(UPDATE)
        .withFolioRecord(INSTANCE)))
      .statusCode(SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    String mappingProfileIdCreate = UUID.randomUUID().toString();
    postRequest(MAPPING_PROFILES_PATH, new MappingProfileUpdateDto()
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
      .statusCode(SC_CREATED)
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

    JobProfileUpdateDto jobProfileUpdateDto = postRequest(JOB_PROFILES_PATH, new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(jobId)
        .withName("testJob")
        .withDataType(MARC))
      .withAddedRelations(List.of(validAssociation1, validAssociation2, validAssociation3)))
      .statusCode(SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    var deleteAssociation = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileCreate.getId())
      .withMasterProfileType(MATCH_PROFILE)
      .withMasterProfileId(matchProfileId)
      .withMasterWrapperId(jobProfileUpdateDto.getAddedRelations().get(1).getMasterWrapperId())
      .withDetailWrapperId(jobProfileUpdateDto.getAddedRelations().get(1).getDetailWrapperId());

    putRequest(JOB_PROFILES_PATH + "/" + jobId, new JobProfileUpdateDto()
      .withId(jobId)
      .withProfile(new JobProfile()
        .withId(jobId)
        .withName("testJob")
        .withDataType(MARC))
      .withDeletedRelations(List.of(deleteAssociation)))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Modify action cannot be used right after a Match"))
      ));
  }

  @DisplayName("should return bad request on POST when job profile has standalone MODIFY action after match")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldReturnBadRequestOnPostJobProfileWithStandaloneModifyActionAfterMatch() {
    var actionProfileId = UUID.randomUUID().toString();
    var matchProfileId = UUID.randomUUID().toString();
    var jobId = UUID.randomUUID().toString();

    postRequest(MATCH_PROFILES_PATH, new MatchProfileUpdateDto()
      .withProfile(new MatchProfile()
        .withId(matchProfileId)
        .withName("testMatch")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE)))
      .statusCode(SC_CREATED);

    postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withName("testAction")
        .withAction(MODIFY)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)
        .withId(actionProfileId)))
      .statusCode(SC_CREATED);

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

    postRequest(JOB_PROFILES_PATH, new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(jobId)
        .withName("testJob")
        .withDataType(MARC))
      .withAddedRelations(List.of(invalidAssociationMatchToJobProfile, invalidAssociationActionToMatch)))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Modify action cannot be used right after a Match"))
      ));
  }

  @DisplayName("should return not found on POST when job profile links non-existent profiles")
  @Test
  void shouldReturnNotFoundOnPostJobProfileWithInvalidLinkedProfiles() {
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

    postRequest(JOB_PROFILES_PATH, new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(jobIdWithAction)
        .withName("testJob")
        .withDataType(MARC))
      .withAddedRelations(List.of(invalidActionAssociation)))
      .statusCode(SC_NOT_FOUND);

    postRequest(JOB_PROFILES_PATH, new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(jobIdWithMatch)
        .withName("testJob")
        .withDataType(MARC))
      .withAddedRelations(List.of(invalidMatchAssociation)))
      .statusCode(SC_NOT_FOUND);
  }

  @DisplayName("should return bad request on PUT when body is invalid")
  @Test
  void shouldReturnBadRequestOnPut() {
    putRequest(JOB_PROFILES_PATH + "/" + UUID.randomUUID(), new JsonObject().toString())
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should return not found on PUT when profile does not exist")
  @Test
  void shouldReturnNotFoundOnPut() {
    putRequest(JOB_PROFILES_PATH + "/" + UUID.randomUUID(), JOB_PROFILE_2)
      .statusCode(SC_NOT_FOUND);
  }

  @DisplayName("should update profile on PUT")
  @Test
  void shouldUpdateProfileOnPut() {
    JobProfileUpdateDto jobProfile2 = createJobProfile(JOB_PROFILE_2, "createAction", "mappingCreate");
    JobProfileUpdateDto jobProfile = postRequest(JOB_PROFILES_PATH, jobProfile2)
      .statusCode(SC_CREATED)
      .extract().body().as(JobProfileUpdateDto.class);

    jobProfile.getProfile().setDescription("test");
    jobProfile.getProfile().setDataType(DELIMITED);
    jobProfile.getAddedRelations().getFirst().setId(UUID.randomUUID().toString());

    putRequest(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId(), jobProfile)
      .statusCode(SC_OK)
      .body("id", is(jobProfile.getProfile().getId()))
      .body("name", is(jobProfile.getProfile().getName()))
      .body("description", is("test"))
      .body("tags.tagList", is(jobProfile.getProfile().getTags().getTagList()))
      .body("userInfo.lastName", is("Doe"))
      .body("userInfo.firstName", is("Jane"))
      .body("userInfo.userName", is("@janedoe"))
      .body("dataType", is(jobProfile.getProfile().getDataType().value()));
  }

  @DisplayName("should update profile associations on PUT")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldUpdateProfileAssociationsOnPut() {
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

    JobProfileUpdateDto jobProfileToUpdate = postRequest(JOB_PROFILES_PATH, jobProfile)
      .statusCode(SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    putRequest(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId(), jobProfileToUpdate
      .withAddedRelations(null)
      .withDeletedRelations(List.of(jobProfile.getAddedRelations().getFirst())))
      .statusCode(SC_OK)
      .body("id", is(jobProfileToUpdate.getProfile().getId()))
      .body("name", is(jobProfileToUpdate.getProfile().getName()))
      .body("tags.tagList", is(jobProfile.getProfile().getTags().getTagList()))
      .body("userInfo.lastName", is("Doe"))
      .body("userInfo.firstName", is("Jane"))
      .body("userInfo.userName", is("@janedoe"))
      .body("dataType", is(jobProfile.getProfile().getDataType().value()))
      .extract().body().asPrettyString();

    getRequest(JOB_PROFILES_PATH + "/" + jobProfileToUpdate.getProfile().getId() + "?withRelations=true")
      .statusCode(SC_OK)
      .body("id", is(jobProfileToUpdate.getProfile().getId()))
      .body("name", is(jobProfileToUpdate.getProfile().getName()))
      .body("userInfo.lastName", is("Doe"))
      .body("userInfo.firstName", is("Jane"))
      .body("userInfo.userName", is("@janedoe"))
      .body("dataType", is(jobProfile.getProfile().getDataType().value()))
      .body("parentProfiles", is(empty()));
  }

  @DisplayName("should return bad request on PUT when job profile has an invalid field")
  @Test
  void shouldReturnBadRequestOnPutJobProfileWithInvalidField() {
    JobProfileUpdateDto jobProfile2 = createJobProfile(JOB_PROFILE_2, "createAction", "createMapping");
    JobProfileUpdateDto jobProfile = postRequest(JOB_PROFILES_PATH, jobProfile2)
      .statusCode(SC_CREATED)
      .extract().body().as(JobProfileUpdateDto.class);

    JsonObject jobProfileJson = JsonObject.mapFrom(jobProfile)
      .put("invalidField", "value");

    putRequest(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId(), jobProfileJson.encode())
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should return not found on GET by ID when profile does not exist")
  @Test
  void shouldReturnNotFoundOnGetById() {
    getRequest(JOB_PROFILES_PATH + "/" + UUID.randomUUID())
      .statusCode(SC_NOT_FOUND);
  }

  @DisplayName("should return profile on GET by ID")
  @Test
  void shouldReturnProfileOnGetById() {
    JobProfileUpdateDto jobProfile3 = createJobProfile(JOB_PROFILE_3, "createAction", "createMapping");
    JobProfileUpdateDto jobProfile = postRequest(JOB_PROFILES_PATH, jobProfile3)
      .statusCode(SC_CREATED)
      .extract().body().as(JobProfileUpdateDto.class);

    getRequest(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId())
      .statusCode(SC_OK)
      .body("id", is(jobProfile.getProfile().getId()))
      .body("name", is(jobProfile.getProfile().getName()))
      .body("tags.tagList", is(jobProfile.getProfile().getTags().getTagList()))
      .body("userInfo.lastName", is("Doe"))
      .body("userInfo.firstName", is("Jane"))
      .body("userInfo.userName", is("@janedoe"))
      .body("dataType", is(jobProfile.getProfile().getDataType().value()));
  }

  @DisplayName("should return bad request on POST when job profile has empty associations")
  @Test
  void shouldReturnBadRequestOnPostJobProfileWithEmptyAssociations() {
    postRequest(JOB_PROFILES_PATH, JOB_PROFILE_3.withAddedRelations(emptyList()))
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should unlink one action profile when two identical action profiles are linked")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldUnlinkOneActionProfileFromTwoIdenticalOnes() {

    //create action profile
    var actionProfileId = UUID.randomUUID().toString();
    var actionProfile = postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withName("testAction")
        .withId(actionProfileId)
        .withAction(CREATE)
        .withFolioRecord(INSTANCE)))
      .statusCode(SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    //create mapping profile
    var mappingProfileId = UUID.randomUUID().toString();
    postRequest(MAPPING_PROFILES_PATH, new MappingProfileUpdateDto()
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
      ))
      .statusCode(SC_CREATED);

    //create job profile
    var jobProfileId = UUID.randomUUID().toString();
    var jobProfile = postRequest(JOB_PROFILES_PATH, new JobProfileUpdateDto()
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
      ))
      .statusCode(SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    var profileAssociationCollection = getRequest(
      ASSOCIATED_PROFILES_PATH,
      Map.of("master", JOB_PROFILE.value(), "detail", ACTION_PROFILE.value())
    )
      .statusCode(SC_OK)
      .extract().body().as(ProfileAssociationCollection.class);
    assertThat(profileAssociationCollection.getTotalRecords().intValue()).isEqualTo(2);

    putRequest(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId(), jobProfile
      .withDeletedRelations(List.of(profileAssociationCollection.getProfileAssociations().getFirst()))
      .withAddedRelations(emptyList()))
      .statusCode(SC_OK)
      .body("id", is(jobProfile.getProfile().getId()))
      .body("name", is(jobProfile.getProfile().getName()))
      .body("dataType", is(jobProfile.getProfile().getDataType().value()))
      .extract().body().asPrettyString();

    profileAssociationCollection = getRequest(
      ASSOCIATED_PROFILES_PATH,
      Map.of("master", JOB_PROFILE.value(), "detail", ACTION_PROFILE.value())
    )
      .statusCode(SC_OK)
      .extract().body().as(ProfileAssociationCollection.class);
    assertThat(profileAssociationCollection.getTotalRecords().intValue()).isEqualTo(1);
  }

  @DisplayName("should unlink mirror action associations when match profiles are identical")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldUnlinkActionsMirrorAssociationsWithEqualsMatchProfiles() {

    JobProfileUpdateDto jobProfileBody = createJobProfile(new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(UUID.randomUUID().toString())
        .withName("testJob")
        .withDataType(MARC)), "createAction", "createMapping");
    //create job profile
    JobProfileUpdateDto jobProfile = postRequest(JOB_PROFILES_PATH, jobProfileBody)
      .statusCode(SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    //create matchProfile with associations to jobProfile
    String matchProfileId = UUID.randomUUID().toString();
    MatchProfileUpdateDto matchProfile = postRequest(MATCH_PROFILES_PATH, new MatchProfileUpdateDto()
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
      .statusCode(SC_CREATED)
      .extract().as(MatchProfileUpdateDto.class);

    //create action profile
    String actionProfileId = UUID.randomUUID().toString();
    ActionProfileUpdateDto actionProfile = postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withName("testAction")
        .withId(actionProfileId)
        .withAction(CREATE)
        .withFolioRecord(INSTANCE)))
      .statusCode(SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    String mappingProfileId = UUID.randomUUID().toString();
    postRequest(MAPPING_PROFILES_PATH, new MappingProfileUpdateDto()
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
      ))
      .statusCode(SC_CREATED)
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

    putRequest(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId(), jobProfile.withAddedRelations(
      List.of(match1ToMatchAction, match1ToNonMatchAction, match2ToMatchAction, match2ToNonMatchAction)
    ))
      .statusCode(SC_OK)
      .body("id", is(jobProfile.getProfile().getId()))
      .body("name", is(jobProfile.getProfile().getName()))
      .body("dataType", is(jobProfile.getProfile().getDataType().value()))
      .extract().body().asPrettyString();

    Object resp2 = getRequest(MATCH_PROFILES_PATH + "?withRelations=true")
      .statusCode(SC_OK)
      .extract().as(Object.class);
    assertThat(resp2).isNotNull();

    ProfileAssociationCollection profileAssociationCollection = getRequest(
      ASSOCIATED_PROFILES_PATH,
      Map.of("master", MATCH_PROFILE.value(), "detail", ACTION_PROFILE.value())
    )
      .statusCode(SC_OK)
      .extract().body().as(ProfileAssociationCollection.class);
    assertThat(profileAssociationCollection.getTotalRecords().intValue()).isEqualTo(4);

    putRequest(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId(), jobProfile
      .withAddedRelations(null)
      .withDeletedRelations(List.of(profileAssociationCollection.getProfileAssociations().getFirst())))
      .statusCode(SC_OK)
      .body("id", is(jobProfile.getProfile().getId()))
      .body("name", is(jobProfile.getProfile().getName()))
      .body("dataType", is(jobProfile.getProfile().getDataType().value()))
      .extract().body().asPrettyString();

    profileAssociationCollection = getRequest(
      ASSOCIATED_PROFILES_PATH,
      Map.of("master", MATCH_PROFILE.value(), "detail", ACTION_PROFILE.value())
    )
      .statusCode(SC_OK)
      .extract().body().as(ProfileAssociationCollection.class);
    assertThat(profileAssociationCollection.getTotalRecords().intValue()).isEqualTo(3);

    ProfileAssociation profileAssociation2deleteWithNullOrder =
      profileAssociationCollection.getProfileAssociations().getFirst().withOrder(null);

    putRequest(JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId(), jobProfile
      .withAddedRelations(null)
      .withDeletedRelations(List.of(profileAssociation2deleteWithNullOrder)))
      .statusCode(SC_OK)
      .body("id", is(jobProfile.getProfile().getId()))
      .body("name", is(jobProfile.getProfile().getName()))
      .body("dataType", is(jobProfile.getProfile().getDataType().value()))
      .extract().body().asPrettyString();

    profileAssociationCollection = getRequest(
      ASSOCIATED_PROFILES_PATH,
      Map.of("master", MATCH_PROFILE.value(), "detail", ACTION_PROFILE.value())
    )
      .statusCode(SC_OK)
      .extract().body().as(ProfileAssociationCollection.class);
    assertThat(profileAssociationCollection.getTotalRecords().intValue()).isEqualTo(2);
  }

  @DisplayName("should return bad request on PUT when UPDATE action profile has no linked match profile")
  @Test
  void shouldReturnBadRequestOnPutWithUpdateActionProfileWithoutMatchProfile() {
    var actionProfileId = UUID.randomUUID().toString();

    JobProfileUpdateDto jobProfile = createJobProfile(JOB_PROFILE_1, "createAction", "createMapping");
    var jobProfileToUpdate = postRequest(JOB_PROFILES_PATH, jobProfile)
      .statusCode(SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withName("testAction")
        .withAction(UPDATE)
        .withFolioRecord(INSTANCE)
        .withId(actionProfileId)))
      .statusCode(SC_CREATED);

    var invalidAssociation = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileId)
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobProfileToUpdate.getProfile().getId());

    putRequest(
      JOB_PROFILES_PATH + "/" + jobProfileToUpdate.getProfile().getId(),
      jobProfileToUpdate.withAddedRelations(List.of(invalidAssociation))
    )
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is(String.format("ActionProfile with id '%s' and action UPDATE requires linked MatchProfile",
            actionProfileId)))
      ));
  }

  @DisplayName("should return bad request on PUT when job profile has no associations")
  @Test
  void shouldReturnBadRequestOnPutIfNoAssociations() {
    JobProfileUpdateDto jobProfile = createJobProfile(JOB_PROFILE_1, "createAction", "createMapping");

    var jobProfileToUpdate = postRequest(JOB_PROFILES_PATH, jobProfile)
      .statusCode(SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    putRequest(JOB_PROFILES_PATH + "/" + jobProfileToUpdate.getProfile().getId(), jobProfileToUpdate
      .withDeletedRelations(jobProfileToUpdate.getAddedRelations())
      .withAddedRelations(emptyList()))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Job profile does not contain any associations"))
      ));
  }

  @DisplayName("should return bad request on PUT when no action profile follows match profile")
  @Test
  void shouldReturnBadRequestOnPutIfNoActionProfileAfterMatch() {
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

    JobProfileUpdateDto jobProfile = createJobProfileWithMatch(JOB_PROFILE_1,
      matchUpdateDto,
      actionProfileUpdateDto,
      mappingProfileUpdateDto);

    JobProfileUpdateDto jobProfileToUpdate = postRequest(JOB_PROFILES_PATH, jobProfile)
      .statusCode(SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    putRequest(JOB_PROFILES_PATH + "/" + jobProfileToUpdate.getProfile().getId(), jobProfileToUpdate
      .withDeletedRelations(List.of(jobProfileToUpdate.getAddedRelations().get(1)))
      .withAddedRelations(emptyList()))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Linked ActionProfile was not found after MatchProfile"))
      ));
  }

  @DisplayName("should return bad request on POST when no action profile follows match profile")
  @Test
  void shouldReturnBadRequestOnPostIfNoActionProfileAfterMatch() {
    var matchUpdateDto = new MatchProfileUpdateDto()
      .withProfile(new MatchProfile()
        .withId(UUID.randomUUID().toString())
        .withName("testMatch")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE));

    JobProfileUpdateDto jobProfile =
      createJobProfile(JOB_PROFILE_1, new MatchProfileWrapper(matchUpdateDto), MATCH_PROFILES_PATH, MATCH_PROFILE);

    postRequest(JOB_PROFILES_PATH, jobProfile)
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Linked ActionProfile was not found after MatchProfile"))
      ));
  }

  @DisplayName("should return bad request on PUT when standalone MODIFY action is added")
  @Test
  void shouldReturnBadRequestOnPutWithStandaloneModifyAction() {
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

    var jobProfile = createJobProfileWithAction(JOB_PROFILE_1, actionProfileUpdateDto, mappingProfileUpdateDto);

    var jobProfileToUpdate = postRequest(JOB_PROFILES_PATH, jobProfile)
      .statusCode(SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    var invalidAssociation = new ProfileAssociation()
      .withDetailProfileType(ACTION_PROFILE)
      .withDetailProfileId(actionProfileId)
      .withMasterProfileType(JOB_PROFILE)
      .withMasterProfileId(jobProfile.getId());

    putRequest(
      JOB_PROFILES_PATH + "/" + jobProfileToUpdate.getProfile().getId(),
      jobProfileToUpdate.withAddedRelations(List.of(invalidAssociation))
    )
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Modify action cannot be used as a standalone action"))
      ));
  }

  @DisplayName("should return bad request on PUT when standalone MODIFY action after match is added")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldReturnBadRequestOnPutWithStandaloneModifyActionAfterMatch() {
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

    var jobProfile = createJobProfileWithMatch(JOB_PROFILE_1, matchProfile,
      actionProfileUpdateDto, mappingProfileUpdateDto);

    var jobProfileToUpdate = postRequest(JOB_PROFILES_PATH, jobProfile)
      .statusCode(SC_CREATED)
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

    putRequest(JOB_PROFILES_PATH + "/" + jobProfileToUpdate.getProfile().getId(), jobProfileToUpdate.withAddedRelations(
      List.of(invalidAssociationMatchToJobProfile, invalidAssociationActionToMatch)))
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors", hasItem(
        hasEntry(is("message"),
          is("Modify action cannot be used right after a Match"))
      ));
  }

  @DisplayName("should delete all associated detail profile associations when job profile is deleted")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldDeleteAssociationsWithDetailProfilesOnDelete() {
    // creation detail-profiles
    String actionProfileId = UUID.randomUUID().toString();
    var actionProfile = postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withId(actionProfileId)
        .withName("testAction")
        .withAction(UPDATE)
        .withFolioRecord(MARC_BIBLIOGRAPHIC)))
      .statusCode(SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    String mappingProfileIdCreate = UUID.randomUUID().toString();
    var mappingProfile = postRequest(MAPPING_PROFILES_PATH, new MappingProfileUpdateDto()
      .withProfile(new MappingProfile().withName("testMapping")
        .withId(mappingProfileIdCreate)
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE)))
      .statusCode(SC_CREATED)
      .extract().as(MappingProfileUpdateDto.class);

    String matchProfileId = UUID.randomUUID().toString();
    var matchProfile = postRequest(MATCH_PROFILES_PATH, new MatchProfileUpdateDto()
      .withProfile(new MatchProfile()
        .withId(matchProfileId)
        .withName("testMatch")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE)))
      .statusCode(SC_CREATED)
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

    var profileToDelete = postRequest(JOB_PROFILES_PATH, jobProfile)
      .statusCode(SC_CREATED)
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
    deleteRequest(JOB_PROFILES_PATH + "/" + profileToDelete.getProfile().getId())
      .statusCode(SC_NO_CONTENT);

    // receiving deleted associations
    getRequest(
      ASSOCIATED_PROFILES_PATH + "/" + jobToActionAssociation.getId(),
      Map.of("master", JOB_PROFILE.value(), "detail", ACTION_PROFILE.value())
    )
      .statusCode(SC_NOT_FOUND);

    getRequest(
      ASSOCIATED_PROFILES_PATH + "/" + jobToMatchAssociation.getId(),
      Map.of("master", JOB_PROFILE.value(), "detail", MATCH_PROFILE.value())
    )
      .statusCode(SC_NOT_FOUND);
  }

  @DisplayName("should return unprocessable entity on PUT when job profile name already exists")
  @Test
  void shouldReturnUnprocessableEntityOnPutJobProfileWithExistingName() {
    JobProfileUpdateDto jobProfile = createJobProfile(JOB_PROFILE_1, "createAction", "createMapping");

    postRequest(JOB_PROFILES_PATH, jobProfile)
      .statusCode(is(SC_CREATED));

    JobProfileUpdateDto newJobProfile = createJobProfile(JOB_PROFILE_2, "createAction2", "createMapping2");
    JobProfileUpdateDto createdJobProfile = postRequest(JOB_PROFILES_PATH, newJobProfile)
      .statusCode(SC_CREATED)
      .extract().body().as(JobProfileUpdateDto.class);

    createdJobProfile.getProfile().setName(JOB_PROFILE_1.getProfile().getName());
    putRequest(JOB_PROFILES_PATH + "/" + createdJobProfile.getProfile().getId(), createdJobProfile)
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should hard delete profile on DELETE")
  @Test
  void shouldHardDeleteProfileOnDeletion() {
    createProfiles();

    JobProfileUpdateDto jobProfile = new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withName("ProfileToDelete")
        .withDataType(MARC));

    JobProfileUpdateDto jobProfileToDelete = createJobProfile(jobProfile, "createAction", "createMapping");

    jobProfileToDelete = postRequest(JOB_PROFILES_PATH, jobProfileToDelete)

      .statusCode(SC_CREATED)
      .extract().body().as(JobProfileUpdateDto.class);

    deleteRequest(JOB_PROFILES_PATH + "/" + jobProfileToDelete.getProfile().getId())

      .statusCode(SC_NO_CONTENT);

    getRequest(JOB_PROFILES_PATH + "/" + jobProfileToDelete.getProfile().getId())
      .statusCode(SC_NOT_FOUND);
  }

  @DisplayName("should create profile on POST when a profile with the same name was previously deleted")
  @Test
  void shouldCreateProfileOnPostWhenWasDeletedProfileWithSameNameBefore() {
    JobProfileUpdateDto jobProfile = new JobProfileUpdateDto()
      .withProfile(new JobProfile().withName("profileName")
        .withDataType(MARC));

    JobProfileUpdateDto jobProfileToDelete = createJobProfile(jobProfile, "createAction", "createMapping");

    jobProfileToDelete = postRequest(JOB_PROFILES_PATH, jobProfileToDelete)

      .statusCode(SC_CREATED)
      .extract().body().as(JobProfileUpdateDto.class);

    deleteRequest(JOB_PROFILES_PATH + "/" + jobProfileToDelete.getProfile().getId())
      .statusCode(SC_NO_CONTENT);

    JobProfileUpdateDto jobProfile2 = createJobProfile(jobProfile, "createAction2", "createMapping2");

    postRequest(JOB_PROFILES_PATH, jobProfile2)

      .statusCode(SC_CREATED);
  }

  @DisplayName("should return bad request on POST when child or parent profile field is not empty")
  @Test
  void shouldReturnBadRequestWhenChildOrParentProfileIsNotEmptyOnPost() {
    JobProfileUpdateDto jobProfile = createJobProfile(JOB_PROFILE_NOT_EMPTY_CHILD_AND_PARENT, "createAction",
      "createMapping");

    postRequest(JOB_PROFILES_PATH, jobProfile)
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Job profile read-only 'child' field should be empty"))
      .body("errors[1].message", is("Job profile read-only 'parent' field should be empty"));
  }

  @DisplayName("should return bad request on PUT when child or parent profile field is not empty")
  @Test
  void shouldReturnBadRequestWhenChildOrParentProfileIsNotEmptyOnPut() {
    JobProfileUpdateDto jobProfileUpdateDto = createJobProfile(JOB_PROFILE_2, "createAction",
      "createMapping");

    JobProfileUpdateDto jobProfile = postRequest(JOB_PROFILES_PATH, jobProfileUpdateDto)
      .statusCode(SC_CREATED)
      .extract().body().as(JobProfileUpdateDto.class);

    putRequest(
      JOB_PROFILES_PATH + "/" + jobProfile.getProfile().getId(),
      JOB_PROFILE_NOT_EMPTY_CHILD_AND_PARENT.withAddedRelations(null)
    )
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Job profile read-only 'child' field should be empty"))
      .body("errors[1].message", is("Job profile read-only 'parent' field should be empty"));
  }

  private <T> JobProfileUpdateDto createJobProfile(JobProfileUpdateDto jobProfileUpdateDto,
                                                   ProfileWrapper<T> profileWrapper, String url,
                                                   ProfileType detailProfileType) {
    T profile = postRequest(url, profileWrapper.getProfile())
      .statusCode(SC_CREATED)
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

  private JobProfileUpdateDto createJobProfile(JobProfileUpdateDto jobProfileUpdateDto,
                                               String actionName,
                                               String mappingName) {
    var actionProfileIdCreate = UUID.randomUUID().toString();
    postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withName(actionName)
        .withId(actionProfileIdCreate)
        .withAction(CREATE)
        .withFolioRecord(INSTANCE)))
      .statusCode(SC_CREATED);

    String mappingProfileIdCreate = UUID.randomUUID().toString();
    postRequest(MAPPING_PROFILES_PATH, new MappingProfileUpdateDto()
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
      .statusCode(SC_CREATED);

    var validAssociation = new ProfileAssociation()
      .withMasterProfileId(jobProfileUpdateDto.getId())
      .withDetailProfileId(actionProfileIdCreate)
      .withMasterProfileType(JOB_PROFILE)
      .withDetailProfileType(ACTION_PROFILE)
      .withOrder(0);

    return jobProfileUpdateDto.withAddedRelations(List.of(validAssociation));
  }

  private JobProfileUpdateDto createJobProfileWithAction(JobProfileUpdateDto jobProfileUpdateDto,
                                                         ActionProfileUpdateDto actionProfileUpdateDto,
                                                         MappingProfileUpdateDto mappingProfileUpdateDto) {
    var mappingProfile = postRequest(MAPPING_PROFILES_PATH, mappingProfileUpdateDto)
      .statusCode(SC_CREATED)
      .extract().as(MappingProfileUpdateDto.class);

    var actionProfile = postRequest(ACTION_PROFILES_PATH, actionProfileUpdateDto)
      .statusCode(SC_CREATED)
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
    var mappingProfile = postRequest(MAPPING_PROFILES_PATH, mappingProfileUpdateDto)
      .statusCode(SC_CREATED)
      .extract().as(MappingProfileUpdateDto.class);

    var matchProfile = postRequest(MATCH_PROFILES_PATH, matchProfileUpdateDto)
      .statusCode(SC_CREATED)
      .extract().as(MatchProfileUpdateDto.class);

    var actionProfile = postRequest(ACTION_PROFILES_PATH, actionProfileUpdateDto)
      .statusCode(SC_CREATED)
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
      Tuple.of(JOB_PROFILE_1, "actionCreate1", "mappingCreate1"),
      Tuple.of(JOB_PROFILE_2, "actionCreate2", "mappingCreate2"),
      Tuple.of(JOB_PROFILE_3, "actionCreate3", "mappingCreate3"));
    for (Tuple profile : jobProfilesToPost) {
      JobProfileUpdateDto jobProfile = createJobProfile(
        profile.get(JobProfileUpdateDto.class, 0),
        profile.getString(1),
        profile.getString(2));
      postRequest(JOB_PROFILES_PATH, jobProfile)
        .statusCode(SC_CREATED);
    }
  }
}

