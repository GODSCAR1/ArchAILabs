package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import com.codeja.master_acdc.catalog.api.CatalogStockProjection;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Procesa como máximo un evento; el llamador mantiene la transacción durante la proyección. */
public final class OrderConfirmationRelay {
  private final JdbcTemplate jdbc;
  private final CatalogStockProjection projection;

  public OrderConfirmationRelay(JdbcTemplate jdbc, CatalogStockProjection projection) {
    this.jdbc = jdbc;
    this.projection = projection;
  }

  public void processOne() {
    var events =
        jdbc.query(
            """
            select id, product_ids from order_confirmation_outbox
            where processed_at is null order by created_at, id
            limit 1 for update skip locked
            """,
            (row, index) ->
                new Event(
                    row.getObject("id", UUID.class),
                    Arrays.asList((UUID[]) row.getArray("product_ids").getArray())));
    if (events.isEmpty()) {
      return;
    }
    Event event = events.getFirst();
    projection.synchronize(event.productIds());
    jdbc.update(
        "update order_confirmation_outbox set processed_at = current_timestamp where id = ?",
        event.id());
  }

  private record Event(UUID id, List<UUID> productIds) {}
}
