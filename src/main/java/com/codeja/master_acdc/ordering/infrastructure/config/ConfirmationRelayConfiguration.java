package com.codeja.master_acdc.ordering.infrastructure.config;

import com.codeja.master_acdc.catalog.api.CatalogStockProjection;
import com.codeja.master_acdc.ordering.infrastructure.adapters.out.events.ScheduledConfirmationRelay;
import com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence.OrderConfirmationRelay;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class ConfirmationRelayConfiguration {
  @Bean
  public OrderConfirmationRelay orderConfirmationRelay(
      JdbcTemplate jdbc, CatalogStockProjection projection) {
    return new OrderConfirmationRelay(jdbc, projection);
  }

  @Bean
  public ScheduledConfirmationRelay scheduledConfirmationRelay(
      OrderConfirmationRelay relay, PlatformTransactionManager manager) {
    var transaction = new TransactionTemplate(manager);
    return new ScheduledConfirmationRelay(
        () -> transaction.executeWithoutResult(status -> relay.processOne()));
  }
}
