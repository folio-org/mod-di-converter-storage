package org.folio.services.migration;

import io.micrometer.core.instrument.util.StringUtils;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.dao.association.CommonProfileAssociationDao;
import org.folio.dao.association.ProfileWrapperDao;
import org.folio.rest.impl.util.OkapiConnectionParams;
import org.folio.rest.persist.PostgresClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static java.lang.String.format;

@Service
public class ProfileMigrationServiceImpl implements ProfileMigrationService {
  private static final Logger LOGGER = LogManager.getLogger();
  private static final String INIT_WRAPPERS = "templates/db_scripts/associations-migration/init_wrappers.sql";
  private static final String REMOVE_WRAPPERS = "templates/db_scripts/associations-migration/clean_profile_wrappers.sql";
  private static final String TENANT_PLACEHOLDER = "${myuniversity}";
  private static final String MODULE_PLACEHOLDER = "${mymodule}";
  private static final String SYSTEM_TABLE_NAME = "metadata_internal";
  private static final String ASSOCIATIONS_MIGRATION = "templates/db_scripts/associations-migration-v2/associations_migration.sql";

  @Autowired
  protected PostgresClientFactory pgClientFactory;
  @Autowired
  private ProfileWrapperDao profileWrapperDao;
  @Autowired
  private CommonProfileAssociationDao commonProfileAssociationDao;

  @Override
  public Future<Boolean> migrateDataImportProfiles(Map<String, String> headers, Context context) {
    String tenantId = new OkapiConnectionParams(headers).getTenantId();
    LOGGER.info("Profile migration started...");

    return profileWrapperDao.getLinesCount(tenantId, SYSTEM_TABLE_NAME)
      .compose(isRowCount -> {
        if (isRowCount == 0) {
          return profileWrapperDao.checkIfDataInTableExists(tenantId)
            .compose(isDataPresent -> processMigration(isDataPresent, tenantId));
        } else if (isRowCount == 1) {
          return commonProfileAssociationDao.checkIfDataInTableExists(tenantId).compose(isDataPresent -> {
            if (!isDataPresent) {
              LOGGER.info("migrateDataImportProfiles:: no associations found, creating associations for default profiles...");
              return runScript(tenantId, ASSOCIATIONS_MIGRATION);
            }
            return Future.succeededFuture();
          });
        } else {
          LOGGER.info("migrateDataImportProfiles:: Migration already executed.");
          return Future.succeededFuture(true);
        }
      })
      .onFailure(th -> LOGGER.error("migrateDataImportProfiles:: Something happened during the profile migration", th));
  }

  private Future<Boolean> processMigration(Boolean isDataPresent, String tenantId) {
    if (Boolean.TRUE.equals(isDataPresent)) {
      return runScriptChain(tenantId, true, REMOVE_WRAPPERS, INIT_WRAPPERS);
    } else {
      return runScriptChain(tenantId, false, INIT_WRAPPERS);
    }
  }

  private Future<Boolean> runScriptChain(String tenantId, Boolean isDataPresent, String... scripts) {
    Future<Boolean> future = Future.succeededFuture(isDataPresent);
    for (String script : scripts) {
      future = future.compose(ar -> runScript(tenantId, script));
    }
    return future;
  }

  private Future<Boolean> runScript(String tenantId, String sqlPath) {
    Promise<Boolean> promise = Promise.promise();

    InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(sqlPath);

    if (inputStream == null) {
      LOGGER.warn("Data will not be migrated: no resources found: {}", sqlPath);
      return Future.failedFuture("Data will not be migrated: no resources found: {}");
    }
    String sqlScript;
    try {
      sqlScript = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
    } catch (IOException e) {
      LOGGER.warn("Data will not be migrated: ", e);
      return Future.failedFuture(format("Data will not be migrated: no resources found: %s ", e));
    }
    if (StringUtils.isBlank(sqlScript)) {
      LOGGER.warn("Data will not be migrated: {} is empty", sqlPath);
      return Future.failedFuture(format("Data will not be migrated: %s is empty", sqlPath));
    }
    String moduleName = PostgresClient.getModuleName();
    sqlScript = sqlScript.replace(TENANT_PLACEHOLDER, tenantId).replace(MODULE_PLACEHOLDER, moduleName);
    pgClientFactory.createInstance(tenantId).runSQLFile(sqlScript, false)
      .onSuccess(e -> {
        LOGGER.info("{} successfully executed", sqlPath);
        promise.complete();
      })
      .onFailure(r -> {
        LOGGER.warn("Fail while executing {}", sqlPath);
        promise.fail(format("Fail while executing %s", sqlPath));
      });
    return promise.future();
  }
}
