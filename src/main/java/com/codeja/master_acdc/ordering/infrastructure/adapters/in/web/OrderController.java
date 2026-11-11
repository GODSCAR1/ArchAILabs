package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import com.codeja.master_acdc.ordering.application.port.CancelOrderUseCase;
import com.codeja.master_acdc.ordering.application.port.CreateOrderUseCase;
import com.codeja.master_acdc.ordering.application.port.GetOrderUseCase;
import com.codeja.master_acdc.ordering.application.port.ListOrdersByStatusUseCase;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderReadScope;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

  private final CreateOrderUseCase createOrderUseCase;
  private final CancelOrderUseCase cancelOrderUseCase;
  private final ListOrdersByStatusUseCase listOrdersByStatusUseCase;
  private final GetOrderUseCase getOrderUseCase;

  public OrderController(
      CreateOrderUseCase createOrderUseCase,
      CancelOrderUseCase cancelOrderUseCase,
      ListOrdersByStatusUseCase listOrdersByStatusUseCase,
      GetOrderUseCase getOrderUseCase) {
    this.createOrderUseCase = createOrderUseCase;
    this.cancelOrderUseCase = cancelOrderUseCase;
    this.listOrdersByStatusUseCase = listOrdersByStatusUseCase;
    this.getOrderUseCase = getOrderUseCase;
  }

  @GetMapping("/api/orders")
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasAnyAuthority('order:read-own','order:read-any')")
  public List<OrderSummaryResponse> list(
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "sort", required = false) String sort) {
    return listOrdersByStatusUseCase
        .list(OrderWebMapper.listingCriteria(status, sort == null ? "id ASC" : sort))
        .stream()
        .map(OrderWebMapper::summary)
        .toList();
  }

  @GetMapping("/api/orders/{id}")
  public OrderResponse detail(@PathVariable("id") String id) {
    return OrderWebMapper.response(
        getOrderUseCase.get(OrderWebMapper.orderId(id), OrderReadScope.None.INSTANCE));
  }

  @PostMapping("/api/orders")
  public ResponseEntity<OrderResponse> create(@RequestBody CreateOrderRequest request) {
    Order order = createOrderUseCase.create(null, OrderWebMapper.toLines(request));
    return ResponseEntity.status(HttpStatus.CREATED).body(OrderWebMapper.response(order));
  }

  @PostMapping("/api/orders/{id}/cancellation")
  public ResponseEntity<OrderResponse> cancel(@PathVariable("id") String orderId) {
    Order order = cancelOrderUseCase.cancel(OrderWebMapper.orderId(orderId), null);
    return ResponseEntity.ok(OrderWebMapper.response(order));
  }
}
