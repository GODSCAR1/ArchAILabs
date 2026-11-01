package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import java.time.Instant;
import java.util.UUID;

public record ProductReviewResponse(
    UUID id, String author, int rating, String comment, Instant createdAt) {}
