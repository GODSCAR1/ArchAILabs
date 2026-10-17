package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import com.codeja.master_acdc.ordering.application.port.CancelOrderUseCase;
import com.codeja.master_acdc.ordering.application.port.CreateOrderUseCase;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotFoundException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

  private static final String CUSTOMER_ID_HEADER = "X-Customer-Id";

  private final CreateOrderUseCase createOrderUseCase;
  private final CancelOrderUseCase cancelOrderUseCase;

  public OrderController(
      CreateOrderUseCase createOrderUseCase, CancelOrderUseCase cancelOrderUseCase) {
    this.createOrderUseCase = createOrderUseCase;
    this.cancelOrderUseCase = cancelOrderUseCase;
  }

  @PostMapping("/api/orders")
  public ResponseEntity<OrderResponse> create(
      @RequestHeader(value = CUSTOMER_ID_HEADER, required = false) String customerIdHeader,
      @RequestBody CreateOrderRequest request) {
    // La cabecera se valida antes que el cuerpo para respetar la precedencia 400 -> 422 -> 409.
    UUID customerId = parseCustomerId(customerIdHeader);
    Order order = createOrderUseCase.create(customerId, toLines(request));
    return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
  }

  @PostMapping("/api/orders/{id}/cancellation")
  public ResponseEntity<OrderResponse> cancel(
      @PathVariable("id") String orderId,
      @RequestHeader(value = CUSTOMER_ID_HEADER, required = false) String customerIdHeader) {
    UUID customerId = parseCustomerId(customerIdHeader);
    UUID parsedOrderId = parseOrderId(orderId);
    Order order = cancelOrderUseCase.cancel(parsedOrderId, customerId);
    return ResponseEntity.ok(OrderResponse.from(order));
  }

  private static UUID parseOrderId(String orderId) {
    try {
      return UUID.fromString(orderId);
    } catch (IllegalArgumentException exception) {
      throw new OrderNotFoundException(null);
    }
  }

  private static UUID parseCustomerId(String customerIdHeader) {
    if (customerIdHeader == null || customerIdHeader.isBlank()) {
      throw new MissingCustomerIdException();
    }
    try {
      return UUID.fromString(customerIdHeader);
    } catch (IllegalArgumentException exception) {
      throw new InvalidCustomerIdException();
    }
  }

  private static List<OrderLine> toLines(CreateOrderRequest request) {
    if (request == null || request.lines() == null) {
      return List.of();
    }
    return request.lines().stream()
        .map(line -> new OrderLine(line.productId(), line.quantity()))
        .toList();
  }
}
