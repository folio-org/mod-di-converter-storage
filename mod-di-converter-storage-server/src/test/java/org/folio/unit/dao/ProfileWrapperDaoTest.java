package org.folio.unit.dao;

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.dao.ProfileDao;
import org.folio.dao.association.ProfileWrapperDao;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileCollection;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ProfileWrapper;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.unit.AbstractUnitTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ProfileWrapperDaoTest extends AbstractUnitTest {

  private static final String SNAPSHOTS_TABLE_NAME = "profile_snapshots";
  private static final String JOB_PROFILES_TABLE_NAME = "job_profiles";
  private static final String ACTION_PROFILES_TABLE_NAME = "action_profiles";
  private static final String JOB_TO_MATCH_PROFILES_TABLE_NAME = "job_to_match_profiles";
  private static final String MATCH_TO_ACTION_PROFILES_TABLE_NAME = "match_to_action_profiles";
  private static final String ACTION_TO_MAPPING_PROFILES_TABLE_NAME = "action_to_mapping_profiles";
  private static final String JOB_TO_ACTION_PROFILES_TABLE_NAME = "job_to_action_profiles";
  private static final String MAPPING_PROFILES_TABLE_NAME = "mapping_profiles";
  private static final String MATCH_PROFILES_TABLE_NAME = "match_profiles";
  private static final String PROFILE_WRAPPERS_TABLE_NAME = "profile_wrappers";

  @Autowired
  private ProfileWrapperDao dao;

  @Autowired
  private ProfileDao<ActionProfile, ActionProfileCollection> actionProfileDao;

  @Test
  public void shouldReturnEmptyProfileWrappersIfNoItemsExist(TestContext context) {
    String wrapperId = UUID.randomUUID().toString();
    dao.getProfileWrapperById(wrapperId, TENANT_ID).onComplete(ar -> {
      context.assertTrue(ar.succeeded());
      Optional<ProfileWrapper> wrapper = ar.result();
      context.assertTrue(wrapper.isEmpty());
    });
  }

  @Test
  public void shouldReturnNewlyCreatedProfileWrapper(TestContext context) {
    Async async = context.async();
    // given
    String actionProfileId = UUID.randomUUID().toString();
    ActionProfile actionProfile = new ActionProfile().withId(actionProfileId);

    String wrapperId = UUID.randomUUID().toString();
    ProfileWrapper profileWrapper1 = new ProfileWrapper().withProfileId(actionProfileId).withProfileType(ProfileType.ACTION_PROFILE).withId(wrapperId);
    actionProfileDao.saveProfile(actionProfile, TENANT_ID).onComplete(savedActionProfileAr -> {
      context.assertTrue(savedActionProfileAr.succeeded());
      dao.save(profileWrapper1, TENANT_ID).onComplete(e -> {
        context.assertTrue(e.succeeded());
        context.assertNotNull(e.result());
        dao.getProfileWrapperById(profileWrapper1.getId(), TENANT_ID).onComplete(r -> {
          context.assertTrue(r.succeeded());
          Optional<ProfileWrapper> result = r.result();
          context.assertEquals(wrapperId, result.get().getId());
          async.complete();
        });
      });
    });
  }

  @Test
  public void shouldDeleteNewlyCreatedProfileWrapper(TestContext context) {
    Async async = context.async();
    // given
    String actionProfileId = UUID.randomUUID().toString();
    ActionProfile actionProfile = new ActionProfile().withId(actionProfileId);

    String wrapperId = UUID.randomUUID().toString();
    ProfileWrapper profileWrapper1 = new ProfileWrapper().withProfileId(actionProfileId).withProfileType(ProfileType.ACTION_PROFILE).withId(wrapperId);
    actionProfileDao.saveProfile(actionProfile, TENANT_ID).onComplete(savedActionProfileAr -> {
      context.assertTrue(savedActionProfileAr.succeeded());
      dao.save(profileWrapper1, TENANT_ID).onComplete(e -> {
        context.assertTrue(e.succeeded());
        context.assertNotNull(e.result());
        dao.getProfileWrapperById(profileWrapper1.getId(), TENANT_ID).onComplete(r -> {
          context.assertTrue(r.succeeded());
          Optional<ProfileWrapper> result = r.result();
          context.assertEquals(wrapperId, result.get().getId());
          dao.deleteById(profileWrapper1.getId(), TENANT_ID).onComplete(t -> {
            context.assertTrue(t.succeeded());
            context.assertNotNull(t.result());
            context.assertTrue(t.result());
            async.complete();
          });
        });
      });
    });
  }

  @Override
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
