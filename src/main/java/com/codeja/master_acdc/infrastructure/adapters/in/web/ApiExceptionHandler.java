package com.codeja.master_acdc.infrastructure.adapters.in.web;

import com.codeja.master_acdc.domain.exception.DuplicateSkuException;
import com.codeja.master_acdc.domain.exception.InvalidProductException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduce los errores de entrada a {@code ProblemDetail} (application/problem+json). */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(InvalidProductException.class)
  ProblemDetail handleInvalidProduct(InvalidProductException exception) {
    return problem(HttpStatus.BAD_REQUEST, "Invalid product", exception.getMessage());
  }

  @ExceptionHandler(DuplicateSkuException.class)
  ProblemDetail handleDuplicateSku(DuplicateSkuException exception) {
    return problem(HttpStatus.CONFLICT, "Duplicate SKU", exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
    String detail =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .collect(Collectors.joining("; "));
    return problem(HttpStatus.BAD_REQUEST, "Invalid request", detail);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail handleHttpMessageNotReadable(HttpMessageNotReadableException exception) {
    return problem(
        HttpStatus.BAD_REQUEST, "Invalid request", "request body is missing or malformed");
  }

  private ProblemDetail problem(HttpStatus status, String title, String detail) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setTitle(title);
    return problemDetail;
  }
}
