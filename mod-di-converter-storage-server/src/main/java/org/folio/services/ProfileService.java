package org.folio.services;

import io.vertx.core.Future;
import org.folio.rest.impl.util.OkapiConnectionParams;
import org.folio.rest.jaxrs.model.EntityTypeCollection;
import org.folio.rest.jaxrs.model.ProfileAssociation;

import java.util.List;
import java.util.Optional;

/**
 * Generic Profile Service
 *
 * @param <T> type of the entity
 * @param <S> type of the collection of T entities
 */
public interface ProfileService<T, S, D> {

  /**
   * Searches for T entities
   *
   * @param showHidden    indicates to return T entities marked as hidden or not
   * @param query         query from URL
   * @param offset        starting index in a list of results
   * @param limit         limit of records for pagination
   * @param withRelations load profile with related profiles
   * @param tenantId      tenant id
   * @return future with S, a collection of T entities
   */
  Future<S> getProfiles(boolean withRelations, boolean showHidden, String query, int offset, int limit, String tenantId);

  /**
   * Searches for T by id
   *
   * @param id            Profile id
   * @param tenantId      tenant id
   * @param withRelations load profile with related profiles
   * @return future with optional {@link T}
   */
  Future<Optional<T>> getProfileById(String id, boolean withRelations, String tenantId);

  /**
   * Saves T entity
   *
   * @param profileDto Profile DTO to save
   * @param params  {@link OkapiConnectionParams}
   * @return future with saved entity
   */
  Future<T> saveProfile(D profileDto, OkapiConnectionParams params);

  /**
   * Updates D with given id
   *
   * @param profileDto Profile DTO to update
   * @param params  {@link OkapiConnectionParams}
   * @return future with updated entity
   */
  Future<T> updateProfile(D profileDto, OkapiConnectionParams params);

  /**
   * Search in database profile with the same name which contains in specified profile
   *
   * @param profile  - T entity
   * @param tenantId - tenant id from request
   * @return - boolean value. True if job profile with the same name already exist
   */
  Future<Boolean> isProfileExistByProfileName(T profile, String tenantId);

  /**
   * Search in database profile with the same id which contains in specified profile
   *
   * @param profile  - T entity
   * @param tenantId - tenant id from request
   * @return - boolean value. True if job profile with the same id already exist
   */
  Future<Boolean> isProfileExistByProfileId(T profile, String tenantId);

  /**
   * Hard deletes profile by its id
   *
   * @param id       Profile id
   * @param tenantId tenant id from request
   * @return future with true if succeeded
   */
  Future<Boolean> hardDeleteProfile(String id, String tenantId);

  /**
   * Returns {@link EntityTypeCollection}
   *
   * @return future with {@link EntityTypeCollection}
   */
  Future<EntityTypeCollection> getEntityTypes();

  /**
   * Returns name of specified profile
   *
   * @param profile - profile entity
   * @return - profile name
   */
  String getProfileName(T profile);

  /**
   * Set deleted relations to specified profile update dto
   *
   * @param profileUpdateDto - profile update dto entity
   * @return - profile update dto
   */
  D withDeletedRelations(D profileUpdateDto, List<ProfileAssociation> profileAssociations);

  /**
   * Checks is profile contains child profiles
   *
   * @param profile  - T entity
   * @return - boolean value. True if profile contains child profiles
   */
  Future<Boolean> isProfileContainsChildProfiles(T profile);

  /**
   * Checks is profile contains parent profiles
   *
   * @param profile  - T entity
   * @return - boolean value. True if profile contains parent profiles
   */
  Future<Boolean> isProfileContainsParentProfiles(T profile);
}
