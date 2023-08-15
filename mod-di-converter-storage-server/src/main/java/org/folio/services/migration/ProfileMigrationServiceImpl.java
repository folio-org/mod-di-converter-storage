package org.folio.services.migration;

import io.micrometer.core.instrument.util.StringUtils;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.dao.ProfileDao;
import org.folio.dao.association.ProfileWrapperDao;
import org.folio.dao.snapshot.ProfileSnapshotDao;
import org.folio.dao.snapshot.ProfileSnapshotItem;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.impl.util.OkapiConnectionParams;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileCollection;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ReactToType;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.association.CommonProfileAssociationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.lang.String.format;

@Service
public class ProfileMigrationServiceImpl implements ProfileMigrationService {
  private static final Logger LOGGER = LogManager.getLogger();
  private static final String UPDATE_WRAPPERS_INSIDE_ASSOCIATIONS = "templates/db_scripts/associations-migration/update_wrappers_inside_associations.sql";
  private static final String REVERT_VIEW = "templates/db_scripts/associations-migration/revert_associations_view.sql";
  private static final String UPDATE_SCHEMA_FOR_MIGRATION = "templates/db_scripts/associations-migration/actualize_schema_for_migrations.sql";
  private static final String TENANT_PLACEHOLDER = "${myuniversity}";
  private static final String MODULE_PLACEHOLDER = "${mymodule}";
  @Autowired
  protected PostgresClientFactory pgClientFactory;
  @Autowired
  private ProfileDao<JobProfile, JobProfileCollection> jobProfileDao;
  @Autowired
  private ProfileWrapperDao profileWrapperDao;
  @Autowired
  private CommonProfileAssociationService commonProfileAssociationService;
  @Autowired
  private ProfileSnapshotDao profileSnapshotDao;

  @Override
  public Future<Boolean> migrateDataImportProfiles(Map<String, String> headers, Context context) {
    Promise<Boolean> creationProfilesFuture = Promise.promise();
    String tenantId = new OkapiConnectionParams(headers).getTenantId();
    return profileWrapperDao.checkIfDataInTableExists(tenantId)
      .compose(e -> {
        if (!e) {
          LOGGER.info("Profile migration started...");
          return runScript(tenantId, REVERT_VIEW)
            .compose(y -> jobProfileDao.getTotalProfilesNumber(tenantId))
            .compose(x -> jobProfileDao.getProfiles(true, true, "cql.allRecords=1   ", 0, x, tenantId))
            .compose(f -> {
              List<Future<List<ProfileSnapshotItem>>> snapshotList = new ArrayList<>();
              for (JobProfile jobProfile : f.getJobProfiles()) {
                snapshotList.add(profileSnapshotDao.getSnapshotItems(jobProfile.getId(), ProfileSnapshotWrapper.ContentType.JOB_PROFILE, jobProfile.getId(), tenantId));
              }
              return wrapAndCreateProfileWrappers(creationProfilesFuture, tenantId, snapshotList);
            })
            .compose(t -> runScript(tenantId, UPDATE_SCHEMA_FOR_MIGRATION))
            .compose(r -> runScript(tenantId, UPDATE_WRAPPERS_INSIDE_ASSOCIATIONS));
        } else {
          LOGGER.info("Migration will not execute. profile_wrappers table is NOT empty already.");
          return Future.succeededFuture(true);
        }
      });
  }

  private Future<Boolean> wrapAndCreateProfileWrappers(Promise<Boolean> promise, String tenantId, List<Future<List<ProfileSnapshotItem>>> snapshotList) {
    GenericCompositeFuture.all(snapshotList).onComplete(ar -> {
      if (ar.succeeded()) {
        List<List<ProfileSnapshotItem>> snapshotItems;
        snapshotItems = ar.result().list();
        List<Future<List<ProfileAssociation>>> wrapFuture = new ArrayList<>();
        for (List<ProfileSnapshotItem> batchSnapshotItems : snapshotItems) {
          List<ProfileAssociation> associations = sortSnapshots(batchSnapshotItems);
          wrapFuture.add(commonProfileAssociationService.wrapAssociationProfiles(associations, new ArrayList<>(), new HashMap<>(), tenantId));
        }
        GenericCompositeFuture.all(wrapFuture).onComplete(r -> {
          if(r.succeeded()) {
            promise.complete();
          } else {
            promise.fail(r.cause());
          }
        });
      } else {
        promise.fail(format("Fail while migrating: %s ", ar.cause()));
      }
    });
    return promise.future();
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

  public List<ProfileAssociation> sortSnapshots(List<ProfileSnapshotItem> snapshotItems) {
    removeDuplicatesByAssociationId(snapshotItems);

    Optional<ProfileSnapshotItem> optionalRootItem = snapshotItems.stream().filter(item -> item.getMasterId() == null).findFirst();
    if (optionalRootItem.isPresent()) {
      ProfileSnapshotItem rootItem = optionalRootItem.get();
      List<ProfileAssociation> result = new ArrayList<>();
      ProfileAssociation rootAssociation = new ProfileAssociation();
      if (rootAssociation.getReactTo() != null) {
        rootAssociation.setReactTo(ReactToType.valueOf(rootItem.getReactTo().name()));
      }
      rootAssociation.setId(rootItem.getAssociationId());
      rootAssociation.setOrder(rootItem.getOrder());
      rootAssociation.setMasterProfileId(rootItem.getMasterId());
      rootAssociation.setDetailProfileId(rootItem.getDetailId());
      rootAssociation.setDetailProfileType(ProfileType.fromValue(rootItem.getDetailType().value()));
      result.add(rootAssociation);
      fillChildSnapshot(rootItem.getDetailId(), result, snapshotItems);
      return result;
    } else {
      throw new IllegalArgumentException("Can not find the root item in snapshot items list");
    }
  }

  private void fillChildSnapshot(String parentId, List<ProfileAssociation> associations, List<ProfileSnapshotItem> snapshotItems) {
    if (parentId != null) {
      for (ProfileSnapshotItem snapshotItem : snapshotItems) {
        if (parentId.equals(snapshotItem.getMasterId())) {
          ProfileAssociation childAssociation = new ProfileAssociation();
          if (childAssociation.getReactTo() != null) {
            childAssociation.setReactTo(ReactToType.valueOf(snapshotItem.getReactTo().name()));
          }
          childAssociation.setId(snapshotItem.getAssociationId());
          childAssociation.setOrder(snapshotItem.getOrder());
          childAssociation.setMasterProfileId(snapshotItem.getMasterId());
          childAssociation.setDetailProfileId(snapshotItem.getDetailId());
          childAssociation.setDetailProfileType(ProfileType.fromValue(snapshotItem.getDetailType().value()));

          associations.add(childAssociation);
          fillChildSnapshot(snapshotItem.getDetailId(), associations, snapshotItems);
        }
      }
    }
  }

  private void removeDuplicatesByAssociationId(List<ProfileSnapshotItem> snapshotItems) {
    Set<String> duplicates = new HashSet<>(snapshotItems.size());
    snapshotItems.removeIf(current -> !duplicates.add(current.getAssociationId()));
  }
}
