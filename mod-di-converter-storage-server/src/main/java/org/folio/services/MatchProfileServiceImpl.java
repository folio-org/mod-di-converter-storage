package org.folio.services;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.folio.dao.ProfileDao;
import org.folio.dao.association.ProfileWrapperDao;
import org.folio.rest.impl.util.OkapiConnectionParams;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.MatchExpression;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.MatchProfileCollection;
import org.folio.rest.jaxrs.model.MatchProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.Qualifier;
import org.folio.services.association.CommonProfileAssociationService;
import org.folio.services.association.ProfileAssociationService;
import org.folio.services.converter.ProfileAssociationConverter;
import org.springframework.stereotype.Service;

@Service
public class MatchProfileServiceImpl
  extends AbstractProfileService<MatchProfile, MatchProfileCollection, MatchProfileUpdateDto> {
  @SuppressWarnings("java:S6418") // Suppress warning about 'AUTH' detection meaning potentially hard-coded secret
  private static final String DEFAULT_DELETE_MARC_AUTHORITY_MATCH_PROFILE_ID = "4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6";
  private static final String BLANK_QUALIFIER_VALUE_ERROR_MESSAGE =
    "Match profile - Use a qualifier field cannot be saved with blank or whitespace only value.";
  private static final String MISSING_QUALIFIER_TYPE_ERROR_MESSAGE =
    "Match profile - Use a qualifier field cannot be saved without a qualifier type.";
  private static final List<String> DEFAULT_MATCH_PROFILES = Arrays.asList(
    "d27d71ce-8a1e-44c6-acea-96961b5592c6", //OCLC_MARC_MARC_MATCH_PROFILE_ID
    "31dbb554-0826-48ec-a0a4-3c55293d4dee"  //OCLC_INSTANCE_UUID_MATCH_PROFILE_ID
  );

  public MatchProfileServiceImpl(ProfileAssociationService profileAssociationService,
                                 CommonProfileAssociationService associationService,
                                 ProfileAssociationConverter associationConverter,
                                 ProfileDao<MatchProfile, MatchProfileCollection> profileDao,
                                 ProfileWrapperDao profileWrapperDao) {
    super(profileAssociationService, associationService, associationConverter, profileDao, profileWrapperDao,
      ProfileRelationsAccessor.of(MatchProfileUpdateDto::getAddedRelations, MatchProfileUpdateDto::getDeletedRelations,
        MatchProfileUpdateDto::withAddedRelations, MatchProfileUpdateDto::withDeletedRelations));
  }

  @Override
  public String getProfileName(MatchProfile profile) {
    return profile.getName();
  }

  @Override
  protected String getProfileId(MatchProfile profile) {
    return profile.getId();
  }

  @Override
  protected MatchProfileUpdateDto prepareAssociations(MatchProfileUpdateDto profileDto) {
    profileDto.getAddedRelations().forEach(association -> {
      if (isEmpty(association.getMasterProfileId())) {
        association.setMasterProfileId(profileDto.getProfile().getId());
      }
      if (isEmpty(association.getDetailProfileId())) {
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
  protected MatchProfile getProfile(MatchProfileUpdateDto dto) {
    return dto.getProfile();
  }

  @Override
  protected List<String> getDefaultProfiles() {
    return DEFAULT_MATCH_PROFILES;
  }

  @Override
  protected List<Error> getMissingRequiredProfileFieldErrors(MatchProfile profile) {
    List<Error> errors = new ArrayList<>();
    if (profile.getName() == null) {
      errors.add(new Error().withMessage("profile.name must not be null"));
    }
    if (profile.getIncomingRecordType() == null) {
      errors.add(new Error().withMessage("profile.incomingRecordType must not be null"));
    }
    if (profile.getExistingRecordType() == null) {
      errors.add(new Error().withMessage("profile.existingRecordType must not be null"));
    }
    List<Qualifier> qualifiers = getQualifiers(profile);
    if (qualifiers.stream().anyMatch(this::hasBlankQualifierValue)) {
      errors.add(new Error().withMessage(BLANK_QUALIFIER_VALUE_ERROR_MESSAGE));
    }
    if (qualifiers.stream().anyMatch(this::hasMissingQualifierType)) {
      errors.add(new Error().withMessage(MISSING_QUALIFIER_TYPE_ERROR_MESSAGE));
    }
    return errors;
  }

  @Override
  protected void normalizeProfile(MatchProfile profile) {
    getQualifiers(profile).stream()
      .filter(q -> q.getQualifierType() == null && isBlank(q.getQualifierValue()))
      .forEach(q -> q.setQualifierValue(null));
  }

  @Override
  protected boolean canDeleteProfile(String profileId) {
    return !DEFAULT_DELETE_MARC_AUTHORITY_MATCH_PROFILE_ID.equals(profileId) && super.canDeleteProfile(profileId);
  }

  @Override
  protected MatchProfile setProfileId(MatchProfile profile) {
    String profileId = profile.getId();
    return profile.withId(isBlank(profileId)
                          ? UUID.randomUUID().toString() : profileId);
  }

  @Override
  protected Future<MatchProfile> setUserInfoForProfile(MatchProfile profile, OkapiConnectionParams params) {
    profile.setMetadata(getMetadata(params.getHeaders()));
    return lookupUser(profile.getMetadata().getUpdatedByUserId(), params)
      .compose(userInfo -> Future.succeededFuture(profile.withUserInfo(userInfo)));
  }

  private List<Qualifier> getQualifiers(MatchProfile profile) {
    if (profile.getMatchDetails() == null) {
      return List.of();
    }
    return profile.getMatchDetails().stream()
      .filter(Objects::nonNull)
      .flatMap(detail -> Stream.of(detail.getIncomingMatchExpression(), detail.getExistingMatchExpression()))
      .filter(Objects::nonNull)
      .map(MatchExpression::getQualifier)
      .filter(Objects::nonNull)
      .toList();
  }

  private boolean hasBlankQualifierValue(Qualifier qualifier) {
    return qualifier.getQualifierType() != null && isBlank(qualifier.getQualifierValue());
  }

  private boolean hasMissingQualifierType(Qualifier qualifier) {
    return qualifier.getQualifierType() == null && isNotBlank(qualifier.getQualifierValue());
  }
}
