package com.codeja.master_acdc.ordering.application;

import static java.util.Comparator.comparing;

import com.codeja.master_acdc.ordering.application.port.CreateOrderUseCase;
import com.codeja.master_acdc.ordering.domain.exception.InsufficientStockException;
import com.codeja.master_acdc.ordering.domain.exception.UnknownProductException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import com.codeja.master_acdc.ordering.domain.port.StockReserver;
import java.util.List;
import java.util.UUID;

public class CreateOrderService implements CreateOrderUseCase {

  private final OrderRepository orderRepository;
  private final StockReserver stockReserver;

  public CreateOrderService(OrderRepository orderRepository, StockReserver stockReserver) {
    this.orderRepository = orderRepository;
    this.stockReserver = stockReserver;
  }

  @Override
  public Order create(UUID customerId, List<OrderLine> lines) {
    Order order = Order.create(customerId, lines);
    // Ordenar por producto alineado con inventario evita interbloqueos entre pedidos concurrentes.
    List<OrderLine> ordered =
        order.lines().stream().sorted(comparing(OrderLine::productId)).toList();

    switch (stockReserver.reserve(ordered)) {
      case INSUFFICIENT_STOCK -> throw new InsufficientStockException();
      case UNKNOWN_PRODUCT -> throw new UnknownProductException();
      case RESERVED -> {
        // sin acción: la reserva se aplicó
      }
    }

    orderRepository.save(order);
    return order;
  }
}
