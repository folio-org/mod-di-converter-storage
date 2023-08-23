package org.folio.unit.snapshot;

import io.vertx.core.Future;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.folio.dao.snapshot.ProfileSnapshotDao;
import org.folio.dao.snapshot.ProfileSnapshotDaoImpl;
import org.folio.dao.snapshot.ProfileSnapshotItem;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.snapshot.ProfileSnapshotService;
import org.folio.services.snapshot.ProfileSnapshotServiceImpl;
import org.folio.unit.AbstractUnitTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.MAPPING_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.MATCH_PROFILE;

public class ProfileSnapshotServiceTest extends AbstractUnitTest {
  private static final String TABLE_NAME = "profile_snapshots";

  @Autowired
  private ProfileSnapshotDao dao;
  @Autowired
  private ProfileSnapshotService service;

  private JobProfile jobProfile = new JobProfile().withId(UUID.randomUUID().toString());
  private ProfileSnapshotItem jobProfileSnapshotItem = new ProfileSnapshotItem();

  private MatchProfile matchProfile = new MatchProfile().withId(UUID.randomUUID().toString());
  private ProfileSnapshotItem matchProfileSnapshotItem = new ProfileSnapshotItem();

  private ActionProfile actionProfile = new ActionProfile().withId(UUID.randomUUID().toString());
  private ProfileSnapshotItem actionProfileSnapshotItem = new ProfileSnapshotItem();

  private MappingProfile mappingProfile = new MappingProfile().withId(UUID.randomUUID().toString());
  private ProfileSnapshotItem mappingProfileSnapshotItem = new ProfileSnapshotItem();

  private List<ProfileSnapshotItem> items;

  @Before
  public void setUp() {
    String jobProfileWrapperId = UUID.randomUUID().toString();
    String matchProfileWrapperId = UUID.randomUUID().toString();
    String actionProfileWrapperId = UUID.randomUUID().toString();
    String mappingProfileWrapperId = UUID.randomUUID().toString();

    jobProfileSnapshotItem.setAssociationId(UUID.randomUUID().toString());
    jobProfileSnapshotItem.setMasterId(null);
    jobProfileSnapshotItem.setDetailId(jobProfile.getId());
    jobProfileSnapshotItem.setDetailWrapperId(jobProfileWrapperId);
    jobProfileSnapshotItem.setDetailType(JOB_PROFILE);
    jobProfileSnapshotItem.setDetail(jobProfile);

    matchProfileSnapshotItem.setAssociationId(UUID.randomUUID().toString());
    matchProfileSnapshotItem.setMasterId(jobProfile.getId());
    matchProfileSnapshotItem.setDetailId(matchProfile.getId());
    matchProfileSnapshotItem.setMasterWrapperId(jobProfileWrapperId);
    matchProfileSnapshotItem.setDetailWrapperId(matchProfileWrapperId);
    matchProfileSnapshotItem.setDetailType(MATCH_PROFILE);
    matchProfileSnapshotItem.setDetail(matchProfile);

    actionProfileSnapshotItem.setAssociationId(UUID.randomUUID().toString());
    actionProfileSnapshotItem.setMasterId(matchProfile.getId());
    actionProfileSnapshotItem.setDetailId(actionProfile.getId());
    actionProfileSnapshotItem.setMasterWrapperId(matchProfileWrapperId);
    actionProfileSnapshotItem.setDetailWrapperId(actionProfileWrapperId);
    actionProfileSnapshotItem.setDetailType(ACTION_PROFILE);
    actionProfileSnapshotItem.setDetail(actionProfile);

    mappingProfileSnapshotItem.setAssociationId(UUID.randomUUID().toString());
    mappingProfileSnapshotItem.setMasterId(actionProfile.getId());
    mappingProfileSnapshotItem.setDetailId(mappingProfile.getId());
    mappingProfileSnapshotItem.setMasterWrapperId(actionProfileWrapperId);
    mappingProfileSnapshotItem.setDetailWrapperId(mappingProfileWrapperId);
    mappingProfileSnapshotItem.setDetailType(MAPPING_PROFILE);
    mappingProfileSnapshotItem.setDetail(mappingProfile);

    items = new ArrayList<>(Arrays.asList(
      jobProfileSnapshotItem,
      actionProfileSnapshotItem,
      matchProfileSnapshotItem,
      mappingProfileSnapshotItem)
    );
  }

