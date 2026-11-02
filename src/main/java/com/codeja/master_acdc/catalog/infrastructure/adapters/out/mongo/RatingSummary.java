package com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo;

import java.math.BigDecimal;

public record RatingSummary(BigDecimal average, long count) {}
