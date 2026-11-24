package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import com.codeja.master_acdc.ordering.application.port.ConfirmOrderCommand;
import com.codeja.master_acdc.ordering.application.port.ConfirmOrderUseCase;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderConfirmationController {
  private final ConfirmOrderUseCase confirmation;

  public OrderConfirmationController(ConfirmOrderUseCase confirmation) {
    this.confirmation = confirmation;
  }

  @PostMapping("/api/orders/{id}/confirmation")
  public OrderResponse confirm(@PathVariable("id") String id) {
    return OrderWebMapper.response(
        confirmation.confirm(new ConfirmOrderCommand(OrderWebMapper.orderId(id), null)));
  }
}
