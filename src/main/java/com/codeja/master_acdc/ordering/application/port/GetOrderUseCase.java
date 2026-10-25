package com.codeja.master_acdc.ordering.application.port;

import com.codeja.master_acdc.ordering.domain.model.Order;
import java.util.UUID;

/** Caso de uso: consultar el detalle de un pedido propio (con sus líneas). */
public interface GetOrderUseCase {

  Order get(UUID orderId, UUID customerId);
}
