package com.codeja.master_acdc.catalog.domain.model;

import java.util.UUID;

public record ProductVariant(
    UUID id, String skuSuffix, String color, String size, long additionalPrice) {}
