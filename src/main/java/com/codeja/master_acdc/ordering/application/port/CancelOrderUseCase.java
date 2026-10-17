package com.codeja.master_acdc.ordering.application.port;

import com.codeja.master_acdc.ordering.domain.model.Order;
import java.util.UUID;

/** Caso de uso: cancelar un pedido propio y liberar su stock (todo o nada). */
public interface CancelOrderUseCase {

  Order cancel(UUID orderId, UUID customerId);
}
