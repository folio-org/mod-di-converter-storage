package org.folio.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.impl.util.OkapiConnectionParams;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileCollection;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ActionProfileServiceImpl extends AbstractProfileService<ActionProfile, ActionProfileCollection, ActionProfileUpdateDto> {
  private static final String[] DEFAULT_ACTION_PROFILES = {
    "d0ebba8a-2f0f-11eb-adc1-0242ac120002", //OCLC_CREATE_INSTANCE_ACTION_PROFILE_ID
    "cddff0e1-233c-47ba-8be5-553c632709d9", //OCLC_UPDATE_INSTANCE_ACTION_PROFILE_ID
    "6aa8e98b-0d9f-41dd-b26f-15658d07eb52", //OCLC_UPDATE_MARC_BIB_ACTION_PROFILE_ID
    "f8e58651-f651-485d-aead-d2fa8700e2d1", //DEFAULT_CREATE_DERIVE_INSTANCE_ACTION_PROFILE_ID
    "f5feddba-f892-4fad-b702-e4e77f04f9a3", //DEFAULT_CREATE_DERIVE_HOLDINGS_ACTION_PROFILE_ID
    "8aa0b850-9182-4005-8435-340b704b2a19", //DEFAULT_CREATE_HOLDINGS_ACTION_PROFILE_ID
    "7915c72e-c6af-4962-969d-403c7238b051", //DEFAULT_CREATE_AUTHORITIES_ACTION_PROFILE_ID
    "7915c72e-c7af-4962-969d-403c7238b051", //DEFAULT_CREATE_AUTHORITIES_ACTION_PROFILE_ID
    "fabd9a3e-33c3-49b7-864d-c5af830d9990", //DEFAULT_DELETE_MARC_AUTHORITY_ACTION_PROFILE_ID
    "c2e2d482-9486-476e-a28c-8f1e303cbe1a", //DEFAULT_QM_MARC_BIB_UPDATE_ACTION_PROFILE_ID
    "7e24a466-349b-451d-a18e-38fb21d71b38", //DEFAULT_QM_HOLDINGS_UPDATE_ACTION_PROFILE_ID
    "f0f788c8-2e65-4e3a-9247-e9444eeb7d70" //DEFAULT_QM_AUTHORITY_UPDATE_ACTION_PROFILE_ID
  };

  @Override
  public Future<ActionProfile> saveProfile(ActionProfileUpdateDto profileDto, OkapiConnectionParams params) {
    setDefaults(profileDto.getProfile());
    return super.saveProfile(profileDto, params);
  }

  @Override
  public Future<ActionProfile> updateProfile(ActionProfileUpdateDto profileDto, OkapiConnectionParams params) {
    setDefaults(profileDto.getProfile());
    return super.updateProfile(profileDto, params);
  }
  @Override
  ActionProfile setProfileId(ActionProfile profile) {
    String profileId = profile.getId();
    return profile.withId(StringUtils.isBlank(profileId) ?
      UUID.randomUUID().toString() : profileId);
  }

  @Override
  Future<ActionProfile> setUserInfoForProfile(ActionProfile profile, OkapiConnectionParams params) {
    return lookupUser(profile.getMetadata().getUpdatedByUserId(), params)
      .compose(userInfo -> Future.succeededFuture(profile.withUserInfo(userInfo)));
  }

  @Override
  public String getProfileName(ActionProfile profile) {
    return profile.getName();
  }

  @Override
  protected String getProfileId(ActionProfile profile) {
    return profile.getId();
  }

  @Override
  protected ActionProfileUpdateDto prepareAssociations(ActionProfileUpdateDto profileDto) {
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
    return ProfileType.ACTION_PROFILE;
  }

  @Override
  protected List<ProfileSnapshotWrapper> getChildProfiles(ActionProfile profile) {
    return profile.getChildProfiles();
  }

  @Override
  protected void setChildProfiles(ActionProfile profile, List<ProfileSnapshotWrapper> childProfiles) {
    profile.setChildProfiles(childProfiles);
  }

  @Override
  protected List<ProfileSnapshotWrapper> getParentProfiles(ActionProfile profile) {
    return profile.getParentProfiles();
  }

  @Override
  protected void setParentProfiles(ActionProfile profile, List<ProfileSnapshotWrapper> parentProfiles) {
    profile.setParentProfiles(parentProfiles);
  }

  @Override
  protected List<ActionProfile> getProfilesList(ActionProfileCollection profilesCollection) {
    return profilesCollection.getActionProfiles();
  }

  @Override
  protected List<ProfileAssociation> getProfileAssociationToAdd(ActionProfileUpdateDto dto) {
    return dto.getAddedRelations();
  }

  @Override
  protected List<ProfileAssociation> getProfileAssociationToDelete(ActionProfileUpdateDto dto) {
    return dto.getDeletedRelations();
  }

  @Override
  protected ActionProfile getProfile(ActionProfileUpdateDto dto) {
    return dto.getProfile();
  }

  @Override
  protected String[] getDefaultProfiles() {
    return DEFAULT_ACTION_PROFILES;
  }

  private void setDefaults(ActionProfile profile) {
    var bibInstanceProfile = profile.getFolioRecord().equals(ActionProfile.FolioRecord.INSTANCE)
      || profile.getFolioRecord().equals(ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC);
    profile.setRemove9Subfields(bibInstanceProfile);
  }

}
