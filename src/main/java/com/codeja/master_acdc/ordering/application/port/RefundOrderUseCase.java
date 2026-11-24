package com.codeja.master_acdc.ordering.application.port;

public interface RefundOrderUseCase {
  OrderDetail refund(RefundOrderCommand command);
}
