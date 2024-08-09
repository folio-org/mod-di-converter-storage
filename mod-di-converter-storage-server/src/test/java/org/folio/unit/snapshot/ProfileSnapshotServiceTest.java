package org.folio.unit.snapshot;

import io.vertx.core.Future;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.folio.dao.snapshot.ProfileSnapshotDao;
import org.folio.dao.snapshot.ProfileSnapshotDaoImpl;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileCollection;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileCollection;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileCollection;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.MatchProfileCollection;
import org.folio.rest.jaxrs.model.MatchProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ReactToType;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.ProfileService;
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

import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MAPPING_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;

public class ProfileSnapshotServiceTest extends AbstractUnitTest {
  private static final String TABLE_NAME = "profile_snapshots";

  @Autowired
  private ProfileSnapshotDao dao;
  @Autowired
  private ProfileSnapshotService service;
  @Autowired
  private ProfileService<JobProfile, JobProfileCollection, JobProfileUpdateDto> jobProfileService;
  @Autowired
  private ProfileService<MatchProfile, MatchProfileCollection, MatchProfileUpdateDto> matchProfileService;
  @Autowired
  private ProfileService<ActionProfile, ActionProfileCollection, ActionProfileUpdateDto> actionProfileService;
  @Autowired
  private ProfileService<MappingProfile, MappingProfileCollection, MappingProfileUpdateDto> mappingProfileService;

  private JobProfile jobProfile = new JobProfile().withId(UUID.randomUUID().toString());
  private ProfileAssociation jobProfileSnapshotAssociation = new ProfileAssociation();

  private MatchProfile matchProfile = new MatchProfile().withId(UUID.randomUUID().toString());
  private ProfileAssociation matchProfileSnapshotAssociation = new ProfileAssociation();

  private ActionProfile actionProfile = new ActionProfile().withId(UUID.randomUUID().toString());
  private ProfileAssociation actionProfileSnapshotAssociation = new ProfileAssociation();

  private MappingProfile mappingProfile = new MappingProfile().withId(UUID.randomUUID().toString());
  private ProfileAssociation mappingProfileSnapshotAssociation = new ProfileAssociation();

  private List<ProfileAssociation> associations;

