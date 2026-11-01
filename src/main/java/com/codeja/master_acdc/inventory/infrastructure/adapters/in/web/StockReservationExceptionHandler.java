package com.codeja.master_acdc.inventory.infrastructure.adapters.in.web;

import com.codeja.master_acdc.inventory.domain.exception.InsufficientStockException;
import com.codeja.master_acdc.inventory.domain.exception.StockBusyException;
import com.codeja.master_acdc.inventory.domain.exception.StockRecordNotFoundException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = StockReservationController.class)
public class StockReservationExceptionHandler {

  @ExceptionHandler(InsufficientStockException.class)
  ProblemDetail handleInsufficientStock(InsufficientStockException exception) {
    ProblemDetail problem = problem(HttpStatus.CONFLICT, "insufficient-stock", exception);
    problem.setProperty("available", exception.available());
    return problem;
  }

  @ExceptionHandler(StockRecordNotFoundException.class)
  ProblemDetail handleStockRecordNotFound(StockRecordNotFoundException exception) {
    return problem(HttpStatus.NOT_FOUND, "stock-not-found", exception);
  }

  @ExceptionHandler(StockBusyException.class)
  ResponseEntity<ProblemDetail> handleStockBusy(StockBusyException exception) {
    ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "stock-busy", exception);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, "1")
        .body(problem);
  }

  private static ProblemDetail problem(HttpStatus status, String type, Exception exception) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    problem.setType(URI.create("https://api.master-acdc.dev/problems/" + type));
    return problem;
  }
}
