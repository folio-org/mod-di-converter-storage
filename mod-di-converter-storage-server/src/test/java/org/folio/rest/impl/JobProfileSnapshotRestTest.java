package org.folio.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.CREATE;
import static org.folio.rest.jaxrs.model.ActionProfile.Action.UPDATE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.INSTANCE;
import static org.folio.rest.jaxrs.model.ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC;
import static org.folio.rest.jaxrs.model.JobProfile.DataType.MARC;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;
import static org.folio.rest.jaxrs.model.ReactToType.MATCH;
import static org.folio.rest.jaxrs.model.ReactToType.NON_MATCH;
import static org.folio.support.TestUtil.ACTION_PROFILES_PATH;
import static org.folio.support.TestUtil.JOB_PROFILES_PATH;
import static org.folio.support.TestUtil.JOB_PROFILE_ID_PARAM;
import static org.folio.support.TestUtil.JOB_PROFILE_SNAPSHOT_PATH;
import static org.folio.support.TestUtil.MAPPING_PROFILES_PATH;
import static org.folio.support.TestUtil.PROFILE_SNAPSHOT_PATH;
import static org.folio.support.TestUtil.PROFILE_TYPE_PARAM;

import com.google.common.collect.Lists;
import io.vertx.core.json.jackson.DatabindCodec;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
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
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.support.AbstractRestTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobProfileSnapshotRestTest extends AbstractRestTest {

  private JobProfileUpdateDto jobProfile;
  private MatchProfileUpdateDto matchProfile;
  private ActionProfileUpdateDto actionProfile;
  private MappingProfileUpdateDto mappingProfile;

  @BeforeEach
  void prepare() {
    mappingProfile = postMappingProfile(new MappingProfileUpdateDto()
      .withProfile(new MappingProfile().withName("testMappingProfile1").withDescription("test-description")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)));

    actionProfile = postActionProfile(new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withName("testActionProfile1").withDescription("test-description")
        .withAction(UPDATE).withFolioRecord(MARC_BIBLIOGRAPHIC))
      .withAddedRelations(Collections.singletonList(new ProfileAssociation()
        .withMasterProfileId(null)
        .withDetailProfileId(mappingProfile.getId())
        .withMasterProfileType(ProfileType.ACTION_PROFILE)
        .withDetailProfileType(ProfileType.MAPPING_PROFILE)
        .withOrder(0)))
    );

    matchProfile = postMatchProfile(new MatchProfileUpdateDto()
      .withProfile(new MatchProfile().withName("testMatchProfile1")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withDescription("test-description"))
      .withAddedRelations(Collections.singletonList(new ProfileAssociation()
        .withMasterProfileId(null)
        .withDetailProfileId(actionProfile.getId())
        .withMasterProfileType(ProfileType.MATCH_PROFILE)
        .withDetailProfileType(ProfileType.ACTION_PROFILE)
        .withReactTo(NON_MATCH)
        .withJobProfileId(null)
        .withOrder(0)))
    );

    jobProfile = postJobProfile(new JobProfileUpdateDto()
      .withProfile(new JobProfile().withName("testJobProfile1").withDataType(MARC).withDescription("test-description"))
      .withAddedRelations(Lists.newArrayList(new ProfileAssociation()
          .withMasterProfileId(null)
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
      )));
  }

  @DisplayName("should return 404 Not Found on GET by id when snapshot does not exist")
  @Test
  void shouldReturnNotFoundOnGetById() {
    String id = UUID.randomUUID().toString();
    getRequest(JOB_PROFILE_SNAPSHOT_PATH + "/" + id)
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @DisplayName("should return 500 Internal Server Error on POST to build job profile snapshot by id")
  @Test
  void shouldBuildAndReturn500OnGetById() {
    String id = UUID.randomUUID().toString();
    postRequest(JOB_PROFILE_SNAPSHOT_PATH + "/" + id, "")
      .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
  }

  @DisplayName("should return 500 Internal Server Error on GET snapshot by id")
  @Test
  void shouldBuildAndReturn500OnGetSnapshotById() {
    String id = UUID.randomUUID().toString();
    getRequest(PROFILE_SNAPSHOT_PATH + "/" + id, Map.of(PROFILE_TYPE_PARAM, JOB_PROFILE.value()))
      .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
  }

  @DisplayName("should return 400 Bad Request when profileType query param is missing")
  @Test
  void shouldReturnBadRequestWhenProfileTypeQueryParamIsMissed() {
    getRequest(PROFILE_SNAPSHOT_PATH + "/" + UUID.randomUUID())
      .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  @DisplayName("should return 400 Bad Request when profileType query param is invalid")
  @Test
  void shouldReturnBadRequestWhenProfileTypeQueryParamIsInvalid() {
    getRequest(PROFILE_SNAPSHOT_PATH + "/" + UUID.randomUUID(), Map.of(PROFILE_TYPE_PARAM, "invalid param"))
      .statusCode(HttpStatus.SC_BAD_REQUEST);
  }

  @DisplayName("should return snapshot wrapper on GET by profile id for job profile with child snapshot wrappers")
  @Test
  void shouldReturnSnapshotWrapperOnGetByProfileIdForJobProfileWithEmptyChildSnapshotWrappers() {
    ProfileSnapshotWrapper jobProfileSnapshot = getRequest(PROFILE_SNAPSHOT_PATH + "/" + jobProfile.getId(),
      Map.of(PROFILE_TYPE_PARAM, JOB_PROFILE.value(), JOB_PROFILE_ID_PARAM, jobProfile.getId()))
      .statusCode(HttpStatus.SC_OK)
      .extract().body().as(ProfileSnapshotWrapper.class);

    JobProfile actualJobProfile =
      DatabindCodec.mapper().convertValue(jobProfileSnapshot.getContent(), JobProfile.class);
    assertThat(actualJobProfile.getId()).isEqualTo(jobProfile.getId());
    assertThat(jobProfileSnapshot.getChildSnapshotWrappers()).hasSize(1);

    ProfileSnapshotWrapper matchProfileSnapshot = jobProfileSnapshot.getChildSnapshotWrappers().getFirst();
    MatchProfile actualMatchProfile =
      DatabindCodec.mapper().convertValue(matchProfileSnapshot.getContent(), MatchProfile.class);
    assertThat(actualMatchProfile.getId()).isEqualTo(matchProfile.getId());
    assertThat(matchProfileSnapshot.getChildSnapshotWrappers()).hasSize(1);

    ProfileSnapshotWrapper actionProfileSnapshot = matchProfileSnapshot.getChildSnapshotWrappers().getFirst();
    ActionProfile actualActionProfile =
      DatabindCodec.mapper().convertValue(actionProfileSnapshot.getContent(), ActionProfile.class);
    assertThat(actualActionProfile.getId()).isEqualTo(actionProfile.getId());
    assertThat(actionProfileSnapshot.getChildSnapshotWrappers()).hasSize(1);

    ProfileSnapshotWrapper mappingProfileSnapshot = actionProfileSnapshot.getChildSnapshotWrappers().getFirst();
    MappingProfile mappingActionProfile =
      DatabindCodec.mapper().convertValue(mappingProfileSnapshot.getContent(), MappingProfile.class);
    assertThat(mappingActionProfile.getId()).isEqualTo(mappingProfile.getId());
    assertThat(mappingProfileSnapshot.getChildSnapshotWrappers()).isEmpty();
  }

  @DisplayName("should return snapshot wrapper for match profile with empty child snapshot wrappers")
  @Test
  void shouldReturnSnapshotWrapperOnGetByProfileIdForMatchProfileWithEmptyChildSnapshotWrappers() {
    var matchProfileSnapshot = getRequest(PROFILE_SNAPSHOT_PATH + "/" + matchProfile.getId(),
      Map.of(PROFILE_TYPE_PARAM, MATCH_PROFILE.value(), JOB_PROFILE_ID_PARAM, jobProfile.getId()))
      .statusCode(HttpStatus.SC_OK)
      .extract().body().as(ProfileSnapshotWrapper.class);

    var actualMatchProfile = DatabindCodec.mapper().convertValue(matchProfileSnapshot.getContent(), MatchProfile.class);
    assertThat(actualMatchProfile.getId()).isEqualTo(matchProfile.getId());
    assertThat(matchProfileSnapshot.getChildSnapshotWrappers()).isEmpty();
  }

  @DisplayName("should return snapshot wrapper on GET by profile id for action profile")
  @Test
  void shouldReturnSnapshotWrapperOnGetByProfileIdForActionProfile() {
    var actionProfileSnapshot = getRequest(PROFILE_SNAPSHOT_PATH + "/" + actionProfile.getId(),
      Map.of(PROFILE_TYPE_PARAM, ACTION_PROFILE.value()))
      .statusCode(HttpStatus.SC_OK)
      .extract().body().as(ProfileSnapshotWrapper.class);

    assertThat(actionProfileSnapshot.getChildSnapshotWrappers()).hasSize(1);
  }

  @DisplayName("should return snapshot wrapper on GET by profile id for job profile")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldReturnSnapshotWrapperOnGetByProfileIdForJobProfile() {
    var actionProfile2 = postRequest(ACTION_PROFILES_PATH, new ActionProfileUpdateDto()
      .withProfile(
        new ActionProfile()
          .withId(UUID.randomUUID().toString())
          .withName("actionProfile2")
          .withDescription("actionProfile2-description")
          .withAction(CREATE)
          .withFolioRecord(INSTANCE)))
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(ActionProfileUpdateDto.class);

    var mappingProfile2 = postRequest(MAPPING_PROFILES_PATH, new MappingProfileUpdateDto()
      .withProfile(
        new MappingProfile()
          .withName("testMapping2")
          .withDescription("testMapping2-description")
          .withId(UUID.randomUUID().toString())
          .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
          .withExistingRecordType(EntityType.INSTANCE)))
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(MappingProfileUpdateDto.class);

    var jobProfile2 = postRequest(JOB_PROFILES_PATH, new JobProfileUpdateDto()
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
          .withOrder(0))))
      .statusCode(HttpStatus.SC_CREATED)
      .extract().as(JobProfileUpdateDto.class);

    var jobProfileSnapshot = getRequest(PROFILE_SNAPSHOT_PATH + "/" + jobProfile2.getId(),
      Map.of(PROFILE_TYPE_PARAM, JOB_PROFILE.value(), JOB_PROFILE_ID_PARAM, jobProfile2.getId()))
      .statusCode(HttpStatus.SC_OK)
      .extract().body().as(ProfileSnapshotWrapper.class);

    var actualJobProfile = DatabindCodec.mapper().convertValue(jobProfileSnapshot.getContent(), JobProfile.class);
    assertThat(actualJobProfile.getId()).isEqualTo(jobProfile2.getId());
    assertThat(jobProfileSnapshot.getChildSnapshotWrappers()).hasSize(1);

    var matchProfileSnapshot = jobProfileSnapshot.getChildSnapshotWrappers().getFirst();
    var actualMatchProfile = DatabindCodec.mapper().convertValue(matchProfileSnapshot.getContent(), MatchProfile.class);
    assertThat(actualMatchProfile.getId()).isEqualTo(matchProfile.getId());
    assertThat(matchProfileSnapshot.getChildSnapshotWrappers()).hasSize(1);

    var actionProfileSnapshot = matchProfileSnapshot.getChildSnapshotWrappers().getFirst();
    var actualActionProfile =
      DatabindCodec.mapper().convertValue(actionProfileSnapshot.getContent(), ActionProfile.class);
    assertThat(actualActionProfile.getId()).isEqualTo(actionProfile2.getId());
    assertThat(actionProfileSnapshot.getChildSnapshotWrappers()).hasSize(1);
  }

  @DisplayName("should return job profile snapshot without match child wrappers when jobProfileId param is missing")
  @Test
  void shouldReturnSnapshotWrapperForJobProfileWithoutMatchProfileChildWrappersWhenJobProfileIdParamIsMissed() {
    var jobProfileSnapshot = getRequest(PROFILE_SNAPSHOT_PATH + "/" + jobProfile.getId(),
      Map.of(PROFILE_TYPE_PARAM, JOB_PROFILE.value()))
      .statusCode(HttpStatus.SC_OK)
      .extract().body().as(ProfileSnapshotWrapper.class);

    var actualJobProfile = DatabindCodec.mapper().convertValue(jobProfileSnapshot.getContent(), JobProfile.class);
    assertThat(actualJobProfile.getId()).isEqualTo(jobProfile.getId());
    assertThat(jobProfileSnapshot.getChildSnapshotWrappers()).hasSize(1);

    var matchProfileSnapshot = jobProfileSnapshot.getChildSnapshotWrappers().getFirst();
    var actualMatchProfile = DatabindCodec.mapper().convertValue(matchProfileSnapshot.getContent(), MatchProfile.class);
    assertThat(actualMatchProfile.getId()).isEqualTo(matchProfile.getId());
    assertThat(matchProfileSnapshot.getChildSnapshotWrappers()).isEmpty();
  }
}
