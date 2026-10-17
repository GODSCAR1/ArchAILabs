package com.codeja.master_acdc.ordering.application.port;

import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import java.util.List;
import java.util.UUID;

/** Caso de uso: crear un pedido reservando su stock (todo o nada). */
public interface CreateOrderUseCase {

  Order create(UUID customerId, List<OrderLine> lines);
}
