package org.folio.services;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.impl.future.CompositeFutureImpl;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.ProfileDao;
import org.folio.dao.association.ProfileWrapperDao;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.impl.util.OkapiConnectionParams;
import org.folio.rest.impl.util.RestUtil;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.EntityTypeCollection;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.MappingDetail;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.OperationType;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.UserInfo;
import org.folio.services.association.CommonProfileAssociationService;
import org.folio.services.association.ProfileAssociationService;
import org.folio.services.exception.ConflictException;
import org.folio.services.exception.UnprocessableEntityException;
import org.folio.services.util.EntityTypes;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MAPPING_PROFILE;

/**
 * Generic implementation of the {@link ProfileService}
 *
 * @param <T> type of the entity
 * @param <S> type of the collection of T entities
 */
public abstract class AbstractProfileService<T, S, D> implements ProfileService<T, S, D> {
  private static final Logger LOGGER = LogManager.getLogger();
  private static final String GET_USER_URL = "/users?query=id==";
  private static final String DELETE_PROFILE_ERROR_MESSAGE = "Can not delete profile by id '%s' cause profile associated with other profiles";
  private static final String DUPLICATE_PROFILE_ERROR_CODE = "%s '%s' already exists";
  private static final String DUPLICATE_PROFILE_ID_ERROR_CODE = "%s with id '%s' already exists";
  private static final String NOT_EMPTY_RELATED_PROFILE_ERROR_CODE = "%s read-only '%s' field should be empty";
  private static final String PROFILE_VALIDATE_ERROR_MESSAGE = "Failed to validate %s";
  private static final String INVALID_ACTION_TYPE_LINKED_ACTION_PROFILE_TO_MAPPING_PROFILE = "Unable to complete requested change. " +
    "MARC Update Action profiles can only be linked with MARC Update Mapping profiles and MARC Modify Action profiles can only be linked with MARC Modify Mapping profiles. " +
    "Please ensure your Action and Mapping profiles are of like types and try again.";
  private static final Map<String, String> ERROR_CODES_TYPES_RELATION = Map.of(
    "mappingProfile", "The field mapping profile",
    "jobProfile", "Job profile",
    "matchProfile", "Match profile",
    "actionProfile", "Action profile"
  );

  @Autowired
  protected ProfileAssociationService profileAssociationService;
  private final EntityTypeCollection entityTypeCollection;

  protected AbstractProfileService() {
    List<String> entityTypeList = Arrays.stream(EntityTypes.values())
      .map(EntityTypes::getName)
      .collect(Collectors.toList());
    entityTypeCollection = new EntityTypeCollection()
      .withEntityTypes(entityTypeList)
      .withTotalRecords(entityTypeList.size());
  }

  @Autowired
  private ProfileDao<T, S> profileDao;

  @Autowired
  protected CommonProfileAssociationService associationService;
  @Autowired
  private ProfileWrapperDao profileWrapperDao;

  @Override
  public Future<S> getProfiles(boolean withRelations, boolean showHidden, String query, int offset, int limit, String tenantId) {
    return profileDao.getProfiles(showHidden, query, offset, limit, tenantId)
      .compose(profilesCollection -> {
        if (withRelations) {
          return fetchRelationsForCollection(profilesCollection, tenantId);
        } else {
          return Future.succeededFuture(profilesCollection);
        }
      });
  }

  @Override
  public Future<Optional<T>> getProfileById(String id, boolean withRelations, String tenantId) {
    return profileDao.getProfileById(id, tenantId)
      .compose(profile -> {
        if (withRelations && profile.isPresent()) {
          return fetchRelations(profile.get(), tenantId).map(Optional::of);
        } else {
          return Future.succeededFuture(profile);
        }
      });
  }

  @Override
  public Future<T> saveProfile(D profileDto, OkapiConnectionParams params) {
    return processValidation(OperationType.CREATE, profileDto, params.getTenantId())
      .compose(profile -> setUserInfoForProfile(profile, params))
      .compose(profileWithInfo -> profileDao.saveProfile(setProfileId(profileWithInfo), params.getTenantId())
        .map(prepareAssociations((profileDto)))
        .compose(ar -> deleteRelatedAssociations(getProfileAssociationToDelete(profileDto), params.getTenantId()))
        .compose(ar -> saveRelatedAssociations(getProfileAssociationToAdd(profileDto), params.getTenantId()))
        .map(profileWithInfo));
  }

