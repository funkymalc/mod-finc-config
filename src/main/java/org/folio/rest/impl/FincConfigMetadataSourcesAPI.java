package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.finc.dao.MetadataSourcesDAO;
import org.folio.finc.dao.MetadataSourcesDAOImpl;
import org.folio.rest.RestVerticle;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.FincConfigMetadataSource;
import org.folio.rest.jaxrs.model.FincConfigMetadataSourcesGetOrder;
import org.folio.rest.jaxrs.resource.FincConfigMetadataSources;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.utils.AttributeNameAdder;
import org.folio.rest.utils.Constants;

/**
 * ATTENTION: API works tenant agnostic. Thus, don't use 'x-okapi-tenant' header, but {@value
 * Constants#MODULE_TENANT} as tenant.
 */
public class FincConfigMetadataSourcesAPI implements FincConfigMetadataSources {

  public static final String TABLE_NAME = "metadata_sources";
  private final Logger logger = LoggerFactory.getLogger(FincConfigMetadataSourcesAPI.class);

  private final MetadataSourcesDAO metadataSourcesDAO;

  public FincConfigMetadataSourcesAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx);
    metadataSourcesDAO = new MetadataSourcesDAOImpl();
  }

  @Override
  @Validate
  public void getFincConfigMetadataSources(
      String query,
      String orderBy,
      FincConfigMetadataSourcesGetOrder order,
      int offset,
      int limit,
      String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    metadataSourcesDAO
        .getAll(query, offset, limit, vertxContext)
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                org.folio.rest.jaxrs.model.FincConfigMetadataSources result = ar.result();
                asyncResultHandler.handle(
                    Future.succeededFuture(
                        GetFincConfigMetadataSourcesResponse.respond200WithApplicationJson(
                            result)));
              } else {
                if (ar.cause() instanceof FieldException) {
                  Future.succeededFuture(
                    GetFincConfigMetadataSourcesResponse.respond400WithTextPlain(ar.cause()));
                } else {
                  Future.succeededFuture(
                      GetFincConfigMetadataSourcesResponse.respond500WithTextPlain(ar.cause()));
                }
              }
            });
  }

  @Override
  @Validate
  public void postFincConfigMetadataSources(
      String lang,
      FincConfigMetadataSource entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {

    Future<FincConfigMetadataSource> sourceWithName =
        AttributeNameAdder.resolveAndAddAttributeNames(entity, okapiHeaders, vertxContext);

    sourceWithName.setHandler(
        ar -> {
          if (ar.succeeded()) {
            okapiHeaders.put(RestVerticle.OKAPI_HEADER_TENANT, Constants.MODULE_TENANT);
            PgUtil.post(
                TABLE_NAME,
                entity,
                okapiHeaders,
                vertxContext,
                PostFincConfigMetadataSourcesResponse.class,
                asyncResultHandler);
          } else {
            logger.error(ar.cause());
            asyncResultHandler.handle(
                Future.succeededFuture(
                    PostFincConfigMetadataSourcesResponse.respond500WithTextPlain(ar.cause())));
          }
        });
  }

  @Override
  @Validate
  public void getFincConfigMetadataSourcesById(
      String id,
      String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    logger.debug("Getting single metadata source by id: " + id);
    okapiHeaders.put(RestVerticle.OKAPI_HEADER_TENANT, Constants.MODULE_TENANT);
    PgUtil.getById(
        TABLE_NAME,
        FincConfigMetadataSource.class,
        id,
        okapiHeaders,
        vertxContext,
        GetFincConfigMetadataSourcesByIdResponse.class,
        asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFincConfigMetadataSourcesById(
      String id,
      String lang,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    logger.debug("Delete metadata source: " + id);
    okapiHeaders.put(RestVerticle.OKAPI_HEADER_TENANT, Constants.MODULE_TENANT);
    PgUtil.deleteById(
        TABLE_NAME,
        id,
        okapiHeaders,
        vertxContext,
        DeleteFincConfigMetadataSourcesByIdResponse.class,
        asyncResultHandler);
  }

  @Override
  @Validate
  public void putFincConfigMetadataSourcesById(
      String id,
      String lang,
      FincConfigMetadataSource entity,
      Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler,
      Context vertxContext) {
    logger.debug("Update metadata source: " + id);

    Future<FincConfigMetadataSource> sourceWithName =
        AttributeNameAdder.resolveAndAddAttributeNames(entity, okapiHeaders, vertxContext);

    sourceWithName.setHandler(
        ar -> {
          if (ar.succeeded()) {
            okapiHeaders.put(RestVerticle.OKAPI_HEADER_TENANT, Constants.MODULE_TENANT);
            PgUtil.put(
                TABLE_NAME,
                entity,
                id,
                okapiHeaders,
                vertxContext,
                PutFincConfigMetadataSourcesByIdResponse.class,
                asyncResultHandler);
          } else {
            logger.error(ar.cause());
            asyncResultHandler.handle(
                Future.succeededFuture(
                    PutFincConfigMetadataSourcesByIdResponse.respond500WithTextPlain(ar.cause())));
          }
        });
  }
}
