package org.folio.services.association;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.ProfileDao;
import org.folio.dao.association.MasterDetailAssociationDao;
import org.folio.dao.association.ProfileAssociationDao;
import org.folio.dao.association.ProfileWrapperDao;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.impl.util.OkapiConnectionParams;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileCollection;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileCollection;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileCollection;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.MatchProfileCollection;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileAssociationCollection;
import org.folio.rest.jaxrs.model.ProfileAssociationRecord;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ProfileWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


/**
 * Generic implementation of the {@link ProfileAssociationService}
 */
@Service
public class CommonProfileAssociationService implements ProfileAssociationService {
  private static final Logger LOGGER = LogManager.getLogger();

  @Autowired
  private ProfileDao<JobProfile, JobProfileCollection> jobProfileDao;
  @Autowired
  private ProfileDao<ActionProfile, ActionProfileCollection> actionProfileDao;
  @Autowired
  private ProfileDao<MappingProfile, MappingProfileCollection> mappingProfileDao;
  @Autowired
  private ProfileDao<MatchProfile, MatchProfileCollection> matchProfileDao;
  @Autowired
  private ProfileAssociationDao profileAssociationDao;
  @Autowired
  private ProfileWrapperDao profileWrapperDao;
  @Autowired
  private MasterDetailAssociationDao masterDetailAssociationDao;

  @Override
  public Future<ProfileAssociationCollection> getAll(ContentType masterType, ContentType detailType, String tenantId) {
    return profileAssociationDao.getAll(masterType, detailType, tenantId);
  }

  @Override
  public Future<Optional<ProfileAssociation>> getById(String id, ContentType masterType, ContentType detailType, String tenantId) {
    return profileAssociationDao.getById(id, masterType, detailType, tenantId)
      .compose(profileAssociationRecordOptional -> {
        if (profileAssociationRecordOptional.isEmpty()) {
          return Future.succeededFuture(Optional.empty());
        }
        ProfileAssociationRecord profileAssociationRecord = profileAssociationRecordOptional.get();
        ProfileAssociation profileAssociation = createProfileAssociation(profileAssociationRecord);
        List<Future> futures = new ArrayList<>();
        if (profileAssociationRecord.getMasterWrapperId() != null) {
          futures.add(getProfileWrapper(tenantId, profileAssociationRecord.getMasterWrapperId())
            .onSuccess(profileWrapperOptional -> profileWrapperOptional.
              ifPresent(profileWrapper -> profileAssociation.setMasterProfileId(profileWrapper.getProfileId()))));
        }
        if (profileAssociationRecord.getDetailWrapperId() != null) {
          futures.add(getProfileWrapper(tenantId, profileAssociationRecord.getDetailWrapperId())
            .onSuccess(profileWrapperOptional -> profileWrapperOptional.
              ifPresent(profileWrapper -> profileAssociation.setDetailProfileId(profileWrapper.getProfileId()))));
        }
        return GenericCompositeFuture.all(futures).compose(ar -> Future.succeededFuture(Optional.of(profileAssociation)));
      });
  }

  private Future<Optional<ProfileWrapper>> getProfileWrapper(String tenantId, String wrapperId) {
    return profileWrapperDao.getProfileWrapperById(wrapperId, tenantId)
      .onFailure(throwable ->
        LOGGER.warn(String.format("getById:: Could not get profile wrapper by id: %s", wrapperId), throwable));
  }

  @Override
  public Future<ProfileAssociation> save(ProfileAssociation entity, ContentType masterType, ContentType detailType, String tenantId) {
    entity.setId(UUID.randomUUID().toString());
    return wrapAssociationProfiles(new ArrayList<>(List.of(entity)), new ArrayList<>(), new HashMap<>(), tenantId)
      .compose((result) -> {
        if (result.size() > 0) {
          return profileAssociationDao.save(result.get(0), masterType, detailType, tenantId).map(entity);
        }
        return Future.failedFuture("Cannot create profile associations");
      });
  }

  @Override
  public Future<List<ProfileAssociationRecord>> save(List<ProfileAssociation> profileAssociations, String tenantId) {
    Promise<List<ProfileAssociationRecord>> result = Promise.promise();
    wrapAssociationProfiles(profileAssociations, new ArrayList<>(), new HashMap<>(), tenantId)
      .onSuccess(wrappedAssociations -> {
        List<Future<ProfileAssociationRecord>> futureList = new ArrayList<>();
        wrappedAssociations.forEach(association -> futureList.add(profileAssociationDao.save(association,
          ProfileSnapshotWrapper.ContentType.fromValue(association.getMasterProfileType().name()),
          ProfileSnapshotWrapper.ContentType.fromValue(association.getDetailProfileType().name()), tenantId).map(association)));
        GenericCompositeFuture.all(futureList).onComplete(ar -> {
          if (ar.succeeded()) {
            result.complete(wrappedAssociations);
          } else {
            result.fail(ar.cause());
          }
        });
      });
    return result.future();
  }

