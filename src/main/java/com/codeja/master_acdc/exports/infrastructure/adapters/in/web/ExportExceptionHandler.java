package com.codeja.master_acdc.exports.infrastructure.adapters.in.web;

import com.codeja.master_acdc.exports.domain.model.ExportException;
import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AdminExportController.class)
public class ExportExceptionHandler {
  @ExceptionHandler(ExportException.class)
  public ProblemDetail handle(ExportException exception) {
    var status = exception.code().equals("export-not-found") ? 404 : 500;
    var detail =
        ProblemDetail.forStatusAndDetail(
            org.springframework.http.HttpStatusCode.valueOf(status), exception.code());
    detail.setType(URI.create("https://api.master-acdc.dev/problems/" + exception.code()));
    return detail;
  }
}
