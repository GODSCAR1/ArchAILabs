package com.codeja.master_acdc.ordering.infrastructure.config;

import com.codeja.master_acdc.inventory.api.InventoryFacade;
import com.codeja.master_acdc.ordering.api.CurrentOrderActor;
import com.codeja.master_acdc.ordering.application.CancelOrderService;
import com.codeja.master_acdc.ordering.application.CreateOrderService;
import com.codeja.master_acdc.ordering.application.GetOrderService;
import com.codeja.master_acdc.ordering.application.ListOrdersByStatusService;
import com.codeja.master_acdc.ordering.application.port.CancelOrderUseCase;
import com.codeja.master_acdc.ordering.application.port.CreateOrderUseCase;
import com.codeja.master_acdc.ordering.application.port.GetOrderUseCase;
import com.codeja.master_acdc.ordering.application.port.ListOrdersByStatusUseCase;
import com.codeja.master_acdc.ordering.domain.port.OrderCancelledPublisher;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import com.codeja.master_acdc.ordering.domain.port.OrderSummaryQuery;
import com.codeja.master_acdc.ordering.domain.port.StockReserver;
import com.codeja.master_acdc.ordering.infrastructure.adapters.in.web.AuthorizedCancelOrderUseCase;
import com.codeja.master_acdc.ordering.infrastructure.adapters.in.web.AuthorizedCreateOrderUseCase;
import com.codeja.master_acdc.ordering.infrastructure.adapters.in.web.AuthorizedGetOrderUseCase;
import com.codeja.master_acdc.ordering.infrastructure.adapters.in.web.AuthorizedListOrdersByStatusUseCase;
import com.codeja.master_acdc.ordering.infrastructure.adapters.in.web.OrderActorResolver;
import com.codeja.master_acdc.ordering.infrastructure.adapters.out.events.SpringOrderCancelledPublisher;
import com.codeja.master_acdc.ordering.infrastructure.adapters.out.inventory.OrderCancelledListener;
import com.codeja.master_acdc.ordering.infrastructure.adapters.out.inventory.StockReserverAdapter;
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
  public OrderActorResolver orderActorResolver(CurrentOrderActor actor) {
    return new OrderActorResolver(actor);
  }

  @Bean
  public GetOrderUseCase getOrderUseCase(
      OrderRepository repository, PlatformTransactionManager manager, OrderActorResolver actor) {
    var service = new GetOrderService(repository);
    var transactions = new TransactionTemplate(manager);
    transactions.setReadOnly(true);
    GetOrderUseCase transactional =
        (id, scope) -> transactions.execute(status -> service.get(id, scope));
    return new AuthorizedGetOrderUseCase(transactional, actor);
  }

  @Bean
  public OrderRepository orderRepository(OrderJpaRepository orderJpaRepository) {
    return new OrderRepositoryAdapter(orderJpaRepository);
  }

  @Bean
  public OrderSummaryQuery orderSummaryQuery(jakarta.persistence.EntityManager entityManager) {
    return new OrderSummaryQueryAdapter(entityManager);
  }

  @Bean
  public ListOrdersByStatusUseCase listOrdersByStatusUseCase(
      OrderSummaryQuery orderSummaryQuery,
      PlatformTransactionManager transactionManager,
      OrderActorResolver actor) {
    ListOrdersByStatusService delegate = new ListOrdersByStatusService(orderSummaryQuery);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setReadOnly(true);
    ListOrdersByStatusUseCase transactional =
        criteria -> transactionTemplate.execute(transaction -> delegate.list(criteria));
    return new AuthorizedListOrdersByStatusUseCase(transactional, actor);
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
      PlatformTransactionManager transactionManager,
      OrderActorResolver actor) {
    CreateOrderService delegate = new CreateOrderService(orderRepository, stockReserver);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    CreateOrderUseCase transactional =
        (customerId, lines) ->
            transactionTemplate.execute(status -> delegate.create(customerId, lines));
    return new AuthorizedCreateOrderUseCase(transactional, actor);
  }

  @Bean
  public CancelOrderUseCase cancelOrderUseCase(
      OrderRepository orderRepository,
      OrderCancelledPublisher orderCancelledPublisher,
      PlatformTransactionManager transactionManager,
      OrderActorResolver actor) {
    CancelOrderService delegate = new CancelOrderService(orderRepository, orderCancelledPublisher);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    CancelOrderUseCase transactional =
        (orderId, customerId) ->
            transactionTemplate.execute(status -> delegate.cancel(orderId, customerId));
    return new AuthorizedCancelOrderUseCase(transactional, actor);
  }
}
