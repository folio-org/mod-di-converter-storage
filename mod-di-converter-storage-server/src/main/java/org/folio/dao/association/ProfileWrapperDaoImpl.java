package org.folio.dao.association;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ProfileWrapper;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.interfaces.Results;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.lang.String.format;
import static org.folio.dao.util.DaoUtil.constructCriteria;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;

@Repository
public class ProfileWrapperDaoImpl implements ProfileWrapperDao {
  private static final Logger LOGGER = LogManager.getLogger();
  private static final String ID_FIELD = "'id'";
  private static final String TABLE_NAME = "profile_wrappers";
  private static final String INSERT_QUERY = "INSERT INTO %s.%s (id, profile_type, %s) VALUES ($1, $2, $3)";
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
    Promise<Results<ProfileWrapper>> promise = Promise.promise();
    try {
      Criteria idCrit = constructCriteria(ID_FIELD, id);
      pgClientFactory.createInstance(tenantId).get(TABLE_NAME, ProfileWrapper.class, new Criterion(idCrit), true, false, promise);
    } catch (Exception e) {
      LOGGER.warn("getProfileWrapperById:: Error querying {} by id", ProfileWrapper.class.getSimpleName(), e);
      promise.fail(e);
    }
    return promise.future()
      .map(Results::getResults)
      .map(profileWrappers -> profileWrappers.isEmpty() ? Optional.empty() : Optional.of(profileWrappers.get(0)));
  }

  @Override
  public Future<String> save(ProfileWrapper entity, String tenantId) {
    Promise<RowSet<Row>> promise = Promise.promise();
    LOGGER.trace("save:: Saving profile wrapper, tenant id {}", tenantId);
    String query = format(INSERT_QUERY, convertToPsqlStandard(tenantId), TABLE_NAME, profileTypeToColumn.get(entity.getProfileType().value()));
    Tuple queryParams = Tuple.of(
      entity.getId(),
      entity.getProfileType(),
      entity.getProfileId());
    pgClientFactory.createInstance(tenantId).execute(query, queryParams, promise);
    return promise.future().map(entity.getId()).onFailure(e -> LOGGER.warn("save:: Error saving profile wrapper", e));
  }
}
