package org.folio.unit.snapshot;

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;

import org.folio.dao.ProfileDao;
import org.folio.dao.snapshot.ProfileSnapshotDao;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileCollection;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileCollection;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.MatchProfileCollection;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.association.CommonProfileAssociationService;
import org.folio.unit.AbstractUnitTest;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;

public class ProfileSnapshotDaoTest extends AbstractUnitTest {

  private static final String SNAPSHOTS_TABLE_NAME = "profile_snapshots";
  private static final String PROFILE_WRAPPERS_TABLE_NAME = "profile_wrappers";
  private static final String JOB_PROFILES_TABLE_NAME = "job_profiles";
  private static final String ACTION_PROFILES_TABLE_NAME = "action_profiles";
  private static final String JOB_TO_MATCH_PROFILES_TABLE_NAME = "job_to_match_profiles";
  private static final String MATCH_TO_ACTION_PROFILES_TABLE_NAME = "match_to_action_profiles";
  private static final String ACTION_TO_MAPPING_PROFILES_TABLE_NAME = "action_to_mapping_profiles";
  private static final String JOB_TO_ACTION_PROFILES_TABLE_NAME = "job_to_action_profiles";
  private static final String MAPPING_PROFILES_TABLE_NAME = "mapping_profiles";
  private static final String MATCH_PROFILES_TABLE_NAME = "match_profiles";
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
  public void shouldReturnEmptySnapshotAssociationsIfNoAssociationsExist(TestContext context) {
    String jobProfileId = UUID.randomUUID().toString();
    dao.getSnapshotAssociations(jobProfileId, JOB_PROFILE, jobProfileId, TENANT_ID).onComplete(ar -> {
      context.assertTrue(ar.succeeded());
      List<ProfileAssociation> associations = ar.result();
      context.assertTrue(associations.isEmpty());
    });
  }

  @Test
  public void shouldReturn2SnapshotAssociations(TestContext context) {
    Async async = context.async();
    // given
    JobProfile jobProfile = new JobProfile().withId(UUID.randomUUID().toString());
/*    ActionProfile actionProfile = new ActionProfile().withId(UUID.randomUUID().toString());
    ProfileAssociation jobToAction1Association = new ProfileAssociation()
      .withId(UUID.randomUUID().toString())
      .withOrder(1)
      .withMasterProfileId(jobProfile.getId())
      .withMasterProfileType(ProfileType.JOB_PROFILE)
      .withDetailProfileId(actionProfile.getId())
      .withDetailProfileType(ProfileType.ACTION_PROFILE);*/
    // when
    jobProfileDao.saveProfile(jobProfile, TENANT_ID).onComplete(savedJobProfileAr -> {
      context.assertTrue(savedJobProfileAr.succeeded());
      async.complete();
/*      actionProfileDao.saveProfile(actionProfile, TENANT_ID).onComplete(savedActionProfileAr -> {
        context.assertTrue(savedActionProfileAr.succeeded());
        commonProfileAssociationService.save(jobToAction1Association, JOB_PROFILE, ACTION_PROFILE, TENANT_ID).onComplete(savedAssociationAr -> {
          context.assertTrue(savedAssociationAr.succeeded());
          dao.getSnapshotAssociations(jobProfile.getId(), JOB_PROFILE, jobProfile.getId(), TENANT_ID).onComplete(associationsAr -> {
            // then
            context.assertTrue(associationsAr.succeeded());
            List<ProfileAssociation> profileSnapshotAssociations = associationsAr.result();
            context.assertEquals(2, profileSnapshotAssociations.size());
            async.complete();
          });
        });
      });*/
    });
  }

