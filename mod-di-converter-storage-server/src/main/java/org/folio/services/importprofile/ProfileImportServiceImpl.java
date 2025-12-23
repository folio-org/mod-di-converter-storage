package org.folio.services.importprofile;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.folio.services.snapshot.ProfileSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.BadRequestException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.function.BiFunction;

import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MAPPING_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;
import static org.folio.services.snapshot.ProfileSnapshotServiceImpl.convertProfileSnapshotWrapperContent;

@Service
public class ProfileImportServiceImpl implements ProfileImportService {
  private static final Logger LOGGER = LogManager.getLogger();
  private static final List<ProfileType> IMPORT_PROFILES_ORDER = List.of(MAPPING_PROFILE, ACTION_PROFILE, MATCH_PROFILE, JOB_PROFILE);
  private static final String PROFILE_SNAPSHOT_INVALID_TYPE = "Cannot import profile snapshot of %s required type is %s";
  private final EnumMap<ProfileType, BiFunction<ProfileSnapshotWrapper, OkapiConnectionParams, Future<Object>>> profileSaveHandlers;
  private final ProfileSnapshotService profileSnapshotService;

  public ProfileImportServiceImpl(@Autowired ProfileSnapshotService profileSnapshotService,
                                  @Autowired ProfileService<JobProfile, JobProfileCollection, JobProfileUpdateDto> jobProfileService,
                                  @Autowired ProfileService<MatchProfile, MatchProfileCollection, MatchProfileUpdateDto> matchProfileService,
                                  @Autowired ProfileService<ActionProfile, ActionProfileCollection, ActionProfileUpdateDto> actionProfileService,
                                  @Autowired ProfileService<MappingProfile, MappingProfileCollection, MappingProfileUpdateDto> mappingProfileService) {
    this.profileSnapshotService = profileSnapshotService;

    profileSaveHandlers = new EnumMap<>(ProfileType.class);

    // Initialization of functions that create profiles depending on profile type
    profileSaveHandlers.put(MAPPING_PROFILE, (snapshot, okapiParams) -> {
      MappingProfile mappingProfile = ((MappingProfile) snapshot.getContent());
      return saveProfile(okapiParams, new MappingProfileUpdateDto()
          .withProfile(mappingProfile), mappingProfileService, mappingProfile.getId(), MAPPING_PROFILE).map(p -> p);
    });

    profileSaveHandlers.put(ACTION_PROFILE, (snapshot, okapiParams) -> {
      ActionProfile actionProfile = ((ActionProfile) snapshot.getContent());
      return saveProfile(okapiParams, new ActionProfileUpdateDto()
          .withProfile(actionProfile)
          .withAddedRelations(formAddedRelations(snapshot, ACTION_PROFILE)), actionProfileService, actionProfile.getId(), ACTION_PROFILE).map(p -> p);
    });

    profileSaveHandlers.put(MATCH_PROFILE, (snapshot, okapiParams) -> {
      MatchProfile matchProfile = ((MatchProfile) snapshot.getContent());
      return saveProfile(okapiParams, new MatchProfileUpdateDto()
        .withProfile(matchProfile), matchProfileService, matchProfile.getId(), MATCH_PROFILE).map(p -> p);
    });

    profileSaveHandlers.put(JOB_PROFILE, (snapshot, okapiParams) -> {
      JobProfile jobProfile = ((JobProfile) snapshot.getContent());
      return saveProfile(okapiParams, new JobProfileUpdateDto()
        .withProfile(jobProfile)
        .withAddedRelations(formAddedRelations(snapshot, JOB_PROFILE)), jobProfileService, jobProfile.getId(), JOB_PROFILE).map(p -> p);
    });
  }

