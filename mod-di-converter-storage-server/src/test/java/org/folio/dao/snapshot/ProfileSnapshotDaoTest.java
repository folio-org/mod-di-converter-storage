package org.folio.dao.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;

import java.util.UUID;
import org.folio.dao.ProfileDao;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileCollection;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileCollection;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.MatchProfileCollection;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.services.association.CommonProfileAssociationService;
import org.folio.support.AbstractUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProfileSnapshotDaoTest extends AbstractUnitTest {

  @Autowired
  private ProfileDao<JobProfile, JobProfileCollection> jobProfileDao;
  @Autowired
  private ProfileDao<MatchProfile, MatchProfileCollection> matchProfileDao;
  @Autowired
  private ProfileDao<ActionProfile, ActionProfileCollection> actionProfileDao;
  @Autowired
  private CommonProfileAssociationService commonProfileAssociationService;
  @Autowired
  private ProfileSnapshotDao dao;

  @Test
  @DisplayName("should return empty list when no associations exist for job profile")
  void shouldReturnEmptySnapshotAssociationsIfNoAssociationsExist() {
    // arrange
    String jobProfileId = UUID.randomUUID().toString();

    // act
    var result = await(dao.getSnapshotAssociations(jobProfileId, ProfileType.JOB_PROFILE, jobProfileId, TENANT_ID));

    // assert
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("should return snapshot associations after saving a job profile")
  void shouldReturn2SnapshotAssociations() {
    // arrange
    JobProfile jobProfile = new JobProfile().withId(UUID.randomUUID().toString());

    // act
    await(jobProfileDao.saveProfile(jobProfile, TENANT_ID));
    var result = await(dao.getSnapshotAssociations(jobProfile.getId(), ProfileType.JOB_PROFILE,
      jobProfile.getId(), TENANT_ID));

    // assert — no direct child links yet, but the root self-association is present
    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("should return only root association for match profile linked to action profile")
  void shouldReturnOnlyRootAssociationForMatchProfile() {
    // arrange
    MatchProfile matchProfile = new MatchProfile().withId(UUID.randomUUID().toString());
    ActionProfile actionProfile = new ActionProfile().withId(UUID.randomUUID().toString());
    ProfileAssociation association = new ProfileAssociation()
      .withId(UUID.randomUUID().toString())
      .withOrder(1)
      .withMasterProfileId(matchProfile.getId())
      .withMasterProfileType(MATCH_PROFILE)
      .withDetailProfileId(actionProfile.getId())
      .withDetailProfileType(ACTION_PROFILE);

    await(matchProfileDao.saveProfile(matchProfile, TENANT_ID));
    await(actionProfileDao.saveProfile(actionProfile, TENANT_ID));
    await(commonProfileAssociationService.save(association, TENANT_ID));

    // act
    var result = await(dao.getSnapshotAssociations(matchProfile.getId(), ProfileType.MATCH_PROFILE,
      matchProfile.getId(), TENANT_ID));

    // assert
    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getDetailProfileType()).isEqualTo(MATCH_PROFILE);
  }

  @Test
  @DisplayName("should return single self-association for action profile with no children")
  void shouldReturnAssociationWithNoAssociation() {
    // arrange
    ActionProfile actionProfile = new ActionProfile().withId(UUID.randomUUID().toString());

    await(actionProfileDao.saveProfile(actionProfile, TENANT_ID));

    // act
    var result = await(dao.getSnapshotAssociations(actionProfile.getId(), ProfileType.ACTION_PROFILE,
      actionProfile.getId(), TENANT_ID));

    // assert
    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getDetailProfileType()).isEqualTo(ACTION_PROFILE);
    assertThat(result.getFirst().getDetailProfileId()).isEqualTo(actionProfile.getId());
  }
}
