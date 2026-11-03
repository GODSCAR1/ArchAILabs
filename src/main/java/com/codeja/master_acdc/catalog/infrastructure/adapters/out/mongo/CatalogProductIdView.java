package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import java.util.UUID;

public record CatalogProductIdView(UUID id, String sku, int availableStock) {}
