package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("catalog_products")
public record CatalogProductSearchView(
    @Id UUID id,
    String name,
    long price,
    ProductStatus status,
    CategoryRef category,
    int availableStock) {}
