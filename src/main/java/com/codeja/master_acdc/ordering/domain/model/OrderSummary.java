package com.codeja.master_acdc.ordering.domain.model;

import java.util.UUID;

public record OrderSummary(UUID id, String customerName, int lineCount, int totalUnits) {}
