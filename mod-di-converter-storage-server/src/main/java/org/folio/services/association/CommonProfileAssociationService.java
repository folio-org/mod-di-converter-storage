package org.folio.services.association;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
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
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ProfileWrapper;
import org.folio.rest.jaxrs.model.ReactToType;
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
    return profileAssociationDao.getById(id, masterType, detailType, tenantId);
  }

  @Override
  public Future<ProfileAssociation> save(ProfileAssociation entity, ContentType masterType, ContentType detailType, String tenantId) {
    entity.setId(UUID.randomUUID().toString());
    return wrapAssociationProfiles(new ArrayList<>(List.of(entity)), tenantId)
      .compose((result) -> profileAssociationDao.save(entity, masterType, detailType, tenantId).map(entity));
  }

  @Override
  public Future<List<ProfileAssociation>> save(List<ProfileAssociation> profileAssociations, String tenantId) {
    Promise<List<ProfileAssociation>> result = Promise.promise();
    wrapAssociationProfiles(profileAssociations, tenantId)
      .onSuccess(wrappedAssociations -> {
        List<Future<ProfileAssociation>> futureList = new ArrayList<>();
        profileAssociations.forEach(association -> futureList.add(profileAssociationDao.save(association,
          ProfileSnapshotWrapper.ContentType.fromValue(association.getMasterProfileType().name()),
          ProfileSnapshotWrapper.ContentType.fromValue(association.getDetailProfileType().name()), tenantId).map(association)));
        GenericCompositeFuture.all(futureList).onComplete(ar -> {
          if (ar.succeeded()) {
            result.complete(profileAssociations);
          } else {
            result.fail(ar.cause());
          }
        });
      });
    return result.future();
  }

  public Future<Void> wrapAssociationProfiles(List<ProfileAssociation> profileAssociations,
                                              String tenantId) {
    if (profileAssociations.isEmpty()) {
      return Future.succeededFuture();
    }

    HashMap<String, String> profileIdToWrapperId = new HashMap<>();
    Future<Void> future = Future.succeededFuture();
    for (ProfileAssociation profileAssociation : profileAssociations) {
      future = future
        .compose(ar -> {
          if (profileAssociation.getMasterProfileId() != null && profileAssociation.getMasterWrapperId() == null) {
            if (profileIdToWrapperId.containsKey(profileAssociation.getMasterProfileId())) {
              profileAssociation.setMasterWrapperId(profileIdToWrapperId.get(profileAssociation.getMasterProfileId()));
            } else {
              return saveWrapper(tenantId, profileAssociation.getMasterProfileType(), profileAssociation.getMasterProfileId())
                .onFailure(th -> LOGGER.error("Something happened while saving master wrapper for association: {}", Json.encode(profileAssociation), th))
                .compose(result -> {
                  profileIdToWrapperId.put(result.getProfileId(), result.getId());
                  profileAssociation.setMasterWrapperId(result.getId());
                  return Future.succeededFuture();
                });
            }
          }
          return Future.succeededFuture();
        })
        .compose(ar -> {
          if (profileAssociation.getDetailProfileId() != null && profileAssociation.getDetailWrapperId() == null) {
            return saveWrapper(tenantId, profileAssociation.getDetailProfileType(), profileAssociation.getDetailProfileId())
              .onFailure(th -> LOGGER.error("Something happened while saving detail wrapper for association: {}", Json.encode(profileAssociation),  th))
              .compose(result -> {
                profileIdToWrapperId.put(result.getProfileId(), result.getId());
                profileAssociation.setDetailWrapperId(result.getId());
                return Future.succeededFuture();
              });
          }
          return Future.succeededFuture();
        });
    }
    return future;
  }

  private Future<ProfileWrapper> saveWrapper(String tenantId, ProfileType profileType, String profileId) {
    ProfileWrapper profileWrapper = new ProfileWrapper().withId(UUID.randomUUID().toString())
      .withProfileType(profileType)
      .withProfileId(profileId);
    return profileWrapperDao.save(profileWrapper, tenantId).map(profileWrapper);
  }

  @Override
  public Future<ProfileAssociation> update(ProfileAssociation entity, ContentType masterType, ContentType detailType, OkapiConnectionParams params) {
    return profileWrapperDao.deleteById(entity.getMasterProfileId(), params.getTenantId())
      .compose(e -> profileWrapperDao.deleteById(entity.getDetailProfileId(), params.getTenantId()))
      .compose(r -> {
        ProfileWrapper masterWrapper = new ProfileWrapper();
        masterWrapper.setId(UUID.randomUUID().toString());
        masterWrapper.setProfileType(entity.getMasterProfileType());
        masterWrapper.setProfileId(entity.getMasterProfileId());
        return profileWrapperDao.save(masterWrapper, params.getTenantId());
      })
      .compose(s -> {
        ProfileWrapper detailWrapper = new ProfileWrapper();
        detailWrapper.setId(UUID.randomUUID().toString());
        detailWrapper.setProfileType(entity.getDetailProfileType());
        detailWrapper.setProfileId(entity.getDetailProfileId());
        return profileWrapperDao.save(detailWrapper, params.getTenantId());
      })
      .compose(f -> profileAssociationDao.update(entity, masterType, detailType, params.getTenantId()));
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
  public Future<Boolean> delete(String masterWrapperId, String detailWrapperId, ContentType masterType, ContentType detailType,
                                String jobProfileId, ReactToType reactTo, Integer order, String tenantId) {
    return profileAssociationDao.delete(masterWrapperId, detailWrapperId, masterType, detailType, jobProfileId, reactTo, order, tenantId);
  }

  @Override
  public Future<Boolean> deleteByMasterWrapperId(String wrapperId, ContentType masterType, ContentType detailType, String tenantId) {
    return profileAssociationDao.deleteByMasterWrapperId(wrapperId, masterType, detailType, tenantId);
  }

  @Override
  public Future<Boolean> deleteByMasterIdAndDetailId(String masterId, String detailId, ContentType masterType,
                                                     ContentType detailType, String tenantId) {
    LOGGER.debug("deleteByMasterIdAndDetailId : masterId={}, detailId={}, masterType={}, detailType={}",
      masterId, detailId, masterType.value(), detailType.value());
    return profileAssociationDao.deleteByMasterIdAndDetailId(masterId, detailId, masterType, detailType, tenantId);
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
}
