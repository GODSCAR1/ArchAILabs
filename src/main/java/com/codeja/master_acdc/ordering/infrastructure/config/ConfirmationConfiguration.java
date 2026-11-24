package com.codeja.master_acdc.ordering.infrastructure.config;

import com.codeja.master_acdc.ordering.application.ConfirmOrderService;
import com.codeja.master_acdc.ordering.application.port.ConfirmOrderUseCase;
import com.codeja.master_acdc.ordering.domain.exception.OrderNotConfirmableException;
import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.PaymentAttempt;
import com.codeja.master_acdc.ordering.domain.port.OrderAmountCalculator;
import com.codeja.master_acdc.ordering.domain.port.OrderConfirmationOutbox;
import com.codeja.master_acdc.ordering.domain.port.OrderPaymentRepository;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import com.codeja.master_acdc.ordering.domain.port.PaymentGateway;
import com.codeja.master_acdc.ordering.domain.port.PaymentTransactions;
import com.codeja.master_acdc.ordering.infrastructure.adapters.in.web.AuthorizedConfirmOrderUseCase;
import com.codeja.master_acdc.ordering.infrastructure.adapters.in.web.OrderActorResolver;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class ConfirmationConfiguration {
  @Bean
  public PaymentTransactions paymentTransactions(
      OrderPaymentRepository payments,
      OrderConfirmationOutbox outbox,
      PlatformTransactionManager manager) {
    var transaction = new TransactionTemplate(manager);
    return new PaymentTransactions() {
      @Override
      public PaymentAttempt begin(UUID orderId, UUID customerId, BigDecimal amount) {
        return transaction.execute(
            status ->
                payments
                    .start(orderId, customerId, amount)
                    .orElseThrow(() -> new OrderNotConfirmableException(orderId)));
      }

      @Override
      public void failed(UUID orderId, UUID customerId, boolean declined) {
        transaction.executeWithoutResult(
            status -> {
              if (!payments.returnToPending(orderId, customerId, declined)) {
                throw new OrderNotConfirmableException(orderId);
              }
            });
      }

      @Override
      public Order complete(Order order) {
        return transaction.execute(
            status -> {
              if (!payments.markPaid(order.id(), order.customerId())) {
                throw new OrderNotConfirmableException(order.id());
              }
              outbox.append(order);
              order.registerPayment();
              return order;
            });
      }
    };
  }

  @Bean
  public ConfirmationDependencies confirmationDependencies(
      OrderRepository orders, OrderPaymentRepository payments, OrderAmountCalculator amounts) {
    return new ConfirmationDependencies(orders, payments, amounts);
  }

  @Bean
  public ConfirmOrderUseCase confirmOrderUseCase(
      ConfirmationDependencies dependencies,
      PaymentGateway gateway,
      PaymentTransactions transactions,
      OrderActorResolver actor) {
    var service =
        new ConfirmOrderService(
            dependencies.orders(),
            dependencies.payments(),
            dependencies.amounts(),
            gateway,
            transactions);
    return new AuthorizedConfirmOrderUseCase(service, actor);
  }

  public record ConfirmationDependencies(
      OrderRepository orders, OrderPaymentRepository payments, OrderAmountCalculator amounts) {}
}
