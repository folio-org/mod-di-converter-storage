package org.folio.support;

import static org.folio.dataimport.testsupport.postgres.PostgresTestSupport.clearTable;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.io.File;
import java.io.IOException;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;

@Log4j2
public class TestUtil {

  public static final String ACTION_PROFILES_PATH = "/data-import-profiles/actionProfiles";
  public static final String ASSOCIATED_PROFILES_PATH = "/data-import-profiles/profileAssociations";
  public static final String DETAILS_BY_MASTER_PATH = "/data-import-profiles/profileAssociations/{masterId}/details";
  public static final String ENTITY_TYPES_PATH = " /data-import-profiles/entityTypes";
  public static final String FIELD_PROTECTION_SETTINGS_PATH = "/field-protection-settings/marc";
  public static final String FORMS_CONFIGS_PATH = "/converter-storage/forms/configs";
  public static final String JOB_PROFILE_SNAPSHOT_PATH = "/data-import-profiles/jobProfileSnapshots";
  public static final String JOB_PROFILES_PATH = "/data-import-profiles/jobProfiles";
  public static final String MAPPING_PROFILES_PATH = "/data-import-profiles/mappingProfiles";
  public static final String MASTERS_BY_DETAIL_PATH = "/data-import-profiles/profileAssociations/{detailId}/masters";
  public static final String MATCH_PROFILES_PATH = "/data-import-profiles/matchProfiles";
  public static final String PROFILE_SNAPSHOT_PATH = "/data-import-profiles/profileSnapshots";

  public static final String ACTION_PROFILES_TABLE = "action_profiles";
  public static final String ASSOCIATIONS_TABLE = "profile_associations";
  public static final String FORMS_CONFIGS_TABLE = "forms_configs";
  public static final String JOB_PROFILES_TABLE = "job_profiles";
  public static final String MAPPING_PROFILES_TABLE = "mapping_profiles";
  public static final String MARC_FIELD_PROTECTION_SETTINGS_TABLE = "marc_field_protection_settings";
  public static final String MATCH_PROFILES_TABLE = "match_profiles";
  public static final String PROFILE_WRAPPERS_TABLE = "profile_wrappers";
  public static final String SNAPSHOTS_TABLE = "profile_snapshots";

  public static final String JOB_PROFILE_ID_PARAM = "jobProfileId";
  public static final String PROFILE_TYPE_PARAM = "profileType";

  public static final String TENANT_ID = "diku";

  public static String readFileFromPath(String path) throws IOException {
    return new String(FileUtils.readFileToByteArray(new File(path)));
  }

  public static Future<Void> clearAllProfileTables(Vertx vertx) {
    return clearTable(ASSOCIATIONS_TABLE, vertx, TENANT_ID)
      .compose(v -> clearTable(SNAPSHOTS_TABLE, vertx, TENANT_ID))
      .compose(v -> clearTable(PROFILE_WRAPPERS_TABLE, vertx, TENANT_ID))
      .compose(v -> clearTable(JOB_PROFILES_TABLE, vertx, TENANT_ID))
      .compose(v -> clearTable(MATCH_PROFILES_TABLE, vertx, TENANT_ID))
      .compose(v -> clearTable(ACTION_PROFILES_TABLE, vertx, TENANT_ID))
      .compose(v -> clearTable(MAPPING_PROFILES_TABLE, vertx, TENANT_ID));
  }
}
