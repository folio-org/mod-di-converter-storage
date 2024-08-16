package org.folio.services.importprofile;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
  private final EnumMap<ProfileType, BiFunction<ProfileSnapshotWrapper, OkapiConnectionParams, Future<Object>>> profileTypeToSaveFunction;
  private final ProfileSnapshotService profileSnapshotService;

  public ProfileImportServiceImpl(@Autowired ProfileSnapshotService profileSnapshotService,
                                  @Autowired ProfileService<JobProfile, JobProfileCollection, JobProfileUpdateDto> jobProfileService,
                                  @Autowired ProfileService<MatchProfile, MatchProfileCollection, MatchProfileUpdateDto> matchProfileService,
                                  @Autowired ProfileService<ActionProfile, ActionProfileCollection, ActionProfileUpdateDto> actionProfileService,
                                  @Autowired ProfileService<MappingProfile, MappingProfileCollection, MappingProfileUpdateDto> mappingProfileService) {
    this.profileSnapshotService = profileSnapshotService;

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
  public Future<ProfileSnapshotWrapper> importProfile(ProfileSnapshotWrapper profileSnapshot, String tenantId, OkapiConnectionParams okapiParams) {
    if (profileSnapshot.getContentType() != JOB_PROFILE) {
      String errorMessage = String.format(PROFILE_SNAPSHOT_INVALID_TYPE, profileSnapshot.getContentType(), JOB_PROFILE);
      LOGGER.warn("importProfile:: {}", errorMessage);
      return Future.failedFuture(new BadRequestException(errorMessage));
    }
    LOGGER.info("importProfile:: Started import for Job Profile with id {}", profileSnapshot.getProfileId());

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
      .compose(v -> profileSnapshotService.constructSnapshot(profileSnapshot.getProfileId(), JOB_PROFILE, profileSnapshot.getProfileId(), okapiParams.getTenantId()));
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

  private <T, S, D> Future<T> saveProfile(OkapiConnectionParams okapiParams, D profileUpdateDto,
                                          ProfileService<T, S, D> profileService, String profileId) {
    return profileService.getProfileById(profileId, false, okapiParams.getTenantId())
      .compose(optionalProfile -> {
        if (optionalProfile.isPresent()) {
          LOGGER.debug("saveProfile:: Overlay profile with id {} during import", profileId);
          return profileService.updateProfile(profileUpdateDto, okapiParams);
        }
        return profileService.saveProfile(profileUpdateDto, okapiParams);
      });
  }
}
