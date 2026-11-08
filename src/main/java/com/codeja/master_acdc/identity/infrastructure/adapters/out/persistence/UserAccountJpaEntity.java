package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "user_account")
public class UserAccountJpaEntity {
  @Id UUID id;

  @Column(nullable = false, unique = true, length = 254)
  String email;

  @Column(name = "password_hash", nullable = false, columnDefinition = "text")
  String passwordHash;

  @Column(nullable = false, columnDefinition = "text[]")
  String[] roles;

  @Column(nullable = false)
  boolean enabled;

  @Column(name = "session_version", nullable = false)
  long sessionVersion;

  @Column(name = "customer_id", unique = true)
  UUID customerId;

  protected UserAccountJpaEntity() {}
}
