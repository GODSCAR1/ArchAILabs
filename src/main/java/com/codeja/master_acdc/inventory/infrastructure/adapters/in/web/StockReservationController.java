package com.codeja.master_acdc.inventory.infrastructure.adapters.in.web;

import com.codeja.master_acdc.inventory.application.port.ReserveProductStockUseCase;
import com.codeja.master_acdc.inventory.domain.model.ProductReservation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StockReservationController {

  private final ReserveProductStockUseCase reserveProductStockUseCase;

  public StockReservationController(ReserveProductStockUseCase reserveProductStockUseCase) {
    this.reserveProductStockUseCase = reserveProductStockUseCase;
  }

  @PostMapping("/api/products/{id}/reservations")
  public ResponseEntity<StockReservationResponse> reserve(
      @PathVariable UUID id, @Valid @RequestBody StockReservationRequest request) {
    ProductReservation reservation = reserveProductStockUseCase.reserve(id, request.units());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(StockReservationWebMapper.toResponse(reservation));
  }
}
