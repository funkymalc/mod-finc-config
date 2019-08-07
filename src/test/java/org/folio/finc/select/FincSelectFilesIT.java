package org.folio.finc.select;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.parsing.Parser;
import com.jayway.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.FincSelectFilterFile;
import org.folio.rest.jaxrs.model.Isil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.utils.Constants;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class FincSelectFilesIT {

  private static final String APPLICATION_JSON = "application/json";
  private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
  private static final String BASE_URL = "/finc-select/files";
  private static final String TENANT_UBL = "ubl";
  private static final String TENANT_DIKU = "diku";
  private static final String TEST_CONTENT = "This is the test content!!!!";
  private static Isil isilUBL;
  private static Isil isilDiku;
  private static FincSelectFilterFile filterFile;
  private static Vertx vertx;

  @Rule public Timeout timeout = Timeout.seconds(10);

  @BeforeClass
  public static void setUp(TestContext context) {
    try {
      String isilStr = new String(Files.readAllBytes(Paths.get("ramls/examples/isil1.sample")));
      isilUBL = Json.decodeValue(isilStr, Isil.class);

      String isilDikuStr = new String(Files.readAllBytes(Paths.get("ramls/examples/isil3.sample")));
      isilDiku = Json.decodeValue(isilDikuStr, Isil.class);

      String fileStr =
          new String(Files.readAllBytes(Paths.get("ramls/examples/fincSelectFilterFile.sample")));
      filterFile = Json.decodeValue(fileStr, FincSelectFilterFile.class);

    } catch (Exception e) {
      context.fail(e);
    }
    vertx = Vertx.vertx();
    try {
      PostgresClient.setIsEmbedded(true);
      PostgresClient instance = PostgresClient.getInstance(vertx);
      instance.startEmbeddedPostgres();
    } catch (Exception e) {
      context.fail(e);
      return;
    }

    Async async = context.async(3);
    int port = NetworkUtils.nextFreePort();

    RestAssured.reset();
    RestAssured.baseURI = "http://localhost";
    RestAssured.port = port;
    RestAssured.defaultParser = Parser.JSON;

    String url = "http://localhost:" + port;
    TenantClient tenantClientFinc =
        new TenantClient(url, Constants.MODULE_TENANT, Constants.MODULE_TENANT);
    TenantClient tenantClientDiku = new TenantClient(url, TENANT_DIKU, TENANT_DIKU);
    TenantClient tenantClientUbl = new TenantClient(url, TENANT_UBL, TENANT_UBL);
    DeploymentOptions options =
        new DeploymentOptions().setConfig(new JsonObject().put("http.port", port)).setWorker(true);

    vertx.deployVerticle(
        RestVerticle.class.getName(),
        options,
        res -> {
          try {
            tenantClientFinc.postTenant(null, postTenantRes -> async.complete());
            tenantClientDiku.postTenant(null, postTenantRes -> async.complete());
            tenantClientUbl.postTenant(null, postTenantRes -> async.complete());
          } catch (Exception e) {
            context.fail(e);
          }
        });
  }

  @AfterClass
  public static void tearDown(TestContext context) {
    RestAssured.reset();
    Async async = context.async();
    vertx.close(
        context.asyncAssertSuccess(
            res -> {
              PostgresClient.stopEmbeddedPostgres();
              async.complete();
            }));
  }

  @Test
  public void checkThatWeCanAddGetAndDeleteFiles() {
    // POST isils
    given()
        .body(Json.encode(isilUBL))
        .header("X-Okapi-Tenant", TENANT_UBL)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
        .post("/finc-config/isils")
        .then()
        .statusCode(201)
        .body("isil", equalTo(isilUBL.getIsil()));

    given()
        .body(Json.encode(isilDiku))
        .header("X-Okapi-Tenant", TENANT_DIKU)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
        .post("/finc-config/isils")
        .then()
        .statusCode(201)
        .body("isil", equalTo(isilDiku.getIsil()));

    // POST File
    Response postResponse =
        given()
            .body(TEST_CONTENT.getBytes())
            .header("X-Okapi-Tenant", TENANT_UBL)
            .header("content-type", APPLICATION_OCTET_STREAM)
            .post(BASE_URL)
            .then()
            .statusCode(200)
            .extract()
            .response();

    String id = postResponse.getBody().print();

    filterFile.setFile(id);
    // POST
    given()
        .body(Json.encode(filterFile))
        .header("X-Okapi-Tenant", TENANT_UBL)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
        .post("/finc-select/filter-files")
        .then()
        .statusCode(201)
        .body("id", equalTo(filterFile.getId()))
        .body("label", equalTo(filterFile.getLabel()))
        .body("filename", equalTo(filterFile.getFilename()))
        .extract()
        .response();

    // GET
    given()
        .header("X-Okapi-Tenant", TENANT_UBL)
        .header("content-type", APPLICATION_OCTET_STREAM)
        .get(BASE_URL + "/" + id)
        .then()
        .contentType(APPLICATION_OCTET_STREAM)
        .statusCode(200)
        .body(equalTo(TEST_CONTENT));

    // DELETE
    given().header("X-Okapi-Tenant", TENANT_UBL).delete(BASE_URL + "/" + id).then().statusCode(204);

    // GET FilterFile (should be deleted with file)
    given()
        .header("X-Okapi-Tenant", TENANT_UBL)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
      .get("/finc-select/filter-files/" + filterFile.getId())
        .then()
        .contentType(ContentType.TEXT)
        .statusCode(404);

    // DELETE isils
    given()
        .header("X-Okapi-Tenant", TENANT_UBL)
        .delete("/finc-config/isils/" + isilUBL.getId())
        .then()
        .statusCode(204);

    given()
        .header("X-Okapi-Tenant", TENANT_UBL)
        .delete("/finc-config/isils/" + isilDiku.getId())
        .then()
        .statusCode(204);
  }
}
