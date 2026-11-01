package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import java.util.UUID;

public record ProductSummaryResponse(
    UUID id, String name, long price, int stock, String categoryName) {}
