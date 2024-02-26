package org.folio.services.migration;

import io.micrometer.core.instrument.util.StringUtils;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
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
  private static final String UPDATE_SCHEMA_FOR_MIGRATION = "templates/db_scripts/associations-migration/actualize_schema_for_migrations.sql";
  private static final String INIT_WRAPPERS = "templates/db_scripts/associations-migration/init_wrappers.sql";
  private static final String TENANT_PLACEHOLDER = "${myuniversity}";
  private static final String MODULE_PLACEHOLDER = "${mymodule}";
  @Autowired
  protected PostgresClientFactory pgClientFactory;
  @Autowired
  private ProfileWrapperDao profileWrapperDao;

  @Override
  public Future<Boolean> migrateDataImportProfiles(Map<String, String> headers, Context context) {
    String tenantId = new OkapiConnectionParams(headers).getTenantId();
    LOGGER.info("Profile migration started...");
    return profileWrapperDao.checkIfDataInTableExists(tenantId)
      .compose(isDataPresent -> {
        if (!isDataPresent) {
          return runScript(tenantId, INIT_WRAPPERS)
            .compose(ar -> runScript(tenantId, UPDATE_SCHEMA_FOR_MIGRATION));
        } else {
          LOGGER.info("migrateDataImportProfiles:: Migration will not execute. profile_wrappers table is NOT empty already.");
          return Future.succeededFuture(true);
        }
      })
      .onFailure(th -> LOGGER.error("migrateDataImportProfiles:: Something happened during the profile migration", th));
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
