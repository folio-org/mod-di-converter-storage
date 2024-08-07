package org.folio.services;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.ProfileDao;
import org.folio.dao.association.ProfileWrapperDao;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.impl.util.OkapiConnectionParams;
import org.folio.rest.impl.util.RestUtil;
import org.folio.rest.jaxrs.model.EntityTypeCollection;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.UserInfo;
import org.folio.services.association.CommonProfileAssociationService;
import org.folio.services.association.ProfileAssociationService;
import org.folio.services.exception.ConflictException;
import org.folio.services.util.EntityTypes;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
  public Future<T> saveProfile(D profile, OkapiConnectionParams params) {
    return setUserInfoForProfile(profile, params)
      .compose(profileWithInfo -> profileDao.saveProfile(setProfileId(profileWithInfo), params.getTenantId())
        .map(prepareAssociations((profile)))
        .compose(ar -> deleteRelatedAssociations(getProfileAssociationToDelete(profile), params.getTenantId()))
        .compose(ar -> saveRelatedAssociations(getProfileAssociationToAdd(profile), params.getTenantId()))
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
  public Future<T> updateProfile(D profile, OkapiConnectionParams params) {
    return setUserInfoForProfile(profile, params)
      .compose(profileWithInfo -> profileDao.updateProfile(profileWithInfo, params.getTenantId()))
      .map(prepareAssociations((profile)))
      .compose(ar -> deleteRelatedAssociations(getProfileAssociationToDelete(profile), params.getTenantId()))
      .compose(ar -> saveRelatedAssociations(getProfileAssociationToAdd(profile), params.getTenantId()))
      .map(getProfile(profile));
  }

  @Override
  public Future<Boolean> hardDeleteProfile(String id, String tenantId) {
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
  abstract Future<T> setUserInfoForProfile(D profile, OkapiConnectionParams params);

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
      .collect(Collectors.toList())));
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
}
