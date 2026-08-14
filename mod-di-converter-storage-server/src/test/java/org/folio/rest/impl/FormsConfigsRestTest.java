package org.folio.rest.impl;

import static org.folio.dataimport.testsupport.postgres.PostgresTestSupport.clearTable;
import static org.folio.support.TestUtil.FORMS_CONFIGS_PATH;
import static org.folio.support.TestUtil.FORMS_CONFIGS_TABLE;
import static org.folio.support.TestUtil.TENANT_ID;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.FormConfig;
import org.folio.support.AbstractRestTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FormsConfigsRestTest extends AbstractRestTest {

  private final FormConfig formConfig = new FormConfig()
    .withFormName("matchProfilesForm")
    .withConfig(new JsonObject());

  @Override
  protected void clearTables(VertxTestContext testContext) {
    clearTable(FORMS_CONFIGS_TABLE, vertx, TENANT_ID)
      .onComplete(testContext.succeedingThenComplete());
  }

  @DisplayName("should return empty collection on GET when no form configs exist")
  @Test
  void shouldReturnEmptyCollectionOnGet() {
    getRequest(FORMS_CONFIGS_PATH)
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(0))
      .body("formConfigs", empty());
  }

  @DisplayName("should return all form configs on GET")
  @Test
  void shouldReturnAllFormConfigsOnGet() {
    postRequest(FORMS_CONFIGS_PATH, formConfig)
      .statusCode(HttpStatus.SC_CREATED)
      .body("formName", is(formConfig.getFormName()))
      .body("config", notNullValue());

    getRequest(FORMS_CONFIGS_PATH)
      .statusCode(HttpStatus.SC_OK)
      .body("totalRecords", is(1))
      .body("formConfigs", hasSize(1));
  }

  @DisplayName("should create form config on POST")
  @Test
  void shouldCreateFormConfigOnPost() {
    postRequest(FORMS_CONFIGS_PATH, formConfig)
      .statusCode(HttpStatus.SC_CREATED)
      .body("formName", is(formConfig.getFormName()))
      .body("config", notNullValue());
  }

  @DisplayName("should return 422 Unprocessable Entity on POST without form name")
  @Test
  void shouldReturnBadRequestOnPostWithoutFormName() {
    FormConfig invalidConfig = new FormConfig()
      .withConfig(new JsonObject());

    postRequest(FORMS_CONFIGS_PATH, invalidConfig)
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should return form config on GET by form name")
  @Test
  void shouldReturnFormConfigOnGetByFormName() {
    postRequest(FORMS_CONFIGS_PATH, formConfig)
      .statusCode(HttpStatus.SC_CREATED);

    getRequest(FORMS_CONFIGS_PATH + "/" + formConfig.getFormName())
      .statusCode(HttpStatus.SC_OK)
      .body("formName", is(formConfig.getFormName()))
      .body("config", notNullValue());
  }

  @DisplayName("should return 404 Not Found on GET by form name when form config does not exist")
  @Test
  void shouldReturnNotFoundOnGetByFormNameWhenFormConfigDoesNotExist() {
    getRequest(FORMS_CONFIGS_PATH + "/" + formConfig.getFormName())
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @DisplayName("should update form config on PUT by form name")
  @Test
  void shouldUpdateFormConfigOnPutByFormName() {
    FormConfig configToUpdate = new FormConfig()
      .withFormName("matchProfilesForm")
      .withConfig(new JsonObject()
        .put("caption", "ui-data-import.summary"));

    postRequest(FORMS_CONFIGS_PATH, formConfig)
      .statusCode(HttpStatus.SC_CREATED)
      .body(notNullValue());

    putRequest(FORMS_CONFIGS_PATH + "/" + formConfig.getFormName(), Json.encode(configToUpdate))
      .statusCode(HttpStatus.SC_OK)
      .body("formName", is(configToUpdate.getFormName()))
      .body("config.caption", is(JsonObject.mapFrom(configToUpdate.getConfig()).getValue("caption")));
  }

  @DisplayName("should return 404 Not Found on PUT by form name when form config does not exist")
  @Test
  void shouldReturnNotFoundOnPutByFormNameWhenFormConfigDoesNotExist() {
    FormConfig configToUpdate = new FormConfig()
      .withFormName("matchProfilesForm")
      .withConfig(new JsonObject()
        .put("caption", "ui-data-import.summary"));

    putRequest(FORMS_CONFIGS_PATH + "/" + formConfig.getFormName(), configToUpdate)
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @DisplayName("should delete form config by form name on DELETE")
  @Test
  void shouldDeleteFormConfigByFormNameOnDelete() {
    postRequest(FORMS_CONFIGS_PATH, formConfig)
      .statusCode(HttpStatus.SC_CREATED)
      .body(notNullValue());

    deleteRequest(FORMS_CONFIGS_PATH + "/" + formConfig.getFormName())
      .statusCode(HttpStatus.SC_NO_CONTENT);
  }

  @DisplayName("should return 404 Not Found on DELETE by form name when form config does not exist")
  @Test
  void shouldReturnNotFoundOnDeleteByFormNameWhenFormConfigDoesNotExist() {
    deleteRequest(FORMS_CONFIGS_PATH + "/" + formConfig.getFormName())
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }
}
