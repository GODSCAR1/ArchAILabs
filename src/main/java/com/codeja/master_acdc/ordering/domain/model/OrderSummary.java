package com.codeja.master_acdc.ordering.domain.model;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record OrderSummary(UUID id, @Nullable String customerName, int lineCount, int totalUnits) {}
