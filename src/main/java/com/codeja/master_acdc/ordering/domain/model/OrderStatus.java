package com.codeja.master_acdc.ordering.domain.model;

/** Estado visible del pedido. */
public enum OrderStatus {
  PENDING,
  PAYING,
  PAID,
  SHIPPED,
  CANCELLED,
  REFUNDED
}
