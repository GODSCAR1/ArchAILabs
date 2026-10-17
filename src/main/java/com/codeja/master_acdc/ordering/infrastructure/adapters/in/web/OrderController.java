package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import com.codeja.master_acdc.ordering.application.port.CreateOrderUseCase;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

  private static final String CUSTOMER_ID_HEADER = "X-Customer-Id";

  private final CreateOrderUseCase createOrderUseCase;

  public OrderController(CreateOrderUseCase createOrderUseCase) {
    this.createOrderUseCase = createOrderUseCase;
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
