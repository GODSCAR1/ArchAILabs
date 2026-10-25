package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "warehouses")
public class WarehouseJpaEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  protected WarehouseJpaEntity() {}
}
