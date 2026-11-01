package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("reviews")
public record CatalogReview(
    @Id UUID id, UUID productId, String author, int rating, String comment, Instant createdAt) {}