  @Before
  public void setUp() {
    String jobProfileWrapperId = UUID.randomUUID().toString();
    String matchProfileWrapperId = UUID.randomUUID().toString();
    String actionProfileWrapperId = UUID.randomUUID().toString();
    String mappingProfileWrapperId = UUID.randomUUID().toString();

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
      mappingProfileSnapshotAssociation)
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
  public void shouldReturnFailedFutureIfNoSnapshotAssociationsExist(TestContext context) {
    Async async = context.async();
    // given
    ProfileSnapshotDao mockDao = Mockito.mock(ProfileSnapshotDaoImpl.class);
    ProfileSnapshotService service = new ProfileSnapshotServiceImpl(dao, jobProfileService, matchProfileService, actionProfileService, mappingProfileService);

    String jobProfileId = UUID.randomUUID().toString();
    Mockito.when(mockDao.getSnapshotAssociations(jobProfileId, JOB_PROFILE, jobProfileId, TENANT_ID)).thenReturn(Future.succeededFuture(new ArrayList<>()));

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
    ProfileSnapshotService service = new ProfileSnapshotServiceImpl(mockDao, jobProfileService, matchProfileService, actionProfileService, mappingProfileService);

    Mockito.when(mockDao.getSnapshotAssociations(jobProfile.getId(), JOB_PROFILE, jobProfile.getId(), TENANT_ID)).thenReturn(Future.succeededFuture(associations));
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
    ProfileSnapshotService service = new ProfileSnapshotServiceImpl(mockDao, jobProfileService, matchProfileService, actionProfileService, mappingProfileService);

    Mockito.when(mockDao.getSnapshotAssociations(jobProfile.getId(), JOB_PROFILE, jobProfile.getId(), TENANT_ID)).thenReturn(Future.succeededFuture(getAssociationsWithDuplicates()));

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
    ProfileSnapshotService service = new ProfileSnapshotServiceImpl(mockDao, jobProfileService, matchProfileService, actionProfileService, mappingProfileService);

    Mockito.when(mockDao.getSnapshotAssociations(jobProfile.getId(), JOB_PROFILE, jobProfile.getId(), TENANT_ID)).thenReturn(Future.succeededFuture(associations));

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

  @Test
  public void shouldReturnSnapshotAssociations(TestContext testContext) {
    Async async = testContext.async();
    // given
    ProfileSnapshotDao mockDao = Mockito.mock(ProfileSnapshotDaoImpl.class);
    ProfileSnapshotService service = new ProfileSnapshotServiceImpl(mockDao, jobProfileService, matchProfileService, actionProfileService, mappingProfileService);

    Mockito.when(mockDao.getSnapshotAssociations(jobProfile.getId(), JOB_PROFILE, jobProfile.getId(), TENANT_ID)).thenReturn(Future.succeededFuture(associations));

    // when
    service.getSnapshotAssociations(jobProfile.getId(), JOB_PROFILE, jobProfile.getId(), TENANT_ID).onComplete(ar -> {
      // then
      testContext.assertTrue(ar.succeeded());
      List<ProfileAssociation> profileAssociations = ar.result();
      testContext.assertEquals(profileAssociations, associations);
      async.complete();
    });
  }

  private void assertExpectedChildOnActualChild(ProfileSnapshotWrapper expected, ProfileSnapshotWrapper actual, TestContext context) {
    context.assertEquals(expected.getId(), actual.getId());
    context.assertEquals(expected.getContentType(), actual.getContentType());
    context.assertEquals(expected.getContent().getClass(), actual.getContent().getClass());
  }

  private List<ProfileAssociation> getAssociationsWithDuplicates() {
    MatchProfile matchProfile2 = new MatchProfile().withId(UUID.randomUUID().toString());

    ProfileAssociation parentMatchProfileSnapshotAssociation1 = new ProfileAssociation();
    ProfileAssociation childMatchProfileSnapshotAssociation1 = new ProfileAssociation();
    ProfileAssociation actionProfileSnapshotAssociation1 = new ProfileAssociation();
    ProfileAssociation mappingProfileSnapshotAssociation1 = new ProfileAssociation();

    String parentMatchProfileWrapperId1 = UUID.randomUUID().toString();
    String childMatchProfileWrapperId1 = UUID.randomUUID().toString();
    String actionProfileWrapperId1 = UUID.randomUUID().toString();
    String mappingProfileWrapperId1 = UUID.randomUUID().toString();

    parentMatchProfileSnapshotAssociation1.setId(UUID.randomUUID().toString());
    parentMatchProfileSnapshotAssociation1.setMasterProfileId(jobProfile.getId());
    parentMatchProfileSnapshotAssociation1.setDetailProfileId(matchProfile.getId());
    parentMatchProfileSnapshotAssociation1.setMasterWrapperId(jobProfileSnapshotAssociation.getDetailWrapperId());
    parentMatchProfileSnapshotAssociation1.setDetailWrapperId(parentMatchProfileWrapperId1);
    parentMatchProfileSnapshotAssociation1.setDetailProfileType(MATCH_PROFILE);
    parentMatchProfileSnapshotAssociation1.setDetail(matchProfile);

    childMatchProfileSnapshotAssociation1.setId(UUID.randomUUID().toString());
    childMatchProfileSnapshotAssociation1.setMasterProfileId(matchProfile.getId());
    childMatchProfileSnapshotAssociation1.setDetailProfileId(matchProfile2.getId());
    childMatchProfileSnapshotAssociation1.setMasterWrapperId(parentMatchProfileWrapperId1);
    childMatchProfileSnapshotAssociation1.setDetailWrapperId(childMatchProfileWrapperId1);
    childMatchProfileSnapshotAssociation1.setDetailProfileType(MATCH_PROFILE);
    childMatchProfileSnapshotAssociation1.setDetail(matchProfile2);

    actionProfileSnapshotAssociation1.setId(UUID.randomUUID().toString());
    actionProfileSnapshotAssociation1.setMasterProfileId(matchProfile2.getId());
    actionProfileSnapshotAssociation1.setDetailProfileId(actionProfile.getId());
    actionProfileSnapshotAssociation1.setMasterWrapperId(childMatchProfileWrapperId1);
    actionProfileSnapshotAssociation1.setDetailWrapperId(actionProfileWrapperId1);
    actionProfileSnapshotAssociation1.setDetailProfileType(ACTION_PROFILE);
    actionProfileSnapshotAssociation1.setDetail(actionProfile);

    mappingProfileSnapshotAssociation1.setId(UUID.randomUUID().toString());
    mappingProfileSnapshotAssociation1.setMasterProfileId(actionProfile.getId());
    mappingProfileSnapshotAssociation1.setDetailProfileId(mappingProfile.getId());
    mappingProfileSnapshotAssociation1.setMasterWrapperId(actionProfileWrapperId1);
    mappingProfileSnapshotAssociation1.setDetailWrapperId(mappingProfileWrapperId1);
    mappingProfileSnapshotAssociation1.setDetailProfileType(MAPPING_PROFILE);
    mappingProfileSnapshotAssociation1.setDetail(mappingProfile);

    ProfileAssociation parentMatchProfileSnapshotAssociation2 = new ProfileAssociation();
    ProfileAssociation childMatchProfileSnapshotAssociation2 = new ProfileAssociation();
    ProfileAssociation actionProfileSnapshotAssociation2 = new ProfileAssociation();
    ProfileAssociation mappingProfileSnapshotAssociation2 = new ProfileAssociation();

    String parentMatchProfileWrapperId2 = UUID.randomUUID().toString();
    String childMatchProfileWrapperId2 = UUID.randomUUID().toString();
    String actionProfileWrapperId2 = UUID.randomUUID().toString();
    String mappingProfileWrapperId2 = UUID.randomUUID().toString();

    parentMatchProfileSnapshotAssociation2.setId(UUID.randomUUID().toString());
    parentMatchProfileSnapshotAssociation2.setMasterProfileId(jobProfile.getId());
    parentMatchProfileSnapshotAssociation2.setDetailProfileId(matchProfile.getId());
    parentMatchProfileSnapshotAssociation2.setMasterWrapperId(jobProfileSnapshotAssociation.getDetailWrapperId());
    parentMatchProfileSnapshotAssociation2.setDetailWrapperId(parentMatchProfileWrapperId2);
    parentMatchProfileSnapshotAssociation2.setDetailProfileType(MATCH_PROFILE);
    parentMatchProfileSnapshotAssociation2.setDetail(matchProfile);

    childMatchProfileSnapshotAssociation2.setId(UUID.randomUUID().toString());
    childMatchProfileSnapshotAssociation2.setMasterProfileId(matchProfile.getId());
    childMatchProfileSnapshotAssociation2.setDetailProfileId(matchProfile2.getId());
    childMatchProfileSnapshotAssociation2.setMasterWrapperId(parentMatchProfileWrapperId2);
    childMatchProfileSnapshotAssociation2.setDetailWrapperId(childMatchProfileWrapperId2);
    childMatchProfileSnapshotAssociation2.setDetailProfileType(MATCH_PROFILE);
    childMatchProfileSnapshotAssociation2.setDetail(matchProfile2);

    actionProfileSnapshotAssociation2.setId(UUID.randomUUID().toString());
    actionProfileSnapshotAssociation2.setMasterProfileId(matchProfile2.getId());
    actionProfileSnapshotAssociation2.setDetailProfileId(actionProfile.getId());
    actionProfileSnapshotAssociation2.setMasterWrapperId(childMatchProfileWrapperId2);
    actionProfileSnapshotAssociation2.setDetailWrapperId(actionProfileWrapperId2);
    actionProfileSnapshotAssociation2.setDetailProfileType(ACTION_PROFILE);
    actionProfileSnapshotAssociation2.setDetail(actionProfile);

    mappingProfileSnapshotAssociation2.setId(UUID.randomUUID().toString());
    mappingProfileSnapshotAssociation2.setMasterProfileId(actionProfile.getId());
    mappingProfileSnapshotAssociation2.setDetailProfileId(mappingProfile.getId());
    mappingProfileSnapshotAssociation2.setMasterWrapperId(actionProfileWrapperId2);
    mappingProfileSnapshotAssociation2.setDetailWrapperId(mappingProfileWrapperId2);
    mappingProfileSnapshotAssociation2.setDetailProfileType(MAPPING_PROFILE);
    mappingProfileSnapshotAssociation2.setDetail(mappingProfile);

    return new ArrayList<>(Arrays.asList(
      jobProfileSnapshotAssociation,
      parentMatchProfileSnapshotAssociation1,
      childMatchProfileSnapshotAssociation1,
      actionProfileSnapshotAssociation1,
      mappingProfileSnapshotAssociation1,
      parentMatchProfileSnapshotAssociation2,
      childMatchProfileSnapshotAssociation2,
      actionProfileSnapshotAssociation2,
      mappingProfileSnapshotAssociation2)
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
