package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.mongodb.core.mapping.Field;

public record ReviewSnippet(
    @Field("id") UUID id, String author, int rating, String comment, Instant createdAt) {}
