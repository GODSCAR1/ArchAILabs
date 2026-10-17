package com.codeja.master_acdc.ordering.domain.port;

import com.codeja.master_acdc.ordering.domain.model.Order;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para persistir pedidos. */
public interface OrderRepository {

  void save(Order order);

  /** Pedido propio del cliente, si existe. */
  Optional<Order> findByIdAndCustomerId(UUID orderId, UUID customerId);

  /**
   * Cancelación condicional: solo afecta si el pedido es del cliente y está en PENDING/PAID.
   * Devuelve true si la actualización afectó a una fila (el cancelador de la carrera).
   */
  boolean cancelIfCancelable(UUID orderId, UUID customerId);
}