  @Test
  public void shouldSaveAndReturnWrappersOnGetById(TestContext context) {
    Async async = context.async();

    ProfileSnapshotWrapper expectedJobProfileWrapper = new ProfileSnapshotWrapper()
      .withId(UUID.randomUUID().toString())
      .withContentType(JOB_PROFILE)
      .withContent(new JobProfile())
      .withChildSnapshotWrappers(Collections.singletonList(
        new ProfileSnapshotWrapper()
          .withId(UUID.randomUUID().toString())
          .withContentType(ProfileSnapshotWrapper.ContentType.MATCH_PROFILE)
          .withContent(new MatchProfile())
          .withReactTo(ProfileSnapshotWrapper.ReactTo.MATCH)
          .withOrder(1)
          .withChildSnapshotWrappers(Collections.singletonList(
            new ProfileSnapshotWrapper()
              .withId(UUID.randomUUID().toString())
              .withContentType(ProfileSnapshotWrapper.ContentType.ACTION_PROFILE)
              .withContent(new ActionProfile())
              .withReactTo(ProfileSnapshotWrapper.ReactTo.MATCH)
              .withOrder(1)
              .withChildSnapshotWrappers(Collections.singletonList(
                new ProfileSnapshotWrapper()
                  .withId(UUID.randomUUID().toString())
                  .withReactTo(ProfileSnapshotWrapper.ReactTo.MATCH)
                  .withOrder(1)
                  .withContentType(ProfileSnapshotWrapper.ContentType.MAPPING_PROFILE)
                  .withContent(new MappingProfile())
              ))
          ))
      ));

    dao.save(expectedJobProfileWrapper, TENANT_ID).compose(ar -> {
      service.getById(expectedJobProfileWrapper.getId(), TENANT_ID).compose(optionalAr -> {
        context.assertTrue(optionalAr.isPresent());

        ProfileSnapshotWrapper actualJobProfileWrapper = optionalAr.get();
        context.assertEquals(expectedJobProfileWrapper.getId(), actualJobProfileWrapper.getId());
        context.assertEquals(expectedJobProfileWrapper.getContentType(), actualJobProfileWrapper.getContentType());
        context.assertEquals(expectedJobProfileWrapper.getContent().getClass(), actualJobProfileWrapper.getContent().getClass());

        ProfileSnapshotWrapper expectedMatchProfileWrapper = expectedJobProfileWrapper.getChildSnapshotWrappers().get(0);
        ProfileSnapshotWrapper actualMatchProfileWrapper = actualJobProfileWrapper.getChildSnapshotWrappers().get(0);
        assertExpectedChildOnActualChild(expectedMatchProfileWrapper, actualMatchProfileWrapper, context);

        ProfileSnapshotWrapper expectedActionProfileWrapper = expectedMatchProfileWrapper.getChildSnapshotWrappers().get(0);
        ProfileSnapshotWrapper actualActionProfileWrapper = actualMatchProfileWrapper.getChildSnapshotWrappers().get(0);
        assertExpectedChildOnActualChild(expectedActionProfileWrapper, actualActionProfileWrapper, context);

        ProfileSnapshotWrapper expectedMappingProfileWrapper = expectedActionProfileWrapper.getChildSnapshotWrappers().get(0);
        ProfileSnapshotWrapper actualMappingProfileWrapper = actualActionProfileWrapper.getChildSnapshotWrappers().get(0);
        assertExpectedChildOnActualChild(expectedMappingProfileWrapper, actualMappingProfileWrapper, context);

        async.complete();
        return Future.succeededFuture();
      });
      return Future.succeededFuture();
    });
  }

  @Test
  public void shouldReturnFailedFutureIfNoSnapshotItemsExist(TestContext context) {
    Async async = context.async();
    // given
    ProfileSnapshotDao mockDao = Mockito.mock(ProfileSnapshotDaoImpl.class);
    ProfileSnapshotService service = new ProfileSnapshotServiceImpl(dao);

    String jobProfileId = UUID.randomUUID().toString();
    Mockito.when(mockDao.getSnapshotItems(jobProfileId, JOB_PROFILE, jobProfileId, TENANT_ID)).thenReturn(Future.succeededFuture(new ArrayList<>()));

    // when
    service.createSnapshot(jobProfileId, TENANT_ID).onComplete(ar -> {
    // then
      context.assertTrue(ar.failed());
      async.complete();
    });
  }

