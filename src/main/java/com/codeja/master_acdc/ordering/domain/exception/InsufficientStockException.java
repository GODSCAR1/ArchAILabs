package com.codeja.master_acdc.ordering.domain.exception;

/** Señala que la reserva de stock del pedido fue rechazada por insuficiencia. */
public class InsufficientStockException extends RuntimeException {

  public InsufficientStockException() {
    super("insufficient stock");
  }
}
