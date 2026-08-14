package org.folio.rest.impl.association;

import static org.folio.rest.jaxrs.model.ActionProfile.Action.CREATE;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.UPDATE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.INSTANCE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC;
import static org.folio.rest.jaxrs.model.JobProfile.DataType.MARC;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MAPPING_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.valueOf;
import static org.folio.rest.jaxrs.model.ProfileType.values;
import static org.folio.support.TestUtil.ACTION_PROFILES_PATH;
import static org.folio.support.TestUtil.ASSOCIATED_PROFILES_PATH;
import static org.folio.support.TestUtil.DETAILS_BY_MASTER_PATH;
import static org.folio.support.TestUtil.JOB_PROFILES_PATH;
import static org.folio.support.TestUtil.MAPPING_PROFILES_PATH;
import static org.folio.support.TestUtil.MASTERS_BY_DETAIL_PATH;
import static org.folio.support.TestUtil.MATCH_PROFILES_PATH;
import static org.hamcrest.Matchers.is;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.folio.rest.impl.association.wrapper.ActionProfileWrapper;
import org.folio.rest.impl.association.wrapper.JobProfileWrapper;
import org.folio.rest.impl.association.wrapper.MappingProfileWrapper;
import org.folio.rest.impl.association.wrapper.MatchProfileWrapper;
import org.folio.rest.impl.association.wrapper.ProfileWrapper;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingDetail;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.MatchProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.support.AbstractRestTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommonProfileAssociationRestTest extends AbstractRestTest {

  private static final String ASSOCIATION_UUID = "7e5da9cf-e9e8-4734-b23e-a4a1129ebcdb";
  private static final String PROFILE_ASSOCIATION_PATH = associatedProfileByIdUrl(ASSOCIATION_UUID);

  private final JobProfileUpdateDto jobProfile1 = new JobProfileUpdateDto()
    .withProfile(new JobProfile().withName("testJobProfile1").withDataType(MARC).withDescription("test-description"));
  private final JobProfileUpdateDto jobProfile2 = new JobProfileUpdateDto()
    .withProfile(new JobProfile().withName("testJobProfile2").withDataType(MARC).withDescription("test-description"));

  private final ActionProfileUpdateDto actionProfile1 = new ActionProfileUpdateDto()
    .withId("f12db804-70d1-49dd-a58c-49edf9031371")
    .withProfile(new ActionProfile().withName("testActionProfile1").withDescription("test-description")
      .withAction(CREATE).withFolioRecord(INSTANCE));

  private final ActionProfileUpdateDto actionProfile2 = new ActionProfileUpdateDto()
    .withId("f12db804-70d1-49dd-a58c-49edf9031372")
    .withProfile(new ActionProfile().withName("testActionProfile2").withDescription("test-description")
      .withAction(UPDATE).withFolioRecord(MARC_BIBLIOGRAPHIC));

  private final ActionProfileUpdateDto actionProfile3 = new ActionProfileUpdateDto()
    .withId("f12db804-70d1-49dd-a58c-49edf9031373")
    .withProfile(new ActionProfile().withName("testActionProfile3").withDescription("test-description")
      .withAction(UPDATE).withFolioRecord(MARC_BIBLIOGRAPHIC));

  private final ActionProfileUpdateDto actionProfile4 = new ActionProfileUpdateDto()
    .withId("f12db804-70d1-49dd-a58c-49edf9031374")
    .withProfile(new ActionProfile().withName("testActionProfile4")
      .withAction(UPDATE).withFolioRecord(MARC_BIBLIOGRAPHIC));

  private final MappingProfileUpdateDto mappingProfile1 = new MappingProfileUpdateDto()
    .withProfile(new MappingProfile().withName("testMappingProfile1").withDescription("test-description")
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.INSTANCE));
  private final MappingProfileUpdateDto mappingProfile2 = new MappingProfileUpdateDto()
    .withProfile(new MappingProfile().withName("testMappingProfile2").withDescription("test-description")
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.INSTANCE));
  private final MappingProfileUpdateDto mappingProfile3 = new MappingProfileUpdateDto()
    .withProfile(new MappingProfile().withName("testMappingProfile3")
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.INSTANCE));

  private final MatchProfileUpdateDto matchProfile1 = new MatchProfileUpdateDto()
    .withProfile(new MatchProfile().withName("testMatchProfile1")
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.INSTANCE)
      .withDescription("test-description"));
  private final MatchProfileUpdateDto matchProfile2 = new MatchProfileUpdateDto()
    .withProfile(new MatchProfile().withName("testMatchProfile2")
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withDescription("test-description"));
  private final MatchProfileUpdateDto matchProfile3 = new MatchProfileUpdateDto()
    .withProfile(new MatchProfile().withName("testMatchProfile3")
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withDescription("test-description"));
  private final MatchProfileUpdateDto matchProfile4 = new MatchProfileUpdateDto()
    .withProfile(new MatchProfile().withName("testMatchProfile4")
      .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC));

  @DisplayName("should return empty 200 OK result on GET all associations (action to action)")
  @Test
  void shouldReturnEmptyOkResultOnGetAll_actionToAction() {
    shouldReturnEmptyOkResultOnGetAll(ACTION_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should return empty 200 OK result on GET all associations (action to mapping)")
  @Test
  void shouldReturnEmptyOkResultOnGetAll_actionToMapping() {
    shouldReturnEmptyOkResultOnGetAll(ACTION_PROFILE, MAPPING_PROFILE);
  }

  @DisplayName("should return empty 200 OK result on GET all associations (action to match)")
  @Test
  void shouldReturnEmptyOkResultOnGetAll_actionToMatch() {
    shouldReturnEmptyOkResultOnGetAll(ACTION_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should return empty 200 OK result on GET all associations (job to action)")
  @Test
  void shouldReturnEmptyOkResultOnGetAll_jobToAction() {
    shouldReturnEmptyOkResultOnGetAll(JOB_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should return empty 200 OK result on GET all associations (job to match)")
  @Test
  void shouldReturnEmptyOkResultOnGetAll_jobToMatch() {
    shouldReturnEmptyOkResultOnGetAll(JOB_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should return empty 200 OK result on GET all associations (match to action)")
  @Test
  void shouldReturnEmptyOkResultOnGetAll_matchToAction() {
    shouldReturnEmptyOkResultOnGetAll(MATCH_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should return empty 200 OK result on GET all associations (match to match)")
  @Test
  void shouldReturnEmptyOkResultOnGetAll_matchToMatch() {
    shouldReturnEmptyOkResultOnGetAll(MATCH_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should return profile association list on GET (action to mapping)")
  @Test
  void shouldReturnProfileAssociationListOnGet_actionToMapping() {
    shouldReturnProfileAssociationListOnGet(
      new ActionProfileWrapper(actionProfile3),
      new MappingProfileWrapper(mappingProfile1),
      ACTION_PROFILES_PATH, MAPPING_PROFILES_PATH, ACTION_PROFILE, MAPPING_PROFILE);
  }

  @DisplayName("should return profile association list on GET (match to action)")
  @Test
  void shouldReturnProfileAssociationListOnGet_matchToAction() {
    shouldReturnProfileAssociationListOnGet(
      new MatchProfileWrapper(matchProfile1),
      new ActionProfileWrapper(actionProfile1),
      MATCH_PROFILES_PATH, ACTION_PROFILES_PATH, MATCH_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should return profile association list on GET (match to match)")
  @Test
  void shouldReturnProfileAssociationListOnGet_matchToMatch() {
    shouldReturnProfileAssociationListOnGet(
      new MatchProfileWrapper(matchProfile2),
      new MatchProfileWrapper(matchProfile3),
      MATCH_PROFILES_PATH, MATCH_PROFILES_PATH, MATCH_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should return profile association list on GET (job to action)")
  @Test
  void shouldReturnProfileAssociationListOnGet_jobToAction() {
    var jobProfile = createJobProfileWithAction(new JobProfileWrapper(jobProfile1),
      new ActionProfileWrapper(actionProfile1),
      new MappingProfileWrapper(mappingProfile1));

    postProfile(new JobProfileWrapper(jobProfile), JOB_PROFILES_PATH);
    getRequest(ASSOCIATED_PROFILES_PATH, queryParams(ProfileType.JOB_PROFILE, ProfileType.ACTION_PROFILE))
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(1));
  }

  @DisplayName("should return profile association list on GET (job to match)")
  @Test
  void shouldReturnProfileAssociationListOnGet_jobToMatch() {
    var jobProfile = createJobProfileWithMatch(new JobProfileWrapper(jobProfile2),
      new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1),
      new MatchProfileWrapper(matchProfile1));

    postProfile(new JobProfileWrapper(jobProfile), JOB_PROFILES_PATH);
    getRequest(ASSOCIATED_PROFILES_PATH, queryParams(ProfileType.JOB_PROFILE, ProfileType.MATCH_PROFILE))
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(1));
  }

  @DisplayName("should return 404 Not Found on GET by id (action to action)")
  @Test
  void shouldReturnNotFoundOnGetById_actionToAction() {
    shouldReturnNotFoundOnGetById(ACTION_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should return 404 Not Found on GET by id (action to mapping)")
  @Test
  void shouldReturnNotFoundOnGetById_actionToMapping() {
    shouldReturnNotFoundOnGetById(ACTION_PROFILE, MAPPING_PROFILE);
  }

  @DisplayName("should return 404 Not Found on GET by id (action to match)")
  @Test
  void shouldReturnNotFoundOnGetById_actionToMatch() {
    shouldReturnNotFoundOnGetById(ACTION_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should return 404 Not Found on GET by id (job to action)")
  @Test
  void shouldReturnNotFoundOnGetById_jobToAction() {
    shouldReturnNotFoundOnGetById(JOB_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should return 404 Not Found on GET by id (job to match)")
  @Test
  void shouldReturnNotFoundOnGetById_jobToMatch() {
    shouldReturnNotFoundOnGetById(JOB_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should return 404 Not Found on GET by id (match to action)")
  @Test
  void shouldReturnNotFoundOnGetById_matchToAction() {
    shouldReturnNotFoundOnGetById(MATCH_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should return 404 Not Found on GET by id (match to match)")
  @Test
  void shouldReturnNotFoundOnGetById_matchToMatch() {
    shouldReturnNotFoundOnGetById(MATCH_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should POST association and GET it by id (action to mapping)")
  @Test
  void shouldPostAndGetById_actionToMapping() {
    shouldPostAndGetById(
      new ActionProfileWrapper(actionProfile1),
      new MappingProfileWrapper(mappingProfile1),
      ACTION_PROFILES_PATH, MAPPING_PROFILES_PATH, ACTION_PROFILE, MAPPING_PROFILE);
  }

  @DisplayName("should POST association and GET it by id (action to match)")
  @Test
  void shouldPostAndGetById_actionToMatch() {
    shouldPostAndGetById(
      new ActionProfileWrapper(actionProfile1),
      new MatchProfileWrapper(matchProfile2),
      ACTION_PROFILES_PATH, MATCH_PROFILES_PATH, ACTION_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should POST association and GET it by id (match to match)")
  @Test
  void shouldPostAndGetById_matchToMatch() {
    shouldPostAndGetById(
      new MatchProfileWrapper(matchProfile1),
      new MatchProfileWrapper(matchProfile3),
      MATCH_PROFILES_PATH, MATCH_PROFILES_PATH, MATCH_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should POST association and GET it by id (job to action)")
  @Test
  void shouldPostAndGetById_jobToAction() {
    var jobProfile = createJobProfileWithAction(
      new JobProfileWrapper(jobProfile1),
      new ActionProfileWrapper(actionProfile1),
      new MappingProfileWrapper(mappingProfile1));

    var masterWrapper = postProfile(new JobProfileWrapper(jobProfile), JOB_PROFILES_PATH);

    getRequest(PROFILE_ASSOCIATION_PATH, queryParams(ProfileType.JOB_PROFILE, ProfileType.ACTION_PROFILE))
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(ASSOCIATION_UUID))
      .body("masterProfileId", is(masterWrapper.getId()))
      .body("detailProfileId", is(jobProfile.getAddedRelations().getFirst().getDetailProfileId()))
      .body("order", is(jobProfile.getAddedRelations().getFirst().getOrder()));
  }

  @DisplayName("should POST association and GET it by id (job to match)")
  @Test
  void shouldPostAndGetById_jobToMatch() {
    var jobProfile = createJobProfileWithMatch(
      new JobProfileWrapper(jobProfile2),
      new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1),
      new MatchProfileWrapper(matchProfile1));

    var masterWrapper = postProfile(new JobProfileWrapper(jobProfile), JOB_PROFILES_PATH);

    getRequest(PROFILE_ASSOCIATION_PATH, queryParams(ProfileType.JOB_PROFILE, ProfileType.MATCH_PROFILE))
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(ASSOCIATION_UUID))
      .body("masterProfileId", is(masterWrapper.getId()))
      .body("detailProfileId", is(jobProfile.getAddedRelations().getFirst().getDetailProfileId()))
      .body("order", is(jobProfile.getAddedRelations().getFirst().getOrder()));
  }

  @DisplayName("should return 422 Unprocessable Entity on POST when body is invalid")
  @Test
  void shouldReturnBadRequestOnPost() {
    postRequest(ASSOCIATED_PROFILES_PATH, new JobProfile())
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should return 404 Not Found on DELETE (action to action)")
  @Test
  void shouldReturnNotFoundOnDelete_actionToAction() {
    shouldReturnNotFoundOnDelete(ACTION_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should return 404 Not Found on DELETE (action to mapping)")
  @Test
  void shouldReturnNotFoundOnDelete_actionToMapping() {
    shouldReturnNotFoundOnDelete(ACTION_PROFILE, MAPPING_PROFILE);
  }

  @DisplayName("should return 404 Not Found on DELETE (action to match)")
  @Test
  void shouldReturnNotFoundOnDelete_actionToMatch() {
    shouldReturnNotFoundOnDelete(ACTION_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should return 404 Not Found on DELETE (job to action)")
  @Test
  void shouldReturnNotFoundOnDelete_jobToAction() {
    shouldReturnNotFoundOnDelete(JOB_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should return 404 Not Found on DELETE (job to match)")
  @Test
  void shouldReturnNotFoundOnDelete_jobToMatch() {
    shouldReturnNotFoundOnDelete(JOB_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should return 404 Not Found on DELETE (match to action)")
  @Test
  void shouldReturnNotFoundOnDelete_matchToAction() {
    shouldReturnNotFoundOnDelete(MATCH_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should return 404 Not Found on DELETE (match to match)")
  @Test
  void shouldReturnNotFoundOnDelete_matchToMatch() {
    shouldReturnNotFoundOnDelete(MATCH_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should delete profile association on DELETE (action to mapping)")
  @Test
  void shouldDeleteProfileOnDelete_actionToMapping() {
    shouldDeleteProfileOnDelete(
      new ActionProfileWrapper(actionProfile3),
      new MappingProfileWrapper(mappingProfile1),
      ACTION_PROFILES_PATH, MAPPING_PROFILES_PATH, ACTION_PROFILE, MAPPING_PROFILE);
  }

  @DisplayName("should delete profile association on DELETE (match to action)")
  @Test
  void shouldDeleteProfileOnDelete_matchToAction() {
    shouldDeleteProfileOnDelete(
      new MatchProfileWrapper(matchProfile2),
      new ActionProfileWrapper(actionProfile1),
      MATCH_PROFILES_PATH, ACTION_PROFILES_PATH, MATCH_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should delete profile association on DELETE (match to match)")
  @Test
  void shouldDeleteProfileOnDelete_matchToMatch() {
    shouldDeleteProfileOnDelete(
      new MatchProfileWrapper(matchProfile1),
      new MatchProfileWrapper(matchProfile2),
      MATCH_PROFILES_PATH, MATCH_PROFILES_PATH, MATCH_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should delete profile association on DELETE (job to action)")
  @Test
  void shouldDeleteProfileOnDelete_jobToAction() {
    var jobProfile = createJobProfileWithAction(new JobProfileWrapper(jobProfile1),
      new ActionProfileWrapper(actionProfile1),
      new MappingProfileWrapper(mappingProfile1));

    postProfile(new JobProfileWrapper(jobProfile), JOB_PROFILES_PATH);
    deleteRequest(PROFILE_ASSOCIATION_PATH, queryParams(ProfileType.JOB_PROFILE, ProfileType.ACTION_PROFILE))
      .statusCode(HttpStatus.SC_NO_CONTENT);
  }

  @DisplayName("should delete profile association on DELETE (job to match)")
  @Test
  void shouldDeleteProfileOnDelete_jobToMatch() {
    var jobProfile = createJobProfileWithMatch(new JobProfileWrapper(jobProfile2),
      new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1), new MatchProfileWrapper(matchProfile1));

    postProfile(new JobProfileWrapper(jobProfile), JOB_PROFILES_PATH);
    deleteRequest(PROFILE_ASSOCIATION_PATH, queryParams(ProfileType.JOB_PROFILE, ProfileType.ACTION_PROFILE))
      .statusCode(HttpStatus.SC_NO_CONTENT);
  }

  @DisplayName("should return 422 Unprocessable Entity on PUT (action to action)")
  @Test
  void shouldReturnBadRequestOnPut_actionToAction() {
    shouldReturnBadRequestOnPut(ACTION_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should return 422 Unprocessable Entity on PUT (action to mapping)")
  @Test
  void shouldReturnBadRequestOnPut_actionToMapping() {
    shouldReturnBadRequestOnPut(ACTION_PROFILE, MAPPING_PROFILE);
  }

  @DisplayName("should return 422 Unprocessable Entity on PUT (action to match)")
  @Test
  void shouldReturnBadRequestOnPut_actionToMatch() {
    shouldReturnBadRequestOnPut(ACTION_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should return 422 Unprocessable Entity on PUT (job to action)")
  @Test
  void shouldReturnBadRequestOnPut_jobToAction() {
    shouldReturnBadRequestOnPut(JOB_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should return 422 Unprocessable Entity on PUT (job to match)")
  @Test
  void shouldReturnBadRequestOnPut_jobToMatch() {
    shouldReturnBadRequestOnPut(JOB_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should return 422 Unprocessable Entity on PUT (match to action)")
  @Test
  void shouldReturnBadRequestOnPut_matchToAction() {
    shouldReturnBadRequestOnPut(MATCH_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should return 422 Unprocessable Entity on PUT (match to match)")
  @Test
  void shouldReturnBadRequestOnPut_matchToMatch() {
    shouldReturnBadRequestOnPut(MATCH_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should return 404 Not Found on PUT (action to mapping)")
  @Test
  void shouldReturnNotFoundOnPut_actionToMapping() {
    var fixture = setUpNotFoundOnPutFixture();
    var masterId = fixture.actionProfileId();
    var detailId = fixture.mappingProfileId();
    shouldReturnNotFoundOnPut(ACTION_PROFILE, masterId, MAPPING_PROFILE, detailId);
  }

  @DisplayName("should return 404 Not Found on PUT (action to match)")
  @Test
  void shouldReturnNotFoundOnPut_actionToMatch() {
    var fixture = setUpNotFoundOnPutFixture();
    var masterId = fixture.actionProfileId();
    var detailId = fixture.firstMatchProfileId();
    shouldReturnNotFoundOnPut(ACTION_PROFILE, masterId, MATCH_PROFILE, detailId);
  }

  @DisplayName("should return 404 Not Found on PUT (match to action)")
  @Test
  void shouldReturnNotFoundOnPut_matchToAction() {
    var fixture = setUpNotFoundOnPutFixture();
    var masterId = fixture.firstMatchProfileId();
    var detailId = fixture.actionProfileId();
    shouldReturnNotFoundOnPut(MATCH_PROFILE, masterId, ACTION_PROFILE, detailId);
  }

  @DisplayName("should return 404 Not Found on PUT (match to match)")
  @Test
  void shouldReturnNotFoundOnPut_matchToMatch() {
    var fixture = setUpNotFoundOnPutFixture();
    var masterId = fixture.firstMatchProfileId();
    var detailId = fixture.secondMatchProfileId();
    shouldReturnNotFoundOnPut(MATCH_PROFILE, masterId, MATCH_PROFILE, detailId);
  }

  @DisplayName("should return 404 Not Found on PUT (job to action)")
  @Test
  void shouldReturnNotFoundOnPut_jobToAction() {
    var fixture = setUpNotFoundOnPutFixture();
    var jobProfileWrapper = fixture.existingJobProfileWrapper();
    var actionProfileId = fixture.actionProfileId();
    var matchProfileId = fixture.firstMatchProfileId();

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
      .withDetailProfileId(fixture.mappingProfileId())
      .withMasterProfileType(ACTION_PROFILE)
      .withDetailProfileType(MAPPING_PROFILE);

    postRequest(JOB_PROFILES_PATH, jobProfileWrapper.getProfile()
      .withAddedRelations(List.of(jobToMatchAssociation, matchToActionAssociation, actionToMappingAssociation)))
      .log().all()
      .statusCode(HttpStatus.SC_CREATED);

    putRequest(associatedProfileByIdUrl(UUID.randomUUID().toString()), jobToMatchAssociation,
      queryParams(ProfileType.JOB_PROFILE, ProfileType.ACTION_PROFILE))
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @DisplayName("should update profile association on PUT (action to mapping)")
  @Test
  void shouldUpdateProfileAssociationOnPut_actionToMapping() {
    shouldUpdateProfileAssociationOnPut(
      new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1),
      new MappingProfileWrapper(mappingProfile2),
      ACTION_PROFILES_PATH, MAPPING_PROFILES_PATH, ACTION_PROFILE, MAPPING_PROFILE);
  }

  @DisplayName("should update profile association on PUT (match to action)")
  @Test
  void shouldUpdateProfileAssociationOnPut_matchToAction() {
    shouldUpdateProfileAssociationOnPut(
      new MatchProfileWrapper(matchProfile1),
      new ActionProfileWrapper(actionProfile1),
      new ActionProfileWrapper(actionProfile2),
      MATCH_PROFILES_PATH, ACTION_PROFILES_PATH, MATCH_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should update profile association on PUT (match to match)")
  @Test
  void shouldUpdateProfileAssociationOnPut_matchToMatch() {
    shouldUpdateProfileAssociationOnPut(
      new MatchProfileWrapper(matchProfile1),
      new MatchProfileWrapper(matchProfile2),
      new MatchProfileWrapper(matchProfile3),
      MATCH_PROFILES_PATH, MATCH_PROFILES_PATH, MATCH_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should update profile association on PUT (job to match to action)")
  @Test
  void shouldUpdateProfileAssociationOnPut_jobToMatchToAction() {
    var jobProfile = createJobProfileWithMatch(new JobProfileWrapper(jobProfile1),
      new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1), new MatchProfileWrapper(matchProfile1));

    var jobWrapper = postProfile(new JobProfileWrapper(jobProfile), JOB_PROFILES_PATH);
    var mappingWrapper = postProfile(new MappingProfileWrapper(mappingProfile2), MAPPING_PROFILES_PATH);

    var actionToMappingAssociation = new ProfileAssociation()
      .withId(jobWrapper.getProfile().getAddedRelations().get(2).getId())
      .withMasterProfileId(jobWrapper.getProfile().getAddedRelations().get(2).getMasterProfileId())
      .withDetailProfileId(mappingWrapper.getProfile().getId())
      .withMasterProfileType(ACTION_PROFILE)
      .withDetailProfileType(MAPPING_PROFILE);

    putRequest(associatedProfileByIdUrl(actionToMappingAssociation.getId()), actionToMappingAssociation,
      queryParams(ProfileType.ACTION_PROFILE, ProfileType.MAPPING_PROFILE))
      .statusCode(is(HttpStatus.SC_OK))
      .body("id", is(actionToMappingAssociation.getId()))
      .body("masterProfileId", is(jobWrapper.getProfile().getAddedRelations().get(2).getMasterProfileId()))
      .body("detailProfileId", is(mappingWrapper.getId()))
      .body("order", is(actionToMappingAssociation.getOrder()));
  }

  @DisplayName("should return detail profiles by master profile (action to mapping)")
  @Test
  void getDetailsByMasterProfile_Ok_actionToMapping() {
    getDetailProfilesByMasterProfile_Ok(
      new ActionProfileWrapper(actionProfile1),
      new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1),
      new MappingProfileWrapper(mappingProfile2),
      ACTION_PROFILES_PATH, MAPPING_PROFILES_PATH, ACTION_PROFILE, MAPPING_PROFILE);
  }

  @DisplayName("should return detail profiles by master profile (match to action)")
  @Test
  void getDetailsByMasterProfile_Ok_matchToAction() {
    getDetailProfilesByMasterProfile_Ok(
      new MatchProfileWrapper(matchProfile1),
      new MatchProfileWrapper(matchProfile2),
      new ActionProfileWrapper(actionProfile1),
      new ActionProfileWrapper(actionProfile2),
      MATCH_PROFILES_PATH, ACTION_PROFILES_PATH, MATCH_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should return detail profiles by master profile (match to match)")
  @Test
  void getDetailsByMasterProfile_Ok_matchToMatch() {
    getDetailProfilesByMasterProfile_Ok(
      new MatchProfileWrapper(matchProfile1),
      new MatchProfileWrapper(matchProfile2),
      new MatchProfileWrapper(matchProfile3),
      new MatchProfileWrapper(matchProfile4),
      MATCH_PROFILES_PATH, MATCH_PROFILES_PATH, MATCH_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should return detail profiles by master profile (job to match to action)")
  @Test
  void getDetailsByMasterProfile_Ok_jobToMatchToAction() {
    var matchProfileWrapper = new MatchProfileWrapper(matchProfile1);
    var jobProfile = createJobProfileWithMatch(new JobProfileWrapper(jobProfile1),
      new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1), matchProfileWrapper);

    var jobWrapper = postProfile(new JobProfileWrapper(jobProfile), JOB_PROFILES_PATH);

    getRequest(getProfileDetailsUrl(jobWrapper.getProfile().getId()),
      Map.of("masterType", JOB_PROFILE,
        "detailType", MATCH_PROFILE,
        "query", "name=" + matchProfileWrapper.getName()))
      .statusCode(is(HttpStatus.SC_OK))
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
  }

  @DisplayName("should return 404 Not Found when getting detail profiles by action master profile")
  @Test
  void getDetailsByMasterProfile_NotFound_action() {
    getDetailProfilesByMasterProfile_NotFound(ACTION_PROFILE);
  }

  @DisplayName("should return 404 Not Found when getting detail profiles by job master profile")
  @Test
  void getDetailsByMasterProfile_NotFound_job() {
    getDetailProfilesByMasterProfile_NotFound(JOB_PROFILE);
  }

  @DisplayName("should return 404 Not Found when getting detail profiles by match master profile")
  @Test
  void getDetailsByMasterProfile_NotFound_match() {
    getDetailProfilesByMasterProfile_NotFound(MATCH_PROFILE);
  }

  @DisplayName("should return 400 Bad Request when getting detail profiles with wrong query parameter")
  @Test
  void getDetailsByMasterProfile_WrongQueryParameter() {
    getRequest(getProfileDetailsUrl(UUID.randomUUID().toString()), Map.of("masterType", "foo"))
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .body(is("The specified type: foo is wrong. It should be " + Arrays.toString(values())));
  }

  @DisplayName("should return empty details list with master profile (job to match to action)")
  @Test
  void getDetailsByMasterProfile_emptyDetailsListWithMasterProfile_jobToMatchToAction() {
    var jobProfile = createJobProfileWithMatch(new JobProfileWrapper(jobProfile1),
      new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1), new MatchProfileWrapper(matchProfile1));

    var jobWrapper = postProfile(new JobProfileWrapper(jobProfile), JOB_PROFILES_PATH);
    getRequest(getProfileDetailsUrl(jobWrapper.getId()), Map.of("masterType", JOB_PROFILE))
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
  }

  @DisplayName("should return empty details list with master profile (match to action)")
  @Test
  void getDetailsByMasterProfile_emptyDetailsListWithMasterProfile_matchToAction() {
    var masterProfileWrapper = postProfile(new MatchProfileWrapper(matchProfile1), MATCH_PROFILES_PATH);

    getRequest(getProfileDetailsUrl(masterProfileWrapper.getId()), Map.of("masterType", MATCH_PROFILE.value()))
      .statusCode(HttpStatus.SC_OK)
      .body("contentType", is(MATCH_PROFILE.value()))
      .body("id", is(masterProfileWrapper.getId()))
      .body("content.id", is(masterProfileWrapper.getId()))
      .body("content.userInfo.firstName", is(masterProfileWrapper.getUserInfo().getFirstName()))
      .body("content.userInfo.lastName", is(masterProfileWrapper.getUserInfo().getLastName()))
      .body("content.userInfo.userName", is(masterProfileWrapper.getUserInfo().getUserName()))
      .body("content.metadata.createdByUserId", is(masterProfileWrapper.getMetadata().getCreatedByUserId()))
      .body("content.metadata.updatedByUserId", is(masterProfileWrapper.getMetadata().getUpdatedByUserId()))
      .body("childSnapshotWrappers.size()", is(0));
  }

  @DisplayName("should return detail profiles sorted by name (action to mapping)")
  @Test
  void getDetailProfilesByMasterProfile_sortByName_Ok_actionToMapping() {
    getDetailProfilesByMasterProfile_sortByName_Ok(
      new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1),
      new MappingProfileWrapper(mappingProfile2),
      new MappingProfileWrapper(mappingProfile3),
      ACTION_PROFILES_PATH, MAPPING_PROFILES_PATH, ACTION_PROFILE, MAPPING_PROFILE);
  }

  @DisplayName("should return detail profiles sorted by name (match to action)")
  @Test
  void getDetailProfilesByMasterProfile_sortByName_Ok_matchToAction() {
    getDetailProfilesByMasterProfile_sortByName_Ok(
      new MatchProfileWrapper(matchProfile1),
      new ActionProfileWrapper(actionProfile1),
      new ActionProfileWrapper(actionProfile2),
      new ActionProfileWrapper(actionProfile4),
      MATCH_PROFILES_PATH, ACTION_PROFILES_PATH, MATCH_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should return detail profiles sorted by name (match to match)")
  @Test
  void getDetailProfilesByMasterProfile_sortByName_Ok_matchToMatch() {
    getDetailProfilesByMasterProfile_sortByName_Ok(
      new MatchProfileWrapper(matchProfile1),
      new MatchProfileWrapper(matchProfile2),
      new MatchProfileWrapper(matchProfile3),
      new MatchProfileWrapper(matchProfile4),
      MATCH_PROFILES_PATH, MATCH_PROFILES_PATH, MATCH_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should return detail profiles sorted by name (job to match)")
  @Test
  void getDetailProfilesByMasterProfile_sortByName_Ok_jobToMatch() {
    var matchProfileWrapper = new MatchProfileWrapper(matchProfile2);
    var jobProfile = createJobProfileWithMatch(new JobProfileWrapper(jobProfile1),
      new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1), matchProfileWrapper);

    var jobWrapper = postProfile(new JobProfileWrapper(jobProfile), JOB_PROFILES_PATH);

    //searching by description and sorting by name
    getRequest(getProfileDetailsUrl(jobWrapper.getId()), Map.of(
      "masterType", JOB_PROFILE.value(),
      "detailType", MATCH_PROFILE.value(),
      "query", "description=test-description and (cql.allRecords=1) sortBy name"))
      .statusCode(is(HttpStatus.SC_OK))
      .log().all()
      .body("contentType", is(JOB_PROFILE.value()))
      .body("childSnapshotWrappers.size()", is(1))
      .body("childSnapshotWrappers[0].content.name", is(matchProfileWrapper.getName()));
  }

  @DisplayName("should return master profiles by detail profile (action to mapping)")
  @Test
  void getMastersByDetailProfile_Ok_actionToMapping() {
    getMastersByDetailProfile_Ok(
      new ActionProfileWrapper(actionProfile3),
      new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1),
      new MappingProfileWrapper(mappingProfile2),
      ACTION_PROFILES_PATH, MAPPING_PROFILES_PATH, ACTION_PROFILE, MAPPING_PROFILE);
  }

  @DisplayName("should return master profiles by detail profile (match to action)")
  @Test
  void getMastersByDetailProfile_Ok_matchToAction() {
    getMastersByDetailProfile_Ok(
      new MatchProfileWrapper(matchProfile1),
      new MatchProfileWrapper(matchProfile2),
      new ActionProfileWrapper(actionProfile1),
      new ActionProfileWrapper(actionProfile2),
      MATCH_PROFILES_PATH, ACTION_PROFILES_PATH, MATCH_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should return master profiles by detail profile (match to match)")
  @Test
  void getMastersByDetailProfile_Ok_matchToMatch() {
    getMastersByDetailProfile_Ok(
      new MatchProfileWrapper(matchProfile1),
      new MatchProfileWrapper(matchProfile2),
      new MatchProfileWrapper(matchProfile3),
      new MatchProfileWrapper(matchProfile4),
      MATCH_PROFILES_PATH, MATCH_PROFILES_PATH, MATCH_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should return master profiles by detail profile (job to match to action)")
  @Test
  void getMastersByDetailProfile_Ok_jobToMatchToAction() {
    var matchProfileWrapper = new MatchProfileWrapper(matchProfile1);
    var jobProfile = createJobProfileWithMatch(
      new JobProfileWrapper(jobProfile1),
      new ActionProfileWrapper(actionProfile2),
      new MappingProfileWrapper(mappingProfile1),
      matchProfileWrapper);

    var jobWrapper = postProfile(new JobProfileWrapper(jobProfile), JOB_PROFILES_PATH);
    getRequest(getMastersByDetailUrl(matchProfileWrapper.getId()),
      Map.of(
        "detailType", MATCH_PROFILE.value(),
        "masterType", JOB_PROFILE.value(),
        "query", "name=" + jobWrapper.getName()
      ))
      .statusCode(is(HttpStatus.SC_OK))
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
  }

  @DisplayName("should return 404 Not Found when getting master profiles by action detail profile")
  @Test
  void getMastersByDetailProfile_NotFound_action() {
    getMastersByDetailProfile_NotFound(ACTION_PROFILE);
  }

  @DisplayName("should return 404 Not Found when getting master profiles by mapping detail profile")
  @Test
  void getMastersByDetailProfile_NotFound_mapping() {
    getMastersByDetailProfile_NotFound(MAPPING_PROFILE);
  }

  @DisplayName("should return 404 Not Found when getting master profiles by match detail profile")
  @Test
  void getMastersByDetailProfile_NotFound_match() {
    getMastersByDetailProfile_NotFound(MATCH_PROFILE);
  }

  @DisplayName("should return 400 Bad Request when getting master profiles with wrong query parameter")
  @Test
  void getMastersByDetailProfile_WrongQueryParameter() {
    getRequest(getMastersByDetailUrl(UUID.randomUUID().toString()), Map.of("detailType", "foo"))
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .body(is("The specified type: foo is wrong. It should be " + Arrays.toString(values())));
  }

  @DisplayName("should return empty masters list with match detail profile")
  @Test
  void getMastersByDetailProfile_emptyMastersListWithDetailProfile_match() {
    getMastersByDetailProfile_emptyMastersListWithDetailProfile(new MatchProfileWrapper(matchProfile1),
      MATCH_PROFILES_PATH, MATCH_PROFILE);
  }

  @DisplayName("should return empty masters list with mapping detail profile")
  @Test
  void getMastersByDetailProfile_emptyMastersListWithDetailProfile_mapping() {
    getMastersByDetailProfile_emptyMastersListWithDetailProfile(new MappingProfileWrapper(mappingProfile1),
      MAPPING_PROFILES_PATH, MAPPING_PROFILE);
  }

  @DisplayName("should return master profiles sorted by name (action to mapping)")
  @Test
  void getMastersByDetailProfile_sortBy_Ok_actionToMapping() {
    getMastersByDetailProfile_sortBy_Ok(
      new ActionProfileWrapper(actionProfile1),
      new ActionProfileWrapper(actionProfile2),
      new ActionProfileWrapper(actionProfile4),
      new MappingProfileWrapper(mappingProfile1),
      ACTION_PROFILES_PATH, MAPPING_PROFILES_PATH, ACTION_PROFILE, MAPPING_PROFILE);
  }

  @DisplayName("should return master profiles sorted by name (match to action)")
  @Test
  void getMastersByDetailProfile_sortBy_Ok_matchToAction() {
    getMastersByDetailProfile_sortBy_Ok(
      new MatchProfileWrapper(matchProfile2),
      new MatchProfileWrapper(matchProfile3),
      new MatchProfileWrapper(matchProfile4),
      new ActionProfileWrapper(actionProfile1),
      MATCH_PROFILES_PATH, ACTION_PROFILES_PATH, MATCH_PROFILE, ACTION_PROFILE);
  }

  @DisplayName("should return master profiles sorted by name (match to match)")
  @Test
  void getMastersByDetailProfile_sortBy_Ok_matchToMatch() {
    getMastersByDetailProfile_sortBy_Ok(
      new MatchProfileWrapper(matchProfile2),
      new MatchProfileWrapper(matchProfile3),
      new MatchProfileWrapper(matchProfile4),
      new MatchProfileWrapper(matchProfile1),
      MATCH_PROFILES_PATH, MATCH_PROFILES_PATH, MATCH_PROFILE, MATCH_PROFILE);
  }

  @DisplayName("should return master profiles sorted by name (job to match to action)")
  @Test
  void getMastersByDetailProfile_sortBy_Ok_jobToMatchToAction() {
    var matchProfileWrapper = new MatchProfileWrapper(matchProfile1);
    var jobProfile = createJobProfileWithMatch(
      new JobProfileWrapper(jobProfile1),
      new ActionProfileWrapper(actionProfile1),
      new MappingProfileWrapper(mappingProfile1), matchProfileWrapper);

    var jobWrapper = postProfile(new JobProfileWrapper(jobProfile), JOB_PROFILES_PATH);

    getRequest(getMastersByDetailUrl(matchProfileWrapper.getId()),
      Map.of(
        "detailType", MATCH_PROFILE.value(),
        "masterType", JOB_PROFILE.value(),
        "query", "description=\"test*\" or description==\"*description\" sortBy name"
      ))
      .statusCode(is(HttpStatus.SC_OK))
      .body("contentType", is(MATCH_PROFILE.value()))
      .body("childSnapshotWrappers.size()", is(1))
      .body("childSnapshotWrappers[0].content.name", is(jobWrapper.getName()));
  }

  private String getMastersByDetailUrl(String id) {
    return MASTERS_BY_DETAIL_PATH.replace("{detailId}", id);
  }

  private String getProfileDetailsUrl(String id) {
    return DETAILS_BY_MASTER_PATH.replace("{masterId}", id);
  }

  private Map<String, String> queryParams(ProfileType masterProfileType, ProfileType detailProfileType) {
    return Map.of("master", masterProfileType.value(), "detail", detailProfileType.value());
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private NotFoundOnPutFixture setUpNotFoundOnPutFixture() {
    var jobProfileId = UUID.randomUUID().toString();
    var actionProfileId = UUID.randomUUID().toString();
    var firstMatchProfileId = UUID.randomUUID().toString();
    var secondMatchProfileId = UUID.randomUUID().toString();
    var mappingProfileId = UUID.randomUUID().toString();

    final var existingJobProfileWrapper = new JobProfileWrapper(new JobProfileUpdateDto()
      .withProfile(new JobProfile()
        .withId(jobProfileId)
        .withName("Existing JobProfile")
        .withDataType(MARC)
        .withHidden(false)
        .withDescription("test-description")));

    final var existingActionProfileWrapper = new ActionProfileWrapper(new ActionProfileUpdateDto()
      .withProfile(new ActionProfile()
        .withId(actionProfileId)
        .withName("Existing ActionProfile")
        .withFolioRecord(MARC_BIBLIOGRAPHIC)
        .withAction(UPDATE)
        .withHidden(false)
        .withDescription("test-description")));

    final var existingMatchProfileWrapper = new MatchProfileWrapper(new MatchProfileUpdateDto()
      .withProfile(new MatchProfile()
        .withId(firstMatchProfileId)
        .withName("Existing MatchProfile")
        .withMatchDetails(Lists.newArrayList())
        .withHidden(false)
        .withDescription("test-description")));

    final var secondExistingMatchProfileWrapper = new MatchProfileWrapper(new MatchProfileUpdateDto()
      .withProfile(new MatchProfile()
        .withId(secondMatchProfileId)
        .withName("Second Existing MatchProfile")
        .withMatchDetails(Lists.newArrayList())
        .withHidden(false)
        .withDescription("test-description")));

    final var existingMappingProfileWrapper = new MappingProfileWrapper(new MappingProfileUpdateDto()
      .withProfile(new MappingProfile()
        .withId(mappingProfileId)
        .withName("Existing MappingProfile")
        .withExistingRecordType(EntityType.INSTANCE)
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withMappingDetails(new MappingDetail())
        .withHidden(false)
        .withDescription("test-description")));

    postRequest(ACTION_PROFILES_PATH, existingActionProfileWrapper.getProfile()).log().all()
      .statusCode(HttpStatus.SC_CREATED)
      .body("profile.id", is(actionProfileId));

    postRequest(MATCH_PROFILES_PATH, existingMatchProfileWrapper.getProfile()).log().all()
      .statusCode(HttpStatus.SC_CREATED)
      .body("profile.id", is(firstMatchProfileId));

    postRequest(MATCH_PROFILES_PATH, secondExistingMatchProfileWrapper.getProfile()).log().all()
      .statusCode(HttpStatus.SC_CREATED)
      .body("profile.id", is(secondMatchProfileId));

    postRequest(MAPPING_PROFILES_PATH, existingMappingProfileWrapper.getProfile()).log().all()
      .statusCode(HttpStatus.SC_CREATED)
      .body("profile.id", is(mappingProfileId));

    return new NotFoundOnPutFixture(existingJobProfileWrapper, actionProfileId, mappingProfileId,
      firstMatchProfileId, secondMatchProfileId);
  }

  private <M, D> void shouldPostAndGetById(ProfileWrapper<M> masterProfileWrapper,
                                           ProfileWrapper<D> detailProfileWrapper,
                                           String masterProfileUrl, String detailProfileUrl,
                                           ProfileType masterProfileType, ProfileType detailProfileType) {
    var masterWrapper = postProfile(masterProfileWrapper, masterProfileUrl);
    var detailWrapper = postProfile(detailProfileWrapper, detailProfileUrl);

    var profileAssociation = new ProfileAssociation()
      .withMasterProfileId(masterWrapper.getId())
      .withDetailProfileId(detailWrapper.getId())
      .withMasterProfileType(valueOf(masterProfileType.value()))
      .withDetailProfileType(valueOf(detailProfileType.value()))
      .withOrder(5)
      .withTriggered(true);

    var savedProfileAssociation = postRequest(ASSOCIATED_PROFILES_PATH, profileAssociation,
      queryParams(masterProfileType, detailProfileType))
      .statusCode(HttpStatus.SC_CREATED)
      .extract().body().as(ProfileAssociation.class);

    getRequest(associatedProfileByIdUrl(savedProfileAssociation.getId()),
      queryParams(masterProfileType, detailProfileType))
      .statusCode(HttpStatus.SC_OK)
      .body("id", is(savedProfileAssociation.getId()))
      .body("masterProfileId", is(masterWrapper.getId()))
      .body("detailProfileId", is(detailWrapper.getId()))
      .body("order", is(savedProfileAssociation.getOrder()));
  }

  private void shouldReturnNotFoundOnDelete(ProfileType masterProfileType,
                                            ProfileType detailProfileType) {
    deleteRequest(associatedProfileByIdUrl(UUID.randomUUID().toString()),
      queryParams(masterProfileType, detailProfileType))
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  private static String associatedProfileByIdUrl(String id) {
    return ASSOCIATED_PROFILES_PATH + "/" + id;
  }

  private <M, D> void shouldDeleteProfileOnDelete(ProfileWrapper<M> masterWrapper,
                                                  ProfileWrapper<D> detailWrapper,
                                                  String masterProfileUrl, String detailProfileUrl,
                                                  ProfileType masterProfileType, ProfileType detailProfileType) {
    var detailProfileWrapper = postProfile(detailWrapper, detailProfileUrl);
    var masterProfileWrapper = postProfile(masterWrapper, masterProfileUrl);

    var profileAssociation = new ProfileAssociation()
      .withMasterProfileId(masterProfileWrapper.getId())
      .withDetailProfileId(detailProfileWrapper.getId())
      .withMasterProfileType(valueOf(masterProfileType.value()))
      .withDetailProfileType(valueOf(detailProfileType.value()))
      .withOrder(10)
      .withTriggered(false);

    var savedProfileAssociation = postRequest(ASSOCIATED_PROFILES_PATH, profileAssociation,
      queryParams(masterProfileType, detailProfileType))
      .statusCode(is(HttpStatus.SC_CREATED))
      .and()
      .extract().body().as(ProfileAssociation.class);
    deleteRequest(associatedProfileByIdUrl(savedProfileAssociation.getId()),
      queryParams(masterProfileType, detailProfileType))
      .statusCode(HttpStatus.SC_NO_CONTENT);
  }

  private void shouldReturnBadRequestOnPut(ProfileType masterContentType,
                                           ProfileType detailContentType) {
    putRequest(associatedProfileByIdUrl(UUID.randomUUID().toString()), new JobProfile(),
      queryParams(masterContentType, detailContentType))
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  private void shouldReturnNotFoundOnPut(ProfileType masterType, String masterId,
                                         ProfileType detailType, String detailId) {

    var profileAssociation = new ProfileAssociation()
      .withId(UUID.randomUUID().toString())
      .withMasterProfileId(masterId)
      .withDetailProfileId(detailId)
      .withMasterProfileType(masterType)
      .withDetailProfileType(detailType);
    putRequest(associatedProfileByIdUrl(UUID.randomUUID().toString()), profileAssociation,
      queryParams(masterType, detailType))
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  private <M, D> void shouldUpdateProfileAssociationOnPut(ProfileWrapper<M> masterWrapper,
                                                          ProfileWrapper<D> detailWrapper,
                                                          ProfileWrapper<D> detailWrapper2,
                                                          String masterProfileUrl, String detailProfileUrl,
                                                          ProfileType masterContentType,
                                                          ProfileType detailContentType) {
    var masterProfileWrapper = postProfile(masterWrapper, masterProfileUrl);
    var detailProfileWrapper = postProfile(detailWrapper, detailProfileUrl);

    var profileAssociation = new ProfileAssociation()
      .withMasterProfileId(masterProfileWrapper.getId())
      .withDetailProfileId(detailProfileWrapper.getId())
      .withMasterProfileType(valueOf(masterContentType.value()))
      .withDetailProfileType(valueOf(detailContentType.value()))
      .withOrder(7)
      .withTriggered(true);

    var savedProfileAssociation = postRequest(ASSOCIATED_PROFILES_PATH, profileAssociation,
      queryParams(masterContentType, detailContentType))
      .statusCode(is(HttpStatus.SC_CREATED))
      .and()
      .extract().body().as(ProfileAssociation.class);

    var detailProfileWrapper2 = postProfile(detailWrapper2, detailProfileUrl);
    savedProfileAssociation.setDetailProfileId(detailProfileWrapper2.getId());
    putRequest(associatedProfileByIdUrl(savedProfileAssociation.getId()), savedProfileAssociation,
      queryParams(masterContentType, detailContentType))
      .statusCode(is(HttpStatus.SC_OK))
      .body("id", is(savedProfileAssociation.getId()))
      .body("masterProfileId", is(masterProfileWrapper.getId()))
      .body("detailProfileId", is(detailProfileWrapper2.getId()))
      .body("order", is(savedProfileAssociation.getOrder()));
  }

  private void getDetailProfilesByMasterProfile_NotFound(ProfileType masterContentType) {
    getRequest(getProfileDetailsUrl(UUID.randomUUID().toString()), Map.of("masterType", masterContentType.value()))
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  private void getMastersByDetailProfile_NotFound(ProfileType detailContentType) {
    getRequest(getMastersByDetailUrl(UUID.randomUUID().toString()), Map.of("detailType", detailContentType.value()))
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  private <D> void getMastersByDetailProfile_emptyMastersListWithDetailProfile(ProfileWrapper<D> detailWrapper,
                                                                               String detailProfileUrl,
                                                                               ProfileType detailProfileType) {
    var detailProfileWrapper = postProfile(detailWrapper, detailProfileUrl);
    getRequest(getMastersByDetailUrl(detailProfileWrapper.getId()), Map.of("detailType", detailProfileType.value()))
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
  }

  private void shouldReturnEmptyOkResultOnGetAll(ProfileType masterProfileType,
                                                 ProfileType detailProfileType) {
    getRequest(ASSOCIATED_PROFILES_PATH, queryParams(masterProfileType, detailProfileType))
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(0));
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private <M, D> void getMastersByDetailProfile_sortBy_Ok(ProfileWrapper<M> masterProfileWrapper1,
                                                          ProfileWrapper<M> masterProfileWrapper2,
                                                          ProfileWrapper<M> masterProfileWrapper3,
                                                          ProfileWrapper<D> detailProfileWrapper,
                                                          String masterProfileUrl, String detailProfileUrl,
                                                          ProfileType masterProfileType,
                                                          ProfileType detailProfileType) {
    var detailWrapper = postProfile(detailProfileWrapper, detailProfileUrl);
    var masterWrapper3 = postProfile(masterProfileWrapper3, masterProfileUrl);
    var masterWrapper2 = postProfile(masterProfileWrapper2, masterProfileUrl);
    var masterWrapper1 = postProfile(masterProfileWrapper1, masterProfileUrl);

    var profileAssociation3 =
      buildProfileAssociation(masterProfileType, detailProfileType, masterWrapper3, detailWrapper);
    postRequest(ASSOCIATED_PROFILES_PATH, profileAssociation3, queryParams(masterProfileType, detailProfileType))
      .statusCode(is(HttpStatus.SC_CREATED));

    var profileAssociation2 =
      buildProfileAssociation(masterProfileType, detailProfileType, masterWrapper2, detailWrapper);
    postRequest(ASSOCIATED_PROFILES_PATH, profileAssociation2, queryParams(masterProfileType, detailProfileType))
      .statusCode(is(HttpStatus.SC_CREATED));

    var profileAssociation1 =
      buildProfileAssociation(masterProfileType, detailProfileType, masterWrapper1, detailWrapper);
    postRequest(ASSOCIATED_PROFILES_PATH, profileAssociation1, queryParams(masterProfileType, detailProfileType))
      .statusCode(is(HttpStatus.SC_CREATED));

    getRequest(getMastersByDetailUrl(detailWrapper.getId()),
      Map.of(
        "detailType", detailProfileType.value(),
        "masterType", masterProfileType.value(),
        "query", "description=\"test*\" or description==\"*description\" sortBy name"))
      .statusCode(is(HttpStatus.SC_OK))
      .body("contentType", is(detailProfileType.value()))
      .body("childSnapshotWrappers.size()", is(2))
      .body("childSnapshotWrappers[0].content.name", is(masterWrapper1.getName()))
      .body("childSnapshotWrappers[1].content.name", is(masterWrapper2.getName()));
  }

  private <M, D> ProfileAssociation buildProfileAssociation(ProfileType masterProfileType,
                                                            ProfileType detailProfileType,
                                                            ProfileWrapper<M> masterWrapper,
                                                            ProfileWrapper<D> detailWrapper) {
    return new ProfileAssociation()
      .withMasterProfileId(masterWrapper.getId())
      .withDetailProfileId(detailWrapper.getId())
      .withMasterProfileType(valueOf(masterProfileType.value()))
      .withDetailProfileType(valueOf(detailProfileType.value()))
      .withOrder(7)
      .withTriggered(true);
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private <M, D> void getMastersByDetailProfile_Ok(ProfileWrapper<M> masterWrapper,
                                                   ProfileWrapper<M> masterWrapper2,
                                                   ProfileWrapper<D> detailWrapper, ProfileWrapper<D> detailWrapper2,
                                                   String masterProfileUrl, String detailProfileUrl,
                                                   ProfileType masterProfileType, ProfileType detailProfileType) {
    var masterProfileWrapper1 = postProfile(masterWrapper, masterProfileUrl);
    var masterProfileWrapper2 = postProfile(masterWrapper2, masterProfileUrl);
    var detailProfileWrapper1 = postProfile(detailWrapper, detailProfileUrl);
    var detailProfileWrapper2 = postProfile(detailWrapper2, detailProfileUrl);

    var profileAssociation1 =
      buildProfileAssociation(masterProfileType, detailProfileType, masterProfileWrapper1, detailProfileWrapper1);
    var profileAssociation2 =
      buildProfileAssociation(masterProfileType, detailProfileType, masterProfileWrapper2, detailProfileWrapper2);

    postRequest(ASSOCIATED_PROFILES_PATH, profileAssociation1, queryParams(masterProfileType, detailProfileType))
      .statusCode(is(HttpStatus.SC_CREATED));
    postRequest(ASSOCIATED_PROFILES_PATH, profileAssociation2, queryParams(masterProfileType, detailProfileType))
      .statusCode(is(HttpStatus.SC_CREATED));

    getRequest(getMastersByDetailUrl(detailProfileWrapper1.getId()),
      Map.of(
        "detailType", detailProfileType.value(),
        "masterType", masterProfileType.value(),
        "query", "name=" + masterProfileWrapper1.getName()))
      .statusCode(is(HttpStatus.SC_OK))
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
      .body("childSnapshotWrappers[0].content.userInfo.firstName",
        is(masterProfileWrapper1.getUserInfo().getFirstName()))
      .body("childSnapshotWrappers[0].content.userInfo.lastName", is(masterProfileWrapper1.getUserInfo().getLastName()))
      .body("childSnapshotWrappers[0].content.userInfo.userName",
        is(masterProfileWrapper1.getUserInfo().getUserName()));
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private <M, D> void getDetailProfilesByMasterProfile_sortByName_Ok(ProfileWrapper<M> masterWrapper,
                                                                     ProfileWrapper<D> detailWrapper,
                                                                     ProfileWrapper<D> detailWrapper2,
                                                                     ProfileWrapper<D> detailWrapper3,
                                                                     String masterProfileUrl, String detailProfileUrl,
                                                                     ProfileType masterProfileType,
                                                                     ProfileType detailProfileType) {
    var masterProfileWrapper1 = postProfile(masterWrapper, masterProfileUrl);
    var detailProfileWrapper2 = postProfile(detailWrapper2, detailProfileUrl);

    var profileAssociation2 =
      buildProfileAssociation(masterProfileType, detailProfileType, masterProfileWrapper1, detailProfileWrapper2);
    postRequest(ASSOCIATED_PROFILES_PATH, profileAssociation2, queryParams(masterProfileType, detailProfileType))
      .statusCode(is(HttpStatus.SC_CREATED));

    var detailProfileWrapper1 = postProfile(detailWrapper, detailProfileUrl);

    var profileAssociation1 =
      buildProfileAssociation(masterProfileType, detailProfileType, masterProfileWrapper1, detailProfileWrapper1);
    postRequest(ASSOCIATED_PROFILES_PATH, profileAssociation1, queryParams(masterProfileType, detailProfileType))
      .statusCode(is(HttpStatus.SC_CREATED));

    var detailProfileWrapper3 = postProfile(detailWrapper3, detailProfileUrl);
    var profileAssociation3 =
      buildProfileAssociation(masterProfileType, detailProfileType, masterProfileWrapper1, detailProfileWrapper3);
    postRequest(ASSOCIATED_PROFILES_PATH, profileAssociation3, queryParams(masterProfileType, detailProfileType))
      .statusCode(is(HttpStatus.SC_CREATED));

    getRequest(getProfileDetailsUrl(masterProfileWrapper1.getId()),
      Map.of(
        "masterType", masterProfileType.value(),
        "detailType", detailProfileType.value(),
        "query", "description=test-description and (cql.allRecords=1) sortBy name"))
      .statusCode(is(HttpStatus.SC_OK))
      .log().all()
      .body("contentType", is(masterProfileType.value()))
      .body("childSnapshotWrappers.size()", is(2))
      .body("childSnapshotWrappers[0].content.name", is(detailProfileWrapper1.getName()))
      .body("childSnapshotWrappers[1].content.name", is(detailProfileWrapper2.getName()));
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private <M, D> void getDetailProfilesByMasterProfile_Ok(ProfileWrapper<M> masterWrapper,
                                                          ProfileWrapper<M> masterWrapper2,
                                                          ProfileWrapper<D> detailWrapper,
                                                          ProfileWrapper<D> detailWrapper2,
                                                          String masterProfileUrl, String detailProfileUrl,
                                                          ProfileType masterProfileType,
                                                          ProfileType detailProfileType) {
    var masterProfileWrapper1 = postProfile(masterWrapper, masterProfileUrl);
    var masterProfileWrapper2 = postProfile(masterWrapper2, masterProfileUrl);
    var detailProfileWrapper1 = postProfile(detailWrapper, detailProfileUrl);
    var detailProfileWrapper2 = postProfile(detailWrapper2, detailProfileUrl);

    var profileAssociation1 =
      buildProfileAssociation(masterProfileType, detailProfileType, masterProfileWrapper1, detailProfileWrapper1);
    var profileAssociation2 =
      buildProfileAssociation(masterProfileType, detailProfileType, masterProfileWrapper2, detailProfileWrapper2);

    postRequest(ASSOCIATED_PROFILES_PATH, profileAssociation1, queryParams(masterProfileType, detailProfileType))
      .statusCode(is(HttpStatus.SC_CREATED));
    postRequest(ASSOCIATED_PROFILES_PATH, profileAssociation2, queryParams(masterProfileType, detailProfileType))
      .statusCode(is(HttpStatus.SC_CREATED));

    getRequest(getProfileDetailsUrl(masterProfileWrapper1.getId()),
      Map.of(
        "masterType", masterProfileType.value(),
        "detailType", detailProfileType.value(),
        "query", "name=" + detailProfileWrapper1.getName()))
      .statusCode(is(HttpStatus.SC_OK))
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
      .body("childSnapshotWrappers[0].content.userInfo.firstName",
        is(detailProfileWrapper1.getUserInfo().getFirstName()))
      .body("childSnapshotWrappers[0].content.userInfo.lastName", is(detailProfileWrapper1.getUserInfo().getLastName()))
      .body("childSnapshotWrappers[0].content.userInfo.userName",
        is(detailProfileWrapper1.getUserInfo().getUserName()));
  }

  private <M, D> void shouldReturnProfileAssociationListOnGet(ProfileWrapper<M> masterWrapper1,
                                                              ProfileWrapper<D> detailWrapper1,
                                                              String masterProfileUrl, String detailProfileUrl,
                                                              ProfileType masterProfileType,
                                                              ProfileType detailProfileType) {
    var masterProfileWrapper1 = postProfile(masterWrapper1, masterProfileUrl);
    var detailProfileWrapper1 = postProfile(detailWrapper1, detailProfileUrl);
    var profileAssociation =
      buildProfileAssociation(masterProfileType, detailProfileType, masterProfileWrapper1, detailProfileWrapper1)
        .withOrder(5);
    postRequest(ASSOCIATED_PROFILES_PATH, profileAssociation, queryParams(masterProfileType, detailProfileType))
      .statusCode(HttpStatus.SC_CREATED);
    getRequest(ASSOCIATED_PROFILES_PATH, queryParams(masterProfileType, detailProfileType))
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(1));
  }

  private void shouldReturnNotFoundOnGetById(ProfileType masterProfileType,
                                             ProfileType detailProfileType) {
    getRequest(associatedProfileByIdUrl(UUID.randomUUID().toString()),
      queryParams(masterProfileType, detailProfileType))
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  private JobProfileUpdateDto createJobProfileWithAction(JobProfileWrapper jobProfileWrapper,
                                                         ActionProfileWrapper actionProfileWrapper,
                                                         MappingProfileWrapper mappingProfileWrapper) {
    var mappingWrapper = postProfile(mappingProfileWrapper, MAPPING_PROFILES_PATH);
    var actionWrapper = postProfile(actionProfileWrapper, ACTION_PROFILES_PATH);

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

    return jobProfileWrapper.getProfile()
      .withAddedRelations(List.of(jobToActionAssociation, actionToMappingAssociation));
  }

  private JobProfileUpdateDto createJobProfileWithMatch(JobProfileWrapper jobProfileWrapper,
                                                        ActionProfileWrapper actionProfileWrapper,
                                                        MappingProfileWrapper mappingProfileWrapper,
                                                        MatchProfileWrapper matchProfileWrapper) {
    var mappingWrapper = postProfile(mappingProfileWrapper, MAPPING_PROFILES_PATH);
    var actionWrapper = postProfile(actionProfileWrapper, ACTION_PROFILES_PATH);
    var matchWrapper = postProfile(matchProfileWrapper, MATCH_PROFILES_PATH);

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

  private <T> ProfileWrapper<T> postProfile(ProfileWrapper<T> profileWrapper, String profileUrl) {
    T profile = postRequest(profileUrl, profileWrapper.getProfile()).log().all()
      .statusCode(HttpStatus.SC_CREATED)
      .and()
      .extract().body().as(profileWrapper.getProfileType());
    profileWrapper.setProfile(profile);
    return profileWrapper;
  }

  private record NotFoundOnPutFixture(JobProfileWrapper existingJobProfileWrapper, String actionProfileId,
                                      String mappingProfileId, String firstMatchProfileId,
                                      String secondMatchProfileId) {
  }
}
