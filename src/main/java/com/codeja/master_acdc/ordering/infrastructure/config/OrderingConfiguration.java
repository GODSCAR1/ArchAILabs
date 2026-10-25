package com.codeja.master_acdc.ordering.infrastructure.config;

import com.codeja.master_acdc.inventory.api.InventoryFacade;
import com.codeja.master_acdc.ordering.application.CancelOrderService;
import com.codeja.master_acdc.ordering.application.CreateOrderService;
import com.codeja.master_acdc.ordering.application.ListOrdersByStatusService;
import com.codeja.master_acdc.ordering.application.port.CancelOrderUseCase;
import com.codeja.master_acdc.ordering.application.port.CreateOrderUseCase;
import com.codeja.master_acdc.ordering.application.port.ListOrdersByStatusUseCase;
import com.codeja.master_acdc.ordering.domain.port.OrderCancelledPublisher;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import com.codeja.master_acdc.ordering.domain.port.OrderSummaryQuery;
import com.codeja.master_acdc.ordering.domain.port.StockReserver;
import com.codeja.master_acdc.ordering.infrastructure.adapters.out.events.SpringOrderCancelledPublisher;
import com.codeja.master_acdc.ordering.infrastructure.adapters.out.inventory.OrderCancelledListener;
import com.codeja.master_acdc.ordering.infrastructure.adapters.out.inventory.StockReserverAdapter;
import com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence.CustomerJpaRepository;
import com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence.OrderJpaRepository;
import com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence.OrderRepositoryAdapter;
import com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence.OrderSummaryQueryAdapter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class OrderingConfiguration {

  @Bean
  public OrderRepository orderRepository(OrderJpaRepository orderJpaRepository) {
    return new OrderRepositoryAdapter(orderJpaRepository);
  }

  @Bean
  public OrderSummaryQuery orderSummaryQuery(
      OrderJpaRepository orderJpaRepository, CustomerJpaRepository customerJpaRepository) {
    return new OrderSummaryQueryAdapter(orderJpaRepository, customerJpaRepository);
  }

  @Bean
  public ListOrdersByStatusUseCase listOrdersByStatusUseCase(
      OrderSummaryQuery orderSummaryQuery, PlatformTransactionManager transactionManager) {
    ListOrdersByStatusService delegate = new ListOrdersByStatusService(orderSummaryQuery);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setReadOnly(true);
    return status -> transactionTemplate.execute(transaction -> delegate.list(status));
  }

  @Bean
  public StockReserver stockReserver(InventoryFacade inventoryFacade) {
    return new StockReserverAdapter(inventoryFacade);
  }

  @Bean
  public OrderCancelledPublisher orderCancelledPublisher(
      ApplicationEventPublisher applicationEventPublisher) {
    return new SpringOrderCancelledPublisher(applicationEventPublisher);
  }

  @Bean
  public OrderCancelledListener orderCancelledListener(InventoryFacade inventoryFacade) {
    return new OrderCancelledListener(inventoryFacade);
  }

  /**
   * Envuelve el caso de uso en una transacción propia: la reserva de inventario (propagación
   * REQUIRED) se une a ella, de modo que pedido y stock se confirman o revierten juntos.
   */
  @Bean
  public CreateOrderUseCase createOrderUseCase(
      OrderRepository orderRepository,
      StockReserver stockReserver,
      PlatformTransactionManager transactionManager) {
    CreateOrderService delegate = new CreateOrderService(orderRepository, stockReserver);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    return (customerId, lines) ->
        transactionTemplate.execute(status -> delegate.create(customerId, lines));
  }

  @Bean
  public CancelOrderUseCase cancelOrderUseCase(
      OrderRepository orderRepository,
      OrderCancelledPublisher orderCancelledPublisher,
      PlatformTransactionManager transactionManager) {
    CancelOrderService delegate = new CancelOrderService(orderRepository, orderCancelledPublisher);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    return (orderId, customerId) ->
        transactionTemplate.execute(status -> delegate.cancel(orderId, customerId));
  }
}
