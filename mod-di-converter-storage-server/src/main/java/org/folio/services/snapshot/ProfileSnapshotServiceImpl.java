package org.folio.services.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.snapshot.ProfileSnapshotDao;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.impl.util.OkapiConnectionParams;
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
import org.folio.services.ProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.validation.constraints.NotNull;

import javax.ws.rs.BadRequestException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiFunction;

import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MAPPING_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;

/**
 * Implementation for Profile snapshot service
 */
@Service
public class ProfileSnapshotServiceImpl implements ProfileSnapshotService {
  private static final Logger LOGGER = LogManager.getLogger();
  private static final List<ProfileType> IMPORT_PROFILES_ORDER = List.of(MAPPING_PROFILE, ACTION_PROFILE, MATCH_PROFILE, JOB_PROFILE);
  public static final String PROFILE_SNAPSHOT_INVALID_TYPE = "Cannot import profile snapshot of %s required type is %s";
  private ProfileService<JobProfile, JobProfileCollection, JobProfileUpdateDto> jobProfileService;
  private ProfileService<MatchProfile, MatchProfileCollection, MatchProfileUpdateDto> matchProfileService;
  private ProfileService<ActionProfile, ActionProfileCollection, ActionProfileUpdateDto> actionProfileService;
  private ProfileService<MappingProfile, MappingProfileCollection, MappingProfileUpdateDto> mappingProfileService;
  private final EnumMap<ProfileType, BiFunction<ProfileSnapshotWrapper, OkapiConnectionParams, Future<Object>>> profileTypeToSaveFunction;
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

