package com.codeja.master_acdc.inventory.infrastructure.adapters.in.web;

import com.codeja.master_acdc.inventory.domain.model.ProductReservation;

public final class StockReservationWebMapper {

  private StockReservationWebMapper() {}

  public static StockReservationResponse toResponse(ProductReservation reservation) {
    return new StockReservationResponse(
        reservation.productId(), reservation.units(), reservation.available());
  }
}
