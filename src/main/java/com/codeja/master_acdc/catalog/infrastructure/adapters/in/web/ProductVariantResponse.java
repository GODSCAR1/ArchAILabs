package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import java.util.UUID;

public record ProductVariantResponse(
    UUID id, String skuSuffix, String color, String size, long additionalPrice) {}
