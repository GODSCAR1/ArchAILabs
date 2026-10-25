package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import com.codeja.master_acdc.ordering.domain.exception.DuplicateProductException;
import com.codeja.master_acdc.ordering.domain.exception.EmptyOrderException;
import com.codeja.master_acdc.ordering.domain.exception.InsufficientStockException;
import com.codeja.master_acdc.ordering.domain.exception.InvalidLineException;
import com.codeja.master_acdc.ordering.domain.exception.InvalidOrderStatusException;
import com.codeja.master_acdc.ordering.domain.exception.InvalidQuantityException;
import com.codeja.master_acdc.ordering.domain.exception.MissingOrderStatusException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotCancellableException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import com.codeja.master_acdc.ordering.domain.exception.UnknownProductException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduce los errores de creación de pedido a {@code ProblemDetail} con un type propio. */
@RestControllerAdvice
public class OrderExceptionHandler {

  private static final String TYPE_BASE = "https://api.master-acdc.dev/problems/";

  @ExceptionHandler(MissingCustomerIdException.class)
  ProblemDetail handleMissingCustomerId(MissingCustomerIdException exception) {
    return problem(HttpStatus.BAD_REQUEST, "missing-customer-id", exception.getMessage());
  }

  @ExceptionHandler(InvalidCustomerIdException.class)
  ProblemDetail handleInvalidCustomerId(InvalidCustomerIdException exception) {
    return problem(HttpStatus.BAD_REQUEST, "invalid-customer-id", exception.getMessage());
  }

  @ExceptionHandler(MissingOrderStatusException.class)
  ProblemDetail handleMissingOrderStatus(MissingOrderStatusException exception) {
    return problem(HttpStatus.BAD_REQUEST, "missing-order-status", exception.getMessage());
  }

  @ExceptionHandler(InvalidOrderStatusException.class)
  ProblemDetail handleInvalidOrderStatus(InvalidOrderStatusException exception) {
    return problem(HttpStatus.BAD_REQUEST, "invalid-order-status", exception.getMessage());
  }

  @ExceptionHandler(EmptyOrderException.class)
  ProblemDetail handleEmptyOrder(EmptyOrderException exception) {
    return problem(HttpStatus.UNPROCESSABLE_ENTITY, "empty-order", exception.getMessage());
  }

  @ExceptionHandler(InvalidLineException.class)
  ProblemDetail handleInvalidLine(InvalidLineException exception) {
    return problem(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-line", exception.getMessage());
  }

  @ExceptionHandler(InvalidQuantityException.class)
  ProblemDetail handleInvalidQuantity(InvalidQuantityException exception) {
    return problem(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-quantity", exception.getMessage());
  }

  @ExceptionHandler(DuplicateProductException.class)
  ProblemDetail handleDuplicateProduct(DuplicateProductException exception) {
    return problem(HttpStatus.UNPROCESSABLE_ENTITY, "duplicate-product", exception.getMessage());
  }

  @ExceptionHandler(UnknownProductException.class)
  ProblemDetail handleUnknownProduct(UnknownProductException exception) {
    return problem(HttpStatus.UNPROCESSABLE_ENTITY, "unknown-product", exception.getMessage());
  }

  @ExceptionHandler(InsufficientStockException.class)
  ProblemDetail handleInsufficientStock(InsufficientStockException exception) {
    return problem(HttpStatus.CONFLICT, "insufficient-stock", exception.getMessage());
  }

  @ExceptionHandler(OrderNotFoundException.class)
  ProblemDetail handleOrderNotFound(OrderNotFoundException exception) {
    return problem(HttpStatus.NOT_FOUND, "order-not-found", exception.getMessage());
  }

  @ExceptionHandler(OrderNotCancellableException.class)
  ProblemDetail handleOrderNotCancellable(OrderNotCancellableException exception) {
    return problem(HttpStatus.CONFLICT, "order-not-cancellable", exception.getMessage());
  }

  private static ProblemDetail problem(HttpStatus status, String slug, String detail) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setType(URI.create(TYPE_BASE + slug));
    return problemDetail;
  }
}
