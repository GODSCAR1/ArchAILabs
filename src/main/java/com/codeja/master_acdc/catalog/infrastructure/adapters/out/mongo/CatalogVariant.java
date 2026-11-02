package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import java.util.UUID;
import org.springframework.data.mongodb.core.mapping.Field;

public record CatalogVariant(
    @Field("id") UUID id, String skuSuffix, String color, String size, long additionalPrice) {}
