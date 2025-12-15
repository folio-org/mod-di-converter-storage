package org.folio.dao;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.NotFoundException;
import java.util.Optional;
import java.util.function.Function;

import static java.lang.String.format;
import static org.folio.dao.util.DaoUtil.constructCriteria;
import static org.folio.dao.util.DaoUtil.getCQLWrapper;

/**
 * Generic implementation of the {@link ProfileDao}
 *
 * @param <T> type of the entity
 * @param <S> type of the collection of T entities
 */
public abstract class AbstractProfileDao<T, S> implements ProfileDao<T, S> {

  private static final Logger logger = LogManager.getLogger();
  private static final String ID_FIELD = "'id'";

  @Autowired
  protected PostgresClientFactory pgClientFactory;
  public static final String IS_PROFILE_ASSOCIATED_AS_DETAIL_BY_ID_SQL = "SELECT exists (SELECT association_id FROM associations_view WHERE detail_id = '%s')";
  public static final String IS_PROFILE_EXIST_BY_NAME = "SELECT jsonb FROM %s WHERE trim(both ' ' from lower(jsonb ->> 'name')) = $1 AND jsonb ->> %s != $2 LIMIT 1;";

  @Override
  public Future<S> getProfiles(boolean showHidden, String query, int offset, int limit, String tenantId) {
    Promise<Results<T>> promise = Promise.promise();
    try {
      String[] fieldList = {"*"};
      CQLWrapper cql = getCQLWrapper(getTableName(), query, limit, offset);
      if (!showHidden) {
        var notHiddenProfilesFilter = "hidden==false";
        cql.addWrapper(getCQLWrapper(getTableName(), notHiddenProfilesFilter));
      }
      pgClientFactory.createInstance(tenantId).get(getTableName(), getProfileType(), fieldList, cql, true, false, result -> {
        if (result.succeeded()) {
          promise.complete(result.result());
        } else {
          promise.fail(result.cause());
        }
      });
    } catch (Exception e) {
      logger.warn("getProfiles:: Error while searching for {}", getProfileType().getSimpleName(), e);
      promise.fail(e);
    }
    return mapResultsToCollection(promise.future());
  }

  @Override
public Future<Optional<T>> getProfileById(String id, String tenantId) {
        Promise<Optional<T>> promise = Promise.promise();
        Criteria idCrit = constructCriteria(ID_FIELD, id);
        try {
          pgClientFactory.createInstance(tenantId).get(getTableName(), getProfileType(), new Criterion(idCrit), true, false, result -> {
            if (result.failed()) {
              logger.warn("getProfileById:: Error querying {} by id", getProfileType().getSimpleName(), result.cause());
              promise.fail(result.cause());
            } else {
              Results<T> rows = result.result();
              if (rows.getResults().isEmpty()) {
                logger.info("getProfileById:: {} with id {} was not found", getProfileType().getSimpleName(), id);
                promise.complete(Optional.empty());
              } else {
                promise.complete(Optional.of(rows.getResults().getFirst()));
              }
            }
          });
        } catch (Exception e) {
          logger.warn("getProfileById:: Error querying {} by id", getProfileType().getSimpleName(), e);
          promise.fail(e);
        }
        return promise.future();
      }

  @Override
  public Future<String> saveProfile(T profile, String tenantId) {
    try {
      return pgClientFactory.createInstance(tenantId).save(getTableName(), getProfileId(profile), profile);
    } catch (Exception e) {
      logger.error("saveProfile:: Failed to initiate save for profile of type {}", getProfileType(), e);
      return Future.failedFuture(e);
    }
  }

  @Override
  public Future<T> updateProfile(T profile, String tenantId) {
    Promise<T> promise = Promise.promise();
    String profileId = getProfileId(profile);
    String className = getProfileType().getSimpleName();
    try {
      Criteria idCrit = constructCriteria(ID_FIELD, getProfileId(profile));
      pgClientFactory.createInstance(tenantId).update(getTableName(), profile, new Criterion(idCrit), true, updateResult -> {
        if (updateResult.failed()) {
          logger.warn("updateProfile:: Could not update {} with id {}", className, profileId, updateResult.cause());
          promise.fail(updateResult.cause());
        } else if (updateResult.result().rowCount() != 1) {
          String errorMessage = format("updateProfile:: %s with id '%s' was not found", className, profileId);
          logger.warn(errorMessage);
          promise.fail(new NotFoundException(errorMessage));
        } else {
          promise.complete(profile);
        }
      });
    } catch (Exception e) {
      logger.warn("updateProfile:: Error updating {} with id {}", className, profileId, e);
      promise.fail(e);
    }
    return promise.future();
  }

  @Override
  public Future<T> updateBlocking(String profileId, Function<T, Future<T>> profileMutator, String tenantId) {
    try {
      PostgresClient pgClient = pgClientFactory.createInstance(tenantId);
      Criterion idCrit = new Criterion(constructCriteria(ID_FIELD, profileId));
      return pgClient.withTrans(conn ->
          conn.get(getTableName(), getProfileType(), idCrit, false)
            .compose(results -> {
              if (results.getResults().isEmpty()) {
                return Future.failedFuture(new NotFoundException(
                  format("%s with id '%s' was not found", getProfileType().getSimpleName(), profileId)));
              }
              return Future.succeededFuture(results.getResults().getFirst());
            })
            .compose(profileMutator)
            .compose(mutatedProfile -> updateProfile(conn, profileId, mutatedProfile))
        )
        .onFailure(throwable -> {
          String message = format("updateBlocking:: Error during %s update by id: %s", getProfileType().getSimpleName(), profileId);
          logger.warn(message, throwable);
        });

    } catch (Exception e) {
      String message = format("updateBlocking:: Failed to initiate update for profile with id: %s", profileId);
      logger.error(message, e);
      return Future.failedFuture(e);
    }
  }

