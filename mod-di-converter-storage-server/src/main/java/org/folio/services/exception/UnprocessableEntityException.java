package org.folio.services.exception;

import org.folio.rest.jaxrs.model.Errors;

/**
 * A runtime exception indicating a request contains invalid body.
 */
public class UnprocessableEntityException extends RuntimeException {
  private transient final Errors errors;

  public UnprocessableEntityException(Errors errors) {
    this.errors = errors;
  }

  public Errors getErrors() {
    return errors;
  }
}
