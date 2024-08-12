package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.impl.util.ExceptionHelper;
import org.folio.rest.impl.util.OkapiConnectionParams;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileCollection;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileCollection;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileCollection;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.MatchProfileCollection;
import org.folio.rest.jaxrs.model.MatchProfileUpdateDto;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.resource.DataImportProfiles;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.ProfileService;
import org.folio.services.association.ProfileAssociationService;
import org.folio.services.snapshot.ProfileSnapshotService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static java.lang.String.format;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.rest.RestVerticle.OKAPI_USERID_HEADER;

public class DataImportProfilesImpl implements DataImportProfiles {

  private static final Logger logger = LogManager.getLogger();
  private static final String MASTER_PROFILE_NOT_FOUND_MSG = "Master profile with id '%s' was not found";
  private static final String DETAIL_PROFILE_NOT_FOUND_MSG = "Detail profile with id '%s' was not found";

  @Autowired
  private ProfileService<JobProfile, JobProfileCollection, JobProfileUpdateDto> jobProfileService;
  @Autowired
  private ProfileService<MatchProfile, MatchProfileCollection, MatchProfileUpdateDto> matchProfileService;
  @Autowired
  private ProfileService<ActionProfile, ActionProfileCollection, ActionProfileUpdateDto> actionProfileService;
  @Autowired
  private ProfileService<MappingProfile, MappingProfileCollection, MappingProfileUpdateDto> mappingProfileService;
  @Autowired
  private ProfileAssociationService profileAssociationService;
  @Autowired
  private ProfileSnapshotService profileSnapshotService;

  private String tenantId;

  public DataImportProfilesImpl(Vertx vertx, String tenantId) { //NOSONAR
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
    this.tenantId = TenantTool.calculateTenantId(tenantId);
  }

