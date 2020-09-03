package org.folio.finc.select;

import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.finc.ApiTestSuite;
import org.folio.finc.select.verticles.UnselectMetadataSourceVerticle;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.FincConfigMetadataCollection;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.utils.Constants;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class UnselectMetadataSourceVerticleTest {

  private static final String TENANT_UBL = "ubl";
  private static Vertx vertx = Vertx.vertx();
  private static UnselectMetadataSourceVerticle cut;
  @Rule
  public Timeout timeout = Timeout.seconds(10);
  private static SelectMetadataSourceVerticleTestHelper selectMetadataSourceVerticleTestHelper;

  @BeforeClass
  public static void setUp(TestContext context) {
    selectMetadataSourceVerticleTestHelper = new SelectMetadataSourceVerticleTestHelper();
    vertx = Vertx.vertx();
    try {
      PostgresClient.setIsEmbedded(true);
      PostgresClient instance = PostgresClient.getInstance(vertx);
      instance.startEmbeddedPostgres();
    } catch (Exception e) {
      context.fail(e);
      return;
    }

    Async async = context.async(2);
    int port = NetworkUtils.nextFreePort();

    RestAssured.reset();
    RestAssured.baseURI = "http://localhost";
    RestAssured.port = port;
    RestAssured.defaultParser = Parser.JSON;

    String url = "http://localhost:" + port;
    TenantClient tenantClientFinc =
        new TenantClient(url, Constants.MODULE_TENANT, Constants.MODULE_TENANT);
    TenantClient tenantClientUBL = new TenantClient(url, TENANT_UBL, TENANT_UBL);
    DeploymentOptions options =
        new DeploymentOptions().setConfig(new JsonObject().put("http.port", port)).setWorker(true);

    vertx.deployVerticle(
        RestVerticle.class.getName(),
        options,
        res -> {
          try {
            tenantClientFinc.postTenant(
                new TenantAttributes().withModuleTo(ApiTestSuite.getModuleVersion()),
                postTenantRes -> {
                  Future<Void> future =
                      selectMetadataSourceVerticleTestHelper.writeDataToDB(context, vertx);
                  future.onComplete(
                      ar -> {
                        if (ar.succeeded()) {
                          async.countDown();
                        }
                      });
                });
            tenantClientUBL.postTenant(
                new TenantAttributes().withModuleTo(ApiTestSuite.getModuleVersion()),
                postTenantRes -> async.countDown()
            );
          } catch (Exception e) {
            context.fail(e);
          }
        });
    cut = new UnselectMetadataSourceVerticle(vertx, vertx.getOrCreateContext());
  }

  @AfterClass
  public static void teardown(TestContext context) {
    RestAssured.reset();
    Async async = context.async();
    vertx.close(
        context.asyncAssertSuccess(
            res -> {
              PostgresClient.stopEmbeddedPostgres();
              async.complete();
            }));
  }

  @Before
  public void before(TestContext context) {
    Async async = context.async();
    JsonObject cfg2 = vertx.getOrCreateContext().config();
    cfg2.put("tenantId", TENANT_UBL);
    cfg2.put(
        "metadataSourceId", SelectMetadataSourceVerticleTestHelper.getMetadataSource2().getId());
    cfg2.put("testing", true);
    vertx.deployVerticle(
        cut,
        new DeploymentOptions().setConfig(cfg2).setWorker(true),
        context.asyncAssertSuccess(
            h ->
                async.complete()
        )
    );
  }

  @Test
  public void testSuccessfulUnSelect(TestContext context) {
    Async async = context.async();
    cut.selectAllCollections(
        SelectMetadataSourceVerticleTestHelper.getMetadataSource1().getId(), TENANT_UBL)
        .onComplete(
            aVoid -> {
              if (aVoid.succeeded()) {
                try {
                  Criteria labelCrit =
                      new Criteria()
                          .addField("'label'")
                          .setJSONB(true)
                          .setOperation("=")
                          .setVal(
                              SelectMetadataSourceVerticleTestHelper.getMetadataCollection1()
                                  .getLabel());
                  Criterion criterion = new Criterion(labelCrit);
                  PostgresClient.getInstance(vertx, Constants.MODULE_TENANT)
                      .get(
                          "metadata_collections",
                          FincConfigMetadataCollection.class,
                          criterion,
                          true,
                          true,
                          ar -> {
                            if (ar.succeeded()) {
                              if (ar.result() != null) {
                                FincConfigMetadataCollection collection =
                                    ar.result().getResults().get(0);
                                if (collection == null) {
                                  context.fail("No results found.");
                                } else {
                                  context.assertFalse(collection.getSelectedBy().contains("DE-15"));
                                }
                              } else {
                                context.fail("No results found.");
                              }
                              async.complete();
                            } else {
                              context.fail(ar.cause().toString());
                            }
                          });
                } catch (Exception e) {
                  context.fail(e);
                }
              } else {
                context.fail();
              }
            });
  }
}
