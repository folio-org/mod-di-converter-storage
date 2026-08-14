package org.folio.services.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MAPPING_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.folio.dao.snapshot.ProfileSnapshotDao;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ReactToType;
import org.folio.support.AbstractUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProfileSnapshotServiceTest extends AbstractUnitTest {

  private final JobProfile jobProfile = new JobProfile().withId(UUID.randomUUID().toString());
  private final ProfileAssociation jobProfileSnapshotAssociation = new ProfileAssociation();
  private final MatchProfile matchProfile = new MatchProfile().withId(UUID.randomUUID().toString());
  private final ProfileAssociation matchProfileSnapshotAssociation = new ProfileAssociation();
  private final ActionProfile actionProfile = new ActionProfile().withId(UUID.randomUUID().toString());
  private final ProfileAssociation actionProfileSnapshotAssociation = new ProfileAssociation();
  private final MappingProfile mappingProfile = new MappingProfile().withId(UUID.randomUUID().toString());
  private final ProfileAssociation mappingProfileSnapshotAssociation = new ProfileAssociation();

  @Autowired
  private ProfileSnapshotDao dao;
  @Autowired
  private ProfileSnapshotService service;
  private List<ProfileAssociation> associations;

  @BeforeEach
  void setUp() {
    final String jobProfileWrapperId = UUID.randomUUID().toString();
    final String matchProfileWrapperId = UUID.randomUUID().toString();
    final String actionProfileWrapperId = UUID.randomUUID().toString();
    final String mappingProfileWrapperId = UUID.randomUUID().toString();

    jobProfileSnapshotAssociation.setId(UUID.randomUUID().toString());
    jobProfileSnapshotAssociation.setMasterProfileId(null);
    jobProfileSnapshotAssociation.setDetailProfileId(jobProfile.getId());
    jobProfileSnapshotAssociation.setDetailWrapperId(jobProfileWrapperId);
    jobProfileSnapshotAssociation.setDetailProfileType(JOB_PROFILE);
    jobProfileSnapshotAssociation.setDetail(jobProfile);

    matchProfileSnapshotAssociation.setId(UUID.randomUUID().toString());
    matchProfileSnapshotAssociation.setMasterProfileId(jobProfile.getId());
    matchProfileSnapshotAssociation.setDetailProfileId(matchProfile.getId());
    matchProfileSnapshotAssociation.setMasterWrapperId(jobProfileWrapperId);
    matchProfileSnapshotAssociation.setDetailWrapperId(matchProfileWrapperId);
    matchProfileSnapshotAssociation.setDetailProfileType(MATCH_PROFILE);
    matchProfileSnapshotAssociation.setDetail(matchProfile);

    actionProfileSnapshotAssociation.setId(UUID.randomUUID().toString());
    actionProfileSnapshotAssociation.setMasterProfileId(matchProfile.getId());
    actionProfileSnapshotAssociation.setDetailProfileId(actionProfile.getId());
    actionProfileSnapshotAssociation.setMasterWrapperId(matchProfileWrapperId);
    actionProfileSnapshotAssociation.setDetailWrapperId(actionProfileWrapperId);
    actionProfileSnapshotAssociation.setDetailProfileType(ACTION_PROFILE);
    actionProfileSnapshotAssociation.setDetail(actionProfile);

    mappingProfileSnapshotAssociation.setId(UUID.randomUUID().toString());
    mappingProfileSnapshotAssociation.setMasterProfileId(actionProfile.getId());
    mappingProfileSnapshotAssociation.setDetailProfileId(mappingProfile.getId());
    mappingProfileSnapshotAssociation.setMasterWrapperId(actionProfileWrapperId);
    mappingProfileSnapshotAssociation.setDetailWrapperId(mappingProfileWrapperId);
    mappingProfileSnapshotAssociation.setDetailProfileType(MAPPING_PROFILE);
    mappingProfileSnapshotAssociation.setDetail(mappingProfile);

    associations = new ArrayList<>(Arrays.asList(
      jobProfileSnapshotAssociation,
      actionProfileSnapshotAssociation,
      matchProfileSnapshotAssociation,
      mappingProfileSnapshotAssociation));
  }

  @Test
  @DisplayName("should save snapshot and return it by id with full nested wrapper tree")
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldSaveAndReturnWrappersOnGetById() {
    // arrange
    ProfileSnapshotWrapper expected = new ProfileSnapshotWrapper()
      .withId(UUID.randomUUID().toString())
      .withContentType(JOB_PROFILE)
      .withContent(new JobProfile())
      .withChildSnapshotWrappers(Collections.singletonList(
        new ProfileSnapshotWrapper()
          .withId(UUID.randomUUID().toString())
          .withContentType(ProfileType.MATCH_PROFILE)
          .withContent(new MatchProfile())
          .withReactTo(ReactToType.MATCH)
          .withOrder(1)
          .withChildSnapshotWrappers(Collections.singletonList(
            new ProfileSnapshotWrapper()
              .withId(UUID.randomUUID().toString())
              .withContentType(ProfileType.ACTION_PROFILE)
              .withContent(new ActionProfile())
              .withReactTo(ReactToType.MATCH)
              .withOrder(1)
              .withChildSnapshotWrappers(Collections.singletonList(
                new ProfileSnapshotWrapper()
                  .withId(UUID.randomUUID().toString())
                  .withReactTo(ReactToType.MATCH)
                  .withOrder(1)
                  .withContentType(ProfileType.MAPPING_PROFILE)
                  .withContent(new MappingProfile())))))));

    // act
    await(dao.save(expected, TENANT_ID));
    Optional<ProfileSnapshotWrapper> result = await(service.getById(expected.getId(), TENANT_ID));

    // assert
    assertThat(result).isPresent();
    ProfileSnapshotWrapper actual = result.get();
    assertThat(actual.getId()).isEqualTo(expected.getId());
    assertThat(actual.getContentType()).isEqualTo(expected.getContentType());
    assertThat(actual.getContent()).isInstanceOf(expected.getContent().getClass());

    ProfileSnapshotWrapper expectedMatch = expected.getChildSnapshotWrappers().getFirst();
    ProfileSnapshotWrapper actualMatch = actual.getChildSnapshotWrappers().getFirst();
    assertWrapper(expectedMatch, actualMatch);

    ProfileSnapshotWrapper expectedAction = expectedMatch.getChildSnapshotWrappers().getFirst();
    ProfileSnapshotWrapper actualAction = actualMatch.getChildSnapshotWrappers().getFirst();
    assertWrapper(expectedAction, actualAction);

    ProfileSnapshotWrapper expectedMapping = expectedAction.getChildSnapshotWrappers().getFirst();
    ProfileSnapshotWrapper actualMapping = actualAction.getChildSnapshotWrappers().getFirst();
    assertWrapper(expectedMapping, actualMapping);
  }

  @Test
  @DisplayName("should return failed future when no snapshot associations exist for job profile")
  void shouldReturnFailedFutureIfNoSnapshotAssociationsExist() {
    // arrange — use real dao; no data in DB for this id, so associations list is empty
    ProfileSnapshotService profileSnapshotService = new ProfileSnapshotServiceImpl(dao);
    String jobProfileId = UUID.randomUUID().toString();

    // act + assert — await() wraps the failed future as IllegalStateException
    var creationFuture = profileSnapshotService.createSnapshot(jobProfileId, TENANT_ID);
    assertThatThrownBy(() -> await(creationFuture))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("should build and save snapshot for job profile using mocked dao")
  void shouldBuildAndSaveSnapshotForJobProfile() {
    // arrange
    ProfileSnapshotDao mockDao = mock(ProfileSnapshotDao.class);
    ProfileSnapshotService profileSnapshotService = new ProfileSnapshotServiceImpl(mockDao);

    when(mockDao.getSnapshotAssociations(jobProfile.getId(), JOB_PROFILE, jobProfile.getId(), TENANT_ID))
      .thenReturn(Future.succeededFuture(associations));
    when(mockDao.save(any(), anyString()))
      .thenReturn(Future.succeededFuture(jobProfile.getId()));

    // act
    ProfileSnapshotWrapper result =
      await(profileSnapshotService.createSnapshot(jobProfile.getId(), TENANT_ID));

    // assert
    assertThat((JobProfile) result.getContent()).extracting(JobProfile::getId).isEqualTo(jobProfile.getId());
    assertThat(result.getProfileId()).isEqualTo(jobProfile.getId());

    ProfileSnapshotWrapper matchWrapper = result.getChildSnapshotWrappers().getFirst();
    assertThat((MatchProfile) matchWrapper.getContent())
      .extracting(MatchProfile::getId).isEqualTo(matchProfile.getId());

    ProfileSnapshotWrapper actionWrapper = matchWrapper.getChildSnapshotWrappers().getFirst();
    assertThat((ActionProfile) actionWrapper.getContent())
      .extracting(ActionProfile::getId).isEqualTo(actionProfile.getId());

    ProfileSnapshotWrapper mappingWrapper = actionWrapper.getChildSnapshotWrappers().getFirst();
    assertThat((MappingProfile) mappingWrapper.getContent())
      .extracting(MappingProfile::getId).isEqualTo(mappingProfile.getId());
  }

  @Test
  @DisplayName("should build and save snapshot with duplicate profiles in the tree")
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldBuildAndSaveSnapshotWithDuplicateProfilesForJobProfile() {
    // arrange
    ProfileSnapshotDao mockDao = mock(ProfileSnapshotDao.class);
    ProfileSnapshotService profileSnapshotService = new ProfileSnapshotServiceImpl(mockDao);

    when(mockDao.getSnapshotAssociations(jobProfile.getId(), JOB_PROFILE, jobProfile.getId(), TENANT_ID))
      .thenReturn(Future.succeededFuture(getAssociationsWithDuplicates()));
    when(mockDao.save(any(), anyString()))
      .thenReturn(Future.succeededFuture(jobProfile.getId()));

    // act
    ProfileSnapshotWrapper result =
      await(profileSnapshotService.createSnapshot(jobProfile.getId(), TENANT_ID));

    // assert
    assertThat((JobProfile) result.getContent()).extracting(JobProfile::getId).isEqualTo(jobProfile.getId());
    assertThat(result.getChildSnapshotWrappers()).hasSize(2);

    ProfileSnapshotWrapper match1 = result.getChildSnapshotWrappers().getFirst();
    assertThat((MatchProfile) match1.getContent()).extracting(MatchProfile::getId).isEqualTo(matchProfile.getId());
    assertThat(match1.getChildSnapshotWrappers()).hasSize(1);

    ProfileSnapshotWrapper childMatch1 = match1.getChildSnapshotWrappers().getFirst();
    assertThat(childMatch1.getChildSnapshotWrappers()).hasSize(1);

    ProfileSnapshotWrapper action1 = childMatch1.getChildSnapshotWrappers().getFirst();
    assertThat((ActionProfile) action1.getContent()).extracting(ActionProfile::getId).isEqualTo(actionProfile.getId());
    assertThat(action1.getChildSnapshotWrappers()).hasSize(1);

    ProfileSnapshotWrapper mapping1 = action1.getChildSnapshotWrappers().getFirst();
    assertThat((MappingProfile) mapping1.getContent())
      .extracting(MappingProfile::getId).isEqualTo(mappingProfile.getId());
    assertThat(mapping1.getChildSnapshotWrappers()).isEmpty();
  }

  @Test
  @DisplayName("should construct snapshot for job profile without saving")
  void shouldConstructSnapshotForJobProfile() {
    // arrange
    ProfileSnapshotDao mockDao = mock(ProfileSnapshotDao.class);
    ProfileSnapshotService profileSnapshotService = new ProfileSnapshotServiceImpl(mockDao);

    when(mockDao.getSnapshotAssociations(jobProfile.getId(), JOB_PROFILE, jobProfile.getId(), TENANT_ID))
      .thenReturn(Future.succeededFuture(associations));

    // act
    ProfileSnapshotWrapper result = await(
      profileSnapshotService.constructSnapshot(jobProfile.getId(), ProfileType.JOB_PROFILE, jobProfile.getId(),
        TENANT_ID));

    // assert
    assertThat((JobProfile) result.getContent()).extracting(JobProfile::getId).isEqualTo(jobProfile.getId());
    assertThat(result.getProfileId()).isEqualTo(jobProfile.getId());

    ProfileSnapshotWrapper matchWrapper = result.getChildSnapshotWrappers().getFirst();
    assertThat((MatchProfile) matchWrapper.getContent())
      .extracting(MatchProfile::getId).isEqualTo(matchProfile.getId());

    ProfileSnapshotWrapper actionWrapper = matchWrapper.getChildSnapshotWrappers().getFirst();
    assertThat((ActionProfile) actionWrapper.getContent())
      .extracting(ActionProfile::getId).isEqualTo(actionProfile.getId());

    ProfileSnapshotWrapper mappingWrapper = actionWrapper.getChildSnapshotWrappers().getFirst();
    assertThat((MappingProfile) mappingWrapper.getContent())
      .extracting(MappingProfile::getId).isEqualTo(mappingProfile.getId());
  }

  @Test
  @DisplayName("should return snapshot associations for job profile")
  void shouldReturnSnapshotAssociations() {
    // arrange
    ProfileSnapshotDao mockDao = mock(ProfileSnapshotDao.class);
    ProfileSnapshotService profileSnapshotService = new ProfileSnapshotServiceImpl(mockDao);

    when(mockDao.getSnapshotAssociations(jobProfile.getId(), JOB_PROFILE, jobProfile.getId(), TENANT_ID))
      .thenReturn(Future.succeededFuture(associations));

    // act
    List<ProfileAssociation> result = await(
      profileSnapshotService.getSnapshotAssociations(jobProfile.getId(), ProfileType.JOB_PROFILE, jobProfile.getId(),
        TENANT_ID));

    // assert
    assertThat(result).isEqualTo(associations);
  }

  private void assertWrapper(ProfileSnapshotWrapper expected, ProfileSnapshotWrapper actual) {
    assertThat(actual.getId()).isEqualTo(expected.getId());
    assertThat(actual.getContentType()).isEqualTo(expected.getContentType());
    assertThat(actual.getContent()).isInstanceOf(expected.getContent().getClass());
  }

  @SuppressWarnings("checkstyle:MethodLength")
  private List<ProfileAssociation> getAssociationsWithDuplicates() {
    final MatchProfile matchProfile2 = new MatchProfile().withId(UUID.randomUUID().toString());

    final String parentMatchWrapperId1 = UUID.randomUUID().toString();
    final String childMatchWrapperId1 = UUID.randomUUID().toString();
    final String actionWrapperId1 = UUID.randomUUID().toString();
    final String mappingWrapperId1 = UUID.randomUUID().toString();

    final ProfileAssociation parentMatch1 = new ProfileAssociation();
    parentMatch1.setId(UUID.randomUUID().toString());
    parentMatch1.setMasterProfileId(jobProfile.getId());
    parentMatch1.setDetailProfileId(matchProfile.getId());
    parentMatch1.setMasterWrapperId(jobProfileSnapshotAssociation.getDetailWrapperId());
    parentMatch1.setDetailWrapperId(parentMatchWrapperId1);
    parentMatch1.setDetailProfileType(MATCH_PROFILE);
    parentMatch1.setDetail(matchProfile);

    final ProfileAssociation childMatch1 = new ProfileAssociation();
    childMatch1.setId(UUID.randomUUID().toString());
    childMatch1.setMasterProfileId(matchProfile.getId());
    childMatch1.setDetailProfileId(matchProfile2.getId());
    childMatch1.setMasterWrapperId(parentMatchWrapperId1);
    childMatch1.setDetailWrapperId(childMatchWrapperId1);
    childMatch1.setDetailProfileType(MATCH_PROFILE);
    childMatch1.setDetail(matchProfile2);

    final ProfileAssociation action1 = new ProfileAssociation();
    action1.setId(UUID.randomUUID().toString());
    action1.setMasterProfileId(matchProfile2.getId());
    action1.setDetailProfileId(actionProfile.getId());
    action1.setMasterWrapperId(childMatchWrapperId1);
    action1.setDetailWrapperId(actionWrapperId1);
    action1.setDetailProfileType(ACTION_PROFILE);
    action1.setDetail(actionProfile);

    final ProfileAssociation mapping1 = new ProfileAssociation();
    mapping1.setId(UUID.randomUUID().toString());
    mapping1.setMasterProfileId(actionProfile.getId());
    mapping1.setDetailProfileId(mappingProfile.getId());
    mapping1.setMasterWrapperId(actionWrapperId1);
    mapping1.setDetailWrapperId(mappingWrapperId1);
    mapping1.setDetailProfileType(MAPPING_PROFILE);
    mapping1.setDetail(mappingProfile);

    final String parentMatchWrapperId2 = UUID.randomUUID().toString();
    final String childMatchWrapperId2 = UUID.randomUUID().toString();
    final String actionWrapperId2 = UUID.randomUUID().toString();
    final String mappingWrapperId2 = UUID.randomUUID().toString();

    final ProfileAssociation parentMatch2 = new ProfileAssociation();
    parentMatch2.setId(UUID.randomUUID().toString());
    parentMatch2.setMasterProfileId(jobProfile.getId());
    parentMatch2.setDetailProfileId(matchProfile.getId());
    parentMatch2.setMasterWrapperId(jobProfileSnapshotAssociation.getDetailWrapperId());
    parentMatch2.setDetailWrapperId(parentMatchWrapperId2);
    parentMatch2.setDetailProfileType(MATCH_PROFILE);
    parentMatch2.setDetail(matchProfile);

    final ProfileAssociation childMatch2 = new ProfileAssociation();
    childMatch2.setId(UUID.randomUUID().toString());
    childMatch2.setMasterProfileId(matchProfile.getId());
    childMatch2.setDetailProfileId(matchProfile2.getId());
    childMatch2.setMasterWrapperId(parentMatchWrapperId2);
    childMatch2.setDetailWrapperId(childMatchWrapperId2);
    childMatch2.setDetailProfileType(MATCH_PROFILE);
    childMatch2.setDetail(matchProfile2);

    final ProfileAssociation action2 = new ProfileAssociation();
    action2.setId(UUID.randomUUID().toString());
    action2.setMasterProfileId(matchProfile2.getId());
    action2.setDetailProfileId(actionProfile.getId());
    action2.setMasterWrapperId(childMatchWrapperId2);
    action2.setDetailWrapperId(actionWrapperId2);
    action2.setDetailProfileType(ACTION_PROFILE);
    action2.setDetail(actionProfile);

    final ProfileAssociation mapping2 = new ProfileAssociation();
    mapping2.setId(UUID.randomUUID().toString());
    mapping2.setMasterProfileId(actionProfile.getId());
    mapping2.setDetailProfileId(mappingProfile.getId());
    mapping2.setMasterWrapperId(actionWrapperId2);
    mapping2.setDetailWrapperId(mappingWrapperId2);
    mapping2.setDetailProfileType(MAPPING_PROFILE);
    mapping2.setDetail(mappingProfile);

    return new ArrayList<>(Arrays.asList(
      jobProfileSnapshotAssociation,
      parentMatch1, childMatch1, action1, mapping1,
      parentMatch2, childMatch2, action2, mapping2));
  }
}
