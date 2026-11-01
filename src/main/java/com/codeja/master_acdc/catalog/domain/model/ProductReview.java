package com.codeja.master_acdc.catalog.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ProductReview(
    UUID id, String author, int rating, String comment, Instant createdAt) {}
