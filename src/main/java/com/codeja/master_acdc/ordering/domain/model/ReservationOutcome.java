package com.codeja.master_acdc.ordering.domain.model;

/** Resultado de reservar el stock de las líneas de un pedido. */
public enum ReservationOutcome {
  RESERVED,
  INSUFFICIENT_STOCK,
  UNKNOWN_PRODUCT
}
