package org.folio.services.migration;

import io.vertx.core.Context;
import io.vertx.core.Future;
import org.folio.dao.snapshot.ProfileSnapshotItem;
import org.folio.rest.jaxrs.model.ProfileAssociation;

import java.util.List;
import java.util.Map;

/**
 * Service for the migration data-import profiles to the wrappers-based mechanism.
 */
public interface ProfileMigrationService {

  /**
   * Migrate all data-import profiles to wrappers-based mechanism.
   * @param headers - headers
   * @param context - context
   * @return - future with result. True if successful, false otherwise
   */
  Future<Boolean> migrateDataImportProfiles(Map<String, String> headers, Context context);

  /**
   * Map profileSnapshotItems to the profileAssociations and sort them in valid order.
   * @param snapshotItems - list with retrieved snapshot items
   * @return - sorted list with profileAssocations
   */
  List<ProfileAssociation> sortSnapshots(List<ProfileSnapshotItem> snapshotItems);
}
