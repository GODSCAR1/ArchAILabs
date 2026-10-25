package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import java.util.UUID;

public record OrderSummaryRow(UUID id, String customerName, long lineCount, long totalUnits) {}