  @Test
  public void shouldBuildAndSaveSnapshotForJobProfile(TestContext testContext) {
    Async async = testContext.async();
    // given
    ProfileSnapshotDao mockDao = Mockito.mock(ProfileSnapshotDaoImpl.class);
    ProfileSnapshotService service = new ProfileSnapshotServiceImpl(mockDao);

    Mockito.when(mockDao.getSnapshotItems(jobProfile.getId(), JOB_PROFILE, jobProfile.getId(), TENANT_ID)).thenReturn(Future.succeededFuture(items));
    Mockito.when(mockDao.save(ArgumentMatchers.any(), ArgumentMatchers.anyString())).thenReturn(Future.succeededFuture(jobProfile.getId()));

    // when
    service.createSnapshot(jobProfile.getId(), TENANT_ID).onComplete(ar -> {
    // then
      testContext.assertTrue(ar.succeeded());
      ProfileSnapshotWrapper jobProfileWrapper = ar.result();
      JobProfile actualJobProfile = (JobProfile) jobProfileWrapper.getContent();
      testContext.assertEquals(jobProfile.getId(), actualJobProfile.getId());
      testContext.assertEquals(jobProfile.getId(), jobProfileWrapper.getProfileId());

      ProfileSnapshotWrapper matchProfileWrapper = jobProfileWrapper.getChildSnapshotWrappers().get(0);
      MatchProfile actualMatchProfile = (MatchProfile) matchProfileWrapper.getContent();
      testContext.assertEquals(matchProfile.getId(), actualMatchProfile.getId());
      testContext.assertEquals(matchProfile.getId(), matchProfileWrapper.getProfileId());

      ProfileSnapshotWrapper actionProfileWrapper = matchProfileWrapper.getChildSnapshotWrappers().get(0);
      ActionProfile actualActionProfile = (ActionProfile) actionProfileWrapper.getContent();
      testContext.assertEquals(actionProfile.getId(), actualActionProfile.getId());
      testContext.assertEquals(actionProfile.getId(), actionProfileWrapper.getProfileId());

      ProfileSnapshotWrapper mappingProfileWrapper = actionProfileWrapper.getChildSnapshotWrappers().get(0);
      MappingProfile actualMappingProfile = (MappingProfile) mappingProfileWrapper.getContent();
      testContext.assertEquals(mappingProfile.getId(), actualMappingProfile.getId());
      testContext.assertEquals(mappingProfile.getId(), mappingProfileWrapper.getProfileId());
      async.complete();
    });
  }

