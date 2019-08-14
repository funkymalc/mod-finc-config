package org.folio.finc.select;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.List;
import java.util.stream.Collectors;
import org.folio.finc.dao.FileDAO;
import org.folio.finc.dao.FileDAOImpl;
import org.folio.finc.dao.FilterDAO;
import org.folio.finc.dao.FilterDAOImpl;
import org.folio.rest.jaxrs.model.FilterFile;
import org.folio.rest.jaxrs.model.FincSelectFilter;

public class FilterHelper {

  private static final Logger logger = LoggerFactory.getLogger(FilterHelper.class);

  private FilterDAO filterDAO;
  private FileDAO fileDAO;

  public FilterHelper() {
    this.filterDAO = new FilterDAOImpl();
    this.fileDAO = new FileDAOImpl();
  }

  public Future<Void> deleteFilesOfFilter(String filterId, String isil, Context vertxContext) {
    return filterDAO
        .getById(filterId, isil, vertxContext)
        .compose(fincSelectFilter -> deleteFilesOfFilter(fincSelectFilter, isil, vertxContext));
  }

  private Future<Void> deleteFilesOfFilter(FincSelectFilter filter, String isil, Context vertxContext) {

    Future result = Future.future();
    List<FilterFile> filterFiles = filter.getFilterFiles();
    if (filterFiles == null || filterFiles.isEmpty()) {
      result.complete();
    } else {
      List<Future> deleteFutures =
          filterFiles.stream()
              .map(filterFile -> fileDAO.deleteById(filterFile.getFileId(), isil, vertxContext))
              .collect(Collectors.toList());

      CompositeFuture.all(deleteFutures)
          .setHandler(
              ar -> {
                if (ar.succeeded()) {
                  logger.info(
                      "Associated files of filter " + filter.getId() + " deleted successfully.");
                  result.complete();
                } else {
                  logger.error(
                      "Error while deleting files of filter "
                          + filter.getId()
                          + ". \n"
                          + ar.cause().getMessage());
                  result.fail(ar.cause());
                }
              });
    }
    return result;
  }

  public Future<FincSelectFilter> removeFilesToDelete(
    FincSelectFilter filter, String isil, Context vertxContext) {

    List<FilterFile> remainingFiles =
      filter.getFilterFiles().stream()
        .filter(filterFile -> filterFile.getDelete() == null || !filterFile.getDelete())
        .collect(Collectors.toList());

    List<Future> filesToDeleteFuture =
      filter.getFilterFiles().stream()
        .filter(filterFile -> filterFile.getDelete() != null && filterFile.getDelete())
        .map(filterFile -> fileDAO.deleteById(filterFile.getFileId(), isil, vertxContext))
        .collect(Collectors.toList());

    Future<FincSelectFilter> result = Future.future();
    CompositeFuture.all(filesToDeleteFuture)
      .setHandler(
        ar -> {
          if (ar.succeeded()) {
            result.complete(filter.withFilterFiles(remainingFiles));
          } else {
            result.fail(ar.cause());
          }
        });
    return result;
  }
}