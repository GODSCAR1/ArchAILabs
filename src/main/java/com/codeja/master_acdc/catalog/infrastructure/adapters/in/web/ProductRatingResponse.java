package com.codeja.master_acdc.catalog.infrastructure.adapters.in.web;

import java.math.BigDecimal;

public record ProductRatingResponse(BigDecimal average, long count) {}
