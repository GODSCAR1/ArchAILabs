package com.codeja.master_acdc.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.IntegrationTest;
import com.codeja.master_acdc.catalog.api.CatalogStockProjection;
import com.codeja.master_acdc.catalog.domain.model.ProductStatus;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CatalogProduct;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.CategoryRef;
import com.codeja.master_acdc.catalog.infrastructure.adapters.out.mongo.RatingSummary;
import com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence.OrderConfirmationRelay;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxRelayIdempotencyIT extends IntegrationTest {
  private final UUID order = UUID.randomUUID();
  private final UUID product = UUID.randomUUID();
  private final UUID event = UUID.randomUUID();
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MongoTemplate mongo;
  @Autowired private CatalogStockProjection projection;
  @Autowired private PlatformTransactionManager manager;

  @BeforeEach
  void seed_event() {
    jdbc.update(
        "insert into orders (id, customer_id, status) values (?, ?, 'PAID')",
        order,
        UUID.randomUUID());
    jdbc.update("insert into stock (product_id, available) values (?, 7)", product);
    jdbc.update(
        "insert into order_confirmation_outbox (id, order_id, product_ids) values (?, ?,"
            + " array[?]::uuid[])",
        event,
        order,
        product);
    mongo.insert(
        new CatalogProduct(
            product,
            "RELAY-SKU",
            "Producto",
            100,
            ProductStatus.ACTIVE,
            Instant.parse("2026-01-01T00:00:00Z"),
            0,
            new CategoryRef(UUID.randomUUID(), "Categoria"),
            List.of(),
            Map.of(),
            0,
            new RatingSummary(BigDecimal.ZERO, 0),
            List.of()));
  }

  @Test
  @DisplayName("Confirmación repetida es idempotente")
  void should_keep_current_stock_when_event_is_processed_again() {
    var relay = new OrderConfirmationRelay(jdbc, projection);
    run(relay);
    jdbc.update("update order_confirmation_outbox set processed_at = null where id = ?", event);
    run(relay);

    assertThat(mongo.findById(product, CatalogProduct.class).availableStock()).isEqualTo(7);
    assertThat(processed()).isTrue();
  }

  @Test
  @DisplayName("Fallo de proyección se recupera")
  void should_retry_when_projection_fails_after_mongo_write() {
    var failing =
        new OrderConfirmationRelay(
            jdbc,
            ids -> {
              projection.synchronize(ids);
              throw new DataAccessResourceFailureException("projection failure");
            });
    assertThatThrownBy(() -> run(failing)).isInstanceOf(DataAccessResourceFailureException.class);
    assertThat(processed()).isFalse();
    assertThat(mongo.findById(product, CatalogProduct.class).availableStock()).isEqualTo(7);

    run(new OrderConfirmationRelay(jdbc, projection));
    assertThat(processed()).isTrue();
    assertThat(mongo.findById(product, CatalogProduct.class).availableStock()).isEqualTo(7);
  }

  @Test
  @DisplayName("Dos instancias no procesan simultáneamente el mismo evento")
  void should_skip_event_when_another_instance_holds_its_lock() {
    AtomicInteger calls = new AtomicInteger();
    var relay = new OrderConfirmationRelay(jdbc, ids -> calls.incrementAndGet());
    try (var executor = Executors.newSingleThreadExecutor()) {
      new TransactionTemplate(manager)
          .executeWithoutResult(
              status -> {
                jdbc.queryForObject(
                    "select id from order_confirmation_outbox where id = ? for update",
                    UUID.class,
                    event);
                var other = executor.submit(() -> run(relay));
                org.assertj.core.api.Assertions.assertThatCode(() -> other.get(2, TimeUnit.SECONDS))
                    .doesNotThrowAnyException();
                assertThat(calls.get()).isZero();
              });
    }
    assertThat(processed()).isFalse();
  }

  private void run(OrderConfirmationRelay relay) {
    new TransactionTemplate(manager).executeWithoutResult(status -> relay.processOne());
  }

  private boolean processed() {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select processed_at is not null from order_confirmation_outbox where id = ?",
            Boolean.class,
            event));
  }
}
