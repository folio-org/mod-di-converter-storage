package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.folio.dataimport.testsupport.postgres.PostgresTestSupport.clearTable;
import static org.folio.rest.jaxrs.model.MarcFieldProtectionSetting.Source;
import static org.folio.support.TestUtil.FIELD_PROTECTION_SETTINGS_PATH;
import static org.folio.support.TestUtil.MARC_FIELD_PROTECTION_SETTINGS_TABLE;
import static org.folio.support.TestUtil.TENANT_ID;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.rest.jaxrs.model.MarcFieldProtectionSetting;
import org.folio.support.AbstractRestTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FieldProtectionSettingsRestTest extends AbstractRestTest {

  private static final MarcFieldProtectionSetting SETTING_1 = new MarcFieldProtectionSetting()
    .withField("001")
    .withIndicator1("")
    .withIndicator2("")
    .withSubfield("")
    .withData("*")
    .withSource(Source.SYSTEM);
  private static final MarcFieldProtectionSetting SETTING_2 = new MarcFieldProtectionSetting()
    .withField("999")
    .withIndicator1("f")
    .withIndicator2("f")
    .withSubfield("*")
    .withData("*")
    .withSource(Source.SYSTEM);
  private static final MarcFieldProtectionSetting SETTING_3 = new MarcFieldProtectionSetting()
    .withField("500")
    .withIndicator1("a")
    .withIndicator2("a")
    .withSubfield("1")
    .withData("*")
    .withSource(Source.USER);

  @Override
  protected void clearTables(VertxTestContext testContext) {
    clearTable(MARC_FIELD_PROTECTION_SETTINGS_TABLE, vertx, TENANT_ID)
      .onComplete(testContext.succeedingThenComplete());
  }

  @DisplayName("should return empty list on GET when there are no settings")
  @Test
  void shouldReturnEmptyListOnGetIfThereIsNoSettings() {
    getRequest(FIELD_PROTECTION_SETTINGS_PATH)
      .statusCode(SC_OK)
      .body("totalRecords", is(0))
      .body("marcFieldProtectionSettings", empty());
  }

  @DisplayName("should return all settings on GET when no query is specified")
  @Test
  void shouldReturnAllSettingsOnGetWhenNoQueryIsSpecified() {
    List<MarcFieldProtectionSetting> settingsToPost = Arrays.asList(SETTING_1, SETTING_2, SETTING_3);
    for (MarcFieldProtectionSetting setting : settingsToPost) {
      postRequest(FIELD_PROTECTION_SETTINGS_PATH, setting)
        .statusCode(SC_CREATED);
    }

    getRequest(FIELD_PROTECTION_SETTINGS_PATH)
      .statusCode(SC_OK)
      .body("totalRecords", is(settingsToPost.size()));
  }

  @DisplayName("should return settings with source SYSTEM when queried by source")
  @Test
  void shouldReturnSettingsWithSourceSystem() {
    List<MarcFieldProtectionSetting> settingsToPost = Arrays.asList(SETTING_1, SETTING_2, SETTING_3);
    for (MarcFieldProtectionSetting setting : settingsToPost) {
      postRequest(FIELD_PROTECTION_SETTINGS_PATH, setting)
        .statusCode(SC_CREATED);
    }

    getRequest(FIELD_PROTECTION_SETTINGS_PATH, Map.of("query", "source=" + Source.SYSTEM))
      .statusCode(SC_OK)
      .body("totalRecords", is(2))
      .body("marcFieldProtectionSettings*.source", everyItem(is(Source.SYSTEM.toString())));
  }

  @DisplayName("should return limited collection on GET with limit parameter")
  @Test
  void shouldReturnLimitedCollectionOnGetWithLimit() {
    List<MarcFieldProtectionSetting> settingsToPost = Arrays.asList(SETTING_1, SETTING_2, SETTING_3);
    for (MarcFieldProtectionSetting setting : settingsToPost) {
      postRequest(FIELD_PROTECTION_SETTINGS_PATH, setting)
        .statusCode(SC_CREATED);
    }

    getRequest(FIELD_PROTECTION_SETTINGS_PATH, Map.of("limit", 2))
      .statusCode(SC_OK)
      .body("marcFieldProtectionSettings.size()", is(2))
      .body("totalRecords", is(settingsToPost.size()));
  }

  @DisplayName("should return 422 Unprocessable Entity on POST when no setting is passed in body")
  @Test
  void shouldReturnBadRequestOnPostWhenNoSettingPassedInBody() {
    postRequest(FIELD_PROTECTION_SETTINGS_PATH, new JsonObject().toString())
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should return 422 Unprocessable Entity on POST when invalid field is passed in body")
  @Test
  void shouldReturnBadRequestOnPostWhenInvalidFieldPassedInBody() {
    JsonObject setting = JsonObject.mapFrom(SETTING_1)
      .put("invalidField", "value");

    postRequest(FIELD_PROTECTION_SETTINGS_PATH, setting.encode())
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should create setting on POST")
  @Test
  void shouldCreateSettingOnPost() {
    postRequest(FIELD_PROTECTION_SETTINGS_PATH, SETTING_3)
      .statusCode(SC_CREATED)
      .body("id", notNullValue())
      .body("field", is(SETTING_3.getField()))
      .body("indicator1", is(SETTING_3.getIndicator1()))
      .body("indicator2", is(SETTING_3.getIndicator2()))
      .body("subfield", is(SETTING_3.getSubfield()))
      .body("data", is(SETTING_3.getData()));
  }

  @DisplayName("should return 422 Unprocessable Entity on PUT when no setting is passed in body")
  @Test
  void shouldReturnBadRequestOnPutWhenNoSettingPassedInBody() {
    putRequest(FIELD_PROTECTION_SETTINGS_PATH + "/" + UUID.randomUUID(), new JsonObject().toString())
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should return 422 Unprocessable Entity on PUT when invalid field is passed in body")
  @Test
  void shouldReturnBadRequestOnPutWhenInvalidFieldPassedInBody() {
    JsonObject invalidFileExtension = JsonObject.mapFrom(SETTING_1)
      .put("invalidField", "value");

    putRequest(FIELD_PROTECTION_SETTINGS_PATH + "/" + UUID.randomUUID(), invalidFileExtension.encode())
      .statusCode(SC_UNPROCESSABLE_ENTITY);
  }

  @DisplayName("should return 404 Not Found on PUT when setting does not exist")
  @Test
  void shouldReturnNotFoundOnPutWhenSettingDoesNotExist() {
    putRequest(FIELD_PROTECTION_SETTINGS_PATH + "/" + UUID.randomUUID(), SETTING_3)
      .statusCode(SC_NOT_FOUND);
  }

  @DisplayName("should return 400 Bad Request on PUT when source is SYSTEM")
  @Test
  void shouldReturnBadRequestIfSourceSystemOnUpdate() {
    var setting = postEntity(FIELD_PROTECTION_SETTINGS_PATH, SETTING_1, SC_CREATED, MarcFieldProtectionSetting.class);
    setting.setIndicator1("3");
    setting.setSource(Source.USER);

    putRequest(FIELD_PROTECTION_SETTINGS_PATH + "/" + setting.getId(), setting)
      .statusCode(SC_BAD_REQUEST);
  }

  @DisplayName("should update existing setting on PUT")
  @Test
  void shouldUpdateExistingSettingOnPut() {
    var setting = postEntity(FIELD_PROTECTION_SETTINGS_PATH, SETTING_3, SC_CREATED, MarcFieldProtectionSetting.class);
    setting.setIndicator1("1");
    putRequest(FIELD_PROTECTION_SETTINGS_PATH + "/" + setting.getId(), setting)
      .statusCode(SC_OK)
      .body("id", is(setting.getId()))
      .body("field", is(setting.getField()))
      .body("indicator1", is(setting.getIndicator1()))
      .body("indicator2", is(setting.getIndicator2()))
      .body("subfield", is(setting.getSubfield()))
      .body("data", is(setting.getData()));
  }

  @DisplayName("should return 404 Not Found on GET by id when setting does not exist")
  @Test
  void shouldReturnNotFoundOnGetByIdWhenSettingDoesNotExist() {
    getRequest(FIELD_PROTECTION_SETTINGS_PATH + "/" + UUID.randomUUID())
      .statusCode(SC_NOT_FOUND);
  }

  @DisplayName("should return existing setting on GET by id")
  @Test
  void shouldReturnExistingFileExtensionOnGetById() {
    var setting = postEntity(FIELD_PROTECTION_SETTINGS_PATH, SETTING_3, SC_CREATED, MarcFieldProtectionSetting.class);

    getRequest(FIELD_PROTECTION_SETTINGS_PATH + "/" + setting.getId())
      .statusCode(SC_OK)
      .body("id", is(setting.getId()))
      .body("field", is(setting.getField()))
      .body("indicator1", is(setting.getIndicator1()))
      .body("indicator2", is(setting.getIndicator2()))
      .body("subfield", is(setting.getSubfield()))
      .body("data", is(setting.getData()));
  }

  @DisplayName("should return 404 Not Found on DELETE when setting does not exist")
  @Test
  void shouldReturnNotFoundOnDeleteWhenSettingDoesNotExist() {
    deleteRequest(FIELD_PROTECTION_SETTINGS_PATH + "/" + UUID.randomUUID())
      .statusCode(SC_NOT_FOUND);
  }

  @DisplayName("should delete existing setting on DELETE")
  @Test
  void shouldDeleteExistingFileExtensionOnDelete() {
    var setting = postEntity(FIELD_PROTECTION_SETTINGS_PATH, SETTING_3, SC_CREATED, MarcFieldProtectionSetting.class);
    deleteRequest(FIELD_PROTECTION_SETTINGS_PATH + "/" + setting.getId())
      .statusCode(SC_NO_CONTENT);
  }

  @DisplayName("should return 400 Bad Request on DELETE when source is SYSTEM")
  @Test
  void shouldReturnBadRequestIfSourceSystemOnDelete() {
    var setting = postEntity(FIELD_PROTECTION_SETTINGS_PATH, SETTING_1, SC_CREATED, MarcFieldProtectionSetting.class);
    deleteRequest(FIELD_PROTECTION_SETTINGS_PATH + "/" + setting.getId())
      .statusCode(SC_BAD_REQUEST);
  }
}
