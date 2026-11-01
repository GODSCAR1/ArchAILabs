package com.codeja.master_acdc.catalog.infrastructure.adapters.out.persistence;

import java.time.Instant;
import java.util.UUID;

record ProductJpaData(
    UUID id,
    String name,
    String sku,
    long price,
    UUID categoryId,
    String status,
    Instant createdAt,
    long version) {}
