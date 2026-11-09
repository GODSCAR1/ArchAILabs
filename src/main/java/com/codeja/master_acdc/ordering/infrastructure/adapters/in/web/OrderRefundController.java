package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import com.codeja.master_acdc.ordering.application.port.RefundOrderUseCase;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderRefundController {
  private final RefundOrderUseCase refund;

  public OrderRefundController(RefundOrderUseCase refund) {
    this.refund = refund;
  }

  @PostMapping("/api/orders/{id}/refund")
  public OrderResponse refund(@PathVariable String id) {
    return OrderWebMapper.response(refund.refund(OrderWebMapper.orderId(id)));
  }
}
