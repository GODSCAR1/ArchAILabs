package com.codeja.master_acdc.ordering.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeja.master_acdc.ordering.domain.model.OrderStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Transactional
class OrderSummaryQueryAdapterIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static final UUID CUSTOMER = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
  private static final UUID PAID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
  private static final UUID PENDING = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
  private static final UUID PRODUCT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID PRODUCT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired OrderSummaryQueryAdapter adapter;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from order_lines");
    jdbcTemplate.update("delete from orders");
    jdbcTemplate.update("delete from customers");
  }

  @Test
  @DisplayName("Pedidos PAID listados con nombre y métricas")
  void should_map_customer_name_and_line_metrics_when_paid_orders_exist() {
    insertCustomer();
    insertOrder(PAID, "PAID", 3, 2);

    var summaries = adapter.findByStatus(OrderStatus.PAID);

    assertThat(summaries)
        .singleElement()
        .satisfies(
            summary -> {
              assertThat(summary.id()).isEqualTo(PAID);
              assertThat(summary.customerName()).isEqualTo("Ada Lovelace");
              assertThat(summary.lineCount()).isEqualTo(2);
              assertThat(summary.totalUnits()).isEqualTo(5);
            });
  }

  @Test
  @DisplayName("Un pedido PENDING no aparece al solicitar PAID")
  void should_filter_orders_by_exact_status_when_pending_and_paid_exist() {
    insertOrder(PAID, "PAID", 3);
    insertOrder(PENDING, "PENDING", 2);

    assertThat(adapter.findByStatus(OrderStatus.PAID))
        .extracting(summary -> summary.id())
        .containsExactly(PAID);
  }

  @Test
  @DisplayName("Sin pedidos PAID")
  void should_return_empty_list_when_no_order_has_requested_status() {
    assertThat(adapter.findByStatus(OrderStatus.PAID)).isEmpty();
  }

  @Test
  @DisplayName("Pedido cuyo cliente no existe")
  void should_return_null_customer_name_when_customer_does_not_exist() {
    insertOrder(PAID, "PAID", 3);

    assertThat(adapter.findByStatus(OrderStatus.PAID))
        .singleElement()
        .satisfies(
            summary -> {
              assertThat(summary.customerName()).isNull();
            });
  }

  private void insertCustomer() {
    jdbcTemplate.update("insert into customers (id, name) values (?, ?)", CUSTOMER, "Ada Lovelace");
  }

  private void insertOrder(UUID orderId, String status, int... quantities) {
    jdbcTemplate.update(
        "insert into orders (id, customer_id, status) values (?, ?, ?)", orderId, CUSTOMER, status);
    List<UUID> products = List.of(PRODUCT_A, PRODUCT_B);
    for (int index = 0; index < quantities.length; index++) {
      jdbcTemplate.update(
          "insert into order_lines (order_id, product_id, quantity) values (?, ?, ?)",
          orderId,
          products.get(index),
          quantities[index]);
    }
  }
}
