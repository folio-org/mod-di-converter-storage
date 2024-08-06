package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.future.CompositeFutureImpl;
import io.vertx.core.json.JsonObject;

import io.vertx.core.json.jackson.DatabindCodec;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.GenericCompositeFuture;
import org.folio.rest.impl.util.ExceptionHelper;
import org.folio.rest.impl.util.OkapiConnectionParams;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileCollection;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileCollection;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileCollection;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingRule;
import org.folio.rest.jaxrs.model.MappingDetail;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.MatchProfileCollection;
import org.folio.rest.jaxrs.model.MatchProfileUpdateDto;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.jaxrs.model.OperationType;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.rest.RestVerticle.OKAPI_USERID_HEADER;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MATCH_PROFILE;

public class DataImportProfilesImpl implements DataImportProfiles {

  private static final Logger logger = LogManager.getLogger();
  private static final String DUPLICATE_PROFILE_ERROR_CODE = "%s '%s' already exists";
  private static final String DUPLICATE_PROFILE_ID_ERROR_CODE = "%s with id '%s' already exists";
  private static final String NOT_EMPTY_RELATED_PROFILE_ERROR_CODE = "%s read-only '%s' field should be empty";
  private static final String PROFILE_VALIDATE_ERROR_MESSAGE = "Failed to validate %s";
  private static final String MASTER_PROFILE_NOT_FOUND_MSG = "Master profile with id '%s' was not found";
  private static final String DETAIL_PROFILE_NOT_FOUND_MSG = "Detail profile with id '%s' was not found";
  private static final String INVALID_REPEATABLE_FIELD_ACTION_FOR_EMPTY_SUBFIELDS_MESSAGE = "Invalid repeatableFieldAction for empty subfields: %s";
  private static final String INVALID_RECORD_TYPE_LINKED_MAPPING_PROFILE_TO_ACTION_PROFILE = "Mapping profile '%s' can not be linked to this Action profile. ExistingRecordType and FolioRecord types are different";
  private static final String INVALID_RECORD_TYPE_LINKED_ACTION_PROFILE_TO_MAPPING_PROFILE = "Action profile '%s' can not be linked to this Mapping profile. FolioRecord and ExistingRecordType types are different";
  private static final String INVALID_MAPPING_PROFILE_NEW_RECORD_TYPE_LINKED_TO_ACTION_PROFILE = "Can not update MappingProfile recordType and linked ActionProfile recordType are different";
  private static final String INVALID_ACTION_PROFILE_NEW_RECORD_TYPE_LINKED_TO_MAPPING_PROFILE = "Can not update ActionProfile recordType and linked MappingProfile recordType are different";
  private static final String INVALID_ACTION_TYPE_LINKED_ACTION_PROFILE_TO_MAPPING_PROFILE = "Unable to complete requested change. " +
    "MARC Update Action profiles can only be linked with MARC Update Mapping profiles and MARC Modify Action profiles can only be linked with MARC Modify Mapping profiles. " +
    "Please ensure your Action and Mapping profiles are of like types and try again.";
  private static final String INVALID_ACTION_PROFILE_ACTION_TYPE = "Can't create ActionProfile for MARC Bib record type with Create action";
  private static final String INVALID_ACTION_PROFILE_LINKED_TO_JOB_PROFILE = "ActionProfile with id '%s' and action UPDATE requires linked MatchProfile";
  private static final String LINKED_ACTION_PROFILES_WERE_NOT_FOUND = "Linked ActionProfiles with ids %s were not found";
  private static final String LINKED_MATCH_PROFILES_WERE_NOT_FOUND = "Linked MatchProfiles with ids %s were not found";
  private static final String MODIFY_ACTION_CANNOT_BE_USED_RIGHT_AFTER_THE_MATCH = "Modify action cannot be used right after a Match";
  private static final String MODIFY_ACTION_CANNOT_BE_USED_AS_A_STANDALONE_ACTION = "Modify action cannot be used as a standalone action";

  static final Map<String, String> ERROR_CODES_TYPES_RELATION = Map.of(
    "mappingProfile", "The field mapping profile",
    "jobProfile", "Job profile",
    "matchProfile", "Match profile",
    "actionProfile", "Action profile"
  );

