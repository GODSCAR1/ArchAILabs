package com.codeja.master_acdc.ordering.domain.model;

/** Estado del pedido. Solo PENDING es alcanzable mediante la API en este cambio. */
public enum OrderStatus {
  PENDING,
  PAID,
  SHIPPED,
  CANCELLED
}
