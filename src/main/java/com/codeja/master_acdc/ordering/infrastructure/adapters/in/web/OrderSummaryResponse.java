package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import java.util.UUID;

public record OrderSummaryResponse(UUID id, String customerName, int lineCount, int totalUnits) {}
