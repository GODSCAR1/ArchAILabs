package com.codeja.master_acdc.ordering.infrastructure.config;

import com.codeja.master_acdc.ordering.application.ConfirmOrderService;
import com.codeja.master_acdc.ordering.application.port.ConfirmOrderUseCase;
import com.codeja.master_acdc.ordering.domain.port.OrderAmountCalculator;
import com.codeja.master_acdc.ordering.domain.port.OrderPayments;
import com.codeja.master_acdc.ordering.domain.port.OrderRepository;
import com.codeja.master_acdc.ordering.domain.port.PaymentGateway;
import com.codeja.master_acdc.ordering.infrastructure.adapters.in.web.AuthorizedConfirmOrderUseCase;
import com.codeja.master_acdc.ordering.infrastructure.adapters.in.web.OrderActorResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfirmationConfiguration {
  @Bean
  public ConfirmOrderUseCase confirmOrderUseCase(
      OrderRepository orders,
      OrderPayments payments,
      OrderAmountCalculator amounts,
      PaymentGateway gateway,
      OrderActorResolver actor) {
    return new AuthorizedConfirmOrderUseCase(
        new ConfirmOrderService(orders, payments, amounts, gateway), actor);
  }
}