  public ProfileSnapshotServiceImpl(@Autowired ProfileSnapshotDao profileSnapshotDao,
                                    @Autowired ProfileService<JobProfile, JobProfileCollection, JobProfileUpdateDto> jobProfileService,
                                    @Autowired ProfileService<MatchProfile, MatchProfileCollection, MatchProfileUpdateDto> matchProfileService,
                                    @Autowired ProfileService<ActionProfile, ActionProfileCollection, ActionProfileUpdateDto> actionProfileService,
                                    @Autowired ProfileService<MappingProfile, MappingProfileCollection, MappingProfileUpdateDto> mappingProfileService) {
    this.profileSnapshotDao = profileSnapshotDao;
    this.jobProfileService = jobProfileService;
    this.matchProfileService = matchProfileService;
    this.actionProfileService = actionProfileService;
    this.mappingProfileService = mappingProfileService;
    this.profileSnapshotWrapperCache = Caffeine.newBuilder()
      .maximumSize(20)
      .executor(cacheExecutor)
      .build();

    profileTypeToSaveFunction = new EnumMap<>(ProfileType.class);

    profileTypeToSaveFunction.put(MAPPING_PROFILE, (snapshot, okapiParams) -> saveProfile(okapiParams, new MappingProfileUpdateDto()
      .withProfile((MappingProfile) snapshot.getContent()), mappingProfileService, ((MappingProfile) snapshot.getContent()).getId()).map(p -> p));

    profileTypeToSaveFunction.put(ACTION_PROFILE, (snapshot, okapiParams) -> saveProfile(okapiParams, new ActionProfileUpdateDto()
      .withProfile((ActionProfile) snapshot.getContent())
      .withAddedRelations(formAddedRelations(snapshot, ACTION_PROFILE)), actionProfileService, ((ActionProfile) snapshot.getContent()).getId()).map(p -> p));

    profileTypeToSaveFunction.put(MATCH_PROFILE, (snapshot, okapiParams) -> saveProfile(okapiParams, new MatchProfileUpdateDto()
      .withProfile((MatchProfile) snapshot.getContent()), matchProfileService, ((MatchProfile) snapshot.getContent()).getId()).map(p -> p));

    profileTypeToSaveFunction.put(JOB_PROFILE, (snapshot, okapiParams) -> saveProfile(okapiParams, new JobProfileUpdateDto()
      .withProfile((JobProfile) snapshot.getContent())
      .withAddedRelations(formAddedRelations(snapshot, JOB_PROFILE)), jobProfileService, ((JobProfile) snapshot.getContent()).getId()).map(p -> p));
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
  public Future<ProfileSnapshotWrapper> importSnapshot(ProfileSnapshotWrapper profileSnapshot, String tenantId, OkapiConnectionParams okapiParams) {
    if (profileSnapshot.getContentType() != JOB_PROFILE) {
      return Future.failedFuture(new BadRequestException(String.format(PROFILE_SNAPSHOT_INVALID_TYPE,
        profileSnapshot.getContentType(), JOB_PROFILE)));
    }

    convertProfileSnapshotWrapperContent(profileSnapshot);

    var profileTypeToSnapshots = getProfileTypeToSnapshot(profileSnapshot);
    var profileTypesInSaveOrder = profileTypeToSnapshots.keySet().stream()
      .sorted(Comparator.comparingInt(IMPORT_PROFILES_ORDER::indexOf))
      .toList();

    Future<List<Object>> saveFuture = Future.succeededFuture();
    for (ProfileType profileType : profileTypesInSaveOrder) {
      saveFuture = saveFuture.compose(v -> processProfilesSaving(profileTypeToSnapshots.get(profileType), profileType, okapiParams));
    }
    return saveFuture
      .compose(v -> constructSnapshot(profileSnapshot.getProfileId(), JOB_PROFILE, profileSnapshot.getProfileId(), okapiParams.getTenantId()));
  }

  @Override
  public Future<List<ProfileAssociation>> getSnapshotAssociations(String profileId, ProfileType profileType, String jobProfileId, String tenantId) {
    return profileSnapshotDao.getSnapshotAssociations(profileId, profileType, jobProfileId, tenantId);
  }

  private Future<List<Object>> processProfilesSaving(List<ProfileSnapshotWrapper> profileSnapshots, ProfileType profileType, OkapiConnectionParams okapiParams) {
    List<Future<Object>> futures = new ArrayList<>();
    var saveProfileFunction = profileTypeToSaveFunction.get(profileType);

    profileSnapshots.forEach(profileSnapshot -> futures.add(saveProfileFunction.apply(profileSnapshot, okapiParams)));

    return GenericCompositeFuture.all(futures).map(CompositeFuture::list);
  }

  private EnumMap<ProfileType, List<ProfileSnapshotWrapper>>  getProfileTypeToSnapshot(ProfileSnapshotWrapper snapshot) {
    EnumMap<ProfileType, List<ProfileSnapshotWrapper>> profileTypeToSnapshots = new EnumMap<>(ProfileType.class);
    addSnapshotsToMap(snapshot, profileTypeToSnapshots);
    return profileTypeToSnapshots;
  }

  private void addSnapshotsToMap(ProfileSnapshotWrapper snapshot, EnumMap<ProfileType, List<ProfileSnapshotWrapper>> profileTypeToSnapshots) {
    List<ProfileSnapshotWrapper> snapshots = profileTypeToSnapshots.computeIfAbsent(snapshot.getContentType(), k -> new ArrayList<>());

    if (snapshots.stream().noneMatch(s -> s.getId().equals(snapshot.getId()))) {
      snapshots.add(snapshot);
    }

    if (snapshot.getChildSnapshotWrappers() != null) {
      for (ProfileSnapshotWrapper child : snapshot.getChildSnapshotWrappers()) {
        addSnapshotsToMap(child, profileTypeToSnapshots);
      }
    }
  }

  private List<ProfileAssociation> formAddedRelations(ProfileSnapshotWrapper rootSnapshot, ProfileType rootContentType) {
    List<ProfileAssociation> associations = new ArrayList<>();
    if (rootSnapshot.getChildSnapshotWrappers() != null) {
      for (ProfileSnapshotWrapper childSnapshot : rootSnapshot.getChildSnapshotWrappers()) {
        if (rootContentType == ACTION_PROFILE || childSnapshot.getContentType() != MAPPING_PROFILE) {
          ProfileAssociation association = createAssociation(rootSnapshot, childSnapshot);
          associations.add(association);
          associations.addAll(formAddedRelations(childSnapshot, rootContentType));
        }
      }
    }
    return associations;
  }

  private ProfileAssociation createAssociation(ProfileSnapshotWrapper parent, ProfileSnapshotWrapper child) {
    ProfileAssociation association = new ProfileAssociation();
    association.setMasterProfileId(parent.getProfileId());
    association.setDetailProfileId(child.getProfileId());
    association.setOrder(child.getOrder());
    association.setMasterProfileType(parent.getContentType());
    association.setDetailProfileType(child.getContentType());
    association.setReactTo(child.getReactTo());
    return association;
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

  private <T, S, D> Future<T> saveProfile(OkapiConnectionParams okapiParams, D profileUpdateDto,
                                          ProfileService<T, S, D> profileService, String profileId) {
    return profileService.getProfileById(profileId, false, okapiParams.getTenantId())
      .compose(optionalProfile -> {
        if (optionalProfile.isPresent()) {
          return profileService.updateProfile(profileUpdateDto, okapiParams);
        }
        return profileService.saveProfile(profileUpdateDto, okapiParams);
      });
  }
}