  @Test
  public void shouldBuildAndSaveSnapshotWithDuplicateProfilesForJobProfile(TestContext testContext) {
    Async async = testContext.async();
    // given
    ProfileSnapshotDao mockDao = Mockito.mock(ProfileSnapshotDaoImpl.class);
    ProfileSnapshotService service = new ProfileSnapshotServiceImpl(mockDao);

    Mockito.when(mockDao.getSnapshotItems(jobProfile.getId(), JOB_PROFILE, jobProfile.getId(), TENANT_ID)).thenReturn(Future.succeededFuture(getItemsWithDuplicates()));

    Mockito.when(mockDao.save(ArgumentMatchers.any(), ArgumentMatchers.anyString())).thenReturn(Future.succeededFuture(jobProfile.getId()));

    // when
    service.createSnapshot(jobProfile.getId(), TENANT_ID).onComplete(ar -> {
      // then
      testContext.assertTrue(ar.succeeded());
      ProfileSnapshotWrapper jobProfileWrapper = ar.result();
      JobProfile actualJobProfile = (JobProfile) jobProfileWrapper.getContent();
      testContext.assertEquals(jobProfile.getId(), actualJobProfile.getId());
      testContext.assertEquals(jobProfile.getId(), jobProfileWrapper.getProfileId());
      testContext.assertEquals(jobProfileWrapper.getChildSnapshotWrappers().size(), 2);

      ProfileSnapshotWrapper matchProfileWrapper1 = jobProfileWrapper.getChildSnapshotWrappers().get(0);
      MatchProfile actualMatchProfile1 = (MatchProfile) matchProfileWrapper1.getContent();
      testContext.assertEquals(matchProfile.getId(), actualMatchProfile1.getId());
      testContext.assertEquals(matchProfile.getId(), matchProfileWrapper1.getProfileId());
      testContext.assertEquals(matchProfileWrapper1.getChildSnapshotWrappers().size(), 1);

      ProfileSnapshotWrapper matchProfileWrapper2 = jobProfileWrapper.getChildSnapshotWrappers().get(0);
      MatchProfile actualMatchProfile2 = (MatchProfile) matchProfileWrapper2.getContent();
      testContext.assertEquals(matchProfile.getId(), actualMatchProfile2.getId());
      testContext.assertEquals(matchProfile.getId(), matchProfileWrapper2.getProfileId());
      testContext.assertEquals(matchProfileWrapper2.getChildSnapshotWrappers().size(), 1);

      ProfileSnapshotWrapper childMatchProfileWrapper1 = matchProfileWrapper1.getChildSnapshotWrappers().get(0);
      testContext.assertEquals(childMatchProfileWrapper1.getChildSnapshotWrappers().size(), 1);

      ProfileSnapshotWrapper childMatchProfileWrapper2 = matchProfileWrapper2.getChildSnapshotWrappers().get(0);
      testContext.assertEquals(childMatchProfileWrapper2.getChildSnapshotWrappers().size(), 1);

      ProfileSnapshotWrapper actionProfileWrapper1 = childMatchProfileWrapper1.getChildSnapshotWrappers().get(0);
      ActionProfile actualActionProfile1 = (ActionProfile) actionProfileWrapper1.getContent();
      testContext.assertEquals(actionProfile.getId(), actualActionProfile1.getId());
      testContext.assertEquals(actionProfile.getId(), actionProfileWrapper1.getProfileId());
      testContext.assertEquals(actionProfileWrapper1.getChildSnapshotWrappers().size(), 1);

      ProfileSnapshotWrapper actionProfileWrapper2 = childMatchProfileWrapper2.getChildSnapshotWrappers().get(0);
      ActionProfile actualActionProfile2 = (ActionProfile) actionProfileWrapper2.getContent();
      testContext.assertEquals(actionProfile.getId(), actualActionProfile2.getId());
      testContext.assertEquals(actionProfile.getId(), actionProfileWrapper2.getProfileId());
      testContext.assertEquals(actionProfileWrapper2.getChildSnapshotWrappers().size(), 1);

      ProfileSnapshotWrapper mappingProfileWrapper1 = actionProfileWrapper1.getChildSnapshotWrappers().get(0);
      MappingProfile actualMappingProfile1 = (MappingProfile) mappingProfileWrapper1.getContent();
      testContext.assertEquals(mappingProfile.getId(), actualMappingProfile1.getId());
      testContext.assertEquals(mappingProfile.getId(), mappingProfileWrapper1.getProfileId());
      testContext.assertEquals(mappingProfileWrapper1.getChildSnapshotWrappers().size(), 0);

      ProfileSnapshotWrapper mappingProfileWrapper2 = actionProfileWrapper2.getChildSnapshotWrappers().get(0);
      MappingProfile actualMappingProfile2 = (MappingProfile) mappingProfileWrapper2.getContent();
      testContext.assertEquals(mappingProfile.getId(), actualMappingProfile2.getId());
      testContext.assertEquals(mappingProfile.getId(), mappingProfileWrapper2.getProfileId());
      testContext.assertEquals(mappingProfileWrapper2.getChildSnapshotWrappers().size(), 0);

      async.complete();
    });
  }

  @Test
  public void shouldConstructSnapshotForJobProfile(TestContext testContext) {
    Async async = testContext.async();
    // given
    ProfileSnapshotDao mockDao = Mockito.mock(ProfileSnapshotDaoImpl.class);
    ProfileSnapshotService service = new ProfileSnapshotServiceImpl(mockDao);

    Mockito.when(mockDao.getSnapshotItems(jobProfile.getId(), JOB_PROFILE, jobProfile.getId(), TENANT_ID)).thenReturn(Future.succeededFuture(items));

    // when
    service.constructSnapshot(jobProfile.getId(), JOB_PROFILE, jobProfile.getId(), TENANT_ID).onComplete(ar -> {
    // then
      testContext.assertTrue(ar.succeeded());
      ProfileSnapshotWrapper jobProfileWrapper = ar.result();
      JobProfile actualJobProfile = (JobProfile) jobProfileWrapper.getContent();
      testContext.assertEquals(jobProfile.getId(), actualJobProfile.getId());
      testContext.assertEquals(jobProfile.getId(), jobProfileWrapper.getProfileId());

      ProfileSnapshotWrapper matchProfileWrapper = jobProfileWrapper.getChildSnapshotWrappers().get(0);
      MatchProfile actualMatchProfile = (MatchProfile) matchProfileWrapper.getContent();
      testContext.assertEquals(matchProfile.getId(), actualMatchProfile.getId());
      testContext.assertEquals(matchProfile.getId(), matchProfileWrapper.getProfileId());

      ProfileSnapshotWrapper actionProfileWrapper = matchProfileWrapper.getChildSnapshotWrappers().get(0);
      ActionProfile actualActionProfile = (ActionProfile) actionProfileWrapper.getContent();
      testContext.assertEquals(actionProfile.getId(), actualActionProfile.getId());
      testContext.assertEquals(actionProfile.getId(), actionProfileWrapper.getProfileId());

      ProfileSnapshotWrapper mappingProfileWrapper = actionProfileWrapper.getChildSnapshotWrappers().get(0);
      MappingProfile actualMappingProfile = (MappingProfile) mappingProfileWrapper.getContent();
      testContext.assertEquals(mappingProfile.getId(), actualMappingProfile.getId());
      testContext.assertEquals(mappingProfile.getId(), mappingProfileWrapper.getProfileId());
      async.complete();
    });
  }

