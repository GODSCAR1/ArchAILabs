package com.codeja.master_acdc.ordering.domain.port;

import com.codeja.master_acdc.ordering.domain.model.Order;

/** Puerto de salida para persistir pedidos. */
public interface OrderRepository {

  void save(Order order);
}
