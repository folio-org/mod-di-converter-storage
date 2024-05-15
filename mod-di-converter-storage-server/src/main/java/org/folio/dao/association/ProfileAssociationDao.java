package org.folio.dao.association;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileAssociationCollection;

import java.util.Optional;
import org.folio.rest.jaxrs.model.ReactToType;

/**
 * This DAO is for association between 2 profiles (which are called 'master' profile and 'detail' profile).
 */
public interface ProfileAssociationDao {

  /**
   * Saves ProfileAssociation entity to database
   *
   * @param entity     ProfileAssociation to save
   * @param tenantId   tenant id
   * @return future
   */
  Future<String> save(ProfileAssociation entity, String tenantId);

  /**
   * Searches for ProfileAssociation by masterType and detailType
   *
   * @param tenantId   tenant id
   * @return future with {@link ProfileAssociationCollection}
   */
  Future<ProfileAssociationCollection> getAll(String tenantId);

  /**
   * Searches for ProfileAssociation entity by id
   *
   * @param id         ProfileAssociation id
   * @param tenantId   tenant id
   * @return future with optional entity
   */
  Future<Optional<ProfileAssociation>> getById(String id, String tenantId);

  /**
   * Updates ProfileAssociation entity in database
   *
   * @param entity     ProfileAssociation entity to update
   * @param tenantId   tenant id
   * @return future with updated entity
   */
  Future<ProfileAssociation> update(ProfileAssociation entity, String tenantId);

  /**
   * Deletes entity from database
   *
   * @param id         ProfileAssociation  id
   * @param tenantId   tenant id
   * @return future with true if succeeded
   */
  Future<Boolean> delete(String id, String tenantId);

  /**
   * Delete ProfileAssociation  by masterWrapperId and detailWrapperId
   *
   * @param masterWrapperId     - UUID of masterProfile wrapper
   * @param detailWrapperId     - UUID of detailProfile wrapper
   * @param jobProfileId - job profile id (optional)
   * @param order        - order
   * @param tenantId     - tenant id
   * @return - boolean result of operation
   */
  Future<Boolean> delete(String masterWrapperId, String detailWrapperId, String jobProfileId, ReactToType reactTo,
                         Integer order, String tenantId);

  /**
   * Delete profile associations for particular master profile by wrapperId
   *
   * @param wrapperId   - master profile wrapper id
   * @param tenantId   - tenant id
   * @return future with boolean
   */
  Future<Boolean> deleteByMasterWrapperId(String wrapperId, String tenantId);

  /**
   * Delete ProfileAssociation  by masterWrapperId and detailWrapperId
   *
   * @param masterId     - UUID of masterProfile
   * @param detailId     - UUID of detailProfile
   * @param tenantId     - tenant id
   * @return - boolean result of operation
   */
  Future<Boolean> deleteByMasterIdAndDetailId(String masterId, String detailId, String tenantId);
}
