package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
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

  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Version private long version;

  @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id", insertable = false, updatable = false)
  private CategoryJpaEntity category;

  @OneToOne(fetch = jakarta.persistence.FetchType.LAZY)
  @JoinColumn(
      name = "id",
      referencedColumnName = "product_id",
      insertable = false,
      updatable = false)
  private CatalogStockJpaEntity stock;

  protected ProductJpaEntity() {}

  ProductJpaEntity(ProductJpaData data) {
    this.id = data.id();
    this.name = data.name();
    this.sku = data.sku();
    this.price = data.price();
    this.categoryId = data.categoryId();
    this.status = data.status();
    this.createdAt = data.createdAt();
    this.version = data.version();
  }

  CategoryJpaEntity category() {
    return category;
  }

  CatalogStockJpaEntity stock() {
    return stock;
  }

  UUID id() {
    return id;
  }

  String name() {
    return name;
  }

  long price() {
    return price;
  }

  String status() {
    return status;
  }

  String sku() {
    return sku;
  }

  UUID categoryId() {
    return categoryId;
  }

  Instant createdAt() {
    return createdAt;
  }

  long version() {
    return version;
  }
}