  private Future<Boolean> deleteRelatedAssociations(List<ProfileAssociation> profileAssociations, String tenantId) {

    if (profileAssociations.isEmpty()) {
      return Future.succeededFuture(true);
    }

    List<Future<Boolean>> futureList = profileAssociations.stream()
      .map(association -> deleteAssociation(association, tenantId))
      .collect(Collectors.toList());

    Promise<Boolean> result = Promise.promise();
    GenericCompositeFuture.all(futureList).onComplete(ar -> {
      if (ar.succeeded()) {
        result.complete(true);
      } else {
        result.fail(ar.cause());
      }
    });

    return result.future();
  }

  private Future<Boolean> deleteAssociation(ProfileAssociation association, String tenantId) {

    ProfileType masterContentType = association.getMasterProfileType();
    ProfileType detailContentType = association.getDetailProfileType();

    if (association.getMasterProfileType() == ACTION_PROFILE && association.getDetailProfileType() == MAPPING_PROFILE) {
      return deleteMappingToActionProfileAssociation(association, masterContentType, detailContentType, tenantId);
    } else {
      LOGGER.debug("deleteAssociation: masterContentType={}, detailContentType={}, reactTo={}",
        masterContentType.value(), detailContentType.value(), association.getReactTo());
      return profileAssociationService.delete(association.getMasterWrapperId(),
        association.getDetailWrapperId(), masterContentType, detailContentType, association.getJobProfileId(),
        association.getReactTo(), association.getOrder(), tenantId);
    }
  }

  private Future<Boolean> deleteMappingToActionProfileAssociation(ProfileAssociation association,
                                                                  ProfileType masterContentType,
                                                                  ProfileType detailContentType,
                                                                  String tenantId) {
    return profileAssociationService.deleteByMasterIdAndDetailId(association.getMasterProfileId(),
      association.getDetailProfileId(), masterContentType, detailContentType, tenantId);
  }

  private Future<Boolean> saveRelatedAssociations(List<ProfileAssociation> profileAssociations, String tenantId) {
    if (profileAssociations.isEmpty()) {
      return Future.succeededFuture(true);
    }
    Promise<Boolean> result = Promise.promise();
    GenericCompositeFuture.all(fillProfileDataIfNeeded(profileAssociations, tenantId))
      .onSuccess(r ->
        associationService.wrapAssociationProfiles(profileAssociations, tenantId)
          .compose(e -> profileAssociationService.save(profileAssociations, tenantId))
          .onComplete(ar -> {
            if (ar.succeeded()) {
              result.complete(true);
            } else {
              result.fail(ar.cause());
            }
          })
      ).onFailure(result::fail);
    return result.future();
  }

  private List<Future<Void>> fillProfileDataIfNeeded(List<ProfileAssociation> profileAssociations, String tenantId) {
    List<Future<Void>> futures = new ArrayList<>();

    int associationsAmount;
    for (int i = 0; i < profileAssociations.size(); i++) {
      associationsAmount = i + 1;
      ProfileAssociation profileAssociation = profileAssociations.get(i);
      if (profileAssociation.getDetailProfileType() == ACTION_PROFILE &&
        (profileAssociations.size() == associationsAmount || profileAssociations.get(associationsAmount).getMasterProfileType() != ACTION_PROFILE)) {

        futures.add(fillActionProfileDetailWrapper(profileAssociation, tenantId));

      } else if (profileAssociation.getMasterProfileType() == ACTION_PROFILE
        && profileAssociation.getDetailProfileType() == MAPPING_PROFILE
        && profileAssociation.getMasterWrapperId() == null) {

        futures.add(fillActionProfileMasterWrapper(profileAssociation, tenantId));
      }
    }
    return futures;
  }

