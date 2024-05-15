package org.folio.dao.association;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileAssociationCollection;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ReactToType;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.ws.rs.NotFoundException;
import java.util.Optional;

import static java.lang.String.format;
import static org.folio.dao.util.DaoUtil.constructCriteria;
import static org.folio.dao.util.DaoUtil.getCQLWrapper;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

/**
 * Generic implementation of the of the {@link ProfileAssociationDao}
 */
@Repository
public class CommonProfileAssociationDao implements ProfileAssociationDao {
  private static final String ID_FIELD = "'id'";
  private static final String MASTER_WRAPPER_ID_FIELD = "master_wrapper_id";
  private static final String DETAIL_WRAPPER_ID_FIELD = "detail_wrapper_id";
  private static final String JOB_PROFILE_ID_FIELD = "jobProfileId";
  private static final String CRITERIA_BY_MASTER_ID_AND_DETAIL_ID_WHERE_CLAUSE =
    "WHERE (left(lower(%1$s.master_profile_id), 600) LIKE lower('%2$s')) " +
      "AND (lower(%1$s.detail_profile_id) LIKE lower('%3$s'))";
  private static final String CRITERIA_BY_REACT_TO_CLAUSE =
    "(lower(%1$s.react_to) LIKE lower('%2$s'))";

  private static final Logger LOGGER = LogManager.getLogger();
  private static final String ASSOCIATION_TABLE = "associations";
  private static final String INSERT_QUERY = "INSERT INTO %s.%s " +
    "(id, job_profile_id, master_wrapper_id, detail_wrapper_id, master_profile_id, detail_profile_id, " +
    "master_profile_type, detail_profile_type, detail_order, react_to) " +
    "VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)";
  private static final String SELECT_QUERY = "SELECT * FROM %s.%s WHERE id = $1";
  @Autowired
  protected PostgresClientFactory pgClientFactory;

  @Override
  public Future<String> save(ProfileAssociation entity, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();

    LOGGER.trace("save:: Saving profile association, tenant id {}, masterType {}, detailType {}",
      tenantId, entity.getMasterProfileType(), entity.getDetailProfileType());

    String query = format(INSERT_QUERY, convertToPsqlStandard(tenantId), ASSOCIATION_TABLE);
    Tuple queryParams = Tuple.of(
      entity.getId(),
      entity.getJobProfileId(),
      entity.getMasterWrapperId(),
      entity.getDetailWrapperId(),
      entity.getMasterProfileId(),
      entity.getDetailProfileId(),
      entity.getMasterProfileType(),
      entity.getDetailProfileType(),
      entity.getOrder(),
      entity.getReactTo());
    pgClientFactory.createInstance(tenantId).execute(query, queryParams, promise);
    return promise.future().map(entity.getId()).onFailure(e -> LOGGER.warn("save:: Error saving profile association", e));
  }

  @Override
  public Future<ProfileAssociationCollection> getAll(String tenantId) {
    Promise<Results<ProfileAssociation>> promise = Promise.promise();
    try {
      String[] fieldList = {"*"};
      pgClientFactory.createInstance(tenantId).get(ASSOCIATION_TABLE, ProfileAssociation.class, fieldList, null, true, promise);
    } catch (Exception e) {
      LOGGER.warn("getAll:: Error while searching for ProfileAssociations", e);
      promise.fail(e);
    }
    return promise.future().map(profileAssociationResults -> new ProfileAssociationCollection()
      .withProfileAssociations(profileAssociationResults.getResults())
      .withTotalRecords(profileAssociationResults.getResultInfo().getTotalRecords()));
  }

  @Override
  public Future<Optional<ProfileAssociation>> getById(String id, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();

    String query = format(SELECT_QUERY, convertToPsqlStandard(tenantId), ASSOCIATION_TABLE);
    Tuple queryParams = Tuple.of(id);
    pgClientFactory.createInstance(tenantId).execute(query, queryParams, promise);
    return promise.future().map(this::mapResultSetToOptionalProfileAssociation);
  }

  @Override
  public Future<ProfileAssociation> update(ProfileAssociation entity, String tenantId) {
    Promise<ProfileAssociation> promise = Promise.promise();
    try {
      Criteria idCrit = constructCriteria(ID_FIELD, entity.getId());
      pgClientFactory.createInstance(tenantId).update(ASSOCIATION_TABLE, entity, new Criterion(idCrit), true, updateResult -> {
        if (updateResult.failed()) {
          LOGGER.warn("update:: Could not update {} with id {}", ProfileAssociation.class, entity.getId(), updateResult.cause());
          promise.fail(updateResult.cause());
        } else if (updateResult.result().rowCount() != 1) {
          String errorMessage = format("update:: %s with id '%s' was not found", ProfileAssociation.class, entity.getId());
          LOGGER.warn(errorMessage);
          promise.fail(new NotFoundException(errorMessage));
        } else {
          promise.complete(entity);
        }
      });
    } catch (Exception e) {
      LOGGER.warn("update:: Error updating {} with id {}", ProfileAssociation.class, entity.getId(), e);
      promise.fail(e);
    }
    return promise.future();
  }

