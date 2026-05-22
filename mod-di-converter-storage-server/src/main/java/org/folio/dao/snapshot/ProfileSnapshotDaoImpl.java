package org.folio.dao.snapshot;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.PostgresClientFactory;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ReactToType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation for Profile snapshot DAO
 */
@Repository
public class ProfileSnapshotDaoImpl implements ProfileSnapshotDao {
  private static final Logger logger = LogManager.getLogger();
  private static final String TABLE_NAME = "profile_snapshots";
  private static final String GET_PROFILE_SNAPSHOT = "select get_profile_snapshot('%s', '%s', '%s', '%s');";
  protected PostgresClientFactory pgClientFactory;

  public ProfileSnapshotDaoImpl(@Autowired PostgresClientFactory pgClientFactory) {
    this.pgClientFactory = pgClientFactory;
  }

  @Override
  public Future<Optional<ProfileSnapshotWrapper>> getById(String id, String tenantId) {
    try {
      return pgClientFactory.createInstance(tenantId)
        .getById(TABLE_NAME, id, ProfileSnapshotWrapper.class)
        .map(Optional::ofNullable)
        .onFailure(e -> logger.warn("getById:: Error querying {} by id", ProfileSnapshotWrapper.class.getSimpleName(), e));
    } catch (Exception e) {
      logger.warn("getById:: Error querying {} by id", ProfileSnapshotWrapper.class.getSimpleName(), e);
      return Future.failedFuture(e);
    }
  }

  @Override
  public Future<String> save(ProfileSnapshotWrapper entity, String tenantId) {
    try {
      return pgClientFactory.createInstance(tenantId)
        .save(TABLE_NAME, entity.getId(), entity)
        .onFailure(e -> logger.warn("save:: Error saving {} with id {}", ProfileSnapshotWrapper.class.getSimpleName(), entity.getId(), e));
    } catch (Exception e) {
      logger.warn("save:: Error saving {} with id {}", ProfileSnapshotWrapper.class.getSimpleName(), entity.getId(), e);
      return Future.failedFuture(e);
    }
  }

  public Future<List<ProfileAssociation>> getSnapshotAssociations(String profileId, ProfileType profileType, String jobProfileId, String tenantId) {

    try {
      SnapshotProfileType snapshotProfileType = SnapshotProfileType.valueOf(profileType.value());
      String createSnapshotQuery = String.format(GET_PROFILE_SNAPSHOT, profileId, profileType.value(), snapshotProfileType.getTableName(), jobProfileId);
      return pgClientFactory.createInstance(tenantId).select(createSnapshotQuery)
        .map(rows -> {
          List<ProfileAssociation> snapshotAssociations = new ArrayList<>();
          rows.forEach(row -> {
            JsonObject jsonItem = row.get(JsonObject.class, 0);
            ProfileAssociation snapshotAssociation = new ProfileAssociation();
            snapshotAssociation.setId(jsonItem.getString("association_id"));
            snapshotAssociation.setMasterProfileId(jsonItem.getString("master_id"));
            snapshotAssociation.setDetailProfileId(jsonItem.getString("detail_id"));
            snapshotAssociation.setMasterWrapperId(jsonItem.getString("masterwrapperid"));
            snapshotAssociation.setDetailWrapperId(jsonItem.getString("detailwrapperid"));
            snapshotAssociation.setDetailProfileType(ProfileType.fromValue(jsonItem.getString("detail_type")));
            snapshotAssociation.setOrder(jsonItem.getInteger("detail_order"));
            snapshotAssociation.setDetail(jsonItem.getJsonArray("detail").getList().getFirst());
            snapshotAssociation.setJobProfileId(jsonItem.getString("job_profile_id"));
            if (StringUtils.isNotEmpty(jsonItem.getString("react_to"))) {
              snapshotAssociation.setReactTo(ReactToType.fromValue(jsonItem.getString("react_to")));
            }
            if (StringUtils.isNotEmpty(jsonItem.getString("master_type"))) {
              snapshotAssociation.setMasterProfileType(ProfileType.fromValue(jsonItem.getString("master_type")));
            }
            snapshotAssociations.add(snapshotAssociation);
          });
          return snapshotAssociations;
        })
        .onFailure(e -> logger.warn("getSnapshotItems:: Error while getSnapshotItems", e));
    } catch (Exception e) {
      logger.warn("getSnapshotItems:: Error while getSnapshotItems", e);
      return Future.failedFuture(e);
    }
  }
}