  private Future<Void> fillActionProfileDetailWrapper(ProfileAssociation actionProfileAssociation, String tenantId) {
    String actionProfileId = actionProfileAssociation.getDetailProfileId();
    return profileWrapperDao.getWrapperByProfileId(actionProfileId, ACTION_PROFILE, tenantId)
      .compose(profileWrappers -> {
        if (profileWrappers.size() == 0) {
          return Future.failedFuture(new NotFoundException(format("Wrapper does NOT exist for this Action Profile with id '%s' ", actionProfileId)));
        }
        actionProfileAssociation.setDetailWrapperId(profileWrappers.get(0).getId());
        return Future.succeededFuture();
      });
  }

  private Future<Void> fillActionProfileMasterWrapper(ProfileAssociation actionProfileAssociation, String tenantId) {
    String actionProfileId = actionProfileAssociation.getMasterProfileId();
    return profileWrapperDao.getWrapperByProfileId(actionProfileId, ACTION_PROFILE, tenantId)
      .compose(profileWrappers -> {
        if (profileWrappers.size() == 1) {
          actionProfileAssociation.setMasterWrapperId(profileWrappers.get(0).getId());
          return Future.succeededFuture();
        }
        if (profileWrappers.size() > 1) {
          return Future.failedFuture(new IllegalStateException(format("Found several wrappers for Action Profile with id '%s' ", actionProfileId)));
        }
        return Future.succeededFuture();
      });
  }

  @Override
  public Future<T> updateProfile(D profileDto, OkapiConnectionParams params) {
    String profileId = getProfileId(getProfile(profileDto));
    return isProfileDtoValidForUpdate(profileId, profileDto, getDefaultProfiles().contains(profileId), params.getTenantId())
      .compose(isDtoValidForUpdate -> {
        if (Boolean.FALSE.equals(isDtoValidForUpdate)) {
          String errorMessage = String.format("Can`t update default %s with id %s", getProfileContentType(), profileId);
          LOGGER.warn("updateProfile:: {}", errorMessage);
          return Future.failedFuture(new BadRequestException(errorMessage));
        }
        return processValidation(OperationType.UPDATE, profileDto, params.getTenantId());
      })
      .compose(profile -> setUserInfoForProfile(profile, params))
      .compose(profileWithInfo -> profileDao.updateProfile(profileWithInfo, params.getTenantId()))
      .map(prepareAssociations((profileDto)))
      .compose(ar -> deleteRelatedAssociations(getProfileAssociationToDelete(profileDto), params.getTenantId()))
      .compose(ar -> saveRelatedAssociations(getProfileAssociationToAdd(profileDto), params.getTenantId()))
      .map(getProfile(profileDto));
  }

  @Override
  public Future<Boolean> hardDeleteProfile(String id, String tenantId) {
    if (!canDeleteProfile(id)) {
      String errorMessage = String.format("Can`t delete default %s with id %s", getProfileContentType(), id);
      LOGGER.warn("hardDeleteProfile:: {}", errorMessage);
      return Future.failedFuture(new BadRequestException(errorMessage));
    }

    return profileDao.isProfileAssociatedAsDetail(id, tenantId)
      .compose(isAssociated -> isAssociated
        ? Future.failedFuture(new ConflictException(String.format(DELETE_PROFILE_ERROR_MESSAGE, id)))
        : profileDao.hardDeleteProfile(id, tenantId))
      .map(true);
  }

  @Override
  public Future<Boolean> isProfileExistByProfileName(T profile, String tenantId) {
    String profileName = getProfileName(profile);
    String profileId = getProfileId(profile);
    return profileDao.isProfileExistByName(profileName, profileId, tenantId);
  }

  @Override
  public Future<Boolean> isProfileExistByProfileId(T profile, String tenantId) {
    String profileId = getProfileId(profile);
    return StringUtils.isBlank(profileId) ?
      Future.succeededFuture(false) : getProfileById(profileId, false, tenantId).map(Optional::isPresent);
  }

