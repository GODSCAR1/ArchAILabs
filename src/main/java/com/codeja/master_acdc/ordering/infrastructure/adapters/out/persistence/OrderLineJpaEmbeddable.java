package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public class OrderLineJpaEmbeddable {

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(nullable = false)
  private int quantity;

  protected OrderLineJpaEmbeddable() {}

  public OrderLineJpaEmbeddable(UUID productId, int quantity) {
    this.productId = productId;
    this.quantity = quantity;
  }

  UUID productId() {
    return productId;
  }

  int quantity() {
    return quantity;
  }
}
