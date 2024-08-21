package org.folio.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.impl.util.OkapiConnectionParams;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.MatchProfileCollection;
import org.folio.rest.jaxrs.model.MatchProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class MatchProfileServiceImpl extends AbstractProfileService<MatchProfile, MatchProfileCollection, MatchProfileUpdateDto> {
  private static final List<String> DEFAULT_MATCH_PROFILES = Arrays.asList(
    "d27d71ce-8a1e-44c6-acea-96961b5592c6", //OCLC_MARC_MARC_MATCH_PROFILE_ID
    "31dbb554-0826-48ec-a0a4-3c55293d4dee", //OCLC_INSTANCE_UUID_MATCH_PROFILE_ID
    "4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6",  //DEFAULT_DELETE_MARC_AUTHORITY_MATCH_PROFILE_ID
    "91cec42a-260d-4a8c-a9fb-90d9435ca2f4", //DEFAULT_QM_MARC_BIB_UPDATE_MATCH_PROFILE_ID
    "2a599369-817f-4fe8-bae2-f3e3987990fe", //DEFAULT_QM_HOLDINGS_UPDATE_MATCH_PROFILE_ID
    "aff72eae-847c-4a97-b7b9-c1ddb8cdcbbf"  //DEFAULT_QM_AUTHORITY_UPDATE_MATCH_PROFILE_ID
    );

  @Override
  MatchProfile setProfileId(MatchProfile profile) {
    String profileId = profile.getId();
    return profile.withId(StringUtils.isBlank(profileId) ?
      UUID.randomUUID().toString() : profileId);
  }

  @Override
  Future<MatchProfile> setUserInfoForProfile(MatchProfile profile, OkapiConnectionParams params) {
    profile.setMetadata(getMetadata(params.getHeaders()));
    return lookupUser(profile.getMetadata().getUpdatedByUserId(), params)
      .compose(userInfo -> Future.succeededFuture(profile.withUserInfo(userInfo)));
  }

  @Override
  public String getProfileName(MatchProfile profile) {
    return profile.getName();
  }

  @Override
  public MatchProfileUpdateDto withDeletedRelations(MatchProfileUpdateDto profileUpdateDto, List<ProfileAssociation> profileAssociations) {
    return profileUpdateDto.withDeletedRelations(profileAssociations);
  }

  @Override
  protected String getProfileId(MatchProfile profile) {
    return profile.getId();
  }

  @Override
  protected MatchProfileUpdateDto prepareAssociations(MatchProfileUpdateDto profileDto) {
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
    return ProfileType.MATCH_PROFILE;
  }

  @Override
  protected List<ProfileSnapshotWrapper> getChildProfiles(MatchProfile profile) {
    return profile.getChildProfiles();
  }

  @Override
  protected void setChildProfiles(MatchProfile profile, List<ProfileSnapshotWrapper> childProfiles) {
    profile.setChildProfiles(childProfiles);
  }

  @Override
  protected List<ProfileSnapshotWrapper> getParentProfiles(MatchProfile profile) {
    return profile.getParentProfiles();
  }

  @Override
  protected void setParentProfiles(MatchProfile profile, List<ProfileSnapshotWrapper> parentProfiles) {
    profile.setParentProfiles(parentProfiles);
  }

  @Override
  protected List<MatchProfile> getProfilesList(MatchProfileCollection profilesCollection) {
    return profilesCollection.getMatchProfiles();
  }

  @Override
  protected List<ProfileAssociation> getProfileAssociationToAdd(MatchProfileUpdateDto dto) {
    return dto.getAddedRelations();
  }

  @Override
  protected List<ProfileAssociation> getProfileAssociationToDelete(MatchProfileUpdateDto dto) {
    return dto.getDeletedRelations();
  }

  @Override
  protected MatchProfile getProfile(MatchProfileUpdateDto dto) {
    return dto.getProfile();
  }

  @Override
  protected List<String> getDefaultProfiles() {
    return DEFAULT_MATCH_PROFILES;
  }
}
