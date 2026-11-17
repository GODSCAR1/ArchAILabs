package com.codeja.master_acdc.ordering.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeja.master_acdc.IntegrationTest;
import com.codeja.master_acdc.ordering.application.port.CancelOrderUseCase;
import com.codeja.master_acdc.ordering.application.port.CreateOrderUseCase;
import com.codeja.master_acdc.ordering.domain.exception.InsufficientStockException;
import com.codeja.master_acdc.ordering.domain.model.RequestedOrderLine;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

class OrderTransactionIT extends IntegrationTest {

  @org.junit.jupiter.api.BeforeEach
  void seed_current_actor() {
    jdbcTemplate.update(
        "insert into customers (id, name) values (?, ?)", UUID.fromString(CUSTOMER), "Fixture");
    jdbcTemplate.update(
        "insert into user_account (id, email, password_hash, roles, enabled, session_version,"
            + " customer_id) values (?, ?, ?, cast(? as text[]), true, 0, ?)",
        UUID.fromString(ACTOR),
        "fixture@example.com",
        "unused",
        "{CUSTOMER}",
        UUID.fromString(CUSTOMER));
  }

  private static final String CUSTOMER = "550e8400-e29b-41d4-a716-446655440000";
  private static final String A_TEXT = "11111111-1111-1111-1111-111111111111";
  private static final String B_TEXT = "22222222-2222-2222-2222-222222222222";
  private static final String ACTOR = "00000000-0000-0000-0000-000000000001";
  private static final String ORDER = "550e8400-e29b-41d4-a716-446655440001";

  @Autowired CreateOrderUseCase createOrderUseCase;
  @Autowired CancelOrderUseCase cancelOrderUseCase;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  @WithMockUser(username = ACTOR, authorities = "order:create")
  void should_rollback_order_and_stock_when_reservation_fails() {
    stock(A_TEXT, 10);
    stock(B_TEXT, 1);
    assertThatThrownBy(
            () ->
                createOrderUseCase.create(
                    UUID.fromString(CUSTOMER), List.of(line(A_TEXT, 3), line(B_TEXT, 2))))
        .isInstanceOf(InsufficientStockException.class);

    assertThat(rowCount("orders")).isZero();
    assertThat(rowCount("order_lines")).isZero();
    assertThat(availableOf(A_TEXT)).isEqualTo(10);
    assertThat(availableOf(B_TEXT)).isEqualTo(1);
  }

  @Test
  @WithMockUser(username = ACTOR, authorities = "order:cancel-own")
  void should_rollback_order_and_stock_when_release_fails() {
    stock(A_TEXT, 5);
    order(ORDER, CUSTOMER, "PENDING");
    orderLine(ORDER, A_TEXT, 3);
    jdbcTemplate.update("delete from stock where product_id = ?", UUID.fromString(A_TEXT));

    assertThatThrownBy(
            () -> cancelOrderUseCase.cancel(UUID.fromString(ORDER), UUID.fromString(CUSTOMER)))
        .isInstanceOf(
            com.codeja.master_acdc.inventory.domain.exception.StockRecordNotFoundException.class);

    assertThat(rowCount("orders")).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from orders where id = ?", String.class, UUID.fromString(ORDER)))
        .isEqualTo("PENDING");
  }

  private static RequestedOrderLine line(String productId, int quantity) {
    return new RequestedOrderLine(UUID.fromString(productId), quantity);
  }

  private void stock(String productId, int available) {
    jdbcTemplate.update(
        "insert into stock (product_id, available) values (?, ?)",
        UUID.fromString(productId),
        available);
  }

  private void order(String orderId, String customerId, String status) {
    jdbcTemplate.update(
        "insert into orders (id, customer_id, status) values (?, ?, ?)",
        UUID.fromString(orderId),
        UUID.fromString(customerId),
        status);
  }

  private void orderLine(String orderId, String productId, int quantity) {
    jdbcTemplate.update(
        "insert into order_lines (order_id, product_id, quantity) values (?, ?, ?)",
        UUID.fromString(orderId),
        UUID.fromString(productId),
        quantity);
  }

  private int rowCount(String table) {
    String sql =
        switch (table) {
          case "orders" -> "select count(*) from orders";
          case "order_lines" -> "select count(*) from order_lines";
          default -> throw new IllegalArgumentException(table);
        };
    return jdbcTemplate.queryForObject(sql, Integer.class);
  }

  private int availableOf(String productId) {
    return jdbcTemplate.queryForObject(
        "select available from stock where product_id = ?",
        Integer.class,
        UUID.fromString(productId));
  }
}