  /**
   * Check profile to access another fields except tags
   *
   * @param id               - Profile id
   * @param profile          - D DTO
   * @param isDefaultProfile - True if the profile lists as default
   * @param tenantId         - Tenant id from request
   * @return - boolean value. True if in the profile DTO has been changed only Tags
   */
  @Override
  public Future<Boolean> isProfileDtoValidForUpdate(String id, D profile, boolean isDefaultProfile, String tenantId) {
    return isDefaultProfile ? getProfileById(id, false, tenantId).map(profileOptional ->
      profileOptional.map(fetchedProfile -> Stream.of(getProfile(profile), fetchedProfile).map(JsonObject::mapFrom)
        .peek(jsonObject -> {
          jsonObject.remove("tags");
          jsonObject.remove("metadata");
        }).distinct().count() <= 1).orElse(false)) : Future.succeededFuture(true);
  }

  @Override
  public Future<Boolean> isProfileContainsChildProfiles(T profile) {
    List<ProfileSnapshotWrapper> childProfiles = getChildProfiles(profile);
    return Future.succeededFuture(childProfiles != null && !childProfiles.isEmpty());
  }

  @Override
  public Future<Boolean> isProfileContainsParentProfiles(T profile) {
    List<ProfileSnapshotWrapper> parentProfiles = getParentProfiles(profile);
    return Future.succeededFuture(parentProfiles != null && !parentProfiles.isEmpty());
  }

  @Override
  public Future<EntityTypeCollection> getEntityTypes() {
    return Future.succeededFuture(entityTypeCollection);
  }

  /**
   * Generates id and sets it to the Profile entity
   *
   * @param profile Profile
   * @return Profile with generated id
   */
  abstract T setProfileId(T profile);

  /**
   * Set UserInfo for the Profile entity
   *
   * @param profile Profile
   * @param params  {@link OkapiConnectionParams}
   * @return Profile with filled userInfo field
   */
  abstract Future<T> setUserInfoForProfile(T profile, OkapiConnectionParams params);

  /**
   * Returns name of specified profile
   *
   * @param profile - profile entity
   * @return - profile name
   */
  public abstract String getProfileName(T profile);

  /**
   * Returns id of specified profile
   *
   * @param profile - profile entity
   * @return - profile id
   */
  protected abstract String getProfileId(T profile);

  protected abstract D prepareAssociations(D profileDto);

  /**
   * Returns type of profile
   *
   * @return - ProfileType of profiles type
   */
  protected abstract ProfileType getProfileContentType();

  protected abstract List<ProfileSnapshotWrapper> getChildProfiles(T profile);

  protected abstract void setChildProfiles(T profile, List<ProfileSnapshotWrapper> childProfiles);

  protected abstract List<ProfileSnapshotWrapper> getParentProfiles(T profile);

  protected abstract void setParentProfiles(T profile, List<ProfileSnapshotWrapper> parentProfiles);

  protected abstract List<T> getProfilesList(S profilesCollection);

  protected abstract List<ProfileAssociation> getProfileAssociationToAdd(D dto);

  protected abstract List<ProfileAssociation> getProfileAssociationToDelete(D dto);

  protected abstract T getProfile(D dto);

  protected abstract List<String> getDefaultProfiles();

  /**
   * Load all related child profiles for existing profile
   *
   * @param profile  - profile entity
   * @param tenantId - tenant id
   * @return - List of all related child profiles
   */
  private Future<List<ProfileSnapshotWrapper>> fetchChildProfiles(T profile, String tenantId) {
    return associationService.findDetails(getProfileId(profile), getProfileContentType(), null, null, 0, 999, tenantId)
      .map(this::convertToProfileSnapshotWrapper);
  }

  /**
   * Load all related parent profiles for existing profile
   *
   * @param profile  - profile entity
   * @param tenantId - tenant id
   * @return - List of all related parent profiles
   */
  private Future<List<ProfileSnapshotWrapper>> fetchParentProfiles(T profile, String tenantId) {
    return associationService.findMasters(getProfileId(profile), getProfileContentType(), null, null, 0, 999, tenantId)
      .map(this::convertToProfileSnapshotWrapper);
  }

  /**
   * Fetch parent and child profiles for each profile in collection
   *
   * @param profilesCollection - profile collection entity
   * @return - profile collection with fetched relations
   */
  private Future<S> fetchRelationsForCollection(S profilesCollection, String tenantId) {
    List<T> profilesList = getProfilesList(profilesCollection);
    List<Future<T>> futureList = new ArrayList<>();
    Promise<S> result = Promise.promise();
    profilesList.forEach(profile ->
      futureList.add(fetchRelations(profile, tenantId)));
    GenericCompositeFuture.all(futureList).onComplete(ar -> {
      if (ar.succeeded()) {
        result.complete(profilesCollection);
      } else {
        LOGGER.warn("fetchRelationsForCollection:: Error during fetching related profiles", ar.cause());
        result.fail(ar.cause());
      }
    });
    return result.future();
  }