  @Override
  public Future<ProfileSnapshotWrapper> importProfile(ProfileSnapshotWrapper profileSnapshot, String tenantId, OkapiConnectionParams okapiParams) {
    LOGGER.info("importProfile:: Started import for Job Profile with id {}", profileSnapshot.getProfileId());
    if (profileSnapshot.getContentType() != JOB_PROFILE) {
      String errorMessage = String.format(PROFILE_SNAPSHOT_INVALID_TYPE, profileSnapshot.getContentType(), JOB_PROFILE);
      LOGGER.warn("importProfile:: {}", errorMessage);
      return Future.failedFuture(new BadRequestException(errorMessage));
    }

    try {
      convertProfileSnapshotWrapperContent(profileSnapshot);
    } catch (Exception e) {
      String errorMessage = String.format("Cannot map profile content, error: %s", e.getMessage());
      LOGGER.warn("importProfile:: {}", errorMessage);
      return Future.failedFuture(new BadRequestException(errorMessage));
    }

    var profileTypeToSnapshots = getProfileTypeToSnapshot(profileSnapshot);
    var profileTypesInSaveOrder = orderProfileTypesBeforeSaving(profileTypeToSnapshots);

    Future<List<Object>> saveProfilesFuture = Future.succeededFuture();
    for (ProfileType profileType : profileTypesInSaveOrder) {
      saveProfilesFuture = saveProfilesFuture.compose(v -> processProfilesSaving(profileTypeToSnapshots.get(profileType), profileType, okapiParams));
    }
    return saveProfilesFuture
      .compose(v -> profileSnapshotService.constructSnapshot(profileSnapshot.getProfileId(), JOB_PROFILE, profileSnapshot.getProfileId(), okapiParams.getTenantId()));
  }

  private static List<ProfileType> orderProfileTypesBeforeSaving(EnumMap<ProfileType, List<ProfileSnapshotWrapper>> profileTypeToSnapshots) {
    return profileTypeToSnapshots.keySet().stream()
      .sorted(Comparator.comparingInt(IMPORT_PROFILES_ORDER::indexOf))
      .toList();
  }

  private Future<List<Object>> processProfilesSaving(List<ProfileSnapshotWrapper> profileSnapshots, ProfileType profileType, OkapiConnectionParams okapiParams) {
    LOGGER.debug("processProfilesSaving:: Saving {}, profiles: {}", profileType, JsonArray.of(profileSnapshots));
    List<Future<Object>> futures = new ArrayList<>();
    var saveProfileFunction = profileSaveHandlers.get(profileType);

    profileSnapshots.forEach(profileSnapshot -> futures.add(saveProfileFunction.apply(profileSnapshot, okapiParams)));

    return Future.all(futures).map(CompositeFuture::list);
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

  private <T, S, D> Future<T> saveProfile(OkapiConnectionParams okapiParams, D profileUpdateDto,
                                          ProfileService<T, S, D> profileService, String profileId, ProfileType profileType) {
    return profileService.getProfileById(profileId, false, okapiParams.getTenantId())
      .compose(optionalProfile -> {
        if (optionalProfile.isPresent()) {
          LOGGER.debug("saveProfile:: Overlay profile with id {} during import", profileId);
          return overlayRelationsIfNeeded(profileUpdateDto, profileId, profileType, profileService, okapiParams)
                 .compose(p -> profileService.updateProfile(profileUpdateDto, okapiParams));
        }
        return profileService.saveProfile(profileUpdateDto, okapiParams);
      });
  }

  private <T, S, D> Future<D> overlayRelationsIfNeeded(D profileUpdateDto, String profileId, ProfileType profileType,
                                                       ProfileService<T, S, D> profileService, OkapiConnectionParams okapiParams) {
    if (profileType != JOB_PROFILE && profileType != ACTION_PROFILE) {
      return Future.succeededFuture(profileUpdateDto);
    }
    return profileSnapshotService.getSnapshotAssociations(profileId, profileType, profileType == JOB_PROFILE ? profileId : null, okapiParams.getTenantId())
      .map(associationsToDelete -> {
        ProfileAssociation rootAssociation = associationsToDelete.stream().filter(profileAssociation -> profileAssociation.getMasterProfileType() == null)
          .findFirst().orElseThrow();

        associationsToDelete.remove(rootAssociation);
        if (profileType == JOB_PROFILE) {
          associationsToDelete = associationsToDelete.stream().filter(association -> association.getDetailProfileType() != MAPPING_PROFILE).toList();
        }

        profileService.getAddedRelations(profileUpdateDto).stream()
          .filter(a -> a.getMasterProfileType().equals(profileType))
          .forEach(a -> a.setMasterWrapperId(rootAssociation.getDetailWrapperId()));

        return profileService.withDeletedRelations(profileUpdateDto, associationsToDelete);
    });
  }
}
