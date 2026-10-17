package com.codeja.master_acdc.ordering.infrastructure.adapters.in.web;

import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de la petición de creación de pedido. Las reglas de dominio se validan al construir el
 * pedido.
 */
public record CreateOrderRequest(List<Line> lines) {

  public record Line(UUID productId, int quantity) {}
}
