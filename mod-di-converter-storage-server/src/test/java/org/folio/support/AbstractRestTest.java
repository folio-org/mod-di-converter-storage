package org.folio.support;

import static org.folio.support.TestUtil.ACTION_PROFILES_PATH;
import static org.folio.support.TestUtil.ASSOCIATED_PROFILES_PATH;
import static org.folio.support.TestUtil.JOB_PROFILES_PATH;
import static org.folio.support.TestUtil.MAPPING_PROFILES_PATH;
import static org.folio.support.TestUtil.MATCH_PROFILES_PATH;
import static org.folio.support.TestUtil.clearAllProfileTables;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Map;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.folio.dataimport.testsupport.rest.BaseRestTest;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.JobProfile;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingProfile;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.MatchProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileType;
import org.folio.rest.tools.utils.ModuleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Base class for module REST API tests: deploys the {@code RestVerticle} against the shared
 * PostgreSQL/Kafka containers and WireMock server provided by
 * {@link org.folio.dataimport.testsupport.rest.BaseRestTest}, stubbing mod-users
 * lookups needed by the module under test.
 */
@ExtendWith(VertxExtension.class)
public abstract class AbstractRestTest extends BaseRestTest {

  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String GET_USER_URL = "/users\\?query=id==" + USER_ID;

  private final JsonObject userResponse = new JsonObject()
    .put("users",
      new JsonArray().add(new JsonObject()
        .put("username", "@janedoe")
        .put("personal", new JsonObject().put("firstName", "Jane").put("lastName", "Doe"))))
    .put("totalRecords", 1);

  @Override
  protected String getModuleName() {
    return ModuleName.getModuleName() + "-1.0.0";
  }

  @Override
  protected Map<String, String> getExtraSpecHeaders() {
    return Map.of(RestVerticle.OKAPI_USERID_HEADER, USER_ID);
  }

  @BeforeEach
  protected void setUp(VertxTestContext testContext) {
    stubGetJson(GET_USER_URL, userResponse.toString());
    clearTables(testContext);
  }

  /**
   * Hook for subclasses to customise table cleanup between tests. Must call
   * {@code testContext.completeNow()} (or fail it) once done. The default implementation
   * clears all profile tables; subclasses may override to skip cleanup (e.g. default-profile tests)
   * or to clear only a specific table.
   *
   * @param testContext the current test context
   */
  protected void clearTables(VertxTestContext testContext) {
    clearAllProfileTables(vertx).onComplete(testContext.succeedingThenComplete());
  }

  protected ProfileAssociation postProfileAssociation(ProfileAssociation profileAssociation,
                                                      ProfileType masterType, ProfileType detailType) {
    return postRequest(ASSOCIATED_PROFILES_PATH, profileAssociation,
      Map.of("master", masterType.value(), "detail", detailType.value()))
      .statusCode(HttpStatus.SC_CREATED)
      .extract().body().as(ProfileAssociation.class);
  }

  protected MappingProfileUpdateDto postMappingProfile(MappingProfileUpdateDto mappingProfileUpdateDto) {
    return postEntity(MAPPING_PROFILES_PATH, mappingProfileUpdateDto, 201, MappingProfileUpdateDto.class);
  }

  protected ActionProfileUpdateDto postActionProfile(ActionProfileUpdateDto actionProfileUpdateDto) {
    return postEntity(ACTION_PROFILES_PATH, actionProfileUpdateDto, 201, ActionProfileUpdateDto.class);
  }

  protected MatchProfileUpdateDto postMatchProfile(MatchProfileUpdateDto matchProfileUpdateDto) {
    return postEntity(MATCH_PROFILES_PATH, matchProfileUpdateDto, 201, MatchProfileUpdateDto.class);
  }

  protected JobProfileUpdateDto postJobProfile(JobProfileUpdateDto jobProfileUpdateDto) {
    return postEntity(JOB_PROFILES_PATH, jobProfileUpdateDto, 201, JobProfileUpdateDto.class);
  }

  protected MappingProfile getMappingProfile(String id) {
    return getEntity(MAPPING_PROFILES_PATH + "/" + id, HttpStatus.SC_OK, MappingProfile.class);
  }

  protected JobProfile getJobProfile(String id) {
    return getEntity(JOB_PROFILES_PATH + "/" + id, HttpStatus.SC_OK, JobProfile.class);
  }

  protected ActionProfile getActionProfile(String id) {
    return getEntity(ACTION_PROFILES_PATH + "/" + id, HttpStatus.SC_OK, ActionProfile.class);
  }

  protected MatchProfile getMatchProfile(String id) {
    return getEntity(MATCH_PROFILES_PATH + "/" + id, HttpStatus.SC_OK, MatchProfile.class);
  }
}
