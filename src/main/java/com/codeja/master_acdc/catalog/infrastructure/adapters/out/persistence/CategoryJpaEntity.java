package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class CategoryJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, length = 120)
  private String name;

  @Version private long version;

  protected CategoryJpaEntity() {}

  String name() {
    return name;
  }
}
