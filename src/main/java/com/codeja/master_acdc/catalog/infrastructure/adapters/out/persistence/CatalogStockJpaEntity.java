package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "stock")
public class CatalogStockJpaEntity {

  @Id
  @Column(name = "product_id")
  private UUID productId;

  @Column(nullable = false)
  private int available;

  protected CatalogStockJpaEntity() {}

  int available() {
    return available;
  }
}
