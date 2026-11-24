package com.codeja.master_acdc.ordering.domain.model;

import com.codeja.master_acdc.ordering.domain.event.OrderCancelled;
import com.codeja.master_acdc.ordering.domain.event.OrderConfirmed;
import com.codeja.master_acdc.ordering.domain.event.OrderEvent;
import com.codeja.master_acdc.ordering.domain.exception.DuplicateProductException;
import com.codeja.master_acdc.ordering.domain.exception.EmptyOrderException;
import com.codeja.master_acdc.ordering.domain.exception.InvalidOrderException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotCancellableException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotConfirmableException;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotRefundableException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Pedido de un cliente: nace en estado PENDING con una o más líneas sin producto repetido. */
public final class Order {
  private final UUID id;
  private final UUID customerId;
  private OrderStatus status;
  private final List<OrderLine> lines;
  private final List<OrderEvent> events = new ArrayList<>();

  private Order(UUID id, UUID customerId, OrderStatus status, List<OrderLine> lines) {
    this.id = Objects.requireNonNull(id, "order id is required");
    this.customerId = Objects.requireNonNull(customerId, "customer id is required");
    this.status = Objects.requireNonNull(status, "order status is required");
    this.lines = List.copyOf(lines);
  }

  public static Order place(@Nullable UUID customerId, @Nullable List<OrderLine> lines) {
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

  public static Order restore(UUID id, UUID customerId, OrderStatus status, List<OrderLine> lines) {
    return new Order(id, customerId, status, lines);
  }

  public UUID id() {
    return id;
  }

  public UUID customerId() {
    return customerId;
  }

  public OrderStatus status() {
    return status;
  }

  public List<OrderLine> lines() {
    return lines;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return this == other || other instanceof Order order && id.equals(order.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  public Order cancel() {
    if (status != OrderStatus.PENDING && status != OrderStatus.PAID) {
      throw new OrderNotCancellableException(id, status);
    }
    status = OrderStatus.CANCELLED;
    events.add(new OrderCancelled(id, lines));
    return this;
  }

  public Order refund() {
    if (status != OrderStatus.PAID && status != OrderStatus.SHIPPED) {
      throw new OrderNotRefundableException(id);
    }
    status = OrderStatus.REFUNDED;
    return this;
  }

  public void confirm() {
    if (status != OrderStatus.PENDING) {
      throw new OrderNotConfirmableException(id);
    }
    status = OrderStatus.PAYING;
  }

  public void registerPayment() {
    if (status != OrderStatus.PAYING) {
      throw new OrderNotConfirmableException(id);
    }
    status = OrderStatus.PAID;
    events.add(new OrderConfirmed(id, lines.stream().map(OrderLine::productId).toList()));
  }

  public List<OrderEvent> pullEvents() {
    List<OrderEvent> result = List.copyOf(events);
    events.clear();
    return result;
  }
}
