package org.folio.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.impl.util.OkapiConnectionParams;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileCollection;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;

@Component
public class MappingProfileServiceImpl extends AbstractProfileService<MappingProfile, MappingProfileCollection, MappingProfileUpdateDto> {
  private static final Logger LOGGER = LogManager.getLogger();

  private static final String[] DEFAULT_MAPPING_PROFILES = {
    "d0ebbc2e-2f0f-11eb-adc1-0242ac120002", //OCLC_CREATE_MAPPING_PROFILE_ID
    "862000b9-84ea-4cae-a223-5fc0552f2b42", //OCLC_UPDATE_MAPPING_PROFILE_ID
    "f90864ef-8030-480f-a43f-8cdd21233252", //OCLC_UPDATE_MARC_BIB_MAPPING_PROFILE_ID
    "991c0300-44a6-47e3-8ea2-b01bb56a38cc", //DEFAULT_CREATE_DERIVE_INSTANCE_MAPPING_PROFILE_ID
    "e0fbaad5-10c0-40d5-9228-498b351dbbaa", //DEFAULT_CREATE_DERIVE_HOLDINGS_MAPPING_PROFILE_ID
    "13cf7adf-c7a7-4c2e-838f-14d0ac36ec0a", //DEFAULT_CREATE_HOLDINGS_MAPPING_PROFILE_ID
    "6a0ec1de-68eb-4833-bdbf-0741db25c314", //DEFAULT_CREATE_AUTHORITIES_MAPPING_PROFILE_ID
    "6a0ec1de-68eb-4833-bdbf-0741db85c314", //DEFAULT_CREATE_AUTHORITY_MAPPING_PROFILE_ID
    "39b265e1-c963-4e5f-859d-6e8c327a265c", //DEFAULT_QM_MARC_BIB_UPDATE_MAPPING_PROFILE_ID
    "b8a9ca7d-4a33-44d3-86e1-f7c6cb7b265f", //DEFAULT_QM_HOLDINGS_UPDATE_MAPPING_PROFILE_ID
    "041f8ff9-9d17-4436-b305-1033e0879501" //DEFAULT_QM_AUTHORITY_UPDATE_MAPPING_PROFILE_ID
  };

  @Override
  public Future<MappingProfile> saveProfile(MappingProfileUpdateDto profileDto, OkapiConnectionParams params) {
    return deleteExistingActionToMappingAssociations(profileDto, params.getTenantId())
      .compose(deleteAr -> super.saveProfile(profileDto, params));
  }

  @Override
  public Future<MappingProfile> updateProfile(MappingProfileUpdateDto profileDto, OkapiConnectionParams params) {
    return deleteExistingActionToMappingAssociations(profileDto, params.getTenantId())
      .compose(deleteAr -> super.updateProfile(profileDto, params));
  }

  @Override
  MappingProfile setProfileId(MappingProfile profile) {
    String profileId = profile.getId();
    return profile.withId(StringUtils.isBlank(profileId) ?
      UUID.randomUUID().toString() : profileId);
  }

  @Override
  Future<MappingProfile> setUserInfoForProfile(MappingProfile profile, OkapiConnectionParams params) {
    return lookupUser(profile.getMetadata().getUpdatedByUserId(), params)
      .compose(userInfo -> Future.succeededFuture(profile.withUserInfo(userInfo)));
  }

  @Override
  public String getProfileName(MappingProfile profile) {
    return profile.getName();
  }

  @Override
  protected String getProfileId(MappingProfile profile) {
    return profile.getId();
  }

  @Override
  protected MappingProfileUpdateDto prepareAssociations(MappingProfileUpdateDto profileDto) {
    profileDto.getAddedRelations().forEach(association -> {
      if (StringUtils.isEmpty(association.getMasterProfileId())) {
        association.setMasterProfileId(profileDto.getProfile().getId());
      }
      if (StringUtils.isEmpty(association.getDetailProfileId())) {
        association.setDetailProfileId(profileDto.getProfile().getId());
      }
    });
    return profileDto;
  }

  @Override
  protected ProfileType getProfileContentType() {
    return ProfileType.MAPPING_PROFILE;
  }

  @Override
  protected List<ProfileSnapshotWrapper> getChildProfiles(MappingProfile profile) {
    return profile.getChildProfiles();
  }

  @Override
  protected void setChildProfiles(MappingProfile profile, List<ProfileSnapshotWrapper> childProfiles) {
    profile.setChildProfiles(childProfiles);
  }

  @Override
  protected List<ProfileSnapshotWrapper> getParentProfiles(MappingProfile profile) {
    return profile.getParentProfiles();
  }

  @Override
  protected void setParentProfiles(MappingProfile profile, List<ProfileSnapshotWrapper> parentProfiles) {
    profile.setParentProfiles(parentProfiles);
  }

  @Override
  protected List<MappingProfile> getProfilesList(MappingProfileCollection profilesCollection) {
    return profilesCollection.getMappingProfiles();
  }

  @Override
  protected List<ProfileAssociation> getProfileAssociationToAdd(MappingProfileUpdateDto dto) {
    return dto.getAddedRelations();
  }

  @Override
  protected List<ProfileAssociation> getProfileAssociationToDelete(MappingProfileUpdateDto dto) {
    return dto.getDeletedRelations();
  }

  @Override
  protected MappingProfile getProfile(MappingProfileUpdateDto dto) {
    return dto.getProfile();
  }

  @Override
  protected String[] getDefaultProfiles() {
    return DEFAULT_MAPPING_PROFILES;
  }

  private Future<Boolean> deleteExistingActionToMappingAssociations(MappingProfileUpdateDto profileDto, String tenantId) {
    List<Future<Boolean>> futures = profileDto.getAddedRelations().stream()
      .filter(profileAssociation -> profileAssociation.getMasterProfileType().equals(ACTION_PROFILE))
      .map(ProfileAssociation::getMasterWrapperId)
      .map(actionProfileId -> profileAssociationService.deleteByMasterWrapperId(actionProfileId, ProfileType.ACTION_PROFILE,
        ProfileType.MAPPING_PROFILE, tenantId))
      .collect(Collectors.toList());

    return GenericCompositeFuture.all(futures)
      .onFailure(th -> LOGGER.warn( "deleteExistingActionToMappingAssociations:: Failed to delete existing action-to-mapping associations", th))
      .map(true);
  }

}
