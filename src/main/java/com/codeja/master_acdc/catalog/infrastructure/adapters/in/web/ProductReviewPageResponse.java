package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import java.util.List;

public record ProductReviewPageResponse(
    List<ProductReviewResponse> content, int page, int size, long totalElements, int totalPages) {}
