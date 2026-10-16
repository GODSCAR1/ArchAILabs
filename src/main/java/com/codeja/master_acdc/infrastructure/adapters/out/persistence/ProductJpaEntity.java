package com.codeja.master_acdc.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.domain.model.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "products")
public class ProductJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 64)
  private String sku;

  @Column(nullable = false)
  private long price;

  protected ProductJpaEntity() {}

  private ProductJpaEntity(UUID id, String name, String sku, long price) {
    this.id = id;
    this.name = name;
    this.sku = sku;
    this.price = price;
  }

  static ProductJpaEntity from(Product product) {
    return new ProductJpaEntity(
        product.id(), product.name().value(), product.sku().value(), product.price().value());
  }
}
