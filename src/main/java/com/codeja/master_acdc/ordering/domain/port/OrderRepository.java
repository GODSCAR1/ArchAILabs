package com.codeja.master_acdc.ordering.domain.port;

import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderReadScope;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para persistir pedidos. */
public interface OrderRepository {

  void save(Order order);

  Optional<Order> findVisibleById(UUID orderId, OrderReadScope scope);

  /** Pedido propio del cliente, si existe. */
  Optional<Order> findByIdAndCustomerId(UUID orderId, UUID customerId);

  /**
   * Cancelación condicional: solo afecta si el pedido es del cliente y está en PENDING/PAID.
   * Devuelve true si la actualización afectó a una fila (el cancelador de la carrera).
   */
  boolean cancelIfCancelable(UUID orderId, UUID customerId);

  boolean refundIfRefundable(UUID orderId);
}
