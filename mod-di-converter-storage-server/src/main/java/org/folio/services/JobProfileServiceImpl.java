package org.folio.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.impl.util.OkapiConnectionParams;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileCollection;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.services.snapshot.ProfileSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.util.List;
import java.util.UUID;

import static java.lang.String.format;
import static org.folio.rest.jaxrs.model.ProfileAssociation.MasterProfileType.MATCH_PROFILE;

@Component
public class JobProfileServiceImpl extends AbstractProfileService<JobProfile, JobProfileCollection, JobProfileUpdateDto> {
  private static final Logger logger = LogManager.getLogger();

  @Autowired
  private ProfileSnapshotService profileSnapshotService;
  @Override
  JobProfile setProfileId(JobProfile profile) {
    String profileId = profile.getId();
    return profile.withId(StringUtils.isBlank(profileId) ?
      UUID.randomUUID().toString() : profileId);
  }

  @Override
  Future<JobProfile> setUserInfoForProfile(JobProfileUpdateDto profile, OkapiConnectionParams params) {
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
  protected ProfileSnapshotWrapper.ContentType getProfileContentType() {
    return ProfileSnapshotWrapper.ContentType.JOB_PROFILE;
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

  @Override
  public Future<JobProfile> updateProfile(JobProfileUpdateDto profile, OkapiConnectionParams params) {
    return isProfileExistByProfileId(getProfile(profile), params.getTenantId())
      .compose(isExist -> isExist ?
        profileSnapshotService.constructSnapshot(getProfileId(getProfile(profile)), ProfileSnapshotWrapper.ContentType.JOB_PROFILE, getProfileId(getProfile(profile)), params.getTenantId()) :
        Future.failedFuture(new NotFoundException(format("updateProfile:: %s with id '%s' was not found", getProfileContentType().value(), getProfileId(getProfile(profile))))))
      .map(profileSnapshotWrappers -> getProfileAssociation(profileSnapshotWrappers.getChildSnapshotWrappers(), getProfileId(getProfile(profile)), getProfileContentType().value()))
      .map(profileAssociations -> {
        profileAssociations.removeAll(getProfileAssociationToDelete(profile));
        profileAssociations.addAll(getProfileAssociationToAdd(profile));
        return profileAssociations;
      })
      .compose(this::validateProfileAddedRelations)
      .compose(associations -> setUserInfoForProfile(profile, params))
      .compose(profileWithInfo -> profileDao.updateProfile(profileWithInfo, params.getTenantId()))
      .map(prepareAssociations((profile)))
      .compose(ar -> deleteRelatedAssociations(getProfileAssociationToDelete(profile), params.getTenantId()))
      .compose(ar -> saveRelatedAssociations(getProfileAssociationToAdd(profile), params.getTenantId()))
      .map(getProfile(profile));
  }
}
