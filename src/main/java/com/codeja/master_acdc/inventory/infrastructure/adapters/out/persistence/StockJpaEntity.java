package com.codeja.master_acdc.inventory.infrastructure.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "stock")
public class StockJpaEntity {

  @Id
  @Column(name = "product_id")
  private UUID productId;

  @Column(nullable = false)
  private int available;

  protected StockJpaEntity() {}

  public StockJpaEntity(UUID productId, int available) {
    this.productId = productId;
    this.available = available;
  }
}
