package com.codeja.master_acdc.inventory.application.port;

import com.codeja.master_acdc.inventory.domain.model.ProductReservation;
import java.util.UUID;

/** Caso de uso: reservar unidades de un producto. */
public interface ReserveProductStockUseCase {

  ProductReservation reserve(UUID productId, int units);
}
