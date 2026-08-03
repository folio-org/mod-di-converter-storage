package org.folio.rest.impl;

import static java.lang.String.format;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.impl.util.ExceptionHelper;
import org.folio.rest.jaxrs.model.FormConfig;
import org.folio.rest.jaxrs.resource.ConverterStorageFormsConfigs;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.services.forms.configs.FormConfigService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("java:S6813")
public class ConverterStorageFormsConfigsImpl implements ConverterStorageFormsConfigs {

  private static final Logger LOGGER = LogManager.getLogger();
  private final String tenantId;

  @Autowired
  private FormConfigService formConfigService;

  public ConverterStorageFormsConfigsImpl(Vertx vertx, String tenantId) { //NOSONAR
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
    this.tenantId = TenantTool.calculateTenantId(tenantId);
  }

  @Override
  public void postConverterStorageFormsConfigs(FormConfig entity, Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler,
                                               Context vertxContext) {
    try {
      formConfigService.save(entity, tenantId)
        .map(formConfig -> (Response) PostConverterStorageFormsConfigsResponse.respond201WithApplicationJson(formConfig,
          PostConverterStorageFormsConfigsResponse.headersFor201()))
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .onComplete(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.warn("postConverterStorageFormsConfigs:: Failed to create FormConfig", e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void getConverterStorageFormsConfigs(Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      formConfigService.getAll(tenantId)
        .map(GetConverterStorageFormsConfigsResponse::respond200WithApplicationJson)
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .onComplete(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.warn("getConverterStorageFormsConfigs:: Failed to get all form configs", e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void putConverterStorageFormsConfigsByFormName(String formName, FormConfig entity,
                                                        Map<String, String> okapiHeaders,
                                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                                        Context vertxContext) {
    try {
      entity.setFormName(formName);
      formConfigService.update(entity, tenantId)
        .map(formConfig -> (Response) PutConverterStorageFormsConfigsByFormNameResponse.respond200WithApplicationJson(
          formConfig))
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .onComplete(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.warn("putConverterStorageFormsConfigsByFormName:: Failed to update FormConfig by formId {}", formName, e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void deleteConverterStorageFormsConfigsByFormName(String formName, Map<String, String> okapiHeaders,
                                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                                           Context vertxContext) {
    try {
      formConfigService.deleteByFormName(formName, tenantId)
        .map(isDeleted -> Boolean.TRUE.equals(isDeleted)
                          ? DeleteConverterStorageFormsConfigsByFormNameResponse.respond204()
                          : DeleteConverterStorageFormsConfigsByFormNameResponse.respond404WithTextPlain(
                            format("FormConfig with formName '%s' was not found", formName)))
        .map(Response.class::cast)
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .onComplete(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.warn("deleteConverterStorageFormsConfigsByFormName:: Failed to delete FormConfig by formName {}", formName,
        e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }

  @Override
  public void getConverterStorageFormsConfigsByFormName(String formName, Map<String, String> okapiHeaders,
                                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                                        Context vertxContext) {
    try {
      formConfigService.getByFormName(formName, tenantId)
        .map(formConfig -> (Response) GetConverterStorageFormsConfigsByFormNameResponse.respond200WithApplicationJson(
          formConfig))
        .otherwise(ExceptionHelper::mapExceptionToResponse)
        .onComplete(asyncResultHandler);
    } catch (Exception e) {
      LOGGER.warn("getConverterStorageFormsConfigsByFormName:: Failed to get FormConfig by formId {}", formName, e);
      asyncResultHandler.handle(Future.succeededFuture(ExceptionHelper.mapExceptionToResponse(e)));
    }
  }
}
