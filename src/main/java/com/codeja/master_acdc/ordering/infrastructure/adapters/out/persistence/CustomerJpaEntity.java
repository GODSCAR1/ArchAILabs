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

  @Column(name = "first_name", length = 120)
  private String firstName;

  @Column(name = "last_name", length = 120)
  private String lastName;

  protected CustomerJpaEntity() {}

  public CustomerJpaEntity(UUID id, String name, String firstName, String lastName) {
    this.id = id;
    this.name = name;
    this.firstName = firstName;
    this.lastName = lastName;
  }

  UUID id() {
    return id;
  }

  String name() {
    return name;
  }

  String firstName() {
    return firstName;
  }

  String lastName() {
    return lastName;
  }
}
