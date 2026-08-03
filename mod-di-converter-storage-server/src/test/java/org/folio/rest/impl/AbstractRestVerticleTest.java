package org.folio.rest.impl;

import static org.folio.rest.impl.ActionProfileTest.ACTION_PROFILES_PATH;
import static org.folio.rest.impl.MappingProfileTest.MAPPING_PROFILES_PATH;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import java.util.UUID;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.ActionProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.ModuleName;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.tools.utils.VertxUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;

public abstract class AbstractRestVerticleTest {

  public static final String TENANT_ID = "diku";
  public static Vertx vertx;
  public static RequestSpecification spec;

  private static final String GET_USER_URL = "/users?query=id==";
  private static final String USER_ID = UUID.randomUUID().toString();
  private static final int PORT = NetworkUtils.nextFreePort();
  private static final int MOCK_PORT = NetworkUtils.nextFreePort();
  private static final String BASE_URL = "http://localhost:";
  private static final String OKAPI_URL = BASE_URL + PORT;
  private static final String MOCK_URL = BASE_URL + MOCK_PORT;

  @Rule
  public WireMockRule mockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .port(MOCK_PORT)
      .notifier(new Slf4jNotifier(true)));

  private final JsonObject userResponse = new JsonObject()
    .put("users",
      new JsonArray().add(new JsonObject()
        .put("username", "@janedoe")
        .put("personal", new JsonObject().put("firstName", "Jane").put("lastName", "Doe"))))
    .put("totalRecords", 1);

  @BeforeClass
  public static void setUpClass(final TestContext context) {
    vertx = Vertx.vertx();
    PostgresClient.closeAllClients();

    PostgresClient.setPostgresTester(new PostgresTesterContainer());

    var tenantClient = prepareTenantClient();
    DeploymentOptions restVerticleDeploymentOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put("http.port", PORT));

    Async async = context.async();
    vertx.deployVerticle(RestVerticle.class.getName(), restVerticleDeploymentOptions)
      .compose(deploymentId -> {
        TenantAttributes tenantAttributes = new TenantAttributes();
        tenantAttributes.setModuleTo(ModuleName.getModuleName() + "-1.0.0");

        return tenantClient.postTenant(tenantAttributes);
      })
      .compose(postTenantResponse -> {
        int statusCode = postTenantResponse.statusCode();

        if (statusCode == 204) {
          return Future.succeededFuture().mapEmpty();
        }

        if (statusCode == 201) {
          TenantJob tenantJob = postTenantResponse.bodyAsJson(TenantJob.class);
          return tenantClient.getTenantByOperationId(tenantJob.getId(), 60000)
            .onSuccess(jobStatusResponse -> {
              TenantJob completedJob = jobStatusResponse.bodyAsJson(TenantJob.class);
              context.assertTrue(completedJob.getComplete(), "Tenant job should be complete");

              String error = completedJob.getError();
              if (error != null) {
                context.assertEquals("Failed to make post tenant. Received status code 400", error);
              }
            })
            .mapEmpty();
        }

        String errorMessage = "Failed to make post tenant. Received status code " + statusCode;
        context.fail(errorMessage + ". Body: " + postTenantResponse.bodyAsString());
        return Future.failedFuture(errorMessage); // Проваливаем Future
      })
      .onSuccess(v -> {
        async.complete();
      })
      .onFailure(context::fail);

    spec = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .setBaseUri(OKAPI_URL)
      .addHeader(RestVerticle.OKAPI_HEADER_TENANT, TENANT_ID)
      .addHeader(RestVerticle.OKAPI_USERID_HEADER, USER_ID)
      .addHeader(RestVerticle.OKAPI_HEADER_PREFIX + "-url", MOCK_URL)
      .build();
  }

  @AfterClass
  public static void tearDownClass(final TestContext context) {
    Async async = context.async();
    vertx.close()
      .onSuccess(v -> {
        PostgresClient.stopPostgresTester();
        async.complete();
      })
      .onFailure(context::fail);
  }

  @Before
  public void setUp(TestContext testContext) {
    WireMock.stubFor(WireMock.get(GET_USER_URL + USER_ID)
      .willReturn(WireMock.okJson(userResponse.toString())));
    clearTables(testContext);
  }

  protected void clearTables(TestContext context) { }

  protected MappingProfileUpdateDto postMappingProfile(MappingProfileUpdateDto mappingProfileUpdateDto) {
    return postProfile(mappingProfileUpdateDto, MAPPING_PROFILES_PATH, MappingProfileUpdateDto.class);
  }

  protected ActionProfileUpdateDto postActionProfile(ActionProfileUpdateDto actionProfileUpdateDto) {
    return postProfile(actionProfileUpdateDto, ACTION_PROFILES_PATH, ActionProfileUpdateDto.class);
  }

  protected <T> T postProfile(T profile, String path, Class<T> clazz) {
    return RestAssured.given()
      .spec(spec)
      .body(profile)
      .when()
      .post(path)
      .body()
      .as(clazz);
  }

  private static TenantClient prepareTenantClient() {
    WebClientOptions options = new WebClientOptions();
    options.setLogActivity(true);
    options.setKeepAlive(true);
    options.setConnectTimeout(2000);
    options.setIdleTimeout(5000);
    var webClient = WebClient.create(VertxUtils.getVertxFromContextOrNew(), options);

    return new TenantClient(OKAPI_URL, TENANT_ID, "dummy-token", webClient);
  }
}
