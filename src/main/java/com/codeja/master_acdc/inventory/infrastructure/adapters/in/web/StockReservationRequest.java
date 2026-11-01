package com.codeja.master_acdc.inventory.infrastructure.adapters.in.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockReservationRequest(@NotNull @Min(1) Integer units) {}
