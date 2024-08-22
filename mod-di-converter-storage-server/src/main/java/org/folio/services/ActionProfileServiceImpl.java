package org.folio.services;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.jackson.DatabindCodec;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.impl.util.OkapiConnectionParams;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileCollection;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileCollection;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.OperationType;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class ActionProfileServiceImpl extends AbstractProfileService<ActionProfile, ActionProfileCollection, ActionProfileUpdateDto> {
  private static final Logger LOGGER = LogManager.getLogger();
  private static final String INVALID_ACTION_PROFILE_ACTION_TYPE = "Can't create ActionProfile for MARC Bib record type with Create action";
  private static final String INVALID_RECORD_TYPE_LINKED_MAPPING_PROFILE_TO_ACTION_PROFILE = "Mapping profile '%s' can not be linked to this Action profile. ExistingRecordType and FolioRecord types are different";
  private static final String INVALID_ACTION_PROFILE_NEW_RECORD_TYPE_LINKED_TO_MAPPING_PROFILE = "Can not update ActionProfile recordType and linked MappingProfile recordType are different";
  private static final List<String> DEFAULT_ACTION_PROFILES = Arrays.asList(
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
  );

  @Autowired
  private ProfileService<MappingProfile, MappingProfileCollection, MappingProfileUpdateDto> mappingProfileService;

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
    profile.setMetadata(getMetadata(params.getHeaders()));
    return lookupUser(profile.getMetadata().getUpdatedByUserId(), params)
      .compose(userInfo -> Future.succeededFuture(profile.withUserInfo(userInfo)));
  }

  @Override
  public String getProfileName(ActionProfile profile) {
    return profile.getName();
  }

  @Override
  public List<ProfileAssociation> getAddedRelations(ActionProfileUpdateDto profileUpdateDto) {
    return profileUpdateDto.getAddedRelations();
  }

  @Override
  public ActionProfileUpdateDto withDeletedRelations(ActionProfileUpdateDto profileUpdateDto, List<ProfileAssociation> profileAssociations) {
    return profileUpdateDto.withDeletedRelations(profileAssociations);
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
  protected List<String> getDefaultProfiles() {
    return DEFAULT_ACTION_PROFILES;
  }

  @Override
  protected Future<Errors> validateProfile(OperationType operationType, ActionProfileUpdateDto profileDto, String tenantId) {
    return composeFutureErrors(
      validateActionProfile(operationType, profileDto, tenantId),
      validateActionProfileAddedRelationsFolioRecord(profileDto, tenantId),
      operationType == OperationType.UPDATE ? validateActionProfileChildProfilesFolioRecord(profileDto, tenantId) : Future.succeededFuture(new Errors())
    );
  }

  private void setDefaults(ActionProfile profile) {
    var bibInstanceProfile = profile.getFolioRecord().equals(ActionProfile.FolioRecord.INSTANCE)
      || profile.getFolioRecord().equals(ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC);
    profile.setRemove9Subfields(bibInstanceProfile);
  }

  private Future<Errors> validateActionProfile(OperationType operationType, ActionProfileUpdateDto actionProfileUpdateDto, String tenantId) {
    ActionProfile actionProfile = actionProfileUpdateDto.getProfile();
    return super.validateProfile(operationType, actionProfileUpdateDto, tenantId)
      .map(errors -> {
        if (ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC == actionProfile.getFolioRecord() && ActionProfile.Action.CREATE == actionProfile.getAction()) {
          LOGGER.warn("validateActionProfile:: {}", INVALID_ACTION_PROFILE_ACTION_TYPE);
          errors.withTotalRecords(errors.getTotalRecords() + 1).getErrors().add(new Error().withMessage(INVALID_ACTION_PROFILE_ACTION_TYPE));
        }
        return errors;
      });
  }

  private Future<Errors> validateActionProfileAddedRelationsFolioRecord(ActionProfileUpdateDto actionProfileUpdateDto, String tenantId) {
    if (CollectionUtils.isEmpty(actionProfileUpdateDto.getAddedRelations())) {
      return Future.succeededFuture(new Errors().withTotalRecords(0));
    }

    var errors = new LinkedList<Error>();
    Promise<Errors> promise = Promise.promise();

    var futures = actionProfileUpdateDto
      .getAddedRelations()
      .stream()
      .filter(profileAssociation -> profileAssociation.getDetailProfileType() == ProfileType.MAPPING_PROFILE)
      .map(profileAssociation -> mappingProfileService.getProfileById(profileAssociation.getDetailProfileId(), false, tenantId))
      .map(futureMappingProfile -> futureMappingProfile.onSuccess(optionalMappingProfile ->
        optionalMappingProfile.ifPresent(mappingProfile -> validateAssociations(actionProfileUpdateDto.getProfile(), mappingProfile, errors,
          String.format(INVALID_RECORD_TYPE_LINKED_MAPPING_PROFILE_TO_ACTION_PROFILE, mappingProfile.getName())))
      ))
      .toList();
    GenericCompositeFuture.all(futures)
      .onSuccess(handler -> promise.complete(new Errors().withErrors(errors)))
      .onFailure(promise::fail);

    return promise.future();
  }

  private Future<Errors> validateActionProfileChildProfilesFolioRecord(ActionProfileUpdateDto actionProfileUpdateDto, String tenantId) {
    String profileId = actionProfileUpdateDto.getProfile().getId();
    var errors = new LinkedList<Error>();
    var deletedRelations = actionProfileUpdateDto.getDeletedRelations();
    Promise<Errors> promise = Promise.promise();

    getProfileById(profileId, true, tenantId)
      .onSuccess(optionalActionProfile ->
        optionalActionProfile.ifPresentOrElse(actionProfile -> {
            var existMappingProfiles = CollectionUtils.isEmpty(deletedRelations) ? actionProfile.getChildProfiles() :
              actionProfile.getChildProfiles().stream()
                .filter(profileSnapshotWrapper -> profileSnapshotWrapper.getContentType() == ProfileType.MAPPING_PROFILE)
                .filter(profileSnapshotWrapper -> deletedRelations.stream()
                  .noneMatch(rel -> Objects.equals(rel.getDetailProfileId(), profileSnapshotWrapper.getProfileId()))
                ).toList();

            existMappingProfiles.forEach(mappingWrapper -> {
              var mappingProfile = DatabindCodec.mapper().convertValue(mappingWrapper.getContent(), MappingProfile.class);
              validateAssociations(actionProfileUpdateDto.getProfile(), mappingProfile, errors, INVALID_ACTION_PROFILE_NEW_RECORD_TYPE_LINKED_TO_MAPPING_PROFILE);
            });
            promise.complete(new Errors().withErrors(errors));
          }, () -> promise.fail(new NotFoundException(String.format("Action profile with id '%s' was not found", profileId)))
        )
      ).onFailure(promise::fail);

    return promise.future();
  }
}