  /**
   * Fetch parent and child profiles for single profile
   *
   * @param profile - profile entity
   * @return - profile collection with fetched relations
   */
  private Future<T> fetchRelations(T profile, String tenantId) {
    List<Future<T>> futureList = new ArrayList<>();
    Promise<T> result = Promise.promise();
    futureList.add(fetchChildProfiles(profile, tenantId)
      .compose(childProfiles -> {
        setChildProfiles(profile, childProfiles);
        return Future.succeededFuture(profile);
      })
      .compose(v -> fetchParentProfiles(profile, tenantId))
      .compose(parentProfiles -> {
        setParentProfiles(profile, parentProfiles);
        return Future.succeededFuture(profile);
      }));
    GenericCompositeFuture.all(futureList).onComplete(ar -> {
      if (ar.succeeded()) {
        result.complete(profile);
      } else {
        LOGGER.warn("fetchRelations:: Error during fetching related profiles", ar.cause());
        result.fail(ar.cause());
      }
    });
    return result.future();
  }

  private List<ProfileSnapshotWrapper> convertToProfileSnapshotWrapper(Optional<ProfileSnapshotWrapper> rootWrapper) {
    List<ProfileSnapshotWrapper> profileSnapshotWrappers = new ArrayList<>();
    rootWrapper.ifPresent(profileSnapshotWrapper -> profileSnapshotWrappers.addAll(profileSnapshotWrapper.getChildSnapshotWrappers()
      .stream()
      .map(JsonObject::mapFrom)
      .map(json -> json.mapTo(ProfileSnapshotWrapper.class))
      .toList()));
    return profileSnapshotWrappers;
  }

  /**
   * Finds user by user id and returns UserInfo
   *
   * @param userId user id
   * @param params Okapi connection params
   * @return Future with found UserInfo
   */
  Future<UserInfo> lookupUser(String userId, OkapiConnectionParams params) {
    Promise<UserInfo> promise = Promise.promise();
    RestUtil.doRequest(params, GET_USER_URL + userId, HttpMethod.GET, null)
      .onComplete(getUserResult -> {
        if (RestUtil.validateAsyncResult(getUserResult, promise)) {
          JsonObject response = getUserResult.result().getJson();
          if (!response.containsKey("totalRecords") || !response.containsKey("users")) {
            promise.fail("Error, missing field(s) 'totalRecords' and/or 'users' in user response object");
          } else {
            int recordCount = response.getInteger("totalRecords");
            if (recordCount > 1) {
              String errorMessage = "lookupUser:: There are more then one user by requested user id : " + userId;
              LOGGER.warn(errorMessage);
              promise.fail(errorMessage);
            } else if (recordCount == 0) {
              String errorMessage = "No user found by user id :" + userId;
              LOGGER.warn(errorMessage);
              promise.fail(errorMessage);
            } else {
              JsonObject jsonUser = response.getJsonArray("users").getJsonObject(0);
              JsonObject userPersonalInfo = jsonUser.getJsonObject("personal");
              UserInfo userInfo = new UserInfo()
                .withFirstName(userPersonalInfo.getString("firstName"))
                .withLastName(userPersonalInfo.getString("lastName"))
                .withUserName(jsonUser.getString("username"));
              promise.complete(userInfo);
            }
          }
        }
      });
    return promise.future();
  }

  private Future<T> processValidation(OperationType operationType, D profileDto, String tenantId) {
    return validateProfile(operationType, profileDto, tenantId)
      .onFailure(th -> LOGGER.warn(PROFILE_VALIDATE_ERROR_MESSAGE, th.getCause()))
      .compose(errors -> {
        if (errors.getTotalRecords() > 0) {
          return Future.failedFuture(new UnprocessableEntityException(errors));
        }
        return Future.succeededFuture(getProfile(profileDto));
      });
  }