  private void assertExpectedChildOnActualChild(ProfileSnapshotWrapper expected, ProfileSnapshotWrapper actual, TestContext context) {
    context.assertEquals(expected.getId(), actual.getId());
    context.assertEquals(expected.getContentType(), actual.getContentType());
    context.assertEquals(expected.getContent().getClass(), actual.getContent().getClass());
  }

  private List<ProfileSnapshotItem> getItemsWithDuplicates() {
    MatchProfile matchProfile2 = new MatchProfile().withId(UUID.randomUUID().toString());

    ProfileSnapshotItem parentMatchProfileSnapshotItem1 = new ProfileSnapshotItem();
    ProfileSnapshotItem childMatchProfileSnapshotItem1 = new ProfileSnapshotItem();
    ProfileSnapshotItem actionProfileSnapshotItem1 = new ProfileSnapshotItem();
    ProfileSnapshotItem mappingProfileSnapshotItem1 = new ProfileSnapshotItem();

    String parentMatchProfileWrapperId1 = UUID.randomUUID().toString();
    String childMatchProfileWrapperId1 = UUID.randomUUID().toString();
    String actionProfileWrapperId1 = UUID.randomUUID().toString();
    String mappingProfileWrapperId1 = UUID.randomUUID().toString();

    parentMatchProfileSnapshotItem1.setAssociationId(UUID.randomUUID().toString());
    parentMatchProfileSnapshotItem1.setMasterId(jobProfile.getId());
    parentMatchProfileSnapshotItem1.setDetailId(matchProfile.getId());
    parentMatchProfileSnapshotItem1.setMasterWrapperId(jobProfileSnapshotItem.getDetailWrapperId());
    parentMatchProfileSnapshotItem1.setDetailWrapperId(parentMatchProfileWrapperId1);
    parentMatchProfileSnapshotItem1.setDetailType(MATCH_PROFILE);
    parentMatchProfileSnapshotItem1.setDetail(matchProfile);

    childMatchProfileSnapshotItem1.setAssociationId(UUID.randomUUID().toString());
    childMatchProfileSnapshotItem1.setMasterId(matchProfile.getId());
    childMatchProfileSnapshotItem1.setDetailId(matchProfile2.getId());
    childMatchProfileSnapshotItem1.setMasterWrapperId(parentMatchProfileWrapperId1);
    childMatchProfileSnapshotItem1.setDetailWrapperId(childMatchProfileWrapperId1);
    childMatchProfileSnapshotItem1.setDetailType(MATCH_PROFILE);
    childMatchProfileSnapshotItem1.setDetail(matchProfile2);

    actionProfileSnapshotItem1.setAssociationId(UUID.randomUUID().toString());
    actionProfileSnapshotItem1.setMasterId(matchProfile2.getId());
    actionProfileSnapshotItem1.setDetailId(actionProfile.getId());
    actionProfileSnapshotItem1.setMasterWrapperId(childMatchProfileWrapperId1);
    actionProfileSnapshotItem1.setDetailWrapperId(actionProfileWrapperId1);
    actionProfileSnapshotItem1.setDetailType(ACTION_PROFILE);
    actionProfileSnapshotItem1.setDetail(actionProfile);

    mappingProfileSnapshotItem1.setAssociationId(UUID.randomUUID().toString());
    mappingProfileSnapshotItem1.setMasterId(actionProfile.getId());
    mappingProfileSnapshotItem1.setDetailId(mappingProfile.getId());
    mappingProfileSnapshotItem1.setMasterWrapperId(actionProfileWrapperId1);
    mappingProfileSnapshotItem1.setDetailWrapperId(mappingProfileWrapperId1);
    mappingProfileSnapshotItem1.setDetailType(MAPPING_PROFILE);
    mappingProfileSnapshotItem1.setDetail(mappingProfile);

    ProfileSnapshotItem parentMatchProfileSnapshotItem2 = new ProfileSnapshotItem();
    ProfileSnapshotItem childMatchProfileSnapshotItem2 = new ProfileSnapshotItem();
    ProfileSnapshotItem actionProfileSnapshotItem2 = new ProfileSnapshotItem();
    ProfileSnapshotItem mappingProfileSnapshotItem2 = new ProfileSnapshotItem();

    String parentMatchProfileWrapperId2 = UUID.randomUUID().toString();
    String childMatchProfileWrapperId2 = UUID.randomUUID().toString();
    String actionProfileWrapperId2 = UUID.randomUUID().toString();
    String mappingProfileWrapperId2 = UUID.randomUUID().toString();

    parentMatchProfileSnapshotItem2.setAssociationId(UUID.randomUUID().toString());
    parentMatchProfileSnapshotItem2.setMasterId(jobProfile.getId());
    parentMatchProfileSnapshotItem2.setDetailId(matchProfile.getId());
    parentMatchProfileSnapshotItem2.setMasterWrapperId(jobProfileSnapshotItem.getDetailWrapperId());
    parentMatchProfileSnapshotItem2.setDetailWrapperId(parentMatchProfileWrapperId2);
    parentMatchProfileSnapshotItem2.setDetailType(MATCH_PROFILE);
    parentMatchProfileSnapshotItem2.setDetail(matchProfile);

    childMatchProfileSnapshotItem2.setAssociationId(UUID.randomUUID().toString());
    childMatchProfileSnapshotItem2.setMasterId(matchProfile.getId());
    childMatchProfileSnapshotItem2.setDetailId(matchProfile2.getId());
    childMatchProfileSnapshotItem2.setMasterWrapperId(parentMatchProfileWrapperId2);
    childMatchProfileSnapshotItem2.setDetailWrapperId(childMatchProfileWrapperId2);
    childMatchProfileSnapshotItem2.setDetailType(MATCH_PROFILE);
    childMatchProfileSnapshotItem2.setDetail(matchProfile2);

    actionProfileSnapshotItem2.setAssociationId(UUID.randomUUID().toString());
    actionProfileSnapshotItem2.setMasterId(matchProfile2.getId());
    actionProfileSnapshotItem2.setDetailId(actionProfile.getId());
    actionProfileSnapshotItem2.setMasterWrapperId(childMatchProfileWrapperId2);
    actionProfileSnapshotItem2.setDetailWrapperId(actionProfileWrapperId2);
    actionProfileSnapshotItem2.setDetailType(ACTION_PROFILE);
    actionProfileSnapshotItem2.setDetail(actionProfile);

    mappingProfileSnapshotItem2.setAssociationId(UUID.randomUUID().toString());
    mappingProfileSnapshotItem2.setMasterId(actionProfile.getId());
    mappingProfileSnapshotItem2.setDetailId(mappingProfile.getId());
    mappingProfileSnapshotItem2.setMasterWrapperId(actionProfileWrapperId2);
    mappingProfileSnapshotItem2.setDetailWrapperId(mappingProfileWrapperId2);
    mappingProfileSnapshotItem2.setDetailType(MAPPING_PROFILE);
    mappingProfileSnapshotItem2.setDetail(mappingProfile);

    return new ArrayList<>(Arrays.asList(
      jobProfileSnapshotItem,
      parentMatchProfileSnapshotItem1,
      childMatchProfileSnapshotItem1,
      actionProfileSnapshotItem1,
      mappingProfileSnapshotItem1,
      parentMatchProfileSnapshotItem2,
      childMatchProfileSnapshotItem2,
      actionProfileSnapshotItem2,
      mappingProfileSnapshotItem2)
    );
  }

  @After
  public void afterTest(TestContext context) {
    Async async = context.async();
    PostgresClient.getInstance(vertx, TENANT_ID).delete(TABLE_NAME, new Criterion(), event -> {
      if (event.failed()) {
        context.fail(event.cause());
      }
      async.complete();
    });
  }
}
