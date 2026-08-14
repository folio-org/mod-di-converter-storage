package org.folio.support;

import static org.folio.dataimport.testsupport.vertx.VertxTestUtil.await;
import static org.folio.support.TestUtil.clearAllProfileTables;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.dataimport.testsupport.postgres.PostgresExtension;
import org.folio.dataimport.testsupport.tenant.TenantTestSupport;
import org.folio.rest.RestVerticle;
import org.folio.rest.tools.utils.ModuleName;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.spring.SpringContextUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Base class for DAO/service layer tests that need a real PostgreSQL schema and Spring DI.
 *
 * <p>Boots the shared PostgreSQL container (via {@link PostgresExtension}), deploys a
 * {@link RestVerticle} to run the Tenant API and create the module schema, then initialises the
 * Spring application context so subclasses can {@code @Autowired} DAOs and services directly.
 *
 * <p>After each test, all profile tables are truncated so tests start with a clean slate.
 * Contrast with {@link AbstractRestTest}, which tests the HTTP layer via
 * RestAssured and has no Spring context.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ApplicationTestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractUnitTest {

  protected static final String TENANT_ID = "diku";
  protected static final String TOKEN = "token";

  @RegisterExtension
  protected static final PostgresExtension POSTGRES = new PostgresExtension();

  protected Vertx vertx;

  @BeforeAll
  void setUpClass() {
    vertx = Vertx.vertx();
    int port = NetworkUtils.nextFreePort();
    String connectionUrl = "http://localhost:" + port;

    await(vertx.deployVerticle(RestVerticle.class.getName(),
      new DeploymentOptions().setConfig(new JsonObject().put("http.port", port))));
    await(TenantTestSupport.enableTenant(vertx, connectionUrl, TENANT_ID, TOKEN, constructModuleName()));

    SpringContextUtil.autowireDependenciesFromFirstContext(this, vertx);
  }

  @AfterEach
  void afterEach() {
    await(clearAllProfileTables(vertx));
  }

  @AfterAll
  void tearDownClass() {
    if (vertx != null) {
      await(vertx.close());
    }
  }

  private static String constructModuleName() {
    return ModuleName.getModuleName().replace("_", "-") + "-" + ModuleName.getModuleVersion();
  }
}
