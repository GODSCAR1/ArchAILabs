package com.codeja.master_acdc.ordering.application.port;

/** Caso de uso: cancelar un pedido propio y liberar su stock (todo o nada). */
public interface CancelOrderUseCase {

  OrderDetail cancel(CancelOrderCommand command);
}
