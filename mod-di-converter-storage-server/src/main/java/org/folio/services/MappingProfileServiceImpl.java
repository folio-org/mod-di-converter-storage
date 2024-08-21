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
import org.folio.rest.jaxrs.model.MappingRule;
import org.folio.rest.jaxrs.model.OperationType;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;

@Component
public class MappingProfileServiceImpl extends AbstractProfileService<MappingProfile, MappingProfileCollection, MappingProfileUpdateDto> {

  private static final Logger LOGGER = LogManager.getLogger();
  private static final String INVALID_RECORD_TYPE_LINKED_ACTION_PROFILE_TO_MAPPING_PROFILE = "Action profile '%s' can not be linked to this Mapping profile. FolioRecord and ExistingRecordType types are different";
  private static final String INVALID_REPEATABLE_FIELD_ACTION_FOR_EMPTY_SUBFIELDS_MESSAGE = "Invalid repeatableFieldAction for empty subfields: %s";
  private static final String INVALID_MAPPING_PROFILE_NEW_RECORD_TYPE_LINKED_TO_ACTION_PROFILE = "Can not update MappingProfile recordType and linked ActionProfile recordType are different";
  private static final List<String> DEFAULT_MAPPING_PROFILES = Arrays.asList(
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
  );

  @Autowired
  private ProfileService<ActionProfile, ActionProfileCollection, ActionProfileUpdateDto> actionProfileService;

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
    profile.setMetadata(getMetadata(params.getHeaders()));
    return lookupUser(profile.getMetadata().getUpdatedByUserId(), params)
      .compose(userInfo -> Future.succeededFuture(profile.withUserInfo(userInfo)));
  }

  @Override
  public String getProfileName(MappingProfile profile) {
    return profile.getName();
  }

  @Override
  public MappingProfileUpdateDto withDeletedRelations(MappingProfileUpdateDto profileUpdateDto, List<ProfileAssociation> profileAssociations) {
    return profileUpdateDto.withDeletedRelations(profileAssociations);
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
  protected List<String> getDefaultProfiles() {
    return DEFAULT_MAPPING_PROFILES;
  }

  @Override
  protected Future<Errors> validateProfile(OperationType operationType, MappingProfileUpdateDto profileDto, String tenantId) {
    return composeFutureErrors(
      validateMappingProfileAddedRelationsFolioRecord(profileDto, tenantId),
      validateMappingProfile(operationType, profileDto, tenantId),
      operationType == OperationType.UPDATE ? validateMappingProfileExistProfilesFolioRecord(profileDto, tenantId) : Future.succeededFuture(new Errors())
    );
  }

  private Future<Boolean> deleteExistingActionToMappingAssociations(MappingProfileUpdateDto profileDto, String tenantId) {
    List<Future<Boolean>> futures = profileDto.getAddedRelations().stream()
      .filter(profileAssociation -> profileAssociation.getMasterProfileType().equals(ACTION_PROFILE))
      .map(ProfileAssociation::getMasterWrapperId)
      .map(actionProfileId -> profileAssociationService.deleteByMasterWrapperId(actionProfileId, ProfileType.ACTION_PROFILE,
        ProfileType.MAPPING_PROFILE, tenantId))
      .collect(Collectors.toList());

    return GenericCompositeFuture.all(futures)
      .onFailure(th -> LOGGER.warn("deleteExistingActionToMappingAssociations:: Failed to delete existing action-to-mapping associations", th))
      .map(true);
  }

  private Future<Errors> validateMappingProfileAddedRelationsFolioRecord(MappingProfileUpdateDto mappingProfileUpdateDto, String tenantId) {
    if (CollectionUtils.isEmpty(mappingProfileUpdateDto.getAddedRelations())) {
      return Future.succeededFuture(new Errors().withTotalRecords(0));
    }

    var errors = new LinkedList<Error>();
    Promise<Errors> promise = Promise.promise();

    var futures = mappingProfileUpdateDto
      .getAddedRelations()
      .stream()
      .filter(profileAssociation -> profileAssociation.getMasterProfileType() == ACTION_PROFILE)
      .map(profileAssociation -> actionProfileService.getProfileById(profileAssociation.getMasterProfileId(), false, tenantId))
      .map(futureActionProfile -> futureActionProfile.onSuccess(optionalActionProfile ->
        optionalActionProfile.ifPresent(actionProfile -> validateAssociations(actionProfile, mappingProfileUpdateDto.getProfile(), errors,
          String.format(INVALID_RECORD_TYPE_LINKED_ACTION_PROFILE_TO_MAPPING_PROFILE, actionProfile.getName())))
      ))
      .toList();
    GenericCompositeFuture.all(futures)
      .onSuccess(handler -> promise.complete(new Errors().withErrors(errors)))
      .onFailure(promise::fail);

    return promise.future();
  }

  private Future<Errors> validateMappingProfile(OperationType operationType, MappingProfileUpdateDto mappingProfileUpdateDto, String tenantId) {
    MappingProfile mappingProfile = mappingProfileUpdateDto.getProfile();
    return super.validateProfile(operationType, mappingProfileUpdateDto, tenantId)
      .map(errors -> {
        List<Error> fieldsValidationErrors = validateRepeatableFields(mappingProfile);
        errors.withTotalRecords(errors.getTotalRecords() + fieldsValidationErrors.size())
          .getErrors().addAll(fieldsValidationErrors);
        return errors;
      });
  }

  private Future<Errors> validateMappingProfileExistProfilesFolioRecord(MappingProfileUpdateDto mappingProfileUpdateDto, String tenantId) {
    String profileId = mappingProfileUpdateDto.getProfile().getId();
    var errors = new LinkedList<Error>();
    var deletedRelations = mappingProfileUpdateDto.getDeletedRelations();
    Promise<Errors> promise = Promise.promise();

    getProfileById(profileId, true, tenantId)
      .onSuccess(optionalMappingProfile ->
        optionalMappingProfile.ifPresentOrElse(mappingProfile -> {
            var existActionProfiles = CollectionUtils.isEmpty(deletedRelations) ? mappingProfile.getParentProfiles() :
              mappingProfile.getParentProfiles().stream()
                .filter(profileSnapshotWrapper -> profileSnapshotWrapper.getContentType() == ACTION_PROFILE)
                .filter(profileSnapshotWrapper -> deletedRelations.stream()
                  .noneMatch(deletedRelation -> Objects.equals(deletedRelation.getMasterProfileId(), profileSnapshotWrapper.getProfileId())))
                .toList();

            existActionProfiles.forEach(actionWrapper -> {
              var actionProfile = DatabindCodec.mapper().convertValue(actionWrapper.getContent(), ActionProfile.class);
              validateAssociations(actionProfile, mappingProfileUpdateDto.getProfile(), errors, INVALID_MAPPING_PROFILE_NEW_RECORD_TYPE_LINKED_TO_ACTION_PROFILE);
            });
            promise.complete(new Errors().withErrors(errors));
          }, () -> promise.fail(new NotFoundException(String.format("Mapping profile with id '%s' was not found", profileId)))
        )
      )
      .onFailure(promise::fail);

    return promise.future();
  }

  private List<Error> validateRepeatableFields(MappingProfile mappingProfile) {
    List<Error> errorList = new ArrayList<>();
    if (mappingProfile.getMappingDetails() != null && mappingProfile.getMappingDetails().getMappingFields() != null) {
      List<MappingRule> mappingFields = mappingProfile.getMappingDetails().getMappingFields();
      for (MappingRule rule : mappingFields) {
        if (rule.getRepeatableFieldAction() != null && rule.getSubfields().isEmpty() &&
          !rule.getRepeatableFieldAction().equals(MappingRule.RepeatableFieldAction.DELETE_EXISTING)) {
          errorList.add(new Error().withMessage(format(INVALID_REPEATABLE_FIELD_ACTION_FOR_EMPTY_SUBFIELDS_MESSAGE, rule.getRepeatableFieldAction())));
        }
      }
    }
    return errorList;
  }
}
