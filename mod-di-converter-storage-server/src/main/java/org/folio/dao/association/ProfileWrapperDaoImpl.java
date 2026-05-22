package org.folio.dao.association;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ProfileWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

@Repository
public class ProfileWrapperDaoImpl implements ProfileWrapperDao {
  private static final Logger LOGGER = LogManager.getLogger();
  private static final String TABLE_NAME = "profile_wrappers";
   private static final String INSERT_QUERY = "INSERT INTO %s.%s (id, profile_type, %s) VALUES ($1, $2, $3)";
  private static final String SELECT_ON_EMPTY_TABLE_QUERY = "SELECT EXISTS (SELECT * FROM %s.%s LIMIT 1)";
  private static final String SQL_LINES_COUNT = "select count(id) from %s.%s";
  private static final String SELECT_QUERY = "SELECT * FROM %s.%s WHERE id = $1";
  private static final String SELECT_QUERY_ON_GETTING_PROFILE_WRAPPER = "SELECT * FROM %s.%s WHERE %s = $1";
  private static final Map<String, String> profileTypeToColumn;

  static {
    profileTypeToColumn = new HashMap<>();
    profileTypeToColumn.put(ProfileType.ACTION_PROFILE.value(), "action_profile_id");
    profileTypeToColumn.put(ProfileType.MAPPING_PROFILE.value(), "mapping_profile_id");
    profileTypeToColumn.put(ProfileType.MATCH_PROFILE.value(), "match_profile_id");
    profileTypeToColumn.put(ProfileType.JOB_PROFILE.value(), "job_profile_id");
  }

  @Autowired
  protected PostgresClientFactory pgClientFactory;

  @Override
  public Future<Optional<ProfileWrapper>> getProfileWrapperById(String id, String tenantId) {
    String query = format(SELECT_QUERY, convertToPsqlStandard(tenantId), TABLE_NAME);
    Tuple queryParams = Tuple.of(id);
    return pgClientFactory.createInstance(tenantId)
      .execute(query, queryParams)
      .map(this::mapResultSetToOptionalProfileWrapper)
      .onFailure(e -> LOGGER.warn("getProfileWrapperById:: Error getting profile wrapper by id {}", id, e));
  }

  @Override
  public Future<String> save(ProfileWrapper entity, String tenantId) {
    LOGGER.trace("save:: Saving profile wrapper, tenant id {}", tenantId);
    if (entity.getProfileType() == null) {
      return Future.failedFuture("save:: Error saving profile wrapper - profile type is empty");
    }
    String query = format(INSERT_QUERY, convertToPsqlStandard(tenantId), TABLE_NAME, profileTypeToColumn.get(entity.getProfileType().value()));
    Tuple queryParams = Tuple.of(
      entity.getId(),
      entity.getProfileType(),
      entity.getProfileId());
    return pgClientFactory.createInstance(tenantId)
      .execute(query, queryParams)
      .map(entity.getId())
      .onFailure(e -> LOGGER.warn("save:: Error saving profile wrapper", e));
  }

  @Override
  public Future<Boolean> deleteById(String id, String tenantId) {
    return pgClientFactory.createInstance(tenantId)
      .delete(TABLE_NAME, id)
      .map(updateResult -> updateResult.rowCount() == 1)
      .onFailure(e -> LOGGER.warn("deleteById:: Error deleting profile wrapper by id {}", id, e));
  }

  @Override
  public Future<Boolean> checkIfDataInTableExists(String tenantId) {
    String query = format(SELECT_ON_EMPTY_TABLE_QUERY, convertToPsqlStandard(tenantId), TABLE_NAME);
    return pgClientFactory.createInstance(tenantId)
      .execute(query)
      .map(this::mapResultSetIfExists)
      .onFailure(e -> LOGGER.warn("checkIfDataInTableExists:: Error checking data existence in table {}", TABLE_NAME, e));
  }

  @Override
  public Future<Integer> getLinesCount(String tenantId, String tableName) {
    String query = format(SQL_LINES_COUNT, convertToPsqlStandard(tenantId), tableName);
    return pgClientFactory.createInstance(tenantId)
      .execute(query)
      .map(resultSet -> resultSet.iterator().next().getInteger(0))
      .onFailure(e -> LOGGER.warn("getLinesCount:: Error getting lines count in table {}", tableName, e));
  }

  @Override
  public Future<List<ProfileWrapper>> getWrapperByProfileId(String profileId, ProfileType profileType, String tenantId) {
    LOGGER.trace("get:: Getting profile wrapper, tenant id {}", tenantId);
    if (profileId == null) {
      return Future.failedFuture("get:: Getting profile wrapper - profile id is empty");
    }
    String query = format(SELECT_QUERY_ON_GETTING_PROFILE_WRAPPER, convertToPsqlStandard(tenantId),
      TABLE_NAME, profileTypeToColumn.get(profileType.value()));

    Tuple queryParams = Tuple.of(profileId);
    return pgClientFactory.createInstance(tenantId)
      .execute(query, queryParams)
      .map(this::mapResultSetToListProfileWrappers)
      .onFailure(e -> LOGGER.warn("get:: Error getting profile wrapper by profile id {}", profileId, e));
  }

  private Optional<ProfileWrapper> mapResultSetToOptionalProfileWrapper(RowSet<Row> resultSet) {
    RowIterator<Row> iterator = resultSet.iterator();
    return iterator.hasNext() ? Optional.of(mapRowToProfileWrapper(iterator.next())) : Optional.empty();
  }

  private Boolean mapResultSetIfExists(RowSet<Row> resultSet) {
    RowIterator<Row> iterator = resultSet.iterator();
    return iterator.hasNext() ? iterator.next().getBoolean("exists") : false;
  }

  private ProfileWrapper mapRowToProfileWrapper(Row row) {
    String profileId = "";
    if (!ObjectUtils.isEmpty(row.getValue("action_profile_id"))){
      profileId = row.getValue("action_profile_id").toString();
    }
    else if (!ObjectUtils.isEmpty(row.getValue("match_profile_id"))){
      profileId = row.getValue("match_profile_id").toString();
    }
    else if (!ObjectUtils.isEmpty(row.getValue("mapping_profile_id"))){
      profileId = row.getValue("mapping_profile_id").toString();
    }
    else if (!ObjectUtils.isEmpty(row.getValue("job_profile_id"))){
      profileId = row.getValue("job_profile_id").toString();
    }
    return new ProfileWrapper()
      .withId(row.getValue("id").toString())
      .withProfileId(profileId)
      .withProfileType(ProfileType.fromValue(row.getValue("profile_type").toString()));
  }

  private List<ProfileWrapper> mapResultSetToListProfileWrappers(RowSet<Row> resultSet) {
    List<ProfileWrapper> profileWrappers = new ArrayList<ProfileWrapper>();
    for (Row row : resultSet) {
      profileWrappers.add(mapRowToProfileWrapper(row));
    }
    return profileWrappers;
  }
}