  @Override
  public Future<Boolean> delete(String id, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
    pgClientFactory.createInstance(tenantId).delete(ASSOCIATION_TABLE, id, promise);
    return promise.future().map(updateResult -> updateResult.rowCount() == 1);
  }

  @Override
  public Future<Boolean> delete(String masterWrapperId, String detailWrapperId, String jobProfileId,
                                ReactToType reactTo, Integer order, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
    try {
      CQLWrapper filter = getCQLWrapper(ASSOCIATION_TABLE,
        MASTER_WRAPPER_ID_FIELD + "==" + masterWrapperId + " AND " + DETAIL_WRAPPER_ID_FIELD + "==" + detailWrapperId
          + " AND (detail_order == " + (order == null ? 0 : order) + ")"
          + " AND (" + JOB_PROFILE_ID_FIELD + "==" + jobProfileId + " OR (cql.allRecords=1 NOT " + JOB_PROFILE_ID_FIELD + "=\"\"))");
      if (reactTo != null) {
        String whereClause = filter.getWhereClause()
          + " AND " + String.format(CRITERIA_BY_REACT_TO_CLAUSE, ASSOCIATION_TABLE, reactTo.value());
        filter.setWhereClause(whereClause);
      }
      pgClientFactory.createInstance(tenantId).delete(ASSOCIATION_TABLE, filter, promise);
    } catch (Exception e) {
      LOGGER.warn("delete:: Error deleting by master wrapper id {}, detail wrapper id {} and order {}",
        masterWrapperId, detailWrapperId, order, e);
      return Future.failedFuture(e);
    }
    return promise.future().map(updateResult -> updateResult.rowCount() == 1);
  }

  @Override
  public Future<Boolean> deleteByMasterWrapperId(String wrapperId, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
    try {
      CQLWrapper filter = getCQLWrapper(ASSOCIATION_TABLE, "(" + MASTER_WRAPPER_ID_FIELD + "==" + wrapperId + ")");
      pgClientFactory.createInstance(tenantId).delete(ASSOCIATION_TABLE, filter, promise);
    } catch (Exception e) {
      LOGGER.warn("deleteByMasterWrapperId:: Error deleting by master wrapper id {}", wrapperId, e);
      return Future.failedFuture(e);
    }
    return promise.future().map(updateResult -> updateResult.rowCount() > 0);
  }

  @Override
  public Future<Boolean> deleteByMasterIdAndDetailId(String masterId, String detailId, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
    try {
      /* Setting WHERE clause explicitly here because incorrect query is created by CQLWrapper by default due to
      presence of 2 definitions of mapping tables in schema.json and the query is generated based on outdated definition */
      CQLWrapper filter = new CQLWrapper().setWhereClause(String.format(CRITERIA_BY_MASTER_ID_AND_DETAIL_ID_WHERE_CLAUSE,
        ASSOCIATION_TABLE, masterId, detailId));
      pgClientFactory.createInstance(tenantId).delete(ASSOCIATION_TABLE, filter, promise);
    } catch (Exception e) {
      LOGGER.warn("deleteByMasterIdAndDetailId:: Error deleting by master id {} and detail id {}", masterId, detailId, e);
      return Future.failedFuture(e);
    }
    return promise.future().map(updateResult -> updateResult.rowCount() == 1);
  }

  private Optional<ProfileAssociation> mapResultSetToOptionalProfileAssociation(RowSet<Row> resultSet) {
    RowIterator<Row> iterator = resultSet.iterator();
    return iterator.hasNext() ? Optional.of(mapRowToProfileAssociation(iterator.next())) : Optional.empty();
  }

  private ProfileAssociation mapRowToProfileAssociation(Row row) {
    return new ProfileAssociation()
      .withId(safeGetString(row, "id"))
      .withJobProfileId(safeGetString(row, "job_profile_id"))
      .withMasterWrapperId(safeGetString(row, MASTER_WRAPPER_ID_FIELD))
      .withDetailWrapperId(safeGetString(row, DETAIL_WRAPPER_ID_FIELD))
      .withMasterProfileId(safeGetString(row, "master_profile_id"))
      .withDetailProfileId(safeGetString(row, "detail_profile_id"))
      .withMasterProfileType(safeGetProfileType(row, "master_profile_type"))
      .withDetailProfileType(safeGetProfileType(row, "detail_profile_type"))
      .withOrder(safeGetInteger(row, "detail_order"))
      .withReactTo(safeGetReactToType(row, "react_to"));
  }

  private String safeGetString(Row row, String columnName) {
    Object value = row.getValue(columnName);
    return value != null ? value.toString() : "";
  }

  private ProfileType safeGetProfileType(Row row, String columnName) {
    String value = safeGetString(row, columnName);
    return StringUtils.isNotEmpty(value) ? ProfileType.valueOf(value) : null;
  }

  private ReactToType safeGetReactToType(Row row, String columnName) {
    String value = safeGetString(row, columnName);
    return StringUtils.isNotEmpty(value) ? ReactToType.valueOf(value) : null;
  }

  private Integer safeGetInteger(Row row, String columnName) {
    String value = safeGetString(row, columnName);
    return StringUtils.isNotEmpty(value) ? Integer.valueOf(value) : null;
  }
}
