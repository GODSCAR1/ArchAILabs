package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class CustomerJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, length = 120)
  private String name;

  protected CustomerJpaEntity() {}

  public CustomerJpaEntity(UUID id, String name) {
    this.id = id;
    this.name = name;
  }

  UUID id() {
    return id;
  }

  String name() {
    return name;
  }
}
