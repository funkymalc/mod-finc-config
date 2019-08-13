package org.folio.finc.select;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

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
import java.util.Arrays;
import java.util.UUID;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.FilterFile;
import org.folio.rest.jaxrs.model.FincSelectFilter;
import org.folio.rest.jaxrs.model.Isil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.utils.Constants;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class FincSelectFiltersIT {
  private static final String APPLICATION_JSON = "application/json";
  private static final String BASE_URL = "/finc-select/filters";
  private static final String TENANT_UBL = "ubl";
  private static final String TENANT_DIKU = "diku";
  private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
  private static Isil isilUBL;
  private static Isil isilDiku;
  private static FincSelectFilter filter1;
  private static FincSelectFilter filter1Changed;
  private static FincSelectFilter filter2;
  private static Vertx vertx;
  @Rule public Timeout timeout = Timeout.seconds(10);

  @BeforeClass
  public static void setUp(TestContext context) {
    try {
      String isilStr = new String(Files.readAllBytes(Paths.get("ramls/examples/isil1.sample")));
      isilUBL = Json.decodeValue(isilStr, Isil.class);

      String isilDikuStr = new String(Files.readAllBytes(Paths.get("ramls/examples/isil3.sample")));
      isilDiku = Json.decodeValue(isilDikuStr, Isil.class);

      String filter1Str =
          new String(Files.readAllBytes(Paths.get("ramls/examples/fincSelectFilter1.sample")));
      filter1 = Json.decodeValue(filter1Str, FincSelectFilter.class);

      String filter2Str =
          new String(Files.readAllBytes(Paths.get("ramls/examples/fincSelectFilter2.sample")));
      filter2 = Json.decodeValue(filter2Str, FincSelectFilter.class);

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
  public void checkThatWeCanAddGetPutAndDeleteFilters() {
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

    // POST File 1
    Response firstPostFileResp =
        given()
            .body("foobar".getBytes())
            .header("X-Okapi-Tenant", TENANT_UBL)
            .header("content-type", APPLICATION_OCTET_STREAM)
            .post("/finc-select/files")
            .then()
            .statusCode(200)
            .extract()
            .response();

    // POST File 2
    Response secondPostFileResp =
        given()
            .body("foobar2".getBytes())
            .header("X-Okapi-Tenant", TENANT_UBL)
            .header("content-type", APPLICATION_OCTET_STREAM)
            .post("/finc-select/files")
            .then()
            .statusCode(200)
            .extract()
            .response();

    // Add posted files to filter's filterFiles
    String firstFileId = firstPostFileResp.getBody().print();
    String secondFileId = secondPostFileResp.getBody().print();
    FilterFile firstFilterFile =
        new FilterFile()
            .withId(UUID.randomUUID().toString())
            .withLabel("First FilterFile")
            .withFileId(firstFileId);
    FilterFile secondFilterFile =
        new FilterFile()
            .withId(UUID.randomUUID().toString())
            .withLabel("Second FilterFile")
            .withFileId(secondFileId);
    filter1.setFilterFiles(Arrays.asList(firstFilterFile, secondFilterFile));

    // POST filter
    Response resp =
        given()
            .body(Json.encode(filter1))
            .header("X-Okapi-Tenant", TENANT_UBL)
            .header("content-type", APPLICATION_JSON)
            .header("accept", APPLICATION_JSON)
            .post(BASE_URL)
            .then()
            .statusCode(201)
            .extract()
            .response();

    FincSelectFilter postedFilter = resp.getBody().as(FincSelectFilter.class);
    Assert.assertNotNull(postedFilter.getId());
    Assert.assertEquals(filter1.getLabel(), postedFilter.getLabel());
    Assert.assertEquals(filter1.getType(), postedFilter.getType());

    // GET filter
    given()
        .header("X-Okapi-Tenant", TENANT_UBL)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
        .get(BASE_URL + "/" + postedFilter.getId())
        .then()
        .contentType(ContentType.JSON)
        .statusCode(200)
        .body("id", equalTo(postedFilter.getId()))
        .body("label", equalTo(filter1.getLabel()))
        .body("type", equalTo(filter1.getType().value()))
        .body("$", not(hasKey("isil")));

    // PUT filter and define second filter file to be deleted
    FilterFile secondFilterFileToDelete = secondFilterFile.withDelete(true);
    FincSelectFilter changed =
        postedFilter
            .withLabel("CHANGED")
            .withFilterFiles(Arrays.asList(firstFilterFile, secondFilterFileToDelete));

    given()
        .body(Json.encode(changed))
        .header("X-Okapi-Tenant", TENANT_UBL)
        .header("content-type", APPLICATION_JSON)
        .header("accept", "text/plain")
        .put(BASE_URL + "/" + postedFilter.getId())
        .then()
        .statusCode(204);

    // GET: check that second file is deleted
    given()
        .header("X-Okapi-Tenant", TENANT_UBL)
        .header("content-type", APPLICATION_OCTET_STREAM)
        .get("/finc-select/files/" + secondFileId)
        .then()
        .statusCode(404);

    // GET changed filter
    given()
        .header("X-Okapi-Tenant", TENANT_UBL)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
        .get(BASE_URL)
        .then()
        .contentType(ContentType.JSON)
        .statusCode(200)
        .body("fincSelectFilters.size()", equalTo(1))
        .body("fincSelectFilters[0].id", equalTo(changed.getId()))
        .body("fincSelectFilters[0].label", equalTo(changed.getLabel()))
        .body("fincSelectFilters[0].filterFiles.size()", equalTo(1))
        .body("$", not(hasKey("isil")));

    // GET - Different tenant
    given()
        .header("X-Okapi-Tenant", TENANT_DIKU)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
        .get(BASE_URL)
        .then()
        .contentType(ContentType.JSON)
        .statusCode(200)
        .body("fincSelectFilters.size()", equalTo(0));

    // DELETE filter
    given()
        .header("X-Okapi-Tenant", TENANT_UBL)
        .delete(BASE_URL + "/" + postedFilter.getId())
        .then()
        .statusCode(204);

    // GET first file and check that it was deleted
    given()
        .header("X-Okapi-Tenant", TENANT_UBL)
        .header("content-type", APPLICATION_OCTET_STREAM)
        .get("/finc-select/files/" + firstFileId)
        .then()
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

  @Test
  public void checkThatWeCanSearchForFilters() {
    filter1.setId(UUID.randomUUID().toString());
    filter2.setId(UUID.randomUUID().toString());
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

    // POST
    given()
        .body(Json.encode(filter1))
        .header("X-Okapi-Tenant", TENANT_UBL)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
        .post(BASE_URL)
        .then()
        .statusCode(201)
        .body("id", equalTo(filter1.getId()))
        .body("label", equalTo(filter1.getLabel()))
        .body("type", equalTo(filter1.getType().value()));

    // POST
    given()
        .body(Json.encode(filter2))
        .header("X-Okapi-Tenant", TENANT_UBL)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
        .post(BASE_URL)
        .then()
        .statusCode(201)
        .body("id", equalTo(filter2.getId()))
        .body("label", equalTo(filter2.getLabel()))
        .body("type", equalTo(filter2.getType().value()));

    // GET
    given()
        .header("X-Okapi-Tenant", TENANT_UBL)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
        .get(BASE_URL + "?query=(label==Holdings 1)")
        .then()
        .contentType(ContentType.JSON)
        .statusCode(200)
        .body("fincSelectFilters.size()", equalTo(1))
        .body("fincSelectFilters[0].id", equalTo(filter1.getId()))
        .body("fincSelectFilters[0].label", equalTo(filter1.getLabel()))
        .body("fincSelectFilters[0]", not(hasKey("isil")));

    // GET
    given()
        .header("X-Okapi-Tenant", TENANT_DIKU)
        .header("content-type", APPLICATION_JSON)
        .header("accept", APPLICATION_JSON)
        .get(BASE_URL + "?query=(isil==DE-15)")
        .then()
        .contentType(ContentType.JSON)
        .statusCode(200)
        .body("fincSelectFilters.size()", equalTo(0));
    // DELETE
    given()
        .header("X-Okapi-Tenant", TENANT_UBL)
        .delete(BASE_URL + "/" + filter1.getId())
        .then()
        .statusCode(204);

    // DELETE
    given()
        .header("X-Okapi-Tenant", TENANT_UBL)
        .delete(BASE_URL + "/" + filter2.getId())
        .then()
        .statusCode(204);

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