  private static final String[] MATCH_PROFILES = {
    "d27d71ce-8a1e-44c6-acea-96961b5592c6", //OCLC_MARC_MARC_MATCH_PROFILE_ID
    "31dbb554-0826-48ec-a0a4-3c55293d4dee", //OCLC_INSTANCE_UUID_MATCH_PROFILE_ID
    "4be5d1d2-1f5a-42ff-a9bd-fc90609d94b6",  //DEFAULT_DELETE_MARC_AUTHORITY_MATCH_PROFILE_ID
    "91cec42a-260d-4a8c-a9fb-90d9435ca2f4", //DEFAULT_QM_MARC_BIB_UPDATE_MATCH_PROFILE_ID
    "2a599369-817f-4fe8-bae2-f3e3987990fe", //DEFAULT_QM_HOLDINGS_UPDATE_MATCH_PROFILE_ID
    "aff72eae-847c-4a97-b7b9-c1ddb8cdcbbf"  //DEFAULT_QM_AUTHORITY_UPDATE_MATCH_PROFILE_ID
  };
  private static final String[] JOB_PROFILES = {
    "d0ebb7b0-2f0f-11eb-adc1-0242ac120002", //OCLC_CREATE_INSTANCE_JOB_PROFILE_ID,
    "91f9b8d6-d80e-4727-9783-73fb53e3c786", //OCLC_UPDATE_INSTANCE_JOB_PROFILE_ID,
    "fa0262c7-5816-48d0-b9b3-7b7a862a5bc7", //DEFAULT_CREATE_DERIVE_HOLDINGS_JOB_PROFILE_ID
    "6409dcff-71fa-433a-bc6a-e70ad38a9604", //DEFAULT_CREATE_DERIVE_INSTANCE_JOB_PROFILE_ID
    "80898dee-449f-44dd-9c8e-37d5eb469b1d", //DEFAULT_CREATE_HOLDINGS_AND_SRS_MARC_HOLDINGS_JOB_PROFILE_ID
    "1a338fcd-3efc-4a03-b007-394eeb0d5fb9", //DEFAULT_DELETE_MARC_AUTHORITY_JOB_PROFILE_ID
    "cf6f2718-5aa5-482a-bba5-5bc9b75614da", //DEFAULT_QM_MARC_BIB_UPDATE_JOB_PROFILE_ID
    "6cb347c6-c0b0-4363-89fc-32cedede87ba", //DEFAULT_QM_HOLDINGS_UPDATE_JOB_PROFILE_ID
    "c7fcbc40-c4c0-411d-b569-1fc6bc142a92",
    "6eefa4c6-bbf7-4845-ad82-de7fc4abd0e3"//DEFAULT_QM_AUTHORITY_CREATE_JOB_PROFILE_ID
  };
  private static final String[] MAPPING_PROFILES = {
    "d0ebbc2e-2f0f-11eb-adc1-0242ac120002", //OCLC_CREATE_MAPPING_PROFILE_ID
    "862000b9-84ea-4cae-a223-5fc0552f2b42", //OCLC_UPDATE_MAPPING_PROFILE_ID
    "f90864ef-8030-480f-a43f-8cdd21233252", //OCLC_UPDATE_MARC_BIB_MAPPING_PROFILE_ID
    "991c0300-44a6-47e3-8ea2-b01bb56a38cc", //DEFAULT_CREATE_DERIVE_INSTANCE_MAPPING_PROFILE_ID
    "e0fbaad5-10c0-40d5-9228-498b351dbbaa", //DEFAULT_CREATE_DERIVE_HOLDINGS_MAPPING_PROFILE_ID
    "13cf7adf-c7a7-4c2e-838f-14d0ac36ec0a", //DEFAULT_CREATE_HOLDINGS_MAPPING_PROFILE_ID
    "6a0ec1de-68eb-4833-bdbf-0741db25c314", //DEFAULT_CREATE_AUTHORITIES_MAPPING_PROFILE_ID
    "6a0ec1de-68eb-4833-bdbf-0741db85c314", //DEFAULT_CREATE_AUTHORITY_MAPPING_PROFILE_ID
    "39b265e1-c963-4e5f-859d-6e8c327a265c", //DEFAULT_QM_MARC_BIB_UPDATE_MAPPING_PROFILE_ID
    "b8a9ca7d-4a33-44d3-86e1-f7c6cb7b265f", //DEFAULT_QM_HOLDINGS_UPDATE_MAPPING_PROFILE_ID
    "041f8ff9-9d17-4436-b305-1033e0879501" //DEFAULT_QM_AUTHORITY_UPDATE_MAPPING_PROFILE_ID
  };
  private static final String[] ACTION_PROFILES = {
    "d0ebba8a-2f0f-11eb-adc1-0242ac120002", //OCLC_CREATE_INSTANCE_ACTION_PROFILE_ID
    "cddff0e1-233c-47ba-8be5-553c632709d9", //OCLC_UPDATE_INSTANCE_ACTION_PROFILE_ID
    "6aa8e98b-0d9f-41dd-b26f-15658d07eb52", //OCLC_UPDATE_MARC_BIB_ACTION_PROFILE_ID
    "f8e58651-f651-485d-aead-d2fa8700e2d1", //DEFAULT_CREATE_DERIVE_INSTANCE_ACTION_PROFILE_ID
    "f5feddba-f892-4fad-b702-e4e77f04f9a3", //DEFAULT_CREATE_DERIVE_HOLDINGS_ACTION_PROFILE_ID
    "8aa0b850-9182-4005-8435-340b704b2a19", //DEFAULT_CREATE_HOLDINGS_ACTION_PROFILE_ID
    "7915c72e-c6af-4962-969d-403c7238b051", //DEFAULT_CREATE_AUTHORITIES_ACTION_PROFILE_ID
    "7915c72e-c7af-4962-969d-403c7238b051", //DEFAULT_CREATE_AUTHORITIES_ACTION_PROFILE_ID
    "fabd9a3e-33c3-49b7-864d-c5af830d9990", //DEFAULT_DELETE_MARC_AUTHORITY_ACTION_PROFILE_ID
    "c2e2d482-9486-476e-a28c-8f1e303cbe1a", //DEFAULT_QM_MARC_BIB_UPDATE_ACTION_PROFILE_ID
    "7e24a466-349b-451d-a18e-38fb21d71b38", //DEFAULT_QM_HOLDINGS_UPDATE_ACTION_PROFILE_ID
    "f0f788c8-2e65-4e3a-9247-e9444eeb7d70" //DEFAULT_QM_AUTHORITY_UPDATE_ACTION_PROFILE_ID
  };

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
        composeFutureErrors(
          validateJobProfileAssociations(entity),
          validateProfile(OperationType.CREATE, entity.getProfile(), jobProfileService, tenantId),
          validateJobProfileLinkedActionProfiles(entity),
          validateJobProfileLinkedMatchProfile(entity)
        ).onComplete(errors -> {
          if (errors.failed()) {
            logger.warn(PROFILE_VALIDATE_ERROR_MESSAGE, errors.cause());
            asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(errors.cause())));
          } else if (errors.result().getTotalRecords() > 0) {
            asyncResultHandler.handle(Future.succeededFuture(PostDataImportProfilesJobProfilesResponse.respond422WithApplicationJson(errors.result())));
          } else {
            jobProfileService.saveProfile(entity, new OkapiConnectionParams(okapiHeaders))
              .map(profile -> (Response) PostDataImportProfilesJobProfilesResponse
                .respond201WithApplicationJson(entity.withProfile(profile).withId(profile.getId()), PostDataImportProfilesJobProfilesResponse.headersFor201()))
              .otherwise(ExceptionHelper::mapExceptionToResponse)
              .onComplete(asyncResultHandler);
          }
        });
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
        jobProfileService.isProfileDtoValidForUpdate(id, entity, canDeleteOrUpdateProfile(id, JOB_PROFILES), tenantId).compose(isDtoValidForUpdate -> {
          if (isDtoValidForUpdate) {
            entity.getProfile().setMetadata(getMetadata(okapiHeaders));
            return composeFutureErrors(
              validateProfile(OperationType.UPDATE, entity.getProfile(), jobProfileService, tenantId),
              validateJobProfileAssociations(entity),
              validateJobProfileLinkedActionProfiles(entity),
              validateJobProfileLinkedMatchProfile(entity)
            ).compose(errors -> {
              entity.getProfile().setId(id);
              return errors.getTotalRecords() > 0 ?
                Future.succeededFuture(PutDataImportProfilesJobProfilesByIdResponse.respond422WithApplicationJson(errors)) :
                jobProfileService.updateProfile(entity, new OkapiConnectionParams(okapiHeaders))
                  .map(PutDataImportProfilesJobProfilesByIdResponse::respond200WithApplicationJson);
            });
          } else {
            logger.warn("putDataImportProfilesJobProfilesById:: Can`t update default OCLC Job Profile with id {}", id);
            return Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(new BadRequestException(String.format("Can`t update default OCLC Job Profile with id %s", id))));
          }
        }).otherwise(ExceptionHelper::mapExceptionToResponse).onComplete(asyncResultHandler);
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
        if ("6eefa4c6-bbf7-4845-ad82-de7fc5abd0e3".equals(id) || Arrays.asList(JOB_PROFILES).contains(id)) {
          logger.warn("deleteDataImportProfilesJobProfilesById:: Can`t delete default OCLC Job Profile with id {}", id);
          asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(new BadRequestException("Can`t delete default OCLC Job Profile with"))));
        } else {
          OkapiConnectionParams params = new OkapiConnectionParams(okapiHeaders);
          jobProfileService.hardDeleteProfile(id, params.getTenantId())
            .map(DeleteDataImportProfilesJobProfilesByIdResponse.respond204WithTextPlain(
              format("Job Profile with id '%s' was successfully deleted", id)))
            .map(Response.class::cast)
            .otherwise(ExceptionHelper::mapExceptionToResponse)
            .onComplete(asyncResultHandler);
        }
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
        validateProfile(OperationType.CREATE, entity.getProfile(), matchProfileService, tenantId).onComplete(errors -> {
          if (errors.failed()) {
            logger.warn(format(PROFILE_VALIDATE_ERROR_MESSAGE, entity.getClass().getSimpleName()), errors.cause());
            asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(errors.cause())));
          } else if (errors.result().getTotalRecords() > 0) {
            asyncResultHandler.handle(Future.succeededFuture(PostDataImportProfilesMatchProfilesResponse.respond422WithApplicationJson(errors.result())));
          } else {
            matchProfileService.saveProfile(entity, new OkapiConnectionParams(okapiHeaders))
              .map(profile -> (Response) PostDataImportProfilesMatchProfilesResponse
                .respond201WithApplicationJson(entity.withProfile(profile).withId(profile.getId()), PostDataImportProfilesMatchProfilesResponse.headersFor201()))
              .otherwise(ExceptionHelper::mapExceptionToResponse)
              .onComplete(asyncResultHandler);
          }
        });
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
        matchProfileService.isProfileDtoValidForUpdate(id, entity, canDeleteOrUpdateProfile(id, MATCH_PROFILES), tenantId).compose(isDtoValidForUpdate -> {
          if(isDtoValidForUpdate) {
            entity.getProfile().setMetadata(getMetadata(okapiHeaders));
            return validateProfile(OperationType.UPDATE, entity.getProfile(), matchProfileService, tenantId).compose(errors -> {
              entity.getProfile().setId(id);
              return errors.getTotalRecords() > 0 ?
                Future.succeededFuture(PutDataImportProfilesMatchProfilesByIdResponse.respond422WithApplicationJson(errors)) :
                matchProfileService.updateProfile(entity, new OkapiConnectionParams(okapiHeaders))
                  .map(PutDataImportProfilesMatchProfilesByIdResponse::respond200WithApplicationJson);
            });
          } else {
            logger.warn("putDataImportProfilesMatchProfilesById:: Can`t update default OCLC Match Profile with id {}", id);
            return Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(new BadRequestException(String.format("Can`t update default OCLC Match Profile with id %s", id))));
          }
        }).otherwise(ExceptionHelper::mapExceptionToResponse).onComplete(asyncResultHandler);
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
        composeFutureErrors(
          validateMappingProfileAddedRelationsFolioRecord(entity, tenantId),
          validateMappingProfile(OperationType.CREATE, entity.getProfile(), tenantId)).onComplete(errors -> {
          if (errors.failed()) {
            logger.warn(format(PROFILE_VALIDATE_ERROR_MESSAGE, entity.getClass().getSimpleName()), errors.cause());
            asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(errors.cause())));
          } else if (errors.result().getTotalRecords() > 0) {
            asyncResultHandler.handle(Future.succeededFuture(PostDataImportProfilesMappingProfilesResponse.respond422WithApplicationJson(errors.result())));
          } else {
            mappingProfileService.saveProfile(entity, new OkapiConnectionParams(okapiHeaders))
              .map(profile -> (Response) PostDataImportProfilesMappingProfilesResponse
                .respond201WithApplicationJson(entity.withProfile(profile).withId(profile.getId()), PostDataImportProfilesMappingProfilesResponse.headersFor201()))
              .otherwise(ExceptionHelper::mapExceptionToResponse)
              .onComplete(asyncResultHandler);
          }
        });
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
        mappingProfileService.isProfileDtoValidForUpdate(id, entity, canDeleteOrUpdateProfile(id, MAPPING_PROFILES), tenantId).compose(isDtoValidForUpdate -> {
          if(isDtoValidForUpdate) {
            entity.getProfile().setMetadata(getMetadata(okapiHeaders));
            return composeFutureErrors(
              validateMappingProfile(OperationType.UPDATE, entity.getProfile(), tenantId),
              validateMappingProfileExistProfilesFolioRecord(entity, tenantId, id),
              validateMappingProfileAddedRelationsFolioRecord(entity, tenantId)
            ).compose(errors -> {
              entity.getProfile().setId(id);
              return errors.getTotalRecords() > 0 ?
                Future.succeededFuture(PutDataImportProfilesMappingProfilesByIdResponse.respond422WithApplicationJson(errors)) :
                mappingProfileService.updateProfile(entity, new OkapiConnectionParams(okapiHeaders))
                  .map(PutDataImportProfilesMappingProfilesByIdResponse::respond200WithApplicationJson);
            });
          } else {
            logger.warn("putDataImportProfilesMappingProfilesById:: Can`t update default OCLC Mapping Profile with id {}", id);
            return Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(new BadRequestException(String.format("Can`t update default OCLC Mapping Profile with id %s", id))));
          }
        }).otherwise(ExceptionHelper::mapExceptionToResponse).onComplete(asyncResultHandler);
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
        if (canDeleteOrUpdateProfile(id, MAPPING_PROFILES)) {
          logger.warn("deleteDataImportProfilesMappingProfilesById:: Can`t delete default OCLC Mapping Profile with id {}", id);
          asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(new BadRequestException("Can`t delete default OCLC Mapping Profile"))));
        } else {
          OkapiConnectionParams params = new OkapiConnectionParams(okapiHeaders);
          mappingProfileService.hardDeleteProfile(id, params.getTenantId())
            .map(DeleteDataImportProfilesMappingProfilesByIdResponse.respond204WithTextPlain(
              format("Mapping Profile with id '%s' was successfully deleted", id)))
            .map(Response.class::cast)
            .otherwise(ExceptionHelper::mapExceptionToResponse)
            .onComplete(asyncResultHandler);
        }
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
        if (canDeleteOrUpdateProfile(id, MATCH_PROFILES)) {
          logger.warn("deleteDataImportProfilesMatchProfilesById:: Can`t delete default OCLC Match Profile with id {}", id);
          asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(new BadRequestException("Can`t delete default OCLC Match Profile"))));
        } else {
          OkapiConnectionParams params = new OkapiConnectionParams(okapiHeaders);
          matchProfileService.hardDeleteProfile(id, params.getTenantId())
            .map(DeleteDataImportProfilesMatchProfilesByIdResponse.respond204WithTextPlain(
              format("Match Profile with id '%s' was successfully deleted", id)))
            .map(Response.class::cast)
            .otherwise(ExceptionHelper::mapExceptionToResponse)
            .onComplete(asyncResultHandler);
        }
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
        entity.getProfile().setMetadata(getMetadata(okapiHeaders));
        composeFutureErrors(
          validateActionProfile(OperationType.CREATE, entity.getProfile(), tenantId),
          validateActionProfileAddedRelationsFolioRecord(entity, tenantId)).onComplete(errors -> {
          if (errors.failed()) {
            logger.warn(format(PROFILE_VALIDATE_ERROR_MESSAGE, entity.getClass().getSimpleName()), errors.cause());
            asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(errors.cause())));
          } else if (errors.result().getTotalRecords() > 0) {
            asyncResultHandler.handle(Future.succeededFuture(PostDataImportProfilesActionProfilesResponse.respond422WithApplicationJson(errors.result())));
          } else {
            actionProfileService.saveProfile(entity, new OkapiConnectionParams(okapiHeaders))
              .map(profile -> (Response) PostDataImportProfilesActionProfilesResponse
                .respond201WithApplicationJson(entity.withProfile(profile).withId(profile.getId()), PostDataImportProfilesActionProfilesResponse.headersFor201()))
              .otherwise(ExceptionHelper::mapExceptionToResponse)
              .onComplete(asyncResultHandler);
          }
        });
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
        actionProfileService.isProfileDtoValidForUpdate(id, entity, canDeleteOrUpdateProfile(id, ACTION_PROFILES), tenantId).compose(isDtoValidForUpdate -> {
          if (isDtoValidForUpdate) {
            entity.getProfile().setMetadata(getMetadata(okapiHeaders));
            return composeFutureErrors(
              validateActionProfile(OperationType.UPDATE, entity.getProfile(), tenantId),
              validateActionProfileChildProfilesFolioRecord(entity, tenantId, id),
              validateActionProfileAddedRelationsFolioRecord(entity, tenantId)
            ).compose(errors -> {
              entity.getProfile().setId(id);
              return errors.getTotalRecords() > 0 ?
                Future.succeededFuture(PutDataImportProfilesActionProfilesByIdResponse.respond422WithApplicationJson(errors)) :
                actionProfileService.updateProfile(entity, new OkapiConnectionParams(okapiHeaders))
                  .map(PutDataImportProfilesActionProfilesByIdResponse::respond200WithApplicationJson);
            });
          } else {
            logger.warn("putDataImportProfilesActionProfilesById:: Can`t update default OCLC Action Profile with id {}", id);
            return Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(new BadRequestException(String.format("Can`t update default OCLC Action Profile with id %s", id))));
          }
        }).otherwise(ExceptionHelper::mapExceptionToResponse).onComplete(asyncResultHandler);
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
        if (canDeleteOrUpdateProfile(id, ACTION_PROFILES)) {
          logger.warn("deleteDataImportProfilesActionProfilesById:: Can`t delete default OCLC Action Profile with id {}", id);
          asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(new BadRequestException("Can`t delete default OCLC Action Profile"))));
        } else {
          actionProfileService.hardDeleteProfile(id, tenantId)
            .map(DeleteDataImportProfilesActionProfilesByIdResponse.respond204WithTextPlain(
              format("Action Profile with id '%s' was successfully deleted", id)))
            .map(Response.class::cast)
            .otherwise(ExceptionHelper::mapExceptionToResponse)
            .onComplete(asyncResultHandler);
        }
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

  private <T, S, D> Future<Errors> validateProfile(OperationType operationType, T profile, ProfileService<T, S, D> profileService, String tenantId) {
    Promise<Errors> promise = Promise.promise();
    String profileTypeName = StringUtils.uncapitalize(profile.getClass().getSimpleName());
    Map<String, Future<Boolean>> validateConditions = getValidateConditions(operationType, profile, profileService, tenantId);
    List<String> errorCodes = new ArrayList<>(validateConditions.keySet());
    List<Future<Boolean>> futures = new ArrayList<>(validateConditions.values());
    GenericCompositeFuture.all(futures).onComplete( ar -> {
      if(ar.succeeded()) {
        List<Error> errors = new ArrayList<>(errorCodes).stream()
          .filter(errorCode -> ar.result().resultAt(errorCodes.indexOf(errorCode)))
          .map(errorCode -> new Error().withMessage(format(errorCode,
            ERROR_CODES_TYPES_RELATION.get(profileTypeName), profileService.getProfileName(profile))))
          .collect(Collectors.toList());
        promise.complete(new Errors().withErrors(errors).withTotalRecords(errors.size()));
      } else {
        promise.fail(ar.cause());
      }
    });
    return promise.future();
  }

  private <T, S, D> Map<String, Future<Boolean>> getValidateConditions(OperationType operationType, T profile, ProfileService<T, S, D> profileService, String tenantId) {
    Map<String, Future<Boolean>> validateConditions = new LinkedHashMap<>();
    validateConditions.put(DUPLICATE_PROFILE_ERROR_CODE, profileService.isProfileExistByProfileName(profile, tenantId));
    validateConditions.put(String.format(NOT_EMPTY_RELATED_PROFILE_ERROR_CODE, "%s", "child"), profileService.isProfileContainsChildProfiles(profile));
    validateConditions.put(String.format(NOT_EMPTY_RELATED_PROFILE_ERROR_CODE, "%s", "parent"), profileService.isProfileContainsParentProfiles(profile));
    if(operationType == OperationType.CREATE) {
      validateConditions.put(DUPLICATE_PROFILE_ID_ERROR_CODE, profileService.isProfileExistByProfileId(profile, tenantId));
    }
    return validateConditions;
  }

  private Future<Errors> validateJobProfileAssociations(List<ProfileAssociation> profileAssociations, List<Error> errors) {
    logger.debug("validateJobProfileAssociations:: Validating JobProfile if it contains associations");
    if (CollectionUtils.isEmpty(profileAssociations)) {
      logger.warn("validateJobProfileAssociations:: Job profile does not contain any associations");
      errors.add(new Error().withMessage("Job profile does not contain any associations"));
    }
    return Future.succeededFuture(new Errors().withErrors(errors).withTotalRecords(errors.size()));
  }

  private Future<Errors> validateJobProfileLinkedMatchProfile(JobProfileUpdateDto jobProfileUpdateDto) {
    String jobProfileId = jobProfileUpdateDto.getProfile().getId();
    logger.debug("validateJobProfileLinkedMatchProfile:: Validating MatchProfiles added to JobProfile {}", jobProfileId);

    List<Error> errors = new LinkedList<>();
    Future<List<ProfileAssociation>> existingJobProfileAssociationsFuture = (jobProfileId != null) ?
                                                                            profileSnapshotService.getSnapshotAssociations(jobProfileId, ProfileType.JOB_PROFILE, jobProfileId, tenantId):
                                                                            Future.succeededFuture(new ArrayList<>());

    return existingJobProfileAssociationsFuture
      .map(this::filterProfileAssociations)
      .map(profileAssociations -> removeDeletedProfileAssociations(profileAssociations, jobProfileUpdateDto))
      .compose(profileAssociations -> validateJobProfileLinkedMatchProfile(profileAssociations, errors));
  }

  private Future<Errors> validateJobProfileLinkedMatchProfile(List<ProfileAssociation> profileAssociations,
                                                               List<Error> errors) {
    var childActionProfileAssociations = actionProfileAssociations(profileAssociations);
    validateMatchProfilesAssociations(childActionProfileAssociations, errors);

    var matchProfileAssociations = matchProfileAssociations(profileAssociations);
    var matchProfileIds = matchProfileAssociations.stream().map(ProfileAssociation::getDetailProfileId).toList();

    var futures = matchProfileIds.stream()
      .map(id -> matchProfileService.getProfileById(id, false, tenantId))
      .collect(Collectors.toList());

    return GenericCompositeFuture.all(futures)
      .compose(matchProfiles -> {
        List<MatchProfile> existingMatchProfiles = matchProfiles.result().<Optional<MatchProfile>>list().stream()
          .filter(Optional::isPresent).map(Optional::get).toList();

        var existingMatchProfilesIds = existingMatchProfiles.stream().map(MatchProfile::getId).toList();
        var notFoundIds = matchProfileIds.stream()
          .filter(id -> !existingMatchProfilesIds.contains(id))
          .map(notFoundedId -> String.format("'%s'", notFoundedId)).toList();

        if (!notFoundIds.isEmpty()) {
          var idStr = String.join(", ", notFoundIds);
          logger.warn("validateJobProfileLinkedMatchProfile:: Linked MatchProfiles with ids {} not founded", idStr);
          return Future.failedFuture(new NotFoundException((String.format(LINKED_MATCH_PROFILES_WERE_NOT_FOUND, idStr))));
        }
        return Future.succeededFuture(new Errors().withErrors(errors).withTotalRecords(errors.size()));
      });
  }


  private Future<Errors> validateMatchProfilesAssociations(List<ProfileAssociation> profileAssociations,
                                                           List<Error> errors) {
    logger.debug("validateMatchProfilesAssociations:: Validating JobProfile if its MatchProfile contains ActionProfile");
    if (CollectionUtils.isEmpty(profileAssociations)) {
      logger.warn("validateMatchProfilesAssociations:: Job profile does not contain any associations");
      errors.add(new Error().withMessage("Linked ActionProfile was not found after MatchProfile"));
    }
    return Future.succeededFuture(new Errors().withErrors(errors).withTotalRecords(errors.size()));
  }


  private Future<Errors> validateJobProfileAssociations(JobProfileUpdateDto entity) {
    String jobProfileId = entity.getProfile().getId();
    Future<List<ProfileAssociation>> existingJobProfileAssociationsFuture = (jobProfileId != null) ?
      profileSnapshotService.getSnapshotAssociations(jobProfileId, ProfileType.JOB_PROFILE, jobProfileId, tenantId):
      Future.succeededFuture(new ArrayList<>());

    List<Error> errors = new LinkedList<>();
    return existingJobProfileAssociationsFuture
      .map(this::filterProfileAssociations)
      .map(profileAssociations -> removeDeletedProfileAssociations(profileAssociations, entity))
      .compose(profileAssociations -> validateJobProfileAssociations(profileAssociations, errors));
  }

  private Future<Errors> validateJobProfileLinkedActionProfiles(JobProfileUpdateDto jobProfileUpdateDto) {
    String jobProfileId = jobProfileUpdateDto.getProfile().getId();
    logger.debug("validateJobProfileLinkedActionProfiles:: Validating ActionProfiles added to JobProfile {}", jobProfileId);
    List<Error> errors = new LinkedList<>();
    Future<List<ProfileAssociation>> existingJobProfileAssociationsFuture;
    if (jobProfileId != null) {
      existingJobProfileAssociationsFuture = profileSnapshotService.getSnapshotAssociations(jobProfileId, ProfileType.JOB_PROFILE, jobProfileId, tenantId);
    } else {
      existingJobProfileAssociationsFuture = Future.succeededFuture(new ArrayList<>());
    }

    return existingJobProfileAssociationsFuture
      .map(this::filterProfileAssociations)
      .map(profileAssociations -> removeDeletedProfileAssociations(profileAssociations, jobProfileUpdateDto))
      .compose(profileAssociations -> validateActionProfilesAssociations(profileAssociations, errors));
  }

  private List<ProfileAssociation> filterProfileAssociations(List<ProfileAssociation> profileAssociations) {
    return profileAssociations.stream()
      .filter(profileAssociation -> profileAssociation.getDetailProfileType() != ProfileType.MAPPING_PROFILE &&
        profileAssociation.getDetailProfileType() != ProfileType.JOB_PROFILE)
      .collect(Collectors.toList());
  }

  private List<ProfileAssociation> removeDeletedProfileAssociations(List<ProfileAssociation> profileAssociations,
                                                        JobProfileUpdateDto jobProfileUpdateDto) {
    if (!profileAssociations.isEmpty() && !jobProfileUpdateDto.getDeletedRelations().isEmpty()) {
      profileAssociations.removeIf(profileAssociation ->
        jobProfileUpdateDto.getDeletedRelations().stream().anyMatch(deleteAssociation -> {
          if (profileAssociation.getId() != null && deleteAssociation.getId() != null) {
            return Objects.equals(profileAssociation.getId(), deleteAssociation.getId());
          }
           return Objects.equals(profileAssociation.getMasterWrapperId(), deleteAssociation.getMasterWrapperId()) &&
             Objects.equals(profileAssociation.getDetailWrapperId(), deleteAssociation.getDetailWrapperId());
          }
        )
      );
    }
    profileAssociations.addAll(jobProfileUpdateDto.getAddedRelations());
    return profileAssociations;
  }

  private Future<Errors> validateActionProfilesAssociations(List<ProfileAssociation> profileAssociations, List<Error> errors) {
    var actionProfileAssociations = actionProfileAssociations(profileAssociations);
    var actionProfileIds = actionProfileAssociations.stream().map(ProfileAssociation::getDetailProfileId).toList();
    var getProfileFutures = actionProfileIds.stream()
      .map(id -> actionProfileService.getProfileById(id, false, tenantId))
      .collect(Collectors.toList());

    return GenericCompositeFuture.all(getProfileFutures)
      .compose(actionProfiles -> {
        List<ActionProfile> existingActionProfiles = actionProfiles.result().<Optional<ActionProfile>>list().stream()
          .filter(Optional::isPresent).map(Optional::get).toList();

        var existingActionProfilesIds = existingActionProfiles.stream().map(ActionProfile::getId).toList();
        var notFoundedIds = actionProfileIds.stream()
          .filter(id -> !existingActionProfilesIds.contains(id))
          .map(notFoundedId -> String.format("'%s'", notFoundedId)).toList();

        if (!notFoundedIds.isEmpty()) {
          var idStr = String.join(", ", notFoundedIds);
          logger.warn("validateActionProfilesAssociations:: Linked ActionProfiles with ids {} not founded", idStr);
          return Future.failedFuture(new NotFoundException((String.format(LINKED_ACTION_PROFILES_WERE_NOT_FOUND, idStr))));
        }
        return Future.succeededFuture(existingActionProfiles);
      })
      .compose(actionProfiles -> {
        actionProfileAssociations.forEach(association -> {
          ActionProfile actionProfile = actionProfiles.stream()
            .filter(profile -> profile.getId().equals(association.getDetailProfileId()))
            .findAny().orElseThrow(() -> new NotFoundException(String.format(LINKED_ACTION_PROFILES_WERE_NOT_FOUND, association.getDetailProfileId())));

          if (actionProfile.getAction() == ActionProfile.Action.UPDATE) {
            validateAddedUpdateActionProfileAssociation(association, actionProfile, errors);
          }
          if (actionProfile.getAction() == ActionProfile.Action.MODIFY) {
            validateAddedModifyActionProfileAssociation(profileAssociations, association, actionProfile, actionProfiles, errors);
          }
        });
        return Future.succeededFuture(new Errors().withErrors(errors).withTotalRecords(errors.size()));
      });
  }

  private static void validateAddedModifyActionProfileAssociation(List<ProfileAssociation> profileAssociations, ProfileAssociation association, ActionProfile actionProfile,
                                                                  List<ActionProfile> actionProfiles, List<Error> errors) {
    List<ProfileAssociation> notModifyProfileAssociations = getNotModifyProfileAssociations(profileAssociations, actionProfiles);

    if (association.getMasterProfileType() == ProfileType.JOB_PROFILE && notModifyProfileAssociations.isEmpty()) {
      logger.warn("validateAddedModifyActionProfileAssociation:: Modify profile with id {}, used as standalone action", actionProfile.getId());
      errors.add(new Error().withMessage(MODIFY_ACTION_CANNOT_BE_USED_AS_A_STANDALONE_ACTION));
    }

    if (association.getMasterProfileType() == MATCH_PROFILE && isFirstAtMatchBlock(profileAssociations, association)) {
      logger.warn("validateAddedModifyActionProfileAssociation:: Modify profile with id {}, used right after Match profile", actionProfile.getId());
      errors.add(new Error().withMessage(MODIFY_ACTION_CANNOT_BE_USED_RIGHT_AFTER_THE_MATCH));
    }
  }

  private static List<ProfileAssociation> getNotModifyProfileAssociations(List<ProfileAssociation> profileAssociations, List<ActionProfile> actionProfiles) {
    List<String> modifyActionProfileIds = actionProfiles.stream().filter(a -> a.getAction() == ActionProfile.Action.MODIFY).map(ActionProfile::getId).toList();
    return profileAssociations.stream().filter(p -> !modifyActionProfileIds.contains(p.getDetailProfileId())).toList();
  }

  private static boolean isFirstAtMatchBlock(List<ProfileAssociation> profileAssociations, ProfileAssociation association) {
    if (association.getOrder() == 0) return true;
    if (association.getMasterWrapperId() != null) {
      List<ProfileAssociation> associationsAtMatchBlock = profileAssociations.stream()
        .filter(a -> Objects.equals(a.getMasterWrapperId(), association.getMasterWrapperId())).toList();

      ProfileAssociation associationWithLowestOrder = Collections.min(associationsAtMatchBlock,
        Comparator.comparingInt(ProfileAssociation::getOrder));

      return associationWithLowestOrder == association;
    }
    return false;
  }

  private static void validateAddedUpdateActionProfileAssociation(ProfileAssociation association, ActionProfile actionProfile, List<Error> errors) {
    if (association.getMasterProfileType() != MATCH_PROFILE) {
      logger.warn("validateAddedUpdateActionProfileAssociation:: Missing linked MatchProfile for ActionProfile {} with action UPDATE", actionProfile.getId());
      errors.add(new Error().withMessage(String.format(INVALID_ACTION_PROFILE_LINKED_TO_JOB_PROFILE, actionProfile.getId())));
    }
  }

  private List<ProfileAssociation> actionProfileAssociations(List<ProfileAssociation> profileAssociations) {
    return profileAssociations.stream()
      .filter(association -> association.getDetailProfileType() == ACTION_PROFILE).toList();
  }

  private List<ProfileAssociation> matchProfileAssociations(List<ProfileAssociation> profileAssociations) {
    return profileAssociations.stream()
      .filter(profileAssociation -> profileAssociation.getDetailProfileType() == MATCH_PROFILE).toList();
  }

  private Future<Errors> validateMappingProfile(OperationType operationType, MappingProfile mappingProfile, String tenantId) {
    return validateProfile(operationType, mappingProfile, mappingProfileService, tenantId)
      .map(errors -> {
        List<Error> fieldsValidationErrors = validateRepeatableFields(mappingProfile);
        errors.withTotalRecords(errors.getTotalRecords() + fieldsValidationErrors.size())
          .getErrors().addAll(fieldsValidationErrors);
        return errors;
      });
  }

  private Future<Errors> validateActionProfile(OperationType operationType, ActionProfile actionProfile, String tenantId) {
    return validateProfile(operationType, actionProfile, actionProfileService, tenantId)
      .map(errors -> {
        if (ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC == actionProfile.getFolioRecord() && ActionProfile.Action.CREATE == actionProfile.getAction()) {
          logger.warn("validateActionProfile:: {}", INVALID_ACTION_PROFILE_ACTION_TYPE);
          errors.withTotalRecords(errors.getTotalRecords() + 1).getErrors().add(new Error().withMessage(INVALID_ACTION_PROFILE_ACTION_TYPE));
        }
        return errors;
      });
  }

  private Future<Errors> validateActionProfileAddedRelationsFolioRecord(ActionProfileUpdateDto actionProfileUpdateDto, String tenantId) {
    if (CollectionUtils.isEmpty(actionProfileUpdateDto.getAddedRelations())) {
      return Future.succeededFuture(new Errors().withTotalRecords(0));
    }

    var errors = new LinkedList<Error>();
    Promise<Errors> promise = Promise.promise();

    var futures = actionProfileUpdateDto
      .getAddedRelations()
      .stream()
      .filter(profileAssociation -> profileAssociation.getDetailProfileType() == ProfileType.MAPPING_PROFILE)
      .map(profileAssociation -> mappingProfileService.getProfileById(profileAssociation.getDetailProfileId(), false, tenantId))
      .map(futureMappingProfile -> futureMappingProfile.onSuccess(optionalMappingProfile ->
        optionalMappingProfile.ifPresent(mappingProfile -> validateAssociations(actionProfileUpdateDto.getProfile(), mappingProfile, errors,
          String.format(INVALID_RECORD_TYPE_LINKED_MAPPING_PROFILE_TO_ACTION_PROFILE, mappingProfile.getName())))
      ))
      .collect(Collectors.toList());
    GenericCompositeFuture.all(futures)
      .onSuccess(handler -> promise.complete(new Errors().withErrors(errors)))
      .onFailure(promise::fail);

    return promise.future();
  }

  private Future<Errors> validateActionProfileChildProfilesFolioRecord(ActionProfileUpdateDto actionProfileUpdateDto, String tenantId, String id) {
    var errors = new LinkedList<Error>();
    var deletedRelations = actionProfileUpdateDto.getDeletedRelations();
    Promise<Errors> promise = Promise.promise();

    actionProfileService.getProfileById(id, true, tenantId)
      .onSuccess(optionalActionProfile ->
        optionalActionProfile.ifPresentOrElse(actionProfile -> {
            var existMappingProfiles = CollectionUtils.isEmpty(deletedRelations) ? actionProfile.getChildProfiles() :
              actionProfile.getChildProfiles().stream()
                .filter(profileSnapshotWrapper -> profileSnapshotWrapper.getContentType() == ProfileType.MAPPING_PROFILE)
                .filter(profileSnapshotWrapper -> deletedRelations.stream()
                  .noneMatch(rel -> Objects.equals(rel.getDetailProfileId(), profileSnapshotWrapper.getProfileId()))
                ).toList();

            existMappingProfiles.forEach(mappingWrapper -> {
              var mappingProfile = DatabindCodec.mapper().convertValue(mappingWrapper.getContent(), MappingProfile.class);
              validateAssociations(actionProfileUpdateDto.getProfile(), mappingProfile, errors, INVALID_ACTION_PROFILE_NEW_RECORD_TYPE_LINKED_TO_MAPPING_PROFILE);
            });
            promise.complete(new Errors().withErrors(errors));
          }, () -> promise.fail(new NotFoundException(String.format("Action profile with id '%s' was not found", id)))
        )
      ).onFailure(promise::fail);

    return promise.future();
  }

  private Future<Errors> validateMappingProfileAddedRelationsFolioRecord(MappingProfileUpdateDto mappingProfileUpdateDto, String tenantId) {
    if (CollectionUtils.isEmpty(mappingProfileUpdateDto.getAddedRelations())) {
      return Future.succeededFuture(new Errors().withTotalRecords(0));
    }

    var errors = new LinkedList<Error>();
    Promise<Errors> promise = Promise.promise();

    var futures = mappingProfileUpdateDto
      .getAddedRelations()
      .stream()
      .filter(profileAssociation -> profileAssociation.getMasterProfileType() == ACTION_PROFILE)
      .map(profileAssociation -> actionProfileService.getProfileById(profileAssociation.getMasterProfileId(), false, tenantId))
      .map(futureActionProfile -> futureActionProfile.onSuccess(optionalActionProfile ->
        optionalActionProfile.ifPresent(actionProfile -> validateAssociations(actionProfile, mappingProfileUpdateDto.getProfile(), errors,
          String.format(INVALID_RECORD_TYPE_LINKED_ACTION_PROFILE_TO_MAPPING_PROFILE, actionProfile.getName())))
      ))
      .collect(Collectors.toList());
    GenericCompositeFuture.all(futures)
      .onSuccess(handler -> promise.complete(new Errors().withErrors(errors)))
      .onFailure(promise::fail);

    return promise.future();
  }

  private Future<Errors> validateMappingProfileExistProfilesFolioRecord(MappingProfileUpdateDto mappingProfileUpdateDto, String tenantId, String id) {
    var errors = new LinkedList<Error>();
    var deletedRelations = mappingProfileUpdateDto.getDeletedRelations();
    Promise<Errors> promise = Promise.promise();

    mappingProfileService.getProfileById(id, true, tenantId)
      .onSuccess(optionalMappingProfile ->
        optionalMappingProfile.ifPresentOrElse(mappingProfile -> {
            var existActionProfiles = CollectionUtils.isEmpty(deletedRelations) ? mappingProfile.getParentProfiles() :
              mappingProfile.getParentProfiles().stream()
                .filter(profileSnapshotWrapper -> profileSnapshotWrapper.getContentType() == ACTION_PROFILE)
                .filter(profileSnapshotWrapper -> deletedRelations.stream()
                  .noneMatch(deletedRelation -> Objects.equals(deletedRelation.getMasterProfileId(), profileSnapshotWrapper.getProfileId())))
                .toList();

            existActionProfiles.forEach(actionWrapper -> {
              var actionProfile = DatabindCodec.mapper().convertValue(actionWrapper.getContent(), ActionProfile.class);
              validateAssociations(actionProfile, mappingProfileUpdateDto.getProfile(), errors, INVALID_MAPPING_PROFILE_NEW_RECORD_TYPE_LINKED_TO_ACTION_PROFILE);
            });
            promise.complete(new Errors().withErrors(errors));
          }, () -> promise.fail(new NotFoundException(String.format("Mapping profile with id '%s' was not found", id)))
        )
      )
      .onFailure(promise::fail);

    return promise.future();
  }

  private void validateAssociations(ActionProfile actionProfile, MappingProfile mappingProfile, List<Error> errors, String errMsg) {
    var mappingRecordType = mappingProfile.getExistingRecordType().value();
    var actionRecordType = actionProfile.getFolioRecord().value();
    if (!actionRecordType.equals(mappingRecordType)) {
      logger.info("validateAssociations:: Can not create or update profile. MappingProfile with ID:{} FolioRecord:{}, linked ActionProfile with ID:{} FolioRecord:{}",
        mappingProfile.getId(), mappingRecordType, actionProfile.getId(), actionRecordType);
      errors.add(new Error().withMessage(errMsg));
      return;
    }

    Optional.ofNullable(mappingProfile.getMappingDetails())
      .map(MappingDetail::getMarcMappingOption)
      .filter(option -> !option.value().equals(actionProfile.getAction().value()))
      .ifPresent(option -> {
        logger.info("validateAssociations:: Can not create or update profile. ActionProfile Action:{}, linked MappingProfile Option:{}", actionProfile.getAction().value(), option);
        errors.add(new Error().withMessage(INVALID_ACTION_TYPE_LINKED_ACTION_PROFILE_TO_MAPPING_PROFILE));
      });
  }

  @SafeVarargs
  private Future<Errors> composeFutureErrors(Future<Errors>... errorsFuture) {
    return CompositeFutureImpl.all(errorsFuture).map(compositeFuture -> compositeFuture.list().stream()
      .map(object -> (Errors) object)
      .reduce(new Errors().withTotalRecords(0), (accumulator, errors) -> addAll(accumulator, errors.getErrors())
      ));
  }

  private List<Error> validateRepeatableFields(MappingProfile mappingProfile) {
    List<Error> errorList = new ArrayList<>();
    if (mappingProfile.getMappingDetails() != null && mappingProfile.getMappingDetails().getMappingFields() != null) {
      List<MappingRule> mappingFields = mappingProfile.getMappingDetails().getMappingFields();
      for (MappingRule rule : mappingFields) {
        if (rule.getRepeatableFieldAction() != null && rule.getSubfields().isEmpty() && !rule.getRepeatableFieldAction().equals(MappingRule.RepeatableFieldAction.DELETE_EXISTING)) {
          errorList.add(new Error().withMessage(format(INVALID_REPEATABLE_FIELD_ACTION_FOR_EMPTY_SUBFIELDS_MESSAGE, rule.getRepeatableFieldAction())));
        }
      }
    }
    return errorList;
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

  private boolean canDeleteOrUpdateProfile(String id, String... uids) {
    return Arrays.asList(uids).contains(id);
  }

  private Errors addAll(Errors errors, List<Error> otherErrors) {
    errors.withTotalRecords(errors.getTotalRecords() + otherErrors.size())
      .getErrors().addAll(otherErrors);
    return errors;
  }
}
