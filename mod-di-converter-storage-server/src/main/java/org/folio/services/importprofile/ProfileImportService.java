package org.folio.services.importprofile;

import io.vertx.core.Future;
import org.folio.rest.impl.util.OkapiConnectionParams;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;

/**
 * JProfile import service
 */
public interface ProfileImportService {

  /**
   * Import a profile snapshot with related profiles
   *
   * @param profileSnapshot  profile snapshot
   * @param tenantId     tenant id
   * @return future with snapshot {@link ProfileSnapshotWrapper}
   */
  Future<ProfileSnapshotWrapper> importProfile(ProfileSnapshotWrapper profileSnapshot, String tenantId, OkapiConnectionParams okapiParams);
}
