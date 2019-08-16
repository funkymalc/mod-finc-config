package org.folio.finc;

import static com.jayway.restassured.RestAssured.given;

import com.jayway.restassured.http.ContentType;
import io.vertx.core.json.Json;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.folio.rest.jaxrs.model.Isil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;

public class ApiTestBase {

  protected static final String TENANT_UBL = "ubl";
  protected static final String TENANT_DIKU = "diku";

  protected static final String ISILS_API_ENDPOINT = "/finc-config/isils";
  protected static final String FINC_SELECT_FILES_ENDPOINT = "/finc-select/files";
  protected static final String FINC_SELECT_FILTERS_ENDPOINT = "/finc-select/filters";
  protected static final String FINC_SELECT_METADATA_COLLECTIONS_ENDPOINT = "/finc-select/metadata-collections";
  protected static final String FINC_SELECT_METADATA_SOURCES_ENDPOINT = "/finc-select/metadata-sources";
  protected static final String FINC_CONFIG_METADATA_COLLECTIONS_ENDPOINT = "/finc-config/metadata-collections";
  protected static final String FINC_CONFIG_METADATA_SOURCES_ENDPOINT = "/finc-config/metadata-sources";
  protected static final String FINC_CONFIG_TINY_METADATA_SOURCES_ENDPOINT = "/finc-config/tiny-metadata-sources";

  private static boolean runningOnOwn;

  @BeforeClass
  public static void before() throws Exception {

    if (ApiTestSuite.isNotInitialised()) {
      System.out.println("Running test on own, initialising suite manually");
      runningOnOwn = true;
      ApiTestSuite.before();
    }
  }

  @AfterClass
  public static void after() throws InterruptedException, ExecutionException, TimeoutException {

    if (runningOnOwn) {
      System.out.println("Running test on own, un-initialising suite manually");
      ApiTestSuite.after();
    }
  }

  public Isil loadIsilUbl() {
    Isil isil =
        new Isil()
            .withId(UUID.randomUUID().toString())
            .withLibrary("UB Leipzig")
            .withIsil("DE-15")
            .withTenant("ubl");
    return loadIsil(isil);
  }

  public Isil loadIsilDiku() {
    Isil isil =
        new Isil()
            .withId(UUID.randomUUID().toString())
            .withLibrary("DIKU")
            .withIsil("DIKU-01")
            .withTenant("diku");
    return loadIsil(isil);
  }

  public Isil loadIsil(Isil isil) {
    Isil isilResp =
        given()
            .body(Json.encode(isil))
            .header("X-Okapi-Tenant", TENANT_UBL)
            .header("content-type", ContentType.JSON)
            .header("accept", ContentType.JSON)
            .post(ISILS_API_ENDPOINT)
            .then()
            .statusCode(201)
            .extract()
            .response()
            .as(Isil.class);
    Assert.assertEquals(isil.getIsil(), isilResp.getIsil());
    return isilResp;
  }

  public void deleteIsil(String isilId) {
    given()
        .header("X-Okapi-Tenant", TENANT_UBL)
        .delete(ISILS_API_ENDPOINT + "/" + isilId)
        .then()
        .statusCode(204);
  }
}