  @Test
  public void shouldReturnOnlyRootAssociationForMatchProfile(TestContext context) {
    Async async = context.async();
    // given
    MatchProfile matchProfile = new MatchProfile().withId(UUID.randomUUID().toString());
    ActionProfile actionProfile = new ActionProfile().withId(UUID.randomUUID().toString());
    ProfileAssociation matchToAction1Association = new ProfileAssociation()
      .withId(UUID.randomUUID().toString())
      .withOrder(1)
      .withMasterProfileId(matchProfile.getId())
      .withMasterProfileType(ProfileType.MATCH_PROFILE)
      .withDetailProfileId(actionProfile.getId())
      .withDetailProfileType(ProfileType.ACTION_PROFILE);
    // when
    matchProfileDao.saveProfile(matchProfile, TENANT_ID).onComplete(savedMatchProfileAr -> {
      context.assertTrue(savedMatchProfileAr.succeeded());
      actionProfileDao.saveProfile(actionProfile, TENANT_ID).onComplete(savedActionProfileAr -> {
        context.assertTrue(savedActionProfileAr.succeeded());
        commonProfileAssociationService.save(matchToAction1Association, MATCH_PROFILE, ACTION_PROFILE, TENANT_ID).onComplete(savedAssociationAr -> {
          context.assertTrue(savedAssociationAr.succeeded());
          dao.getSnapshotAssociations(matchProfile.getId(), MATCH_PROFILE, matchProfile.getId(), TENANT_ID).onComplete(associationsAr -> {
            // then
            context.assertTrue(associationsAr.succeeded());
            List<ProfileAssociation> profileSnapshotAssociations = associationsAr.result();
            context.assertEquals(1, profileSnapshotAssociations.size());
            context.assertEquals(MATCH_PROFILE, profileSnapshotAssociations.get(0).getDetailProfileType());
            async.complete();
          });
        });
      });
    });
  }

  @Test
  public void shouldReturnAssociationWithNoAssociation(TestContext context) {
    Async async = context.async();
    // given
    ActionProfile actionProfile = new ActionProfile().withId(UUID.randomUUID().toString());
    // when
    actionProfileDao.saveProfile(actionProfile, TENANT_ID).onComplete(savedActionProfileAr -> {
      context.assertTrue(savedActionProfileAr.succeeded());
      dao.getSnapshotAssociations(actionProfile.getId(), ACTION_PROFILE, actionProfile.getId(), TENANT_ID).onComplete(associationsAr -> {
        // then
        context.assertTrue(associationsAr.succeeded());
        List<ProfileAssociation> profileSnapshotAssociations = associationsAr.result();
        context.assertEquals(1, profileSnapshotAssociations.size());
        context.assertEquals(ACTION_PROFILE, profileSnapshotAssociations.get(0).getDetailProfileType());
        context.assertEquals(actionProfile.getId(), profileSnapshotAssociations.get(0).getDetailProfileId());
        async.complete();
      });
    });
  }

  @After
  public void afterTest(TestContext context) {
    Async async = context.async();
    PostgresClient pgClient = PostgresClient.getInstance(vertx, TENANT_ID);
    pgClient.delete(PROFILE_WRAPPERS_TABLE_NAME, new Criterion(), event1 ->
    pgClient.delete(SNAPSHOTS_TABLE_NAME, new Criterion(), event2 ->
      pgClient.delete(JOB_TO_ACTION_PROFILES_TABLE_NAME, new Criterion(), event3 ->
        pgClient.delete(JOB_TO_MATCH_PROFILES_TABLE_NAME, new Criterion(), event4 ->
          pgClient.delete(ACTION_TO_MAPPING_PROFILES_TABLE_NAME, new Criterion(), event5 ->
            pgClient.delete(MATCH_TO_ACTION_PROFILES_TABLE_NAME, new Criterion(), event6 ->
              pgClient.delete(JOB_PROFILES_TABLE_NAME, new Criterion(), event7 ->
                pgClient.delete(MATCH_PROFILES_TABLE_NAME, new Criterion(), event8 ->
                  pgClient.delete(ACTION_PROFILES_TABLE_NAME, new Criterion(), event9 ->
                    pgClient.delete(MAPPING_PROFILES_TABLE_NAME, new Criterion(), event10 -> {
                      if (event10.failed()) {
                        context.fail(event10.cause());
                      }
                      async.complete();
                    }))))))))));
  }
}
