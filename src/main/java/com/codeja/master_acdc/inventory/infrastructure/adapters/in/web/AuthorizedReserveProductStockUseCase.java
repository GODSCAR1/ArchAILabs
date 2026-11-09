package com.codeja.master_acdc.inventory.infrastructure.adapters.in.web;

import com.codeja.master_acdc.inventory.application.port.ReserveProductStockUseCase;
import com.codeja.master_acdc.inventory.domain.model.ProductReservation;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

public class AuthorizedReserveProductStockUseCase implements ReserveProductStockUseCase {
  private final ReserveProductStockUseCase delegate;

  public AuthorizedReserveProductStockUseCase(ReserveProductStockUseCase delegate) {
    this.delegate = delegate;
  }

  @Override
  @PreAuthorize("hasAuthority('stock:reserve')")
  public ProductReservation reserve(UUID productId, int units) {
    return delegate.reserve(productId, units);
  }
}
