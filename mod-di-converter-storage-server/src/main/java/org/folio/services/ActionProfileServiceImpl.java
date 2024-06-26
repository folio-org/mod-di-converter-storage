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

  @Override
  public Future<ActionProfile> saveProfile(ActionProfileUpdateDto profile, OkapiConnectionParams params) {
    setDefaults(profile.getProfile());
    return super.saveProfile(profile, params);
  }

  @Override
  public Future<ActionProfile> updateProfile(ActionProfileUpdateDto profile, OkapiConnectionParams params) {
    setDefaults(profile.getProfile());
    return super.updateProfile(profile, params);
  }
  @Override
  ActionProfile setProfileId(ActionProfile profile) {
    String profileId = profile.getId();
    return profile.withId(StringUtils.isBlank(profileId) ?
      UUID.randomUUID().toString() : profileId);
  }

  @Override
  Future<ActionProfile> setUserInfoForProfile(ActionProfileUpdateDto profile, OkapiConnectionParams params) {
    return lookupUser(profile.getProfile().getMetadata().getUpdatedByUserId(), params)
      .compose(userInfo -> Future.succeededFuture(profile.getProfile().withUserInfo(userInfo)));
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

  private void setDefaults(ActionProfile profile) {
    var bibInstanceProfile = profile.getFolioRecord().equals(ActionProfile.FolioRecord.INSTANCE)
      || profile.getFolioRecord().equals(ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC);
    profile.setRemove9Subfields(bibInstanceProfile);
  }

}
