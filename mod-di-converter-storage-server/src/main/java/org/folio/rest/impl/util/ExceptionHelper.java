package org.folio.rest.impl.util;

import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.tools.utils.ValidationHelper;
import org.folio.services.exception.ConflictException;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.List;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

public final class ExceptionHelper {

  private static final Logger LOGGER = LogManager.getLogger();
  private ExceptionHelper() {
  }

  public static Response mapExceptionToResponse(Throwable throwable) {
    if (throwable instanceof BadRequestException) {
      return Response.status(BAD_REQUEST.getStatusCode())
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(mapExceptionToMessage(throwable))
        .build();
    }
    if (throwable instanceof NotFoundException) {
      return Response.status(NOT_FOUND.getStatusCode())
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(mapExceptionToMessage(throwable))
        .build();
    }
    if (throwable instanceof ConflictException) {
      return Response.status(CONFLICT.getStatusCode())
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(mapExceptionToMessage(throwable))
        .build();
    }

    LOGGER.error("{}", throwable.getMessage(), throwable);
    Promise<Response> validationFuture = Promise.promise();
    ValidationHelper.handleError(throwable, validationFuture);
    if (validationFuture.future().isComplete()) {
      Response response = validationFuture.future().result();
      if (response.getStatus() == INTERNAL_SERVER_ERROR.getStatusCode()) {
        LOGGER.warn(throwable.getMessage(), throwable);
      }
      return response;
    }
    return Response.status(INTERNAL_SERVER_ERROR.getStatusCode())
      .type(MediaType.TEXT_PLAIN)
      .entity(INTERNAL_SERVER_ERROR.getReasonPhrase())
      .build();
  }

  private static String mapExceptionToMessage(Throwable throwable){
    return Json.encode(new Errors()
      .withErrors(List.of(mapThrowable(throwable))).withTotalRecords(1));
  }

  private static Error mapThrowable(Throwable throwable) {
    return new Error().withMessage(throwable.getMessage());
  }
}
