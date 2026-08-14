package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rest.jaxrs.model.ProfileType.ACTION_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.JOB_PROFILE;
import static org.folio.rest.jaxrs.model.ProfileType.MAPPING_PROFILE;
import static org.folio.support.TestUtil.ACTION_PROFILES_PATH;
import static org.folio.support.TestUtil.JOB_PROFILES_PATH;
import static org.folio.support.TestUtil.JOB_PROFILE_ID_PARAM;
import static org.folio.support.TestUtil.MAPPING_PROFILES_PATH;
import static org.folio.support.TestUtil.MATCH_PROFILES_PATH;
import static org.folio.support.TestUtil.PROFILE_SNAPSHOT_PATH;
import static org.folio.support.TestUtil.PROFILE_TYPE_PARAM;
import static org.folio.support.TestUtil.readFileFromPath;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileSnapshotWrapper;
import org.folio.support.AbstractRestTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfileImportRestTest extends AbstractRestTest {

  private static final String PROFILE_SNAPSHOT_FILE_PATH = "src/test/resources/snapshots/";

  @DisplayName("should import profile snapshot")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldImportProfileSnapshot() throws IOException {
    String mappingProfileId = UUID.randomUUID().toString();
    String jobProfileId = UUID.randomUUID().toString();
    String matchProfileId = UUID.randomUUID().toString();
    String actionProfileId = UUID.randomUUID().toString();

    JsonObject importWrapper = constructProfileWrapper(PROFILE_SNAPSHOT_FILE_PATH + "profileSnapshot.json",
      jobProfileId, matchProfileId, actionProfileId, mappingProfileId);

    JsonObject postProfileSnapshotWrapper = new JsonObject(postRequest(PROFILE_SNAPSHOT_PATH, importWrapper.encode())
      .statusCode(SC_CREATED)
      .extract().body().asPrettyString());

    removeWrapperId(importWrapper);
    removeWrapperId(postProfileSnapshotWrapper);

    assertThat(postProfileSnapshotWrapper).isNotEqualTo(importWrapper);

    JsonObject resultSnapshotWrapper = new JsonObject(getRequest(PROFILE_SNAPSHOT_PATH + "/" + jobProfileId,
      Map.of(PROFILE_TYPE_PARAM, JOB_PROFILE.value(), JOB_PROFILE_ID_PARAM, jobProfileId))
      .statusCode(SC_OK)
      .extract().body().asPrettyString());

    removeWrapperId(resultSnapshotWrapper);

    assertThat(postProfileSnapshotWrapper).isEqualTo(resultSnapshotWrapper);

    prepareProfileSnapshotToCompare(importWrapper);
    prepareProfileSnapshotToCompare(resultSnapshotWrapper);

    assertThat(importWrapper).isEqualTo(resultSnapshotWrapper);

    getRequest(MAPPING_PROFILES_PATH + "/" + mappingProfileId)
      .statusCode(SC_OK);

    getRequest(ACTION_PROFILES_PATH + "/" + actionProfileId)
      .statusCode(SC_OK);

    getRequest(MATCH_PROFILES_PATH + "/" + matchProfileId)
      .statusCode(SC_OK);

    getRequest(JOB_PROFILES_PATH + "/" + jobProfileId)
      .statusCode(SC_OK);
  }

  @DisplayName("should import profile and update action profile if it already exists")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldImportProfileAndUpdateActionProfileIfAlreadyExist()
    throws IOException {
    String mappingProfileId = UUID.randomUUID().toString();
    String jobProfileId = UUID.randomUUID().toString();
    String matchProfileId = UUID.randomUUID().toString();
    String actionProfileId = UUID.randomUUID().toString();

    MappingProfileUpdateDto existingMappingProfile = new MappingProfileUpdateDto()
      .withProfile(new MappingProfile().withId(UUID.randomUUID().toString())
        .withName("testMappingProfile2").withDescription("test-description")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE));

    existingMappingProfile =
      postProfile(existingMappingProfile, MAPPING_PROFILES_PATH).body().as(MappingProfileUpdateDto.class);

    ActionProfileUpdateDto existingActionProfile = new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withId(actionProfileId)
        .withName("testActionProfile2").withDescription("test-description")
        .withAction(ActionProfile.Action.CREATE)
        .withFolioRecord(ActionProfile.FolioRecord.INSTANCE))
      .withAddedRelations(List.of(new ProfileAssociation()
        .withMasterProfileId(actionProfileId)
        .withMasterProfileType(ACTION_PROFILE)
        .withDetailProfileId(existingMappingProfile.getId())
        .withDetailProfileType(MAPPING_PROFILE)));

    existingActionProfile =
      postProfile(existingActionProfile, ACTION_PROFILES_PATH).body().as(ActionProfileUpdateDto.class);

    JsonObject importWrapper = constructProfileWrapper(PROFILE_SNAPSHOT_FILE_PATH + "profileSnapshot.json",
      jobProfileId, matchProfileId, actionProfileId, mappingProfileId);

    postRequest(PROFILE_SNAPSHOT_PATH, importWrapper.encode())
      .statusCode(SC_CREATED);

    JsonObject resultSnapshotWrapper = new JsonObject(getRequest(PROFILE_SNAPSHOT_PATH + "/" + jobProfileId,
      Map.of(PROFILE_TYPE_PARAM, JOB_PROFILE.value(), JOB_PROFILE_ID_PARAM, jobProfileId))
      .statusCode(SC_OK)
      .extract().body().asPrettyString());

    prepareProfileSnapshotToCompare(importWrapper);
    prepareProfileSnapshotToCompare(resultSnapshotWrapper);

    assertThat(importWrapper).isEqualTo(resultSnapshotWrapper);

    ActionProfile overlayActionProfile = getRequest(ACTION_PROFILES_PATH + "/" + actionProfileId)
      .statusCode(SC_OK)
      .extract().body().as(ActionProfile.class);

    assertThat(overlayActionProfile.getMetadata().getUpdatedDate())
      .isNotEqualTo(existingActionProfile.getProfile().getMetadata().getUpdatedDate());

    getRequest(MAPPING_PROFILES_PATH + "/" + mappingProfileId)
      .statusCode(SC_OK);

    getRequest(ACTION_PROFILES_PATH + "/" + actionProfileId)
      .statusCode(SC_OK);

    getRequest(MATCH_PROFILES_PATH + "/" + matchProfileId)
      .statusCode(SC_OK);

    getRequest(JOB_PROFILES_PATH + "/" + jobProfileId)
      .statusCode(SC_OK);
  }

  @DisplayName("should import profile and update job profile if it already exists")
  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldImportProfileAndUpdateJobProfileIfAlreadyExist() throws IOException {
    final String mappingProfileId = UUID.randomUUID().toString();
    final String jobProfileId = UUID.randomUUID().toString();
    final String matchProfileId = UUID.randomUUID().toString();
    final String actionProfileId = UUID.randomUUID().toString();

    MappingProfileUpdateDto existingMappingProfile = new MappingProfileUpdateDto()
      .withProfile(new MappingProfile().withId(mappingProfileId)
        .withName("testMappingProfile3").withDescription("test-description")
        .withIncomingRecordType(EntityType.MARC_BIBLIOGRAPHIC)
        .withExistingRecordType(EntityType.INSTANCE));

    existingMappingProfile =
      postProfile(existingMappingProfile, MAPPING_PROFILES_PATH).body().as(MappingProfileUpdateDto.class);

    ActionProfileUpdateDto existingActionProfile = new ActionProfileUpdateDto()
      .withProfile(new ActionProfile().withId(actionProfileId)
        .withName("testActionProfile3").withDescription("test-description")
        .withAction(ActionProfile.Action.CREATE)
        .withFolioRecord(ActionProfile.FolioRecord.INSTANCE))
      .withAddedRelations(List.of(new ProfileAssociation()
        .withMasterProfileId(actionProfileId)
        .withMasterProfileType(ACTION_PROFILE)
        .withDetailProfileId(mappingProfileId)
        .withDetailProfileType(MAPPING_PROFILE)));

    existingActionProfile =
      postProfile(existingActionProfile, ACTION_PROFILES_PATH).body().as(ActionProfileUpdateDto.class);

    JobProfileUpdateDto existingJobProfile = new JobProfileUpdateDto()
      .withProfile(new JobProfile().withId(jobProfileId)
        .withName("testJobProfile3").withDescription("test-description")
        .withDataType(JobProfile.DataType.MARC))
      .withAddedRelations(List.of(new ProfileAssociation()
        .withMasterProfileId(jobProfileId)
        .withMasterProfileType(JOB_PROFILE)
        .withDetailProfileId(actionProfileId)
        .withDetailProfileType(ACTION_PROFILE)));

    existingJobProfile =
      postProfile(existingJobProfile, JOB_PROFILES_PATH).body().as(JobProfileUpdateDto.class);

    JsonObject importWrapper = constructProfileWrapper(PROFILE_SNAPSHOT_FILE_PATH + "profileSnapshot.json",
      jobProfileId, matchProfileId, actionProfileId, mappingProfileId);

    postRequest(PROFILE_SNAPSHOT_PATH, importWrapper.encode())
      .statusCode(SC_CREATED);

    JsonObject resultSnapshotWrapper = new JsonObject(getRequest(PROFILE_SNAPSHOT_PATH + "/" + jobProfileId,
      Map.of(PROFILE_TYPE_PARAM, JOB_PROFILE.value(), JOB_PROFILE_ID_PARAM, jobProfileId))
      .statusCode(SC_OK)
      .extract().body().asPrettyString());

    prepareProfileSnapshotToCompare(importWrapper);
    prepareProfileSnapshotToCompare(resultSnapshotWrapper);

    assertThat(importWrapper).isEqualTo(resultSnapshotWrapper);

    MappingProfile overlayMappingProfile = getRequest(MAPPING_PROFILES_PATH + "/" + mappingProfileId)
      .statusCode(SC_OK)
      .extract().body().as(MappingProfile.class);

    assertThat(overlayMappingProfile.getMetadata().getUpdatedDate())
      .isNotEqualTo(existingMappingProfile.getProfile().getMetadata().getUpdatedDate());

    ActionProfile overlayActionProfile = getRequest(ACTION_PROFILES_PATH + "/" + actionProfileId)
      .statusCode(SC_OK)
      .extract().body().as(ActionProfile.class);

    assertThat(overlayActionProfile.getMetadata().getUpdatedDate())
      .isNotEqualTo(existingActionProfile.getProfile().getMetadata().getUpdatedDate());

    JobProfile overlayJobProfile = getRequest(JOB_PROFILES_PATH + "/" + jobProfileId)
      .statusCode(SC_OK)
      .extract().body().as(JobProfile.class);

    assertThat(overlayJobProfile.getMetadata().getUpdatedDate())
      .isNotEqualTo(existingJobProfile.getProfile().getMetadata().getUpdatedDate());

    getRequest(MATCH_PROFILES_PATH + "/" + matchProfileId)
      .statusCode(SC_OK);
  }

  @DisplayName("should not import profile snapshot when content type is not JOB_PROFILE")
  @Test
  void shouldNotImportProfileSnapshotIfNotJobProfileType() {
    ProfileSnapshotWrapper profileSnapshotWrapper =
      new ProfileSnapshotWrapper().withContentType(ACTION_PROFILE).withContent(new ActionProfile());
    postRequest(PROFILE_SNAPSHOT_PATH, profileSnapshotWrapper)
      .statusCode(SC_BAD_REQUEST)
      .body(is(String.format("Cannot import profile snapshot of %s required type is %s", ACTION_PROFILE, JOB_PROFILE)));
  }

  @DisplayName("should return 422 Unprocessable Entity when profile snapshot fails validation during import")
  @Test
  void shouldValidateProfileSnapshotDuringImport() throws IOException {
    String mappingProfileId = UUID.randomUUID().toString();
    String jobProfileId = UUID.randomUUID().toString();
    String matchProfileId = UUID.randomUUID().toString();
    String actionProfileId = UUID.randomUUID().toString();

    JsonObject importWrapper =
      constructProfileWrapper(PROFILE_SNAPSHOT_FILE_PATH + "invalidProfileSnapshotAssociation.json",
        jobProfileId, matchProfileId, actionProfileId, mappingProfileId);

    postRequest(PROFILE_SNAPSHOT_PATH, importWrapper.encode())
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body("errors[0].message", is("Modify action cannot be used as a standalone action"));
  }

  @DisplayName("should return 400 Bad Request with error message when profile snapshot content is invalid")
  @Test
  void shouldReturnErrorMessageIfInvalidProfileContent() throws IOException {
    String mappingProfileId = UUID.randomUUID().toString();
    String jobProfileId = UUID.randomUUID().toString();
    String matchProfileId = UUID.randomUUID().toString();
    String actionProfileId = UUID.randomUUID().toString();

    JsonObject importWrapper =
      constructProfileWrapper(PROFILE_SNAPSHOT_FILE_PATH + "invalidProfileSnapshotContent.json",
        jobProfileId, matchProfileId, actionProfileId, mappingProfileId);

    postRequest(PROFILE_SNAPSHOT_PATH, importWrapper.encode())
      .statusCode(SC_BAD_REQUEST)
      .body(startsWith("Cannot map profile content, error: "));
  }

  private JsonObject constructProfileWrapper(String profilePath, String jobProfileId, String matchProfileId,
                                             String actionProfileId, String mappingProfileId)
    throws IOException {
    return new JsonObject(readFileFromPath(profilePath)
      .replace("#(jobProfileId)", jobProfileId)
      .replace("#(matchProfileId)", matchProfileId)
      .replace("#(actionProfileId)", actionProfileId)
      .replace("#(mappingProfileId)", mappingProfileId)
      .replace("#(uuid)", UUID.randomUUID().toString()));
  }

  private void removeWrapperId(JsonObject importWrapper) {
    importWrapper.remove("id");

    JsonArray childSnapshotWrapper = importWrapper.getJsonArray("childSnapshotWrappers");
    if (!childSnapshotWrapper.isEmpty()) {
      for (Object object : childSnapshotWrapper) {
        removeWrapperId((JsonObject) object);
      }
    }
  }

  private void prepareProfileSnapshotToCompare(JsonObject importWrapper) {
    removeRedundantFieldsFormSnapshotWrapper(importWrapper);

    if (importWrapper.containsKey("content")) {
      removeRedundantFieldsFormSnapshotWrapper(importWrapper.getJsonObject("content"));
    }

    JsonArray childSnapshotWrapper = importWrapper.getJsonArray("childSnapshotWrappers");
    if (!childSnapshotWrapper.isEmpty()) {
      for (Object object : childSnapshotWrapper) {
        prepareProfileSnapshotToCompare((JsonObject) object);
      }
    }
  }

  private static void removeRedundantFieldsFormSnapshotWrapper(JsonObject importWrapper) {
    importWrapper.remove("id");
    importWrapper.remove("profileWrapperId");
    importWrapper.remove("userInfo");
    importWrapper.remove("metadata");
  }

  private <T> ExtractableResponse<Response> postProfile(T profileDto, String profileUrl) {
    return postRequest(profileUrl, profileDto)
      .statusCode(SC_CREATED)
      .extract();
  }
}
