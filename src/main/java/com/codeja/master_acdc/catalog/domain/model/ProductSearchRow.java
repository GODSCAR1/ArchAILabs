package com.codeja.master_acdc.catalog.domain.model;

import java.util.UUID;

public record ProductSearchRow(
    UUID id, String name, long price, int stock, String categoryName, ProductStatus status) {}
