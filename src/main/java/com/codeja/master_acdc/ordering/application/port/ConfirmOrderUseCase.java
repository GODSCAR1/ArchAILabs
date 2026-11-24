package com.codeja.master_acdc.ordering.application.port;

public interface ConfirmOrderUseCase {
  OrderDetail confirm(ConfirmOrderCommand command);
}
