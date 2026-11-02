package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import com.codeja.master_acdc.catalog.domain.exception.ConcurrentProductModificationException;
import com.codeja.master_acdc.catalog.domain.exception.DuplicateSkuException;
import com.codeja.master_acdc.catalog.domain.exception.InvalidPriceRangeException;
import com.codeja.master_acdc.catalog.domain.exception.InvalidProductException;
import com.codeja.master_acdc.catalog.domain.exception.InvalidSortFieldException;
import com.codeja.master_acdc.catalog.domain.exception.ProductNotFoundException;
import java.net.URI;
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

  @ExceptionHandler(InvalidPriceRangeException.class)
  ProblemDetail handleInvalidPriceRange(InvalidPriceRangeException exception) {
    return typedProblem(
        HttpStatus.BAD_REQUEST,
        "Invalid price range",
        exception.getMessage(),
        "https://api.master-acdc.dev/problems/invalid-price-range");
  }

  @ExceptionHandler(InvalidSortFieldException.class)
  ProblemDetail handleInvalidSortField(InvalidSortFieldException exception) {
    return typedProblem(
        HttpStatus.BAD_REQUEST,
        "Invalid sort field",
        exception.getMessage(),
        "https://api.master-acdc.dev/problems/invalid-sort-field");
  }

  @ExceptionHandler(DuplicateSkuException.class)
  ProblemDetail handleDuplicateSku(DuplicateSkuException exception) {
    ProblemDetail problem = problem(HttpStatus.CONFLICT, "Duplicate SKU", exception.getMessage());
    problem.setType(URI.create("about:blank"));
    return problem;
  }

  @ExceptionHandler(ProductNotFoundException.class)
  ProblemDetail handleProductNotFound(ProductNotFoundException exception) {
    return typedProblem(
        HttpStatus.NOT_FOUND,
        "Product not found",
        exception.getMessage(),
        "https://api.master-acdc.dev/problems/product-not-found");
  }

  @ExceptionHandler(ConcurrentProductModificationException.class)
  ProblemDetail handleConcurrentProductModification(
      ConcurrentProductModificationException exception) {
    return typedProblem(
        HttpStatus.CONFLICT,
        "Concurrent modification",
        exception.getMessage(),
        "https://api.master-acdc.dev/problems/concurrent-modification");
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

  @ExceptionHandler(InvalidSearchParameterException.class)
  ProblemDetail handleInvalidSearchParameter(InvalidSearchParameterException exception) {
    return typedProblem(
        HttpStatus.BAD_REQUEST,
        "Invalid search parameter",
        "invalid search parameter: " + exception.parameter(),
        "https://api.master-acdc.dev/problems/invalid-search-parameter");
  }

  private ProblemDetail problem(HttpStatus status, String title, String detail) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setTitle(title);
    return problemDetail;
  }

  private ProblemDetail typedProblem(HttpStatus status, String title, String detail, String type) {
    ProblemDetail problemDetail = problem(status, title, detail);
    problemDetail.setType(URI.create(type));
    return problemDetail;
  }
}
