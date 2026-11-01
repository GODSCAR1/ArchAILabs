package com.codeja.master_acdc.inventory.infrastructure.adapters.in.web;

import com.codeja.master_acdc.inventory.domain.model.ProductReservation;

final class StockReservationWebMapper {

  private StockReservationWebMapper() {}

  static StockReservationResponse toResponse(ProductReservation reservation) {
    return new StockReservationResponse(
        reservation.productId(), reservation.units(), reservation.available());
  }
}