  private Future<List<ProfileAssociationRecord>> wrapAssociationProfiles(List<ProfileAssociation> profileAssociations,
                                                                         List<ProfileAssociationRecord> profileAssociationRecords,
                                                                         HashMap<String, String> profileIdToWrapperId,
                                                                         String tenantId) {
    Optional<ProfileAssociation> profileAssociationOptional = profileAssociations.stream().findFirst();
    List<Future> futureList = new ArrayList<>();
    if (profileAssociationOptional.isEmpty()) {
      return Future.succeededFuture(profileAssociationRecords);
    }
    ProfileAssociation profileAssociation = profileAssociationOptional.get();
    ProfileAssociationRecord profileAssociationRecord = CommonProfileAssociationService
      .createProfileAssociationRecord(profileAssociation.withId(UUID.randomUUID().toString()));

    if (profileAssociation.getMasterProfileId() != null) {
      if (profileIdToWrapperId.containsKey(profileAssociation.getMasterProfileId())) {
        profileAssociationRecord.setMasterWrapperId(profileIdToWrapperId.get(profileAssociation.getMasterProfileId()));
      } else {
        futureList.add(saveWrapper(tenantId, profileAssociation.getMasterProfileType(), profileAssociation.getMasterProfileId())
          .onSuccess(result -> {
              profileIdToWrapperId.put(result.getProfileId(), result.getId());
              profileAssociationRecord.setMasterWrapperId(result.getId());
          }));
      }
    }
    if (profileAssociation.getDetailProfileId() != null) {
      futureList.add(saveWrapper(tenantId, profileAssociation.getDetailProfileType(), profileAssociation.getDetailProfileId())
        .onSuccess(result -> {
            profileIdToWrapperId.put(result.getProfileId(), result.getId());
            profileAssociationRecord.setDetailWrapperId(result.getId());
        }));
    }
    return GenericCompositeFuture.all(futureList).onSuccess(ar -> {
      profileAssociationRecords.add(profileAssociationRecord);
      profileAssociations.remove(0);
    }).compose(ar -> wrapAssociationProfiles(profileAssociations, profileAssociationRecords, profileIdToWrapperId, tenantId));
  }

  private Future<ProfileWrapper> saveWrapper(String tenantId, ProfileType profileType, String profileId) {
    ProfileWrapper profileWrapper = new ProfileWrapper().withId(UUID.randomUUID().toString())
      .withProfileType(profileType)
      .withProfileId(profileId);
    return profileWrapperDao.save(profileWrapper, tenantId).map(profileWrapper);
  }

  @Override
  public Future<ProfileAssociation> update(ProfileAssociation entity, ContentType masterType, ContentType detailType, OkapiConnectionParams params) {
    return profileAssociationDao.update(entity, masterType, detailType, params.getTenantId());
  }

  @Override
  public Future<Boolean> delete(String id, ContentType masterType, ContentType detailType, String tenantId) {
    return profileAssociationDao.delete(id, masterType, detailType, tenantId);
  }

  @Override
  public Future<Optional<ProfileSnapshotWrapper>> findDetails(String masterId, ContentType masterType, ContentType detailType, String query, int offset, int limit, String tenantId) {
    Promise<Optional<ProfileSnapshotWrapper>> result = Promise.promise();

    masterDetailAssociationDao.getDetailProfilesByMasterId(masterId, detailType, query, offset, limit, tenantId)
      .onComplete(ar -> {
        if (ar.failed()) {
          LOGGER.warn("findDetails:: Could not get details profiles by master id '{}', for the tenant '{}'", masterId, tenantId);
          result.fail(ar.cause());
        }
        List<ProfileSnapshotWrapper> details = ar.result();
        ProfileSnapshotWrapper wrapper = getProfileSnapshotWrapper(masterId, masterType, details);
        fillProfile(tenantId, result, wrapper);
      });

    return result.future();
  }

  @Override
  public Future<Optional<ProfileSnapshotWrapper>> findMasters(String detailId, ContentType detailType, ContentType masterType, String query, int offset, int limit, String tenantId) {
    Promise<Optional<ProfileSnapshotWrapper>> result = Promise.promise();

    masterDetailAssociationDao.getMasterProfilesByDetailId(detailId, masterType, query, offset, limit, tenantId)
      .onComplete(ar -> {
        if (ar.failed()) {
          LOGGER.warn("findMasters:: Could not get master profiles by detail id '{}', for the tenant '{}'", detailId, tenantId);
          result.fail(ar.cause());
        }
        ProfileSnapshotWrapper wrapper = getProfileSnapshotWrapper(detailId, detailType, ar.result());
        fillProfile(tenantId, result, wrapper);
      });

    return result.future();
  }

