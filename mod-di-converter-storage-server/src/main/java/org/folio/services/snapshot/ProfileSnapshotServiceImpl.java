package org.folio.services.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.snapshot.ProfileSnapshotDao;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;

/**
 * Implementation for Profile snapshot service
 */
@Service
public class ProfileSnapshotServiceImpl implements ProfileSnapshotService {

  private static final Logger LOGGER = LogManager.getLogger();
  private final ProfileSnapshotDao profileSnapshotDao;
  private final Cache<String, ProfileSnapshotWrapper> profileSnapshotWrapperCache;
  private final Executor cacheExecutor = runnable -> {
    Context context = Vertx.currentContext();
    if (context != null) {
      context.runOnContext(ar -> runnable.run());
    } else {
      // The common pool below is used because it is the  default executor for caffeine
      ForkJoinPool.commonPool().execute(runnable);
    }
  };

  public ProfileSnapshotServiceImpl(@Autowired ProfileSnapshotDao profileSnapshotDao) {
    this.profileSnapshotDao = profileSnapshotDao;
    this.profileSnapshotWrapperCache = Caffeine.newBuilder()
      .maximumSize(20)
      .executor(cacheExecutor)
      .build();
  }

  @Override
  public Future<Optional<ProfileSnapshotWrapper>> getById(String id, String tenantId) {
    final String cacheKey = tenantId + id;
    ProfileSnapshotWrapper profileSnapshotWrapper = profileSnapshotWrapperCache.getIfPresent(cacheKey);
    if (profileSnapshotWrapper == null) {
      return profileSnapshotDao.getById(id, tenantId)
        .map(optionalWrapper ->
          optionalWrapper.map(this::convertProfileSnapshotWrapperContent))
        .onSuccess(wrapper -> {
          if (wrapper.isEmpty()) {
            return;
          }
          profileSnapshotWrapperCache.put(cacheKey, wrapper.get());
        });
    }
    return Future.succeededFuture(Optional.of(profileSnapshotWrapper));
  }

  @Override
  public Future<ProfileSnapshotWrapper> createSnapshot(String jobProfileId, String tenantId) {
    Promise<ProfileSnapshotWrapper> promise = Promise.promise();
    return constructSnapshot(jobProfileId, JOB_PROFILE, jobProfileId, tenantId)
      .compose(rootWrapper -> {
        profileSnapshotDao.save(rootWrapper, tenantId).onComplete(savedAr -> {
          if (savedAr.failed()) {
            promise.fail(savedAr.cause());
          } else {
            promise.complete(rootWrapper);
          }
        });
        return promise.future();
      });
  }

  @Override
  public Future<ProfileSnapshotWrapper> constructSnapshot(String profileId, ProfileType profileType, String jobProfileId, String tenantId) {
    return getSnapshotAssociations(profileId, profileType, jobProfileId, tenantId)
      .compose(snapshotAssociations -> {
        if (CollectionUtils.isEmpty(snapshotAssociations)) {
          String errorMessage = "constructSnapshot:: Cannot build snapshot for Profile " + profileId;
          LOGGER.warn(errorMessage);
          return Future.failedFuture(errorMessage);
        }
        return Future.succeededFuture(snapshotAssociations);
      })
      .compose(snapshotAssociations -> Future.succeededFuture(buildSnapshot(snapshotAssociations)));
  }

  @Override
  public Future<List<ProfileAssociation>> getSnapshotAssociations(String profileId, ProfileType profileType, String jobProfileId, String tenantId) {
    return profileSnapshotDao.getSnapshotAssociations(profileId, profileType, jobProfileId, tenantId);
  }

  /**
   * Creates ProfileSnapshotWrapper traversing through collection of profile associations.
   *
   * @param snapshotAssociations list of profile associations (rows)
   * @return root snapshot (ProfileSnapshotWrapper) with child items (ChildSnapshotWrapper) inside
   */
  private ProfileSnapshotWrapper buildSnapshot(List<ProfileAssociation> snapshotAssociations) {
    /* We need to remove duplicates to avoid double-appearance of the same child profiles in diamond inheritance */
    removeDuplicatesByAssociationId(snapshotAssociations);

    Optional<ProfileAssociation> optionalRootItem = snapshotAssociations.stream().filter(item -> item.getMasterProfileId() == null).findFirst();
    if (optionalRootItem.isPresent()) {
      ProfileAssociation rootAssociation = optionalRootItem.get();
      ProfileSnapshotWrapper rootWrapper = new ProfileSnapshotWrapper();
      if (rootAssociation.getReactTo() != null) {
        rootWrapper.setReactTo(rootAssociation.getReactTo());
      }
      rootWrapper.setOrder(rootAssociation.getOrder());
      rootWrapper.setId(UUID.randomUUID().toString());
      rootWrapper.setProfileId(rootAssociation.getDetailProfileId());
      rootWrapper.setProfileWrapperId(rootAssociation.getDetailWrapperId());
      rootWrapper.setContentType(rootAssociation.getDetailProfileType());
      rootWrapper.setContent(convertContentByType(rootAssociation.getDetail(), rootAssociation.getDetailProfileType()));
      fillChildSnapshotWrappers(rootAssociation.getDetailWrapperId(), rootWrapper.getChildSnapshotWrappers(), snapshotAssociations);
      return rootWrapper;
    } else {
      throw new IllegalArgumentException("Can not find the root item in snapshot associations list");
    }
  }

