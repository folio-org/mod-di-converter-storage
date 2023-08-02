package org.folio.dao.association;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.ProfileWrapper;

import java.util.Optional;

/**
 * This DAO is for profiles wrappers which are used in associations
 */
public interface ProfileWrapperDao {
  /**
   * Returns profile wrapper by id
   *
   * @param id         profile wrapper id
   * @param tenantId   tenant id
   * @return a list of details for a master id
   */
  Future<Optional<ProfileWrapper>> getProfileWrapperById(String id, String tenantId);


  /**
   * Saves ProfileWrapper entity to database
   *
   * @param entity     ProfileWrapper to save
   * @param tenantId   tenant id
   * @return future
   */
  Future<String> save(ProfileWrapper entity, String tenantId);

  /**
   * Deletes ProfileWrapper entity from database by id
   * @param id -  profile wrapper id
   * @param tenantId - tenant id
   * @return future with if delete = true, if not - false
   */
  Future<Boolean> deleteById(String id, String tenantId);

  /**
   * Check if profile_wrapper table is empty
   * @param tenantId - tenant id
   * @return future with if delete = true, if not - false
   */
  Future<Boolean> checkIfDataInTableExists(String tenantId);
}
