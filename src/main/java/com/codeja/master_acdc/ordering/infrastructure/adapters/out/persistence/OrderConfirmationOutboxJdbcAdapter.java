package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.ordering.domain.model.Order;
import com.codeja.master_acdc.ordering.domain.model.OrderLine;
import com.codeja.master_acdc.ordering.domain.port.OrderConfirmationOutbox;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class OrderConfirmationOutboxJdbcAdapter implements OrderConfirmationOutbox {
  private final JdbcTemplate jdbc;

  public OrderConfirmationOutboxJdbcAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void append(Order order) {
    UUID[] ids = order.lines().stream().map(OrderLine::productId).toArray(UUID[]::new);
    jdbc.update(
        "insert into order_confirmation_outbox (id, order_id, product_ids) values (?, ?, ?)",
        statement -> {
          statement.setObject(1, UUID.randomUUID());
          statement.setObject(2, order.id());
          statement.setArray(3, statement.getConnection().createArrayOf("uuid", ids));
        });
  }
}
