package org.folio.dao.fieldprotectionsettings;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.jaxrs.model.MarcFieldProtectionSetting;
import org.folio.rest.jaxrs.model.MarcFieldProtectionSettingsCollection;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.facets.FacetField;
import org.folio.rest.persist.interfaces.Results;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;

import static org.folio.dao.util.DaoUtil.constructCriteria;
import static org.folio.dao.util.DaoUtil.getCQLWrapper;

@Repository
public class MarcFieldProtectionSettingsDaoImpl implements MarcFieldProtectionSettingsDao {

  private static final Logger LOGGER = LogManager.getLogger();

  private static final String MARC_FIELDS_PROTECTION_SETTINGS_TABLE = "marc_field_protection_settings";
  private static final String ID_FIELD = "'id'";

  @Autowired
  private PostgresClientFactory pgClientFactory;

  @Override
  public Future<MarcFieldProtectionSettingsCollection> getAll(String query, int offset, int limit, String tenantId) {
    try {
      String[] fieldList = {"*"};
      CQLWrapper cql = getCQLWrapper(MARC_FIELDS_PROTECTION_SETTINGS_TABLE, query, limit, offset);
      return pgClientFactory.createInstance(tenantId)
        .get(MARC_FIELDS_PROTECTION_SETTINGS_TABLE, MarcFieldProtectionSetting.class, fieldList, cql,true,false, (List<FacetField>) null)
        .map(results -> new MarcFieldProtectionSettingsCollection()
          .withMarcFieldProtectionSettings(results.getResults())
          .withTotalRecords(results.getResultInfo().getTotalRecords()))
        .onFailure(e -> LOGGER.warn("getAll:: Error while searching for MarcFieldProtectionSettings", e));
    } catch (Exception e) {
      LOGGER.warn("getAll:: Error while searching for MarcFieldProtectionSettings", e);
      return Future.failedFuture(e);
    }
  }

  @Override
  public Future<Optional<MarcFieldProtectionSetting>> getById(String id, String tenantId) {
    Criteria crit = constructCriteria(ID_FIELD, id);
    try {
      return pgClientFactory.createInstance(tenantId)
        .get(MARC_FIELDS_PROTECTION_SETTINGS_TABLE, MarcFieldProtectionSetting.class, new Criterion(crit), true, false, (List<FacetField>) null)
        .map(Results::getResults)
        .map(settings -> settings.isEmpty() ? Optional.<MarcFieldProtectionSetting>empty() : Optional.of(settings.getFirst()))
        .onFailure(e -> LOGGER.warn("getById:: Error querying MarcFieldProtectionSetting by id {}", id, e));
    } catch (Exception e) {
      LOGGER.warn("getById:: Error querying MarcFieldProtectionSetting by id {}", id, e);
      return Future.failedFuture(e);
    }
  }

  @Override
  public Future<String> save(MarcFieldProtectionSetting marcFieldProtectionSetting, String tenantId) {
    return pgClientFactory.createInstance(tenantId)
      .save(MARC_FIELDS_PROTECTION_SETTINGS_TABLE, marcFieldProtectionSetting.getId(), marcFieldProtectionSetting);
  }

  @Override
  public Future<MarcFieldProtectionSetting> update(MarcFieldProtectionSetting marcFieldProtectionSetting, String tenantId) {
    Promise<MarcFieldProtectionSetting> promise = Promise.promise();
    try {
      Criteria idCrit = constructCriteria(ID_FIELD, marcFieldProtectionSetting.getId());
      pgClientFactory.createInstance(tenantId).update(MARC_FIELDS_PROTECTION_SETTINGS_TABLE, marcFieldProtectionSetting, new Criterion(idCrit), true, updateResult -> {
        if (updateResult.failed()) {
          LOGGER.warn("update:: Could not update MARC field protection setting with id {}", marcFieldProtectionSetting.getId(), updateResult.cause());
          promise.fail(updateResult.cause());
        } else if (updateResult.result().rowCount() != 1) {
          String errorMessage = String.format("update:: MARC field protection setting with id '%s' was not found", marcFieldProtectionSetting.getId());
          LOGGER.warn(errorMessage);
          promise.fail(new NotFoundException(errorMessage));
        } else {
          promise.complete(marcFieldProtectionSetting);
        }
      });
    } catch (Exception e) {
      LOGGER.warn("update:: Error updating MARC field protection setting", e);
      promise.fail(e);
    }
    return promise.future();
  }

  @Override
  public Future<Boolean> delete(String id, String tenantId) {
    return pgClientFactory.createInstance(tenantId)
      .delete(MARC_FIELDS_PROTECTION_SETTINGS_TABLE, id)
      .map(updateResult -> updateResult.rowCount() == 1)
      .onFailure(e -> LOGGER.warn("delete:: Error deleting MARC field protection setting with id {}", id, e));
  }
}

