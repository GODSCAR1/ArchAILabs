package com.codeja.master_acdc.inventory.infrastructure.adapters.in.web;

import java.util.UUID;

public record StockReservationResponse(UUID productId, int units, int available) {}
