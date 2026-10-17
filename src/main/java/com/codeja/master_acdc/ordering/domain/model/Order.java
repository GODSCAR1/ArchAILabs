package com.codeja.master_acdc.ordering.domain.model;

import com.codeja.master_acdc.ordering.domain.exception.DuplicateProductException;
import com.codeja.master_acdc.ordering.domain.exception.EmptyOrderException;
import com.codeja.master_acdc.ordering.domain.exception.InvalidOrderException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotCancellableException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Pedido de un cliente: nace en estado PENDING con una o más líneas sin producto repetido. */
public record Order(UUID id, UUID customerId, OrderStatus status, List<OrderLine> lines) {

  public Order {
    lines = List.copyOf(lines);
  }

  public static Order create(UUID customerId, List<OrderLine> lines) {
    if (customerId == null) {
      throw new InvalidOrderException("customer id must not be null");
    }
    if (lines == null || lines.isEmpty()) {
      throw new EmptyOrderException();
    }
    Set<UUID> seenProducts = new HashSet<>();
    for (OrderLine line : lines) {
      if (!seenProducts.add(line.productId())) {
        throw new DuplicateProductException(line.productId());
      }
    }
    return new Order(UUID.randomUUID(), customerId, OrderStatus.PENDING, lines);
  }

  /** Devuelve el pedido en estado CANCELLED si es cancelable (PENDING/PAID); si no, lanza. */
  public Order cancel() {
    if (status != OrderStatus.PENDING && status != OrderStatus.PAID) {
      throw new OrderNotCancellableException(id, status);
    }
    return new Order(id, customerId, OrderStatus.CANCELLED, lines);
  }
}
