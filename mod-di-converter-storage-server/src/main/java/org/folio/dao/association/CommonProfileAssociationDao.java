package org.folio.dao.association;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileAssociationCollection;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType;
import org.folio.rest.jaxrs.model.ReactToType;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static org.folio.dao.util.DaoUtil.constructCriteria;
import static org.folio.dao.util.DaoUtil.getCQLWrapper;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.MAPPING_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileSnapshotWrapper.ContentType.MATCH_PROFILE;

/**
 * Generic implementation of the of the {@link ProfileAssociationDao}
 */
@Repository
public class CommonProfileAssociationDao implements ProfileAssociationDao {
  private static final String ID_FIELD = "'id'";
  private static final String MASTER_WRAPPER_ID_FIELD = "masterWrapperId";
  private static final String DETAIL_WRAPPER_ID_FIELD = "detailWrapperId";
  private static final String JOB_PROFILE_ID_FIELD = "jobProfileId";
  private static final String CRITERIA_BY_MASTER_ID_AND_DETAIL_ID_WHERE_CLAUSE =
    "WHERE (left(lower(%1$s.jsonb->>'masterProfileId'),600) LIKE lower('%2$s')) " +
      "AND (lower(%1$s.jsonb->>'detailProfileId') LIKE lower('%3$s'))";
  private static final String CRITERIA_BY_REACT_TO_CLAUSE =
    "(lower(%1$s.jsonb->>'reactTo') LIKE lower('%2$s'))";
  private static final Logger LOGGER = LogManager.getLogger();
  private static final String CORRECT_PROFILE_ASSOCIATION_TYPES_MESSAGE = "Correct ProfileAssociation types: " +
    "ACTION_PROFILE_TO_ACTION_PROFILE, " +
    "ACTION_PROFILE_TO_MAPPING_PROFILE, " +
    "ACTION_PROFILE_TO_MATCH_PROFILE, " +
    "JOB_PROFILE_TO_ACTION_PROFILE, " +
    "JOB_PROFILE_TO_MATCH_PROFILE, " +
    "MATCH_PROFILE_TO_ACTION_PROFILE, " +
    "MATCH_PROFILE_TO_MATCH_PROFILE";
  private static final Map<String, String> associationTableNamesMap;
  @Autowired
  protected PostgresClientFactory pgClientFactory;

  static {
    associationTableNamesMap = new HashMap<>();
    associationTableNamesMap.put(ACTION_PROFILE.value() + ACTION_PROFILE.value(), "action_to_action_profiles");
    associationTableNamesMap.put(ACTION_PROFILE.value() + MAPPING_PROFILE.value(), "action_to_mapping_profiles");
    associationTableNamesMap.put(ACTION_PROFILE.value() + MATCH_PROFILE.value(), "action_to_match_profiles");
    associationTableNamesMap.put(JOB_PROFILE.value() + ACTION_PROFILE.value(), "job_to_action_profiles");
    associationTableNamesMap.put(JOB_PROFILE.value() + MATCH_PROFILE.value(), "job_to_match_profiles");
    associationTableNamesMap.put(MATCH_PROFILE.value() + ACTION_PROFILE.value(), "match_to_action_profiles");
    associationTableNamesMap.put(MATCH_PROFILE.value() + MATCH_PROFILE.value(), "match_to_match_profiles");
  }

  @Override
  public Future<String> save(ProfileAssociation entity, ContentType masterType, ContentType detailType, String tenantId) {
    Promise<String> promise = Promise.promise();
    pgClientFactory.createInstance(tenantId).save(getAssociationTableName(masterType, detailType), entity.getId(), entity, promise);
    return promise.future();
  }

  @Override
  public Future<ProfileAssociationCollection> getAll(ContentType masterType, ContentType detailType, String tenantId) {
    Promise<Results<ProfileAssociation>> promise = Promise.promise();
    try {
      String[] fieldList = {"*"};
      pgClientFactory.createInstance(tenantId).get(getAssociationTableName(masterType, detailType), ProfileAssociation.class, fieldList, null, true, promise);
    } catch (Exception e) {
      LOGGER.warn("getAll:: Error while searching for ProfileAssociations", e);
      promise.fail(e);
    }
    return promise.future().map(profileAssociationResults -> new ProfileAssociationCollection()
      .withProfileAssociations(profileAssociationResults.getResults())
      .withTotalRecords(profileAssociationResults.getResultInfo().getTotalRecords()));
  }

  @Override
  public Future<Optional<ProfileAssociation>> getById(String id, ContentType masterType, ContentType detailType, String tenantId) {
    Promise<Results<ProfileAssociation>> promise = Promise.promise();
    try {
      Criteria idCrit = constructCriteria(ID_FIELD, id);
      pgClientFactory.createInstance(tenantId).get(getAssociationTableName(masterType, detailType), ProfileAssociation.class, new Criterion(idCrit), true, false, promise);
    } catch (Exception e) {
      LOGGER.warn("getById:: Error querying {} by id", ProfileAssociation.class.getSimpleName(), e);
      promise.fail(e);
    }
    return promise.future()
      .map(Results::getResults)
      .map(profiles -> profiles.isEmpty() ? Optional.empty() : Optional.of(profiles.get(0)));
  }

