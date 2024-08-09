package org.folio.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.impl.util.OkapiConnectionParams;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileCollection;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;

@Component
public class JobProfileServiceImpl extends AbstractProfileService<JobProfile, JobProfileCollection, JobProfileUpdateDto> {

  @Override
  JobProfile setProfileId(JobProfile profile) {
    String profileId = profile.getId();
    return profile.withId(StringUtils.isBlank(profileId) ?
      UUID.randomUUID().toString() : profileId);
  }

  @Override
  Future<JobProfile> setUserInfoForProfile(JobProfileUpdateDto profile, OkapiConnectionParams params) {
    profile.getProfile().setMetadata(getMetadata(params.getHeaders()));
    return lookupUser(profile.getProfile().getMetadata().getUpdatedByUserId(), params)
      .compose(userInfo -> Future.succeededFuture(profile.getProfile().withUserInfo(userInfo)));
  }

  @Override
  public String getProfileName(JobProfile profile) {
    return profile.getName();
  }

  @Override
  protected String getProfileId(JobProfile profile) {
    return profile.getId();
  }

  @Override
  protected JobProfileUpdateDto prepareAssociations(JobProfileUpdateDto profileDto) {
    profileDto.getAddedRelations().forEach(association -> {
      if (StringUtils.isEmpty(association.getMasterProfileId())) {
        association.setMasterProfileId(profileDto.getProfile().getId());
      }
      if (StringUtils.isEmpty(association.getDetailProfileId())) {
        association.setDetailProfileId(profileDto.getProfile().getId());
      }
      if (association.getMasterProfileType() == MATCH_PROFILE) {
        association.setJobProfileId(profileDto.getProfile().getId());
      }
    });
    profileDto.getDeletedRelations().forEach(association -> {
      if (association.getMasterProfileType() == MATCH_PROFILE) {
        association.setJobProfileId(profileDto.getProfile().getId());
      }
    });
    return profileDto;
  }

  @Override
  protected ProfileType getProfileContentType() {
    return ProfileType.JOB_PROFILE;
  }

  @Override
  protected List<ProfileSnapshotWrapper> getChildProfiles(JobProfile profile) {
    return profile.getChildProfiles();
  }

  @Override
  protected void setChildProfiles(JobProfile profile, List<ProfileSnapshotWrapper> childProfiles) {
    profile.setChildProfiles(childProfiles);
  }

  @Override
  protected List<ProfileSnapshotWrapper> getParentProfiles(JobProfile profile) {
    return profile.getParentProfiles();
  }

  @Override
  protected void setParentProfiles(JobProfile profile, List<ProfileSnapshotWrapper> parentProfiles) {
    profile.setParentProfiles(parentProfiles);
  }

  @Override
  protected List<JobProfile> getProfilesList(JobProfileCollection profilesCollection) {
    return profilesCollection.getJobProfiles();
  }

  @Override
  protected List<ProfileAssociation> getProfileAssociationToAdd(JobProfileUpdateDto dto) {
    return dto.getAddedRelations();
  }

  @Override
  protected List<ProfileAssociation> getProfileAssociationToDelete(JobProfileUpdateDto dto) {
    return dto.getDeletedRelations();
  }

  @Override
  protected JobProfile getProfile(JobProfileUpdateDto dto) {
    return dto.getProfile();
  }

}
