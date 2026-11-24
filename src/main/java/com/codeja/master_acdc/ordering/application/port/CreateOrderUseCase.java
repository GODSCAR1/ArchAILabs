package com.codeja.master_acdc.ordering.application.port;

/** Caso de uso: crear un pedido reservando su stock (todo o nada). */
public interface CreateOrderUseCase {

  OrderDetail create(PlaceOrderCommand command);
}
