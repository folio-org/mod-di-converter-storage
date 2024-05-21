package org.folio.dao.association;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileAssociationCollection;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ReactToType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.ws.rs.NotFoundException;
import java.util.Optional;

import static java.lang.String.format;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

/**
 * Generic implementation of the of the {@link ProfileAssociationDao}
 */
@Repository
public class CommonProfileAssociationDao implements ProfileAssociationDao {
  private static final String MASTER_WRAPPER_ID_FIELD = "master_wrapper_id";
  private static final String DETAIL_WRAPPER_ID_FIELD = "detail_wrapper_id";
  private static final String JOB_PROFILE_ID_FIELD = "job_profile_id";
  private static final Logger LOGGER = LogManager.getLogger();
  private static final String ASSOCIATION_TABLE = "associations";
  private static final String INSERT_QUERY = "INSERT INTO %s.%s " +
    "(id, job_profile_id, master_wrapper_id, detail_wrapper_id, master_profile_id, detail_profile_id, " +
    "master_profile_type, detail_profile_type, detail_order, react_to) " +
    "VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)";
  private static final String SELECT_BY_ID_QUERY = "SELECT * FROM %s.%s WHERE id = $1";
  private static final String SELECT_BY_MASTER_AND_DETAIL_TYPE_QUERY = "SELECT * FROM %s.%s " +
    "WHERE master_profile_type = $1 AND detail_profile_type = $2";
  private static final String DELETE_BY_MASTER_WRAPPER_ID_QUERY = "DELETE FROM %s.%s WHERE master_wrapper_id  = $1";
  private static final String DELETE_BY_MASTER_AND_DETAIL_PROFILES_IDS_QUERY = "DELETE FROM %s.%s " +
    "WHERE master_profile_id = $1 AND detail_profile_id = $2";
  private static final String DELETE_BY_IDS_QUERY = "DELETE FROM %s.%s WHERE master_wrapper_id = $1 " +
    "AND detail_wrapper_id = $2 " +
    "AND detail_order = COALESCE($3, 0) " +
    "AND (job_profile_id = $4 OR job_profile_id IS NULL)";
  private static final String CRITERIA_BY_REACT_TO_CLAUSE = " AND react_to = $5";
  private static final String UPDATE_QUERY = "UPDATE %s.%s " +
    " SET " +
    "    job_profile_id = $2, " +
    "    master_wrapper_id = $3, " +
    "    detail_wrapper_id = $4, " +
    "    master_profile_id = $5, " +
    "    detail_profile_id = $6, " +
    "    master_profile_type = $7, " +
    "    detail_profile_type = $8, " +
    "    detail_order = $9, " +
    "    react_to = $10 " +
    "WHERE id = $1;";

  @Autowired
  protected PostgresClientFactory pgClientFactory;