  @Override
  public Future<ProfileAssociation> update(ProfileAssociation entity, ProfileSnapshotWrapper.ContentType masterType, ProfileSnapshotWrapper.ContentType detailType, String tenantId) {
    Promise<ProfileAssociation> promise = Promise.promise();
    try {
      Criteria idCrit = constructCriteria(ID_FIELD, entity.getId());
      pgClientFactory.createInstance(tenantId).update(getAssociationTableName(masterType, detailType), entity, new Criterion(idCrit), true, updateResult -> {
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
  public Future<Boolean> delete(String id, ProfileSnapshotWrapper.ContentType masterType, ProfileSnapshotWrapper.ContentType detailType, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
    pgClientFactory.createInstance(tenantId).delete(getAssociationTableName(masterType, detailType), id, promise);
    return promise.future().map(updateResult -> updateResult.rowCount() == 1);
  }

  @Override
  public Future<Boolean> delete(String masterWrapperId, String detailWrapperId, ProfileSnapshotWrapper.ContentType masterType,
                                ProfileSnapshotWrapper.ContentType detailType, String jobProfileId, ReactToType reactTo, Integer order, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
    try {
      CQLWrapper filter = getCQLWrapper(getAssociationTableName(masterType, detailType),
        MASTER_WRAPPER_ID_FIELD + "==" + masterWrapperId + " AND " + DETAIL_WRAPPER_ID_FIELD + "==" + detailWrapperId
          + " AND (order == " + (order == null ? 0 : order) + ")"
          + " AND (" + JOB_PROFILE_ID_FIELD + "==" + jobProfileId + " OR (cql.allRecords=1 NOT " + JOB_PROFILE_ID_FIELD + "=\"\"))");
      if (reactTo != null) {
        String whereClause = filter.getWhereClause()
          + " AND " + String.format(CRITERIA_BY_REACT_TO_CLAUSE, getAssociationTableName(masterType, detailType), reactTo.value());
        filter.setWhereClause(whereClause);
      }
      pgClientFactory.createInstance(tenantId).delete(getAssociationTableName(masterType, detailType), filter, promise);
    } catch (Exception e) {
      LOGGER.warn("delete:: Error deleting by master wrapper id {}, detail wrapper id {}, with reactTo {} and order {}",
        masterWrapperId, detailWrapperId, (reactTo != null ? reactTo.value() : null) , order, e);
      return Future.failedFuture(e);
    }
    return promise.future().map(updateResult -> updateResult.rowCount() == 1);
  }

  @Override
  public Future<Boolean> deleteByMasterWrapperId(String wrapperId, ProfileSnapshotWrapper.ContentType masterType, ProfileSnapshotWrapper.ContentType detailType, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
    try {
      CQLWrapper filter = getCQLWrapper(getAssociationTableName(masterType, detailType), "(" + MASTER_WRAPPER_ID_FIELD + "==" + wrapperId + ")");
      pgClientFactory.createInstance(tenantId).delete(getAssociationTableName(masterType, detailType), filter, promise);
    } catch (Exception e) {
      LOGGER.warn("deleteByMasterWrapperId:: Error deleting by master wrapper id {}", wrapperId, e);
      return Future.failedFuture(e);
    }
    return promise.future().map(updateResult -> updateResult.rowCount() > 0);
  }

  @Override
  public Future<Boolean> deleteByMasterIdAndDetailId(String masterId, String detailId, ContentType masterType,
                                                     ContentType detailType, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
    try {
      /* Setting WHERE clause explicitly here because incorrect query is created by CQLWrapper by default due to
      presence of 2 definitions of mapping tables in schema.json and the query is generated based on outdated definition */
      CQLWrapper filter = new CQLWrapper().setWhereClause(String.format(CRITERIA_BY_MASTER_ID_AND_DETAIL_ID_WHERE_CLAUSE,
        getAssociationTableName(masterType, detailType), masterId, detailId));
      pgClientFactory.createInstance(tenantId).delete(getAssociationTableName(masterType, detailType), filter, promise);
    } catch (Exception e) {
      LOGGER.warn("deleteByMasterIdAndDetailId:: Error deleting by master id {} and detail id {}", masterId, detailId, e);
      return Future.failedFuture(e);
    }
    return promise.future().map(updateResult -> updateResult.rowCount() == 1);
  }

  /**
   * Returns association table name by masterType and detailType
   *
   * @param masterType a master type in association
   * @param detailType a detail type in association
   * @return table name
   */
  private String getAssociationTableName(ContentType masterType, ContentType detailType) {
    String associationTableName = associationTableNamesMap.get(masterType.value() + detailType.value());
    if (associationTableName == null) {
      String message = format("Invalid ProfileAssociation type with master type '%s' and detail type '%s'. ", masterType, detailType);
      LOGGER.warn(message);
      throw new BadRequestException(CORRECT_PROFILE_ASSOCIATION_TYPES_MESSAGE);
    }
    return associationTableName;
  }
}
