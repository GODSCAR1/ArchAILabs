package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderJpaEntity {

  @Id private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "warehouse_id")
  private WarehouseJpaEntity warehouse;

  @Column(nullable = false, length = 16)
  private String status;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "order_lines", joinColumns = @JoinColumn(name = "order_id"))
  private List<OrderLineJpaEmbeddable> lines = new ArrayList<>();

  protected OrderJpaEntity() {}

  UUID id() {
    return id;
  }

  UUID customerId() {
    return customerId;
  }

  List<OrderLineJpaEmbeddable> lines() {
    return lines;
  }

  private OrderJpaEntity(UUID id, UUID customerId, String status) {
    this.id = id;
    this.customerId = customerId;
    this.status = status;
  }

  static OrderJpaEntity from(Order order) {
    OrderJpaEntity entity =
        new OrderJpaEntity(order.id(), order.customerId(), order.status().name());
    order
        .lines()
        .forEach(
            line ->
                entity.lines.add(new OrderLineJpaEmbeddable(line.productId(), line.quantity())));
    return entity;
  }

  Order toDomain() {
    List<OrderLine> domainLines =
        lines.stream().map(line -> new OrderLine(line.productId(), line.quantity())).toList();
    return new Order(id, customerId, OrderStatus.valueOf(status), domainLines);
  }
}