  @Override
  public void postDataImportProfilesJobProfiles(JobProfileUpdateDto entity, Map<String, String> okapiHeaders,
                                                Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        entity.getProfile().setMetadata(getMetadata(okapiHeaders));
        jobProfileService.saveProfile(entity, new OkapiConnectionParams(okapiHeaders))
          .map(profile -> (Response) PostDataImportProfilesJobProfilesResponse
            .respond201WithApplicationJson(entity.withProfile(profile).withId(profile.getId()), PostDataImportProfilesJobProfilesResponse.headersFor201()))
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("postDataImportProfilesJobProfiles:: Failed to create Job Profile", e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void getDataImportProfilesJobProfiles(boolean showHidden, boolean withRelations,
                                               String query, String totalRecords, int offset, int limit,
                                               Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        jobProfileService.getProfiles(withRelations, showHidden, query, offset, limit, tenantId)
          .map(GetDataImportProfilesJobProfilesResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("getDataImportProfilesJobProfiles:: Failed to get Job Profiles");
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void putDataImportProfilesJobProfilesById(String id, JobProfileUpdateDto entity, Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        entity.getProfile().setMetadata(getMetadata(okapiHeaders));
        entity.getProfile().setId(id);
        jobProfileService.updateProfile(entity, new OkapiConnectionParams(okapiHeaders))
          .map(PutDataImportProfilesJobProfilesByIdResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("putDataImportProfilesJobProfilesById:: Failed to update Job Profile with id {}", id, e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void getDataImportProfilesJobProfilesById(String id, boolean withRelations, Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(c -> {
      try {
        jobProfileService.getProfileById(id, withRelations, tenantId)
          .map(optionalProfile -> optionalProfile.orElseThrow(() ->
            new NotFoundException(format("Job Profile with id '%s' was not found", id))))
          .map(GetDataImportProfilesJobProfilesByIdResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("getDataImportProfilesJobProfilesById:: Failed to get Job Profile by id {}", id, e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void deleteDataImportProfilesJobProfilesById(String id, Map<String, String> okapiHeaders,
                                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        OkapiConnectionParams params = new OkapiConnectionParams(okapiHeaders);
        jobProfileService.hardDeleteProfile(id, params.getTenantId())
          .map(DeleteDataImportProfilesJobProfilesByIdResponse.respond204WithTextPlain(
            format("Job Profile with id '%s' was successfully deleted", id)))
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("deleteDataImportProfilesJobProfilesById:: Failed to delete Job Profile with id {}", id, e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void postDataImportProfilesMatchProfiles(MatchProfileUpdateDto entity, Map<String, String> okapiHeaders,
                                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        entity.getProfile().setMetadata(getMetadata(okapiHeaders));
        matchProfileService.saveProfile(entity, new OkapiConnectionParams(okapiHeaders))
          .map(profile -> (Response) PostDataImportProfilesMatchProfilesResponse
            .respond201WithApplicationJson(entity.withProfile(profile).withId(profile.getId()), PostDataImportProfilesMatchProfilesResponse.headersFor201()))
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("postDataImportProfilesMatchProfiles:: Failed to create Match Profile", e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void getDataImportProfilesMatchProfiles(boolean showHidden, boolean withRelations,
                                                 String query, String totalRecords, int offset, int limit,
                                                 Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                                 Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        matchProfileService.getProfiles(withRelations, showHidden, query, offset, limit, tenantId)
          .map(GetDataImportProfilesMatchProfilesResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("getDataImportProfilesMatchProfiles:: Failed to get Match Profiles");
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void putDataImportProfilesMatchProfilesById(String id, MatchProfileUpdateDto entity, Map<String, String> okapiHeaders,
                                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        entity.getProfile().setMetadata(getMetadata(okapiHeaders));
        entity.getProfile().setId(id);
        matchProfileService.updateProfile(entity, new OkapiConnectionParams(okapiHeaders))
          .map(PutDataImportProfilesMatchProfilesByIdResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("putDataImportProfilesMatchProfilesById:: Failed to update Match Profile with id {}", id, e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void getDataImportProfilesMatchProfilesById(String id, boolean withRelations, Map<String, String> okapiHeaders,
                                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(c -> {
      try {
        matchProfileService.getProfileById(id, withRelations, tenantId)
          .map(optionalProfile -> optionalProfile.orElseThrow(() ->
            new NotFoundException(format("Match Profile with id '%s' was not found", id))))
          .map(GetDataImportProfilesMatchProfilesByIdResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("getDataImportProfilesMatchProfilesById:: Failed to get Match Profile by id {}", id, e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void postDataImportProfilesMappingProfiles(MappingProfileUpdateDto entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        entity.getProfile().setMetadata(getMetadata(okapiHeaders));
        mappingProfileService.saveProfile(entity, new OkapiConnectionParams(okapiHeaders))
          .map(profile -> (Response) PostDataImportProfilesMappingProfilesResponse
            .respond201WithApplicationJson(entity.withProfile(profile).withId(profile.getId()), PostDataImportProfilesMappingProfilesResponse.headersFor201()))
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("postDataImportProfilesMappingProfiles:: Failed to create Mapping Profile", e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void getDataImportProfilesMappingProfiles(boolean showHidden, boolean withRelations,
                                                   String query, String totalRecords, int offset, int limit,
                                                   Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                                   Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        mappingProfileService.getProfiles(withRelations, showHidden, query, offset, limit, tenantId)
          .map(GetDataImportProfilesMappingProfilesResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("getDataImportProfilesMappingProfiles:: Failed to get Mapping Profiles");
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void putDataImportProfilesMappingProfilesById(String id, MappingProfileUpdateDto entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        entity.getProfile().setMetadata(getMetadata(okapiHeaders));
        entity.getProfile().setId(id);

        mappingProfileService.updateProfile(entity, new OkapiConnectionParams(okapiHeaders))
          .map(PutDataImportProfilesMappingProfilesByIdResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse).onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("putDataImportProfilesMappingProfilesById:: Failed to update Mapping Profile with id {}", id, e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void deleteDataImportProfilesMappingProfilesById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        OkapiConnectionParams params = new OkapiConnectionParams(okapiHeaders);
        mappingProfileService.hardDeleteProfile(id, params.getTenantId())
          .map(DeleteDataImportProfilesMappingProfilesByIdResponse.respond204WithTextPlain(
            format("Mapping Profile with id '%s' was successfully deleted", id)))
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("deleteDataImportProfilesMappingProfilesById:: Failed to delete Mapping Profile with id {}", id, e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void getDataImportProfilesMappingProfilesById(String id, boolean withRelations, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(c -> {
      try {
        mappingProfileService.getProfileById(id, withRelations, tenantId)
          .map(optionalProfile -> optionalProfile.orElseThrow(() ->
            new NotFoundException(format("Mapping Profile with id '%s' was not found", id))))
          .map(GetDataImportProfilesMappingProfilesByIdResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("getDataImportProfilesMappingProfilesById:: Failed to get Mapping Profile by id {}", id, e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void deleteDataImportProfilesMatchProfilesById(String id, Map<String, String> okapiHeaders,
                                                        Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        OkapiConnectionParams params = new OkapiConnectionParams(okapiHeaders);
        matchProfileService.hardDeleteProfile(id, params.getTenantId())
          .map(DeleteDataImportProfilesMatchProfilesByIdResponse.respond204WithTextPlain(
            format("Match Profile with id '%s' was successfully deleted", id)))
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);

      } catch (Exception e) {
        logger.warn("deleteDataImportProfilesMatchProfilesById:: Failed to delete Match Profile with id {}", id, e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void postDataImportProfilesActionProfiles(ActionProfileUpdateDto entity, Map<String, String> okapiHeaders,
                                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {

        actionProfileService.saveProfile(entity, new OkapiConnectionParams(okapiHeaders))
          .map(profile -> (Response) PostDataImportProfilesActionProfilesResponse
            .respond201WithApplicationJson(entity.withProfile(profile).withId(profile.getId()), PostDataImportProfilesActionProfilesResponse.headersFor201()))
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("postDataImportProfilesActionProfiles:: Failed to create Action Profile", e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void getDataImportProfilesActionProfiles(boolean showHidden, boolean withRelations,
                                                  String query, String totalRecords, int offset, int limit,
                                                  Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                                  Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        actionProfileService.getProfiles(withRelations, showHidden, query, offset, limit, tenantId)
          .map(GetDataImportProfilesActionProfilesResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("getDataImportProfilesActionProfiles:: Failed to get Action Profiles");
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void putDataImportProfilesActionProfilesById(String id, ActionProfileUpdateDto entity, Map<String, String> okapiHeaders,
                                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        entity.getProfile().setMetadata(getMetadata(okapiHeaders));
        entity.getProfile().setId(id);
        actionProfileService.updateProfile(entity, new OkapiConnectionParams(okapiHeaders))
          .map(PutDataImportProfilesActionProfilesByIdResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse).onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("putDataImportProfilesActionProfilesById:: Failed to update Action Profile with id {}", id, e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void getDataImportProfilesActionProfilesById(String id, boolean withRelations, Map<String, String> okapiHeaders,
                                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(c -> {
      try {
        actionProfileService.getProfileById(id, withRelations, tenantId)
          .map(optionalProfile -> optionalProfile.orElseThrow(() ->
            new NotFoundException(format("Action Profile with id '%s' was not found", id))))
          .map(GetDataImportProfilesActionProfilesByIdResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("getDataImportProfilesActionProfilesById:: Failed to get Action Profile by id {}", id, e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void postDataImportProfilesProfileAssociations(String master, String detail, ProfileAssociation entity, Map<String, String> okapiHeaders,
                                                        Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        profileAssociationService.save(entity, tenantId)
          .map((Response) PostDataImportProfilesProfileAssociationsResponse
            .respond201WithApplicationJson(entity, PostDataImportProfilesProfileAssociationsResponse.headersFor201()))
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("postDataImportProfilesProfileAssociations:: Failed to create Profile association", e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void getDataImportProfilesProfileAssociations(String master, String detail, Map<String, String> okapiHeaders,
                                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
        try {
          OkapiConnectionParams params = new OkapiConnectionParams(okapiHeaders);
          profileAssociationService.getAll(mapContentType(master), mapContentType(detail), params.getTenantId())
            .map(GetDataImportProfilesProfileAssociationsResponse::respond200WithApplicationJson)
            .map(Response.class::cast)
            .otherwise(ExceptionHelper::mapExceptionToResponse)
            .onComplete(asyncResultHandler);
        } catch (Exception e) {
          logger.warn("getDataImportProfilesProfileAssociations:: Failed to get ProfileAssociations by masterType '{}' and detailType '{}", master, detail, e);
          asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
        }
      }
    );
  }

  @Override
  public void putDataImportProfilesProfileAssociationsById(String id, String master, String detail, ProfileAssociation entity,
                                                           Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        entity.setId(id);
        profileAssociationService.update(entity, mapContentType(master), mapContentType(detail), new OkapiConnectionParams(okapiHeaders))
          .map(updatedEntity -> (Response) PutDataImportProfilesProfileAssociationsByIdResponse.respond200WithApplicationJson(updatedEntity))
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("putDataImportProfilesProfileAssociationsById:: Failed to update Profile association with id {}", id, e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void deleteDataImportProfilesProfileAssociationsById(String id, String master, String detail, Map<String, String> okapiHeaders,
                                                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        profileAssociationService.delete(id, tenantId)
          .map(deleted -> deleted
            ? DeleteDataImportProfilesProfileAssociationsByIdResponse.respond204WithTextPlain(
            format("Profile association with id '%s' was successfully deleted", id))
            : DeleteDataImportProfilesProfileAssociationsByIdResponse.respond404WithTextPlain(
            format("Profile association with id '%s' was not found", id)))
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("deleteDataImportProfilesProfileAssociationsById:: Failed to delete Profile association with id {}", id, e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void getDataImportProfilesProfileAssociationsById(
    String id,
    String master,
    String detail,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    vertxContext.runOnContext(c -> {
      try {
        OkapiConnectionParams params = new OkapiConnectionParams(okapiHeaders);
        profileAssociationService.getById(id, params.getTenantId())
          .map(optionalProfile -> optionalProfile.orElseThrow(() ->
            new NotFoundException(format("Profile association with id '%s' was not found", id))))
          .map(GetDataImportProfilesProfileAssociationsByIdResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("getDataImportProfilesProfileAssociationsById:: Failed to get Profile association by id {}", id, e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void getDataImportProfilesJobProfileSnapshotsById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(c -> {
      try {
        profileSnapshotService.getById(id, tenantId)
          .map(optionalSnapshot -> optionalSnapshot.orElseThrow(() ->
            new NotFoundException(format("Profile snapshot with id '%s' was not found", id))))
          .map(GetDataImportProfilesJobProfileSnapshotsByIdResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("getDataImportProfilesJobProfileSnapshotsById:: Failed to get Profile snapshot by id {}", id, e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void postDataImportProfilesJobProfileSnapshotsById(String jobProfileId, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        profileSnapshotService.createSnapshot(jobProfileId, tenantId)
          .map(snapshot -> (Response) PostDataImportProfilesProfileAssociationsResponse
            .respond201WithApplicationJson(snapshot, PostDataImportProfilesProfileAssociationsResponse.headersFor201()))
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("postDataImportProfilesJobProfileSnapshotsById:: Failed to create Profile association", e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void getDataImportProfilesProfileAssociationsDetailsById(
    String id,
    String masterType,
    String detailType,
    String query,
    String totalRecords,
    int offset,
    int limit,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    vertxContext.runOnContext(event -> {
        try {
          profileAssociationService.findDetails(id, mapContentType(masterType), mapContentTypeOrNull(detailType), query, offset, limit, tenantId)
            .map(optional -> optional.orElseThrow(() -> new NotFoundException(format(MASTER_PROFILE_NOT_FOUND_MSG, id))))
            .map(GetDataImportProfilesProfileAssociationsDetailsByIdResponse::respond200WithApplicationJson)
            .map(Response.class::cast)
            .otherwise(ExceptionHelper::mapExceptionToResponse)
            .onComplete(asyncResultHandler);
        } catch (Exception e) {
          logger.warn("getDataImportProfilesProfileAssociationsDetailsById:: Failed to retrieve details by master profile with id {}", id, e);
          asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
        }
      }
    );
  }

  private ProfileType mapContentTypeOrNull(String detailType) {
    return Arrays.stream(ProfileType.values())
      .filter(it -> it.value().equals(detailType))
      .findFirst()
      .orElse(null);
  }

  @Override
  public void getDataImportProfilesProfileAssociationsMastersById(
    String id,
    String detailType,
    String masterType,
    String query,
    String totalRecords,
    int offset,
    int limit,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    vertxContext.runOnContext(event -> {
        try {
          profileAssociationService.findMasters(id, mapContentType(detailType), mapContentTypeOrNull(masterType), query, offset, limit, tenantId)
            .map(optional -> optional.orElseThrow(() -> new NotFoundException(format(DETAIL_PROFILE_NOT_FOUND_MSG, id))))
            .map(GetDataImportProfilesProfileAssociationsMastersByIdResponse::respond200WithApplicationJson)
            .map(Response.class::cast)
            .otherwise(ExceptionHelper::mapExceptionToResponse)
            .onComplete(asyncResultHandler);
        } catch (Exception e) {
          logger.warn("getDataImportProfilesProfileAssociationsMastersById:: Failed to retrieve masters by detail profile with id {}", id, e);
          asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
        }
      }
    );
  }

  @Override
  public void deleteDataImportProfilesActionProfilesById(String id, Map<String, String> okapiHeaders,
                                                         Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        actionProfileService.hardDeleteProfile(id, tenantId)
          .map(DeleteDataImportProfilesActionProfilesByIdResponse.respond204WithTextPlain(
            format("Action Profile with id '%s' was successfully deleted", id)))
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("deleteDataImportProfilesActionProfilesById:: Failed to delete Action Profile with id {}", id, e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void getDataImportProfilesEntityTypes(Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        actionProfileService.getEntityTypes()
          .map(GetDataImportProfilesEntityTypesResponse::respond200WithApplicationJson)
          .map(Response.class::cast)
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("getDataImportProfilesEntityTypes:: Failed to get all entity types", e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  @Override
  public void getDataImportProfilesProfileSnapshotsByProfileId(String id, String profileType, String jobProfileId, Map<String, String> okapiHeaders,
                                                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext(v -> {
      try {
        profileSnapshotService.constructSnapshot(id, mapContentType(profileType), jobProfileId, tenantId)
          .map(snapshot -> (Response) GetDataImportProfilesProfileSnapshotsByProfileIdResponse
            .respond200WithApplicationJson(snapshot))
          .otherwise(ExceptionHelper::mapExceptionToResponse)
          .onComplete(asyncResultHandler);
      } catch (Exception e) {
        logger.warn("getDataImportProfilesProfileSnapshotsByProfileId:: Failed to construct Profile Snapshot", e);
        asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
      }
    });
  }

  private ProfileType mapContentType(String contentType) {
    try {
      return ProfileType.fromValue(contentType);
    } catch (IllegalArgumentException e) {
      String message = "The specified type: %s is wrong. It should be " + Arrays.toString(ProfileType.values());
      throw new BadRequestException(format(message, contentType), e);
    }
  }

  private Metadata getMetadata(Map<String, String> okapiHeaders) {
    String userId = okapiHeaders.get(OKAPI_USERID_HEADER);
    String token = okapiHeaders.get(OKAPI_HEADER_TOKEN);
    if (userId == null && token != null) {
      userId = userIdFromToken(token);
    }
    Metadata md = new Metadata();
    md.setUpdatedDate(new Date());
    md.setUpdatedByUserId(userId);
    md.setCreatedDate(md.getUpdatedDate());
    md.setCreatedByUserId(userId);
    return md;
  }

  private static String userIdFromToken(String token) {
    try {
      String[] split = token.split("\\.");
      String json = getJson(split[1]);
      JsonObject j = new JsonObject(json);
      return j.getString("user_id");
    } catch (Exception e) {
      logger.warn("userIdFromToken:: Invalid x-okapi-token: {}", token, e);
      return null;
    }
  }

  private static String getJson(String strEncoded) {
    byte[] decodedBytes = Base64.getDecoder().decode(strEncoded);
    return new String(decodedBytes, StandardCharsets.UTF_8);
  }
}