  protected Future<Errors> validateProfile(OperationType operationType, D profileDto, String tenantId) {
    T profile = getProfile(profileDto);
    Promise<Errors> promise = Promise.promise();
    String profileTypeName = StringUtils.uncapitalize(profile.getClass().getSimpleName());
    Map<String, Future<Boolean>> validateConditions = getValidateConditions(operationType, profile, tenantId);
    List<String> errorCodes = new ArrayList<>(validateConditions.keySet());
    List<Future<Boolean>> futures = new ArrayList<>(validateConditions.values());
    GenericCompositeFuture.all(futures).onComplete(ar -> {
      if (ar.succeeded()) {
        List<Error> errors = new ArrayList<>(errorCodes).stream()
          .filter(errorCode -> ar.result().resultAt(errorCodes.indexOf(errorCode)))
          .map(errorCode -> new Error().withMessage(format(errorCode,
            ERROR_CODES_TYPES_RELATION.get(profileTypeName), getProfileName(profile))))
          .collect(Collectors.toList());
        promise.complete(new Errors().withErrors(errors).withTotalRecords(errors.size()));
      } else {
        promise.fail(ar.cause());
      }
    });
    return promise.future();
  }

  private Map<String, Future<Boolean>> getValidateConditions(OperationType operationType, T profile, String tenantId) {
    Map<String, Future<Boolean>> validateConditions = new LinkedHashMap<>();
    validateConditions.put(DUPLICATE_PROFILE_ERROR_CODE, isProfileExistByProfileName(profile, tenantId));
    validateConditions.put(String.format(NOT_EMPTY_RELATED_PROFILE_ERROR_CODE, "%s", "child"), isProfileContainsChildProfiles(profile));
    validateConditions.put(String.format(NOT_EMPTY_RELATED_PROFILE_ERROR_CODE, "%s", "parent"), isProfileContainsParentProfiles(profile));
    if (operationType == OperationType.CREATE) {
      validateConditions.put(DUPLICATE_PROFILE_ID_ERROR_CODE, isProfileExistByProfileId(profile, tenantId));
    }
    return validateConditions;
  }

  protected void validateAssociations(ActionProfile actionProfile, MappingProfile mappingProfile, List<Error> errors, String errMsg) {
    var mappingRecordType = mappingProfile.getExistingRecordType().value();
    var actionRecordType = actionProfile.getFolioRecord().value();
    if (!actionRecordType.equals(mappingRecordType)) {
      LOGGER.info("validateAssociations:: Can not create or update profile. MappingProfile with ID:{} FolioRecord:{}, linked ActionProfile with ID:{} FolioRecord:{}",
        mappingProfile.getId(), mappingRecordType, actionProfile.getId(), actionRecordType);
      errors.add(new Error().withMessage(errMsg));
      return;
    }

    Optional.ofNullable(mappingProfile.getMappingDetails())
      .map(MappingDetail::getMarcMappingOption)
      .filter(option -> !option.value().equals(actionProfile.getAction().value()))
      .ifPresent(option -> {
        LOGGER.info("validateAssociations:: Can not create or update profile. ActionProfile Action:{}, linked MappingProfile Option:{}", actionProfile.getAction().value(), option);
        errors.add(new Error().withMessage(INVALID_ACTION_TYPE_LINKED_ACTION_PROFILE_TO_MAPPING_PROFILE));
      });
  }

  @SafeVarargs
  protected final Future<Errors> composeFutureErrors(Future<Errors>... errorsFuture) {
    return CompositeFutureImpl.all(errorsFuture).map(compositeFuture -> compositeFuture.list().stream()
      .map(Errors.class::cast)
      .reduce(new Errors().withTotalRecords(0), (accumulator, errors) -> addAll(accumulator, errors.getErrors())
      ));
  }

  private Errors addAll(Errors errors, List<Error> otherErrors) {
    errors.withTotalRecords(errors.getTotalRecords() + otherErrors.size())
      .getErrors().addAll(otherErrors);
    return errors;
  }

  protected boolean canDeleteProfile(String profileId) {
    return !getDefaultProfiles().contains(profileId);
  }
}