  /**
   * Fills given collection by child wrappers traversing through snapshot.
   * The method finds a first child of given parent profile, adds a child to parent profile(in childWrappers collection)
   * and falls into recursion passing child profile just been found (Depth-first traversal algorithm).
   *
   * @param parentWrapperId      parent wrapper profile id
   * @param childWrappers collection of child snapshot wrappers linked to given parent id
   * @param snapshotAssociations collection of profile associations
   */
  private void fillChildSnapshotWrappers(String parentWrapperId, List<ProfileSnapshotWrapper> childWrappers, List<ProfileAssociation> snapshotAssociations) {
    if (parentWrapperId != null) {
      for (ProfileAssociation snapshotAssociation : snapshotAssociations) {
        if (parentWrapperId.equals(snapshotAssociation.getMasterWrapperId())) {
          ProfileSnapshotWrapper childWrapper = new ProfileSnapshotWrapper();
          childWrapper.setId(UUID.randomUUID().toString());
          childWrapper.setProfileId(snapshotAssociation.getDetailProfileId());
          childWrapper.setProfileWrapperId(snapshotAssociation.getDetailWrapperId());
          childWrapper.setContentType(snapshotAssociation.getDetailProfileType());
          childWrapper.setContent(convertContentByType(snapshotAssociation.getDetail(), snapshotAssociation.getDetailProfileType()));
          if (snapshotAssociation.getReactTo() != null) {
            childWrapper.setReactTo(snapshotAssociation.getReactTo());
          }
          childWrapper.setOrder(snapshotAssociation.getOrder());
          childWrappers.add(childWrapper);
          fillChildSnapshotWrappers(snapshotAssociation.getDetailWrapperId(), childWrapper.getChildSnapshotWrappers(), snapshotAssociations);
        }
      }
    }
  }

  /**
   * Removes the associations with the same id
   *
   * @param snapshotAssociations collection of snapshot associations (rows)
   */
  private void removeDuplicatesByAssociationId(List<ProfileAssociation> snapshotAssociations) {
    Set<String> duplicates = new HashSet<>(snapshotAssociations.size());
    snapshotAssociations.removeIf(current -> !duplicates.add(current.getId()));
  }

  /**
   * Method converts an Object 'content' field to concrete Profile class doing the same for all the child wrappers.
   * to concrete Profile class. The class resolution happens by 'content type' field.
   *
   * @param wrapper the given ProfileSnapshotWrapper
   * @return ProfileSnapshotWrapper with converted 'content' field
   */
  private ProfileSnapshotWrapper convertProfileSnapshotWrapperContent(@NotNull ProfileSnapshotWrapper wrapper) {
    wrapper.setContent(convertContentByType(wrapper.getContent(), wrapper.getContentType()));
    for (ProfileSnapshotWrapper child : wrapper.getChildSnapshotWrappers()) {
      convertProfileSnapshotWrapperContent(child);
    }
    return wrapper;
  }

  /**
   * Method converts an Object 'content' field to concrete Profile class.
   *
   * @param content     wrapper's content
   * @param contentType type of wrapper's content
   * @param <T>         concrete class of the Profile
   * @return concrete class of the Profile
   */
  private <T> T convertContentByType(Object content, ProfileType contentType) {
    ObjectMapper mapper = new ObjectMapper();
      return switch (contentType) {
          case JOB_PROFILE -> (T) mapper.convertValue(content, JobProfile.class);
          case MATCH_PROFILE -> (T) mapper.convertValue(content, MatchProfile.class);
          case ACTION_PROFILE -> (T) mapper.convertValue(content, ActionProfile.class);
          case MAPPING_PROFILE -> (T) mapper.convertValue(content, MappingProfile.class);
          default -> throw new IllegalStateException("Can not find profile by snapshot content type: " + contentType);
      };
  }
}
