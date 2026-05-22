package org.folio.unit;

import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.ModuleName;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.spring.SpringContextUtil;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public abstract class AbstractUnitTest {

  protected static final String TENANT_ID = "diku";
  protected static final String HTTP_PORT = "http.port";
  protected static final String TOKEN = "token";
  protected static Vertx vertx = Vertx.vertx();

  public AbstractUnitTest() {
    Context vertxContext = vertx.getOrCreateContext();
    SpringContextUtil.init(vertx, vertxContext, ApplicationTestConfig.class);
    SpringContextUtil.autowireDependencies(this, vertxContext);
  }

  @BeforeClass
  public static void beforeClass(TestContext context) {
    Async async = context.async();
    int port = NetworkUtils.nextFreePort();
    String okapiUrl = "http://localhost:" + port;
    PostgresClient.closeAllClients();
    PostgresClient.setPostgresTester(new PostgresTesterContainer());
    TenantClient tenantClient = new TenantClient(okapiUrl, TENANT_ID, TOKEN);

    final DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject().put(HTTP_PORT, port));
    vertx.deployVerticle(RestVerticle.class.getName(), options)
      .compose(deploymentId -> {
        TenantAttributes tenantAttributes = new TenantAttributes();
        tenantAttributes.setModuleTo(constructModuleName());

        return tenantClient.postTenant(tenantAttributes);
      })
      .compose(postTenantResponse -> {
        int statusCode = postTenantResponse.statusCode();

        if (statusCode == 204) {
          return Future.succeededFuture();
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
            });
        }

        String errorMessage = "Failed to make post tenant. Received status code " + statusCode;
        context.fail(errorMessage + ". Body: " + postTenantResponse.bodyAsString());
        return Future.failedFuture(errorMessage);
      })
      .onSuccess(v -> {
        async.complete();
      })
      .onFailure(context::fail);
  }

  private static String constructModuleName() {
    String result = ModuleName.getModuleName().replace("_", "-");
    return result + "-" + ModuleName.getModuleVersion();
  }

  @After
  public abstract void afterTest(TestContext context);
}
