package org.folio.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;

import java.util.UUID;
import org.folio.dao.association.ProfileWrapperDao;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileCollection;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ProfileWrapper;
import org.folio.support.AbstractUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JobProfileDaoTest extends AbstractUnitTest {

  @Autowired
  private ProfileDao<JobProfile, JobProfileCollection> jobProfileDao;

  @Autowired
  private ProfileWrapperDao profileWrapperDao;

  @Test
  @DisplayName("should cascade-delete profile wrapper when hard-deleting a profile")
  void shouldHardDeleteProfile() {
    // arrange
    String jobProfileId = UUID.randomUUID().toString();
    String wrapperId = UUID.randomUUID().toString();

    await(jobProfileDao.saveProfile(new JobProfile().withId(jobProfileId), TENANT_ID));
    await(profileWrapperDao.save(new ProfileWrapper()
      .withProfileId(jobProfileId)
      .withProfileType(ProfileType.JOB_PROFILE)
      .withId(wrapperId), TENANT_ID));

    assertThat(await(jobProfileDao.getProfileById(jobProfileId, TENANT_ID))).isPresent();
    assertThat(await(profileWrapperDao.getProfileWrapperById(wrapperId, TENANT_ID))).isPresent();

    // act
    await(jobProfileDao.hardDeleteProfile(jobProfileId, TENANT_ID));

    // assert
    assertThat(await(jobProfileDao.getProfileById(jobProfileId, TENANT_ID))).isEmpty();
    assertThat(await(profileWrapperDao.getProfileWrapperById(wrapperId, TENANT_ID))).isEmpty();
  }
}