  @Override
  public Future<String> save(ProfileAssociation entity, String tenantId) {
    if (entity.getId() == null) entity.setId(UUID.randomUUID().toString());
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
  public Future<ProfileAssociationCollection> getAll(ProfileType masterType, ProfileType detailType, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
    // return by master profile type
    String query = format(SELECT_BY_MASTER_AND_DETAIL_TYPE_QUERY, convertToPsqlStandard(tenantId), ASSOCIATION_TABLE);
    Tuple queryParams = Tuple.of(masterType, detailType);
    pgClientFactory.createInstance(tenantId).execute(query, queryParams, result -> {
      if (result.failed()) {
        LOGGER.warn("getAll:: Error while searching for ProfileAssociations", result.cause());
        promise.fail(result.cause());
      } else {
        promise.complete(result.result());
      }
    });
    return promise.future().map(this::mapResultSetToProfileAssociationCollection);
  }

  @Override
  public Future<Optional<ProfileAssociation>> getById(String id, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();

    String query = format(SELECT_BY_ID_QUERY, convertToPsqlStandard(tenantId), ASSOCIATION_TABLE);
    Tuple queryParams = Tuple.of(getValidUUIDOrNull(id));
    pgClientFactory.createInstance(tenantId).execute(query, queryParams, promise);
    return promise.future().map(this::mapResultSetToOptionalProfileAssociation);
  }

  @Override
  public Future<ProfileAssociation> update(ProfileAssociation entity, ProfileType masterType, ProfileType detailType, String tenantId) {
    Promise<ProfileAssociation> promise = Promise.promise();
    try {
      String query = format(UPDATE_QUERY, convertToPsqlStandard(tenantId), ASSOCIATION_TABLE);
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
      pgClientFactory.createInstance(tenantId).execute(query, queryParams,  updateResult -> {
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
  public Future<Boolean> delete(String masterWrapperId, String detailWrapperId, ProfileType masterType,
                                ProfileType detailType, String jobProfileId, ReactToType reactTo, Integer order, String tenantId) {
    LOGGER.debug("delete : masterWrapperId={}, detailWrapperId={}, masterType={}, detailType={}",
      masterWrapperId, detailWrapperId, masterType.value(), detailType.value());
    Promise<RowSet<Row>> promise = Promise.promise();
    try {
      StringBuilder queryBuilder = new StringBuilder();
      queryBuilder.append(String.format(DELETE_BY_IDS_QUERY, convertToPsqlStandard(tenantId), ASSOCIATION_TABLE));

      Tuple queryParams = Tuple.of(getValidUUIDOrNull(masterWrapperId),
        getValidUUIDOrNull(detailWrapperId),
        order,
        getValidUUIDOrNull(jobProfileId));

      if (reactTo != null) {
        queryBuilder.append(CRITERIA_BY_REACT_TO_CLAUSE);
        queryParams.addValue(reactTo);
      }

      String query = queryBuilder.toString();
      pgClientFactory.createInstance(tenantId).execute(query, queryParams, promise);
    } catch (Exception e) {
      LOGGER.warn("delete:: Error deleting by master wrapper id {}, detail wrapper id {} and order {}",
        masterWrapperId, detailWrapperId, order, e);
      return Future.failedFuture(e);
    }
    return promise.future().map(updateResult -> updateResult.rowCount() == 1);
  }

  @Override
  public Future<Boolean> deleteByMasterWrapperId(String wrapperId, ProfileType masterType, ProfileType detailType, String tenantId) {
    LOGGER.debug("deleteByMasterWrapperId : wrapperId={}, masterType={}, detailType={}", wrapperId, masterType.value(), detailType.value());
    Promise<RowSet<Row>> promise = Promise.promise();
    try {
      String query = format(DELETE_BY_MASTER_WRAPPER_ID_QUERY, convertToPsqlStandard(tenantId), ASSOCIATION_TABLE);
      Tuple queryParams = Tuple.of(getValidUUIDOrNull(wrapperId));
      pgClientFactory.createInstance(tenantId).execute(query, queryParams, promise);
    } catch (Exception e) {
      LOGGER.warn("deleteByMasterWrapperId:: Error deleting by master wrapper id {}", wrapperId, e);
      return Future.failedFuture(e);
    }
    return promise.future().map(updateResult -> updateResult.rowCount() > 0);
  }

  @Override
  public Future<Boolean> deleteByMasterIdAndDetailId(String masterId, String detailId, ProfileType masterType,
                                                     ProfileType detailType, String tenantId) {
    LOGGER.debug("deleteByMasterIdAndDetailId : masterId={}, detailId={}, masterType={}, detailType={}",
      masterId, detailId, masterType.value(), detailType.value());
    Promise<RowSet<Row>> promise = Promise.promise();
    try {
      String query = format(DELETE_BY_MASTER_AND_DETAIL_PROFILES_IDS_QUERY, convertToPsqlStandard(tenantId), ASSOCIATION_TABLE);
      Tuple queryParams = Tuple.of(getValidUUIDOrNull(masterId), getValidUUIDOrNull(detailId));
      pgClientFactory.createInstance(tenantId).execute(query, queryParams, promise);
    } catch (Exception e) {
      LOGGER.warn("deleteByMasterIdAndDetailId:: Error deleting by master id {} and detail id {}", masterId, detailId, e);
      return Future.failedFuture(e);
    }
    return promise.future().map(updateResult -> updateResult.rowCount() == 1);
  }

  private UUID getValidUUIDOrNull(String input) {
    if (input == null) return null;
    try {
      return UUID.fromString(input);
    } catch (IllegalArgumentException ex) {
      LOGGER.debug("getValidUUIDOrNull:: Invalid UUID format for input: {}, returning null...", input);
      return null;
    }
  }

  private Optional<ProfileAssociation> mapResultSetToOptionalProfileAssociation(RowSet<Row> resultSet) {
    RowIterator<Row> iterator = resultSet.iterator();
    return iterator.hasNext() ? Optional.of(mapRowToProfileAssociation(iterator.next())) : Optional.empty();
  }

  private ProfileAssociationCollection mapResultSetToProfileAssociationCollection(RowSet<Row> resultSet) {
    List<ProfileAssociation> list = new ArrayList<>();
    resultSet.forEach(row -> list.add(mapRowToProfileAssociation(row)));

    return new ProfileAssociationCollection()
      .withProfileAssociations(list)
      .withTotalRecords(list.size());
  }


  private ProfileAssociation mapRowToProfileAssociation(Row row) {
    return new ProfileAssociation()
      .withId(safeGetString(row, "id"))
      .withJobProfileId(safeGetString(row, JOB_PROFILE_ID_FIELD))
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
