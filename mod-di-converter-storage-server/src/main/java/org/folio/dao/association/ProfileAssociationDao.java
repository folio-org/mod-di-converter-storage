package org.folio.dao.association;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileAssociationCollection;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ReactToType;

import java.util.Optional;

/**
 * This DAO is for association between 2 profiles (which are called 'master' profile and 'detail' profile).
 */
public interface ProfileAssociationDao {

  /**
   * Saves ProfileAssociation entity to database
   *
   * @param entity     ProfileAssociation to save
   * @param masterType a master type in association
   * @param detailType a detail type in association
   * @param tenantId   tenant id
   * @return future
   */
  Future<String> save(ProfileAssociation entity, ProfileSnapshotWrapper.ContentType masterType, ProfileSnapshotWrapper.ContentType detailType, String tenantId);

  /**
   * Searches for ProfileAssociation by masterType and detailType
   *
   * @param masterType a master type in association
   * @param detailType a detail type in association
   * @param tenantId   tenant id
   * @return future with {@link ProfileAssociationCollection}
   */
  Future<ProfileAssociationCollection> getAll(ProfileSnapshotWrapper.ContentType masterType, ProfileSnapshotWrapper.ContentType detailType, String tenantId);

  /**
   * Searches for ProfileAssociation entity by id
   *
   * @param id         ProfileAssociation id
   * @param masterType a master type in association
   * @param detailType a detail type in association
   * @param tenantId   tenant id
   * @return future with optional entity
   */
  Future<Optional<ProfileAssociation>> getById(String id, ProfileSnapshotWrapper.ContentType masterType, ProfileSnapshotWrapper.ContentType detailType, String tenantId);

  /**
   * Updates ProfileAssociation entity in database
   *
   * @param entity     ProfileAssociation entity to update
   * @param masterType a master type in association
   * @param detailType a detail type in association
   * @param tenantId   tenant id
   * @return future with updated entity
   */
  Future<ProfileAssociation> update(ProfileAssociation entity, ProfileSnapshotWrapper.ContentType masterType, ProfileSnapshotWrapper.ContentType detailType, String tenantId);

  /**
   * Deletes entity from database
   *
   * @param id         ProfileAssociation  id
   * @param masterType a master type in association
   * @param detailType a detail type in association
   * @param tenantId   tenant id
   * @return future with true if succeeded
   */
  Future<Boolean> delete(String id, ProfileSnapshotWrapper.ContentType masterType, ProfileSnapshotWrapper.ContentType detailType, String tenantId);

  /**
   * Delete ProfileAssociation  by masterWrapperId and detailWrapperId
   *
   * @param masterWrapperId     - UUID of masterProfile wrapper
   * @param detailWrapperId     - UUID of detailProfile wrapper
   * @param masterType   - master Profile Type
   * @param detailType   - detail Profile Type
   * @param tenantId     - tenant id
   * @param jobProfileId - job profile id (optional)
   * @return - boolean result of operation
   */
  Future<Boolean> delete(String masterWrapperId, String detailWrapperId, ProfileSnapshotWrapper.ContentType masterType,
                         ProfileSnapshotWrapper.ContentType detailType, String jobProfileId, ReactToType reactTo, String tenantId);

  /**
   * Delete profile associations for particular master profile by wrapperId
   *
   * @param wrapperId   - master profile wrapper id
   * @param masterType - master profile type
   * @param detailType - detail profile type
   * @param tenantId   - tenant id
   * @return future with boolean
   */
  Future<Boolean> deleteByMasterWrapperId(String wrapperId, ProfileSnapshotWrapper.ContentType masterType, ProfileSnapshotWrapper.ContentType detailType, String tenantId);

  /**
   * Delete ProfileAssociation  by masterWrapperId and detailWrapperId
   *
   * @param masterId     - UUID of masterProfile
   * @param detailId     - UUID of detailProfile
   * @param masterType   - master Profile Type
   * @param detailType   - detail Profile Type
   * @param tenantId     - tenant id
   * @return - boolean result of operation
   */
  Future<Boolean> deleteByMasterIdAndDetailId(String masterId, String detailId, ProfileSnapshotWrapper.ContentType masterType,
                                              ProfileSnapshotWrapper.ContentType detailType, String tenantId);
}
