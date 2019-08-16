package org.folio.finc;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.parsing.Parser;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.folio.finc.config.ConfigMetadataCollectionsIT;
import org.folio.finc.config.ConfigMetadataSourcesIT;
import org.folio.finc.config.TinyMetadataSourcesIT;
import org.folio.finc.select.FincSelectFilesIT;
import org.folio.finc.select.FincSelectFiltersIT;
import org.folio.finc.select.IsilsIT;
import org.folio.finc.select.SelectMetadataCollectionsIT;
import org.folio.finc.select.SelectMetadataSourcesIT;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.utils.Constants;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  ConfigMetadataCollectionsIT.class,
  ConfigMetadataSourcesIT.class,
  FincSelectFilesIT.class,
  FincSelectFiltersIT.class,
  IsilsIT.class,
  SelectMetadataCollectionsIT.class,
  SelectMetadataSourcesIT.class,
  TinyMetadataSourcesIT.class
})
public class ApiTestSuite {

  public static final String TENANT_UBL = "ubl";
  public static final String TENANT_DIKU = "diku";

  private static final int okapiPort = NetworkUtils.nextFreePort();
  private static Vertx vertx;
  private static int port;
  private static boolean initialised = false;

  @BeforeClass
  public static void before()
      throws IOException, InterruptedException, ExecutionException, TimeoutException {

    if (vertx == null) {
      vertx = Vertx.vertx();
    }

    PostgresClient.setIsEmbedded(true);
    PostgresClient.setEmbeddedPort(NetworkUtils.nextFreePort());
    PostgresClient client = PostgresClient.getInstance(vertx);
    client.startEmbeddedPostgres();

    initialised = true;
    port = NetworkUtils.nextFreePort();

    RestAssured.reset();
    RestAssured.baseURI = "http://localhost";
    RestAssured.port = port;
    RestAssured.defaultParser = Parser.JSON;

    DeploymentOptions options = new DeploymentOptions();
    options.setConfig(new JsonObject().put("http.port", port));
    options.setWorker(true);

    startVerticle(options);

    prepareTenants();
  }

  @AfterClass
  public static void after() throws InterruptedException, ExecutionException, TimeoutException {

    initialised = false;
    CompletableFuture<String> undeploymentComplete = new CompletableFuture<>();

    vertx.close(
        res -> {
          if (res.succeeded()) {
            undeploymentComplete.complete(null);
          } else {
            undeploymentComplete.completeExceptionally(res.cause());
          }
        });

    undeploymentComplete.get(20, TimeUnit.SECONDS);

    PostgresClient.stopEmbeddedPostgres();
  }

  public static boolean isNotInitialised() {
    return !initialised;
  }

  private static void startVerticle(DeploymentOptions options)
      throws InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<String> deploymentComplete = new CompletableFuture<>();

    vertx.deployVerticle(
        RestVerticle.class.getName(),
        options,
        res -> {
          if (res.succeeded()) {
            deploymentComplete.complete(res.result());
          } else {
            deploymentComplete.completeExceptionally(res.cause());
          }
        });

    deploymentComplete.get(30, TimeUnit.SECONDS);
  }

  private static void prepareTenants() {
    String url = RestAssured.baseURI + ":" + RestAssured.port;
    try {
      CompletableFuture fincFuture = new CompletableFuture();
      CompletableFuture ublFuture = new CompletableFuture();
      CompletableFuture dikuFuture = new CompletableFuture();
      TenantClient tenantClientFinc =
          new TenantClient(url, Constants.MODULE_TENANT, Constants.MODULE_TENANT);
      TenantClient tenantClientDiku = new TenantClient(url, TENANT_DIKU, TENANT_DIKU);
      TenantClient tenantClientUbl = new TenantClient(url, TENANT_UBL, TENANT_UBL);
      tenantClientFinc.postTenant(null, postTenantRes -> fincFuture.complete(postTenantRes));
      tenantClientDiku.postTenant(null, postTenantRes -> dikuFuture.complete(postTenantRes));
      tenantClientUbl.postTenant(null, postTenantRes -> ublFuture.complete(postTenantRes));
      fincFuture.get(30, TimeUnit.SECONDS);
      dikuFuture.get(30, TimeUnit.SECONDS);
      ublFuture.get(30, TimeUnit.SECONDS);
    } catch (Exception e) {
      assert false;
    }
  }
}