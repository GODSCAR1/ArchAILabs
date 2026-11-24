package com.codeja.master_acdc.ordering.infrastructure.adapters.out.events;

import com.codeja.master_acdc.ordering.domain.event.OrderCancelled;
import com.codeja.master_acdc.ordering.domain.event.OrderConfirmed;
import com.codeja.master_acdc.ordering.domain.event.OrderEvent;
import com.codeja.master_acdc.ordering.domain.port.PublishOrderEvents;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

public final class OrderEventsAdapter implements PublishOrderEvents {
  private final ApplicationEventPublisher publisher;
  private final JdbcTemplate jdbc;

  public OrderEventsAdapter(ApplicationEventPublisher publisher, JdbcTemplate jdbc) {
    this.publisher = publisher;
    this.jdbc = jdbc;
  }

  @Override
  public void publish(List<OrderEvent> events) {
    for (OrderEvent event : events) {
      switch (event) {
        case OrderCancelled cancelled -> publisher.publishEvent(cancelled);
        case OrderConfirmed confirmed -> append(confirmed);
      }
    }
  }

  private void append(OrderConfirmed event) {
    UUID[] ids = event.productIds().toArray(UUID[]::new);
    jdbc.update(
        "insert into order_confirmation_outbox (id, order_id, product_ids) values (?, ?, ?)",
        statement -> {
          statement.setObject(1, UUID.randomUUID());
          statement.setObject(2, event.orderId());
          statement.setArray(3, statement.getConnection().createArrayOf("uuid", ids));
        });
  }
}