  @Override
  public Future<Boolean> delete(String masterId, String detailId, ContentType masterType, ContentType detailType, String tenantId, String jobProfileId) {
    return profileAssociationDao.delete(masterId, detailId, masterType, detailType, tenantId, jobProfileId);
  }

  @Override
  public Future<Boolean> deleteByMasterId(String masterId, ContentType masterType, ContentType detailType, String tenantId) {
    return profileAssociationDao.deleteByMasterId(masterId, masterType, detailType, tenantId);
  }

  /**
   * Retrieves profile by profile id and profile type and then fill profile wrapper with the instance.
   *
   * @param tenantId a tenant id.
   * @param result   a result future.
   * @param wrapper  a profile wrapper.
   */
  private void fillProfile(String tenantId, Promise<Optional<ProfileSnapshotWrapper>> result, ProfileSnapshotWrapper wrapper) {
    String profileId = wrapper.getId();
    ContentType profileType = wrapper.getContentType();

    if (profileType == ContentType.JOB_PROFILE) {
      jobProfileDao.getProfileById(profileId, tenantId).onComplete(fillSnapshotWrapperContent(result, wrapper));
    } else if (profileType == ContentType.ACTION_PROFILE) {
      actionProfileDao.getProfileById(profileId, tenantId).onComplete(fillSnapshotWrapperContent(result, wrapper));
    } else if (profileType == ContentType.MAPPING_PROFILE) {
      mappingProfileDao.getProfileById(profileId, tenantId).onComplete(fillSnapshotWrapperContent(result, wrapper));
    } else if (profileType == ContentType.MATCH_PROFILE) {
      matchProfileDao.getProfileById(profileId, tenantId).onComplete(fillSnapshotWrapperContent(result, wrapper));
    } else {
      result.complete(Optional.empty());
    }
  }

  /**
   * Creates a profile wrapper.
   *
   * @param profileId   a profile id.
   * @param profileType a profile type.
   * @param children    a list of children
   * @return profile wrapper
   */
  private ProfileSnapshotWrapper getProfileSnapshotWrapper(String profileId, ContentType profileType, List<ProfileSnapshotWrapper> children) {
    ProfileSnapshotWrapper wrapper = new ProfileSnapshotWrapper();
    wrapper.setChildSnapshotWrappers(children);
    wrapper.setId(profileId);
    wrapper.setContentType(ContentType.fromValue(profileType.value()));
    return wrapper;
  }

  /**
   * Fills a profile wrapper with a profile instance if it's present otherwise it will complete result future with empty optional.
   *
   * @param result  a future result.
   * @param wrapper a profile wrapper.
   * @param <T>     a profile type.
   * @return the handler.
   */
  private <T> Handler<AsyncResult<Optional<T>>> fillSnapshotWrapperContent(Promise<Optional<ProfileSnapshotWrapper>> result, ProfileSnapshotWrapper wrapper) {
    return asyncResult -> {
      if (asyncResult.failed()) {
        LOGGER.warn("fillWrapperContent:: Could not get a profile", asyncResult.cause());
        result.fail(asyncResult.cause());
      }

      Optional<T> resultOptional = asyncResult.result();
      if (resultOptional.isPresent()) {
        wrapper.setContent(resultOptional.get());
        result.complete(Optional.of(wrapper));
      } else {
        result.complete(Optional.empty());
      }
    };
  }

  public static ProfileAssociationRecord createProfileAssociationRecord(ProfileAssociation entity) {
    return new ProfileAssociationRecord()
      .withId(entity.getId())
      .withJobProfileId(entity.getJobProfileId())
      .withMasterProfileType(entity.getMasterProfileType())
      .withDetailProfileType(entity.getDetailProfileType())
      .withOrder(entity.getOrder())
      .withTriggered(entity.getTriggered())
      .withReactTo(entity.getReactTo());
  }

  public static ProfileAssociation createProfileAssociation(ProfileAssociationRecord entity) {
    return new ProfileAssociation()
      .withId(entity.getId())
      .withJobProfileId(entity.getJobProfileId())
      .withMasterProfileType(entity.getMasterProfileType())
      .withDetailProfileType(entity.getDetailProfileType())
      .withOrder(entity.getOrder())
      .withTriggered(entity.getTriggered())
      .withReactTo(entity.getReactTo());
  }
}
