package org.folio.dao.snapshot;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;

import java.util.List;
import java.util.Optional;

/**
 * Profile snapshot DAO
 */
public interface ProfileSnapshotDao {
  /**
   * Searches for ProfileSnapshotWrapper by id
   *
   * @param id       ProfileSnapshotWrapper id
   * @param tenantId tenant id
   * @return future with optional {@link ProfileSnapshotWrapper}
   */
  Future<Optional<ProfileSnapshotWrapper>> getById(String id, String tenantId);

  /**
   * Saves ProfileSnapshotWrapper entity to database
   *
   * @param entity   ProfileSnapshotWrapper to save
   * @param tenantId tenant id
   * @return future
   */
  Future<String> save(ProfileSnapshotWrapper entity, String tenantId);

  /**
   * Returns the list of snapshot associations, listed in hierarchical order
   *
   * @param profileId    profile uuid
   * @param profileType  profile type
   * @param jobProfileId job profile uuid
   * @param tenantId     tenant id
   * @return list of the snapshot items
   */
  Future<List<ProfileAssociation>> getSnapshotAssociations(String profileId, ProfileType profileType, String jobProfileId, String tenantId);
}
