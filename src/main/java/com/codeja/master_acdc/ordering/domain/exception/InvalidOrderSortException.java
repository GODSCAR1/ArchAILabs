package com.codeja.master_acdc.ordering.domain.exception;

public class InvalidOrderSortException extends RuntimeException {
  public InvalidOrderSortException() {
    super("Ordenación de pedidos no válida");
  }
}
