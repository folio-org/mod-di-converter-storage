package org.folio.dao.forms.configs;

import io.vertx.core.Future;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.jaxrs.model.FormConfig;
import org.folio.rest.jaxrs.model.FormConfigCollection;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.interfaces.Results;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.ws.rs.NotFoundException;
import java.util.Optional;
import java.util.UUID;

import static org.folio.dao.util.DaoUtil.constructCriteria;


@Repository
public class FormConfigDaoImpl implements FormConfigDao {

  public static final String TABLE_NAME = "forms_configs";
  private static final String FORM_NAME_FIELD = "'formName'";

  @Autowired
  private PostgresClientFactory pgClientFactory;

  @Override
  public Future<FormConfig> save(FormConfig formConfig, String tenantId) {
    formConfig.withId(UUID.randomUUID().toString());
    try {
      return pgClientFactory.createInstance(tenantId)
        .save(TABLE_NAME, formConfig.getId(), formConfig)
        .map(id -> formConfig);
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  @Override
  public Future<FormConfigCollection> getAll(String tenantId) {
    try {
      return pgClientFactory.createInstance(tenantId)
        .get(TABLE_NAME, FormConfig.class, new Criterion(new Criteria()), true)
        .map(results -> new FormConfigCollection()
          .withFormConfigs(results.getResults())
          .withTotalRecords(results.getResults().size()));
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  @Override
  public Future<Optional<FormConfig>> getByFormName(String formName, String tenantId) {
    try {
      Criteria formIdCriteria = constructCriteria(FORM_NAME_FIELD, formName);
      Criterion criterion = new Criterion(formIdCriteria);
      return pgClientFactory.createInstance(tenantId)
        .get(TABLE_NAME, FormConfig.class, criterion, true)
        .map(Results::getResults)
        .map(configsList -> configsList.stream().findFirst());
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  @Override
  public Future<FormConfig> updateByFormName(FormConfig formConfig, String tenantId) {
    Criteria formIdCriteria = constructCriteria(FORM_NAME_FIELD, formConfig.getFormName());
    return pgClientFactory.createInstance(tenantId)
      .update(TABLE_NAME, formConfig, new Criterion(formIdCriteria), true)
      .compose(updateResult -> updateResult.rowCount() == 1
        ? Future.succeededFuture(formConfig)
        : Future.failedFuture(new NotFoundException(String.format("FormConfig with formName '%s' was not found", formConfig.getFormName()))));
  }

  @Override
  public Future<Boolean> deleteByFormName(String formName, String tenantId) {
    Criteria formIdCriteria = constructCriteria(FORM_NAME_FIELD, formName);
    return pgClientFactory.createInstance(tenantId)
      .delete(TABLE_NAME, new Criterion(formIdCriteria))
      .map(deleteResult -> deleteResult.rowCount() == 1);
  }
}
