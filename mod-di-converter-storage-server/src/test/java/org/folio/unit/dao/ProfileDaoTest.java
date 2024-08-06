package org.folio.unit.dao;

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.folio.dao.ProfileDao;
import org.folio.dao.association.ProfileWrapperDao;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileCollection;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ProfileWrapper;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.unit.AbstractUnitTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProfileDaoTest extends AbstractUnitTest {
  private static final String SNAPSHOTS_TABLE_NAME = "profile_snapshots";
  private static final String JOB_PROFILES_TABLE_NAME = "job_profiles";
  private static final String ACTION_PROFILES_TABLE_NAME = "action_profiles";
  private static final String ASSOCIATIONS_TABLE_NAME = "profile_associations";
  private static final String MAPPING_PROFILES_TABLE_NAME = "mapping_profiles";
  private static final String MATCH_PROFILES_TABLE_NAME = "match_profiles";
  private static final String PROFILE_WRAPPERS_TABLE_NAME = "profile_wrappers";

  @Autowired
  private ProfileDao<JobProfile, JobProfileCollection> jobProfileDao;

  @Autowired
  private ProfileWrapperDao profileWrapperDao;

  @Test
  public void shouldHardDeleteProfile(TestContext context) {
    Async async = context.async();
    String jobProfileId = UUID.randomUUID().toString();
    JobProfile jobProfile = new JobProfile().withId(jobProfileId);

    String wrapperId = UUID.randomUUID().toString();
    ProfileWrapper profileWrapper = new ProfileWrapper().withProfileId(jobProfileId).withProfileType(ProfileType.JOB_PROFILE).withId(wrapperId);

    jobProfileDao.saveProfile(jobProfile, TENANT_ID).onComplete(savedJobProfileAr -> {
      context.assertTrue(savedJobProfileAr.succeeded());
      jobProfileDao.getProfileById(jobProfileId, TENANT_ID).onComplete(getJobProfileAr1 -> {
        context.assertTrue(getJobProfileAr1.succeeded());
        context.assertTrue(getJobProfileAr1.result().isPresent());
        context.assertEquals(getJobProfileAr1.result().get().getId(), jobProfileId);
        profileWrapperDao.save(profileWrapper, TENANT_ID).onComplete(savedWrapperAr -> {
          context.assertTrue(savedWrapperAr.succeeded());
          profileWrapperDao.getProfileWrapperById(wrapperId, TENANT_ID).onComplete(getProfileWrapperAr1 -> {
            context.assertTrue(getProfileWrapperAr1.succeeded());
            context.assertTrue(getProfileWrapperAr1.result().isPresent());
            context.assertEquals(getProfileWrapperAr1.result().get().getId(), wrapperId);
            jobProfileDao.hardDeleteProfile(jobProfileId, TENANT_ID).onComplete(deleteJobProfileAr -> {
              context.assertTrue(deleteJobProfileAr.succeeded());
              jobProfileDao.getProfileById(jobProfileId, TENANT_ID).onComplete(getJobProfileAr2 -> {
                context.assertTrue(getJobProfileAr2.succeeded());
                context.assertTrue(getJobProfileAr2.result().isEmpty());
                profileWrapperDao.getProfileWrapperById(wrapperId, TENANT_ID).onComplete(getProfileWrapperAr2 -> {
                  context.assertTrue(getProfileWrapperAr2.succeeded());
                  context.assertTrue(getProfileWrapperAr2.result().isEmpty());
                  async.complete();
                });
              });
            });
          });
        });
      });
    });
  }

  @Override
  public void afterTest(TestContext context) {
    Async async = context.async();
    PostgresClient pgClient = PostgresClient.getInstance(vertx, TENANT_ID);
    pgClient.delete(ASSOCIATIONS_TABLE_NAME, new Criterion(), event1 ->
      pgClient.delete(PROFILE_WRAPPERS_TABLE_NAME, new Criterion(), event2 ->
        pgClient.delete(SNAPSHOTS_TABLE_NAME, new Criterion(), event3 ->
          pgClient.delete(JOB_PROFILES_TABLE_NAME, new Criterion(), event4 ->
            pgClient.delete(MATCH_PROFILES_TABLE_NAME, new Criterion(), event5 ->
              pgClient.delete(ACTION_PROFILES_TABLE_NAME, new Criterion(), event6 ->
                pgClient.delete(MAPPING_PROFILES_TABLE_NAME, new Criterion(), event7 -> {
                  if (event7.failed()) {
                    context.fail(event7.cause());
                  }
                  async.complete();
                })))))));
  }
}