  protected Future<T> updateProfile(Conn conn, String profileId, T profile) {
    try {
      Criterion idCrit = new Criterion(constructCriteria(ID_FIELD, profileId));
      return conn.update(getTableName(), profile, idCrit, true)
        .compose(updateResult -> {
          if (updateResult.rowCount() == 0) {
            String message = String.format("updateProfile:: %s with id '%s' not found for update.", getProfileType().getSimpleName(), profileId);
            logger.warn(message);
            return Future.failedFuture(new NotFoundException(message));
          }
          return Future.succeededFuture(profile);
        });
    } catch (Exception e) {
      logger.warn("updateProfile:: Error preparing to update {} by ID", getProfileType(), e);
      return Future.failedFuture(e);
    }
  }

  protected Future<Boolean> deleteProfile(PostgresClient pgClient, String profileId) {
    return pgClient.delete(getTableName(), profileId).map(true);
  }

  @Override
  public Future<Boolean> isProfileExistByName(String profileName, String profileId, String tenantId) {
    Promise<Boolean> promise = Promise.promise();
    PostgresClient client = pgClientFactory.createInstance(tenantId);
    String tableName = PostgresClient.convertToPsqlStandard(tenantId) + "." + getTableName();
    String sql = String.format(IS_PROFILE_EXIST_BY_NAME, tableName, ID_FIELD);
    Tuple tuple = Tuple.of(profileName.toLowerCase().trim(), String.valueOf(profileId));
    client.selectRead(sql, tuple, reply -> {
      if (reply.succeeded()) {
        promise.complete(reply.result().rowCount() > 0);
      } else {
        logger.warn("isProfileExistByName:: Error during counting profiles by its name. Profile name {}", profileName, reply.cause());
        promise.fail(reply.cause());
      }
    });
    return promise.future();
  }

  @Override
  public Future<Boolean> isProfileAssociatedAsDetail(String profileId, String tenantId) {
    Promise<Boolean> promise = Promise.promise();
    String preparedSql = format(IS_PROFILE_ASSOCIATED_AS_DETAIL_BY_ID_SQL, profileId);
    pgClientFactory.createInstance((tenantId)).select(preparedSql, selectAr -> {
      if (selectAr.succeeded()) {
        promise.complete(selectAr.result().iterator().next().getBoolean(0));
      } else {
        logger.warn("isProfileAssociatedAsDetail:: Error during retrieving associations for particular profile by its id. Profile id {}", profileId, selectAr.cause());
        promise.fail(selectAr.cause());
      }
    });
    return promise.future();
  }

  @Override
  public Future<Boolean> hardDeleteProfile(String profileId, String tenantId) {
    try {
      PostgresClient pgClient = pgClientFactory.createInstance(tenantId);
      Criterion idCrit = new Criterion(constructCriteria(ID_FIELD, profileId));
      return pgClient.get(getTableName(), getProfileType(), idCrit, false)
        .compose(results -> {
          if (results.getResults().isEmpty()) {
            return Future.failedFuture(new NotFoundException(
              format("%s with id '%s' was not found", getProfileType().getSimpleName(), profileId)));
          }
          return deleteProfile(pgClient, profileId);
        })
        .onFailure(throwable -> {
          String message = format("hardDeleteProfile:: Error during hard delete %s with id: %s", getProfileType().getSimpleName(), profileId);
          logger.warn(message, throwable);
        });
    } catch (Exception e) {
      String message = format("hardDeleteProfile:: Failed to initiate hard delete for profile with id: %s", profileId);
      logger.error(message, e);
      return Future.failedFuture(e);
    }
  }

  @Override
  public Future<Integer> getTotalProfilesNumber(String tenantId) {
    Promise<Integer> promise = Promise.promise();
    String totalCountSql = format("SELECT count(*) AS exact_count FROM %s;", getTableName());
    pgClientFactory.createInstance((tenantId)).select(totalCountSql, selectAr -> {
      if (selectAr.succeeded()) {
        promise.complete(selectAr.result().iterator().next().getInteger(0));
      } else {
        logger.warn("getTotalProfilesNumber:: Error during retrieving total count for profiles", selectAr.cause());
        promise.fail(selectAr.cause());
      }
    });
    return promise.future();
  }

  /**
   * Provides access to the table name
   *
   * @return table name
   */
  abstract String getTableName();

  /**
   * Maps results to S, a collection of T entities
   *
   * @param resultsFuture future parametrized by T Results
   * @return future with S, a collection of T entities
   */
  abstract Future<S> mapResultsToCollection(Future<Results<T>> resultsFuture);

  /**
   * Provides access to the Class of the Profile type
   *
   * @return Class
   */
  abstract Class<T> getProfileType();

  /**
   * Provides access to the profile id
   *
   * @param profile Profile
   * @return Profile id
   */
  abstract String getProfileId(T profile);
}
